package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.LocalAuditLogEntity
import com.example.data.model.FirestoreAuditLog
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "AuditRepository"

class AuditRepository(
    private val db: FirebaseFirestore,
    private val appDb: AppDatabase
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        AppDatabase.getInstance(context.applicationContext)
    )

    private val auth = Firebase.auth

    fun observeAuditLogs(): Flow<List<FirestoreAuditLog>> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        val path = "auditLogs"
        val query = db.collection("auditLogs")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        emitAll(
            query.snapshots()
                .map { snapshot ->
                    val logs = snapshot.toObjects(FirestoreAuditLog::class.java)
                    // Cache locally in background
                    withContext(Dispatchers.IO) {
                        try {
                            appDb.auditLogDao().insertLogs(
                                logs.map {
                                    LocalAuditLogEntity(
                                        id = it.id,
                                        userId = it.userId,
                                        userEmail = it.userEmail,
                                        documentId = it.documentId,
                                        documentTitle = it.documentTitle,
                                        action = it.action,
                                        details = it.details,
                                        timestampEpoch = it.timestamp?.toDate()?.time ?: System.currentTimeMillis(),
                                        isSynced = true
                                    )
                                }
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed caching audit logs: ${e.message}")
                        }
                    }
                    logs
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    // Fallback to local Room logs
                    emitAll(
                        appDb.auditLogDao().getAllLogs().map { cached ->
                            cached.map {
                                FirestoreAuditLog(
                                    id = it.id,
                                    userId = it.userId,
                                    userEmail = it.userEmail,
                                    documentId = it.documentId,
                                    documentTitle = it.documentTitle,
                                    action = it.action,
                                    details = it.details
                                )
                            }
                        }
                    )
                }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun logActivity(
        action: String,
        details: String,
        documentId: String = "",
        documentTitle: String = ""
    ) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        val email = auth.currentUser?.email ?: "anonymous@docuinsight.ai"
        val logId = "log_${System.currentTimeMillis()}"

        val localLog = LocalAuditLogEntity(
            id = logId,
            userId = uid,
            userEmail = email,
            documentId = documentId,
            documentTitle = documentTitle,
            action = action,
            details = details,
            timestampEpoch = System.currentTimeMillis(),
            isSynced = false
        )
        appDb.auditLogDao().insertLog(localLog)

        val ref = db.collection("auditLogs").document(logId)
        val payload = hashMapOf<String, Any>(
            "userId" to uid,
            "userEmail" to email,
            "documentId" to documentId,
            "documentTitle" to documentTitle,
            "action" to action,
            "details" to details,
            "timestamp" to FieldValue.serverTimestamp()
        )
        try {
            ref.set(payload).await()
            appDb.auditLogDao().insertLog(localLog.copy(isSynced = true))
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Log.w(TAG, "Offline audit logged: ${e.message}")
        }
    }

    /**
     * Formats activity history logs into a downloadable JSON/Text compliance report.
     */
    fun exportLogsToJson(logs: List<FirestoreAuditLog>): String {
        val array = JSONArray()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
        for (log in logs) {
            val obj = JSONObject().apply {
                put("id", log.id)
                put("userId", log.userId)
                put("userEmail", log.userEmail)
                put("action", log.action)
                put("documentTitle", log.documentTitle)
                put("details", log.details)
                put("timestamp", log.timestamp?.toDate()?.let { sdf.format(it) } ?: "Pending Sync")
            }
            array.put(obj)
        }
        return array.toString(2)
    }
}
