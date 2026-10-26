package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FirestoreChatMessage
import com.example.data.model.FirestoreComment
import com.example.data.model.FirestoreDocument
import com.example.data.model.FirestoreInsight
import com.example.data.repository.AiRepository
import com.example.data.repository.AuditRepository
import com.example.data.repository.CloudFileItem
import com.example.data.repository.CloudStorageRepository
import com.example.data.repository.DocumentRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "DocViewModel"

data class AppNotification(
    val id: String = "notif_${System.currentTimeMillis()}",
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class DocViewModel(context: Context) : ViewModel() {
    private val docRepository = DocumentRepository(context)
    private val aiRepository = AiRepository()
    private val auditRepository = AuditRepository(context)
    private val cloudStorageRepository = CloudStorageRepository()

    val documents: StateFlow<List<FirestoreDocument>> = docRepository.observeDocuments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _selectedDocument = MutableStateFlow<FirestoreDocument?>(null)
    val selectedDocument: StateFlow<FirestoreDocument?> = _selectedDocument.asStateFlow()

    private val _insights = MutableStateFlow<List<FirestoreInsight>>(emptyList())
    val insights: StateFlow<List<FirestoreInsight>> = _insights.asStateFlow()

    private val _comments = MutableStateFlow<List<FirestoreComment>>(emptyList())
    val comments: StateFlow<List<FirestoreComment>> = _comments.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<FirestoreChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<FirestoreChatMessage>> = _chatMessages.asStateFlow()

    val auditLogs = auditRepository.observeAuditLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // Operation loading states
    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    private val _isExtractingInsights = MutableStateFlow(false)
    val isExtractingInsights: StateFlow<Boolean> = _isExtractingInsights.asStateFlow()

    private val _isSendingChat = MutableStateFlow(false)
    val isSendingChat: StateFlow<Boolean> = _isSendingChat.asStateFlow()

    private val _streamingSummaryText = MutableStateFlow<String?>(null)
    val streamingSummaryText: StateFlow<String?> = _streamingSummaryText.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Settings & security toggles
    private val _offlineModeEnabled = MutableStateFlow(false)
    val offlineModeEnabled: StateFlow<Boolean> = _offlineModeEnabled.asStateFlow()

    private val _biometricSecurityEnabled = MutableStateFlow(false)
    val biometricSecurityEnabled: StateFlow<Boolean> = _biometricSecurityEnabled.asStateFlow()

    private val _isBiometricLocked = MutableStateFlow(false)
    val isBiometricLocked: StateFlow<Boolean> = _isBiometricLocked.asStateFlow()

    private val _themeMode = MutableStateFlow("system") // system, dark, light
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun selectDocument(doc: FirestoreDocument?) {
        _selectedDocument.value = doc
        if (doc != null) {
            if (_biometricSecurityEnabled.value) {
                _isBiometricLocked.value = true
            }
            observeSelectedDocumentData(doc.id)
            viewModelScope.launch {
                auditRepository.logActivity(
                    action = "view_document",
                    details = "Opened document '${doc.title}' (Format: ${doc.format.uppercase()})",
                    documentId = doc.id,
                    documentTitle = doc.title
                )
            }
        }
    }

    private fun observeSelectedDocumentData(docId: String) {
        viewModelScope.launch {
            docRepository.observeInsights(docId).collect {
                _insights.value = it
            }
        }
        viewModelScope.launch {
            docRepository.observeComments(docId).collect {
                _comments.value = it
            }
        }
        viewModelScope.launch {
            docRepository.observeChatMessages(docId).collect {
                _chatMessages.value = it
            }
        }
    }

    fun unlockBiometrics() {
        _isBiometricLocked.value = false
        addNotification("Biometrics Verified", "Sensitive document unlocked securely.")
    }

    fun toggleBiometricSecurity(enabled: Boolean) {
        _biometricSecurityEnabled.value = enabled
        if (!enabled) {
            _isBiometricLocked.value = false
        }
        viewModelScope.launch {
            auditRepository.logActivity(
                action = "security_settings",
                details = "Biometric authentication lock set to: $enabled"
            )
        }
    }

    fun toggleOfflineMode(enabled: Boolean) {
        _offlineModeEnabled.value = enabled
        if (!enabled) {
            // Online restored: trigger auto-sync
            viewModelScope.launch {
                docRepository.syncPendingOfflineData()
                addNotification("Sync Complete", "Offline documents and annotations synced with cloud.")
                auditRepository.logActivity(
                    action = "offline_sync",
                    details = "Automatic cloud sync restored and completed."
                )
            }
        } else {
            addNotification("Offline Mode", "Productivity mode active. Local changes encrypted with AES-256-GCM.")
        }
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
    }

    fun addNotification(title: String, message: String) {
        val notif = AppNotification(title = title, message = message)
        _notifications.value = listOf(notif) + _notifications.value.take(9)
    }

    fun dismissNotification(id: String) {
        _notifications.value = _notifications.value.filter { it.id != id }
    }

    fun createDocument(
        title: String,
        format: String,
        content: String,
        cloudSource: String = "local",
        pageCount: Long = 1L
    ) {
        viewModelScope.launch {
            try {
                val docId = docRepository.createDocument(
                    title = title,
                    format = format,
                    content = content,
                    cloudSource = cloudSource,
                    pageCount = pageCount
                )
                auditRepository.logActivity(
                    action = "create_doc",
                    details = "Created document '$title' [$format]",
                    documentId = docId,
                    documentTitle = title
                )
                addNotification("Document Added", "'$title' has been added to your workspace.")
                _statusMessage.value = "Document created successfully!"
            } catch (e: Exception) {
                Log.e(TAG, "Error creating document", e)
                _statusMessage.value = "Failed to create document: ${e.localizedMessage}"
            }
        }
    }

    fun generateSummary(document: FirestoreDocument) {
        viewModelScope.launch {
            _isSummarizing.value = true
            _streamingSummaryText.value = "Analyzing document content with Gemini AI…"
            try {
                docRepository.updateDocumentSummary(document.id, "", "processing")
                var fullSummary = ""
                aiRepository.generateSummaryStream(document.title, document.content).collect { partial ->
                    fullSummary = partial
                    _streamingSummaryText.value = partial
                }
                docRepository.updateDocumentSummary(document.id, fullSummary, "completed")
                _selectedDocument.value = _selectedDocument.value?.copy(
                    summary = fullSummary,
                    summaryStatus = "completed"
                )
                auditRepository.logActivity(
                    action = "summarize",
                    details = "Generated AI executive summary for '${document.title}'",
                    documentId = document.id,
                    documentTitle = document.title
                )
                addNotification("Summary Ready", "AI summary generated for '${document.title}'.")
                _statusMessage.value = "Summary generated successfully!"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed generating summary", e)
                docRepository.updateDocumentSummary(document.id, "", "error")
                _statusMessage.value = "Summarization error: ${e.localizedMessage}"
            } finally {
                _isSummarizing.value = false
                _streamingSummaryText.value = null
            }
        }
    }

    fun extractKeyInsights(document: FirestoreDocument) {
        viewModelScope.launch {
            _isExtractingInsights.value = true
            try {
                val extracted = aiRepository.extractKeyInsights(
                    documentId = document.id,
                    ownerId = document.ownerId,
                    collaborators = document.collaborators,
                    documentContent = document.content
                )
                for (insight in extracted) {
                    docRepository.saveInsight(insight)
                }
                auditRepository.logActivity(
                    action = "extract_insights",
                    details = "Extracted ${extracted.size} key insights with verified source citations.",
                    documentId = document.id,
                    documentTitle = document.title
                )
                addNotification("Key Insights Extracted", "${extracted.size} insights with source references ready for review.")
                _statusMessage.value = "Extracted ${extracted.size} key insights with source citations!"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Insight extraction error", e)
                _statusMessage.value = "Insight extraction failed: ${e.localizedMessage}"
            } finally {
                _isExtractingInsights.value = false
            }
        }
    }

    fun sendFollowUpQuestion(document: FirestoreDocument, question: String) {
        if (question.isBlank()) return
        val currentUid = Firebase.auth.currentUser?.uid ?: "user"
        viewModelScope.launch {
            _isSendingChat.value = true
            try {
                // Post user message
                docRepository.addChatMessage(
                    documentId = document.id,
                    ownerId = document.ownerId,
                    collaborators = document.collaborators,
                    senderId = currentUid,
                    senderRole = "user",
                    message = question
                )

                // Stream AI reply
                var fullAiReply = ""
                aiRepository.askFollowUpQuestionStream(
                    documentTitle = document.title,
                    documentContent = document.content,
                    question = question
                ).collect { partial ->
                    fullAiReply = partial
                }

                // Save AI response
                docRepository.addChatMessage(
                    documentId = document.id,
                    ownerId = document.ownerId,
                    collaborators = document.collaborators,
                    senderId = "gemini_ai",
                    senderRole = "ai",
                    message = fullAiReply
                )

                auditRepository.logActivity(
                    action = "ask_ai",
                    details = "Asked follow-up question on '${document.title}'",
                    documentId = document.id,
                    documentTitle = document.title
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat Q&A failed", e)
                _statusMessage.value = "Chat error: ${e.localizedMessage}"
            } finally {
                _isSendingChat.value = false
            }
        }
    }

    fun addComment(document: FirestoreDocument, text: String, quoteReference: String = "") {
        if (text.isBlank()) return
        val user = Firebase.auth.currentUser
        val authorName = user?.displayName ?: user?.email?.substringBefore("@") ?: "Team Member"
        viewModelScope.launch {
            try {
                docRepository.addComment(
                    documentId = document.id,
                    ownerId = document.ownerId,
                    collaborators = document.collaborators,
                    authorName = authorName,
                    text = text,
                    quoteReference = quoteReference
                )
                auditRepository.logActivity(
                    action = "add_comment",
                    details = "Added collaborative comment: '$text'",
                    documentId = document.id,
                    documentTitle = document.title
                )
                addNotification("New Comment Added", "$authorName commented on '${document.title}'.")
            } catch (e: Exception) {
                Log.e(TAG, "Error posting comment", e)
            }
        }
    }

    fun updateCollaborators(document: FirestoreDocument, collaboratorList: List<String>) {
        viewModelScope.launch {
            try {
                docRepository.updateCollaborators(document.id, collaboratorList)
                _selectedDocument.value = _selectedDocument.value?.copy(collaborators = collaboratorList)
                auditRepository.logActivity(
                    action = "share_permission",
                    details = "Updated team collaborators: ${collaboratorList.joinToString(", ")}",
                    documentId = document.id,
                    documentTitle = document.title
                )
                addNotification("Permissions Updated", "Updated collaboration permissions for ${collaboratorList.size} team member(s).")
            } catch (e: Exception) {
                Log.e(TAG, "Failed updating collaborators", e)
            }
        }
    }

    fun getGoogleDriveFiles(): List<CloudFileItem> = cloudStorageRepository.getGoogleDriveFiles()
    fun getDropboxFiles(): List<CloudFileItem> = cloudStorageRepository.getDropboxFiles()

    fun importCloudFile(fileItem: CloudFileItem) {
        createDocument(
            title = fileItem.name,
            format = fileItem.format,
            content = fileItem.content,
            cloudSource = fileItem.platform,
            pageCount = fileItem.pageCount
        )
        viewModelScope.launch {
            auditRepository.logActivity(
                action = "cloud_import",
                details = "Imported '${fileItem.name}' from ${fileItem.platform.replace("_", " ").uppercase()}"
            )
        }
        addNotification("Cloud Import Succeeded", "'${fileItem.name}' imported from ${fileItem.platform.replace("_", " ")}.")
    }

    fun exportActivityLogs(): String {
        val currentLogs = auditLogs.value
        return auditRepository.exportLogsToJson(currentLogs)
    }
}
