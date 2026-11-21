package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.example.IncomingCallActivity
import com.example.MainActivity
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.AppNotificationType
import com.example.data.model.CallSession
import com.example.data.model.CallType

object CallNotifications {
    const val CHANNEL_INCOMING_CALLS = "connect_live_incoming_calls_silent_v2"
    private const val LEGACY_CHANNEL_INCOMING_CALLS = "connect_live_incoming_calls"
    const val CHANNEL_ONGOING_CALLS = "connect_live_ongoing_calls"
    const val CHANNEL_BACKGROUND_READY = "connect_live_background_ready"
    const val CHANNEL_MESSAGES = "connect_live_messages"
    const val CHANNEL_SOCIAL_ACTIVITY = "connect_live_social_activity"

    const val NOTIFICATION_ID_INCOMING = 1001
    const val NOTIFICATION_ID_ONGOING = 1002
    const val NOTIFICATION_ID_LISTENER = 1003
    const val NOTIFICATION_ID_MESSAGE_BASE = 2000
    const val NOTIFICATION_ID_SOCIAL_BASE = 3000

    const val EXTRA_OPEN_ACTIVE_CALL = "extra_open_active_call"
    const val EXTRA_OPEN_CONVERSATION_ID = "extra_open_conversation_id"
    const val EXTRA_OPEN_NOTIFICATIONS_SHEET = "extra_open_notifications_sheet"
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CALLER_NAME = "extra_caller_name"
    const val EXTRA_CALLER_PHOTO = "extra_caller_photo"
    const val EXTRA_CALL_TYPE = "extra_call_type"

    fun ensureChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_INCOMING_CALLS) }

        val incomingChannel = NotificationChannelCompat.Builder(
            CHANNEL_INCOMING_CALLS,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
            .setName("Incoming Calls")
            .setDescription("Full-screen alerts for incoming audio & video calls")
            .setVibrationEnabled(true)
            .setVibrationPattern(longArrayOf(0, 800, 500, 800))
            .setLightsEnabled(true)
            .setSound(null, null)
            .build()

        val ongoingChannel = NotificationChannelCompat.Builder(
            CHANNEL_ONGOING_CALLS,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
            .setName("Ongoing Calls")
            .setDescription("Keeps active audio and video calls connected when minimized")
            .setSound(null, null)
            .setVibrationEnabled(false)
            .build()

        val listenerChannel = NotificationChannelCompat.Builder(
            CHANNEL_BACKGROUND_READY,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName("Background Call Readiness")
            .setDescription("Keeps Connect Live ready to alert you of incoming calls")
            .setShowBadge(false)
            .setSound(null, null)
            .build()

        val messageChannel = NotificationChannelCompat.Builder(
            CHANNEL_MESSAGES,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
            .setName("Messages")
            .setDescription("Real-time chat message alerts")
            .setVibrationEnabled(true)
            .build()

        val socialChannel = NotificationChannelCompat.Builder(
            CHANNEL_SOCIAL_ACTIVITY,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        )
            .setName("Social Activity & Requests")
            .setDescription("Likes, comments, replies, follows, reposts, and friend requests")
            .setVibrationEnabled(true)
            .build()

        manager.createNotificationChannel(incomingChannel)
        manager.createNotificationChannel(ongoingChannel)
        manager.createNotificationChannel(listenerChannel)
        manager.createNotificationChannel(messageChannel)
        manager.createNotificationChannel(socialChannel)
    }

    fun buildListenerNotification(context: Context): Notification {
        ensureChannels(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_BACKGROUND_READY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Connect Live is active")
            .setContentText("Ready to receive incoming audio & video calls")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildIncomingCallNotification(context: Context, session: CallSession): Notification {
        ensureChannels(context)
        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALL_ID, session.id)
            putExtra(EXTRA_CALLER_NAME, session.callerName)
            putExtra(EXTRA_CALLER_PHOTO, session.callerPhoto)
            putExtra(EXTRA_CALL_TYPE, session.callType)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            20,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Direct Activity PendingIntent avoids Android 12/13+ Notification Trampoline blocking
        val answerIntent = Intent(context, MainActivity::class.java).apply {
            action = CallActionReceiver.ACTION_ACCEPT_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALL_ID, session.id)
            putExtra(EXTRA_OPEN_ACTIVE_CALL, true)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            context,
            21,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE_CALL
            putExtra(EXTRA_CALL_ID, session.id)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            22,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerPerson = Person.Builder()
            .setName(session.callerName.ifBlank { "Connect User" })
            .setImportant(true)
            .build()

        val isVideo = session.parsedType == CallType.VIDEO
        val callStyle = NotificationCompat.CallStyle.forIncomingCall(
            callerPerson,
            declinePendingIntent,
            answerPendingIntent
        ).setIsVideo(isVideo)

        val builder = NotificationCompat.Builder(context, CHANNEL_INCOMING_CALLS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(session.callerName.ifBlank { "Connect User" })
            .setContentText(if (isVideo) "Incoming video call" else "Incoming audio call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setStyle(callStyle)
            .addPerson(callerPerson)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            builder.setColorized(true)
        }
        return builder.build().apply {
            flags = flags or Notification.FLAG_INSISTENT
        }
    }

    fun buildOngoingCallNotification(
        context: Context,
        peerName: String,
        isVideo: Boolean,
        statusText: String
    ): Notification {
        ensureChannels(context)
        val returnIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ACTIVE_CALL, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            30,
            returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_HANGUP_CALL
        }
        val hangupPendingIntent = PendingIntent.getBroadcast(
            context,
            31,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val peerPerson = Person.Builder()
            .setName(peerName.ifBlank { "Connect User" })
            .setImportant(true)
            .build()

        val callStyle = NotificationCompat.CallStyle.forOngoingCall(
            peerPerson,
            hangupPendingIntent
        ).setIsVideo(isVideo)

        return NotificationCompat.Builder(context, CHANNEL_ONGOING_CALLS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(peerName.ifBlank { "Connect Live Call" })
            .setContentText(statusText)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setContentIntent(contentPendingIntent)
            .setStyle(callStyle)
            .addPerson(peerPerson)
            .build()
    }

    fun cancelIncomingCallNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_INCOMING)
    }

    fun showIncomingCallNotification(context: Context, session: CallSession) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID_INCOMING, buildIncomingCallNotification(context, session))
        } catch (_: SecurityException) {
        }
    }

    fun showNewMessageNotification(
        context: Context,
        conversationId: String,
        senderName: String,
        messageText: String
    ) {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CONVERSATION_ID, conversationId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName.ifBlank { "New Message" })
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val id = NOTIFICATION_ID_MESSAGE_BASE + (conversationId.hashCode() and 0x0FFF)
            nm.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    fun showActivityNotification(context: Context, notif: AppNotification) {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (notif.parsedType == AppNotificationType.MESSAGE && notif.targetId.isNotBlank()) {
                putExtra(EXTRA_OPEN_CONVERSATION_ID, notif.targetId)
            } else {
                putExtra(EXTRA_OPEN_NOTIFICATIONS_SHEET, true)
            }
        }
        val reqCode = notif.id.ifBlank { notif.actorUid + notif.type }.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            reqCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isMsg = notif.parsedType == AppNotificationType.MESSAGE
        val channelId = if (isMsg) CHANNEL_MESSAGES else CHANNEL_SOCIAL_ACTIVITY
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notif.title.ifBlank { notif.actorName.ifBlank { "Connect Live" } })
            .setContentText(notif.body.ifBlank { "New activity" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(notif.body.ifBlank { "New activity" }))
            .setPriority(if (isMsg) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val id = NOTIFICATION_ID_SOCIAL_BASE + (reqCode and 0x0FFF)
            nm.notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }
}
