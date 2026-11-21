package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AppNotification
import com.example.data.model.AppNotificationType
import com.example.data.model.DirectoryUser
import com.example.data.model.FeedComment
import com.example.data.model.FeedMediaType
import com.example.data.model.FeedPost
import com.example.data.model.FollowRelation
import com.example.data.model.IceCandidateItem
import com.example.data.model.PostPrivacyLevel
import com.example.data.model.VoiceTransferSession
import com.example.data.model.VoiceTransferStatus
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SocialAndMediaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google.")

    private fun safeUidOrNull(): String? =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    private fun <T> Flow<T>.logErrors(op: OperationType, path: String, expectedUid: String): Flow<T> =
        catch { error ->
            val fs = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fs == null && error is CancellationException) throw error
            val ex = fs ?: (error as? Exception)
            if (ex != null && auth.currentUser?.uid == expectedUid) {
                handleFirestoreError(ex, op, path)
            }
            throw error
        }

    // --- Social Feed Posts ---

    fun observeVisibleFeedPosts(currentUid: String, friendUids: List<String>): Flow<List<FeedPost>> = flow {
        val authed = requireUserId()
        check(authed == currentUid) { "UID mismatch" }

        val publicFlow = db.collection("feedPosts")
            .whereEqualTo("viewVisibility", PostPrivacyLevel.PUBLIC.name)
            .snapshots()
            .map { snap -> snap.toObjects(FeedPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .logErrors(OperationType.LIST, "feedPosts(PUBLIC)", currentUid)

        val ownFlow = db.collection("feedPosts")
            .whereEqualTo("authorUid", currentUid)
            .snapshots()
            .map { snap -> snap.toObjects(FeedPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .logErrors(OperationType.LIST, "feedPosts(OWN)", currentUid)

        val friendChunk = friendUids.distinct().filter { it.isNotBlank() && it != currentUid }.take(10)
        val friendsFlow: Flow<List<FeedPost>> = if (friendChunk.isEmpty()) {
            flow { emit(emptyList()) }
        } else {
            db.collection("feedPosts")
                .whereEqualTo("viewVisibility", PostPrivacyLevel.FRIENDS.name)
                .whereIn("authorUid", friendChunk)
                .snapshots()
                .map { snap -> snap.toObjects(FeedPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { emit(emptyList()) }
        }

        emitAll(
            combine(publicFlow, ownFlow, friendsFlow) { pub, own, fr ->
                (pub + own + fr)
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
        )
    }

    fun observeMyPosts(currentUid: String): Flow<List<FeedPost>> = flow {
        val authed = requireUserId()
        check(authed == currentUid) { "UID mismatch" }
        emitAll(
            db.collection("feedPosts")
                .whereEqualTo("authorUid", currentUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FeedPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logErrors(OperationType.LIST, "feedPosts(MY)", currentUid)
        )
    }

    suspend fun createFeedPost(
        author: DirectoryUser,
        title: String,
        description: String,
        mediaUrls: List<String>,
        mediaType: FeedMediaType = FeedMediaType.IMAGE,
        videoUrl: String = "",
        viewVisibility: PostPrivacyLevel = PostPrivacyLevel.PUBLIC,
        likePermission: PostPrivacyLevel = PostPrivacyLevel.PUBLIC,
        commentPermission: PostPrivacyLevel = PostPrivacyLevel.PUBLIC
    ): Result<String> {
        val uid = safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        val safeTitle = title.trim().take(120)
        val safeDesc = description.trim().take(2000)
        val safeMedia = mediaUrls.map { it.trim().take(1000) }.filter { it.isNotBlank() }.take(10)
        val safeVideo = videoUrl.trim().take(1000)
        if (safeTitle.isEmpty() && safeDesc.isEmpty() && safeMedia.isEmpty() && safeVideo.isEmpty()) {
            return Result.failure(IllegalArgumentException("Add a photo, video, title, or description to post."))
        }
        val postId = "post_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val postRef = db.collection("feedPosts").document(postId)
        return try {
            val payload = mutableMapOf<String, Any>(
                "authorUid" to uid,
                "authorName" to author.displayName.ifBlank { "Connect User" }.take(80),
                "authorPhoto" to author.photoUrl.take(1000),
                "title" to safeTitle,
                "description" to safeDesc,
                "mediaUrls" to safeMedia,
                "mediaType" to mediaType.name,
                "viewVisibility" to viewVisibility.name,
                "likePermission" to likePermission.name,
                "commentPermission" to commentPermission.name,
                "likeCount" to 0L,
                "commentCount" to 0L,
                "shareCount" to 0L,
                "repostCount" to 0L,
                "reactionsBy" to emptyMap<String, String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (author.connectNumber.matches(Regex("^01[0-9]{7}$"))) {
                payload["authorNumber"] = author.connectNumber
            }
            if (safeVideo.isNotBlank()) {
                payload["videoUrl"] = safeVideo
            }
            postRef.set(payload).await()

            runCatching {
                db.collection("directory").document(uid).update(
                    mapOf(
                        "postsCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(postId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, postRef.path)
            Result.failure(e)
        }
    }

    suspend fun togglePostReaction(
        post: FeedPost,
        actor: DirectoryUser,
        emoji: String = "❤️"
    ): Result<Unit> {
        val uid = safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        val postRef = db.collection("feedPosts").document(post.id)
        return try {
            val currentReaction = post.reactionsBy[uid]
            val removing = currentReaction == emoji
            val updatedMap = post.reactionsBy.toMutableMap()
            if (removing) {
                updatedMap.remove(uid)
            } else {
                updatedMap[uid] = emoji.take(16)
            }
            val newLikeCount = updatedMap.size.toLong()
            val deltaForAuthor = when {
                currentReaction == null && !removing -> 1L
                currentReaction != null && removing -> -1L
                else -> 0L
            }

            postRef.update(
                mapOf(
                    "reactionsBy" to updatedMap,
                    "likeCount" to newLikeCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (deltaForAuthor != 0L && post.authorUid.isNotBlank()) {
                runCatching {
                    db.collection("directory").document(post.authorUid).update(
                        mapOf(
                            "totalLikesCount" to FieldValue.increment(deltaForAuthor),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            if (deltaForAuthor > 0L && post.authorUid != uid) {
                createAppNotification(
                    recipientUid = post.authorUid,
                    actor = actor,
                    type = AppNotificationType.LIKE,
                    title = "${actor.displayName} reacted $emoji",
                    body = post.title.ifBlank { post.description.ifBlank { "Liked your Feed post" } }.take(140),
                    targetId = post.id
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            Result.failure(e)
        }
    }

    fun observePostComments(postId: String): Flow<List<FeedComment>> = flow {
        val uid = requireUserId()
        val path = "feedPosts/$postId/comments"
        emitAll(
            db.collection("feedPosts").document(postId).collection("comments")
                .snapshots()
                .map { snap ->
                    snap.toObjects(FeedComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .logErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun addPostComment(
        post: FeedPost,
        actor: DirectoryUser,
        text: String,
        imageUrl: String = "",
        replyTo: FeedComment? = null
    ): Result<Unit> {
        val uid = safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        val safeText = text.trim().take(1000)
        val safeImg = imageUrl.trim().take(1000)
        if (safeText.isEmpty() && safeImg.isEmpty()) {
            return Result.failure(IllegalArgumentException("Write a comment or attach an image."))
        }
        val postRef = db.collection("feedPosts").document(post.id)
        val commentRef = postRef.collection("comments").document()
        return try {
            val payload = mutableMapOf<String, Any>(
                "postId" to post.id,
                "authorUid" to uid,
                "authorName" to actor.displayName.ifBlank { "Connect User" }.take(80),
                "authorPhoto" to actor.photoUrl.take(1000),
                "text" to safeText,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (safeImg.isNotBlank()) payload["imageUrl"] = safeImg
            if (replyTo != null) {
                payload["replyToCommentId"] = replyTo.id.take(128)
                payload["replyToAuthorName"] = replyTo.authorName.take(80)
                payload["replyToText"] = replyTo.text.ifBlank { "📷 Image comment" }.take(300)
            }
            commentRef.set(payload).await()

            postRef.update(
                mapOf(
                    "commentCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (replyTo != null && replyTo.authorUid.isNotBlank() && replyTo.authorUid != uid) {
                createAppNotification(
                    recipientUid = replyTo.authorUid,
                    actor = actor,
                    type = AppNotificationType.REPLY,
                    title = "${actor.displayName} replied to your comment",
                    body = safeText.ifBlank { "📷 Sent an image reply" },
                    targetId = post.id
                )
            }
            if (post.authorUid != uid && post.authorUid != replyTo?.authorUid) {
                createAppNotification(
                    recipientUid = post.authorUid,
                    actor = actor,
                    type = AppNotificationType.COMMENT,
                    title = "${actor.displayName} commented on your post",
                    body = safeText.ifBlank { "📷 Sent an image comment" },
                    targetId = post.id
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            Result.failure(e)
        }
    }

    suspend fun repostFeedPost(original: FeedPost, actor: DirectoryUser): Result<String> {
        val uid = safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        val postId = "repost_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val postRef = db.collection("feedPosts").document(postId)
        val origRef = db.collection("feedPosts").document(original.id)
        return try {
            val payload = mutableMapOf<String, Any>(
                "authorUid" to uid,
                "authorName" to actor.displayName.ifBlank { "Connect User" }.take(80),
                "authorPhoto" to actor.photoUrl.take(1000),
                "title" to original.title.ifBlank { "Reposted from ${original.authorName}" }.take(120),
                "description" to original.description.take(2000),
                "mediaUrls" to original.mediaUrls,
                "mediaType" to original.mediaType,
                "viewVisibility" to PostPrivacyLevel.PUBLIC.name,
                "likePermission" to PostPrivacyLevel.PUBLIC.name,
                "commentPermission" to PostPrivacyLevel.PUBLIC.name,
                "likeCount" to 0L,
                "commentCount" to 0L,
                "shareCount" to 0L,
                "repostCount" to 0L,
                "reactionsBy" to emptyMap<String, String>(),
                "repostedFromPostId" to original.id.take(128),
                "repostedByUid" to uid,
                "repostedByName" to original.authorName.take(80),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (actor.connectNumber.matches(Regex("^01[0-9]{7}$"))) {
                payload["authorNumber"] = actor.connectNumber
            }
            if (original.videoUrl.isNotBlank()) {
                payload["videoUrl"] = original.videoUrl.take(1000)
            }
            postRef.set(payload).await()

            runCatching {
                origRef.update(
                    mapOf(
                        "repostCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                db.collection("directory").document(uid).update(
                    mapOf(
                        "postsCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (original.authorUid != uid) {
                createAppNotification(
                    recipientUid = original.authorUid,
                    actor = actor,
                    type = AppNotificationType.REPOST,
                    title = "${actor.displayName} reposted your post",
                    body = original.title.ifBlank { original.description }.take(140).ifBlank { "Shared to Feed" },
                    targetId = original.id
                )
            }
            Result.success(postId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, postRef.path)
            Result.failure(e)
        }
    }

    suspend fun incrementPostShare(postId: String): Result<Unit> {
        safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        val postRef = db.collection("feedPosts").document(postId)
        return try {
            postRef.update(
                mapOf(
                    "shareCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            Result.failure(e)
        }
    }

    // --- Follower / Following System ---

    fun observeFollowingRelations(currentUid: String): Flow<List<FollowRelation>> = flow {
        val authed = requireUserId()
        check(authed == currentUid) { "UID mismatch" }
        emitAll(
            db.collection("follows")
                .whereEqualTo("followerUid", currentUid)
                .snapshots()
                .map { it.toObjects(FollowRelation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logErrors(OperationType.LIST, "follows(following)", currentUid)
        )
    }

    fun observeFollowersRelations(currentUid: String): Flow<List<FollowRelation>> = flow {
        val authed = requireUserId()
        check(authed == currentUid) { "UID mismatch" }
        emitAll(
            db.collection("follows")
                .whereEqualTo("targetUid", currentUid)
                .snapshots()
                .map { it.toObjects(FollowRelation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logErrors(OperationType.LIST, "follows(followers)", currentUid)
        )
    }

    suspend fun fetchCreatorProfile(targetUid: String): DirectoryUser? {
        if (targetUid.isBlank()) return null
        return runCatching {
            db.collection("directory").document(targetUid).get().await()
                .toObject(DirectoryUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }.getOrNull()
    }

    suspend fun toggleFollow(
        currentUser: DirectoryUser,
        targetUid: String,
        targetName: String
    ): Result<Boolean> {
        val uid = safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        if (targetUid.isBlank() || targetUid == uid) {
            return Result.failure(IllegalArgumentException("Cannot follow yourself."))
        }
        val followId = "${uid}_${targetUid}"
        val followRef = db.collection("follows").document(followId)
        return try {
            val snap = followRef.get().await()
            if (snap.exists()) {
                followRef.delete().await()
                runCatching {
                    db.collection("directory").document(uid).update(
                        mapOf("followingCount" to FieldValue.increment(-1L), "updatedAt" to FieldValue.serverTimestamp())
                    ).await()
                    db.collection("directory").document(targetUid).update(
                        mapOf("followersCount" to FieldValue.increment(-1L), "updatedAt" to FieldValue.serverTimestamp())
                    ).await()
                }
                Result.success(false)
            } else {
                followRef.set(
                    mapOf(
                        "followerUid" to uid,
                        "targetUid" to targetUid,
                        "followerName" to currentUser.displayName.ifBlank { "Connect User" }.take(80),
                        "targetName" to targetName.ifBlank { "Creator" }.take(80),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                runCatching {
                    db.collection("directory").document(uid).update(
                        mapOf("followingCount" to FieldValue.increment(1L), "updatedAt" to FieldValue.serverTimestamp())
                    ).await()
                    db.collection("directory").document(targetUid).update(
                        mapOf("followersCount" to FieldValue.increment(1L), "updatedAt" to FieldValue.serverTimestamp())
                    ).await()
                }
                createAppNotification(
                    recipientUid = targetUid,
                    actor = currentUser,
                    type = AppNotificationType.FOLLOW,
                    title = "New Follower",
                    body = "${currentUser.displayName} started following you",
                    targetId = uid
                )
                Result.success(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, followRef.path)
            Result.failure(e)
        }
    }

    // --- Social & Message Notifications + Offline Recovery ---

    suspend fun createAppNotification(
        recipientUid: String,
        actor: DirectoryUser,
        type: AppNotificationType,
        title: String,
        body: String,
        targetId: String = ""
    ) {
        val uid = safeUidOrNull() ?: return
        if (recipientUid.isBlank() || recipientUid == uid) return
        runCatching {
            db.collection("notifications").document().set(
                mapOf(
                    "recipientUid" to recipientUid,
                    "actorUid" to uid,
                    "actorName" to actor.displayName.ifBlank { "Connect User" }.take(80),
                    "actorPhoto" to actor.photoUrl.take(1000),
                    "type" to type.name,
                    "title" to title.ifBlank { "Connect Live" }.take(120),
                    "body" to body.ifBlank { "New activity" }.take(500),
                    "targetId" to targetId.take(200),
                    "delivered" to false,
                    "read" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeNotifications(currentUid: String): Flow<List<AppNotification>> = flow {
        val authed = requireUserId()
        check(authed == currentUid) { "UID mismatch" }
        emitAll(
            db.collection("notifications")
                .whereEqualTo("recipientUid", currentUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logErrors(OperationType.LIST, "notifications", currentUid)
        )
    }

    suspend fun markNotificationDelivered(notificationId: String) {
        if (notificationId.isBlank()) return
        runCatching {
            db.collection("notifications").document(notificationId).update("delivered", true).await()
        }
    }

    suspend fun markNotificationRead(notificationId: String) {
        if (notificationId.isBlank()) return
        runCatching {
            db.collection("notifications").document(notificationId)
                .update(mapOf("delivered" to true, "read" to true))
                .await()
        }
    }

    suspend fun markAllNotificationsRead(notifications: List<AppNotification>) {
        notifications.filter { !it.read && it.id.isNotBlank() }.forEach { notif ->
            markNotificationRead(notif.id)
        }
    }

    // --- WebRTC Peer-to-Peer Voice Transfer Signaling ---

    suspend fun upsertVoiceTransferOffer(
        transferId: String,
        recipientUid: String,
        conversationId: String,
        durationMs: Long,
        offerSdp: String,
        offerType: String = "offer"
    ): Result<Unit> {
        val uid = safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        val ref = db.collection("voiceTransfers").document(transferId)
        return try {
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "senderUid" to uid,
                        "recipientUid" to recipientUid,
                        "conversationId" to conversationId,
                        "participantIds" to listOf(uid, recipientUid).sorted(),
                        "durationMs" to durationMs.coerceIn(0L, 600_000L),
                        "status" to VoiceTransferStatus.OFFERED.name,
                        "offerSdp" to offerSdp.take(25000),
                        "offerType" to offerType.lowercase(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "status" to VoiceTransferStatus.OFFERED.name,
                        "offerSdp" to offerSdp.take(25000),
                        "offerType" to offerType.lowercase(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun answerVoiceTransfer(
        transferId: String,
        answerSdp: String,
        answerType: String = "answer"
    ) {
        runCatching {
            db.collection("voiceTransfers").document(transferId).update(
                mapOf(
                    "status" to VoiceTransferStatus.ANSWERED.name,
                    "answerSdp" to answerSdp.take(25000),
                    "answerType" to answerType.lowercase(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun updateVoiceTransferStatus(transferId: String, status: VoiceTransferStatus) {
        runCatching {
            db.collection("voiceTransfers").document(transferId).update(
                mapOf(
                    "status" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeVoiceTransfer(transferId: String, currentUid: String): Flow<VoiceTransferSession?> = flow {
        requireUserId()
        emitAll(
            db.collection("voiceTransfers").document(transferId)
                .snapshots()
                .map { it.toObject(VoiceTransferSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logErrors(OperationType.GET, "voiceTransfers/$transferId", currentUid)
        )
    }

    fun observeIncomingVoiceTransfers(currentUid: String): Flow<List<VoiceTransferSession>> = flow {
        val authed = requireUserId()
        check(authed == currentUid) { "UID mismatch" }
        emitAll(
            db.collection("voiceTransfers")
                .whereArrayContains("participantIds", currentUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VoiceTransferSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.recipientUid == currentUid && it.parsedStatus == VoiceTransferStatus.OFFERED }
                }
                .logErrors(OperationType.LIST, "voiceTransfers", currentUid)
        )
    }

    suspend fun addVoiceIceCandidate(
        transferId: String,
        role: String,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String,
        participantIds: List<String>
    ) {
        val uid = safeUidOrNull() ?: return
        runCatching {
            db.collection("voiceTransfers").document(transferId).collection("candidates").document().set(
                mapOf(
                    "senderId" to uid,
                    "role" to role,
                    "sdpMid" to sdpMid.take(64),
                    "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 32),
                    "candidate" to candidate.take(2000),
                    "participantIds" to participantIds,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeVoiceIceCandidates(transferId: String, currentUid: String): Flow<List<IceCandidateItem>> = flow {
        requireUserId()
        emitAll(
            db.collection("voiceTransfers").document(transferId).collection("candidates")
                .whereArrayContains("participantIds", currentUid)
                .snapshots()
                .map { it.toObjects(IceCandidateItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logErrors(OperationType.LIST, "voiceTransfers/$transferId/candidates", currentUid)
        )
    }

    suspend fun checkPeerOnline(peerUid: String): Boolean {
        if (peerUid.isBlank()) return false
        return runCatching {
            db.collection("directory").document(peerUid).get().await().getBoolean("online") ?: false
        }.getOrDefault(false)
    }

    fun observePeerOnline(peerUid: String): Flow<Boolean> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("directory").document(peerUid)
                .snapshots()
                .map { it.getBoolean("online") ?: false }
                .logErrors(OperationType.GET, "directory/$peerUid", uid)
        )
    }
}
