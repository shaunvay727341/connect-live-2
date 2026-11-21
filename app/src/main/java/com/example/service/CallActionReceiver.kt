package com.example.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.call.CallEngine
import com.example.data.model.CallType

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val engine = CallEngine.getInstance(context)
        when (intent.action) {
            ACTION_ACCEPT_CALL -> {
                CallNotifications.cancelIncomingCallNotification(context)
                val callId = intent.getStringExtra(CallNotifications.EXTRA_CALL_ID)
                val session = engine.incomingRingingCall.value
                    ?.takeIf { callId == null || it.id == callId }
                if (session != null) {
                    val hasAudio = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasCam = if (session.parsedType == CallType.VIDEO) {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                    engine.prepareIncomingCallAcceptance(
                        session = session,
                        hasRequiredPermissions = hasAudio && hasCam
                    )
                }
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(CallNotifications.EXTRA_OPEN_ACTIVE_CALL, true)
                }
                context.startActivity(openIntent)
            }
            ACTION_DECLINE_CALL -> {
                val callId = intent.getStringExtra(CallNotifications.EXTRA_CALL_ID)
                    ?: engine.incomingRingingCall.value?.id
                if (!callId.isNullOrBlank()) {
                    engine.rejectIncomingCall(callId)
                } else {
                    CallNotifications.cancelIncomingCallNotification(context)
                }
            }
            ACTION_HANGUP_CALL -> {
                engine.endActiveCall()
            }
            ACTION_TOGGLE_MUTE -> {
                engine.toggleMute()
            }
        }
    }

    companion object {
        const val ACTION_ACCEPT_CALL = "com.example.service.ACTION_ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "com.example.service.ACTION_DECLINE_CALL"
        const val ACTION_HANGUP_CALL = "com.example.service.ACTION_HANGUP_CALL"
        const val ACTION_TOGGLE_MUTE = "com.example.service.ACTION_TOGGLE_MUTE"
    }
}
