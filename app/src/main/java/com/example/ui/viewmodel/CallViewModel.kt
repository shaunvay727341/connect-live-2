package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.call.CallEngine
import com.example.data.model.CallSession
import com.example.data.model.CallType
import com.example.data.model.DirectoryUser
import com.example.data.repository.ConnectLiveRepository
import com.example.data.turn.TurnCredentialsService
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

enum class CallPhase {
    IDLE,
    REQUESTING_PERMISSIONS,
    INITIALIZING,
    RINGING_OUTGOING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ENDED
}

data class ActiveCallUiState(
    val callId: String = "",
    val peerId: String = "",
    val peerName: String = "",
    val peerPhoto: String = "",
    val callType: CallType = CallType.AUDIO,
    val phase: CallPhase = CallPhase.IDLE,
    val isCaller: Boolean = true,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val isMediaReady: Boolean = false,
    val hasRemoteVideo: Boolean = false,
    val isMinimizedInApp: Boolean = false,
    val durationSeconds: Long = 0L,
    val iceStateLabel: String = "Initializing",
    val turnStatusLabel: String = "Configuring ICE",
    val errorMessage: String? = null
)

class CallViewModel(
    appContext: Context,
    @Suppress("UNUSED_PARAMETER") repository: ConnectLiveRepository,
    @Suppress("UNUSED_PARAMETER") turnService: TurnCredentialsService,
    val currentUserId: String
) : ViewModel() {

    private val engine: CallEngine = CallEngine.getInstance(appContext)

    val uiState: StateFlow<ActiveCallUiState> = engine.uiState

    fun eglContext(): EglBase.Context? = engine.eglContext()

    fun hasActiveOrPendingCall(): Boolean = engine.hasActiveOrPendingCall()

    fun setMinimizedInApp(minimized: Boolean) = engine.setMinimizedInApp(minimized)

    fun prepareOutgoingCall(
        caller: DirectoryUser,
        callee: DirectoryUser,
        callType: CallType,
        hasRequiredPermissions: Boolean
    ) {
        engine.prepareOutgoingCall(
            caller = caller,
            callee = callee,
            callType = callType,
            hasRequiredPermissions = hasRequiredPermissions
        )
    }

    fun prepareIncomingCallAcceptance(
        session: CallSession,
        hasRequiredPermissions: Boolean
    ) {
        engine.prepareIncomingCallAcceptance(
            session = session,
            hasRequiredPermissions = hasRequiredPermissions
        )
    }

    fun onPermissionsResult(granted: Boolean) {
        engine.onPermissionsResult(granted)
    }

    fun toggleMute() = engine.toggleMute()

    fun toggleSpeaker() = engine.toggleSpeaker()

    fun toggleCamera() = engine.toggleCamera()

    fun switchCamera() = engine.switchCamera()

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) = engine.attachLocalRenderer(renderer)

    fun detachLocalRenderer(renderer: SurfaceViewRenderer) = engine.detachLocalRenderer(renderer)

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) = engine.attachRemoteRenderer(renderer)

    fun detachRemoteRenderer(renderer: SurfaceViewRenderer) = engine.detachRemoteRenderer(renderer)

    fun rejectIncomingCall(callId: String) = engine.rejectIncomingCall(callId)

    fun endActiveCall() = engine.endActiveCall()

    fun dismissEndedState() = engine.dismissEndedState()

    // Intentionally does NOT clean up WebRtcCallManager on ViewModel clear so active calls
    // survive Activity recreation, PiP transitions, and app minimization via CallEngine + ActiveCallService.
}
