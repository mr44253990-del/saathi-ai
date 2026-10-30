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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.saathi.ai.live.Phase
import com.saathi.ai.ui.theme.Aqua
import com.saathi.ai.ui.theme.Pink
import com.saathi.ai.ui.theme.Violet
import com.saathi.ai.ui.theme.VioletSoft
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * JIMI's core — the whole visual identity of the assistant.
 *
 * Layers, from back to front:
 *  1. atmospheric bloom
 *  2. two counter-rotating sweep rings
 *  3. a 96-bar radial spectrum that reacts to the real microphone level
 *  4. a liquid-wave body whose edges ripple with the voice
 *  5. a bright nucleus + specular highlight
 *  6. orbiting "satellite" particles while Jimi is thinking/acting
 *
 * Every number below is driven by [phase] (session state) and [level]
 * (RMS of the mic / speaker), so the visualiser is genuinely reactive, not a
 * decorative loop.
 */
@Composable
fun JimiOrb(
    phase: Phase,
    level: Float,
    modifier: Modifier = Modifier
) {
    val t = rememberInfiniteTransition(label = "jimi")
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(
            tween(
                when (phase) {
                    Phase.SPEAKING -> 2600
                    Phase.THINKING -> 1700
                    Phase.RECONNECTING -> 4200
                    else -> 7200
                }, easing = LinearEasing
            ),
            RepeatMode.Restart
        ), label = "spin"
    )
    val counter by t.animateFloat(
        360f, 0f,
        infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Restart),
        label = "counter"
    )
    val breathe by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val shimmer by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    val active = phase == Phase.LISTENING || phase == Phase.SPEAKING || phase == Phase.THINKING
    val energy = if (active) level.coerceIn(0f, 1f) else 0f
    val busy = phase == Phase.THINKING || phase == Phase.RECONNECTING

    val c1 = when (phase) {
        Phase.ERROR -> Color(0xFFFF5D73)
        Phase.ENDED -> Color(0xFF475069)
        Phase.RECONNECTING -> Color(0xFF6C8CFF)
        Phase.SPEAKING -> Pink
        Phase.THINKING -> VioletSoft
        else -> Violet
    }
    val c2 = when (phase) {
        Phase.SPEAKING -> Color(0xFF9B6BFF)
        Phase.THINKING, Phase.LISTENING -> Aqua
        Phase.RECONNECTING -> Aqua
        else -> Color(0xFF2B3470)
    }
    val c3 = if (phase == Phase.SPEAKING) Aqua else Pink

    // Stable pseudo-random satellite offsets
    val seeds = remember { List(9) { i -> (i * 37 % 100) / 100f } }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val base = size.minDimension * 0.27f
            val pulse = base * (1f + 0.045f * breathe + 0.22f * energy)
            val minDim = size.minDimension

            // 1 — atmospheric bloom
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        c1.copy(alpha = 0.26f + 0.3f * energy),
                        c2.copy(alpha = 0.10f + 0.12f * energy),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = pulse * 2.35f
                ),
                radius = pulse * 2.35f,
                center = Offset(cx, cy)
            )

            // 2 — counter-rotating sweep rings
            rotate(spin, Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            c1.copy(alpha = 0.95f), c2.copy(alpha = 0.75f),
                            Color.Transparent, c1.copy(alpha = 0.95f)
                        ),
                        center = Offset(cx, cy)
                    ),
                    startAngle = 0f, sweepAngle = 250f, useCenter = false,
                    topLeft = Offset(cx - pulse * 1.38f, cy - pulse * 1.38f),
                    size = Size(pulse * 2.76f, pulse * 2.76f),
                    style = Stroke(width = minDim * 0.011f, cap = StrokeCap.Round)
                )
            }
            rotate(counter, Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            c3.copy(alpha = 0.8f), Color.Transparent,
                            c2.copy(alpha = 0.6f), c3.copy(alpha = 0.8f)
                        ),
                        center = Offset(cx, cy)
                    ),
                    startAngle = 30f, sweepAngle = 170f, useCenter = false,
                    topLeft = Offset(cx - pulse * 1.66f, cy - pulse * 1.66f),
                    size = Size(pulse * 3.32f, pulse * 3.32f),
                    style = Stroke(width = minDim * 0.005f, cap = StrokeCap.Round)
                )
            }

            // 3 — radial spectrum (mirrors the real audio energy)
            val bars = 96
            for (i in 0 until bars) {
                val angle = (i.toFloat() / bars) * 2f * PI
                val sway = abs(sin(i * 0.55f + spin * 0.02f))
                val gate = if (active) (0.25f + 0.75f * sway) else 0.18f
                val len = pulse * (0.07f + (0.55f * energy + 0.06f) * gate)
                val r0 = pulse * 1.04f
                val x0 = cx + (r0 * cos(angle)).toFloat()
                val y0 = cy + (r0 * sin(angle)).toFloat()
                val x1 = cx + ((r0 + len) * cos(angle)).toFloat()
                val y1 = cy + ((r0 + len) * sin(angle)).toFloat()
                val a = (0.08f + 0.62f * energy).coerceIn(0.04f, 0.8f)
                drawLine(
                    color = when {
                        i % 5 == 0 -> Color.White.copy(alpha = a)
                        i % 2 == 0 -> c2.copy(alpha = a)
                        else -> c1.copy(alpha = a)
                    },
                    start = Offset(x0, y0), end = Offset(x1, y1),
                    strokeWidth = minDim * 0.007f, cap = StrokeCap.Round
                )
            }

            // 4 — liquid wave body (3 offset waves = organic edge)
            val waves = 3
            for (w in 0 until waves) {
                val path = Path()
                val r = pulse * (1f + 0.05f * w) * (1f + 0.05f * energy)
                val steps = 90
                val amp = (minDim * 0.012f) + minDim * 0.022f * energy
                for (s in 0..steps) {
                    val ang = (s.toFloat() / steps) * 2f * PI
                    val wob = sin(ang * (3f + w) + spin * 0.035f + w * 1.7f) *
                        cos(ang * 2f + shimmer * 6.28f) * amp
                    val rr = r + wob
                    val x = cx + (rr * cos(ang)).toFloat()
                    val y = cy + (rr * sin(ang)).toFloat()
                    if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(
                    path = path,
                    brush = Brush.radialGradient(
                        colors = when (w) {
                            0 -> listOf(Color.White.copy(alpha = 0.92f), c1.copy(alpha = 0.92f), c2.copy(alpha = 0.85f), Color(0xFF101438))
                            else -> listOf(c1.copy(alpha = 0.16f), c2.copy(alpha = 0.06f), Color.Transparent)
                        },
                        center = Offset(cx - pulse * 0.26f, cy - pulse * 0.3f),
                        radius = r * 1.5f
                    )
                )
            }

            // 5 — nucleus + specular
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.95f),
                        c1.copy(alpha = 0.85f),
                        Color.Transparent
                    ),
                    center = Offset(cx - pulse * 0.32f, cy - pulse * 0.36f),
                    radius = pulse * 0.7f
                ),
                radius = pulse * 0.7f,
                center = Offset(cx - pulse * 0.32f, cy - pulse * 0.36f)
            )

            // 6 — satellites while Jimi is working
            if (busy) {
                seeds.forEachIndexed { i, s ->
                    val ang = (i / seeds.size.toFloat()) * 2f * PI + spin * 0.02f
                    val rad = pulse * (1.55f + 0.18f * sin(shimmer * 6.28f + s * 6f))
                    val x = cx + (rad * cos(ang)).toFloat()
                    val y = cy + (rad * sin(ang)).toFloat()
                    drawCircle(
                        color = (if (i % 2 == 0) Aqua else c3).copy(alpha = 0.55f + 0.35f * abs(sin(shimmer * 6.28f + i))),
                        radius = minDim * (0.006f + 0.004f * s),
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}
