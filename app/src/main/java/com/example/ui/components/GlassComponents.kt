package com.example.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AzurePrimary
import com.example.ui.theme.CobaltDeep
import com.example.ui.theme.FrostCanvas
import com.example.ui.theme.FrostOutline
import com.example.ui.theme.IceBlueTint
import com.example.ui.theme.MidnightCanvas
import com.example.ui.theme.MidnightOutline
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.SkyMist

@Immutable
class TouchFeedbackController(
    private val view: View,
    private val soundEnabled: Boolean,
    private val hapticsEnabled: Boolean
) {
    fun triggerTap() {
        if (soundEnabled) {
            runCatching { view.playSoundEffect(SoundEffectConstants.CLICK) }
        }
        if (hapticsEnabled) {
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
        }
    }

    fun triggerSwipe() {
        if (soundEnabled) {
            runCatching { view.playSoundEffect(SoundEffectConstants.CLICK) }
        }
        if (hapticsEnabled) {
            val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.GESTURE_END
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            runCatching { view.performHapticFeedback(constant) }
        }
    }
}

val LocalTouchFeedback = staticCompositionLocalOf<TouchFeedbackController?> { null }

@Composable
fun ProvideTouchFeedback(
    soundEnabled: Boolean,
    hapticsEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val controller = remember(view, soundEnabled, hapticsEnabled) {
        TouchFeedbackController(
            view = view,
            soundEnabled = soundEnabled,
            hapticsEnabled = hapticsEnabled
        )
    }
    CompositionLocalProvider(LocalTouchFeedback provides controller) {
        content()
    }
}

@Composable
fun isGlassDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.4f

@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isGlassDarkTheme()
    val bgBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(MidnightCanvas, MidnightSurface, Color(0xFF0A162B))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFF9FCFF), FrostCanvas, IceBlueTint)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AzurePrimary.copy(alpha = if (isDark) 0.22f else 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.10f),
                    radius = size.minDimension * 0.65f
                ),
                center = Offset(size.width * 0.88f, size.height * 0.10f),
                radius = size.minDimension * 0.65f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SkyMist.copy(alpha = if (isDark) 0.14f else 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.12f, size.height * 0.82f),
                    radius = size.minDimension * 0.58f
                ),
                center = Offset(size.width * 0.12f, size.height * 0.82f),
                radius = size.minDimension * 0.58f
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    tintAlpha: Float? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isDark = isGlassDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)
    val surfaceColor = if (isDark) {
        MidnightSurface.copy(alpha = tintAlpha ?: 0.80f)
    } else {
        Color.White.copy(alpha = tintAlpha ?: 0.84f)
    }
    val borderBrush = if (borderColor != null) {
        Brush.linearGradient(listOf(borderColor, borderColor.copy(alpha = 0.6f)))
    } else if (isDark) {
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.18f), MidnightOutline.copy(alpha = 0.85f))
        )
    } else {
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.96f), FrostOutline.copy(alpha = 0.92f))
        )
    }
    val feedback = LocalTouchFeedback.current

    val cardModifier = modifier
        .shadow(
            elevation = if (isDark) 6.dp else 10.dp,
            shape = shape,
            ambientColor = CobaltDeep.copy(alpha = if (isDark) 0.28f else 0.08f),
            spotColor = AzurePrimary.copy(alpha = if (isDark) 0.25f else 0.10f)
        )
        .clip(shape)
        .border(width = 1.dp, brush = borderBrush, shape = shape)
        .let { base ->
            if (onClick != null) {
                base.clickable {
                    feedback?.triggerTap()
                    onClick()
                }
            } else {
                base
            }
        }

    Surface(
        shape = shape,
        color = surfaceColor,
        modifier = cardModifier
    ) {
        content()
    }
}

@Composable
fun GlassPillChip(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val isDark = isGlassDarkTheme()
    val bg = containerColor ?: if (isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.86f)
    }
    val stroke = borderColor ?: if (isDark) {
        Color.White.copy(alpha = 0.18f)
    } else {
        FrostOutline
    }
    val feedback = LocalTouchFeedback.current

    Surface(
        shape = CircleShape,
        color = bg,
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, stroke, CircleShape)
            .let { base ->
                if (onClick != null) {
                    base.clickable {
                        feedback?.triggerTap()
                        onClick()
                    }
                } else {
                    base
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
