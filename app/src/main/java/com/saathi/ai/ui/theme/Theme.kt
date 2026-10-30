package com.saathi.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- legacy aliases
// Kept so existing screens keep compiling; they now delegate to the live palette.
val DeepNight = Color(0xFF070B14)
val NightSurface = Color(0xFF101528)
val Violet = Color(0xFF7C5CFF)
val VioletSoft = Color(0xFFB4A0FF)
val Aqua = Color(0xFF35D6C8)
val Pink = Color(0xFFFF7AC6)
val TextPrimary = Color(0xFFF3F1FF)
val TextMuted = Color(0xFF9AA3C0)

private fun schemeFor(p: JimiPalette) = darkColorScheme(
    primary = p.primary,
    onPrimary = Color.White,
    secondary = p.secondary,
    onSecondary = Color(0xFF04211F),
    tertiary = p.accent,
    background = p.bgTop,
    onBackground = p.textPrimary,
    surface = p.bgMid,
    onSurface = p.textPrimary,
    surfaceVariant = p.bgMid,
    onSurfaceVariant = p.textMuted,
    error = Color(0xFFFF6B81),
    outline = Color.White.copy(alpha = 0.18f)
)

private val JimiTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)

@Composable
fun SaathiTheme(spec: JimiThemeSpec = JimiThemes.all.first(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalJimi provides spec) {
        MaterialTheme(
            colorScheme = schemeFor(spec.palette),
            typography = JimiTypography,
            content = content
        )
    }
}

/** Shortcut for the current palette anywhere in the tree. */
val jimi: JimiPalette
    @Composable get() = LocalJimi.current.palette

/** Kept for callers that still ask "is the system dark" — Jimi is always dark-first. */
@Composable
fun isDark(): Boolean = isSystemInDarkTheme() || true
