package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.media.CloudinaryService
import com.example.data.media.ImgBbService
import com.example.data.media.SafMediaManager
import com.example.data.model.DirectoryUser
import com.example.data.model.FeedComment
import com.example.data.model.FeedMediaType
import com.example.data.model.FeedPost
import com.example.data.model.Friendship
import com.example.data.model.PostPrivacyLevel
import com.example.data.model.UiState
import com.example.data.prefs.RingtonePreferences
import com.example.data.repository.ConnectLiveRepository
import com.example.data.repository.SocialAndMediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeedTabFilter(val label: String) {
    FOR_YOU("For You"),
    FOLLOWING("Following")
}

class FeedViewModel(
    private val socialRepository: SocialAndMediaRepository,
    private val chatRepository: ConnectLiveRepository,
    private val ringtonePreferences: RingtonePreferences,
    val currentUserId: String,
    private val imgBbService: ImgBbService = ImgBbService(),
    private val cloudinaryService: CloudinaryService = CloudinaryService()
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(FeedTabFilter.FOR_YOU)
    val selectedFilter: StateFlow<FeedTabFilter> = _selectedFilter.asStateFlow()

    private val _isCreatingPost = MutableStateFlow(false)
    val isCreatingPost: StateFlow<Boolean> = _isCreatingPost.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    private val _isSubmittingComment = MutableStateFlow(false)
    val isSubmittingComment: StateFlow<Boolean> = _isSubmittingComment.asStateFlow()

    private val _feedStatusMessage = MutableStateFlow<String?>(null)
    val feedStatusMessage: StateFlow<String?> = _feedStatusMessage.asStateFlow()

    private val _activeCommentPostId = MutableStateFlow<String?>(null)
    val activeCommentPostId: StateFlow<String?> = _activeCommentPostId.asStateFlow()

    private val _replyTargetComment = MutableStateFlow<FeedComment?>(null)
    val replyTargetComment: StateFlow<FeedComment?> = _replyTargetComment.asStateFlow()

    private val _creatorProfile = MutableStateFlow<DirectoryUser?>(null)
    val creatorProfile: StateFlow<DirectoryUser?> = _creatorProfile.asStateFlow()

    val friendships: StateFlow<List<Friendship>> = chatRepository
        .observeFriendships(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val friendUidSet: StateFlow<Set<String>> = friendships
        .map { list -> list.map { it.peerUid(currentUserId) }.filter { it.isNotBlank() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val followingUidSet: StateFlow<Set<String>> = socialRepository
        .observeFollowingRelations(currentUserId)
        .map { rels -> rels.map { it.targetUid }.toSet() }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allVisiblePostsState: StateFlow<UiState<List<FeedPost>>> = friendUidSet
        .flatMapLatest { friends ->
            socialRepository.observeVisibleFeedPosts(currentUserId, friends.toList())
                .map<List<FeedPost>, UiState<List<FeedPost>>> { UiState.Success(it) }
                .catch { e ->
                    Log.w(TAG, "Error loading feed posts", e)
                    emit(UiState.Error(e.localizedMessage ?: "Failed to load feed"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val displayedPostsState: StateFlow<UiState<List<FeedPost>>> = combine(
        allVisiblePostsState,
        _selectedFilter,
        followingUidSet
    ) { state, filter, following ->
        when (state) {
            is UiState.Success -> {
                val filtered = if (filter == FeedTabFilter.FOLLOWING) {
                    state.data.filter { it.authorUid in following || it.authorUid == currentUserId }
                } else {
                    state.data
                }
                UiState.Success(filtered)
            }
            else -> state
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val commentsForSelectedPost: StateFlow<List<FeedComment>> = _activeCommentPostId
        .flatMapLatest { postId ->
            if (postId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                socialRepository.observePostComments(postId).catch { emit(emptyList()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun selectFilter(filter: FeedTabFilter) {
        _selectedFilter.value = filter
    }

    fun openComments(postId: String) {
        _activeCommentPostId.value = postId
        _replyTargetComment.value = null
    }

    fun closeComments() {
        _activeCommentPostId.value = null
        _replyTargetComment.value = null
    }

    fun setReplyComment(comment: FeedComment?) {
        _replyTargetComment.value = comment
    }

    fun clearStatusMessage() {
        _feedStatusMessage.value = null
    }

    fun canUserReact(post: FeedPost): Boolean {
        if (post.authorUid == currentUserId) return true
        return when (post.parsedLikePermission) {
            PostPrivacyLevel.PUBLIC -> true
            PostPrivacyLevel.FRIENDS -> post.authorUid in friendUidSet.value
            PostPrivacyLevel.ONLY_ME -> false
        }
    }

    fun canUserComment(post: FeedPost): Boolean {
        if (post.authorUid == currentUserId) return true
        return when (post.parsedCommentPermission) {
            PostPrivacyLevel.PUBLIC -> true
            PostPrivacyLevel.FRIENDS -> post.authorUid in friendUidSet.value
            PostPrivacyLevel.ONLY_ME -> false
        }
    }

    fun createPost(
        context: Context,
        author: DirectoryUser,
        title: String,
        description: String,
        imageUris: List<Uri>,
        videoUri: Uri?,
        viewVisibility: PostPrivacyLevel,
        likePermission: PostPrivacyLevel,
        commentPermission: PostPrivacyLevel,
        onCreated: () -> Unit = {}
    ) {
        if (_isCreatingPost.value) return
        viewModelScope.launch {
            _isCreatingPost.value = true
            _uploadProgress.value = 0.05f
            val config = ringtonePreferences.getConfig()
            val safUri = config.safTreeUri
            val uploadedImages = mutableListOf<String>()

            if (imageUris.isNotEmpty()) {
                val step = 0.6f / imageUris.size.coerceAtLeast(1)
                for ((idx, uri) in imageUris.take(6).withIndex()) {
                    if (safUri.isNotBlank()) {
                        SafMediaManager.copyUriToSafFolder(
                            context = context,
                            safTreeUri = safUri,
                            subfolder = SafMediaManager.SUB_IMAGES,
                            sourceUri = uri,
                            fileName = "feed_${System.currentTimeMillis()}_$idx.jpg",
                            mimeType = "image/jpeg"
                        )
                    }
                    val res = imgBbService.uploadImage(
                        context = context,
                        uri = uri,
                        namePrefix = "feed_$currentUserId",
                        overrideApiKey = config.customImgBbKey
                    )
                    if (res.isFailure) {
                        _isCreatingPost.value = false
                        _uploadProgress.value = 0f
                        _feedStatusMessage.value = res.exceptionOrNull()?.localizedMessage
                            ?: "Image upload failed. Set ImgBB key in Media Settings."
                        return@launch
                    }
                    uploadedImages += res.getOrThrow().url
                    _uploadProgress.value = 0.1f + (idx + 1) * step
                }
            }

            var uploadedVideoUrl = ""
            if (videoUri != null) {
                if (safUri.isNotBlank()) {
                    SafMediaManager.copyUriToSafFolder(
                        context = context,
                        safTreeUri = safUri,
                        subfolder = SafMediaManager.SUB_VIDEOS,
                        sourceUri = videoUri,
                        fileName = "feed_vid_${System.currentTimeMillis()}.mp4",
                        mimeType = "video/mp4"
                    )
                }
                val vidRes = cloudinaryService.uploadVideo(
                    context = context,
                    videoUri = videoUri,
                    overrideCloudName = config.cloudinaryCloudName,
                    overridePreset = config.cloudinaryUploadPreset,
                    folderName = "connect_live_feed",
                    onProgress = { p -> _uploadProgress.value = (0.3f + p * 0.65f).coerceIn(0.3f, 0.96f) }
                )
                if (vidRes.isFailure) {
                    _isCreatingPost.value = false
                    _uploadProgress.value = 0f
                    _feedStatusMessage.value = vidRes.exceptionOrNull()?.localizedMessage
                        ?: "Video upload failed. Configure Cloudinary in Media Settings."
                    return@launch
                }
                val v = vidRes.getOrThrow()
                uploadedVideoUrl = v.videoUrl
                if (uploadedImages.isEmpty() && v.thumbnailUrl.isNotBlank()) {
                    uploadedImages += v.thumbnailUrl
                }
            }

            val mediaType = if (uploadedVideoUrl.isNotBlank()) FeedMediaType.VIDEO else FeedMediaType.IMAGE
            val createRes = socialRepository.createFeedPost(
                author = author,
                title = title,
                description = description,
                mediaUrls = uploadedImages,
                mediaType = mediaType,
                videoUrl = uploadedVideoUrl,
                viewVisibility = viewVisibility,
                likePermission = likePermission,
                commentPermission = commentPermission
            )
            _isCreatingPost.value = false
            _uploadProgress.value = 0f
            if (createRes.isSuccess) {
                _feedStatusMessage.value = "Published to Connect Live Feed!"
                onCreated()
            } else {
                _feedStatusMessage.value = createRes.exceptionOrNull()?.localizedMessage ?: "Failed to publish post."
            }
        }
    }

    fun toggleReaction(post: FeedPost, actor: DirectoryUser, emoji: String = "❤️") {
        if (!canUserReact(post)) {
            _feedStatusMessage.value = "Reactions restricted by post privacy (${post.parsedLikePermission.label})."
            return
        }
        viewModelScope.launch {
            val res = socialRepository.togglePostReaction(post, actor, emoji)
            if (res.isFailure) {
                _feedStatusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Unable to react."
            }
        }
    }

    fun addComment(
        context: Context,
        post: FeedPost,
        actor: DirectoryUser,
        text: String,
        imageUri: Uri?
    ) {
        if (!canUserComment(post)) {
            _feedStatusMessage.value = "Comments restricted by post privacy (${post.parsedCommentPermission.label})."
            return
        }
        if (_isSubmittingComment.value) return
        val replyTarget = _replyTargetComment.value
        viewModelScope.launch {
            _isSubmittingComment.value = true
            var uploadedImageUrl = ""
            if (imageUri != null) {
                val config = ringtonePreferences.getConfig()
                if (config.safTreeUri.isNotBlank()) {
                    SafMediaManager.copyUriToSafFolder(
                        context = context,
                        safTreeUri = config.safTreeUri,
                        subfolder = SafMediaManager.SUB_IMAGES,
                        sourceUri = imageUri,
                        fileName = "comment_${System.currentTimeMillis()}.jpg",
                        mimeType = "image/jpeg"
                    )
                }
                val imgRes = imgBbService.uploadImage(
                    context = context,
                    uri = imageUri,
                    namePrefix = "comment_${post.id}",
                    overrideApiKey = config.customImgBbKey
                )
                if (imgRes.isFailure) {
                    _isSubmittingComment.value = false
                    _feedStatusMessage.value = imgRes.exceptionOrNull()?.localizedMessage
                        ?: "Comment image upload failed."
                    return@launch
                }
                uploadedImageUrl = imgRes.getOrThrow().url
            }

            val res = socialRepository.addPostComment(
                post = post,
                actor = actor,
                text = text,
                imageUrl = uploadedImageUrl,
                replyTo = replyTarget
            )
            _isSubmittingComment.value = false
            if (res.isSuccess) {
                _replyTargetComment.value = null
            } else {
                _feedStatusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to post comment."
            }
        }
    }

    fun repost(post: FeedPost, actor: DirectoryUser) {
        viewModelScope.launch {
            val res = socialRepository.repostFeedPost(post, actor)
            _feedStatusMessage.value = if (res.isSuccess) {
                "Reposted to your followers!"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Repost failed."
            }
        }
    }

    fun incrementShare(postId: String) {
        viewModelScope.launch {
            socialRepository.incrementPostShare(postId)
        }
    }

    fun toggleFollow(actor: DirectoryUser, targetUid: String, targetName: String) {
        viewModelScope.launch {
            val res = socialRepository.toggleFollow(actor, targetUid, targetName)
            res.onSuccess { nowFollowing ->
                _feedStatusMessage.value = if (nowFollowing) "Following $targetName" else "Unfollowed $targetName"
            }.onFailure { err ->
                _feedStatusMessage.value = err.localizedMessage ?: "Unable to update follow status."
            }
        }
    }

    fun openCreatorProfile(targetUid: String) {
        _creatorProfile.value = null
        viewModelScope.launch {
            _creatorProfile.value = socialRepository.fetchCreatorProfile(targetUid)
        }
    }

    fun dismissCreatorProfile() {
        _creatorProfile.value = null
    }

    private companion object {
        const val TAG = "FeedViewModel"
    }
}
