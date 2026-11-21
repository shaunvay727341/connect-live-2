package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.media.CloudinaryService
import com.example.data.media.ImgBbService
import com.example.data.media.SafMediaManager
import com.example.data.model.AppNotification
import com.example.data.model.AppNotificationType
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.ConnectNumberEntry
import com.example.data.model.Conversation
import com.example.data.model.DirectoryUser
import com.example.data.model.FollowRelation
import com.example.data.model.FriendRequest
import com.example.data.model.FriendRequestStatus
import com.example.data.model.Friendship
import com.example.data.model.UiState
import com.example.data.model.UserPrivate
import com.example.data.prefs.ChatBubbleStyle
import com.example.data.prefs.DialToneMode
import com.example.data.prefs.IncomingToneMode
import com.example.data.prefs.RingtoneConfig
import com.example.data.prefs.RingtonePreferences
import com.example.data.repository.ConnectLiveRepository
import com.example.data.repository.SocialAndMediaRepository
import com.example.data.turn.IceConfigResult
import com.example.data.turn.TurnCredentialsService
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: ConnectLiveRepository,
    private val socialRepository: SocialAndMediaRepository,
    private val turnService: TurnCredentialsService,
    private val ringtonePreferences: RingtonePreferences,
    val currentUserId: String,
    private val imgBbService: ImgBbService = ImgBbService(),
    private val cloudinaryService: CloudinaryService = CloudinaryService()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val ringtoneConfig: StateFlow<RingtoneConfig> = ringtonePreferences.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), RingtoneConfig())

    val isDarkTheme: StateFlow<Boolean> = ringtoneConfig
        .map { it.darkThemeEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _turnDiagnostic = MutableStateFlow<IceConfigResult?>(null)
    val turnDiagnostic: StateFlow<IceConfigResult?> = _turnDiagnostic.asStateFlow()

    private val _isSavingProfile = MutableStateFlow(false)
    val isSavingProfile: StateFlow<Boolean> = _isSavingProfile.asStateFlow()

    private val _isUploadingAvatar = MutableStateFlow(false)
    val isUploadingAvatar: StateFlow<Boolean> = _isUploadingAvatar.asStateFlow()

    private val _lookupLoading = MutableStateFlow(false)
    val lookupLoading: StateFlow<Boolean> = _lookupLoading.asStateFlow()

    private val _lookupResult = MutableStateFlow<ConnectNumberEntry?>(null)
    val lookupResult: StateFlow<ConnectNumberEntry?> = _lookupResult.asStateFlow()

    private val _lookupError = MutableStateFlow<String?>(null)
    val lookupError: StateFlow<String?> = _lookupError.asStateFlow()

    val currentUserPrivate: StateFlow<UserPrivate?> = repository.observeCurrentUserPrivate(currentUserId)
        .catch { error ->
            if (unwrapFirestoreOrRethrow(error, "observeCurrentUserPrivate") == null) return@catch
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val currentDirectoryUser: StateFlow<DirectoryUser?> = repository.observeCurrentDirectoryUser(currentUserId)
        .catch { error ->
            if (unwrapFirestoreOrRethrow(error, "observeCurrentDirectoryUser") == null) return@catch
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val rawContacts: StateFlow<UiState<List<DirectoryUser>>> = repository
        .observeDirectoryUsers(currentUserId)
        .toUiState("observeDirectoryUsers")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val filteredContacts: StateFlow<UiState<List<DirectoryUser>>> = combine(
        rawContacts,
        _searchQuery
    ) { contactsState, query ->
        when (contactsState) {
            is UiState.Success -> {
                val trimmed = query.trim().lowercase()
                val list = if (trimmed.isEmpty()) {
                    contactsState.data
                } else {
                    contactsState.data.filter {
                        it.displayNameLower.contains(trimmed) ||
                            it.statusText.lowercase().contains(trimmed) ||
                            it.connectNumber.contains(trimmed)
                    }
                }
                UiState.Success(list)
            }
            else -> contactsState
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val friendRequestsState: StateFlow<UiState<List<FriendRequest>>> = repository
        .observeFriendRequests(currentUserId)
        .toUiState("observeFriendRequests")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val incomingPendingRequestsCount: StateFlow<Int> = friendRequestsState
        .map { state ->
            (state as? UiState.Success)?.data?.count { req ->
                req.toUid == currentUserId && req.parsedStatus == FriendRequestStatus.PENDING
            } ?: 0
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val friendshipsState: StateFlow<UiState<List<Friendship>>> = repository
        .observeFriendships(currentUserId)
        .toUiState("observeFriendships")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val followingRelationsState: StateFlow<List<FollowRelation>> = socialRepository
        .observeFollowingRelations(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val followingIds: StateFlow<Set<String>> = followingRelationsState
        .map { list -> list.map { it.targetUid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val notificationsState: StateFlow<UiState<List<AppNotification>>> = socialRepository
        .observeNotifications(currentUserId)
        .toUiState("observeNotifications")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val unreadNotificationsCount: StateFlow<Int> = notificationsState
        .map { state ->
            (state as? UiState.Success)?.data?.count { !it.read } ?: 0
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val conversationsState: StateFlow<UiState<List<Conversation>>> = combine(
        repository.observeConversations(currentUserId).toUiState("observeConversations"),
        _searchQuery
    ) { state, query ->
        when (state) {
            is UiState.Success -> {
                val trimmed = query.trim().lowercase()
                val filtered = if (trimmed.isEmpty()) {
                    state.data
                } else {
                    state.data.filter {
                        it.peerName(currentUserId).lowercase().contains(trimmed) ||
                            it.lastMessageText.lowercase().contains(trimmed)
                    }
                }
                UiState.Success(filtered)
            }
            else -> state
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val callsState: StateFlow<UiState<List<CallSession>>> = repository
        .observeCalls(currentUserId)
        .toUiState("observeCalls")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val incomingCall: StateFlow<CallSession?> = callsState
        .map { state ->
            (state as? UiState.Success)?.data?.firstOrNull { call ->
                call.calleeId == currentUserId && call.parsedStatus == CallStatus.RINGING
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val missedCallsCount: StateFlow<Int> = callsState
        .map { state ->
            (state as? UiState.Success)?.data?.count { call ->
                call.calleeId == currentUserId && call.parsedStatus == CallStatus.MISSED
            } ?: 0
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    init {
        viewModelScope.launch {
            val initResult = repository.ensureUserProfileInitialized()
            if (initResult.isFailure) {
                _statusMessage.value = initResult.exceptionOrNull()?.localizedMessage
                    ?: "Failed to initialize profile."
            }
        }
        refreshTurnDiagnostics()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun showStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun toggleDarkTheme(isDark: Boolean) {
        viewModelScope.launch {
            ringtonePreferences.setDarkThemeEnabled(isDark)
        }
    }

    fun setTouchSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            ringtonePreferences.setTouchSoundEnabled(enabled)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            ringtonePreferences.setHapticsEnabled(enabled)
        }
    }

    fun setCustomImgBbKey(key: String) {
        viewModelScope.launch {
            ringtonePreferences.setCustomImgBbKey(key)
            _statusMessage.value = if (key.isBlank()) {
                "Custom ImgBB key cleared"
            } else {
                "ImgBB key saved"
            }
        }
    }

    fun setCloudinaryConfig(cloudName: String, uploadPreset: String) {
        viewModelScope.launch {
            ringtonePreferences.setCloudinaryConfig(cloudName, uploadPreset)
            _statusMessage.value = if (cloudName.isBlank() || uploadPreset.isBlank()) {
                "Cloudinary settings cleared"
            } else {
                "Cloudinary video hosting configured"
            }
        }
    }

    fun setSafTreeUri(uriString: String) {
        viewModelScope.launch {
            ringtonePreferences.setSafTreeUri(uriString)
            if (uriString.isNotBlank()) {
                _statusMessage.value = "Media & Backup folder connected!"
            }
        }
    }

    fun setChatBubbleStyle(style: ChatBubbleStyle) {
        viewModelScope.launch {
            ringtonePreferences.setChatBubbleStyle(style)
            _statusMessage.value = "Chat theme: ${style.label}"
        }
    }

    fun setDefaultChatBgPreset(preset: String) {
        viewModelScope.launch {
            ringtonePreferences.setDefaultChatBgPreset(preset)
            _statusMessage.value = "Default chat background updated"
        }
    }

    fun setIncomingCallBackgroundUri(uriOrUrl: String) {
        viewModelScope.launch {
            ringtonePreferences.setIncomingCallBackgroundUri(uriOrUrl)
            _statusMessage.value = if (uriOrUrl.isBlank()) {
                "Incoming call screen background reset"
            } else {
                "Incoming call screen background updated"
            }
        }
    }

    fun setBiometricSettings(enabled: Boolean, allowPinFallback: Boolean) {
        viewModelScope.launch {
            ringtonePreferences.setBiometricSettings(enabled, allowPinFallback)
            _statusMessage.value = if (enabled) {
                "Fingerprint lock enabled"
            } else {
                "Fingerprint lock disabled"
            }
        }
    }

    fun hasCloudImageUploadConfigured(): Boolean =
        imgBbService.hasAnyCloudUploadConfigured(ringtoneConfig.value.customImgBbKey)

    fun hasCloudinaryConfigured(): Boolean =
        cloudinaryService.isConfigured(
            overrideCloudName = ringtoneConfig.value.cloudinaryCloudName,
            overridePreset = ringtoneConfig.value.cloudinaryUploadPreset
        )

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearLookupState() {
        _lookupResult.value = null
        _lookupError.value = null
        _lookupLoading.value = false
    }

    fun setOnlinePresence(online: Boolean) {
        viewModelScope.launch {
            repository.updatePresence(online)
        }
    }

    suspend fun markOfflineBeforeSignOut() {
        repository.updatePresence(false)
    }

    fun refreshTurnDiagnostics() {
        viewModelScope.launch {
            _turnDiagnostic.value = turnService.fetchIceServers()
        }
    }

    fun updateProfile(
        displayName: String,
        statusText: String,
        photoUrl: String,
        discoverable: Boolean,
        bio: String? = null,
        location: String? = null,
        birthday: String? = null,
        gender: String? = null
    ) {
        viewModelScope.launch {
            _isSavingProfile.value = true
            val connectNum = currentUserPrivate.value?.connectNumber
                ?: currentDirectoryUser.value?.connectNumber
                ?: ""
            val result = repository.updateProfile(
                displayName = displayName,
                statusText = statusText,
                photoUrl = photoUrl,
                discoverable = discoverable,
                connectNumber = connectNum,
                bio = bio,
                location = location,
                birthday = birthday,
                gender = gender
            )
            _isSavingProfile.value = false
            _statusMessage.value = if (result.isSuccess) {
                "Profile updated"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Unable to update profile"
            }
        }
    }

    fun uploadAndSetProfilePhoto(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isUploadingAvatar.value = true
            val config = ringtoneConfig.value
            val safUri = config.safTreeUri
            if (safUri.isNotBlank()) {
                SafMediaManager.copyUriToSafFolder(
                    context = context,
                    safTreeUri = safUri,
                    subfolder = SafMediaManager.SUB_IMAGES,
                    sourceUri = uri,
                    fileName = "avatar_${System.currentTimeMillis()}.jpg",
                    mimeType = "image/jpeg"
                )
            }
            val uploadRes = imgBbService.uploadImage(
                context = context,
                uri = uri,
                namePrefix = "avatar_$currentUserId",
                overrideApiKey = config.customImgBbKey,
                allowLocalAvatarFallback = true
            )
            uploadRes.onSuccess { uploaded ->
                val curDir = currentDirectoryUser.value
                val curPriv = currentUserPrivate.value
                val name = curDir?.displayName ?: curPriv?.displayName ?: "Connect User"
                val status = curDir?.statusText ?: "Available on Connect Live"
                val discoverable = curDir?.discoverable ?: true
                val connectNum = curPriv?.connectNumber ?: curDir?.connectNumber ?: ""
                val saveRes = repository.updateProfile(name, status, uploaded.url, discoverable, connectNum)
                _statusMessage.value = if (saveRes.isSuccess) {
                    if (uploaded.usedLocalFallback) {
                        "Profile photo updated! Add an ImgBB key in Media Settings for full HD cloud photos."
                    } else {
                        "Profile photo uploaded & synced!"
                    }
                } else {
                    saveRes.exceptionOrNull()?.localizedMessage ?: "Photo uploaded, failed to save profile"
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to update profile photo"
            }
            _isUploadingAvatar.value = false
        }
    }

    fun lookupByConnectNumber(rawNumber: String) {
        viewModelScope.launch {
            _lookupLoading.value = true
            _lookupError.value = null
            _lookupResult.value = null
            val res = repository.lookupUserByConnectNumber(rawNumber)
            _lookupLoading.value = false
            res.onSuccess { entry ->
                if (entry == null) {
                    _lookupError.value = "No user registered with number $rawNumber."
                } else {
                    _lookupResult.value = entry
                }
            }.onFailure { err ->
                _lookupError.value = err.localizedMessage ?: "Lookup failed."
            }
        }
    }

    fun sendFriendRequestToEntry(entry: ConnectNumberEntry) {
        viewModelScope.launch {
            val me = buildCurrentDirectoryUser()
            val res = repository.sendFriendRequest(me, entry)
            if (res.isSuccess && entry.uid.isNotBlank() && entry.uid != currentUserId) {
                socialRepository.createAppNotification(
                    recipientUid = entry.uid,
                    actor = me,
                    type = AppNotificationType.FRIEND_REQUEST,
                    title = "Friend Request",
                    body = "${me.displayName} sent you a friend request",
                    targetId = currentUserId
                )
            }
            _statusMessage.value = res.getOrElse {
                it.localizedMessage ?: "Unable to send friend request"
            }
        }
    }

    fun sendFriendRequestToDirectoryUser(peer: DirectoryUser) {
        val entry = ConnectNumberEntry(
            number = peer.connectNumber,
            uid = peer.uid,
            displayName = peer.displayName,
            photoUrl = peer.photoUrl
        )
        sendFriendRequestToEntry(entry)
    }

    fun respondToFriendRequest(
        request: FriendRequest,
        accept: Boolean,
        onAcceptedConversation: (convId: String, peer: DirectoryUser) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = repository.respondToFriendRequest(request, accept)
            res.onSuccess { convId ->
                if (accept && convId != null) {
                    _statusMessage.value = "Added ${request.fromName} as friend!"
                    val peer = DirectoryUser(
                        uid = request.fromUid,
                        displayName = request.fromName,
                        displayNameLower = request.fromName.lowercase(),
                        photoUrl = request.fromPhoto,
                        connectNumber = request.fromNumber
                    )
                    onAcceptedConversation(convId, peer)
                } else {
                    _statusMessage.value = "Request declined"
                }
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Unable to respond to request"
            }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            socialRepository.markNotificationRead(notificationId)
        }
    }

    fun markAllNotificationsRead() {
        val list = (notificationsState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            socialRepository.markAllNotificationsRead(list)
        }
    }

    fun setIncomingRingtone(mode: IncomingToneMode, uri: String = "", label: String = mode.label) {
        viewModelScope.launch {
            ringtonePreferences.setIncomingTone(mode, uri, label)
            _statusMessage.value = "Incoming ringtone: $label"
        }
    }

    fun setDialTone(mode: DialToneMode, uri: String = "", label: String = mode.label) {
        viewModelScope.launch {
            ringtonePreferences.setDialTone(mode, uri, label)
            _statusMessage.value = "Dial tone: $label"
        }
    }

    fun buildCurrentDirectoryUser(): DirectoryUser {
        val dir = currentDirectoryUser.value
        val priv = currentUserPrivate.value
        return dir ?: DirectoryUser(
            uid = currentUserId,
            displayName = priv?.displayName ?: "Me",
            displayNameLower = (priv?.displayName ?: "me").lowercase(),
            photoUrl = priv?.photoUrl ?: "",
            connectNumber = priv?.connectNumber ?: ""
        )
    }

    suspend fun openConversationWith(
        peer: DirectoryUser,
        isFeedDirectMessage: Boolean = false
    ): Result<String> {
        val res = repository.getOrCreateConversation(
            currentUser = buildCurrentDirectoryUser(),
            peer = peer,
            isFeedDirectMessage = isFeedDirectMessage
        )
        if (res.isFailure) {
            _statusMessage.value = res.exceptionOrNull()?.localizedMessage
                ?: "Add ${peer.displayName} as a friend first to chat."
        }
        return res
    }

    suspend fun rejectIncomingCall(callId: String) {
        val result = repository.rejectCall(callId)
        if (result.isFailure) {
            _statusMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to decline call"
        }
    }

    private fun <T> Flow<T>.toUiState(label: String): Flow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreOrRethrow(error, label) ?: return@catch
                emit(UiState.Error(root.localizedMessage ?: "Failed to load data"))
            }

    private fun unwrapFirestoreOrRethrow(error: Throwable, label: String): Throwable? {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) {
            throw error
        }
        val rootError = firestoreError ?: error
        Log.w(TAG, "Stream error in $label", rootError)
        return rootError
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
