package com.example.call

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.DirectoryUser
import com.example.data.model.MessageType
import com.example.data.prefs.RingtoneAudioController
import com.example.data.prefs.RingtonePreferences
import com.example.data.repository.ConnectLiveRepository
import com.example.data.turn.TurnCredentialsService
import com.example.service.ActiveCallService
import com.example.service.CallNotifications
import com.example.ui.viewmodel.ActiveCallUiState
import com.example.ui.viewmodel.CallPhase
import com.example.webrtc.WebRtcCallManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer

class CallEngine private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        appContext.getString(R.string.firestore_database_id)
    )
    private val repository = ConnectLiveRepository(db)
    private val turnService = TurnCredentialsService()
    private val ringtonePrefs = RingtonePreferences(appContext)
    private val dialToneController = RingtoneAudioController(appContext)

    private val _uiState = MutableStateFlow(ActiveCallUiState())
    val uiState: StateFlow<ActiveCallUiState> = _uiState.asStateFlow()

    private val _incomingRingingCall = MutableStateFlow<CallSession?>(null)
    val incomingRingingCall: StateFlow<CallSession?> = _incomingRingingCall.asStateFlow()

    @Volatile
    var isAppInForeground: Boolean = false

    private var webRtcManager: WebRtcCallManager? = null
    private var callDocJob: Job? = null
    private var iceCandidatesJob: Job? = null
    private var timerJob: Job? = null
    private var ringingTimeoutJob: Job? = null
    private var reconnectJob: Job? = null

    private var remoteAnswerApplied = false
    private var pendingOutgoingCaller: DirectoryUser? = null
    private var pendingOutgoingCallee: DirectoryUser? = null
    private var pendingOutgoingType: CallType? = null
    private var pendingIncomingSession: CallSession? = null

    fun eglContext(): EglBase.Context? = webRtcManager?.eglBase?.eglBaseContext

    fun updateIncomingRingingCall(session: CallSession?) {
        _incomingRingingCall.value = session
    }

    fun hasActiveOrPendingCall(): Boolean {
        val phase = _uiState.value.phase
        return phase != CallPhase.IDLE && phase != CallPhase.ENDED
    }

    fun setMinimizedInApp(minimized: Boolean) {
        _uiState.update { it.copy(isMinimizedInApp = minimized) }
    }

    fun acceptIncomingFromIntent(callId: String?, hasRequiredPermissions: Boolean): Boolean {
        val session = _incomingRingingCall.value
            ?.takeIf { callId.isNullOrBlank() || it.id == callId }
            ?: return false
        prepareIncomingCallAcceptance(session, hasRequiredPermissions)
        return true
    }

    fun prepareOutgoingCall(
        caller: DirectoryUser,
        callee: DirectoryUser,
        callType: CallType,
        hasRequiredPermissions: Boolean
    ) {
        cleanupCallResources()
        pendingIncomingSession = null
        pendingOutgoingCaller = caller
        pendingOutgoingCallee = callee
        pendingOutgoingType = callType

        _uiState.value = ActiveCallUiState(
            peerId = callee.uid,
            peerName = callee.displayName,
            peerPhoto = callee.photoUrl,
            callType = callType,
            phase = if (hasRequiredPermissions) CallPhase.INITIALIZING else CallPhase.REQUESTING_PERMISSIONS,
            isCaller = true,
            isSpeakerOn = callType == CallType.VIDEO,
            isCameraEnabled = callType == CallType.VIDEO
        )

        if (hasRequiredPermissions) {
            launchOutgoingCall(caller, callee, callType)
        }
    }

    fun prepareIncomingCallAcceptance(
        session: CallSession,
        hasRequiredPermissions: Boolean
    ) {
        CallNotifications.cancelIncomingCallNotification(appContext)
        _incomingRingingCall.value = null
        cleanupCallResources()
        pendingOutgoingCaller = null
        pendingOutgoingCallee = null
        pendingOutgoingType = null
        pendingIncomingSession = session

        _uiState.value = ActiveCallUiState(
            callId = session.id,
            peerId = session.callerId,
            peerName = session.callerName,
            peerPhoto = session.callerPhoto,
            callType = session.parsedType,
            phase = if (hasRequiredPermissions) CallPhase.CONNECTING else CallPhase.REQUESTING_PERMISSIONS,
            isCaller = false,
            isSpeakerOn = session.parsedType == CallType.VIDEO,
            isCameraEnabled = session.parsedType == CallType.VIDEO
        )

        if (hasRequiredPermissions) {
            launchIncomingAcceptance(session)
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        if (!granted) {
            _uiState.update {
                it.copy(
                    phase = CallPhase.REQUESTING_PERMISSIONS,
                    errorMessage = "Microphone and Camera permissions are required to place or answer this call."
                )
            }
            return
        }

        _uiState.update { it.copy(errorMessage = null) }
        val incoming = pendingIncomingSession
        val caller = pendingOutgoingCaller
        val callee = pendingOutgoingCallee
        val callType = pendingOutgoingType
        if (incoming != null) {
            launchIncomingAcceptance(incoming)
        } else if (caller != null && callee != null && callType != null) {
            launchOutgoingCall(caller, callee, callType)
        }
    }

    private fun startOngoingCallForegroundService(peerName: String, isVideo: Boolean, statusText: String) {
        try {
            val intent = Intent(appContext, ActiveCallService::class.java).apply {
                action = ActiveCallService.ACTION_START_OR_UPDATE
                putExtra(ActiveCallService.EXTRA_PEER_NAME, peerName)
                putExtra(ActiveCallService.EXTRA_IS_VIDEO, isVideo)
                putExtra(ActiveCallService.EXTRA_STATUS_TEXT, statusText)
            }
            ContextCompat.startForegroundService(appContext, intent)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start ActiveCallService", e)
        }
    }

    private fun stopOngoingCallForegroundService() {
        try {
            appContext.stopService(Intent(appContext, ActiveCallService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to stop ActiveCallService", e)
        }
    }

    private fun launchOutgoingCall(
        caller: DirectoryUser,
        callee: DirectoryUser,
        callType: CallType
    ) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        scope.launch {
            try {
                val isVideo = callType == CallType.VIDEO
                _uiState.update { it.copy(phase = CallPhase.INITIALIZING, errorMessage = null) }
                startOngoingCallForegroundService(callee.displayName, isVideo, "Calling...")

                val iceConfig = turnService.fetchIceServers()
                _uiState.update { it.copy(turnStatusLabel = iceConfig.statusLabel) }

                val manager = WebRtcCallManager(appContext)
                webRtcManager = manager
                manager.configureAudioForCall(enableSpeaker = isVideo)

                val mediaRes = manager.startLocalMedia(isVideo)
                if (mediaRes.isFailure) {
                    failCall(mediaRes.exceptionOrNull()?.localizedMessage ?: "Unable to access microphone/camera.")
                    return@launch
                }
                _uiState.update { it.copy(isMediaReady = true) }

                val participantIds = listOf(caller.uid, callee.uid)
                val pendingCandidates = mutableListOf<org.webrtc.IceCandidate>()
                var createdCallId: String? = null

                val pcRes = manager.createPeerConnection(
                    iceServers = iceConfig.iceServers,
                    onLocalIceCandidate = { candidate ->
                        val cid = createdCallId
                        if (cid == null) {
                            pendingCandidates.add(candidate)
                        } else {
                            sendLocalCandidate(cid, "caller", candidate, participantIds)
                        }
                    },
                    onIceConnectionChanged = { state -> handleIceState(state) },
                    onRemoteVideoReceived = {
                        _uiState.update { it.copy(hasRemoteVideo = true) }
                    }
                )
                if (pcRes.isFailure) {
                    failCall(pcRes.exceptionOrNull()?.localizedMessage ?: "Failed to create PeerConnection.")
                    return@launch
                }

                val offer = manager.createAndSetLocalOffer(isVideo = isVideo)
                val createRes = repository.createCallSession(
                    caller = caller,
                    callee = callee,
                    callType = callType,
                    offerSdp = offer.description,
                    offerType = offer.type.canonicalForm()
                )
                if (createRes.isFailure) {
                    failCall(createRes.exceptionOrNull()?.localizedMessage ?: "Failed to signal outgoing call.")
                    return@launch
                }

                val callId = createRes.getOrThrow()
                createdCallId = callId
                _uiState.update {
                    it.copy(
                        callId = callId,
                        phase = CallPhase.RINGING_OUTGOING,
                        iceStateLabel = "Ringing..."
                    )
                }
                startOngoingCallForegroundService(callee.displayName, isVideo, "Ringing...")
                dialToneController.playOutgoingDialTone(ringtonePrefs.getConfig(), previewOnly = false)

                pendingCandidates.forEach { sendLocalCandidate(callId, "caller", it, participantIds) }
                pendingCandidates.clear()

                observeCallSession(callId, currentUid, isCaller = true)
                observeRemoteCandidates(callId, currentUid, expectedRole = "callee")
                startRingingTimeout(callId, caller, callee, callType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Outgoing call failure", e)
                failCall(e.localizedMessage ?: "Call setup failed.")
            }
        }
    }

    private fun launchIncomingAcceptance(session: CallSession) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        scope.launch {
            try {
                val isVideo = session.parsedType == CallType.VIDEO
                val callId = session.id
                _uiState.update { it.copy(phase = CallPhase.CONNECTING, errorMessage = null) }
                startOngoingCallForegroundService(session.callerName, isVideo, "Connecting...")

                val iceConfig = turnService.fetchIceServers()
                _uiState.update { it.copy(turnStatusLabel = iceConfig.statusLabel) }

                val manager = WebRtcCallManager(appContext)
                webRtcManager = manager
                manager.configureAudioForCall(enableSpeaker = isVideo)

                val mediaRes = manager.startLocalMedia(isVideo)
                if (mediaRes.isFailure) {
                    repository.rejectCall(callId)
                    failCall(mediaRes.exceptionOrNull()?.localizedMessage ?: "Unable to access microphone/camera.")
                    return@launch
                }
                _uiState.update { it.copy(isMediaReady = true) }

                val pcRes = manager.createPeerConnection(
                    iceServers = iceConfig.iceServers,
                    onLocalIceCandidate = { candidate ->
                        sendLocalCandidate(callId, "callee", candidate, session.participantIds)
                    },
                    onIceConnectionChanged = { state -> handleIceState(state) },
                    onRemoteVideoReceived = {
                        _uiState.update { it.copy(hasRemoteVideo = true) }
                    }
                )
                if (pcRes.isFailure) {
                    repository.rejectCall(callId)
                    failCall(pcRes.exceptionOrNull()?.localizedMessage ?: "Failed to create PeerConnection.")
                    return@launch
                }

                val offerSdp = session.offerSdp
                if (offerSdp.isNullOrBlank()) {
                    repository.rejectCall(callId)
                    failCall("Incoming call offer SDP is missing.")
                    return@launch
                }

                manager.setRemoteDescription(SessionDescription.Type.OFFER, offerSdp)
                val answer = manager.createAndSetLocalAnswer(isVideo = isVideo)

                val answerRes = repository.answerCall(
                    callId = callId,
                    answerSdp = answer.description,
                    answerType = answer.type.canonicalForm()
                )
                if (answerRes.isFailure) {
                    failCall(answerRes.exceptionOrNull()?.localizedMessage ?: "Failed to answer call.")
                    return@launch
                }

                observeCallSession(callId, currentUid, isCaller = false)
                observeRemoteCandidates(callId, currentUid, expectedRole = "caller")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Incoming call acceptance failure", e)
                failCall(e.localizedMessage ?: "Unable to connect call.")
            }
        }
    }

    private fun observeCallSession(callId: String, currentUid: String, isCaller: Boolean) {
        callDocJob?.cancel()
        callDocJob = scope.launch {
            repository.observeCallDocument(callId, currentUid)
                .catch { error ->
                    val firestoreError = generateSequence<Throwable>(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    _uiState.update {
                        it.copy(errorMessage = (firestoreError ?: error).localizedMessage ?: "Signaling error")
                    }
                }
                .collect { session ->
                    if (session == null) return@collect

                    if (isCaller && !remoteAnswerApplied && !session.answerSdp.isNullOrBlank()) {
                        remoteAnswerApplied = true
                        ringingTimeoutJob?.cancel()
                        dialToneController.stop()
                        try {
                            _uiState.update { it.copy(phase = CallPhase.CONNECTING, iceStateLabel = "Connecting...") }
                            webRtcManager?.setRemoteDescription(
                                SessionDescription.Type.ANSWER,
                                session.answerSdp
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error setting remote answer SDP", e)
                            failCall("SDP negotiation failed: ${e.localizedMessage}")
                        }
                    }

                    when (session.parsedStatus) {
                        CallStatus.REJECTED -> {
                            ringingTimeoutJob?.cancel()
                            finishLocally("Call declined")
                        }
                        CallStatus.MISSED -> {
                            ringingTimeoutJob?.cancel()
                            finishLocally("No answer")
                        }
                        CallStatus.ENDED -> {
                            ringingTimeoutJob?.cancel()
                            finishLocally(null)
                        }
                        else -> Unit
                    }
                }
        }
    }

    private fun observeRemoteCandidates(callId: String, currentUid: String, expectedRole: String) {
        iceCandidatesJob?.cancel()
        iceCandidatesJob = scope.launch {
            repository.observeIceCandidates(callId, currentUid)
                .catch { error ->
                    if (error is CancellationException &&
                        generateSequence<Throwable>(error) { it.cause }.none { it is FirebaseFirestoreException }
                    ) throw error
                    Log.w(TAG, "ICE candidate observer warning", error)
                }
                .collect { items ->
                    items.filter { it.role == expectedRole && it.senderId != currentUid }
                        .forEach { item ->
                            webRtcManager?.addRemoteIceCandidate(
                                sdpMid = item.sdpMid,
                                sdpMLineIndex = item.sdpMLineIndex.toInt(),
                                candidateStr = item.candidate
                            )
                        }
                }
        }
    }

    private fun sendLocalCandidate(
        callId: String,
        role: String,
        candidate: org.webrtc.IceCandidate,
        participantIds: List<String>
    ) {
        scope.launch {
            repository.addIceCandidate(
                callId = callId,
                role = role,
                sdpMid = candidate.sdpMid ?: "0",
                sdpMLineIndex = candidate.sdpMLineIndex,
                candidate = candidate.sdp,
                participantIds = participantIds
            )
        }
    }

    private fun handleIceState(state: PeerConnection.IceConnectionState) {
        val current = _uiState.value
        when (state) {
            PeerConnection.IceConnectionState.CHECKING -> {
                _uiState.update { it.copy(iceStateLabel = "Negotiating ICE...") }
            }
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                dialToneController.stop()
                reconnectJob?.cancel()
                startDurationTimerIfNeeded()
                _uiState.update {
                    it.copy(
                        phase = CallPhase.CONNECTED,
                        iceStateLabel = "Connected",
                        errorMessage = null
                    )
                }
                startOngoingCallForegroundService(
                    current.peerName,
                    current.callType == CallType.VIDEO,
                    "Connected • ${current.turnStatusLabel}"
                )
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                _uiState.update {
                    it.copy(
                        phase = CallPhase.RECONNECTING,
                        iceStateLabel = "Reconnecting..."
                    )
                }
                startOngoingCallForegroundService(
                    current.peerName,
                    current.callType == CallType.VIDEO,
                    "Reconnecting..."
                )
                scheduleIceRestartIfCaller()
            }
            PeerConnection.IceConnectionState.FAILED -> {
                _uiState.update {
                    it.copy(
                        phase = CallPhase.RECONNECTING,
                        iceStateLabel = "Connection failed",
                        errorMessage = "Network dropped. Configure Metered TURN relay for strict NAT/cellular."
                    )
                }
                scheduleIceRestartIfCaller()
            }
            PeerConnection.IceConnectionState.CLOSED -> {
                finishLocally(null)
            }
            else -> Unit
        }
    }

    private fun scheduleIceRestartIfCaller() {
        val state = _uiState.value
        if (!state.isCaller || state.callId.isBlank()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3500L)
            if (_uiState.value.phase == CallPhase.RECONNECTING) {
                try {
                    val offer = webRtcManager?.createAndSetLocalOffer(
                        isVideo = _uiState.value.callType == CallType.VIDEO,
                        iceRestart = true
                    ) ?: return@launch
                    repository.publishOffer(state.callId, offer.description, offer.type.canonicalForm())
                } catch (e: Exception) {
                    Log.w(TAG, "ICE restart attempt failed", e)
                }
            }
        }
    }

    private fun startRingingTimeout(
        callId: String,
        caller: DirectoryUser,
        callee: DirectoryUser,
        callType: CallType
    ) {
        ringingTimeoutJob?.cancel()
        ringingTimeoutJob = scope.launch {
            delay(35_000L)
            if (_uiState.value.phase == CallPhase.RINGING_OUTGOING) {
                repository.markCallMissed(callId)
                val convRes = repository.getOrCreateConversation(caller, callee)
                convRes.getOrNull()?.let { convId ->
                    repository.sendMessage(
                        conversationId = convId,
                        participantIds = listOf(caller.uid, callee.uid).sorted(),
                        senderName = caller.displayName,
                        text = "Missed ${if (callType == CallType.VIDEO) "video" else "audio"} call",
                        messageType = MessageType.CALL_EVENT
                    )
                }
                finishLocally("No answer (missed call)")
            }
        }
    }

    private fun startDurationTimerIfNeeded() {
        if (timerJob?.isActive == true) return
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                _uiState.update { it.copy(durationSeconds = it.durationSeconds + 1L) }
            }
        }
    }

    fun toggleMute() {
        val next = !_uiState.value.isMuted
        webRtcManager?.setMicrophoneMuted(next)
        _uiState.update { it.copy(isMuted = next) }
    }

    fun toggleSpeaker() {
        val next = !_uiState.value.isSpeakerOn
        webRtcManager?.setSpeakerphone(next)
        _uiState.update { it.copy(isSpeakerOn = next) }
    }

    fun toggleCamera() {
        val next = !_uiState.value.isCameraEnabled
        webRtcManager?.setCameraEnabled(next)
        _uiState.update { it.copy(isCameraEnabled = next) }
    }

    fun switchCamera() {
        webRtcManager?.switchCamera { err ->
            _uiState.update { it.copy(errorMessage = err) }
        }
        scope.launch {
            delay(150L)
            webRtcManager?.isFrontCamera?.value?.let { front ->
                _uiState.update { it.copy(isFrontCamera = front) }
            }
        }
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        webRtcManager?.attachLocalRenderer(renderer)
    }

    fun detachLocalRenderer(renderer: SurfaceViewRenderer) {
        webRtcManager?.detachLocalRenderer(renderer)
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        webRtcManager?.attachRemoteRenderer(renderer)
    }

    fun detachRemoteRenderer(renderer: SurfaceViewRenderer) {
        webRtcManager?.detachRemoteRenderer(renderer)
    }

    fun rejectIncomingCall(callId: String) {
        CallNotifications.cancelIncomingCallNotification(appContext)
        _incomingRingingCall.value = null
        scope.launch {
            repository.rejectCall(callId)
        }
    }

    fun endActiveCall() {
        val state = _uiState.value
        val callId = state.callId
        val duration = state.durationSeconds
        val wasRinging = state.phase == CallPhase.RINGING_OUTGOING

        scope.launch {
            if (callId.isNotBlank()) {
                if (wasRinging && state.isCaller) {
                    repository.markCallMissed(callId)
                } else {
                    repository.endCall(callId, duration)
                }
            }
            finishLocally(null)
        }
    }

    fun dismissEndedState() {
        cleanupCallResources()
        _uiState.value = ActiveCallUiState()
    }

    private fun failCall(message: String) {
        val callId = _uiState.value.callId
        if (callId.isNotBlank()) {
            scope.launch { repository.endCall(callId, _uiState.value.durationSeconds) }
        }
        cleanupCallResources()
        _uiState.update {
            it.copy(
                phase = CallPhase.ENDED,
                errorMessage = message
            )
        }
    }

    private fun finishLocally(reason: String?) {
        cleanupCallResources()
        _uiState.update {
            it.copy(
                phase = CallPhase.ENDED,
                errorMessage = reason ?: it.errorMessage
            )
        }
    }

    private fun cleanupCallResources() {
        dialToneController.stop()
        callDocJob?.cancel()
        iceCandidatesJob?.cancel()
        timerJob?.cancel()
        ringingTimeoutJob?.cancel()
        reconnectJob?.cancel()
        callDocJob = null
        iceCandidatesJob = null
        timerJob = null
        ringingTimeoutJob = null
        reconnectJob = null
        remoteAnswerApplied = false
        webRtcManager?.release()
        webRtcManager = null
        stopOngoingCallForegroundService()
    }

    companion object {
        private const val TAG = "CallEngine"

        @Volatile
        private var INSTANCE: CallEngine? = null

        fun getInstance(context: Context): CallEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CallEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
