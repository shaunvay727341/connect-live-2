package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.AppNotificationType
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectNumberEntry
import com.example.data.model.Conversation
import com.example.data.model.DeliveryStatus
import com.example.data.model.DirectoryUser
import com.example.data.model.FriendRequest
import com.example.data.model.FriendRequestStatus
import com.example.data.model.Friendship
import com.example.data.model.IceCandidateItem
import com.example.data.model.MessageType
import com.example.data.model.UserPrivate
import com.example.util.QrCodeUtils
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.UUID

class ConnectLiveRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val random = SecureRandom()

    private fun requireUserId(): String =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun safeUidOrNull(): String? =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    private fun <T> Flow<T>.logFirestoreErrors(op: OperationType, path: String, expectedUid: String): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val ex = firestoreError ?: (error as? Exception)
            if (ex != null && auth.currentUser?.uid == expectedUid) {
                handleFirestoreError(ex, op, path)
            }
            throw error
        }

    // --- User Profile, 9-Digit 01xxxxxxx Number & Online Presence ---

    private fun generateCandidate01Number(): String {
        val suffix = random.nextInt(10_000_000)
        return String.format(java.util.Locale.US, "01%07d", suffix)
    }

    private suspend fun reserveUniqueConnectNumber(
        uid: String,
        displayName: String,
        photoUrl: String
    ): String {
        repeat(12) {
            val candidate = generateCandidate01Number()
            val numRef = db.collection("connectNumbers").document(candidate)
            val snap = runCatching { numRef.get().await() }.getOrNull()
            if (snap == null || !snap.exists()) {
                val created = runCatching {
                    numRef.set(
                        mapOf(
                            "number" to candidate,
                            "uid" to uid,
                            "displayName" to displayName.ifBlank { "Connect User" }.take(80),
                            "photoUrl" to photoUrl.take(1000),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }.isSuccess
                if (created) return candidate
            }
        }
        // Fallback deterministic 01xxxxxxx derived from UID hash if random retries collided
        val hashSuffix = (uid.hashCode().toLong() and 0x7FFFFFFFL) % 10_000_000L
        val fallback = String.format(java.util.Locale.US, "01%07d", hashSuffix)
        val numRef = db.collection("connectNumbers").document(fallback)
        runCatching {
            if (!numRef.get().await().exists()) {
                numRef.set(
                    mapOf(
                        "number" to fallback,
                        "uid" to uid,
                        "displayName" to displayName.ifBlank { "Connect User" }.take(80),
                        "photoUrl" to photoUrl.take(1000),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        return fallback
    }

    suspend fun ensureUserProfileInitialized(): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = currentUser.uid
        val userDocPath = "users/$uid"
        val dirDocPath = "directory/$uid"
        val defaultName = (currentUser.displayName?.takeIf { it.isNotBlank() }
            ?: currentUser.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Connect User").take(80)
        val safeEmail = (currentUser.email ?: "").take(320)
        val safePhoto = (currentUser.photoUrl?.toString() ?: "").take(1000)

        return try {
            val userRef = db.collection("users").document(uid)
            val dirRef = db.collection("directory").document(uid)
            val userSnap = userRef.get().await()
            val dirSnap = dirRef.get().await()

            val existingUserNum = userSnap.getString("connectNumber").orEmpty()
                .takeIf { QrCodeUtils.isValidConnectNumber(it) }
            val existingDirNum = dirSnap.getString("connectNumber").orEmpty()
                .takeIf { QrCodeUtils.isValidConnectNumber(it) }

            val assignedNumber = existingUserNum
                ?: existingDirNum
                ?: reserveUniqueConnectNumber(
                    uid = uid,
                    displayName = userSnap.getString("displayName") ?: defaultName,
                    photoUrl = userSnap.getString("photoUrl") ?: safePhoto
                )

            // Ensure index doc exists for existing users too
            if (existingUserNum != null || existingDirNum != null) {
                val idxRef = db.collection("connectNumbers").document(assignedNumber)
                val idxSnap = runCatching { idxRef.get().await() }.getOrNull()
                if (idxSnap != null && !idxSnap.exists()) {
                    runCatching {
                        idxRef.set(
                            mapOf(
                                "number" to assignedNumber,
                                "uid" to uid,
                                "displayName" to (userSnap.getString("displayName") ?: defaultName).take(80),
                                "photoUrl" to (userSnap.getString("photoUrl") ?: safePhoto).take(1000),
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }

            if (!userSnap.exists()) {
                val privatePayload = mapOf(
                    "uid" to uid,
                    "email" to safeEmail,
                    "displayName" to defaultName,
                    "photoUrl" to safePhoto,
                    "connectNumber" to assignedNumber,
                    "online" to true,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(privatePayload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "online" to true,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (existingUserNum == null) {
                    updates["connectNumber"] = assignedNumber
                }
                userRef.update(updates).await()
            }

            if (!dirSnap.exists()) {
                val dirPayload = mapOf(
                    "uid" to uid,
                    "displayName" to defaultName,
                    "displayNameLower" to defaultName.lowercase(),
                    "statusText" to "Available on Connect Live",
                    "photoUrl" to safePhoto,
                    "connectNumber" to assignedNumber,
                    "followersCount" to 0L,
                    "followingCount" to 0L,
                    "totalLikesCount" to 0L,
                    "postsCount" to 0L,
                    "discoverable" to true,
                    "online" to true,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                dirRef.set(dirPayload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "online" to true,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (existingDirNum == null) {
                    updates["connectNumber"] = assignedNumber
                }
                dirRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$userDocPath | $dirDocPath")
            Result.failure(e)
        }
    }

    suspend fun updatePresence(online: Boolean): Result<Unit> {
        val uid = safeUidOrNull() ?: return Result.success(Unit)
        return try {
            val updates = mapOf(
                "online" to online,
                "lastSeen" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).update(updates).await()
            db.collection("directory").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        statusText: String,
        photoUrl: String,
        discoverable: Boolean,
        connectNumber: String = "",
        bio: String? = null,
        location: String? = null,
        birthday: String? = null,
        gender: String? = null
    ): Result<Unit> {
        val trimmedName = displayName.trim().take(80)
        val trimmedStatus = statusText.trim().take(160)
        val trimmedPhoto = photoUrl.trim().take(1000)
        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Display name cannot be empty."))
        }
        val uid = safeUidOrNull()
            ?: return Result.failure(IllegalStateException("Must be signed in."))

        return try {
            val privateUpdates = mutableMapOf<String, Any>(
                "displayName" to trimmedName,
                "photoUrl" to trimmedPhoto,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            bio?.let { privateUpdates["bio"] = it.trim().take(300) }
            location?.let { privateUpdates["location"] = it.trim().take(100) }
            birthday?.let { privateUpdates["birthday"] = it.trim().take(40) }
            gender?.let { privateUpdates["gender"] = it.trim().take(40) }
            db.collection("users").document(uid).update(privateUpdates).await()

            val dirUpdates = mutableMapOf<String, Any>(
                "displayName" to trimmedName,
                "displayNameLower" to trimmedName.lowercase(),
                "statusText" to trimmedStatus,
                "photoUrl" to trimmedPhoto,
                "discoverable" to discoverable,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            bio?.let { dirUpdates["bio"] = it.trim().take(300) }
            location?.let { dirUpdates["location"] = it.trim().take(100) }
            db.collection("directory").document(uid).update(dirUpdates).await()

            val num = connectNumber.takeIf { QrCodeUtils.isValidConnectNumber(it) }
                ?: db.collection("users").document(uid).get().await().getString("connectNumber")
                    ?.takeIf { QrCodeUtils.isValidConnectNumber(it) }
            if (num != null) {
                runCatching {
                    db.collection("connectNumbers").document(num).update(
                        mapOf(
                            "displayName" to trimmedName,
                            "photoUrl" to trimmedPhoto,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "directory/$uid")
            Result.failure(e)
        }
    }

    fun observeCurrentUserPrivate(uid: String): Flow<UserPrivate?> = flow {
        val authedUid = requireUserId()
        check(authedUid == uid) { "UID mismatch" }
        val ref = db.collection("users").document(uid)
        emitAll(
            ref.snapshots()
                .map { snap -> snap.toObject(UserPrivate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.GET, ref.path, uid)
        )
    }

    fun observeCurrentDirectoryUser(uid: String): Flow<DirectoryUser?> = flow {
        val authedUid = requireUserId()
        check(authedUid == uid) { "UID mismatch" }
        val ref = db.collection("directory").document(uid)
        emitAll(
            ref.snapshots()
                .map { snap -> snap.toObject(DirectoryUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.GET, ref.path, uid)
        )
    }

    fun observeDirectoryUsers(currentUid: String): Flow<List<DirectoryUser>> = flow {
        val authedUid = requireUserId()
        check(authedUid == currentUid) { "UID mismatch" }
        val query = db.collection("directory").whereEqualTo("discoverable", true)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(DirectoryUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.uid != currentUid }
                        .sortedWith(compareByDescending<DirectoryUser> { it.online }.thenBy { it.displayNameLower })
                }
                .logFirestoreErrors(OperationType.LIST, "directory", currentUid)
        )
    }

    // --- 9-Digit 01xxxxxxx Connect Number Lookup & Friend System ---

    suspend fun lookupUserByConnectNumber(rawNumber: String): Result<ConnectNumberEntry?> {
        val number = QrCodeUtils.sanitizeConnectNumberInput(rawNumber)
        if (!QrCodeUtils.isValidConnectNumber(number)) {
            return Result.failure(IllegalArgumentException("Enter a valid 9-digit number starting with 01 (e.g., 014829301)."))
        }
        return try {
            requireUserId()
            val snap = db.collection("connectNumbers").document(number).get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                Result.success(
                    snap.toObject(ConnectNumberEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "connectNumbers/$number")
            Result.failure(e)
        }
    }

    fun observeFriendRequests(currentUid: String): Flow<List<FriendRequest>> = flow {
        val authedUid = requireUserId()
        check(authedUid == currentUid) { "UID mismatch" }
        val query = db.collection("friendRequests").whereArrayContains("participantIds", currentUid)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FriendRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, "friendRequests", currentUid)
        )
    }

    fun observeFriendships(currentUid: String): Flow<List<Friendship>> = flow {
        val authedUid = requireUserId()
        check(authedUid == currentUid) { "UID mismatch" }
        val query = db.collection("friendships").whereArrayContains("participantIds", currentUid)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Friendship::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.peerName(currentUid).lowercase() }
                }
                .logFirestoreErrors(OperationType.LIST, "friendships", currentUid)
        )
    }

    suspend fun sendFriendRequest(
        currentUser: DirectoryUser,
        target: ConnectNumberEntry
    ): Result<String> {
        val uid = safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        if (target.uid.isBlank() || target.uid == uid) {
            return Result.failure(IllegalArgumentException("You cannot add your own Connect Number."))
        }
        val pairId = buildConversationId(uid, target.uid)
        val reqRef = db.collection("friendRequests").document(pairId)

        return try {
            val existingFriendship = db.collection("friendships").document(pairId).get().await()
            if (existingFriendship.exists()) {
                return Result.success("Already connected as friends!")
            }

            val existingReq = reqRef.get().await()
            if (existingReq.exists()) {
                val existingStatus = existingReq.getString("status")
                val fromUid = existingReq.getString("fromUid")
                if (existingStatus == FriendRequestStatus.PENDING.name && fromUid == uid) {
                    return Result.success("Friend request already sent.")
                }
                if (existingStatus == FriendRequestStatus.PENDING.name && fromUid == target.uid) {
                    val parsed = existingReq.toObject(FriendRequest::class.java)
                    if (parsed != null) {
                        respondToFriendRequest(parsed, accept = true)
                        return Result.success("Matched! You are now friends with ${target.displayName}.")
                    }
                }
                val updatePayload = mutableMapOf<String, Any>(
                    "fromUid" to uid,
                    "fromName" to currentUser.displayName.ifBlank { "Connect User" }.take(80),
                    "fromPhoto" to currentUser.photoUrl.take(1000),
                    "toUid" to target.uid,
                    "toName" to target.displayName.ifBlank { "Connect User" }.take(80),
                    "toPhoto" to target.photoUrl.take(1000),
                    "status" to FriendRequestStatus.PENDING.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (QrCodeUtils.isValidConnectNumber(currentUser.connectNumber)) {
                    updatePayload["fromNumber"] = currentUser.connectNumber
                }
                if (QrCodeUtils.isValidConnectNumber(target.number)) {
                    updatePayload["toNumber"] = target.number
                }
                reqRef.update(updatePayload).await()
                return Result.success("Friend request sent to ${target.displayName}.")
            }

            val payload = mutableMapOf<String, Any>(
                "fromUid" to uid,
                "fromName" to currentUser.displayName.ifBlank { "Connect User" }.take(80),
                "fromPhoto" to currentUser.photoUrl.take(1000),
                "toUid" to target.uid,
                "toName" to target.displayName.ifBlank { "Connect User" }.take(80),
                "toPhoto" to target.photoUrl.take(1000),
                "participantIds" to listOf(uid, target.uid).sorted(),
                "status" to FriendRequestStatus.PENDING.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (QrCodeUtils.isValidConnectNumber(currentUser.connectNumber)) {
                payload["fromNumber"] = currentUser.connectNumber
            }
            if (QrCodeUtils.isValidConnectNumber(target.number)) {
                payload["toNumber"] = target.number
            }

            reqRef.set(payload).await()
            Result.success("Friend request sent to ${target.displayName}.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "friendRequests/$pairId")
            Result.failure(e)
        }
    }

    suspend fun respondToFriendRequest(
        request: FriendRequest,
        accept: Boolean
    ): Result<String?> {
        val uid = safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        val pairId = request.id.ifBlank { buildConversationId(request.fromUid, request.toUid) }
        val reqRef = db.collection("friendRequests").document(pairId)
        val newStatus = if (accept) FriendRequestStatus.ACCEPTED.name else FriendRequestStatus.REJECTED.name

        return try {
            reqRef.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (accept) {
                val friendRef = db.collection("friendships").document(pairId)
                val friendSnap = friendRef.get().await()
                val names = mapOf(
                    request.fromUid to request.fromName.ifBlank { "Friend" }.take(80),
                    request.toUid to request.toName.ifBlank { "Friend" }.take(80)
                )
                val photos = mapOf(
                    request.fromUid to request.fromPhoto.take(1000),
                    request.toUid to request.toPhoto.take(1000)
                )
                val numbers = buildMap {
                    if (request.fromNumber.isNotBlank()) put(request.fromUid, request.fromNumber)
                    if (request.toNumber.isNotBlank()) put(request.toUid, request.toNumber)
                }

                if (!friendSnap.exists()) {
                    friendRef.set(
                        mapOf(
                            "participantIds" to listOf(request.fromUid, request.toUid).sorted(),
                            "participantNames" to names,
                            "participantPhotos" to photos,
                            "participantNumbers" to numbers,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    friendRef.update(
                        mapOf(
                            "participantNames" to names,
                            "participantPhotos" to photos,
                            "participantNumbers" to numbers,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                val peerUser = DirectoryUser(
                    uid = if (request.fromUid == uid) request.toUid else request.fromUid,
                    displayName = if (request.fromUid == uid) request.toName else request.fromName,
                    photoUrl = if (request.fromUid == uid) request.toPhoto else request.fromPhoto,
                    connectNumber = if (request.fromUid == uid) request.toNumber else request.fromNumber
                )
                val meUser = DirectoryUser(
                    uid = uid,
                    displayName = if (request.fromUid == uid) request.fromName else request.toName,
                    photoUrl = if (request.fromUid == uid) request.fromPhoto else request.toPhoto,
                    connectNumber = if (request.fromUid == uid) request.fromNumber else request.toNumber
                )
                getOrCreateConversation(meUser, peerUser)
                return Result.success(pairId)
            }
            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "friendRequests/$pairId")
            Result.failure(e)
        }
    }

    // --- Conversations & Real-Time Rich Chat ---

    fun observeConversations(currentUid: String): Flow<List<Conversation>> = flow {
        val authedUid = requireUserId()
        check(authedUid == currentUid) { "UID mismatch" }
        val query = db.collection("conversations").whereArrayContains("participantIds", currentUid)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, "conversations", currentUid)
        )
    }

    fun buildConversationId(uidA: String, uidB: String): String =
        if (uidA < uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"

    suspend fun getOrCreateConversation(
        currentUser: DirectoryUser,
        peer: DirectoryUser,
        isFeedDirectMessage: Boolean = false
    ): Result<String> {
        return try {
            val uid = requireUserId()
            require(peer.uid.isNotBlank() && peer.uid != uid) { "Invalid peer user" }
            val convId = buildConversationId(uid, peer.uid)
            val convRef = db.collection("conversations").document(convId)
            val snap = convRef.get().await()
            val isFriend = runCatching {
                db.collection("friendships").document(convId).get().await().exists()
            }.getOrDefault(false)

            val namesMap = mapOf(
                uid to currentUser.displayName.ifBlank { "Me" }.take(80),
                peer.uid to peer.displayName.ifBlank { "Connect User" }.take(80)
            )
            val photosMap = mapOf(
                uid to currentUser.photoUrl.take(1000),
                peer.uid to peer.photoUrl.take(1000)
            )
            if (!snap.exists()) {
                if (!isFriend && !isFeedDirectMessage) {
                    return Result.failure(
                        IllegalStateException("World chat is disabled. Add ${peer.displayName} as a friend first to chat.")
                    )
                }
                val participants = listOf(uid, peer.uid).sorted()
                convRef.set(
                    mapOf(
                        "participantIds" to participants,
                        "participantNames" to namesMap,
                        "participantPhotos" to photosMap,
                        "typingBy" to emptyMap<String, Boolean>(),
                        "wallpaperBy" to emptyMap<String, String>(),
                        "isFriendChat" to isFriend,
                        "dmInitiatorUid" to if (isFriend) "" else uid,
                        "dmSenderMessageCount" to 0L,
                        "dmRecipientReplied" to isFriend,
                        "lastMessageText" to "",
                        "lastMessageSenderId" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "participantNames" to namesMap,
                    "participantPhotos" to photosMap,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (isFriend && snap.getBoolean("isFriendChat") == false) {
                    updates["isFriendChat"] = true
                    updates["dmRecipientReplied"] = true
                }
                convRef.update(updates).await()
            }
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "conversations")
            Result.failure(e)
        }
    }

    fun observeConversation(conversationId: String, currentUid: String): Flow<Conversation?> = flow {
        val authedUid = requireUserId()
        check(authedUid == currentUid) { "UID mismatch" }
        val ref = db.collection("conversations").document(conversationId)
        emitAll(
            ref.snapshots()
                .map { snap -> snap.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.GET, ref.path, currentUid)
        )
    }

    fun observeMessages(conversationId: String, currentUid: String): Flow<List<ChatMessage>> = flow {
        val authedUid = requireUserId()
        check(authedUid == currentUid) { "UID mismatch" }
        val path = "conversations/$conversationId/messages"
        val query = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .whereArrayContains("participantIds", currentUid)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, path, currentUid)
        )
    }

    suspend fun sendMessage(
        conversationId: String,
        participantIds: List<String>,
        senderName: String,
        text: String,
        messageType: MessageType = MessageType.TEXT,
        imageUrl: String = "",
        videoUrl: String = "",
        videoThumbUrl: String = "",
        videoDurationMs: Long = 0L,
        voiceUrl: String = "",
        voiceDurationMs: Long = 0L,
        voiceTransferId: String = "",
        deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,
        replyToId: String = "",
        replyToText: String = "",
        replyToSenderName: String = "",
        senderPhoto: String = ""
    ): Result<String> {
        val trimmedText = text.trim().take(2000)
        val trimmedImage = imageUrl.trim().take(1000)
        val trimmedVideo = videoUrl.trim().take(1000)
        val trimmedVoice = voiceUrl.trim().take(1000)
        if (messageType !in listOf(MessageType.IMAGE, MessageType.VOICE, MessageType.VIDEO) && trimmedText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty."))
        }
        if (messageType == MessageType.IMAGE && trimmedImage.isEmpty()) {
            return Result.failure(IllegalArgumentException("Image URL cannot be empty."))
        }
        if (messageType == MessageType.VIDEO && trimmedVideo.isEmpty()) {
            return Result.failure(IllegalArgumentException("Video URL cannot be empty."))
        }

        var docPath = "conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            require(participantIds.size == 2 && uid in participantIds) { "Invalid conversation participants" }
            val convRef = db.collection("conversations").document(conversationId)
            val convSnap = convRef.get().await()
            val isFriendChat = convSnap.getBoolean("isFriendChat") ?: true
            val dmInitiator = convSnap.getString("dmInitiatorUid").orEmpty()
            val dmCount = convSnap.getLong("dmSenderMessageCount") ?: 0L
            val dmReplied = convSnap.getBoolean("dmRecipientReplied") ?: false

            if (!isFriendChat && !dmReplied && dmInitiator == uid && dmCount >= 3L) {
                return Result.failure(
                    IllegalStateException("Feed DM limit (3 messages) reached. Chat unlocks when they reply or accept friend request.")
                )
            }

            val msgRef = convRef.collection("messages").document()
            docPath = msgRef.path

            val safeSenderName = senderName.trim().ifBlank { "Connect User" }.take(80)
            val msgPayload = mutableMapOf<String, Any>(
                "conversationId" to conversationId,
                "senderId" to uid,
                "senderName" to safeSenderName,
                "text" to trimmedText,
                "messageType" to messageType.name,
                "deliveryStatus" to deliveryStatus.name,
                "participantIds" to participantIds,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (trimmedImage.isNotBlank()) msgPayload["imageUrl"] = trimmedImage
            if (trimmedVideo.isNotBlank()) {
                msgPayload["videoUrl"] = trimmedVideo
                if (videoThumbUrl.isNotBlank()) msgPayload["videoThumbUrl"] = videoThumbUrl.take(1000)
                if (videoDurationMs > 0L) msgPayload["videoDurationMs"] = videoDurationMs.coerceIn(0L, 3_600_000L)
            }
            if (trimmedVoice.isNotBlank()) msgPayload["voiceUrl"] = trimmedVoice
            if (voiceDurationMs > 0L) msgPayload["voiceDurationMs"] = voiceDurationMs.coerceIn(0L, 600_000L)
            if (voiceTransferId.isNotBlank()) msgPayload["voiceTransferId"] = voiceTransferId.take(128)
            if (replyToId.isNotBlank()) {
                msgPayload["replyToId"] = replyToId.take(128)
                msgPayload["replyToText"] = replyToText.take(500)
                msgPayload["replyToSenderName"] = replyToSenderName.take(80)
            }

            msgRef.set(msgPayload).await()

            val preview = when (messageType) {
                MessageType.IMAGE -> if (trimmedText.isNotBlank()) "📷 $trimmedText" else "📷 Photo"
                MessageType.VIDEO -> if (trimmedText.isNotBlank()) "🎬 $trimmedText" else "🎬 Video"
                MessageType.VOICE -> "🎤 Voice message"
                else -> trimmedText
            }.take(500)

            val convUpdates = mutableMapOf<String, Any>(
                "lastMessageText" to preview,
                "lastMessageSenderId" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "typingBy.$uid" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!isFriendChat && !dmReplied) {
                if (uid == dmInitiator) {
                    convUpdates["dmSenderMessageCount"] = dmCount + 1L
                } else {
                    convUpdates["dmRecipientReplied"] = true
                }
            }
            convRef.update(convUpdates).await()

            // Create durable activity notification for offline/background recovery
            val recipientUid = participantIds.firstOrNull { it != uid }
            if (!recipientUid.isNullOrBlank()) {
                runCatching {
                    db.collection("notifications").document().set(
                        mapOf(
                            "recipientUid" to recipientUid,
                            "actorUid" to uid,
                            "actorName" to safeSenderName,
                            "actorPhoto" to senderPhoto.take(1000),
                            "type" to AppNotificationType.MESSAGE.name,
                            "title" to safeSenderName,
                            "body" to preview,
                            "targetId" to conversationId,
                            "delivered" to false,
                            "read" to false,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateMessageDeliveryStatus(
        conversationId: String,
        messageId: String,
        status: DeliveryStatus
    ): Result<Unit> {
        safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        return try {
            msgRef.update("deliveryStatus", status.name).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleMessageReaction(
        conversationId: String,
        message: ChatMessage,
        emoji: String
    ): Result<Unit> {
        val uid = safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(message.id)
        return try {
            val current = message.reactions[uid]
            val updateValue: Any = if (current == emoji) FieldValue.delete() else emoji.take(16)
            msgRef.update("reactions.$uid", updateValue).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(
        conversationId: String,
        messageId: String
    ): Result<Unit> {
        safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        val msgRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        return try {
            msgRef.update(
                mapOf(
                    "text" to "This message was deleted",
                    "imageUrl" to "",
                    "deleted" to true
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun setTypingStatus(conversationId: String, isTyping: Boolean) {
        val uid = safeUidOrNull() ?: return
        runCatching {
            db.collection("conversations").document(conversationId).update(
                mapOf(
                    "typingBy.$uid" to isTyping,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun setConversationWallpaper(conversationId: String, wallpaper: String): Result<Unit> {
        val uid = safeUidOrNull() ?: return Result.failure(IllegalStateException("Must be signed in."))
        val ref = db.collection("conversations").document(conversationId)
        return try {
            ref.update(
                mapOf(
                    "wallpaperBy.$uid" to wallpaper.trim().take(1000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    // --- WebRTC Call Signaling & Call History ---

    fun observeCalls(currentUid: String): Flow<List<CallSession>> = flow {
        val authedUid = requireUserId()
        check(authedUid == currentUid) { "UID mismatch" }
        val query = db.collection("calls").whereArrayContains("participantIds", currentUid)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .logFirestoreErrors(OperationType.LIST, "calls", currentUid)
        )
    }

    fun observeCallDocument(callId: String, currentUid: String): Flow<CallSession?> = flow {
        val authedUid = requireUserId()
        check(authedUid == currentUid) { "UID mismatch" }
        val ref = db.collection("calls").document(callId)
        emitAll(
            ref.snapshots()
                .map { snap -> snap.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.GET, ref.path, currentUid)
        )
    }

    suspend fun createCallSession(
        caller: DirectoryUser,
        callee: DirectoryUser,
        callType: CallType,
        offerSdp: String? = null,
        offerType: String? = null
    ): Result<String> {
        var docPath = "calls"
        return try {
            val uid = requireUserId()
            require(callee.uid.isNotBlank() && callee.uid != uid) { "Cannot call yourself" }
            val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            val callRef = db.collection("calls").document(callId)
            docPath = callRef.path

            val payload = mutableMapOf<String, Any>(
                "callerId" to uid,
                "callerName" to caller.displayName.ifBlank { "Connect User" }.take(80),
                "callerPhoto" to caller.photoUrl.take(1000),
                "calleeId" to callee.uid,
                "calleeName" to callee.displayName.ifBlank { "Connect User" }.take(80),
                "calleePhoto" to callee.photoUrl.take(1000),
                "participantIds" to listOf(uid, callee.uid),
                "callType" to callType.name,
                "status" to CallStatus.RINGING.name,
                "durationSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!offerSdp.isNullOrBlank()) {
                payload["offerSdp"] = offerSdp.take(25000)
                payload["offerType"] = (offerType ?: "offer").lowercase()
            }

            callRef.set(payload).await()
            Result.success(callId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun publishOffer(callId: String, offerSdp: String, offerType: String = "offer"): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "offerSdp" to offerSdp.take(25000),
                    "offerType" to offerType.lowercase(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun answerCall(callId: String, answerSdp: String, answerType: String = "answer"): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to CallStatus.CONNECTED.name,
                    "answerSdp" to answerSdp.take(25000),
                    "answerType" to answerType.lowercase(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun rejectCall(callId: String): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to CallStatus.REJECTED.name,
                    "endedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markCallMissed(callId: String): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to CallStatus.MISSED.name,
                    "endedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun endCall(callId: String, durationSeconds: Long): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            val safeDuration = durationSeconds.coerceIn(0L, 86400L)
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to CallStatus.ENDED.name,
                    "durationSeconds" to safeDuration,
                    "endedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addIceCandidate(
        callId: String,
        role: String,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String,
        participantIds: List<String>
    ): Result<Unit> {
        val path = "calls/$callId/candidates"
        return try {
            val uid = requireUserId()
            require(participantIds.size == 2 && uid in participantIds) { "Invalid call participants" }
            val candRef = db.collection("calls").document(callId).collection("candidates").document()
            candRef.set(
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
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeIceCandidates(callId: String, currentUid: String): Flow<List<IceCandidateItem>> = flow {
        val authedUid = requireUserId()
        check(authedUid == currentUid) { "UID mismatch" }
        val path = "calls/$callId/candidates"
        val query = db.collection("calls")
            .document(callId)
            .collection("candidates")
            .whereArrayContains("participantIds", currentUid)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(IceCandidateItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logFirestoreErrors(OperationType.LIST, path, currentUid)
        )
    }
}
