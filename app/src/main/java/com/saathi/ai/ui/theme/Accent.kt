package com.saathi.ai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Accent override: any theme can be re-tinted with one of twelve accents.
 *
 * `accentOverride` in [com.saathi.ai.data.SecurePrefs] is an index into
 * [JimiThemes.accents] (0..11) or -1 for "follow the theme". The whole UI picks
 * this up through [withAccent], so the orb, buttons, glow and chips all shift
 * together — that is what makes the colour swatches in Settings feel live.
 */
fun JimiThemeSpec.withAccent(index: Int): JimiThemeSpec {
    val accents = JimiThemes.accents
    if (index < 0 || index >= accents.size) return this
    val a = accents[index]
    return copy(
        palette = palette.copy(
            primary = a,
            secondary = blend(a, palette.secondary, 0.45f),
            glowA = a,
            glowB = blend(a, palette.glowB, 0.55f)
        )
    )
}

fun blend(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = a.red * (1 - f) + b.red * f,
        green = a.green * (1 - f) + b.green * f,
        blue = a.blue * (1 - f) + b.blue * f,
        alpha = 1f
    )
}

/** Resolves the user's stored theme id + accent index into a renderable spec. */
fun resolveTheme(themeId: String?, accentIndex: Int): JimiThemeSpec =
    JimiThemes.get(themeId).withAccent(accentIndex)
