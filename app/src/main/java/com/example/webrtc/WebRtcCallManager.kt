package com.example.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebRtcCallManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    val eglBase: EglBase = EglBase.create()

    private val audioDeviceModule: JavaAudioDeviceModule = JavaAudioDeviceModule.builder(appContext)
        .setUseHardwareAcousticEchoCanceler(true)
        .setUseHardwareNoiseSuppressor(true)
        .createAudioDeviceModule()

    private val peerConnectionFactory: PeerConnectionFactory

    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var currentCameraDeviceName: String? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private var remoteDescriptionSet = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private val processedCandidateKeys = mutableSetOf<String>()

    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerphone: Boolean = false
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _iceState = MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    val iceState: StateFlow<PeerConnection.IceConnectionState> = _iceState.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private var isReleased = false

    init {
        ensureFactoryInitialized(appContext)
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun configureAudioForCall(enableSpeaker: Boolean) {
        previousAudioMode = audioManager.mode
        previousSpeakerphone = audioManager.isSpeakerphoneOn

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audioFocusRequest = focusReq
            audioManager.requestAudioFocus(focusReq)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeakerphone(enableSpeaker)
    }

    fun setSpeakerphone(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetType = if (enabled) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            val targetDevice = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == targetType }
            if (targetDevice != null) {
                audioManager.setCommunicationDevice(targetDevice)
                return
            } else if (!enabled) {
                audioManager.clearCommunicationDevice()
                return
            }
        }
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = enabled
    }

    fun setMicrophoneMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera(onError: (String) -> Unit = {}) {
        val capturer = videoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                mainHandler.post {
                    _isFrontCamera.value = isFront
                    localRenderer?.setMirror(isFront)
                }
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                mainHandler.post {
                    onError(errorDescription ?: "Unable to switch camera")
                }
            }
        })
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        if (localRenderer === renderer) return
        localRenderer?.let { prev -> runCatching { localVideoTrack?.removeSink(prev) } }
        localRenderer = renderer
        localVideoTrack?.addSink(renderer)
    }

    fun detachLocalRenderer(renderer: SurfaceViewRenderer) {
        runCatching { localVideoTrack?.removeSink(renderer) }
        if (localRenderer === renderer) {
            localRenderer = null
        }
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        if (remoteRenderer === renderer) return
        remoteRenderer?.let { prev -> runCatching { remoteVideoTrack?.removeSink(prev) } }
        remoteRenderer = renderer
        remoteVideoTrack?.addSink(renderer)
    }

    fun detachRemoteRenderer(renderer: SurfaceViewRenderer) {
        runCatching { remoteVideoTrack?.removeSink(renderer) }
        if (remoteRenderer === renderer) {
            remoteRenderer = null
        }
    }

    fun startLocalMedia(isVideo: Boolean): Result<Unit> {
        return try {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
            val aSource = peerConnectionFactory.createAudioSource(audioConstraints)
            val aTrack = peerConnectionFactory.createAudioTrack("connect_audio_0", aSource)
            aTrack.setEnabled(true)
            audioSource = aSource
            localAudioTrack = aTrack

            if (isVideo) {
                val capturer = createCameraCapturer()
                    ?: return Result.failure(IllegalStateException("No camera hardware available on this device."))
                val helper = SurfaceTextureHelper.create("ConnectCaptureThread", eglBase.eglBaseContext)
                val vSource = peerConnectionFactory.createVideoSource(capturer.isScreencast)
                capturer.initialize(helper, appContext, vSource.capturerObserver)

                val (capW, capH, capFps) = pickBestCaptureFormat(currentCameraDeviceName)
                capturer.startCapture(capW, capH, capFps)

                val vTrack = peerConnectionFactory.createVideoTrack("connect_video_0", vSource)
                vTrack.setEnabled(true)

                videoCapturer = capturer
                surfaceTextureHelper = helper
                videoSource = vSource
                localVideoTrack = vTrack

                mainHandler.post {
                    localRenderer?.let { vTrack.addSink(it) }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local media", e)
            Result.failure(e)
        }
    }

    private fun pickBestCaptureFormat(deviceName: String?): Triple<Int, Int, Int> {
        val enumerator = Camera2Enumerator(appContext)
        val formats = deviceName?.let { runCatching { enumerator.getSupportedFormats(it) }.getOrNull() }
            .orEmpty()
        if (formats.isEmpty()) return Triple(640, 480, 30)

        val candidates = formats
            .filter { it.width in 320..1280 && it.height in 240..720 }
            .sortedWith(
                compareByDescending<org.webrtc.CameraEnumerationAndroid.CaptureFormat> {
                    if (it.width == 1280 && it.height == 720) 3
                    else if (it.width == 960 && it.height == 540) 2
                    else if (it.width == 640 && it.height == 480) 1
                    else 0
                }.thenByDescending { it.width * it.height }
            )
        val chosen = candidates.firstOrNull() ?: formats.minByOrNull { it.width * it.height }
        val maxFps = chosen?.framerate?.max?.let { rawMax ->
            val normalized = if (rawMax > 1000) rawMax / 1000 else rawMax
            normalized.coerceIn(15, 30)
        } ?: 30
        return Triple(chosen?.width ?: 640, chosen?.height ?: 480, maxFps)
    }

    fun createPeerConnection(
        iceServers: List<PeerConnection.IceServer>,
        onLocalIceCandidate: (IceCandidate) -> Unit,
        onIceConnectionChanged: (PeerConnection.IceConnectionState) -> Unit,
        onRemoteVideoReceived: () -> Unit
    ): Result<Unit> {
        return try {
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                iceTransportsType = PeerConnection.IceTransportsType.ALL
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            }

            val pc = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {
                    override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}

                    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                        if (newState != null) {
                            _iceState.value = newState
                            mainHandler.post { onIceConnectionChanged(newState) }
                        }
                    }

                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}

                    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}

                    override fun onIceCandidate(candidate: IceCandidate?) {
                        if (candidate != null) {
                            onLocalIceCandidate(candidate)
                        }
                    }

                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                    override fun onAddStream(stream: MediaStream?) {
                        stream?.videoTracks?.firstOrNull()?.let { track ->
                            attachRemoteTrack(track, onRemoteVideoReceived)
                        }
                    }

                    override fun onRemoveStream(stream: MediaStream?) {}

                    override fun onDataChannel(dataChannel: DataChannel?) {}

                    override fun onRenegotiationNeeded() {}

                    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                        val track = receiver?.track()
                        if (track is VideoTrack) {
                            attachRemoteTrack(track, onRemoteVideoReceived)
                        }
                    }

                    override fun onTrack(transceiver: RtpTransceiver?) {
                        val track = transceiver?.receiver?.track()
                        if (track is VideoTrack) {
                            attachRemoteTrack(track, onRemoteVideoReceived)
                        }
                    }
                }
            ) ?: return Result.failure(IllegalStateException("Failed to initialize RTCPeerConnection."))

            val streamIds = listOf("connect_live_stream")
            localAudioTrack?.let { pc.addTrack(it, streamIds) }
            localVideoTrack?.let { pc.addTrack(it, streamIds) }

            peerConnection = pc
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create PeerConnection", e)
            Result.failure(e)
        }
    }

    private fun attachRemoteTrack(track: VideoTrack, onRemoteVideoReceived: () -> Unit) {
        mainHandler.post {
            track.setEnabled(true)
            remoteVideoTrack = track
            remoteRenderer?.let { renderer ->
                runCatching { track.removeSink(renderer) }
                track.addSink(renderer)
            }
            onRemoteVideoReceived()
        }
    }

    suspend fun createAndSetLocalOffer(isVideo: Boolean, iceRestart: Boolean = false): SessionDescription {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection not initialized")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo.toString()))
            if (iceRestart) {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
        }
        val offer = suspendCancellableCoroutine<SessionDescription> { cont ->
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null && cont.isActive) cont.resume(sdp)
                    else if (cont.isActive) cont.resumeWithException(IllegalStateException("Empty offer SDP"))
                }

                override fun onSetSuccess() {}

                override fun onCreateFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Create offer failed"))
                }

                override fun onSetFailure(error: String?) {}
            }, constraints)
        }
        setLocalDescription(pc, offer)
        return offer
    }

    suspend fun createAndSetLocalAnswer(isVideo: Boolean): SessionDescription {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection not initialized")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo.toString()))
        }
        val answer = suspendCancellableCoroutine<SessionDescription> { cont ->
            pc.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null && cont.isActive) cont.resume(sdp)
                    else if (cont.isActive) cont.resumeWithException(IllegalStateException("Empty answer SDP"))
                }

                override fun onSetSuccess() {}

                override fun onCreateFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Create answer failed"))
                }

                override fun onSetFailure(error: String?) {}
            }, constraints)
        }
        setLocalDescription(pc, answer)
        return answer
    }

    suspend fun setRemoteDescription(type: SessionDescription.Type, sdp: String) {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection not initialized")
        val desc = SessionDescription(type, sdp)
        suspendCancellableCoroutine { cont ->
            pc.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {}

                override fun onSetSuccess() {
                    remoteDescriptionSet = true
                    drainPendingCandidates(pc)
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onCreateFailure(error: String?) {}

                override fun onSetFailure(error: String?) {
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException(error ?: "Set remote description failed"))
                    }
                }
            }, desc)
        }
    }

    private suspend fun setLocalDescription(pc: PeerConnection, desc: SessionDescription) {
        suspendCancellableCoroutine { cont ->
            pc.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {}

                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onCreateFailure(error: String?) {}

                override fun onSetFailure(error: String?) {
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException(error ?: "Set local description failed"))
                    }
                }
            }, desc)
        }
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidateStr: String) {
        val key = "$sdpMid|$sdpMLineIndex|$candidateStr"
        if (!processedCandidateKeys.add(key)) return

        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
        val pc = peerConnection
        if (pc != null && remoteDescriptionSet) {
            pc.addIceCandidate(candidate)
        } else {
            pendingRemoteCandidates.add(candidate)
        }
    }

    private fun drainPendingCandidates(pc: PeerConnection) {
        if (pendingRemoteCandidates.isEmpty()) return
        val copy = pendingRemoteCandidates.toList()
        pendingRemoteCandidates.clear()
        copy.forEach { pc.addIceCandidate(it) }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
        val deviceNames = enumerator.deviceNames
        // Prefer front-facing camera
        for (name in deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                enumerator.createCapturer(name, null)?.let {
                    currentCameraDeviceName = name
                    _isFrontCamera.value = true
                    return it
                }
            }
        }
        // Fallback to any camera
        for (name in deviceNames) {
            enumerator.createCapturer(name, null)?.let {
                currentCameraDeviceName = name
                _isFrontCamera.value = false
                return it
            }
        }
        return null
    }

    fun release() {
        if (isReleased) return
        isReleased = true

        try {
            localRenderer?.let { localVideoTrack?.removeSink(it) }
            remoteRenderer?.let { remoteVideoTrack?.removeSink(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error detaching renderers", e)
        }

        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping video capture", e)
        }
        try {
            videoCapturer?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error disposing video capturer", e)
        }
        videoCapturer = null

        try {
            localAudioTrack?.dispose()
            localVideoTrack?.dispose()
            audioSource?.dispose()
            videoSource?.dispose()
            surfaceTextureHelper?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error disposing media tracks/sources", e)
        }

        try {
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing PeerConnection", e)
        }
        peerConnection = null

        try {
            audioDeviceModule.release()
            peerConnectionFactory.dispose()
            eglBase.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing factory/EGL", e)
        }

        restoreAudio()
    }

    private fun restoreAudio() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = previousSpeakerphone
            }
            audioManager.mode = previousAudioMode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring audio settings", e)
        }
    }

    companion object {
        private const val TAG = "WebRtcCallManager"
        @Volatile
        private var factoryInitialized = false

        fun ensureFactoryInitialized(context: Context) {
            if (!factoryInitialized) {
                synchronized(this) {
                    if (!factoryInitialized) {
                        PeerConnectionFactory.initialize(
                            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                                .setEnableInternalTracer(false)
                                .createInitializationOptions()
                        )
                        factoryInitialized = true
                    }
                }
            }
        }
    }
}
