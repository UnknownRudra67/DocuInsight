package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_documents")
data class LocalDocumentEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val title: String,
    val format: String,
    val encryptedContent: String,
    val pageCount: Int,
    val wordCount: Int,
    val summary: String,
    val summaryStatus: String,
    val cloudSource: String,
    val collaboratorsJson: String, // comma-separated or json string
    val isPendingSync: Boolean = false,
    val createdAtEpoch: Long = System.currentTimeMillis(),
    val updatedAtEpoch: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_insights")
data class LocalInsightEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val ownerId: String,
    val title: String,
    val content: String,
    val sourceReference: String,
    val category: String,
    val createdAtEpoch: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_comments")
data class LocalCommentEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val ownerId: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val quoteReference: String,
    val resolved: Boolean,
    val isPendingSync: Boolean = false,
    val createdAtEpoch: Long = System.currentTimeMillis(),
    val updatedAtEpoch: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_audit_logs")
data class LocalAuditLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val userEmail: String,
    val documentId: String,
    val documentTitle: String,
    val action: String,
    val details: String,
    val timestampEpoch: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
