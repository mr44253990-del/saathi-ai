package com.saathi.ai.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A Jimi theme: one palette, one orb character, one mood.
 *
 * Everything on screen (background wash, orb, buttons, bubble, chips) reads its
 * colours from here, so switching a theme instantly re-skins the whole app.
 */
data class JimiPalette(
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val glowA: Color,
    val glowB: Color
)

enum class OrbStyle(val label: String) {
    AURORA("Aurora"),
    NEBULA("Nebula"),
    PULSE("Pulse"),
    PRISM("Prism")
}

data class JimiThemeSpec(
    val id: String,
    val name: String,
    val bangla: String,
    val emoji: String,
    val palette: JimiPalette,
    val orbStyle: OrbStyle = OrbStyle.AURORA
)

object JimiThemes {

    val all: List<JimiThemeSpec> = listOf(
        JimiThemeSpec(
            id = "aurora", name = "Aurora", bangla = "অরোরা", emoji = "🌌",
            palette = JimiPalette(
                bgTop = Color(0xFF05070F), bgMid = Color(0xFF0B1024), bgBottom = Color(0xFF060A18),
                primary = Color(0xFF7C5CFF), secondary = Color(0xFF35D6C8), accent = Color(0xFFFF7AC6),
                textPrimary = Color(0xFFF3F1FF), textMuted = Color(0xFF9AA3C0),
                glowA = Color(0xFF6C4CFF), glowB = Color(0xFF00D9C0)
            )
        ),
        JimiThemeSpec(
            id = "midnight", name = "Midnight", bangla = "মিডনাইট", emoji = "🌙",
            palette = JimiPalette(
                bgTop = Color(0xFF01040A), bgMid = Color(0xFF071426), bgBottom = Color(0xFF02070F),
                primary = Color(0xFF3B82F6), secondary = Color(0xFF60E1FF), accent = Color(0xFFB78CFF),
                textPrimary = Color(0xFFEAF2FF), textMuted = Color(0xFF8095B8),
                glowA = Color(0xFF1E5BFF), glowB = Color(0xFF26D9FF)
            )
        ),
        JimiThemeSpec(
            id = "sunset", name = "Sunset", bangla = "সানসেট", emoji = "🌅",
            palette = JimiPalette(
                bgTop = Color(0xFF14060E), bgMid = Color(0xFF2A0E1C), bgBottom = Color(0xFF160812),
                primary = Color(0xFFFF7A45), secondary = Color(0xFFFFC46B), accent = Color(0xFFFF5C8A),
                textPrimary = Color(0xFFFFF3EC), textMuted = Color(0xFFC09A9E),
                glowA = Color(0xFFFF5E3A), glowB = Color(0xFFFFC24B)
            ), orbStyle = OrbStyle.PULSE
        ),
        JimiThemeSpec(
            id = "sakura", name = "Sakura", bangla = "সাকুরা", emoji = "🌸",
            palette = JimiPalette(
                bgTop = Color(0xFF120711), bgMid = Color(0xFF210E1C), bgBottom = Color(0xFF140811),
                primary = Color(0xFFFF8FC7), secondary = Color(0xFFFFC2DE), accent = Color(0xFFB78CFF),
                textPrimary = Color(0xFFFFF2F8), textMuted = Color(0xFFC6A2B6),
                glowA = Color(0xFFFF6FB0), glowB = Color(0xFFF7A8FF)
            ), orbStyle = OrbStyle.NEBULA
        ),
        JimiThemeSpec(
            id = "emerald", name = "Emerald", bangla = "এমারেল্ড", emoji = "🌿",
            palette = JimiPalette(
                bgTop = Color(0xFF030C09), bgMid = Color(0xFF071B14), bgBottom = Color(0xFF04110D),
                primary = Color(0xFF22D3A6), secondary = Color(0xFF7EE787), accent = Color(0xFF4FD1FF),
                textPrimary = Color(0xFFEFFFF8), textMuted = Color(0xFF87AFA2),
                glowA = Color(0xFF0FAF86), glowB = Color(0xFF6EE7A8)
            )
        ),
        JimiThemeSpec(
            id = "royal", name = "Royal", bangla = "রয়্যাল", emoji = "👑",
            palette = JimiPalette(
                bgTop = Color(0xFF080615), bgMid = Color(0xFF16103A), bgBottom = Color(0xFF0A0720),
                primary = Color(0xFF9A6BFF), secondary = Color(0xFFFFD166), accent = Color(0xFF6F8CFF),
                textPrimary = Color(0xFFF5F1FF), textMuted = Color(0xFFA79CC8),
                glowA = Color(0xFF7B4CFF), glowB = Color(0xFFFFCE4D)
            ), orbStyle = OrbStyle.PRISM
        ),
        JimiThemeSpec(
            id = "cyber", name = "Cyber", bangla = "সাইবার", emoji = "⚡",
            palette = JimiPalette(
                bgTop = Color(0xFF040A0C), bgMid = Color(0xFF06202A), bgBottom = Color(0xFF03080C),
                primary = Color(0xFF00FFD1), secondary = Color(0xFFFF2E88), accent = Color(0xFF9D4EFF),
                textPrimary = Color(0xFFE6FFFB), textMuted = Color(0xFF6F9AA3),
                glowA = Color(0xFF00E6BD), glowB = Color(0xFFFF2E88)
            ), orbStyle = OrbStyle.PRISM
        ),
        JimiThemeSpec(
            id = "ember", name = "Ember", bangla = "এম্বার", emoji = "🔥",
            palette = JimiPalette(
                bgTop = Color(0xFF0D0604), bgMid = Color(0xFF22100A), bgBottom = Color(0xFF120705),
                primary = Color(0xFFFF6B35), secondary = Color(0xFFFFB454), accent = Color(0xFFFF3D5A),
                textPrimary = Color(0xFFFFF1E8), textMuted = Color(0xFFC1998B),
                glowA = Color(0xFFFF4D1F), glowB = Color(0xFFFFA22B)
            ), orbStyle = OrbStyle.PULSE
        ),
        JimiThemeSpec(
            id = "ocean", name = "Ocean", bangla = "ওশান", emoji = "🌊",
            palette = JimiPalette(
                bgTop = Color(0xFF02090F), bgMid = Color(0xFF06202E), bgBottom = Color(0xFF031019),
                primary = Color(0xFF2FA8FF), secondary = Color(0xFF66F0D0), accent = Color(0xFF7C8CFF),
                textPrimary = Color(0xFFE8FBFF), textMuted = Color(0xFF7FA3B5),
                glowA = Color(0xFF1583FF), glowB = Color(0xFF3FE8C4)
            ), orbStyle = OrbStyle.NEBULA
        ),
        JimiThemeSpec(
            id = "mono", name = "Mono", bangla = "মনো", emoji = "🖤",
            palette = JimiPalette(
                bgTop = Color(0xFF070707), bgMid = Color(0xFF141414), bgBottom = Color(0xFF0A0A0A),
                primary = Color(0xFFE6E6E6), secondary = Color(0xFF9C9C9C), accent = Color(0xFFFFFFFF),
                textPrimary = Color(0xFFF7F7F7), textMuted = Color(0xFF8A8A8A),
                glowA = Color(0xFFBDBDBD), glowB = Color(0xFF6E6E6E)
            )
        ),
        JimiThemeSpec(
            id = "vapor", name = "Vapor", bangla = "ভেপর", emoji = "🛸",
            palette = JimiPalette(
                bgTop = Color(0xFF0B0418), bgMid = Color(0xFF1B0A33), bgBottom = Color(0xFF0A0418),
                primary = Color(0xFFB15CFF), secondary = Color(0xFF4EF0E0), accent = Color(0xFFFF5FD2),
                textPrimary = Color(0xFFF6EEFF), textMuted = Color(0xFFAA96C8),
                glowA = Color(0xFF9B3BFF), glowB = Color(0xFF25E0CE)
            ), orbStyle = OrbStyle.NEBULA
        ),
        JimiThemeSpec(
            id = "saffron", name = "Saffron", bangla = "জাফরান", emoji = "🪔",
            palette = JimiPalette(
                bgTop = Color(0xFF0C0803), bgMid = Color(0xFF231803), bgBottom = Color(0xFF110C05),
                primary = Color(0xFFFFA93B), secondary = Color(0xFF7ED957), accent = Color(0xFFFF7043),
                textPrimary = Color(0xFFFFF6E6), textMuted = Color(0xFFC4A87E),
                glowA = Color(0xFFFF9A1F), glowB = Color(0xFFB8E986)
            )
        )
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String?): JimiThemeSpec = byId[id] ?: all.first()

    val accents: List<Color> = listOf(
        Color(0xFF7C5CFF), Color(0xFF35D6C8), Color(0xFFFF7AC6), Color(0xFFFF7A45),
        Color(0xFF22D3A6), Color(0xFF3B82F6), Color(0xFFFFD166), Color(0xFFFF4D6D),
        Color(0xFFB15CFF), Color(0xFF00FFD1), Color(0xFFE6E6E6), Color(0xFF7ED957)
    )
}

/** The single source of truth for "what does Jimi look like right now". */
val LocalJimi = staticCompositionLocalOf { JimiThemes.all.first() }
