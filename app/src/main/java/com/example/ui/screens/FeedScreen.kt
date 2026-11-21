package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.model.DirectoryUser
import com.example.data.model.FeedComment
import com.example.data.model.FeedPost
import com.example.data.model.PostPrivacyLevel
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPillChip
import com.example.ui.components.LocalTouchFeedback
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.AzurePrimary
import com.example.ui.theme.CallCrimson
import com.example.ui.theme.CobaltDeep
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.SkyMist
import com.example.ui.viewmodel.FeedTabFilter
import com.example.ui.viewmodel.FeedViewModel

private val FEED_REACTION_EMOJIS = listOf("❤️", "🔥", "😂", "👍", "😍")

@Composable
fun FeedTab(
    feedViewModel: FeedViewModel,
    currentUser: DirectoryUser,
    onOpenDirectMessage: (DirectoryUser) -> Unit,
    onAddFriendFromCreator: (DirectoryUser) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = LocalTouchFeedback.current
    val selectedFilter by feedViewModel.selectedFilter.collectAsStateWithLifecycle()
    val postsState by feedViewModel.displayedPostsState.collectAsStateWithLifecycle()
    val followingIds by feedViewModel.followingUidSet.collectAsStateWithLifecycle()
    val friendIds by feedViewModel.friendUidSet.collectAsStateWithLifecycle()
    val isCreatingPost by feedViewModel.isCreatingPost.collectAsStateWithLifecycle()
    val uploadProgress by feedViewModel.uploadProgress.collectAsStateWithLifecycle()
    val activeCommentPostId by feedViewModel.activeCommentPostId.collectAsStateWithLifecycle()
    val comments by feedViewModel.commentsForSelectedPost.collectAsStateWithLifecycle()
    val replyTargetComment by feedViewModel.replyTargetComment.collectAsStateWithLifecycle()
    val isSubmittingComment by feedViewModel.isSubmittingComment.collectAsStateWithLifecycle()
    val feedStatus by feedViewModel.feedStatusMessage.collectAsStateWithLifecycle()
    val creatorProfile by feedViewModel.creatorProfile.collectAsStateWithLifecycle()

    var showCreateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(feedStatus) {
        feedStatus?.let {
            onShowMessage(it)
            feedViewModel.clearStatusMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = postsState) {
            UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                EmptyStateView(
                    title = "Unable to load Feed",
                    subtitle = state.message,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is UiState.Success -> {
                val posts = state.data
                if (posts.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        EmptyStateView(
                            title = if (selectedFilter == FeedTabFilter.FOLLOWING) {
                                "No Following posts yet"
                            } else {
                                "No Feed posts yet"
                            },
                            subtitle = "Be the first to share photos, videos & stories with Connect Live."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showCreateSheet = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("empty_create_post_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create First Post")
                        }
                    }
                } else {
                    val pagerState = rememberPagerState(pageCount = { posts.size })
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 84.dp)
                    ) { pageIndex ->
                        val post = posts[pageIndex]
                        val isCurrentPage = pagerState.currentPage == pageIndex
                        FeedPostPage(
                            post = post,
                            isActivePage = isCurrentPage,
                            currentUserId = feedViewModel.currentUserId,
                            isFollowingAuthor = post.authorUid in followingIds,
                            canReact = feedViewModel.canUserReact(post),
                            onToggleFollow = {
                                feedback?.triggerTap()
                                feedViewModel.toggleFollow(currentUser, post.authorUid, post.authorName)
                            },
                            onOpenCreator = {
                                feedback?.triggerTap()
                                feedViewModel.openCreatorProfile(post.authorUid)
                            },
                            onReact = { emoji ->
                                feedback?.triggerTap()
                                feedViewModel.toggleReaction(post, currentUser, emoji)
                            },
                            onOpenComments = {
                                feedback?.triggerTap()
                                feedViewModel.openComments(post.id)
                            },
                            onRepost = {
                                feedback?.triggerTap()
                                feedViewModel.repost(post, currentUser)
                            },
                            onShare = {
                                feedback?.triggerTap()
                                feedViewModel.incrementShare(post.id)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "${post.authorName}: ${post.title}\n${post.description}\n${post.videoUrl.ifBlank { post.mediaUrls.firstOrNull().orEmpty() }}"
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Post"))
                            },
                            onMessageAuthor = {
                                feedback?.triggerTap()
                                onOpenDirectMessage(
                                    DirectoryUser(
                                        uid = post.authorUid,
                                        displayName = post.authorName,
                                        displayNameLower = post.authorName.lowercase(),
                                        photoUrl = post.authorPhoto,
                                        connectNumber = post.authorNumber
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        // Top Floating Following | For You Segmented Pill + Create Button
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassPillChip(
                containerColor = Color(0xCC0C172C),
                borderColor = Color.White.copy(alpha = 0.25f)
            ) {
                FeedTabFilter.entries.forEach { tab ->
                    val active = selectedFilter == tab
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (active) AzurePrimary else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                feedback?.triggerTap()
                                feedViewModel.selectFilter(tab)
                            }
                            .testTag("feed_filter_${tab.name.lowercase()}")
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White.copy(alpha = if (active) 1f else 0.72f),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    feedback?.triggerTap()
                    showCreateSheet = true
                },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("feed_create_post_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Post", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Create Post Sheet
    if (showCreateSheet) {
        CreateFeedPostSheet(
            isCreating = isCreatingPost,
            uploadProgress = uploadProgress,
            onDismiss = { if (!isCreatingPost) showCreateSheet = false },
            onPublish = { title, desc, images, video, viewPriv, likePriv, commentPriv ->
                feedViewModel.createPost(
                    context = context,
                    author = currentUser,
                    title = title,
                    description = desc,
                    imageUris = images,
                    videoUri = video,
                    viewVisibility = viewPriv,
                    likePermission = likePriv,
                    commentPermission = commentPriv,
                    onCreated = { showCreateSheet = false }
                )
            }
        )
    }

    // Comments Sheet
    val commentTargetPost = (postsState as? UiState.Success)?.data
        ?.firstOrNull { it.id == activeCommentPostId }
    if (commentTargetPost != null) {
        FeedCommentsSheet(
            post = commentTargetPost,
            comments = comments,
            canComment = feedViewModel.canUserComment(commentTargetPost),
            replyTarget = replyTargetComment,
            isSubmitting = isSubmittingComment,
            onSetReplyTarget = feedViewModel::setReplyComment,
            onSendComment = { txt, imgUri ->
                feedViewModel.addComment(context, commentTargetPost, currentUser, txt, imgUri)
            },
            onDismiss = feedViewModel::closeComments
        )
    }

    // Creator Profile Sheet
    creatorProfile?.let { creator ->
        CreatorProfileSheet(
            creator = creator,
            isMe = creator.uid == feedViewModel.currentUserId,
            isFollowing = creator.uid in followingIds,
            isFriend = creator.uid in friendIds,
            onToggleFollow = {
                feedViewModel.toggleFollow(currentUser, creator.uid, creator.displayName)
            },
            onAddFriend = {
                onAddFriendFromCreator(creator)
            },
            onDirectMessage = {
                feedViewModel.dismissCreatorProfile()
                onOpenDirectMessage(creator)
            },
            onDismiss = feedViewModel::dismissCreatorProfile
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedPostPage(
    post: FeedPost,
    isActivePage: Boolean,
    currentUserId: String,
    isFollowingAuthor: Boolean,
    canReact: Boolean,
    onToggleFollow: () -> Unit,
    onOpenCreator: () -> Unit,
    onReact: (String) -> Unit,
    onOpenComments: () -> Unit,
    onRepost: () -> Unit,
    onShare: () -> Unit,
    onMessageAuthor: () -> Unit
) {
    var showReactionTray by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    val myReaction = post.reactionsBy[currentUserId]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF0A1426))
    ) {
        // Media Stage
        when {
            post.videoUrl.isNotBlank() -> {
                FeedExoPlayerSurface(
                    videoUrl = post.videoUrl,
                    isActive = isActivePage,
                    isMuted = isMuted,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 64.dp, end = 14.dp)
                        .background(Color(0x88000000), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = if (isMuted) "Unmute video" else "Mute video",
                        tint = Color.White
                    )
                }
            }
            post.mediaUrls.isNotEmpty() -> {
                val imgPagerState = rememberPagerState(pageCount = { post.mediaUrls.size })
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = imgPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { imgIdx ->
                        AsyncImage(
                            model = post.mediaUrls[imgIdx],
                            contentDescription = post.title.ifBlank { "Feed photo" },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (post.mediaUrls.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x99000000),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 64.dp, end = 16.dp)
                        ) {
                            Text(
                                text = "${imgPagerState.currentPage + 1}/${post.mediaUrls.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(CobaltDeep, Color(0xFF153B6E), AzurePrimary)
                            )
                        )
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (post.title.isNotBlank()) {
                            Text(
                                text = post.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (post.description.isNotBlank()) {
                            Text(
                                text = post.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Contrast Scrim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(230.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xD9050B16))
                    )
                )
        )

        // Author + Title + Description Bottom Left
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (post.repostedFromPostId.isNotBlank()) {
                GlassPillChip(
                    containerColor = Color(0x8810203A),
                    borderColor = OnlineEmerald.copy(alpha = 0.45f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = null,
                        tint = OnlineEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reposted from ${post.repostedByName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenCreator() }
                ) {
                    UserAvatar(
                        name = post.authorName,
                        photoUrl = post.authorPhoto,
                        size = 42.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = post.authorName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (post.parsedViewVisibility == PostPrivacyLevel.PUBLIC) {
                                    Icons.Rounded.Public
                                } else {
                                    Icons.Rounded.Lock
                                },
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.72f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${post.parsedViewVisibility.label} • ${formatTimestampShort(post.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                if (post.authorUid != currentUserId) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isFollowingAuthor) Color(0x66FFFFFF) else AzurePrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onToggleFollow() }
                            .testTag("feed_follow_${post.id}")
                    ) {
                        Text(
                            text = if (isFollowingAuthor) "Following" else "Follow",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (post.title.isNotBlank() && (post.mediaUrls.isNotEmpty() || post.videoUrl.isNotBlank())) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (post.description.isNotBlank() && (post.mediaUrls.isNotEmpty() || post.videoUrl.isNotBlank())) {
                Text(
                    text = post.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right-Side Vertical Social Action Rail
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Emoji Reaction Picker Popup
            if (showReactionTray) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xEB0B172E),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FEED_REACTION_EMOJIS.forEach { emoji ->
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier
                                    .clickable {
                                        showReactionTray = false
                                        onReact(emoji)
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }

            // Like / React
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = Color(0x880B162C),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = canReact,
                            onClick = { onReact(myReaction ?: "❤️") },
                            onLongClick = { if (canReact) showReactionTray = !showReactionTray }
                        )
                        .testTag("feed_like_${post.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (myReaction != null) {
                            Text(text = myReaction, style = MaterialTheme.typography.titleLarge)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.FavoriteBorder,
                                contentDescription = "Like post",
                                tint = if (canReact) Color.White else Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
                Text(
                    text = post.likeCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            FeedActionRailButton(
                icon = Icons.Rounded.Comment,
                countText = post.commentCount.toString(),
                contentDesc = "Comments",
                tag = "feed_comments_${post.id}",
                onClick = onOpenComments
            )

            FeedActionRailButton(
                icon = Icons.Rounded.Repeat,
                countText = post.repostCount.toString(),
                contentDesc = "Repost",
                tag = "feed_repost_${post.id}",
                onClick = onRepost
            )

            FeedActionRailButton(
                icon = Icons.Rounded.Share,
                countText = post.shareCount.toString(),
                contentDesc = "Share",
                tag = "feed_share_${post.id}",
                onClick = onShare
            )

            if (post.authorUid != currentUserId) {
                FeedActionRailButton(
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    countText = "DM",
                    contentDesc = "Message creator",
                    tag = "feed_dm_${post.id}",
                    tint = SkyMist,
                    onClick = onMessageAuthor
                )
            }
        }
    }
}

@Composable
private fun FeedActionRailButton(
    icon: ImageVector,
    countText: String,
    contentDesc: String,
    tag: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0x880B162C),
                contentColor = tint
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag(tag)
        ) {
            Icon(imageVector = icon, contentDescription = contentDesc)
        }
        Text(
            text = countText,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FeedExoPlayerSurface(
    videoUrl: String,
    isActive: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    LaunchedEffect(player, isActive) {
        player.playWhenReady = isActive
        if (!isActive) player.pause()
    }

    LaunchedEffect(player, isMuted) {
        player.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier.clickable {
            if (player.isPlaying) player.pause() else player.play()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFeedPostSheet(
    isCreating: Boolean,
    uploadProgress: Float,
    onDismiss: () -> Unit,
    onPublish: (
        title: String,
        description: String,
        imageUris: List<Uri>,
        videoUri: Uri?,
        viewVisibility: PostPrivacyLevel,
        likePermission: PostPrivacyLevel,
        commentPermission: PostPrivacyLevel
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }
    var viewVisibility by remember { mutableStateOf(PostPrivacyLevel.PUBLIC) }
    var likePermission by remember { mutableStateOf(PostPrivacyLevel.PUBLIC) }
    var commentPermission by remember { mutableStateOf(PostPrivacyLevel.PUBLIC) }

    val multiImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) selectedImages = uris
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedVideo = uri
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Create Feed Post",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (isCreating) {
                LinearProgressIndicator(
                    progress = { uploadProgress.coerceIn(0.05f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        multiImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isCreating,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("create_post_pick_images")
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Photos (${selectedImages.size})", maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        videoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    enabled = !isCreating,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("create_post_pick_video")
                ) {
                    Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (selectedVideo != null) "1 Video" else "Add Video", maxLines = 1)
                }
            }

            if (selectedImages.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedImages) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }

            if (selectedVideo != null) {
                GlassPillChip(borderColor = AzurePrimary.copy(alpha = 0.35f)) {
                    Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = AzurePrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Video ready to upload via Cloudinary", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { selectedVideo = null }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove video", modifier = Modifier.size(16.dp))
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Post Title") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_post_title_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Story") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_post_desc_input")
            )

            PrivacySelectorRow(
                label = "Who can view",
                selected = viewVisibility,
                onSelect = { viewVisibility = it }
            )
            PrivacySelectorRow(
                label = "Who can react & like",
                selected = likePermission,
                onSelect = { likePermission = it }
            )
            PrivacySelectorRow(
                label = "Who can comment",
                selected = commentPermission,
                onSelect = { commentPermission = it }
            )

            Button(
                onClick = {
                    onPublish(
                        title,
                        description,
                        selectedImages,
                        selectedVideo,
                        viewVisibility,
                        likePermission,
                        commentPermission
                    )
                },
                enabled = !isCreating && (
                    title.isNotBlank() || description.isNotBlank() || selectedImages.isNotEmpty() || selectedVideo != null
                    ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("publish_feed_post_button")
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Publishing...")
                } else {
                    Text("Publish Post", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PrivacySelectorRow(
    label: String,
    selected: PostPrivacyLevel,
    onSelect: (PostPrivacyLevel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PostPrivacyLevel.entries.forEach { level ->
                val active = selected == level
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) AzurePrimary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(level) }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = level.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedCommentsSheet(
    post: FeedPost,
    comments: List<FeedComment>,
    canComment: Boolean,
    replyTarget: FeedComment?,
    isSubmitting: Boolean,
    onSetReplyTarget: (FeedComment?) -> Unit,
    onSendComment: (text: String, imageUri: Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var commentText by remember { mutableStateOf("") }
    var attachedImage by remember { mutableStateOf<Uri?>(null) }

    val commentImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) attachedImage = uri
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 340.dp, max = 560.dp)
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 18.dp)
        ) {
            Text(
                text = "Comments (${post.commentCount})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Text(
                            text = "No comments yet. Start the conversation!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                } else {
                    items(comments, key = { it.id }) { item ->
                        GlassCard(cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    UserAvatar(
                                        name = item.authorName,
                                        photoUrl = item.authorPhoto,
                                        size = 32.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.authorName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = formatTimestampShort(item.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (canComment) {
                                        TextButton(onClick = { onSetReplyTarget(item) }) {
                                            Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reply")
                                        }
                                    }
                                }

                                if (item.replyToCommentId.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = AzurePrimary.copy(alpha = 0.08f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Replying to ${item.replyToAuthorName}: ${item.replyToText}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AzurePrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                if (item.text.isNotBlank()) {
                                    Text(text = item.text, style = MaterialTheme.typography.bodyMedium)
                                }

                                if (item.imageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = "Comment image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(150.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (canComment) {
                replyTarget?.let { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AzurePrimary.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Replying to ${target.authorName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AzurePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onSetReplyTarget(null) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                attachedImage?.let { uri ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Attached comment photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Image attached", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { attachedImage = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove photo")
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            commentImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("comment_attach_image_button")
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Attach comment image", tint = AzurePrimary)
                    }
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Add a comment or reply...") },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comment_text_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            val txt = commentText
                            val img = attachedImage
                            commentText = ""
                            attachedImage = null
                            onSendComment(txt, img)
                        },
                        enabled = !isSubmitting && (commentText.isNotBlank() || attachedImage != null),
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send comment")
                        }
                    }
                }
            } else {
                Text(
                    text = "Comments are restricted (${post.parsedCommentPermission.label}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatorProfileSheet(
    creator: DirectoryUser,
    isMe: Boolean,
    isFollowing: Boolean,
    isFriend: Boolean,
    onToggleFollow: () -> Unit,
    onAddFriend: () -> Unit,
    onDirectMessage: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserAvatar(
                name = creator.displayName,
                photoUrl = creator.photoUrl,
                size = 82.dp,
                online = creator.online
            )
            Text(
                text = creator.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (creator.connectNumber.isNotBlank()) {
                GlassPillChip {
                    Text(
                        text = "Connect ID: ${creator.connectNumber}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
            if (creator.bio.isNotBlank() || creator.statusText.isNotBlank()) {
                Text(
                    text = creator.bio.ifBlank { creator.statusText },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn("Followers", creator.followersCount.toString())
                StatColumn("Following", creator.followingCount.toString())
                StatColumn("Likes", creator.totalLikesCount.toString())
                StatColumn("Posts", creator.postsCount.toString())
            }

            if (!isMe) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onToggleFollow,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(if (isFollowing) "Following" else "Follow", fontWeight = FontWeight.Bold)
                    }
                    if (!isFriend) {
                        OutlinedButton(
                            onClick = onAddFriend,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Friend")
                        }
                    }
                }
                OutlinedButton(
                    onClick = onDirectMessage,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isFriend) "Message Friend" else "Send Direct Message (3-Message Intro Limit)"
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
