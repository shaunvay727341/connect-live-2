package com.example

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.call.CallEngine
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.prefs.RingtoneConfig
import com.example.data.prefs.RingtonePreferences
import com.example.service.CallNotifications
import com.example.ui.components.ProvideTouchFeedback
import com.example.ui.screens.IncomingCallOverlay
import com.example.ui.theme.ConnectLiveTheme

class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockScreenBehavior()

        val callEngine = CallEngine.getInstance(applicationContext)
        val fallbackSession = CallSession(
            id = intent.getStringExtra(CallNotifications.EXTRA_CALL_ID).orEmpty(),
            callerName = intent.getStringExtra(CallNotifications.EXTRA_CALLER_NAME).orEmpty(),
            callerPhoto = intent.getStringExtra(CallNotifications.EXTRA_CALLER_PHOTO).orEmpty(),
            callType = intent.getStringExtra(CallNotifications.EXTRA_CALL_TYPE) ?: CallType.AUDIO.name,
            status = CallStatus.RINGING.name
        )

        setContent {
            val prefs = remember { RingtonePreferences(applicationContext) }
            val ringtoneConfig by prefs.configFlow.collectAsStateWithLifecycle(initialValue = RingtoneConfig())
            val liveIncoming by callEngine.incomingRingingCall.collectAsStateWithLifecycle()
            val activeSession = liveIncoming ?: fallbackSession

            LaunchedEffect(liveIncoming) {
                if (liveIncoming == null && callEngine.hasActiveOrPendingCall()) {
                    finish()
                }
            }

            ConnectLiveTheme(darkTheme = ringtoneConfig.darkThemeEnabled) {
                ProvideTouchFeedback(
                    soundEnabled = ringtoneConfig.touchSoundEnabled,
                    hapticsEnabled = ringtoneConfig.hapticsEnabled
                ) {
                    IncomingCallOverlay(
                        callSession = activeSession,
                        customBackgroundUri = ringtoneConfig.incomingCallBackgroundUri,
                        onAccept = {
                            dismissKeyguardAndAccept(callEngine, activeSession)
                        },
                        onReject = {
                            if (activeSession.id.isNotBlank()) {
                                callEngine.rejectIncomingCall(activeSession.id)
                            }
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun configureLockScreenBehavior() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun dismissKeyguardAndAccept(callEngine: CallEngine, session: CallSession) {
        val launchMainCall = {
            val hasAudio = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val hasCam = if (session.parsedType == CallType.VIDEO) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            callEngine.prepareIncomingCallAcceptance(
                session = session,
                hasRequiredPermissions = hasAudio && hasCam
            )
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(CallNotifications.EXTRA_OPEN_ACTIVE_CALL, true)
                }
            )
            finish()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (km.isKeyguardLocked) {
                km.requestDismissKeyguard(
                    this,
                    object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            launchMainCall()
                        }

                        override fun onDismissCancelled() {
                            launchMainCall()
                        }

                        override fun onDismissError() {
                            launchMainCall()
                        }
                    }
                )
            } else {
                launchMainCall()
            }
        } else {
            launchMainCall()
        }
    }
}
