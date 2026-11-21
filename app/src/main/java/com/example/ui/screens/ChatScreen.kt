package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PhoneMissed
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wallpaper
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.model.CallType
import com.example.data.model.ChatMessage
import com.example.data.model.DeliveryStatus
import com.example.data.model.DirectoryUser
import com.example.data.model.MessageType
import com.example.data.model.UiState
import com.example.data.prefs.ChatBubbleStyle
import com.example.data.prefs.RingtoneConfig
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassBackdrop
import com.example.ui.components.GlassPillChip
import com.example.ui.components.LocalTouchFeedback
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.isGlassDarkTheme
import com.example.ui.theme.AzurePrimary
import com.example.ui.theme.CallCrimson
import com.example.ui.theme.CobaltDeep
import com.example.ui.theme.FrostOutline
import com.example.ui.theme.MidnightOutline
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.RingingAmber
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.DetectedMessageEntity
import com.example.util.MessageEntityParser
import com.example.util.MessageEntityType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val QUICK_EMOJIS = listOf("❤️", "👍", "😂", "🔥", "😢")

private val WALLPAPER_PRESETS = listOf(
    "preset_default" to Pair("Default", listOf(Color.Transparent, Color.Transparent)),
    "preset_aurora" to Pair("Aurora", listOf(Color(0xFF062127), Color(0xFF0B1325))),
    "preset_indigo" to Pair("Galaxy", listOf(Color(0xFF14183E), Color(0xFF0A0F1D))),
    "preset_sunset" to Pair("Dusk", listOf(Color(0xFF2D1627), Color(0xFF101321)))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    currentUserName: String,
    peerDirectoryUser: DirectoryUser?,
    ringtoneConfig: RingtoneConfig = RingtoneConfig(),
    onBack: () -> Unit,
    onStartCall: (DirectoryUser, CallType) -> Unit,
    onResolveConnectNumberForCall: (suspend (String) -> DirectoryUser?)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val input by viewModel.messageInput.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isUploadingMedia by viewModel.isUploadingMedia.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val infoBanner by viewModel.infoBanner.collectAsStateWithLifecycle()

    val isRecordingVoice by viewModel.isRecordingVoice.collectAsStateWithLifecycle()
    val recordingElapsedMs by viewModel.recordingElapsedMs.collectAsStateWithLifecycle()
    val hasRecordedDraft by viewModel.hasRecordedDraft.collectAsStateWithLifecycle()
    val recordedDraftDurationMs by viewModel.recordedDraftDurationMs.collectAsStateWithLifecycle()
    val playingVoiceId by viewModel.playingVoiceId.collectAsStateWithLifecycle()
    val queuedVoiceCount by viewModel.queuedVoiceCount.collectAsStateWithLifecycle()
    val localVoiceRefreshTick by viewModel.localVoiceRefreshTick.collectAsStateWithLifecycle()

    val peer = peerDirectoryUser ?: viewModel.peerAsDirectoryUser()
    val peerName = peer?.displayName ?: conversation?.peerName(viewModel.currentUserId) ?: "Chat"
    val peerPhoto = peer?.photoUrl ?: conversation?.peerPhoto(viewModel.currentUserId) ?: ""
    val isPeerOnline = peerDirectoryUser?.online
    val isPeerTyping = conversation?.isPeerTyping(viewModel.currentUserId) == true
    val conversationWallpaper = conversation?.myWallpaper(viewModel.currentUserId).orEmpty()
    val effectiveWallpaper = conversationWallpaper.ifBlank { ringtoneConfig.defaultChatBgPreset }

    val isDmBlocked = conversation?.isDmLimitReached(viewModel.currentUserId) == true
    val remainingDm = conversation?.remainingDmMessages(viewModel.currentUserId) ?: Int.MAX_VALUE
    val showDmCapBanner = conversation?.let { !it.isFriendChat && !it.dmRecipientReplied } == true

    var showWallpaperSheet by remember { mutableStateOf(false) }
    var selectedMessageForActions by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedEntityForActions by remember { mutableStateOf<DetectedMessageEntity?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var fullscreenVideoUrl by remember { mutableStateOf<String?>(null) }

    val photoMessageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadAndSendImage(context, uri, currentUserName, peer?.uid)
        }
    }

    val videoMessageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadAndSendVideo(context, uri, currentUserName, peer?.uid)
        }
    }

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            showWallpaperSheet = false
            viewModel.uploadAndSetWallpaper(context, uri)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        } else {
            Toast.makeText(context, "Microphone permission is needed to record voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    val listState = rememberLazyListState()
    val messageCount = (messagesState as? UiState.Success)?.data?.size ?: 0
    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    val isDark = isGlassDarkTheme()
    val feedback = LocalTouchFeedback.current
    val frostedBarBg = if (isDark) {
        MidnightSurface.copy(alpha = 0.88f)
    } else {
        Color.White.copy(alpha = 0.88f)
    }

    GlassBackdrop(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = frostedBarBg
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                feedback?.triggerTap()
                                onBack()
                            },
                            modifier = Modifier.testTag("chat_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(
                                name = peerName,
                                photoUrl = peerPhoto,
                                size = 38.dp,
                                online = isPeerOnline
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = peerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        isPeerTyping -> "Typing..."
                                        isPeerOnline == true -> "Online now"
                                        isPeerOnline == false -> "Offline"
                                        else -> "Direct message"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        isPeerTyping -> MaterialTheme.colorScheme.primary
                                        isPeerOnline == true -> OnlineEmerald
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                feedback?.triggerTap()
                                viewModel.backupChatToFolder(context)
                            },
                            modifier = Modifier.testTag("chat_backup_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Backup,
                                contentDescription = "Backup chat to folder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                feedback?.triggerTap()
                                showWallpaperSheet = true
                            },
                            modifier = Modifier.testTag("chat_wallpaper_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Wallpaper,
                                contentDescription = "Chat background",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (peer != null) {
                            IconButton(
                                onClick = {
                                    feedback?.triggerTap()
                                    onStartCall(peer, CallType.AUDIO)
                                },
                                modifier = Modifier.testTag("chat_audio_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Call,
                                    contentDescription = "Start audio call",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    feedback?.triggerTap()
                                    onStartCall(peer, CallType.VIDEO)
                                },
                                modifier = Modifier.testTag("chat_video_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Videocam,
                                    contentDescription = "Start video call",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    color = frostedBarBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isUploadingMedia) {
                            LinearProgressIndicator(
                                progress = { uploadProgress.coerceIn(0.05f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        replyingTo?.let { replyMsg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(32.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Replying to ${replyMsg.senderName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = when (replyMsg.parsedType) {
                                            MessageType.IMAGE -> replyMsg.text.ifBlank { "📷 Photo" }
                                            MessageType.VIDEO -> replyMsg.text.ifBlank { "🎬 Video" }
                                            MessageType.VOICE -> "🎤 Voice note"
                                            else -> replyMsg.text
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { viewModel.setReplyTarget(null) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Cancel reply")
                                }
                            }
                        }

                        if (isRecordingVoice || hasRecordedDraft) {
                            VoiceRecordingComposerBar(
                                isRecording = isRecordingVoice,
                                durationMs = if (isRecordingVoice) recordingElapsedMs else recordedDraftDurationMs,
                                isSending = isSending,
                                onCancel = {
                                    feedback?.triggerTap()
                                    viewModel.cancelVoiceRecordingOrDraft()
                                },
                                onAgainRecord = {
                                    feedback?.triggerTap()
                                    viewModel.restartVoiceRecording()
                                },
                                onStopForReview = {
                                    feedback?.triggerTap()
                                    viewModel.stopVoiceRecordingForReview()
                                },
                                onSend = {
                                    feedback?.triggerTap()
                                    viewModel.sendVoiceNote(currentUserName, peer?.uid)
                                }
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        feedback?.triggerTap()
                                        photoMessageLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isUploadingMedia && !isDmBlocked,
                                    modifier = Modifier.testTag("chat_attach_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = "Send photo",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        feedback?.triggerTap()
                                        videoMessageLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    enabled = !isUploadingMedia && !isDmBlocked,
                                    modifier = Modifier.testTag("chat_attach_video_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VideoLibrary,
                                        contentDescription = "Send video",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                OutlinedTextField(
                                    value = input,
                                    onValueChange = viewModel::updateInput,
                                    enabled = !isDmBlocked,
                                    placeholder = {
                                        Text(
                                            if (isDmBlocked) "Reply required to continue..." else "Message $peerName..."
                                        )
                                    },
                                    singleLine = false,
                                    maxLines = 4,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (input.isNotBlank() && !isDmBlocked) {
                                                feedback?.triggerTap()
                                                viewModel.sendMessage(currentUserName, peer?.uid)
                                            }
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("chat_message_input")
                                )
                                Spacer(modifier = Modifier.width(6.dp))

                                val hasText = input.isNotBlank()
                                FilledIconButton(
                                    onClick = {
                                        feedback?.triggerTap()
                                        if (hasText) {
                                            viewModel.sendMessage(currentUserName, peer?.uid)
                                        } else {
                                            val hasMic = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasMic) {
                                                viewModel.startVoiceRecording()
                                            } else {
                                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                    enabled = !isSending && !isDmBlocked,
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = if (hasText) MaterialTheme.colorScheme.primary else CobaltDeep,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag(if (hasText) "chat_send_button" else "chat_voice_record_button")
                                ) {
                                    if (isSending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (hasText) Icons.AutoMirrored.Rounded.Send else Icons.Rounded.Mic,
                                            contentDescription = if (hasText) "Send message" else "Record voice message"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ChatWallpaperLayer(wallpaper = effectiveWallpaper)

                Column(modifier = Modifier.fillMaxSize()) {
                    if (showDmCapBanner) {
                        Surface(
                            color = (if (isDmBlocked) RingingAmber else AzurePrimary).copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isDmBlocked) {
                                    "Feed DM limit reached (3/3). Messaging unlocks once $peerName replies or accepts your friend request."
                                } else {
                                    "Feed Direct Message • $remainingDm message${if (remainingDm == 1) "" else "s"} remaining until $peerName replies."
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isDmBlocked) RingingAmber else AzurePrimary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (errorBanner != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.dismissError() }
                        ) {
                            Text(
                                text = errorBanner.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (infoBanner != null) {
                        Surface(
                            color = OnlineEmerald.copy(alpha = 0.14f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.dismissInfo() }
                        ) {
                            Text(
                                text = infoBanner.orEmpty(),
                                color = OnlineEmerald,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    when (val state = messagesState) {
                        UiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is UiState.Error -> {
                            EmptyStateView(
                                title = "Unable to load chat",
                                subtitle = state.message,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is UiState.Success -> {
                            if (state.data.isEmpty()) {
                                EmptyStateView(
                                    title = "Say hello to $peerName",
                                    subtitle = "Send a message, voice note, photo, or video — swipe right on any message to reply.",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(state.data, key = { it.id }) { msg ->
                                        val isPlayingVoice = playingVoiceId == msg.id
                                        val hasLocalVoice = remember(msg.voiceTransferId, localVoiceRefreshTick) {
                                            msg.voiceTransferId.isNotBlank() && viewModel.getVoiceFile(msg.voiceTransferId) != null
                                        }
                                        SwipeableReplyMessageContainer(
                                            enabled = msg.parsedType != MessageType.CALL_EVENT && !msg.deleted,
                                            onSwipeReply = {
                                                feedback?.triggerSwipe()
                                                viewModel.setReplyTarget(msg)
                                            }
                                        ) {
                                            MessageBubbleRow(
                                                message = msg,
                                                isMine = msg.senderId == viewModel.currentUserId,
                                                bubbleStyle = ringtoneConfig.chatBubbleStyle,
                                                isPlayingVoice = isPlayingVoice,
                                                hasLocalVoice = hasLocalVoice,
                                                queuedVoiceCount = queuedVoiceCount,
                                                onLongPress = {
                                                    feedback?.triggerTap()
                                                    selectedMessageForActions = msg
                                                },
                                                onImageClick = { url -> fullscreenImageUrl = url },
                                                onVideoClick = { url -> fullscreenVideoUrl = url },
                                                onVoiceToggle = {
                                                    feedback?.triggerTap()
                                                    viewModel.toggleVoicePlayback(msg)
                                                },
                                                onEntityTap = { entity ->
                                                    feedback?.triggerTap()
                                                    selectedEntityForActions = entity
                                                },
                                                onToggleEmoji = { emoji ->
                                                    feedback?.triggerTap()
                                                    viewModel.toggleReaction(msg, emoji)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Message Long-Press Actions Sheet
    selectedMessageForActions?.let { targetMsg ->
        ModalBottomSheet(
            onDismissRequest = { selectedMessageForActions = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QUICK_EMOJIS.forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    feedback?.triggerTap()
                                    viewModel.toggleReaction(targetMsg, emoji)
                                    selectedMessageForActions = null
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                ActionRowItem(
                    icon = Icons.AutoMirrored.Rounded.Reply,
                    label = "Reply",
                    onClick = {
                        viewModel.setReplyTarget(targetMsg)
                        selectedMessageForActions = null
                    }
                )
                if (targetMsg.text.isNotBlank() && !targetMsg.deleted) {
                    ActionRowItem(
                        icon = Icons.Rounded.ContentCopy,
                        label = "Copy Text",
                        onClick = {
                            clipboard.setText(AnnotatedString(targetMsg.text))
                            Toast.makeText(context, "Copied message", Toast.LENGTH_SHORT).show()
                            selectedMessageForActions = null
                        }
                    )
                }
                if (targetMsg.senderId == viewModel.currentUserId && !targetMsg.deleted) {
                    ActionRowItem(
                        icon = Icons.Rounded.DeleteOutline,
                        label = "Unsend Message",
                        tint = CallCrimson,
                        onClick = {
                            viewModel.deleteMessage(targetMsg)
                            selectedMessageForActions = null
                        }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    // Smart Entity Tap Bottom Sheet (URL / Email / Phone)
    selectedEntityForActions?.let { entity ->
        ModalBottomSheet(
            onDismissRequest = { selectedEntityForActions = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = entity.rawValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                when (entity.type) {
                    MessageEntityType.URL -> {
                        ActionRowItem(
                            icon = Icons.Rounded.ContentCopy,
                            label = "Copy Link",
                            onClick = {
                                clipboard.setText(AnnotatedString(entity.rawValue))
                                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                                selectedEntityForActions = null
                            }
                        )
                        ActionRowItem(
                            icon = Icons.Rounded.OpenInBrowser,
                            label = "Go to Link",
                            tint = AzurePrimary,
                            onClick = {
                                val normalized = MessageEntityParser.normalizeUrl(entity.rawValue)
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
                                }.onFailure {
                                    Toast.makeText(context, "No browser found for link", Toast.LENGTH_SHORT).show()
                                }
                                selectedEntityForActions = null
                            }
                        )
                    }

                    MessageEntityType.EMAIL -> {
                        ActionRowItem(
                            icon = Icons.Rounded.ContentCopy,
                            label = "Copy Email",
                            onClick = {
                                clipboard.setText(AnnotatedString(entity.rawValue))
                                Toast.makeText(context, "Email copied", Toast.LENGTH_SHORT).show()
                                selectedEntityForActions = null
                            }
                        )
                        ActionRowItem(
                            icon = Icons.Rounded.Email,
                            label = "Go to Email",
                            tint = AzurePrimary,
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${entity.rawValue}"))
                                    )
                                }.onFailure {
                                    Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                                }
                                selectedEntityForActions = null
                            }
                        )
                    }

                    MessageEntityType.PHONE -> {
                        val digits = entity.rawValue.filter { it.isDigit() }
                        ActionRowItem(
                            icon = Icons.Rounded.ContentCopy,
                            label = "Copy Number",
                            onClick = {
                                clipboard.setText(AnnotatedString(entity.rawValue))
                                Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                                selectedEntityForActions = null
                            }
                        )
                        ActionRowItem(
                            icon = Icons.Rounded.Call,
                            label = "Go to Call",
                            tint = OnlineEmerald,
                            onClick = {
                                selectedEntityForActions = null
                                val resolver = onResolveConnectNumberForCall
                                if (resolver != null && digits.length == 9 && digits.startsWith("01")) {
                                    scope.launch {
                                        val matched = resolver(digits)
                                        if (matched != null) {
                                            onStartCall(matched, CallType.AUDIO)
                                        } else {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entity.rawValue}"))
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entity.rawValue}"))
                                        )
                                    }
                                }
                            }
                        )
                        ActionRowItem(
                            icon = Icons.Rounded.Dialpad,
                            label = "Dial",
                            tint = AzurePrimary,
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entity.rawValue}"))
                                    )
                                }
                                selectedEntityForActions = null
                            }
                        )
                        ActionRowItem(
                            icon = Icons.Rounded.Contacts,
                            label = "Contact",
                            onClick = {
                                runCatching {
                                    val contactIntent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                        type = ContactsContract.RawContacts.CONTENT_TYPE
                                        putExtra(ContactsContract.Intents.Insert.PHONE, entity.rawValue)
                                    }
                                    context.startActivity(contactIntent)
                                }.onFailure {
                                    Toast.makeText(context, "Unable to open Contacts", Toast.LENGTH_SHORT).show()
                                }
                                selectedEntityForActions = null
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    // Chat Wallpaper Picker Sheet
    if (showWallpaperSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWallpaperSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Chat Background Wallpaper",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pick a gradient theme or upload a custom photo via ImgBB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WALLPAPER_PRESETS.forEach { (key, info) ->
                        val (label, colors) = info
                        val selected = (key == "preset_default" && conversationWallpaper.isBlank()) ||
                            conversationWallpaper == key
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    feedback?.triggerTap()
                                    viewModel.setWallpaperPreset(if (key == "preset_default") "" else key)
                                    showWallpaperSheet = false
                                }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (colors[0] == Color.Transparent) {
                                            Brush.verticalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                    MaterialTheme.colorScheme.background
                                                )
                                            )
                                        } else {
                                            Brush.verticalGradient(colors)
                                        }
                                    )
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        wallpaperPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Custom Wallpaper (ImgBB)")
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // Full-Screen Image Viewer Dialog
    fullscreenImageUrl?.let { url ->
        Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "Full screen image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { fullscreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    // Full-Screen Video Player Dialog (Media3 ExoPlayer)
    fullscreenVideoUrl?.let { url ->
        FullscreenChatVideoDialog(
            videoUrl = url,
            onDismiss = { fullscreenVideoUrl = null }
        )
    }
}

@Composable
private fun FullscreenChatVideoDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        this.player = player
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close video", tint = Color.White)
            }
        }
    }
}

@Composable
private fun VoiceRecordingComposerBar(
    isRecording: Boolean,
    durationMs: Long,
    isSending: Boolean,
    onCancel: () -> Unit,
    onAgainRecord: () -> Unit,
    onStopForReview: () -> Unit,
    onSend: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "rec_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier.testTag("voice_cancel_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Cancel voice note",
                tint = CallCrimson
            )
        }

        IconButton(
            onClick = onAgainRecord,
            modifier = Modifier.testTag("voice_again_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Again Record",
                tint = AzurePrimary
            )
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = if (isRecording) CallCrimson.copy(alpha = 0.12f) else AzurePrimary.copy(alpha = 0.12f),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(if (isRecording) pulseAlpha else 1f)
                        .clip(CircleShape)
                        .background(if (isRecording) CallCrimson else OnlineEmerald)
                )
                Text(
                    text = if (isRecording) {
                        "Recording ${formatDurationMmSs(durationMs)}"
                    } else {
                        "Voice Ready (${formatDurationMmSs(durationMs)})"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isRecording) {
                    IconButton(
                        onClick = onStopForReview,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("voice_stop_review_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop to review",
                            tint = CallCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))
        FilledIconButton(
            onClick = onSend,
            enabled = !isSending,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = OnlineEmerald,
                contentColor = Color.White
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("voice_send_button")
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send voice message")
            }
        }
    }
}

@Composable
private fun SwipeableReplyMessageContainer(
    enabled: Boolean,
    onSwipeReply: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

    val density = LocalDensity.current
    val triggerThresholdPx = remember(density) { with(density) { 56.dp.toPx() } }
    val maxDragPx = remember(density) { with(density) { 82.dp.toPx() } }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val feedback = LocalTouchFeedback.current
    var thresholdCrossed by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        val progress = (offsetX.value / triggerThresholdPx).coerceIn(0f, 1f)
        if (offsetX.value > 4f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(34.dp)
                    .scale(0.6f + 0.45f * progress)
                    .alpha(progress)
                    .clip(CircleShape)
                    .background(AzurePrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Reply,
                    contentDescription = null,
                    tint = AzurePrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { thresholdCrossed = false },
                        onHorizontalDrag = { _, dragAmount ->
                            val next = (offsetX.value + dragAmount).coerceIn(0f, maxDragPx)
                            if (!thresholdCrossed && next >= triggerThresholdPx) {
                                thresholdCrossed = true
                                feedback?.triggerSwipe()
                            }
                            scope.launch { offsetX.snapTo(next) }
                        },
                        onDragEnd = {
                            val shouldReply = offsetX.value >= triggerThresholdPx
                            scope.launch {
                                offsetX.animateTo(0f, animationSpec = spring(dampingRatio = 0.65f))
                            }
                            if (shouldReply) onSwipeReply()
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, animationSpec = spring(dampingRatio = 0.65f))
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
private fun ChatWallpaperLayer(wallpaper: String) {
    when {
        wallpaper.startsWith("http") -> {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = wallpaper,
                    contentDescription = "Chat background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.42f))
                )
            }
        }
        wallpaper.startsWith("preset_") -> {
            val colors = WALLPAPER_PRESETS.firstOrNull { it.first == wallpaper }?.second?.second
            if (colors != null && colors[0] != Color.Transparent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(colors))
                )
            }
        }
    }
}

@Composable
private fun ActionRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageBubbleRow(
    message: ChatMessage,
    isMine: Boolean,
    bubbleStyle: ChatBubbleStyle,
    isPlayingVoice: Boolean,
    hasLocalVoice: Boolean,
    queuedVoiceCount: Int,
    onLongPress: () -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onVoiceToggle: () -> Unit,
    onEntityTap: (DetectedMessageEntity) -> Unit,
    onToggleEmoji: (String) -> Unit
) {
    val isDark = isGlassDarkTheme()
    if (message.parsedType == MessageType.CALL_EVENT) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            GlassPillChip {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PhoneMissed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${message.text} • ${formatTimestampShort(message.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val cornerBig = if (bubbleStyle == ChatBubbleStyle.CLASSIC_ROUNDED) 18.dp else 22.dp
    val cornerTail = if (bubbleStyle == ChatBubbleStyle.CLASSIC_ROUNDED) 18.dp else 6.dp
    val bubbleShape = RoundedCornerShape(
        topStart = cornerBig,
        topEnd = cornerBig,
        bottomStart = if (isMine) cornerBig else cornerTail,
        bottomEnd = if (isMine) cornerTail else cornerBig
    )

    val incomingBg = when (bubbleStyle) {
        ChatBubbleStyle.MINIMAL_OUTLINE -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        else -> if (isDark) MidnightSurface.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.92f)
    }

    val outgoingBg = when (bubbleStyle) {
        ChatBubbleStyle.IOS_GLASS -> MaterialTheme.colorScheme.primary
        ChatBubbleStyle.CLASSIC_ROUNDED -> AzurePrimary
        ChatBubbleStyle.EMERALD_MINT -> OnlineEmerald
        ChatBubbleStyle.MIDNIGHT_NEON -> CobaltDeep
        ChatBubbleStyle.MINIMAL_OUTLINE -> if (isDark) CobaltDeep.copy(alpha = 0.35f) else AzurePrimary.copy(alpha = 0.14f)
    }

    val mineTextColor = if (bubbleStyle == ChatBubbleStyle.MINIMAL_OUTLINE) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color.White
    }

    val bubbleBorder = when (bubbleStyle) {
        ChatBubbleStyle.MINIMAL_OUTLINE -> if (isMine) AzurePrimary.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline
        ChatBubbleStyle.MIDNIGHT_NEON -> if (isMine) Color(0xFF60A5FA).copy(alpha = 0.7f) else MidnightOutline
        else -> if (isMine) {
            Color.White.copy(alpha = 0.25f)
        } else if (isDark) {
            MidnightOutline
        } else {
            FrostOutline
        }
    }

    val linkColor = if (isMine && bubbleStyle != ChatBubbleStyle.MINIMAL_OUTLINE) {
        Color(0xFFD6F0FF)
    } else {
        AzurePrimary
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = bubbleShape,
            color = if (isMine) outgoingBg else incomingBg,
            shadowElevation = if (bubbleStyle == ChatBubbleStyle.MINIMAL_OUTLINE) 0.dp else 2.dp,
            modifier = Modifier
                .widthIn(max = 298.dp)
                .border(1.dp, bubbleBorder, bubbleShape)
                .clip(bubbleShape)
                .combinedClickable(
                    onClick = {
                        when {
                            message.deleted -> Unit
                            message.parsedType == MessageType.IMAGE && message.imageUrl.isNotBlank() ->
                                onImageClick(message.imageUrl)
                            message.parsedType == MessageType.VIDEO && message.videoUrl.isNotBlank() ->
                                onVideoClick(message.videoUrl)
                            message.parsedType == MessageType.VOICE ->
                                onVoiceToggle()
                        }
                    },
                    onLongClick = onLongPress
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
                if (message.replyToId.isNotBlank() && !message.deleted) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMine && bubbleStyle != ChatBubbleStyle.MINIMAL_OUTLINE) {
                            Color.White.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                            Text(
                                text = message.replyToSenderName.ifBlank { "Reply" },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isMine && bubbleStyle != ChatBubbleStyle.MINIMAL_OUTLINE) Color.White else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = message.replyToText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isMine && bubbleStyle != ChatBubbleStyle.MINIMAL_OUTLINE) {
                                    Color.White.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (message.parsedType == MessageType.IMAGE && message.imageUrl.isNotBlank() && !message.deleted) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Shared photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 240.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    if (message.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                if (message.parsedType == MessageType.VIDEO && message.videoUrl.isNotBlank() && !message.deleted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (message.videoThumbUrl.isNotBlank()) {
                            AsyncImage(
                                model = message.videoThumbUrl,
                                contentDescription = "Shared video preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0x99000000),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play video",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        if (message.videoDurationMs > 0L) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xAA000000),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = formatDurationMmSs(message.videoDurationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (message.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                if (message.parsedType == MessageType.VOICE && !message.deleted) {
                    val fg = if (isMine) mineTextColor else MaterialTheme.colorScheme.onSurface
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        FilledIconButton(
                            onClick = onVoiceToggle,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isMine && bubbleStyle != ChatBubbleStyle.MINIMAL_OUTLINE) {
                                    Color.White.copy(alpha = 0.22f)
                                } else {
                                    AzurePrimary.copy(alpha = 0.14f)
                                },
                                contentColor = if (isMine && bubbleStyle != ChatBubbleStyle.MINIMAL_OUTLINE) {
                                    Color.White
                                } else {
                                    AzurePrimary
                                }
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlayingVoice) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlayingVoice) "Pause voice note" else "Play voice note"
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = fg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Voice • ${formatDurationMmSs(message.voiceDurationMs)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = fg
                                )
                            }
                            val p2pLabel = when {
                                hasLocalVoice || message.parsedDeliveryStatus == DeliveryStatus.DELIVERED ->
                                    "P2P Delivered"
                                message.parsedDeliveryStatus == DeliveryStatus.PENDING_P2P ->
                                    if (queuedVoiceCount > 0) "Queued P2P (Auto-send when online)" else "P2P Syncing..."
                                else -> "WebRTC P2P Voice"
                            }
                            Text(
                                text = p2pLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = fg.copy(alpha = 0.78f)
                            )
                        }
                    }
                }

                if (message.text.isNotBlank() && (message.parsedType != MessageType.VOICE || message.deleted)) {
                    val annotated = remember(message.text, message.deleted, linkColor) {
                        if (message.deleted) {
                            AnnotatedString(message.text)
                        } else {
                            MessageEntityParser.buildHighlightedText(message.text, linkColor)
                        }
                    }
                    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontStyle = if (message.deleted) FontStyle.Italic else FontStyle.Normal
                        ),
                        color = if (isMine) {
                            mineTextColor.copy(alpha = if (message.deleted) 0.72f else 1f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = if (message.deleted) 0.72f else 1f)
                        },
                        onTextLayout = { layoutResult = it },
                        modifier = Modifier.pointerInput(message.id, annotated) {
                            detectTapGestures(
                                onLongPress = { onLongPress() },
                                onTap = { pos ->
                                    val lr = layoutResult ?: return@detectTapGestures
                                    val charIndex = lr.getOffsetForPosition(pos)
                                    val urlAnn = annotated.getStringAnnotations(MessageEntityParser.TAG_URL, charIndex, charIndex).firstOrNull()
                                    val emailAnn = annotated.getStringAnnotations(MessageEntityParser.TAG_EMAIL, charIndex, charIndex).firstOrNull()
                                    val phoneAnn = annotated.getStringAnnotations(MessageEntityParser.TAG_PHONE, charIndex, charIndex).firstOrNull()
                                    when {
                                        urlAnn != null -> onEntityTap(DetectedMessageEntity(MessageEntityType.URL, urlAnn.item))
                                        emailAnn != null -> onEntityTap(DetectedMessageEntity(MessageEntityType.EMAIL, emailAnn.item))
                                        phoneAnn != null -> onEntityTap(DetectedMessageEntity(MessageEntityType.PHONE, phoneAnn.item))
                                    }
                                }
                            )
                        }
                    )
                }

                val timestampText = buildString {
                    if (message.id.startsWith("outbox_") || message.parsedDeliveryStatus == DeliveryStatus.PENDING_P2P) {
                        append("Queued • ")
                    }
                    append(formatTimestampShort(message.createdAt))
                }
                Text(
                    text = timestampText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = if (isMine) {
                        mineTextColor.copy(alpha = 0.74f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }

        if (message.reactions.isNotEmpty() && !message.deleted) {
            val grouped = message.reactions.values.groupingBy { it }.eachCount()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)
            ) {
                grouped.forEach { (emoji, count) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleEmoji(emoji) }
                    ) {
                        Text(
                            text = if (count > 1) "$emoji $count" else emoji,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDurationMmSs(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%02d:%02d".format(mins, secs)
}
