package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.squareup.moshi.JsonClass

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class CallType {
    AUDIO,
    VIDEO;

    companion object {
        fun fromRaw(raw: String?): CallType =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: AUDIO
    }
}

enum class CallStatus {
    RINGING,
    CONNECTED,
    ENDED,
    REJECTED,
    MISSED;

    val isTerminal: Boolean
        get() = this == ENDED || this == REJECTED || this == MISSED

    companion object {
        fun fromRaw(raw: String?): CallStatus =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: RINGING
    }
}

enum class MessageType {
    TEXT,
    IMAGE,
    VOICE,
    VIDEO,
    CALL_EVENT;

    companion object {
        fun fromRaw(raw: String?): MessageType =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: TEXT
    }
}

enum class DeliveryStatus {
    SENT,
    PENDING_P2P,
    DELIVERED;

    companion object {
        fun fromRaw(raw: String?): DeliveryStatus =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: SENT
    }
}

enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED;

    companion object {
        fun fromRaw(raw: String?): FriendRequestStatus =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: PENDING
    }
}

enum class PostPrivacyLevel(val label: String, val subtitle: String) {
    PUBLIC("Everyone", "All Connect Live users"),
    FRIENDS("Friends Only", "Only confirmed friends"),
    ONLY_ME("Only Me", "Private to you");

    companion object {
        fun fromRaw(raw: String?): PostPrivacyLevel =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: PUBLIC
    }
}

enum class FeedMediaType {
    IMAGE,
    VIDEO;

    companion object {
        fun fromRaw(raw: String?): FeedMediaType =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: IMAGE
    }
}

enum class AppNotificationType {
    MESSAGE,
    COMMENT,
    LIKE,
    FOLLOW,
    REPLY,
    REPOST,
    FRIEND_REQUEST;

    companion object {
        fun fromRaw(raw: String?): AppNotificationType =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: MESSAGE
    }
}

enum class VoiceTransferStatus {
    QUEUED,
    OFFERED,
    ANSWERED,
    TRANSFERRED,
    CANCELLED;

    companion object {
        fun fromRaw(raw: String?): VoiceTransferStatus =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: QUEUED
    }
}

@Keep
@IgnoreExtraProperties
data class UserPrivate(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val connectNumber: String = "",
    val bio: String = "",
    val location: String = "",
    val birthday: String = "",
    val gender: String = "",
    val online: Boolean = false,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DirectoryUser(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val displayNameLower: String = "",
    val statusText: String = "Available on Connect Live",
    val bio: String = "",
    val location: String = "",
    val photoUrl: String = "",
    val connectNumber: String = "",
    val discoverable: Boolean = true,
    val online: Boolean = false,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val totalLikesCount: Long = 0L,
    val postsCount: Long = 0L,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ConnectNumberEntry(
    @DocumentId val id: String = "",
    val number: String = "",
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDirectoryUser(): DirectoryUser = DirectoryUser(
        id = uid,
        uid = uid,
        displayName = displayName,
        displayNameLower = displayName.lowercase(),
        photoUrl = photoUrl,
        connectNumber = number
    )
}

@Keep
@IgnoreExtraProperties
data class FriendRequest(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val fromPhoto: String = "",
    val fromNumber: String = "",
    val toUid: String = "",
    val toName: String = "",
    val toPhoto: String = "",
    val toNumber: String = "",
    val participantIds: List<String> = emptyList(),
    val status: String = FriendRequestStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedStatus: FriendRequestStatus
        get() = FriendRequestStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class Friendship(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val participantNumbers: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun peerUid(currentUid: String): String =
        participantIds.firstOrNull { it != currentUid } ?: ""

    fun peerName(currentUid: String): String {
        val peer = peerUid(currentUid)
        return participantNames[peer]?.takeIf { it.isNotBlank() } ?: "Friend"
    }

    fun peerPhoto(currentUid: String): String {
        val peer = peerUid(currentUid)
        return participantPhotos[peer].orEmpty()
    }

    fun peerNumber(currentUid: String): String {
        val peer = peerUid(currentUid)
        return participantNumbers[peer].orEmpty()
    }
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val typingBy: Map<String, Boolean> = emptyMap(),
    val wallpaperBy: Map<String, String> = emptyMap(),
    val isFriendChat: Boolean = true,
    val dmInitiatorUid: String = "",
    val dmSenderMessageCount: Long = 0L,
    val dmRecipientReplied: Boolean = false,
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun peerUid(currentUid: String): String =
        participantIds.firstOrNull { it != currentUid } ?: ""

    fun peerName(currentUid: String): String {
        val peer = peerUid(currentUid)
        return participantNames[peer]?.takeIf { it.isNotBlank() } ?: "Connect User"
    }

    fun peerPhoto(currentUid: String): String {
        val peer = peerUid(currentUid)
        return participantPhotos[peer].orEmpty()
    }

    fun isPeerTyping(currentUid: String): Boolean {
        val peer = peerUid(currentUid)
        return peer.isNotBlank() && typingBy[peer] == true
    }

    fun myWallpaper(currentUid: String): String {
        return wallpaperBy[currentUid].orEmpty()
    }

    fun remainingDmMessages(currentUid: String): Int {
        if (isFriendChat || dmRecipientReplied || dmInitiatorUid != currentUid) return Int.MAX_VALUE
        return (3L - dmSenderMessageCount).coerceAtLeast(0L).toInt()
    }

    fun isDmLimitReached(currentUid: String): Boolean {
        return !isFriendChat && !dmRecipientReplied && dmInitiatorUid == currentUid && dmSenderMessageCount >= 3L
    }
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val videoUrl: String = "",
    val videoThumbUrl: String = "",
    val videoDurationMs: Long = 0L,
    val voiceUrl: String = "",
    val voiceDurationMs: Long = 0L,
    val voiceTransferId: String = "",
    val deliveryStatus: String = DeliveryStatus.SENT.name,
    val messageType: String = MessageType.TEXT.name,
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSenderName: String = "",
    val reactions: Map<String, String> = emptyMap(),
    val deleted: Boolean = false,
    val participantIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedType: MessageType
        get() = MessageType.fromRaw(messageType)

    val parsedDeliveryStatus: DeliveryStatus
        get() = DeliveryStatus.fromRaw(deliveryStatus)
}

@Keep
@IgnoreExtraProperties
data class FeedPost(
    @DocumentId val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorPhoto: String = "",
    val authorNumber: String = "",
    val title: String = "",
    val description: String = "",
    val mediaUrls: List<String> = emptyList(),
    val mediaType: String = FeedMediaType.IMAGE.name,
    val videoUrl: String = "",
    val viewVisibility: String = PostPrivacyLevel.PUBLIC.name,
    val likePermission: String = PostPrivacyLevel.PUBLIC.name,
    val commentPermission: String = PostPrivacyLevel.PUBLIC.name,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    val shareCount: Long = 0L,
    val repostCount: Long = 0L,
    val reactionsBy: Map<String, String> = emptyMap(),
    val repostedFromPostId: String = "",
    val repostedByUid: String = "",
    val repostedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedMediaType: FeedMediaType get() = FeedMediaType.fromRaw(mediaType)
    val parsedViewVisibility: PostPrivacyLevel get() = PostPrivacyLevel.fromRaw(viewVisibility)
    val parsedLikePermission: PostPrivacyLevel get() = PostPrivacyLevel.fromRaw(likePermission)
    val parsedCommentPermission: PostPrivacyLevel get() = PostPrivacyLevel.fromRaw(commentPermission)
}

@Keep
@IgnoreExtraProperties
data class FeedComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorPhoto: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val replyToCommentId: String = "",
    val replyToAuthorName: String = "",
    val replyToText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FollowRelation(
    @DocumentId val id: String = "",
    val followerUid: String = "",
    val targetUid: String = "",
    val followerName: String = "",
    val targetName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorPhoto: String = "",
    val type: String = AppNotificationType.MESSAGE.name,
    val title: String = "",
    val body: String = "",
    val targetId: String = "",
    val delivered: Boolean = false,
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedType: AppNotificationType get() = AppNotificationType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class VoiceTransferSession(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val recipientUid: String = "",
    val conversationId: String = "",
    val participantIds: List<String> = emptyList(),
    val durationMs: Long = 0L,
    val status: String = VoiceTransferStatus.QUEUED.name,
    val offerSdp: String? = null,
    val offerType: String? = null,
    val answerSdp: String? = null,
    val answerType: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedStatus: VoiceTransferStatus get() = VoiceTransferStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerPhoto: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val calleePhoto: String = "",
    val participantIds: List<String> = emptyList(),
    val callType: String = CallType.AUDIO.name,
    val status: String = CallStatus.RINGING.name,
    val offerSdp: String? = null,
    val offerType: String? = null,
    val answerSdp: String? = null,
    val answerType: String? = null,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val endedAt: Timestamp? = null
) {
    val parsedType: CallType
        get() = CallType.fromRaw(callType)

    val parsedStatus: CallStatus
        get() = CallStatus.fromRaw(status)

    fun peerId(currentUid: String): String =
        if (callerId == currentUid) calleeId else callerId

    fun peerName(currentUid: String): String =
        if (callerId == currentUid) calleeName else callerName

    fun peerPhoto(currentUid: String): String =
        if (callerId == currentUid) calleePhoto else callerPhoto
}

@Keep
@IgnoreExtraProperties
data class IceCandidateItem(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val role: String = "caller",
    val sdpMid: String = "",
    val sdpMLineIndex: Long = 0L,
    val candidate: String = "",
    val participantIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@JsonClass(generateAdapter = true)
data class IceServerDto(
    val urls: Any? = null,
    val username: String? = null,
    val credential: String? = null
)

@JsonClass(generateAdapter = true)
data class TurnBackendResponseDto(
    val iceServers: List<IceServerDto> = emptyList(),
    val uid: String? = null,
    val fetchedAt: String? = null
)
