package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CallSession
import com.example.data.model.CallType
import com.example.service.CallListenerService
import com.example.ui.components.GlassBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPillChip
import com.example.ui.components.LocalTouchFeedback
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatCallDuration
import com.example.ui.components.isGlassDarkTheme
import com.example.ui.theme.AzurePrimary
import com.example.ui.theme.CallCrimson
import com.example.ui.theme.CobaltDeep
import com.example.ui.theme.FrostOutline
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.RingingAmber
import com.example.ui.theme.SkyMist
import com.example.ui.viewmodel.ActiveCallUiState
import com.example.ui.viewmodel.CallPhase
import com.example.ui.viewmodel.CallViewModel
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import kotlin.math.roundToInt

@Composable
fun IncomingCallOverlay(
    callSession: CallSession,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    customBackgroundUri: String = ""
) {
    val isDark = isGlassDarkTheme()
    val feedback = LocalTouchFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "incoming_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    GlassBackdrop(modifier = modifier) {
        if (customBackgroundUri.isNotBlank()) {
            AsyncImage(
                model = customBackgroundUri,
                contentDescription = "Upcoming call screen background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(Color(0xB8081226), Color(0xCC060C1A))
                            } else {
                                listOf(Color(0x9EF5FAFF), Color(0xCCE6F1FF))
                            }
                        )
                    )
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxSize()
                .align(Alignment.Center)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Frosted Call Type Pill
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(14.dp))
                GlassPillChip(
                    containerColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.90f),
                    borderColor = AzurePrimary.copy(alpha = 0.35f)
                ) {
                    Icon(
                        imageVector = if (callSession.parsedType == CallType.VIDEO) {
                            Icons.Rounded.Videocam
                        } else {
                            Icons.Rounded.Call
                        },
                        contentDescription = null,
                        tint = AzurePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (callSession.parsedType == CallType.VIDEO) {
                            stringResource(R.string.incoming_video_call)
                        } else {
                            stringResource(R.string.incoming_audio_call)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = AzurePrimary
                    )
                }
            }

            // Center Caller Frosted Halo & Glass Card
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(164.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        AzurePrimary.copy(alpha = if (isDark) 0.34f else 0.22f),
                                        SkyMist.copy(alpha = 0.04f)
                                    )
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(128.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.92f)
                            )
                            .border(2.dp, AzurePrimary.copy(alpha = 0.40f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        UserAvatar(
                            name = callSession.callerName.ifBlank { "Connect User" },
                            photoUrl = callSession.callerPhoto,
                            size = 114.dp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(26.dp))
                Text(
                    text = callSession.callerName.ifBlank { "Connect User" },
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect Live Encrypted Call",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bottom iPhone-Style Slide-to-Answer + Decline
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SlideToAnswerBar(
                    isVideo = callSession.parsedType == CallType.VIDEO,
                    onAnswered = {
                        feedback?.triggerSwipe()
                        onAccept()
                    }
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = {
                            feedback?.triggerTap()
                            onReject()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = CallCrimson,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(10.dp, CircleShape, ambientColor = CallCrimson.copy(alpha = 0.35f))
                            .testTag("incoming_reject_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = stringResource(R.string.reject_call),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.reject_call),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = CallCrimson
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideToAnswerBar(
    isVideo: Boolean,
    onAnswered: () -> Unit
) {
    val isDark = isGlassDarkTheme()
    val density = LocalDensity.current
    val thumbSize = 60.dp
    val trackHeight = 72.dp
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var answeredTriggered by remember { mutableStateOf(false) }

    val animatedOffsetPx by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "slide_answer_offset"
    )

    GlassCard(
        cornerRadius = 36.dp,
        tintAlpha = if (isDark) 0.85f else 0.90f,
        borderColor = AzurePrimary.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val maxDragPx = with(density) { (maxWidth - thumbSize).toPx().coerceAtLeast(1f) }
            val progress = (animatedOffsetPx / maxDragPx).coerceIn(0f, 1f)

            // Subtle progress wash
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (0.18f + progress * 0.82f).coerceIn(0.18f, 1f))
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                OnlineEmerald.copy(alpha = 0.18f + 0.35f * progress),
                                AzurePrimary.copy(alpha = 0.10f + 0.25f * progress)
                            )
                        )
                    )
            )

            // Prompt label
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = 34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isVideo) "Slide to answer video" else "Slide to answer",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = (1f - progress * 1.1f).coerceIn(0.15f, 0.88f))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = AzurePrimary.copy(alpha = (1f - progress).coerceIn(0.2f, 0.9f)),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Draggable & Tappable Thumb
            Surface(
                shape = CircleShape,
                color = OnlineEmerald,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .clickable(enabled = !answeredTriggered) {
                        answeredTriggered = true
                        onAnswered()
                    }
                    .pointerInput(maxDragPx) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                if (answeredTriggered) return@detectHorizontalDragGestures
                                change.consume()
                                dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, maxDragPx)
                                if (dragOffsetPx >= maxDragPx * 0.72f) {
                                    answeredTriggered = true
                                    dragOffsetPx = maxDragPx
                                    onAnswered()
                                }
                            },
                            onDragEnd = {
                                if (!answeredTriggered) {
                                    dragOffsetPx = 0f
                                }
                            },
                            onDragCancel = {
                                if (!answeredTriggered) {
                                    dragOffsetPx = 0f
                                }
                            }
                        )
                    }
                    .testTag("incoming_accept_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                        contentDescription = stringResource(R.string.accept_call),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveCallScreen(
    viewModel: CallViewModel,
    onCallFinished: () -> Unit,
    isInPipMode: Boolean = false,
    onMinimizeToPip: () -> Unit = {},
    onMinimizeInApp: () -> Unit = {},
    onCallbackAudio: () -> Unit = {},
    onCallbackVideo: () -> Unit = {},
    onMessagePeer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val feedback = LocalTouchFeedback.current
    val isDark = isGlassDarkTheme()
    val eglContext = remember(state.isMediaReady, state.callId, state.phase) {
        viewModel.eglContext()
    }

    BackHandler {
        when {
            state.phase == CallPhase.ENDED || state.phase == CallPhase.IDLE -> {
                viewModel.dismissEndedState()
                onCallFinished()
            }
            state.phase == CallPhase.REQUESTING_PERMISSIONS -> {
                viewModel.dismissEndedState()
                onCallFinished()
            }
            else -> {
                onMinimizeInApp()
            }
        }
    }

    val coreCallPermissions = remember(state.callType) {
        if (state.callType == CallType.VIDEO) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }

    val promptPermissions = remember(state.callType) {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (state.callType == CallType.VIDEO) {
                add(Manifest.permission.CAMERA)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val coreGranted = coreCallPermissions.all { perm ->
            results[perm] == true ||
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        CallListenerService.startIfSignedIn(context)
        viewModel.onPermissionsResult(coreGranted)
    }

    LaunchedEffect(state.phase) {
        if (state.phase == CallPhase.REQUESTING_PERMISSIONS && state.errorMessage == null) {
            permissionLauncher.launch(promptPermissions)
        }
    }

    if (isInPipMode) {
        PipCallContent(
            state = state,
            eglContext = eglContext,
            onAttachRemote = viewModel::attachRemoteRenderer,
            onDetachRemote = viewModel::detachRemoteRenderer,
            modifier = modifier
        )
        return
    }

    val isVideoStage = state.callType == CallType.VIDEO &&
        state.phase != CallPhase.REQUESTING_PERMISSIONS &&
        state.phase != CallPhase.ENDED

    Box(modifier = modifier.fillMaxSize()) {
        if (isVideoStage) {
            VideoCallStage(
                state = state,
                eglContext = eglContext,
                onAttachRemote = viewModel::attachRemoteRenderer,
                onDetachRemote = viewModel::detachRemoteRenderer,
                onAttachLocal = viewModel::attachLocalRenderer,
                onDetachLocal = viewModel::detachLocalRenderer
            )
        } else {
            AudioCallStage(state = state)
        }

        // Top Frosted Call Status Bar + In-App Minimize + System PiP
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val chipContainer = if (isVideoStage || isDark) {
                    Color(0x990A1428)
                } else {
                    Color.White.copy(alpha = 0.88f)
                }
                val chipContent = if (isVideoStage || isDark) Color.White else MaterialTheme.colorScheme.onSurface

                if (state.phase != CallPhase.ENDED &&
                    state.phase != CallPhase.IDLE &&
                    state.phase != CallPhase.REQUESTING_PERMISSIONS
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledIconButton(
                            onClick = {
                                feedback?.triggerTap()
                                onMinimizeInApp()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = chipContainer,
                                contentColor = chipContent
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                .testTag("call_minimize_inapp_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.minimize_in_app),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            FilledIconButton(
                                onClick = {
                                    feedback?.triggerTap()
                                    onMinimizeToPip()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = chipContainer,
                                    contentColor = chipContent
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                    .testTag("call_pip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureInPictureAlt,
                                    contentDescription = stringResource(R.string.minimize_pip),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }

                GlassPillChip(
                    containerColor = chipContainer,
                    borderColor = if (isVideoStage || isDark) Color.White.copy(alpha = 0.24f) else FrostOutline
                ) {
                    val dotColor = when (state.phase) {
                        CallPhase.CONNECTED -> OnlineEmerald
                        CallPhase.RECONNECTING -> RingingAmber
                        CallPhase.ENDED -> CallCrimson
                        else -> AzurePrimary
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buildStatusLine(state),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = chipContent,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Spacer(modifier = Modifier.width(48.dp))
            }

            if (state.errorMessage != null && state.phase != CallPhase.ENDED) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CallCrimson.copy(alpha = 0.90f),
                    modifier = Modifier.widthIn(max = 460.dp)
                ) {
                    Text(
                        text = state.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Bottom Controls Dock / Post-Call Callback Sheet
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            when (state.phase) {
                CallPhase.REQUESTING_PERMISSIONS -> {
                    GlassCard(
                        cornerRadius = 26.dp,
                        modifier = Modifier.widthIn(max = 420.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.permission_required_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.permission_required_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        feedback?.triggerTap()
                                        viewModel.dismissEndedState()
                                        onCallFinished()
                                    }
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        feedback?.triggerTap()
                                        permissionLauncher.launch(promptPermissions)
                                    },
                                    modifier = Modifier.testTag("grant_call_permissions_button")
                                ) {
                                    Text(stringResource(R.string.grant_permissions))
                                }
                            }
                        }
                    }
                }
                CallPhase.ENDED -> {
                    GlassCard(
                        cornerRadius = 28.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = state.errorMessage ?: "Call Ended • ${formatCallDuration(state.durationSeconds)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            if (state.peerId.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            feedback?.triggerTap()
                                            onCallbackAudio()
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("call_again_audio_button")
                                    ) {
                                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Audio", maxLines = 1)
                                    }

                                    Button(
                                        onClick = {
                                            feedback?.triggerTap()
                                            onCallbackVideo()
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("call_again_video_button")
                                    ) {
                                        Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Video", maxLines = 1)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            feedback?.triggerTap()
                                            onMessagePeer()
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("call_ended_message_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Chat", maxLines = 1)
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    feedback?.triggerTap()
                                    viewModel.dismissEndedState()
                                    onCallFinished()
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("call_done_button")
                            ) {
                                Text("Done", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                else -> {
                    CallControlDock(
                        state = state,
                        isVideoStage = isVideoStage,
                        onToggleMute = {
                            feedback?.triggerTap()
                            viewModel.toggleMute()
                        },
                        onToggleSpeaker = {
                            feedback?.triggerTap()
                            viewModel.toggleSpeaker()
                        },
                        onToggleCamera = {
                            feedback?.triggerTap()
                            viewModel.toggleCamera()
                        },
                        onSwitchCamera = {
                            feedback?.triggerTap()
                            viewModel.switchCamera()
                        },
                        onEndCall = {
                            feedback?.triggerTap()
                            viewModel.endActiveCall()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCallStage(
    state: ActiveCallUiState,
    eglContext: EglBase.Context?,
    onAttachRemote: (SurfaceViewRenderer) -> Unit,
    onDetachRemote: (SurfaceViewRenderer) -> Unit,
    onAttachLocal: (SurfaceViewRenderer) -> Unit,
    onDetachLocal: (SurfaceViewRenderer) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF071122))
    ) {
        if (eglContext != null) {
            RemoteVideoSurface(
                eglContext = eglContext,
                onAttach = onAttachRemote,
                onDetach = onDetachRemote,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!state.hasRemoteVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xCC091326), Color(0xCC132645))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UserAvatar(
                        name = state.peerName.ifBlank { "Connect User" },
                        photoUrl = state.peerPhoto,
                        size = 108.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.peerName,
                        style = MaterialTheme.typography.headlineSmall.copy(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.phase == CallPhase.RINGING_OUTGOING) "Ringing..." else "Connecting video...",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFC4D6F2))
                    )
                }
            }
        }

        // Local Camera Picture-in-Picture Preview (translucent frame, never opaque mask over SurfaceView)
        if (eglContext != null && state.isMediaReady) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 68.dp, end = 16.dp)
                    .size(width = 114.dp, height = 162.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.8.dp, Color.White.copy(alpha = 0.52f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (state.isCameraEnabled) {
                    LocalVideoSurface(
                        eglContext = eglContext,
                        isFrontCamera = state.isFrontCamera,
                        onAttach = onAttachLocal,
                        onDetach = onDetachLocal,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xE60D192E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VideocamOff,
                            contentDescription = "Camera off",
                            tint = Color(0xFFC4D6F2),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteVideoSurface(
    eglContext: EglBase.Context,
    onAttach: (SurfaceViewRenderer) -> Unit,
    onDetach: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(eglContext) {
        SurfaceViewRenderer(context).apply {
            init(eglContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
            setMirror(false)
        }
    }

    DisposableEffect(renderer) {
        onAttach(renderer)
        onDispose {
            onDetach(renderer)
            renderer.release()
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}

@Composable
fun LocalVideoSurface(
    eglContext: EglBase.Context,
    isFrontCamera: Boolean,
    onAttach: (SurfaceViewRenderer) -> Unit,
    onDetach: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(eglContext) {
        SurfaceViewRenderer(context).apply {
            setZOrderMediaOverlay(true)
            init(eglContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
            setMirror(isFrontCamera)
        }
    }

    LaunchedEffect(renderer, isFrontCamera) {
        renderer.setMirror(isFrontCamera)
    }

    DisposableEffect(renderer) {
        onAttach(renderer)
        onDispose {
            onDetach(renderer)
            renderer.release()
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}

@Composable
private fun AudioCallStage(state: ActiveCallUiState) {
    val isDark = isGlassDarkTheme()
    val infiniteTransition = rememberInfiniteTransition(label = "audio_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "audio_ring"
    )

    GlassBackdrop(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .padding(bottom = 80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state.phase == CallPhase.CONNECTED || state.phase == CallPhase.RINGING_OUTGOING) {
                    Box(
                        modifier = Modifier
                            .size(168.dp)
                            .scale(pulse)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        AzurePrimary.copy(alpha = if (isDark) 0.34f else 0.24f),
                                        SkyMist.copy(alpha = 0.05f)
                                    )
                                )
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.92f))
                        .border(2.dp, AzurePrimary.copy(alpha = 0.36f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatar(
                        name = state.peerName.ifBlank { "Connect User" },
                        photoUrl = state.peerPhoto,
                        size = 116.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = state.peerName.ifBlank { "Connect User" },
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = when (state.phase) {
                    CallPhase.CONNECTED -> formatCallDuration(state.durationSeconds)
                    CallPhase.RINGING_OUTGOING -> "Ringing..."
                    CallPhase.CONNECTING -> "Connecting audio..."
                    CallPhase.RECONNECTING -> "Reconnecting..."
                    CallPhase.ENDED -> "Call Ended (${formatCallDuration(state.durationSeconds)})"
                    else -> "Preparing call..."
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = if (state.phase == CallPhase.CONNECTED) AzurePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
private fun CallControlDock(
    state: ActiveCallUiState,
    isVideoStage: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    val isDark = isGlassDarkTheme()
    val useDarkGlass = isVideoStage || isDark
    val container = if (useDarkGlass) Color(0xCC0C1A32) else Color.White.copy(alpha = 0.88f)
    val border = if (useDarkGlass) Color.White.copy(alpha = 0.24f) else FrostOutline

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = container,
        modifier = Modifier
            .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = CobaltDeep.copy(alpha = 0.2f))
            .border(1.dp, border, RoundedCornerShape(32.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallToggleButton(
                icon = if (state.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                label = if (state.isMuted) stringResource(R.string.unmute_mic) else stringResource(R.string.mute_mic),
                active = state.isMuted,
                useDarkGlass = useDarkGlass,
                onClick = onToggleMute,
                tag = "call_mute_button"
            )

            CallToggleButton(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                label = stringResource(R.string.speaker_on),
                active = state.isSpeakerOn,
                useDarkGlass = useDarkGlass,
                onClick = onToggleSpeaker,
                tag = "call_speaker_button"
            )

            if (state.callType == CallType.VIDEO) {
                CallToggleButton(
                    icon = if (state.isCameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                    label = stringResource(R.string.camera_toggle),
                    active = !state.isCameraEnabled,
                    useDarkGlass = useDarkGlass,
                    onClick = onToggleCamera,
                    tag = "call_camera_toggle_button"
                )
                CallToggleButton(
                    icon = Icons.Rounded.Cameraswitch,
                    label = stringResource(R.string.switch_camera),
                    active = false,
                    useDarkGlass = useDarkGlass,
                    onClick = onSwitchCamera,
                    tag = "call_switch_camera_button"
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = onEndCall,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CallCrimson,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("call_end_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = stringResource(R.string.end_call),
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.end_call),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (useDarkGlass) Color.White else CallCrimson,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

@Composable
private fun CallToggleButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    useDarkGlass: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val bg = when {
        active && useDarkGlass -> Color.White
        active && !useDarkGlass -> AzurePrimary
        useDarkGlass -> Color.White.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        active && useDarkGlass -> Color(0xFF0A1428)
        active && !useDarkGlass -> Color.White
        useDarkGlass -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val labelColor = if (useDarkGlass) Color(0xFFDCE7FA) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = bg,
                contentColor = fg
            ),
            modifier = Modifier
                .size(52.dp)
                .testTag(tag)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(color = labelColor)
        )
    }
}

private fun buildStatusLine(state: ActiveCallUiState): String {
    val phaseLabel = when (state.phase) {
        CallPhase.CONNECTED -> formatCallDuration(state.durationSeconds)
        CallPhase.RINGING_OUTGOING -> "Ringing"
        CallPhase.CONNECTING -> "Connecting"
        CallPhase.RECONNECTING -> "Reconnecting"
        CallPhase.ENDED -> "Ended"
        CallPhase.REQUESTING_PERMISSIONS -> "Permissions"
        else -> "Initializing"
    }
    return "$phaseLabel • ${state.turnStatusLabel}"
}

@Composable
private fun PipCallContent(
    state: ActiveCallUiState,
    eglContext: EglBase.Context?,
    onAttachRemote: (SurfaceViewRenderer) -> Unit,
    onDetachRemote: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF081428), Color(0xFF132C54))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (state.callType == CallType.VIDEO &&
            eglContext != null &&
            state.phase != CallPhase.ENDED &&
            state.phase != CallPhase.REQUESTING_PERMISSIONS
        ) {
            RemoteVideoSurface(
                eglContext = eglContext,
                onAttach = onAttachRemote,
                onDetach = onDetachRemote,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (state.callType != CallType.VIDEO || !state.hasRemoteVideo) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(10.dp)
            ) {
                UserAvatar(
                    name = state.peerName.ifBlank { "Connect User" },
                    photoUrl = state.peerPhoto,
                    size = 46.dp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.peerName.ifBlank { "Connect User" },
                    style = MaterialTheme.typography.labelLarge.copy(color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (state.phase) {
                        CallPhase.CONNECTED -> formatCallDuration(state.durationSeconds)
                        CallPhase.RINGING_OUTGOING -> "Ringing..."
                        CallPhase.RECONNECTING -> "Reconnecting..."
                        CallPhase.ENDED -> "Call Ended"
                        else -> "Connecting..."
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (state.phase == CallPhase.CONNECTED) SkyMist else Color(0xFFC4D6F2)
                    ),
                    maxLines = 1
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0x99071124),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = formatCallDuration(state.durationSeconds),
                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (state.isMuted) {
            Surface(
                shape = CircleShape,
                color = CallCrimson.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MicOff,
                        contentDescription = "Muted",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
