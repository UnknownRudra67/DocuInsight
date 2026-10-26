package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM local_documents ORDER BY updatedAtEpoch DESC")
    fun getAllDocuments(): Flow<List<LocalDocumentEntity>>

    @Query("SELECT * FROM local_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): LocalDocumentEntity?

    @Query("SELECT * FROM local_documents WHERE isPendingSync = 1")
    suspend fun getPendingSyncDocuments(): List<LocalDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(doc: LocalDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocuments(docs: List<LocalDocumentEntity>)

    @Query("DELETE FROM local_documents WHERE id = :id")
    suspend fun deleteDocument(id: String)
}

@Dao
interface InsightDao {
    @Query("SELECT * FROM local_insights WHERE documentId = :documentId ORDER BY createdAtEpoch ASC")
    fun getInsightsForDocument(documentId: String): Flow<List<LocalInsightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInsights(insights: List<LocalInsightEntity>)

    @Query("DELETE FROM local_insights WHERE documentId = :documentId")
    suspend fun deleteInsightsForDocument(documentId: String)
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM local_comments WHERE documentId = :documentId ORDER BY createdAtEpoch ASC")
    fun getCommentsForDocument(documentId: String): Flow<List<LocalCommentEntity>>

    @Query("SELECT * FROM local_comments WHERE isPendingSync = 1")
    suspend fun getPendingSyncComments(): List<LocalCommentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComment(comment: LocalCommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComments(comments: List<LocalCommentEntity>)

    @Query("DELETE FROM local_comments WHERE id = :commentId")
    suspend fun deleteComment(commentId: String)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM local_audit_logs ORDER BY timestampEpoch DESC")
    fun getAllLogs(): Flow<List<LocalAuditLogEntity>>

    @Query("SELECT * FROM local_audit_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<LocalAuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LocalAuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<LocalAuditLogEntity>)

    @Query("DELETE FROM local_audit_logs")
    suspend fun clearLogs()
}

@Database(
    entities = [
        LocalDocumentEntity::class,
        LocalInsightEntity::class,
        LocalCommentEntity::class,
        LocalAuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun insightDao(): InsightDao
    abstract fun commentDao(): CommentDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "docu_insight.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
