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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import com.saathi.ai.live.Phase
import com.saathi.ai.ui.theme.JimiPalette
import com.saathi.ai.ui.theme.LocalJimi
import com.saathi.ai.ui.theme.OrbStyle
import com.saathi.ai.ui.theme.jimi
import kotlin.math.cos
import kotlin.math.sin

/**
 * Jimi's orb.
 *
 * Everything here is **circular geometry only** — an earlier version drew a wave
 * sheet with straight left/right edges, which read as an ugly box sitting in the
 * middle of the screen. Now the liquid layer is clipped to the nucleus oval, so
 * whatever the phase, the silhouette stays a clean sphere.
 *
 * Layers, back → front: bloom haze → counter-rotating comet rings → 96-bar
 * spectrum → clipped liquid core → glass rim → specular highlight → satellites.
 */
@Composable
fun JimiOrb(
    phase: Phase,
    level: Float,
    modifier: Modifier = Modifier,
    interactive: Boolean = true
) {
    val spec = LocalJimi.current
    val p = jimi

    val t = rememberInfiniteTransition(label = "orb")

    val spin by t.animateFloat(
        0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "spin"
    )
    val spinBack by t.animateFloat(
        0f, -360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "spinBack"
    )
    val breathe by t.animateFloat(
        0.97f, 1.03f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val wave by t.animateFloat(
        0f, 6.2831f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "wave"
    )

    val mood = when (phase) {
        Phase.LISTENING, Phase.SPEAKING -> 1f
        Phase.THINKING -> 0.75f
        Phase.CONNECTING, Phase.RECONNECTING -> 0.5f
        Phase.ERROR -> 0.2f
        else -> 0.42f
    }
    val amp = ((level.coerceIn(0f, 1f) * 0.7f) + 0.3f) * mood

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f * 0.92f

            drawBloom(cx, cy, r, p, amp, breathe)
            drawRings(cx, cy, r, p, spin, spinBack, mood)
            drawSpectrum(cx, cy, r, p, amp, level, spin)
            drawCore(cx, cy, r * 0.70f, p, breathe, wave, amp, phase)
            drawRim(cx, cy, r * 0.70f, p, amp)
            drawSpecular(cx, cy, r * 0.70f)
            if (spec.orbStyle == OrbStyle.PRISM || spec.orbStyle == OrbStyle.NEBULA) {
                drawSatellites(cx, cy, r, p, spin, mood)
            }
        }
    }
}

// ---------------------------------------------------------------- layers

private fun DrawScope.drawBloom(cx: Float, cy: Float, r: Float, p: JimiPalette, amp: Float, breathe: Float) {
    val radius = r * (1.08f + 0.06f * breathe + 0.05f * amp)
    drawCircle(
        brush = Brush.radialGradient(
            0f to p.glowA.copy(alpha = 0.34f * amp + 0.12f),
            0.5f to p.glowB.copy(alpha = 0.16f * amp),
            1f to Color.Transparent,
            center = Offset(cx, cy),
            radius = radius
        ),
        radius = radius,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawRings(
    cx: Float, cy: Float, r: Float, p: JimiPalette,
    spin: Float, spinBack: Float, mood: Float
) {
    rotate(spin, Offset(cx, cy)) {
        drawArc(
            brush = Brush.sweepGradient(
                listOf(
                    Color.Transparent,
                    p.secondary.copy(alpha = 0.05f),
                    p.secondary.copy(alpha = 0.55f * mood + 0.15f),
                    p.primary.copy(alpha = 0.85f * mood + 0.10f),
                    Color.Transparent
                ),
                center = Offset(cx, cy)
            ),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(cx - r * 0.94f, cy - r * 0.94f),
            size = Size(r * 1.88f, r * 1.88f),
            style = Stroke(width = r * 0.05f)
        )
    }
    rotate(spinBack, Offset(cx, cy)) {
        drawArc(
            color = p.accent.copy(alpha = 0.30f * mood + 0.06f),
            startAngle = 0f, sweepAngle = 240f, useCenter = false,
            topLeft = Offset(cx - r * 0.80f, cy - r * 0.80f),
            size = Size(r * 1.60f, r * 1.60f),
            style = Stroke(width = r * 0.016f)
        )
    }
}

private fun DrawScope.drawSpectrum(
    cx: Float, cy: Float, r: Float, p: JimiPalette,
    amp: Float, level: Float, spin: Float
) {
    val bars = 96
    val base = r * 0.755f
    for (i in 0 until bars) {
        val frac = i.toFloat() / bars
        val ang = frac * 6.2831f
        val noise = (sin(i * 1.63f + spin * 0.02f) * 0.5f + 0.5f) *
            (sin(i * 0.41f - spin * 0.01f) * 0.5f + 0.5f)
        val h = r * (0.02f + 0.16f * amp * noise + 0.06f * level * noise)
        drawLine(
            color = (if (frac < 0.5f) p.secondary else p.primary).copy(alpha = 0.42f * amp + 0.08f),
            start = Offset(cx + cos(ang) * base, cy + sin(ang) * base),
            end = Offset(cx + cos(ang) * (base + h), cy + sin(ang) * (base + h)),
            strokeWidth = r * 0.010f
        )
    }
}

/** Liquid core, strictly clipped to the nucleus circle — no straight edges. */
private fun DrawScope.drawCore(
    cx: Float, cy: Float, r: Float, p: JimiPalette,
    breathe: Float, wave: Float, amp: Float, phase: Phase
) {
    val rr = r * breathe
    val oval = Path().apply { addOval(Rect(cx - rr, cy - rr, cx + rr, cy + rr)) }

    drawCircle(
        brush = Brush.radialGradient(
            0f to p.bgMid,
            1f to p.bgTop,
            center = Offset(cx - rr * 0.25f, cy - rr * 0.3f),
            radius = rr * 1.6f
        ),
        radius = rr, center = Offset(cx, cy)
    )

    clipPath(oval) {
        val steps = 72
        val liquid = Path()
        val top = cy - rr
        liquid.moveTo(cx - rr, cy + rr)
        for (i in 0..steps) {
            val x = cx - rr + (2 * rr) * (i.toFloat() / steps)
            val y = top + rr * (1.05f - 0.22f * amp) +
                sin((i.toFloat() / steps) * 6.2831f * 1.3f + wave) * rr * 0.085f * amp +
                sin((i.toFloat() / steps) * 6.2831f * 2.9f - wave * 0.8f) * rr * 0.045f * amp
            liquid.lineTo(x, y)
        }
        liquid.lineTo(cx + rr, cy + rr)
        liquid.close()

        drawPath(
            path = liquid,
            brush = Brush.verticalGradient(
                listOf(
                    p.secondary.copy(alpha = 0.50f + 0.35f * amp),
                    p.primary.copy(alpha = 0.70f + 0.25f * amp),
                    p.accent.copy(alpha = 0.55f + 0.30f * amp)
                ),
                startY = top, endY = cy + rr
            )
        )
    }

    if (phase == Phase.ERROR) {
        drawCircle(
            color = Color(0xFFFF6B81).copy(alpha = 0.22f),
            radius = rr, center = Offset(cx, cy)
        )
    }
}

private fun DrawScope.drawRim(cx: Float, cy: Float, r: Float, p: JimiPalette, amp: Float) {
    drawCircle(
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.34f),
                Color.Transparent,
                p.accent.copy(alpha = 0.25f * amp)
            ),
            startY = cy - r, endY = cy + r
        ),
        radius = r, center = Offset(cx, cy),
        style = Stroke(width = r * 0.035f)
    )
}

private fun DrawScope.drawSpecular(cx: Float, cy: Float, r: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.48f),
            1f to Color.Transparent,
            center = Offset(cx - r * 0.34f, cy - r * 0.40f),
            radius = r * 0.52f
        ),
        radius = r * 0.52f,
        center = Offset(cx - r * 0.34f, cy - r * 0.40f)
    )
}

private fun DrawScope.drawSatellites(
    cx: Float, cy: Float, r: Float, p: JimiPalette, spin: Float, mood: Float
) {
    if (mood < 0.6f) return
    val count = 8
    for (i in 0 until count) {
        val a = (spin * 1.3f + i * (360f / count)) * 0.0174533f
        val orbit = r * (1.0f + 0.04f * sin(a * 2f))
        drawCircle(
            color = (if (i % 2 == 0) p.accent else p.secondary).copy(alpha = 0.8f),
            radius = r * 0.013f,
            center = Offset(cx + cos(a) * orbit, cy + sin(a) * orbit)
        )
    }
}
