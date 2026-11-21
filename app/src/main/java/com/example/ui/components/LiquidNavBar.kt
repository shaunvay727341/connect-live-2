package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AzurePrimary
import com.example.ui.theme.CallCrimson
import com.example.ui.theme.CobaltDeep
import com.example.ui.theme.FrostOutline
import com.example.ui.theme.IceBlueTint
import com.example.ui.theme.MidnightOutline
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.SkyMist

@Immutable
data class LiquidNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0,
    val testTag: String
)

@Composable
fun LiquidGlassBottomBar(
    items: List<LiquidNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val isDark = isGlassDarkTheme()
    val feedback = LocalTouchFeedback.current
    val safeSelected = selectedIndex.coerceIn(0, items.lastIndex)

    val containerBg = if (isDark) {
        MidnightSurface.copy(alpha = 0.88f)
    } else {
        Color.White.copy(alpha = 0.88f)
    }
    val capsuleBorder = if (isDark) {
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.22f), MidnightOutline)
        )
    } else {
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.98f), FrostOutline)
        )
    }

    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = containerBg,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(30.dp),
                    ambientColor = CobaltDeep.copy(alpha = if (isDark) 0.35f else 0.14f),
                    spotColor = AzurePrimary.copy(alpha = if (isDark) 0.30f else 0.18f)
                )
                .clip(RoundedCornerShape(30.dp))
                .border(1.2.dp, capsuleBorder, RoundedCornerShape(30.dp))
                .pointerInput(items.size, safeSelected) {
                    detectHorizontalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDrag += dragAmount
                        },
                        onDragEnd = {
                            if (accumulatedDrag > 48f && safeSelected < items.lastIndex) {
                                feedback?.triggerSwipe()
                                onSelect(safeSelected + 1)
                            } else if (accumulatedDrag < -48f && safeSelected > 0) {
                                feedback?.triggerSwipe()
                                onSelect(safeSelected - 1)
                            }
                            accumulatedDrag = 0f
                        },
                        onDragCancel = { accumulatedDrag = 0f }
                    )
                }
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            ) {
                val tabWidth = maxWidth / items.size
                val pillOffset by animateDpAsState(
                    targetValue = tabWidth * safeSelected,
                    animationSpec = spring(
                        dampingRatio = 0.68f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "liquid_nav_pill_offset"
                )

                val dropletBrush = if (isDark) {
                    Brush.linearGradient(
                        listOf(
                            AzurePrimary.copy(alpha = 0.34f),
                            SkyMist.copy(alpha = 0.22f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            IceBlueTint,
                            AzurePrimary.copy(alpha = 0.16f)
                        )
                    )
                }

                // Sliding Liquid Glass Droplet Pill
                Box(
                    modifier = Modifier
                        .offset(x = pillOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(2.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(dropletBrush)
                        .border(
                            width = 1.dp,
                            color = if (isDark) {
                                Color.White.copy(alpha = 0.26f)
                            } else {
                                AzurePrimary.copy(alpha = 0.32f)
                            },
                            shape = RoundedCornerShape(24.dp)
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val selected = index == safeSelected
                        val itemTint by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            label = "liquid_nav_tint_$index"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(24.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (index != safeSelected) {
                                        feedback?.triggerTap()
                                        onSelect(index)
                                    } else {
                                        feedback?.triggerTap()
                                    }
                                }
                                .testTag(item.testTag),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (item.badgeCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = CallCrimson,
                                                contentColor = Color.White
                                            ) {
                                                Text(item.badgeCount.coerceAtMost(99).toString())
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label,
                                            tint = itemTint,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                        tint = itemTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = itemTint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
