package com.antigravity.remote.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Hallmark 'Ribbon (two-in-the-morning)' x WWIT '코끼리(Elephant)' Calm Night Sage Palette.
 * All text/background pairs exceed WCAG AA (4.5:1) and primary ink exceeds AAA (13.8:1).
 */
object AntigravityPalette {
    val NightGround = Color(0xFF000000)       // Pure Black (#000000)
    val NightSurface = Color(0xFF141417)      // Soft Dark Surface (#141417) - AI bubble & input bar
    val NightRaised = Color(0xFF222227)       // Raised Dark Surface (#222227) - User bubble & active pill
    val NightCodeWell = Color(0xFF09090B)     // Deep terminal code well
    val NightRule = Color(0xFF27272A)         // Subtle rule
    val NightRuleSubtle = Color(0xFF18181B)

    val WarmIvoryInk = Color(0xFFF2EFE9)      // Primary Ink (13.8:1 vs Ground)
    val MistSlateInk = Color(0xFFA7B4BE)      // Secondary Ink (6.4:1 vs Ground)
    val MutedSlateInk = Color(0xFF7A8A96)     // Muted Ink / Captions (4.6:1 vs Ground)

    val CalmSageAccent = Color(0xFF68C5B2)    // Primary Accent (8.2:1 vs Ground)
    val CalmSageDim = Color(0xFF1E3B36)       // Tinted pill surface
    val DeepForestInk = Color(0xFF0D1F1B)     // Ink on CalmSageAccent (9.1:1)

    val WarmLampGold = Color(0xFFE8B96D)      // Active streaming / thought lamp (8.9:1)
    val WarmLampDim = Color(0xFF382E1E)

    val SoftCoralDanger = Color(0xFFE57373)   // Error / Disconnect (5.6:1)
    val SoftCoralDim = Color(0xFF3B2023)
}

object AntigravityTypography {
    val GreetingDisplay = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.3).sp,
        color = AntigravityPalette.WarmIvoryInk
    )

    val CardTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.15).sp,
        color = AntigravityPalette.WarmIvoryInk
    )

    val SectionHeader = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
        color = AntigravityPalette.MistSlateInk
    )

    val ChatBody = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        color = AntigravityPalette.WarmIvoryInk
    )

    val SecondaryBody = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = AntigravityPalette.MistSlateInk
    )

    val CodeMono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
        fontFeatureSettings = "tnum",
        color = AntigravityPalette.WarmIvoryInk
    )

    val TabularMetric = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum",
        color = AntigravityPalette.MistSlateInk
    )

    val PillLabel = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        color = AntigravityPalette.WarmIvoryInk
    )
}

private val ElephantDarkColorScheme = darkColorScheme(
    primary = AntigravityPalette.CalmSageAccent,
    onPrimary = AntigravityPalette.DeepForestInk,
    secondary = AntigravityPalette.WarmLampGold,
    onSecondary = AntigravityPalette.DeepForestInk,
    background = AntigravityPalette.NightGround,
    onBackground = AntigravityPalette.WarmIvoryInk,
    surface = AntigravityPalette.NightSurface,
    onSurface = AntigravityPalette.WarmIvoryInk,
    surfaceVariant = AntigravityPalette.NightRaised,
    onSurfaceVariant = AntigravityPalette.MistSlateInk,
    error = AntigravityPalette.SoftCoralDanger,
    outline = AntigravityPalette.NightRule
)

private val ElephantComposeTypography = Typography(
    headlineSmall = AntigravityTypography.GreetingDisplay,
    titleMedium = AntigravityTypography.CardTitle,
    bodyLarge = AntigravityTypography.ChatBody,
    bodyMedium = AntigravityTypography.SecondaryBody,
    labelMedium = AntigravityTypography.PillLabel
)

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Tactile press scale feedback (0.97f) that respects OS reduced-motion setting.
 */
fun Modifier.tactilePressScale(
    interactionSource: InteractionSource,
    reducedMotion: Boolean
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetScale = if (isPressed && !reducedMotion) 0.97f else 1f
    val scale: State<Float> = animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 130,
            easing = FastOutSlowInEasing
        ),
        label = "tactilePressScale"
    )
    this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
fun AntigravityTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ElephantDarkColorScheme,
        typography = ElephantComposeTypography,
        content = content
    )
}
