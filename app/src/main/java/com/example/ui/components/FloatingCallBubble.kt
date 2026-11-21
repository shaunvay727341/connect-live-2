package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.model.CallType
import com.example.ui.screens.RemoteVideoSurface
import com.example.ui.theme.AzurePrimary
import com.example.ui.theme.CallCrimson
import com.example.ui.theme.CobaltDeep
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.SkyMist
import com.example.ui.viewmodel.ActiveCallUiState
import com.example.ui.viewmodel.CallPhase
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import kotlin.math.roundToInt

@Composable
fun FloatingCallBubble(
    state: ActiveCallUiState,
    eglContext: EglBase.Context?,
    onAttachRemote: (SurfaceViewRenderer) -> Unit,
    onDetachRemote: (SurfaceViewRenderer) -> Unit,
    onToggleMute: () -> Unit,
    onExpand: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val feedback = LocalTouchFeedback.current

    val isVideoBubble = state.callType == CallType.VIDEO && state.isMediaReady && eglContext != null
    val cardWidth = if (isVideoBubble) 156.dp else 220.dp
    val cardHeight = if (isVideoBubble) 222.dp else 118.dp
    val bubbleShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 64.dp, end = 12.dp)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .testTag("floating_call_bubble")
    ) {
        Surface(
            shape = bubbleShape,
            color = Color(0xEC0C1A32),
            modifier = Modifier
                .size(width = cardWidth, height = cardHeight)
                .shadow(
                    elevation = 16.dp,
                    shape = bubbleShape,
                    ambientColor = CobaltDeep.copy(alpha = 0.35f),
                    spotColor = AzurePrimary.copy(alpha = 0.35f)
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.72f), SkyMist.copy(alpha = 0.85f))
                    ),
                    shape = bubbleShape
                )
                .clip(bubbleShape)
                .clickable {
                    feedback?.triggerTap()
                    onExpand()
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isVideoBubble && eglContext != null) {
                    RemoteVideoSurface(
                        eglContext = eglContext,
                        onAttach = onAttachRemote,
                        onDetach = onDetachRemote,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!state.hasRemoteVideo) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xD90A1730), Color(0xD9122A52))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            UserAvatar(
                                name = state.peerName.ifBlank { "Call" },
                                photoUrl = state.peerPhoto,
                                size = 56.dp
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0A1D3D), Color(0xFF143566))
                                )
                            )
                    )
                }

                // Top frosted strip
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Color(0x99071124))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (state.phase == CallPhase.CONNECTED) OnlineEmerald else SkyMist)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.peerName.ifBlank { "Call" },
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (state.phase == CallPhase.CONNECTED) {
                            formatCallDuration(state.durationSeconds)
                        } else {
                            "..."
                        },
                        style = MaterialTheme.typography.labelSmall.copy(color = SkyMist)
                    )
                }

                // Bottom quick controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xAA071124))
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = {
                                feedback?.triggerTap()
                                onToggleMute()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (state.isMuted) Color.White else Color.White.copy(alpha = 0.16f),
                                contentColor = if (state.isMuted) Color(0xFF091326) else Color.White
                            ),
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("bubble_mute_button")
                        ) {
                            Icon(
                                imageVector = if (state.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                contentDescription = "Toggle mic",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        FilledIconButton(
                            onClick = {
                                feedback?.triggerTap()
                                onExpand()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = AzurePrimary.copy(alpha = 0.34f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("bubble_expand_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInFull,
                                contentDescription = "Expand call",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        FilledIconButton(
                            onClick = {
                                feedback?.triggerTap()
                                onEndCall()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = CallCrimson,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("bubble_end_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CallEnd,
                                contentDescription = "End call",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
