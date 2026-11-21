package com.example.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.IncomingCallActivity
import com.example.R
import com.example.call.CallEngine
import com.example.data.model.AppNotificationType
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.prefs.RingtoneAudioController
import com.example.data.prefs.RingtonePreferences
import com.example.data.recovery.MessageOutboxManager
import com.example.data.repository.ConnectLiveRepository
import com.example.data.repository.SocialAndMediaRepository
import com.example.webrtc.WebRtcVoiceTransferManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class CallListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listenerJob: Job? = null
    private var messageListenerJob: Job? = null
    private var activityNotifJob: Job? = null
    private var voiceTransferJob: Job? = null
    private var ringStopJob: Job? = null
    private var activeUid: String? = null
    private var lastAlertedCallId: String? = null
    private var initializedMessageWatermark = false
    private val lastSeenMessageTimes = mutableMapOf<String, Long>()
    private val alertedNotificationIds = mutableSetOf<String>()

    private var ringtoneController: RingtoneAudioController? = null
    private var ringtonePrefs: RingtonePreferences? = null
    private var vibrator: Vibrator? = null
    private var screenWakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = CallNotifications.buildListenerNotification(this)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            CallNotifications.NOTIFICATION_ID_LISTENER,
            notification,
            fgsType
        )

        if (activeUid != currentUser.uid || listenerJob?.isActive != true) {
            activeUid = currentUser.uid
            startObservingCalls(currentUser.uid)
        }

        return START_STICKY
    }

    private fun startObservingCalls(uid: String) {
        listenerJob?.cancel()
        ringStopJob?.cancel()
        val db = FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
        val repository = ConnectLiveRepository(db)
        val callEngine = CallEngine.getInstance(applicationContext)

        ringStopJob = scope.launch {
            callEngine.incomingRingingCall.collect { incoming ->
                if (incoming == null || callEngine.hasActiveOrPendingCall()) {
                    if (lastAlertedCallId != null) {
                        lastAlertedCallId = null
                        stopRingingAndClearAlert()
                    }
                }
            }
        }

        listenerJob = scope.launch {
            repository.observeCalls(uid)
                .catch { error ->
                    val firestoreError = generateSequence<Throwable>(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Background call listener error", firestoreError ?: error)
                }
                .collect { calls ->
                    val incoming = calls.firstOrNull { call ->
                        call.calleeId == uid && call.parsedStatus == CallStatus.RINGING
                    }
                    callEngine.updateIncomingRingingCall(incoming)

                    if (incoming != null && !callEngine.hasActiveOrPendingCall()) {
                        if (lastAlertedCallId != incoming.id) {
                            lastAlertedCallId = incoming.id
                            triggerIncomingCallAlert(incoming)
                        }
                    } else {
                        if (lastAlertedCallId != null) {
                            lastAlertedCallId = null
                            stopRingingAndClearAlert()
                        }
                    }
                }
        }

        messageListenerJob?.cancel()
        initializedMessageWatermark = false
        lastSeenMessageTimes.clear()
        messageListenerJob = scope.launch {
            repository.observeConversations(uid)
                .catch { error ->
                    val firestoreError = generateSequence<Throwable>(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Background message listener error", firestoreError ?: error)
                }
                .collect { conversations ->
                    if (!initializedMessageWatermark) {
                        initializedMessageWatermark = true
                        conversations.forEach { conv ->
                            val ts = conv.lastMessageAt?.toDate()?.time ?: 0L
                            lastSeenMessageTimes[conv.id] = ts
                        }
                        return@collect
                    }

                    conversations.forEach { conv ->
                        val msgTime = conv.lastMessageAt?.toDate()?.time ?: 0L
                        val previousTime = lastSeenMessageTimes[conv.id] ?: 0L
                        if (msgTime > previousTime) {
                            lastSeenMessageTimes[conv.id] = msgTime
                            val fromPeer = conv.lastMessageSenderId.isNotBlank() && conv.lastMessageSenderId != uid
                            if (fromPeer && !callEngine.isAppInForeground && conv.lastMessageText.isNotBlank()) {
                                CallNotifications.showNewMessageNotification(
                                    context = this@CallListenerService,
                                    conversationId = conv.id,
                                    senderName = conv.peerName(uid),
                                    messageText = conv.lastMessageText
                                )
                            }
                        }
                    }
                }
        }

        // Durable Social Activity & Message Delivery Recovery Listener
        val socialRepo = SocialAndMediaRepository(db)
        MessageOutboxManager.getInstance(applicationContext).flushAllPending()

        activityNotifJob?.cancel()
        activityNotifJob = scope.launch {
            socialRepo.observeNotifications(uid)
                .catch { error ->
                    val fs = generateSequence<Throwable>(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    Log.w(TAG, "Activity notification recovery error", fs ?: error)
                }
                .collect { notifications ->
                    notifications.filter { !it.delivered && !it.read && it.id.isNotBlank() }
                        .forEach { notif ->
                            if (alertedNotificationIds.add(notif.id)) {
                                val shouldPostSystem = !callEngine.isAppInForeground ||
                                    notif.parsedType != AppNotificationType.MESSAGE
                                if (shouldPostSystem) {
                                    CallNotifications.showActivityNotification(this@CallListenerService, notif)
                                }
                                socialRepo.markNotificationDelivered(notif.id)
                            }
                        }
                }
        }

        // Background/Foreground WebRTC P2P Voice Note Receiver
        val voiceManager = WebRtcVoiceTransferManager.getInstance(applicationContext)
        voiceTransferJob?.cancel()
        voiceTransferJob = scope.launch {
            socialRepo.observeIncomingVoiceTransfers(uid)
                .catch { error ->
                    val fs = generateSequence<Throwable>(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    Log.w(TAG, "Voice P2P listener error", fs ?: error)
                }
                .collect { incomingSessions ->
                    incomingSessions.forEach { session ->
                        voiceManager.handleIncomingTransferOffer(session)
                    }
                }
        }
    }

    private fun triggerIncomingCallAlert(session: CallSession) {
        wakeScreenBriefly()
        CallNotifications.showIncomingCallNotification(this, session)
        startRinging()

        // Launch native lock-screen / over-other-apps IncomingCallActivity directly when permitted
        try {
            val intent = Intent(this, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(CallNotifications.EXTRA_CALL_ID, session.id)
                putExtra(CallNotifications.EXTRA_CALLER_NAME, session.callerName)
                putExtra(CallNotifications.EXTRA_CALLER_PHOTO, session.callerPhoto)
                putExtra(CallNotifications.EXTRA_CALL_TYPE, session.callType)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Android 10+ background activity start restriction falls back cleanly to FullScreenIntent notification!
            Log.i(TAG, "Direct activity start deferred to FullScreenIntent notification: ${e.message}")
        }
    }

    private fun stopRingingAndClearAlert() {
        CallNotifications.cancelIncomingCallNotification(this)
        try {
            ringtoneController?.stop()
        } catch (_: Exception) {}
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
        vibrator = null
        try {
            screenWakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        screenWakeLock = null
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) {
                screenWakeLock?.let { if (it.isHeld) it.release() }
                screenWakeLock = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "ConnectLive::IncomingWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(15_000L)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Screen wake lock warning", e)
        }
    }

    private fun startRinging() {
        scope.launch {
            try {
                val prefs = ringtonePrefs ?: RingtonePreferences(applicationContext).also { ringtonePrefs = it }
                val ctrl = ringtoneController ?: RingtoneAudioController(applicationContext).also { ringtoneController = it }
                val config = prefs.getConfig()
                ctrl.playIncomingRingtone(config, previewOnly = false)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to play custom ringtone", e)
            }
        }

        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = vib
            val pattern = longArrayOf(0, 700, 500, 700)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to vibrate", e)
        }
    }

    override fun onDestroy() {
        stopRingingAndClearAlert()
        ringStopJob?.cancel()
        listenerJob?.cancel()
        messageListenerJob?.cancel()
        activityNotifJob?.cancel()
        voiceTransferJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallListenerService"

        fun startIfSignedIn(context: Context) {
            if (Firebase.auth.currentUser == null) return
            val hasNotifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            if (!hasNotifPerm) {
                // Start after user grants notification permission so foreground notification is visible
                return
            }
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, CallListenerService::class.java)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not start CallListenerService", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, CallListenerService::class.java))
            } catch (_: Exception) {}
        }
    }
}
