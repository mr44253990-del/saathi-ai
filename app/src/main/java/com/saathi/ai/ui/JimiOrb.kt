package com.saathi.ai.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.saathi.ai.live.Phase
import com.saathi.ai.ui.theme.LocalJimi
import com.saathi.ai.ui.theme.OrbStyle
import com.saathi.ai.ui.theme.jimi
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Jimi orb.
 *
 * Layers (back → front):
 *   1. bloom haze
 *   2. two counter-rotating sweep rings with a comet tail
 *   3. a 96-bar radial spectrum that reads the live mic/voice level
 *   4. three liquid wave bodies (sine sheets clipped to a circle)
 *   5. a glass nucleus with a specular highlight
 *   6. satellites that orbit only while Jimi is working
 *
 * Colours come from the active theme, so every one of the 12 themes gets a
 * genuinely different orb — not just a recoloured one.
 */
@Composable
fun JimiOrb(
    phase: Phase,
    level: Float,
    modifier: Modifier = Modifier
) {
    val spec = LocalJimi.current
    val p = jimi

    val transition = rememberInfiniteTransition(label = "orb")

    val spin by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "spin"
    )
    val spinBack by transition.animateFloat(
        0f, -360f,
        infiniteRepeatable(tween(22000, easing = LinearEasing)),
        label = "spinBack"
    )
    val breathe by transition.animateFloat(
        0.96f, 1.04f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val wave by transition.animateFloat(
        0f, 6.2831f,
        infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "wave"
    )

    val mood = when (phase) {
        Phase.LISTENING -> 1f
        Phase.SPEAKING -> 1f
        Phase.THINKING -> 0.72f
        Phase.CONNECTING, Phase.RECONNECTING -> 0.5f
        Phase.ERROR -> 0.22f
        else -> 0.45f
    }
    val amp = (level.coerceIn(0f, 1f) * 0.75f + 0.25f) * mood

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = (size.minDimension / 2f) * 0.92f

            drawBloom(cx, cy, r, p.glowA, p.glowB, amp, breathe)
            drawSweeps(cx, cy, r, p, spin, spinBack, mood)
            drawSpectrum(cx, cy, r, p, amp, level, spin)
            drawCore(cx, cy, r * 0.72f, p, breathe, wave, amp, phase)
            drawSpecular(cx, cy, r * 0.72f, p)
            if (spec.orbStyle == OrbStyle.PRISM || spec.orbStyle == OrbStyle.NEBULA) {
                drawSatellites(cx, cy, r, p, spin, mood)
            }
        }
    }
}

private fun DrawScope.drawBloom(
    cx: Float, cy: Float, r: Float,
    glowA: Color, glowB: Color,
    amp: Float, breathe: Float
) {
    val radius = r * (1.15f + 0.08f * breathe)
    drawCircle(
        brush = Brush.radialGradient(
            0f to glowA.copy(alpha = 0.40f * amp + 0.10f),
            0.45f to glowB.copy(alpha = 0.18f * amp),
            1f to Color.Transparent,
            center = Offset(cx, cy),
            radius = radius
        ),
        radius = radius,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawSweeps(
    cx: Float, cy: Float, r: Float,
    p: com.saathi.ai.ui.theme.JimiPalette,
    spin: Float, spinBack: Float, mood: Float
) {
    // outer comet ring
    rotate(spin, Offset(cx, cy)) {
        drawArc(
            brush = Brush.sweepGradient(
                listOf(
                    Color.Transparent,
                    p.secondary.copy(alpha = 0.0f),
                    p.secondary.copy(alpha = 0.85f * mood + 0.1f),
                    p.primary.copy(alpha = 0.95f * mood + 0.05f),
                    Color.Transparent
                ),
                center = Offset(cx, cy)
            ),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(2 * r, 2 * r),
            style = Stroke(width = r * 0.055f)
        )
    }
    // inner counter ring
    rotate(spinBack, Offset(cx, cy)) {
        drawArc(
            brush = Brush.sweepGradient(
                listOf(
                    Color.Transparent,
                    p.accent.copy(alpha = 0.7f * mood + 0.1f),
                    Color.Transparent
                ),
                center = Offset(cx, cy)
            ),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(cx - r * 0.84f, cy - r * 0.84f),
            size = Size(r * 1.68f, r * 1.68f),
            style = Stroke(width = r * 0.03f)
        )
    }
}

private fun DrawScope.drawSpectrum(
    cx: Float, cy: Float, r: Float,
    p: com.saathi.ai.ui.theme.JimiPalette,
    amp: Float, level: Float, spin: Float
) {
    val bars = 96
    val base = r * 0.80f
    for (i in 0 until bars) {
        val t = i.toFloat() / bars
        val ang = t * 6.2831f
        // pseudo-spectrum: deterministic per-index noise shaped by the live level
        val noise = (sin(i * 1.7f + spin * 0.02f) * 0.5f + 0.5f) *
            (sin(i * 0.37f) * 0.5f + 0.5f)
        val h = r * (0.035f + 0.20f * amp * noise + 0.05f * level * noise)
        val sx = cx + cos(ang) * base
        val sy = cy + sin(ang) * base
        val ex = cx + cos(ang) * (base + h)
        val ey = cy + sin(ang) * (base + h)
        val c = if (t < 0.5f) p.secondary else p.primary
        drawLine(
            color = c.copy(alpha = 0.55f * amp + 0.08f),
            start = Offset(sx, sy),
            end = Offset(ex, ey),
            strokeWidth = r * 0.012f
        )
    }
}

private fun DrawScope.drawCore(
    cx: Float, cy: Float, r: Float,
    p: com.saathi.ai.ui.theme.JimiPalette,
    breathe: Float, wave: Float, amp: Float, phase: Phase
) {
    // liquid body clipped to a circle, built from three sine sheets
    val path = Path()
    val steps = 64
    val top = cy - r
    path.moveTo(cx - r, cy + r * 0.1f)
    for (i in 0..steps) {
        val x = cx - r + (2 * r) * (i.toFloat() / steps)
        val y = top + r * 0.55f +
            sin((i.toFloat() / steps) * 6.2831f * 1.4f + wave) * r * 0.10f * amp +
            sin((i.toFloat() / steps) * 6.2831f * 2.7f - wave * 0.7f) * r * 0.06f * amp
        path.lineTo(x, y)
    }
    path.lineTo(cx + r, cy + r)
    path.lineTo(cx - r, cy + r)
    path.close()

    drawCircle(
        brush = Brush.radialGradient(
            0f to p.bgMid,
            1f to p.bgTop,
            center = Offset(cx - r * 0.2f, cy - r * 0.3f),
            radius = r * 1.7f
        ),
        radius = r * breathe,
        center = Offset(cx, cy)
    )

    drawCircle(
        brush = Brush.verticalGradient(
            listOf(
                p.secondary.copy(alpha = 0.25f + 0.3f * amp),
                p.primary.copy(alpha = 0.55f + 0.3f * amp),
                p.accent.copy(alpha = 0.35f + 0.3f * amp)
            ),
            startY = top,
            endY = cy + r
        ),
        radius = r * breathe,
        center = Offset(cx, cy)
    )

    // clip the liquid sheet into the nucleus by drawing it scaled a touch smaller
    scale(0.99f, 0.99f, Offset(cx, cy)) {
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                listOf(
                    p.accent.copy(alpha = 0.55f * amp + 0.15f),
                    p.primary.copy(alpha = 0.75f * amp + 0.2f)
                ),
                startY = top,
                endY = cy + r
            )
        )
    }

    // rim light
    drawCircle(
        color = Color.White.copy(alpha = 0.10f + 0.12f * amp),
        radius = r * breathe,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.02f)
    )

    if (phase == Phase.ERROR) {
        drawCircle(
            color = Color(0xFFFF6B81).copy(alpha = 0.30f),
            radius = r,
            center = Offset(cx, cy),
            blendMode = BlendMode.Screen
        )
    }
}

private fun DrawScope.drawSpecular(
    cx: Float, cy: Float, r: Float,
    p: com.saathi.ai.ui.theme.JimiPalette
) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.55f),
            1f to Color.Transparent,
            center = Offset(cx - r * 0.32f, cy - r * 0.38f),
            radius = r * 0.55f
        ),
        radius = r * 0.55f,
        center = Offset(cx - r * 0.32f, cy - r * 0.38f)
    )
}

private fun DrawScope.drawSatellites(
    cx: Float, cy: Float, r: Float,
    p: com.saathi.ai.ui.theme.JimiPalette,
    spin: Float, mood: Float
) {
    if (mood < 0.6f) return
    val count = 9
    for (i in 0 until count) {
        val a = (spin * 1.4f + i * (360f / count)) * (3.14159f / 180f)
        val orbit = r * (1.02f + 0.05f * sin(a * 2))
        val x = cx + cos(a) * orbit
        val y = cy + sin(a) * orbit
        val radius = r * (0.012f + 0.006f * (i % 3))
        drawCircle(
            color = (if (i % 2 == 0) p.accent else p.secondary).copy(alpha = 0.85f),
            radius = radius,
            center = Offset(x, y)
        )
    }
}
