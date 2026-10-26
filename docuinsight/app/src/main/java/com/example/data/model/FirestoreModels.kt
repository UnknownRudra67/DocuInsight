package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreDocument(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val title: String = "",
    val format: String = "txt",
    val content: String = "",
    val pageCount: Long = 1L,
    val wordCount: Long = 0L,
    val summary: String = "",
    val summaryStatus: String = "pending", // pending, processing, completed, error
    val cloudSource: String = "local", // local, google_drive, dropbox
    val collaborators: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreInsight(
    @DocumentId val id: String = "",
    val documentId: String = "",
    val ownerId: String = "",
    val collaborators: List<String> = emptyList(),
    val title: String = "",
    val content: String = "",
    val sourceReference: String = "", // e.g. "Page 3, Paragraph 2"
    val category: String = "key_takeaway", // key_takeaway, risk, action_item, financial, statistic
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreComment(
    @DocumentId val id: String = "",
    val documentId: String = "",
    val ownerId: String = "",
    val collaborators: List<String> = emptyList(),
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val quoteReference: String = "",
    val resolved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreChatMessage(
    @DocumentId val id: String = "",
    val documentId: String = "",
    val ownerId: String = "",
    val collaborators: List<String> = emptyList(),
    val senderId: String = "",
    val senderRole: String = "user", // user, ai
    val message: String = "",
    val citations: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreAuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val documentId: String = "",
    val documentTitle: String = "",
    val action: String = "",
    val details: String = "",
    @ServerTimestamp val timestamp: Timestamp? = null
)

enum class UserDocRole {
    OWNER,
    EDITOR,
    VIEWER
}
