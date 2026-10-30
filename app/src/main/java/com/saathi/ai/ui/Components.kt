package com.saathi.ai.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
