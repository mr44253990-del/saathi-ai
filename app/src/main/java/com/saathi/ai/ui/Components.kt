package com.saathi.ai.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.ui.theme.Aqua
import com.saathi.ai.ui.theme.NightSurface
import com.saathi.ai.ui.theme.Pink
import com.saathi.ai.ui.theme.TextMuted
import com.saathi.ai.ui.theme.TextPrimary
import com.saathi.ai.ui.theme.Violet
import com.saathi.ai.ui.theme.jimi

/** Full-screen animated background used by every screen. */
@Composable
fun SaathiBackground(content: @Composable BoxScope.() -> Unit) {
    val p = jimi
    Box(
        modifier = Modifier
            .background(Brush.verticalGradient(listOf(p.bgTop, p.bgMid, p.bgBottom)))
            .background(
                Brush.radialGradient(
                    colors = listOf(p.glowA.copy(alpha = 0.22f), Color.Transparent),
                    radius = 1100f
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(p.glowB.copy(alpha = 0.12f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(1600f, 2200f),
                    radius = 1200f
                )
            )
    ) {
        content()
    }
}

/** Frosted glass card. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = jimi.bgMid.copy(alpha = 0.72f),
        shape = RoundedCornerShape(corner),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.02f))
                    ),
                    shape = RoundedCornerShape(corner)
                )
                .padding(contentPadding),
            content = content
        )
    }
}

/** Big pill button with the assistant gradient. */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.45f, tween(200), label = "btnAlpha")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(jimi.primary.copy(alpha = alpha), jimi.secondary.copy(alpha = alpha * 0.92f))
                )
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Circular icon button used in the bottom control dock. */
@Composable
fun RoundAction(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    tint: Color = TextPrimary,
    size: Dp = 54.dp,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (active) {
                        Brush.linearGradient(listOf(jimi.secondary.copy(alpha = 0.9f), jimi.primary.copy(alpha = 0.9f)))
                    } else {
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
                        )
                    }
                )
                .border(1.dp, Color.White.copy(alpha = if (active) 0.35f else 0.10f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFF06121A) else tint,
                modifier = Modifier.size(size * 0.42f)
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(label, color = if (active) jimi.secondary else jimi.textMuted, fontSize = 10.sp)
    }
}

/** Small rounded status chip with a colour dot. */
@Composable
fun StatusChip(text: String, dotColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = jimi.textPrimary.copy(alpha = 0.92f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Pulsing listening indicator. */
@Composable
fun PulseDot(color: Color, scale: Float) {
    Box(
        Modifier
            .size(10.dp)
            .scale(1f + scale * 0.35f)
            .clip(CircleShape)
            .background(color)
    )
}

val AccentGradient = Brush.linearGradient(listOf(Violet, Pink, Aqua))

/**
 * A theme's own mark, drawn in code from its palette — the emoji it used to carry is
 * gone. Two arcs plus a filled core read as "this colour story" at a glance.
 */
@Composable
fun ThemeMark(spec: com.saathi.ai.ui.theme.JimiThemeSpec, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val c = androidx.compose.ui.geometry.Offset(cx, cy)
        val box = androidx.compose.ui.geometry.Offset(cx - r * 0.95f, cy - r * 0.95f)
        val dim = androidx.compose.ui.geometry.Size(r * 1.9f, r * 1.9f)
        drawCircle(
            brush = Brush.linearGradient(listOf(spec.palette.primary, spec.palette.secondary)),
            radius = r * 0.62f,
            center = c
        )
        drawArc(
            color = spec.palette.accent,
            startAngle = -40f, sweepAngle = 200f, useCenter = false,
            topLeft = box, size = dim,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.16f)
        )
        drawArc(
            color = spec.palette.glowB.copy(alpha = 0.85f),
            startAngle = 150f, sweepAngle = 160f, useCenter = false,
            topLeft = box, size = dim,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.16f)
        )
    }
}

/**
 * Side indicator rail: the vertical marker strip requested for the redesign. One
 * segment per section, the active one lit with the accent gradient and longer, so the
 * current place in a long screen is visible without reading a word.
 */
@Composable
fun SideRail(
    sections: List<String>,
    active: Int,
    modifier: Modifier = Modifier,
    onSelect: ((Int) -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        sections.forEachIndexed { i, _ ->
            val on = i == active
            Box(
                modifier = Modifier
                    .padding(vertical = 3.dp)
                    .width(if (on) 4.dp else 3.dp)
                    .height(if (on) 22.dp else 11.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (on) {
                            Brush.verticalGradient(listOf(jimi.accent, jimi.secondary))
                        } else {
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.08f))
                            )
                        }
                    )
                    .then(if (onSelect != null) Modifier.clickable { onSelect(i) } else Modifier)
            )
        }
    }
}

/** Section heading with a code-drawn icon instead of a glyph. */
@Composable
fun SectionTitle(
    title: String,
    icon: ImageVector,
    tint: Color = jimi.secondary,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(title, color = jimi.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * "উপরের বারে বর্তমান কাজ" — one strip, one source of truth ([SessionHub.state]),
 * shown inside the app exactly like the bubble shows it outside. Task first, then
 * tool, then whatever Jimi is saying or hearing.
 */
@Composable
fun NowBar(state: com.saathi.ai.live.SessionState, modifier: Modifier = Modifier) {
    val phase = state.phase
    val (label, detail, tone) = when {
        state.taskText.isNotBlank() -> Triple("চলমান কাজ", state.taskText, jimi.accent)
        phase == com.saathi.ai.live.Phase.THINKING ->
            Triple("ভাবছে", state.lastTool.ifBlank { state.modelText.take(60) }, jimi.secondary)
        phase == com.saathi.ai.live.Phase.SPEAKING ->
            Triple("বলছে", state.modelText.take(70), jimi.secondary)
        phase == com.saathi.ai.live.Phase.LISTENING ->
            Triple("শুনছে", state.userText.ifBlank { "বলো — শুনছি" }.take(70), Color(0xFF34D399))
        phase == com.saathi.ai.live.Phase.CONNECTING || phase == com.saathi.ai.live.Phase.RECONNECTING ->
            Triple("যুক্ত হচ্ছে", "সেশন খোলা হচ্ছে…", Color(0xFFFBBF24))
        phase == com.saathi.ai.live.Phase.ERROR ->
            Triple("সমস্যা", state.error?.take(70) ?: "আবার চেষ্টা করছি", Color(0xFFFF8A9C))
        phase == com.saathi.ai.live.Phase.ENDED ->
            Triple("বন্ধ", "সেশন শেষ — বললে আবার চালু হবে", jimi.textMuted)
        else -> Triple("তৈরি", state.lastTool.ifBlank { "অপেক্ষমাণ" }.take(70), jimi.textMuted)
    }
    val progress = state.taskProgress.coerceIn(0, 100)

    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, tone.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(tone)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = tone, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                if (progress in 1..100) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.10f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress / 100f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(tone)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("$progress%", color = tone, fontSize = 9.5.sp)
                }
            }
            Text(
                detail.ifBlank { "—" },
                color = jimi.textPrimary.copy(alpha = 0.9f),
                fontSize = 12.sp,
                maxLines = 2,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (phase == com.saathi.ai.live.Phase.SPEAKING || phase == com.saathi.ai.live.Phase.LISTENING) {
                SaathiIcons.Mic
            } else {
                SaathiIcons.Robot
            },
            null, tint = tone, modifier = Modifier.size(16.dp)
        )
    }
}
