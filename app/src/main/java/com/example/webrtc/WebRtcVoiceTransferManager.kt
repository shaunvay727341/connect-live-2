package com.example.webrtc

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.example.data.media.SafMediaManager
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageType
import com.example.data.model.VoiceTransferSession
import com.example.data.model.VoiceTransferStatus
import com.example.data.prefs.RingtonePreferences
import com.example.data.recovery.MessageOutboxManager
import com.example.data.repository.ConnectLiveRepository
import com.example.data.repository.SocialAndMediaRepository
import com.example.data.turn.TurnCredentialsService
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class QueuedVoiceTransfer(
    val transferId: String,
    val messageId: String,
    val conversationId: String,
    val senderUid: String,
    val recipientUid: String,
    val durationMs: Long,
    val localFilePath: String
)

/**
 * Handles Voice Message Recording, Playback, and WebRTC Peer-to-Peer DataChannel voice file transfer
 * with persistent offline-peer waiting queue (automatically dispatches when peer comes online).
 */
class WebRtcVoiceTransferManager private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueFile = File(appContext.filesDir, "voice_p2p_queue.json")
    private val voiceNotesDir = File(appContext.filesDir, "voice_notes").apply { mkdirs() }
    private val draftsDir = File(appContext.filesDir, "voice_drafts").apply { mkdirs() }

    private val ringtonePrefs = RingtonePreferences(appContext)
    private val socialRepo = SocialAndMediaRepository(appContext)
    private val chatRepo = ConnectLiveRepository(appContext)
    private val turnService = TurnCredentialsService(appContext)
    private val outbox = MessageOutboxManager.getInstance(appContext)

    private var recorder: MediaRecorder? = null
    private var currentDraftFile: File? = null
    private var recordStartMs = 0L

    private var mediaPlayer: MediaPlayer? = null
    private val _playingId = MutableStateFlow<String?>(null)
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    private val _queuedCount = MutableStateFlow(0)
    val queuedCount: StateFlow<Int> = _queuedCount.asStateFlow()

    private val _localVoiceFileSaved = MutableStateFlow(0)
    val localVoiceFileSaved: StateFlow<Int> = _localVoiceFileSaved.asStateFlow()

    private var queueWatcherJob: Job? = null
    private val activeTransfers = mutableSetOf<String>()

    private val peerFactory: PeerConnectionFactory by lazy {
        WebRtcCallManager.ensureFactoryInitialized(appContext)
        val egl = EglBase.create()
        val adm = JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule()
        PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    init {
        _queuedCount.value = loadQueue().size
        startQueueWatcher()
    }

    fun getVoiceFileForTransfer(transferId: String): File? {
        if (transferId.isBlank()) return null
        val f = File(voiceNotesDir, "voice_$transferId.m4a")
        return if (f.exists() && f.length() > 0L) f else null
    }

    fun startRecording(): File? {
        stopPlayback()
        cancelRecording()
        val outFile = File(draftsDir, "draft_${System.currentTimeMillis()}.m4a")
        return try {
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            currentDraftFile = outFile
            recordStartMs = System.currentTimeMillis()
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice recording", e)
            runCatching { outFile.delete() }
            null
        }
    }

    fun stopRecording(): Pair<File, Long>? {
        val rec = recorder ?: return null
        val file = currentDraftFile
        val durationMs = (System.currentTimeMillis() - recordStartMs).coerceAtLeast(500L)
        recorder = null
        return runCatching {
            rec.stop()
            rec.release()
            if (file != null && file.exists() && file.length() > 0L) {
                file to durationMs
            } else {
                null
            }
        }.onFailure {
            runCatching { rec.release() }
            runCatching { file?.delete() }
        }.getOrNull()
    }

    fun cancelRecording() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { currentDraftFile?.delete() }
        currentDraftFile = null
    }

    fun togglePlayback(id: String, file: File) {
        if (_playingId.value == id) {
            stopPlayback()
            return
        }
        stopPlayback()
        if (!file.exists()) return
        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _playingId.value = null
                }
                start()
            }
            _playingId.value = id
        }.onFailure {
            _playingId.value = null
        }
    }

    fun stopPlayback() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        _playingId.value = null
    }

    /**
     * Stores recorded voice note into internal voice storage + user SAF VoiceNotes folder,
     * sends the VOICE chat message, and either transfers immediately over WebRTC P2P or
     * queues it until the peer comes online.
     */
    suspend fun sendVoiceNote(
        conversationId: String,
        participantIds: List<String>,
        senderName: String,
        senderPhoto: String,
        recordedFile: File,
        durationMs: Long,
        replyToId: String = "",
        replyToText: String = "",
        replyToSenderName: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val uid = Firebase.auth.currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("Must be signed in."))
        val recipientUid = participantIds.firstOrNull { it != uid }
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid recipient"))

        val transferId = "vt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val bytes = runCatching { recordedFile.readBytes() }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Voice recording missing."))

        val safUri = ringtonePrefs.getConfig().safTreeUri
        val (savedFile, _) = SafMediaManager.saveBytesToSafAndLocal(
            context = appContext,
            safTreeUri = safUri,
            subfolder = SafMediaManager.SUB_VOICE_NOTES,
            localSubdir = "voice_notes",
            fileName = "voice_$transferId.m4a",
            mimeType = "audio/mp4",
            bytes = bytes
        )
        runCatching { recordedFile.delete() }
        _localVoiceFileSaved.value += 1

        val peerOnline = outbox.checkCurrentlyOnline() && socialRepo.checkPeerOnline(recipientUid)
        val msgResult = chatRepo.sendMessage(
            conversationId = conversationId,
            participantIds = participantIds,
            senderName = senderName,
            text = "",
            messageType = MessageType.VOICE,
            voiceUrl = "p2p://$transferId",
            voiceDurationMs = durationMs,
            voiceTransferId = transferId,
            deliveryStatus = DeliveryStatus.PENDING_P2P,
            replyToId = replyToId,
            replyToText = replyToText,
            replyToSenderName = replyToSenderName,
            senderPhoto = senderPhoto
        )
        if (msgResult.isFailure) {
            return@withContext Result.failure(
                msgResult.exceptionOrNull() ?: IllegalStateException("Unable to send voice message.")
            )
        }
        val msgId = msgResult.getOrThrow()

        val queuedItem = QueuedVoiceTransfer(
            transferId = transferId,
            messageId = msgId,
            conversationId = conversationId,
            senderUid = uid,
            recipientUid = recipientUid,
            durationMs = durationMs,
            localFilePath = savedFile.absolutePath
        )
        enqueueTransfer(queuedItem)

        if (peerOnline) {
            scope.launch { attemptSenderTransfer(queuedItem) }
        }
        Result.success(transferId)
    }

    private fun startQueueWatcher() {
        queueWatcherJob?.cancel()
        queueWatcherJob = scope.launch {
            while (isActive) {
                delay(4000L)
                val uid = Firebase.auth.currentUser?.uid ?: continue
                if (!outbox.checkCurrentlyOnline()) continue
                val pending = loadQueue().filter { it.senderUid == uid }
                for (item in pending) {
                    if (activeTransfers.contains(item.transferId)) continue
                    if (socialRepo.checkPeerOnline(item.recipientUid)) {
                        launch { attemptSenderTransfer(item) }
                    }
                }
            }
        }
    }

    private suspend fun attemptSenderTransfer(item: QueuedVoiceTransfer) {
        if (!activeTransfers.add(item.transferId)) return
        var pc: PeerConnection? = null
        var dc: DataChannel? = null
        var candJob: Job? = null
        var docJob: Job? = null
        try {
            val audioFile = File(item.localFilePath)
            if (!audioFile.exists()) {
                removeFromQueue(item.transferId)
                return
            }
            val bytes = audioFile.readBytes()
            val iceConfig = turnService.fetchIceConfig()
            val rtcConfig = PeerConnection.RTCConfiguration(iceConfig.iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }

            val participantIds = listOf(item.senderUid, item.recipientUid).sorted()
            val transferDone = CompletableSignal()
            var remoteSet = false
            val pendingCandidates = mutableListOf<IceCandidate>()
            val addedKeys = mutableSetOf<String>()

            pc = peerFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate ?: return
                    scope.launch {
                        socialRepo.addVoiceIceCandidate(
                            transferId = item.transferId,
                            role = "caller",
                            sdpMid = candidate.sdpMid ?: "",
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            candidate = candidate.sdp,
                            participantIds = participantIds
                        )
                    }
                }

                override fun onDataChannel(p0: DataChannel?) {}
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
            }) ?: return

            dc = pc.createDataChannel("voice_p2p", DataChannel.Init().apply {
                ordered = true
            })
            dc.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(p0: Long) {}
                override fun onStateChange() {
                    if (dc?.state() == DataChannel.State.OPEN) {
                        scope.launch {
                            streamBytesOverDataChannel(dc, bytes)
                        }
                    }
                }

                override fun onMessage(buffer: DataChannel.Buffer?) {
                    buffer ?: return
                    val msgBytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(msgBytes)
                    if (String(msgBytes) == "ACK") {
                        transferDone.complete(true)
                    }
                }
            })

            val offer = pc.awaitCreateOffer()
            pc.awaitSetLocalDescription(offer)
            socialRepo.upsertVoiceTransferOffer(
                transferId = item.transferId,
                recipientUid = item.recipientUid,
                conversationId = item.conversationId,
                durationMs = item.durationMs,
                offerSdp = offer.description,
                offerType = offer.type.canonicalForm()
            )

            docJob = scope.launch {
                socialRepo.observeVoiceTransfer(item.transferId, item.senderUid).collect { session ->
                    if (session != null && !remoteSet && !session.answerSdp.isNullOrBlank()) {
                        remoteSet = true
                        val ans = SessionDescription(SessionDescription.Type.ANSWER, session.answerSdp)
                        pc.awaitSetRemoteDescription(ans)
                        pendingCandidates.forEach { pc.addIceCandidate(it) }
                        pendingCandidates.clear()
                    }
                }
            }
            candJob = scope.launch {
                socialRepo.observeVoiceIceCandidates(item.transferId, item.senderUid).collect { list ->
                    list.filter { it.senderId != item.senderUid }.forEach { c ->
                        val key = "${c.id}:${c.sdpMid}:${c.sdpMLineIndex}:${c.candidate}"
                        if (addedKeys.add(key)) {
                            val cand = IceCandidate(c.sdpMid, c.sdpMLineIndex.toInt(), c.candidate)
                            if (remoteSet) pc.addIceCandidate(cand) else pendingCandidates += cand
                        }
                    }
                }
            }

            val acked = transferDone.awaitWithTimeout(22_000L)
            if (acked) {
                socialRepo.updateVoiceTransferStatus(item.transferId, VoiceTransferStatus.TRANSFERRED)
                chatRepo.updateMessageDeliveryStatus(item.conversationId, item.messageId, DeliveryStatus.DELIVERED)
                removeFromQueue(item.transferId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "P2P voice send deferred for ${item.transferId}", e)
        } finally {
            candJob?.cancel()
            docJob?.cancel()
            runCatching { dc?.close() }
            runCatching { pc?.close() }
            activeTransfers.remove(item.transferId)
        }
    }

    private fun streamBytesOverDataChannel(dc: DataChannel, bytes: ByteArray) {
        val header = "HDR:${bytes.size}".toByteArray()
        dc.send(DataChannel.Buffer(ByteBuffer.wrap(header), false))
        val chunkSize = 12_000
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            val slice = bytes.copyOfRange(offset, end)
            dc.send(DataChannel.Buffer(ByteBuffer.wrap(slice), true))
            offset = end
        }
        dc.send(DataChannel.Buffer(ByteBuffer.wrap("EOF".toByteArray()), false))
    }

    /**
     * Called by CallListenerService / foreground session when an incoming P2P voice transfer offer arrives.
     */
    fun handleIncomingTransferOffer(session: VoiceTransferSession) {
        if (session.id.isBlank() || session.offerSdp.isNullOrBlank()) return
        if (getVoiceFileForTransfer(session.id) != null) {
            scope.launch {
                socialRepo.updateVoiceTransferStatus(session.id, VoiceTransferStatus.TRANSFERRED)
            }
            return
        }
        if (!activeTransfers.add("rx_${session.id}")) return

        scope.launch {
            var pc: PeerConnection? = null
            var candJob: Job? = null
            try {
                val uid = Firebase.auth.currentUser?.uid ?: return@launch
                val iceConfig = turnService.fetchIceConfig()
                val rtcConfig = PeerConnection.RTCConfiguration(iceConfig.iceServers).apply {
                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                }
                val participantIds = listOf(session.senderUid, session.recipientUid).sorted()
                val doneSignal = CompletableSignal()
                val bufferOut = ByteArrayOutputStream()
                var expectedBytes = -1

                pc = peerFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate?) {
                        candidate ?: return
                        scope.launch {
                            socialRepo.addVoiceIceCandidate(
                                transferId = session.id,
                                role = "callee",
                                sdpMid = candidate.sdpMid ?: "",
                                sdpMLineIndex = candidate.sdpMLineIndex,
                                candidate = candidate.sdp,
                                participantIds = participantIds
                            )
                        }
                    }

                    override fun onDataChannel(channel: DataChannel?) {
                        channel ?: return
                        channel.registerObserver(object : DataChannel.Observer {
                            override fun onBufferedAmountChange(p0: Long) {}
                            override fun onStateChange() {}
                            override fun onMessage(buf: DataChannel.Buffer?) {
                                buf ?: return
                                val data = ByteArray(buf.data.remaining())
                                buf.data.get(data)
                                if (!buf.binary) {
                                    val txt = String(data)
                                    if (txt.startsWith("HDR:")) {
                                        expectedBytes = txt.removePrefix("HDR:").toIntOrNull() ?: -1
                                    } else if (txt == "EOF") {
                                        val finalBytes = bufferOut.toByteArray()
                                        if (finalBytes.isNotEmpty() && (expectedBytes <= 0 || finalBytes.size == expectedBytes)) {
                                            scope.launch {
                                                val safUri = ringtonePrefs.getConfig().safTreeUri
                                                SafMediaManager.saveBytesToSafAndLocal(
                                                    context = appContext,
                                                    safTreeUri = safUri,
                                                    subfolder = SafMediaManager.SUB_VOICE_NOTES,
                                                    localSubdir = "voice_notes",
                                                    fileName = "voice_${session.id}.m4a",
                                                    mimeType = "audio/mp4",
                                                    bytes = finalBytes
                                                )
                                                _localVoiceFileSaved.value += 1
                                                channel.send(DataChannel.Buffer(ByteBuffer.wrap("ACK".toByteArray()), false))
                                                doneSignal.complete(true)
                                            }
                                        }
                                    }
                                } else {
                                    bufferOut.write(data)
                                }
                            }
                        })
                    }

                    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                    override fun onIceConnectionReceivingChange(p0: Boolean) {}
                    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                    override fun onAddStream(p0: MediaStream?) {}
                    override fun onRemoveStream(p0: MediaStream?) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
                }) ?: return@launch

                pc.awaitSetRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, session.offerSdp))
                val answer = pc.awaitCreateAnswer()
                pc.awaitSetLocalDescription(answer)
                socialRepo.answerVoiceTransfer(session.id, answer.description, answer.type.canonicalForm())

                val addedKeys = mutableSetOf<String>()
                candJob = scope.launch {
                    socialRepo.observeVoiceIceCandidates(session.id, uid).collect { list ->
                        list.filter { it.senderId != uid }.forEach { c ->
                            val key = "${c.id}:${c.sdpMid}:${c.sdpMLineIndex}:${c.candidate}"
                            if (addedKeys.add(key)) {
                                pc.addIceCandidate(IceCandidate(c.sdpMid, c.sdpMLineIndex.toInt(), c.candidate))
                            }
                        }
                    }
                }

                doneSignal.awaitWithTimeout(25_000L)
            } catch (e: Exception) {
                Log.w(TAG, "P2P voice receive warning for ${session.id}", e)
            } finally {
                candJob?.cancel()
                runCatching { pc?.close() }
                activeTransfers.remove("rx_${session.id}")
            }
        }
    }

    private fun enqueueTransfer(item: QueuedVoiceTransfer) {
        val current = loadQueue().toMutableList()
        if (current.none { it.transferId == item.transferId }) {
            current += item
            saveQueue(current)
        }
        _queuedCount.value = current.size
    }

    private fun removeFromQueue(transferId: String) {
        val updated = loadQueue().filterNot { it.transferId == transferId }
        saveQueue(updated)
        _queuedCount.value = updated.size
    }

    private fun loadQueue(): List<QueuedVoiceTransfer> {
        if (!queueFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(queueFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        QueuedVoiceTransfer(
                            transferId = o.getString("transferId"),
                            messageId = o.getString("messageId"),
                            conversationId = o.getString("conversationId"),
                            senderUid = o.getString("senderUid"),
                            recipientUid = o.getString("recipientUid"),
                            durationMs = o.optLong("durationMs"),
                            localFilePath = o.getString("localFilePath")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveQueue(list: List<QueuedVoiceTransfer>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { q ->
                arr.put(JSONObject().apply {
                    put("transferId", q.transferId)
                    put("messageId", q.messageId)
                    put("conversationId", q.conversationId)
                    put("senderUid", q.senderUid)
                    put("recipientUid", q.recipientUid)
                    put("durationMs", q.durationMs)
                    put("localFilePath", q.localFilePath)
                })
            }
            queueFile.writeText(arr.toString())
        }
    }

    private class CompletableSignal {
        private val flow = MutableStateFlow(false)
        fun complete(value: Boolean) {
            flow.value = value
        }

        suspend fun awaitWithTimeout(timeoutMs: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (flow.value) return true
                delay(200L)
            }
            return flow.value
        }
    }

    private suspend fun PeerConnection.awaitCreateOffer(): SessionDescription =
        suspendCancellableCoroutine { cont ->
            createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc != null) cont.resume(desc) else cont.resumeWithException(IllegalStateException("Null offer"))
                }
                override fun onCreateFailure(err: String?) {
                    cont.resumeWithException(IllegalStateException(err ?: "Create offer failed"))
                }
                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, MediaConstraints())
        }

    private suspend fun PeerConnection.awaitCreateAnswer(): SessionDescription =
        suspendCancellableCoroutine { cont ->
            createAnswer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc != null) cont.resume(desc) else cont.resumeWithException(IllegalStateException("Null answer"))
                }
                override fun onCreateFailure(err: String?) {
                    cont.resumeWithException(IllegalStateException(err ?: "Create answer failed"))
                }
                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, MediaConstraints())
        }

    private suspend fun PeerConnection.awaitSetLocalDescription(desc: SessionDescription) =
        suspendCancellableCoroutine { cont ->
            setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(err: String?) { cont.resumeWithException(IllegalStateException(err ?: "Set local failed")) }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, desc)
        }

    private suspend fun PeerConnection.awaitSetRemoteDescription(desc: SessionDescription) =
        suspendCancellableCoroutine { cont ->
            setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(err: String?) { cont.resumeWithException(IllegalStateException(err ?: "Set remote failed")) }
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, desc)
        }

    companion object {
        private const val TAG = "WebRtcVoiceTransfer"

        @Volatile
        private var instance: WebRtcVoiceTransferManager? = null

        fun getInstance(context: Context): WebRtcVoiceTransferManager {
            return instance ?: synchronized(this) {
                instance ?: WebRtcVoiceTransferManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
