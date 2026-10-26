package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.LocalCommentEntity
import com.example.data.local.LocalDocumentEntity
import com.example.data.local.LocalInsightEntity
import com.example.data.model.FirestoreChatMessage
import com.example.data.model.FirestoreComment
import com.example.data.model.FirestoreDocument
import com.example.data.model.FirestoreInsight
import com.example.util.LocalEncryptionHelper
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "DocumentRepository"

class DocumentRepository(
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

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    /**
     * Observes documents belonging to the user or where the user is a collaborator.
     * Also updates local encrypted Room database for seamless offline mode.
     */
    fun observeDocuments(): Flow<List<FirestoreDocument>> = flow {
        val uid = requireUserId()
        val path = "documents"

        // Firestore real-time observation
        val query = db.collection("documents")
            .whereEqualTo("ownerId", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        emitAll(
            query.snapshots()
                .map { snapshot ->
                    val docs = snapshot.toObjects(FirestoreDocument::class.java)
                    // Asynchronously cache documents locally with encrypted content
                    withContext(Dispatchers.IO) {
                        try {
                            val localEntities = docs.map { doc ->
                                LocalDocumentEntity(
                                    id = doc.id,
                                    ownerId = doc.ownerId,
                                    title = doc.title,
                                    format = doc.format,
                                    encryptedContent = LocalEncryptionHelper.encrypt(doc.content),
                                    pageCount = doc.pageCount.toInt(),
                                    wordCount = doc.wordCount.toInt(),
                                    summary = doc.summary,
                                    summaryStatus = doc.summaryStatus,
                                    cloudSource = doc.cloudSource,
                                    collaboratorsJson = doc.collaborators.joinToString(","),
                                    isPendingSync = false,
                                    createdAtEpoch = doc.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                                    updatedAtEpoch = doc.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            }
                            appDb.documentDao().upsertDocuments(localEntities)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to cache documents locally: ${e.message}")
                        }
                    }
                    docs
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    // Fallback to local offline cache
                    emitAll(
                        appDb.documentDao().getAllDocuments().map { cachedList ->
                            cachedList.map { local ->
                                FirestoreDocument(
                                    id = local.id,
                                    ownerId = local.ownerId,
                                    title = local.title,
                                    format = local.format,
                                    content = LocalEncryptionHelper.decrypt(local.encryptedContent),
                                    pageCount = local.pageCount.toLong(),
                                    wordCount = local.wordCount.toLong(),
                                    summary = local.summary,
                                    summaryStatus = local.summaryStatus,
                                    cloudSource = local.cloudSource,
                                    collaborators = if (local.collaboratorsJson.isBlank()) emptyList() else local.collaboratorsJson.split(",")
                                )
                            }
                        }
                    )
                }
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Creates a new document both in Firestore and encrypted local Room cache.
     */
    suspend fun createDocument(
        title: String,
        format: String,
        content: String,
        cloudSource: String = "local",
        pageCount: Long = 1L,
        collaborators: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val docId = "doc_${System.currentTimeMillis()}"
        val words = content.split("\\s+".toRegex()).count { it.isNotBlank() }.toLong()

        val docRef = db.collection("documents").document(docId)
        val payload = hashMapOf<String, Any>(
            "ownerId" to uid,
            "title" to title,
            "format" to format,
            "content" to content,
            "pageCount" to pageCount,
            "wordCount" to words,
            "summary" to "",
            "summaryStatus" to "pending",
            "cloudSource" to cloudSource,
            "collaborators" to collaborators,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // Cache locally first for instant offline responsiveness
        val localEntity = LocalDocumentEntity(
            id = docId,
            ownerId = uid,
            title = title,
            format = format,
            encryptedContent = LocalEncryptionHelper.encrypt(content),
            pageCount = pageCount.toInt(),
            wordCount = words.toInt(),
            summary = "",
            summaryStatus = "pending",
            cloudSource = cloudSource,
            collaboratorsJson = collaborators.joinToString(","),
            isPendingSync = true
        )
        appDb.documentDao().upsertDocument(localEntity)

        try {
            docRef.set(payload).await()
            // Mark synced locally
            appDb.documentDao().upsertDocument(localEntity.copy(isPendingSync = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Log.w(TAG, "Offline create queued: ${e.message}")
        }

        docId
    }

    /**
     * Updates an existing document (e.g. summary or content changes).
     */
    suspend fun updateDocumentSummary(documentId: String, summary: String, status: String) = withContext(Dispatchers.IO) {
        val docRef = db.collection("documents").document(documentId)
        val updates = mapOf(
            "summary" to summary,
            "summaryStatus" to status,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
            val existing = appDb.documentDao().getDocumentById(documentId)
            if (existing != null) {
                appDb.documentDao().upsertDocument(
                    existing.copy(summary = summary, summaryStatus = status, isPendingSync = false)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            val existing = appDb.documentDao().getDocumentById(documentId)
            if (existing != null) {
                appDb.documentDao().upsertDocument(
                    existing.copy(summary = summary, summaryStatus = status, isPendingSync = true)
                )
            }
        }
    }

    /**
     * Updates collaborators on a document.
     */
    suspend fun updateCollaborators(documentId: String, collaborators: List<String>) = withContext(Dispatchers.IO) {
        val docRef = db.collection("documents").document(documentId)
        try {
            docRef.update(
                mapOf(
                    "collaborators" to collaborators,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    /**
     * Observes extracted key insights for a specific document in real time.
     */
    fun observeInsights(documentId: String): Flow<List<FirestoreInsight>> = flow {
        val path = "documents/$documentId/insights"
        emitAll(
            db.collection("documents").document(documentId).collection("insights")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    val insights = snapshot.toObjects(FirestoreInsight::class.java)
                    withContext(Dispatchers.IO) {
                        try {
                            appDb.insightDao().upsertInsights(
                                insights.map {
                                    LocalInsightEntity(
                                        id = it.id,
                                        documentId = it.documentId,
                                        ownerId = it.ownerId,
                                        title = it.title,
                                        content = it.content,
                                        sourceReference = it.sourceReference,
                                        category = it.category,
                                        createdAtEpoch = it.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                                    )
                                }
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to cache insights: ${e.message}")
                        }
                    }
                    insights
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    // Offline fallback
                    emitAll(
                        appDb.insightDao().getInsightsForDocument(documentId).map { cached ->
                            cached.map {
                                FirestoreInsight(
                                    id = it.id,
                                    documentId = it.documentId,
                                    ownerId = it.ownerId,
                                    title = it.title,
                                    content = it.content,
                                    sourceReference = it.sourceReference,
                                    category = it.category
                                )
                            }
                        }
                    )
                }
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Adds an extracted insight to Firestore.
     */
    suspend fun saveInsight(insight: FirestoreInsight) = withContext(Dispatchers.IO) {
        val ref = db.collection("documents").document(insight.documentId).collection("insights").document(insight.id)
        val payload = hashMapOf<String, Any>(
            "documentId" to insight.documentId,
            "ownerId" to insight.ownerId,
            "collaborators" to insight.collaborators,
            "title" to insight.title,
            "content" to insight.content,
            "sourceReference" to insight.sourceReference,
            "category" to insight.category,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            ref.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
        }
    }

    /**
     * Observes live collaborative comments for a document.
     */
    fun observeComments(documentId: String): Flow<List<FirestoreComment>> = flow {
        val path = "documents/$documentId/comments"
        emitAll(
            db.collection("documents").document(documentId).collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreComment::class.java) }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    emitAll(
                        appDb.commentDao().getCommentsForDocument(documentId).map { cached ->
                            cached.map {
                                FirestoreComment(
                                    id = it.id,
                                    documentId = it.documentId,
                                    ownerId = it.ownerId,
                                    authorId = it.authorId,
                                    authorName = it.authorName,
                                    text = it.text,
                                    quoteReference = it.quoteReference,
                                    resolved = it.resolved
                                )
                            }
                        }
                    )
                }
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Adds a collaborative team comment.
     */
    suspend fun addComment(
        documentId: String,
        ownerId: String,
        collaborators: List<String>,
        authorName: String,
        text: String,
        quoteReference: String = ""
    ) = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val commentId = "com_${System.currentTimeMillis()}"
        val ref = db.collection("documents").document(documentId).collection("comments").document(commentId)
        val payload = hashMapOf<String, Any>(
            "documentId" to documentId,
            "ownerId" to ownerId,
            "collaborators" to collaborators,
            "authorId" to uid,
            "authorName" to authorName,
            "text" to text,
            "quoteReference" to quoteReference,
            "resolved" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            ref.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            // Cache locally pending sync
            appDb.commentDao().upsertComment(
                LocalCommentEntity(
                    id = commentId,
                    documentId = documentId,
                    ownerId = ownerId,
                    authorId = uid,
                    authorName = authorName,
                    text = text,
                    quoteReference = quoteReference,
                    resolved = false,
                    isPendingSync = true
                )
            )
        }
    }

    /**
     * Observes interactive Q&A chat messages for a document.
     */
    fun observeChatMessages(documentId: String): Flow<List<FirestoreChatMessage>> = flow {
        val path = "documents/$documentId/chatMessages"
        emitAll(
            db.collection("documents").document(documentId).collection("chatMessages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreChatMessage::class.java) }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    emit(emptyList())
                }
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Appends a chat message to the document Q&A thread.
     */
    suspend fun addChatMessage(
        documentId: String,
        ownerId: String,
        collaborators: List<String>,
        senderId: String,
        senderRole: String,
        message: String,
        citations: List<String> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val msgId = "msg_${System.currentTimeMillis()}"
        val ref = db.collection("documents").document(documentId).collection("chatMessages").document(msgId)
        val payload = hashMapOf<String, Any>(
            "documentId" to documentId,
            "ownerId" to ownerId,
            "collaborators" to collaborators,
            "senderId" to senderId,
            "senderRole" to senderRole,
            "message" to message,
            "citations" to citations,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            ref.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
        }
    }

    /**
     * Performs automatic background sync for offline-created items when connection restores.
     */
    suspend fun syncPendingOfflineData() = withContext(Dispatchers.IO) {
        try {
            val pendingDocs = appDb.documentDao().getPendingSyncDocuments()
            for (doc in pendingDocs) {
                val ref = db.collection("documents").document(doc.id)
                val plainContent = LocalEncryptionHelper.decrypt(doc.encryptedContent)
                val payload = hashMapOf<String, Any>(
                    "ownerId" to doc.ownerId,
                    "title" to doc.title,
                    "format" to doc.format,
                    "content" to plainContent,
                    "pageCount" to doc.pageCount.toLong(),
                    "wordCount" to doc.wordCount.toLong(),
                    "summary" to doc.summary,
                    "summaryStatus" to doc.summaryStatus,
                    "cloudSource" to doc.cloudSource,
                    "collaborators" to if (doc.collaboratorsJson.isBlank()) emptyList() else doc.collaboratorsJson.split(","),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
                appDb.documentDao().upsertDocument(doc.copy(isPendingSync = false))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed: ${e.message}")
        }
    }
}
