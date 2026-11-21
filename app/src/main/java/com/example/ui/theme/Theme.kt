package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val ConnectLightColorScheme = lightColorScheme(
    primary = AzurePrimary,
    onPrimary = Color.White,
    primaryContainer = IceBlueTint,
    onPrimaryContainer = Color(0xFF082D66),
    secondary = CobaltDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EBFF),
    onSecondaryContainer = Color(0xFF102A6B),
    tertiary = SkyMist,
    onTertiary = Color(0xFF052438),
    background = FrostCanvas,
    onBackground = TextPrimaryLight,
    surface = FrostSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = FrostSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = FrostOutline,
    error = CallCrimson,
    onError = Color.White
)

private val ConnectDarkColorScheme = darkColorScheme(
    primary = Color(0xFF389BFF),
    onPrimary = Color(0xFF041C38),
    primaryContainer = IceBlueContainerDark,
    onPrimaryContainer = Color(0xFFCFE4FF),
    secondary = SkyMist,
    onSecondary = Color(0xFF052136),
    secondaryContainer = Color(0xFF153257),
    onSecondaryContainer = Color(0xFFD6EAFF),
    tertiary = CobaltDeep,
    onTertiary = Color.White,
    background = MidnightCanvas,
    onBackground = TextPrimaryDark,
    surface = MidnightSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = MidnightOutline,
    error = CallCrimson,
    onError = Color.White
)

val ConnectLiveShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun ConnectLiveTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ConnectDarkColorScheme else ConnectLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ConnectLiveTypography,
        shapes = ConnectLiveShapes,
        content = content
    )
}
