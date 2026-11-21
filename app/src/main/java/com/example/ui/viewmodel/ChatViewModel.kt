package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.media.CloudinaryService
import com.example.data.media.ImgBbService
import com.example.data.media.SafMediaManager
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.DeliveryStatus
import com.example.data.model.DirectoryUser
import com.example.data.model.MessageType
import com.example.data.model.UiState
import com.example.data.prefs.RingtonePreferences
import com.example.data.recovery.MessageOutboxManager
import com.example.data.recovery.PendingOutboxMessage
import com.example.data.repository.ConnectLiveRepository
import com.example.webrtc.WebRtcVoiceTransferManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

class ChatViewModel(
    private val repository: ConnectLiveRepository,
    val conversationId: String,
    val currentUserId: String,
    private val ringtonePreferences: RingtonePreferences? = null,
    private val imgBbService: ImgBbService = ImgBbService(),
    private val cloudinaryService: CloudinaryService = CloudinaryService(),
    private val outboxManager: MessageOutboxManager? = null,
    private val voiceManager: WebRtcVoiceTransferManager? = null
) : ViewModel() {

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isUploadingMedia = MutableStateFlow(false)
    val isUploadingMedia: StateFlow<Boolean> = _isUploadingMedia.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    private val _replyingTo = MutableStateFlow<ChatMessage?>(null)
    val replyingTo: StateFlow<ChatMessage?> = _replyingTo.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _infoBanner = MutableStateFlow<String?>(null)
    val infoBanner: StateFlow<String?> = _infoBanner.asStateFlow()

    // Voice Recording State
    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    private val _recordingElapsedMs = MutableStateFlow(0L)
    val recordingElapsedMs: StateFlow<Long> = _recordingElapsedMs.asStateFlow()

    private var draftFile: File? = null
    private val _hasRecordedDraft = MutableStateFlow(false)
    val hasRecordedDraft: StateFlow<Boolean> = _hasRecordedDraft.asStateFlow()

    private val _recordedDraftDurationMs = MutableStateFlow(0L)
    val recordedDraftDurationMs: StateFlow<Long> = _recordedDraftDurationMs.asStateFlow()

    val playingVoiceId: StateFlow<String?> = voiceManager?.playingId
        ?: MutableStateFlow<String?>(null).asStateFlow()

    val queuedVoiceCount: StateFlow<Int> = voiceManager?.queuedCount
        ?: MutableStateFlow(0).asStateFlow()

    val localVoiceRefreshTick: StateFlow<Int> = voiceManager?.localVoiceFileSaved
        ?: MutableStateFlow(0).asStateFlow()

    private var typingDebounceJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var lastReportedTyping = false

    val conversation: StateFlow<Conversation?> = repository
        .observeConversation(conversationId, currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing conversation", firestoreError ?: error)
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val remoteMessagesFlow = repository.observeMessages(conversationId, currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            throw (firestoreError ?: error)
        }

    val messagesState: StateFlow<UiState<List<ChatMessage>>> = combine(
        remoteMessagesFlow,
        outboxManager?.pendingMessages ?: MutableStateFlow(emptyList())
    ) { remote, pendingAll ->
        val localPending = pendingAll
            .filter { it.conversationId == conversationId }
            .map { p ->
                ChatMessage(
                    id = p.localId,
                    conversationId = p.conversationId,
                    senderId = currentUserId,
                    senderName = p.senderName,
                    text = p.text,
                    imageUrl = p.imageUrl,
                    videoUrl = p.videoUrl,
                    videoThumbUrl = p.videoThumbUrl,
                    videoDurationMs = p.videoDurationMs,
                    deliveryStatus = DeliveryStatus.PENDING_P2P.name,
                    messageType = p.messageType,
                    replyToId = p.replyToId,
                    replyToText = p.replyToText,
                    replyToSenderName = p.replyToSenderName,
                    participantIds = p.participantIds,
                    createdAt = Timestamp(Date(p.createdAtMs))
                )
            }
        UiState.Success<List<ChatMessage>>(remote + localPending) as UiState<List<ChatMessage>>
    }.catch { error ->
        val root = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull() ?: error
        if (root is CancellationException) throw root
        Log.w(TAG, "Error observing messages", root)
        emit(UiState.Error(root.localizedMessage ?: "Failed to load messages"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        outboxManager?.flushAllPending()
    }

    fun updateInput(text: String) {
        _messageInput.value = text
        val typingNow = text.isNotBlank()
        typingDebounceJob?.cancel()
        if (typingNow && !lastReportedTyping) {
            lastReportedTyping = true
            viewModelScope.launch { repository.setTypingStatus(conversationId, true) }
        }
        if (typingNow) {
            typingDebounceJob = viewModelScope.launch {
                delay(2800L)
                if (lastReportedTyping) {
                    lastReportedTyping = false
                    repository.setTypingStatus(conversationId, false)
                }
            }
        } else if (lastReportedTyping) {
            lastReportedTyping = false
            viewModelScope.launch { repository.setTypingStatus(conversationId, false) }
        }
    }

    fun setReplyTarget(message: ChatMessage?) {
        _replyingTo.value = message
    }

    fun dismissError() {
        _errorBanner.value = null
    }

    fun dismissInfo() {
        _infoBanner.value = null
    }

    private fun resolveParticipants(peerIdFallback: String?): List<String>? {
        return conversation.value?.participantIds?.takeIf { it.size == 2 }
            ?: peerIdFallback?.takeIf { it.isNotBlank() && it != currentUserId }
                ?.let { listOf(currentUserId, it).sorted() }
            ?: conversationId.split("_").takeIf {
                it.size == 2 && it.all { part -> part.isNotBlank() } && currentUserId in it
            }?.sorted()
    }

    private fun buildReplyPreview(reply: ChatMessage?): String = when {
        reply == null -> ""
        reply.parsedType == MessageType.IMAGE -> reply.text.ifBlank { "📷 Photo" }
        reply.parsedType == MessageType.VIDEO -> reply.text.ifBlank { "🎬 Video" }
        reply.parsedType == MessageType.VOICE -> "🎤 Voice message"
        else -> reply.text
    }

    fun sendMessage(
        senderName: String,
        peerIdFallback: String? = null,
        senderPhoto: String = ""
    ) {
        val text = _messageInput.value.trim()
        if (text.isEmpty() || _isSending.value) return

        val participants = resolveParticipants(peerIdFallback)
        if (participants == null) {
            _errorBanner.value = "Still loading conversation. Please try again in a moment."
            return
        }

        val conv = conversation.value
        if (conv != null && conv.isDmLimitReached(currentUserId)) {
            _errorBanner.value = "3-message DM limit reached. Unlocks when they reply or accept your friend request."
            return
        }

        val reply = _replyingTo.value
        val replyPreview = buildReplyPreview(reply)
        typingDebounceJob?.cancel()
        lastReportedTyping = false

        viewModelScope.launch {
            _isSending.value = true
            _errorBanner.value = null
            _messageInput.value = ""
            _replyingTo.value = null

            val isOnline = outboxManager?.checkCurrentlyOnline() ?: true
            if (!isOnline && outboxManager != null) {
                outboxManager.enqueueMessage(
                    PendingOutboxMessage(
                        localId = outboxManager.newLocalId(),
                        conversationId = conversationId,
                        participantIds = participants,
                        senderName = senderName,
                        senderPhoto = senderPhoto,
                        text = text,
                        messageType = MessageType.TEXT.name,
                        replyToId = reply?.id.orEmpty(),
                        replyToText = replyPreview,
                        replyToSenderName = reply?.senderName.orEmpty()
                    )
                )
                _infoBanner.value = "Offline — queued and will auto-send when back online."
                _isSending.value = false
                return@launch
            }

            val result = repository.sendMessage(
                conversationId = conversationId,
                participantIds = participants,
                senderName = senderName,
                text = text,
                messageType = MessageType.TEXT,
                replyToId = reply?.id.orEmpty(),
                replyToText = replyPreview,
                replyToSenderName = reply?.senderName.orEmpty(),
                senderPhoto = senderPhoto
            )
            _isSending.value = false
            if (result.isFailure) {
                val errMsg = result.exceptionOrNull()?.localizedMessage ?: "Failed to send message."
                if (outboxManager != null && !errMsg.contains("limit", ignoreCase = true)) {
                    outboxManager.enqueueMessage(
                        PendingOutboxMessage(
                            localId = outboxManager.newLocalId(),
                            conversationId = conversationId,
                            participantIds = participants,
                            senderName = senderName,
                            senderPhoto = senderPhoto,
                            text = text,
                            messageType = MessageType.TEXT.name,
                            replyToId = reply?.id.orEmpty(),
                            replyToText = replyPreview,
                            replyToSenderName = reply?.senderName.orEmpty()
                        )
                    )
                    _infoBanner.value = "Connection unstable — queued for automatic retry."
                } else {
                    _messageInput.value = text
                    _replyingTo.value = reply
                    _errorBanner.value = errMsg
                }
            }
        }
    }

    fun uploadAndSendImage(
        context: Context,
        uri: Uri,
        senderName: String,
        peerIdFallback: String? = null,
        senderPhoto: String = ""
    ) {
        if (_isUploadingMedia.value) return
        val participants = resolveParticipants(peerIdFallback)
        if (participants == null) {
            _errorBanner.value = "Still loading conversation. Please try again."
            return
        }

        val caption = _messageInput.value.trim()
        val reply = _replyingTo.value
        val replyPreview = buildReplyPreview(reply)
        viewModelScope.launch {
            _isUploadingMedia.value = true
            _uploadProgress.value = 0.25f
            _errorBanner.value = null
            val config = ringtonePreferences?.getConfig()
            val safUri = config?.safTreeUri.orEmpty()
            if (safUri.isNotBlank()) {
                SafMediaManager.copyUriToSafFolder(
                    context = context,
                    safTreeUri = safUri,
                    subfolder = SafMediaManager.SUB_IMAGES,
                    sourceUri = uri,
                    fileName = "chat_img_${System.currentTimeMillis()}.jpg",
                    mimeType = "image/jpeg"
                )
            }
            val uploadRes = imgBbService.uploadImage(
                context = context,
                uri = uri,
                namePrefix = "chat_$conversationId",
                overrideApiKey = config?.customImgBbKey.orEmpty()
            )
            uploadRes.onSuccess { uploaded ->
                _uploadProgress.value = 1f
                _messageInput.value = ""
                _replyingTo.value = null
                val sendRes = repository.sendMessage(
                    conversationId = conversationId,
                    participantIds = participants,
                    senderName = senderName,
                    text = caption,
                    messageType = MessageType.IMAGE,
                    imageUrl = uploaded.url,
                    replyToId = reply?.id.orEmpty(),
                    replyToText = replyPreview,
                    replyToSenderName = reply?.senderName.orEmpty(),
                    senderPhoto = senderPhoto
                )
                if (sendRes.isFailure) {
                    if (outboxManager != null) {
                        outboxManager.enqueueMessage(
                            PendingOutboxMessage(
                                localId = outboxManager.newLocalId(),
                                conversationId = conversationId,
                                participantIds = participants,
                                senderName = senderName,
                                senderPhoto = senderPhoto,
                                text = caption,
                                messageType = MessageType.IMAGE.name,
                                imageUrl = uploaded.url,
                                replyToId = reply?.id.orEmpty(),
                                replyToText = replyPreview,
                                replyToSenderName = reply?.senderName.orEmpty()
                            )
                        )
                        _infoBanner.value = "Photo queued for delivery recovery."
                    } else {
                        _errorBanner.value = sendRes.exceptionOrNull()?.localizedMessage ?: "Failed to send image."
                    }
                }
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Image upload to ImgBB failed."
            }
            _isUploadingMedia.value = false
            _uploadProgress.value = 0f
        }
    }

    fun uploadAndSendVideo(
        context: Context,
        uri: Uri,
        senderName: String,
        peerIdFallback: String? = null,
        senderPhoto: String = ""
    ) {
        if (_isUploadingMedia.value) return
        val participants = resolveParticipants(peerIdFallback)
        if (participants == null) {
            _errorBanner.value = "Still loading conversation. Please try again."
            return
        }

        val caption = _messageInput.value.trim()
        val reply = _replyingTo.value
        val replyPreview = buildReplyPreview(reply)

        viewModelScope.launch {
            _isUploadingMedia.value = true
            _uploadProgress.value = 0.05f
            _errorBanner.value = null
            val config = ringtonePreferences?.getConfig()
            val safUri = config?.safTreeUri.orEmpty()
            if (safUri.isNotBlank()) {
                SafMediaManager.copyUriToSafFolder(
                    context = context,
                    safTreeUri = safUri,
                    subfolder = SafMediaManager.SUB_VIDEOS,
                    sourceUri = uri,
                    fileName = "chat_vid_${System.currentTimeMillis()}.mp4",
                    mimeType = "video/mp4"
                )
            }

            val uploadRes = cloudinaryService.uploadVideo(
                context = context,
                videoUri = uri,
                overrideCloudName = config?.cloudinaryCloudName.orEmpty(),
                overridePreset = config?.cloudinaryUploadPreset.orEmpty(),
                onProgress = { prog -> _uploadProgress.value = prog }
            )
            uploadRes.onSuccess { uploaded ->
                _messageInput.value = ""
                _replyingTo.value = null
                val sendRes = repository.sendMessage(
                    conversationId = conversationId,
                    participantIds = participants,
                    senderName = senderName,
                    text = caption,
                    messageType = MessageType.VIDEO,
                    videoUrl = uploaded.videoUrl,
                    videoThumbUrl = uploaded.thumbnailUrl,
                    videoDurationMs = uploaded.durationMs,
                    replyToId = reply?.id.orEmpty(),
                    replyToText = replyPreview,
                    replyToSenderName = reply?.senderName.orEmpty(),
                    senderPhoto = senderPhoto
                )
                if (sendRes.isFailure) {
                    if (outboxManager != null) {
                        outboxManager.enqueueMessage(
                            PendingOutboxMessage(
                                localId = outboxManager.newLocalId(),
                                conversationId = conversationId,
                                participantIds = participants,
                                senderName = senderName,
                                senderPhoto = senderPhoto,
                                text = caption,
                                messageType = MessageType.VIDEO.name,
                                videoUrl = uploaded.videoUrl,
                                videoThumbUrl = uploaded.thumbnailUrl,
                                videoDurationMs = uploaded.durationMs,
                                replyToId = reply?.id.orEmpty(),
                                replyToText = replyPreview,
                                replyToSenderName = reply?.senderName.orEmpty()
                            )
                        )
                        _infoBanner.value = "Video uploaded & queued for delivery."
                    } else {
                        _errorBanner.value = sendRes.exceptionOrNull()?.localizedMessage ?: "Failed to send video."
                    }
                }
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Video upload to Cloudinary failed."
            }
            _isUploadingMedia.value = false
            _uploadProgress.value = 0f
        }
    }

    // --- Voice Recording & WebRTC P2P Transfer ---

    fun startVoiceRecording() {
        val mgr = voiceManager ?: return
        clearDraftInternal()
        val file = mgr.startRecording()
        if (file == null) {
            _errorBanner.value = "Microphone unavailable or permission missing."
            return
        }
        _isRecordingVoice.value = true
        _recordingElapsedMs.value = 0L
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            while (isActive && _isRecordingVoice.value) {
                _recordingElapsedMs.value = System.currentTimeMillis() - start
                delay(150L)
            }
        }
    }

    fun stopVoiceRecordingForReview() {
        val mgr = voiceManager ?: return
        recordingTimerJob?.cancel()
        _isRecordingVoice.value = false
        val result = mgr.stopRecording()
        if (result != null) {
            draftFile = result.first
            _recordedDraftDurationMs.value = result.second
            _hasRecordedDraft.value = true
        } else {
            _hasRecordedDraft.value = false
            _errorBanner.value = "Voice recording was too short."
        }
    }

    fun restartVoiceRecording() {
        cancelVoiceRecordingOrDraft()
        startVoiceRecording()
    }

    fun cancelVoiceRecordingOrDraft() {
        recordingTimerJob?.cancel()
        _isRecordingVoice.value = false
        _recordingElapsedMs.value = 0L
        voiceManager?.cancelRecording()
        clearDraftInternal()
    }

    private fun clearDraftInternal() {
        runCatching { draftFile?.delete() }
        draftFile = null
        _hasRecordedDraft.value = false
        _recordedDraftDurationMs.value = 0L
    }

    fun sendVoiceNote(
        senderName: String,
        peerIdFallback: String? = null,
        senderPhoto: String = ""
    ) {
        val mgr = voiceManager ?: return
        val participants = resolveParticipants(peerIdFallback)
        if (participants == null) {
            _errorBanner.value = "Conversation still loading."
            return
        }

        recordingTimerJob?.cancel()
        val pair: Pair<File, Long>? = if (_isRecordingVoice.value) {
            _isRecordingVoice.value = false
            mgr.stopRecording()
        } else {
            val f = draftFile
            if (f != null && f.exists()) f to _recordedDraftDurationMs.value else null
        }

        if (pair == null) {
            _errorBanner.value = "No voice recording to send."
            return
        }

        val reply = _replyingTo.value
        val replyPreview = buildReplyPreview(reply)
        draftFile = null
        _hasRecordedDraft.value = false
        _recordedDraftDurationMs.value = 0L
        _replyingTo.value = null

        viewModelScope.launch {
            _isSending.value = true
            val res = mgr.sendVoiceNote(
                conversationId = conversationId,
                participantIds = participants,
                senderName = senderName,
                senderPhoto = senderPhoto,
                recordedFile = pair.first,
                durationMs = pair.second,
                replyToId = reply?.id.orEmpty(),
                replyToText = replyPreview,
                replyToSenderName = reply?.senderName.orEmpty()
            )
            _isSending.value = false
            if (res.isFailure) {
                _errorBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Unable to send voice message."
            } else {
                _infoBanner.value = "Voice note sent via WebRTC P2P (queues automatically if friend is offline)."
            }
        }
    }

    fun getVoiceFile(transferId: String): File? =
        voiceManager?.getVoiceFileForTransfer(transferId)

    fun toggleVoicePlayback(message: ChatMessage) {
        val mgr = voiceManager ?: return
        val file = mgr.getVoiceFileForTransfer(message.voiceTransferId)
        if (file == null) {
            _infoBanner.value = "Waiting for WebRTC P2P voice transfer from sender when both are online."
            return
        }
        mgr.togglePlayback(message.id, file)
    }

    fun backupChatToFolder(context: Context) {
        val messages = (messagesState.value as? UiState.Success)?.data ?: emptyList()
        viewModelScope.launch {
            val safUri = ringtonePreferences?.getConfig()?.safTreeUri.orEmpty()
            val res = SafMediaManager.backupConversationMessages(
                context = context,
                safTreeUri = safUri,
                conversationId = conversationId,
                messages = messages
            )
            res.onSuccess { msg ->
                _infoBanner.value = msg
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Chat backup failed."
            }
        }
    }

    fun uploadAndSetWallpaper(context: Context, uri: Uri) {
        if (_isUploadingMedia.value) return
        viewModelScope.launch {
            _isUploadingMedia.value = true
            _errorBanner.value = null
            val config = ringtonePreferences?.getConfig()
            val safUri = config?.safTreeUri.orEmpty()
            if (safUri.isNotBlank()) {
                SafMediaManager.copyUriToSafFolder(
                    context = context,
                    safTreeUri = safUri,
                    subfolder = SafMediaManager.SUB_IMAGES,
                    sourceUri = uri,
                    fileName = "wallpaper_${System.currentTimeMillis()}.jpg",
                    mimeType = "image/jpeg"
                )
            }
            val uploadRes = imgBbService.uploadImage(
                context = context,
                uri = uri,
                namePrefix = "wallpaper_$conversationId",
                overrideApiKey = config?.customImgBbKey.orEmpty()
            )
            uploadRes.onSuccess { uploaded ->
                val res = repository.setConversationWallpaper(conversationId, uploaded.url)
                if (res.isFailure) {
                    _errorBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save wallpaper."
                }
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Failed to upload wallpaper."
            }
            _isUploadingMedia.value = false
        }
    }

    fun setWallpaperPreset(presetKey: String) {
        viewModelScope.launch {
            val res = repository.setConversationWallpaper(conversationId, presetKey)
            if (res.isFailure) {
                _errorBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update chat background."
            }
        }
    }

    fun toggleReaction(message: ChatMessage, emoji: String) {
        if (message.id.startsWith("outbox_")) return
        viewModelScope.launch {
            repository.toggleMessageReaction(conversationId, message, emoji)
        }
    }

    fun deleteMessage(message: ChatMessage) {
        if (message.id.startsWith("outbox_")) {
            viewModelScope.launch { outboxManager?.removeMessage(message.id) }
            return
        }
        viewModelScope.launch {
            val res = repository.deleteMessage(conversationId, message.id)
            if (res.isFailure) {
                _errorBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Unable to delete message."
            }
        }
    }

    fun peerAsDirectoryUser(): DirectoryUser? {
        val conv = conversation.value ?: return null
        val peerId = conv.peerUid(currentUserId).takeIf { it.isNotBlank() } ?: return null
        return DirectoryUser(
            uid = peerId,
            displayName = conv.peerName(currentUserId),
            displayNameLower = conv.peerName(currentUserId).lowercase(),
            photoUrl = conv.peerPhoto(currentUserId)
        )
    }

    override fun onCleared() {
        typingDebounceJob?.cancel()
        recordingTimerJob?.cancel()
        voiceManager?.stopPlayback()
        cancelVoiceRecordingOrDraft()
        super.onCleared()
    }

    private companion object {
        const val TAG = "ChatViewModel"
    }
}
