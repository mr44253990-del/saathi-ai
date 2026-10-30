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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.saathi.ai.live.Phase
import com.saathi.ai.ui.theme.Aqua
import com.saathi.ai.ui.theme.Pink
import com.saathi.ai.ui.theme.Violet
import com.saathi.ai.ui.theme.VioletSoft
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The animated "living" core of the assistant. Speed, colour and ring count
 * react to the real session state (listening / thinking / speaking) and to the
 * live microphone level.
 */
@Composable
fun SaathiOrb(
    phase: Phase,
    level: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (phase) {
                    Phase.SPEAKING -> 3600
                    Phase.THINKING -> 2200
                    Phase.RECONNECTING -> 5000
                    else -> 9000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val counterSpin by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter"
    )

    val active = phase == Phase.LISTENING || phase == Phase.SPEAKING || phase == Phase.THINKING
    val energy = if (active) level.coerceIn(0f, 1f) else 0f

    val coreA = when (phase) {
        Phase.ERROR -> Color(0xFFFF6B81)
        Phase.ENDED -> Color(0xFF5A6482)
        Phase.RECONNECTING -> Color(0xFF7C9CFF)
        Phase.SPEAKING -> Pink
        Phase.THINKING -> VioletSoft
        else -> Violet
    }
    val coreB = when (phase) {
        Phase.SPEAKING -> Violet
        Phase.THINKING -> Aqua
        Phase.LISTENING -> Aqua
        else -> Color(0xFF2A2F6B)
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val base = size.minDimension * 0.30f
            val pulse = base * (1f + 0.05f * breathe + 0.20f * energy)

            // Outer halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreA.copy(alpha = 0.30f + 0.25f * energy), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = pulse * 2.05f
                ),
                radius = pulse * 2.05f,
                center = Offset(cx, cy)
            )

            // Orbiting arcs
            rotate(spin, Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            coreA.copy(alpha = 0.9f),
                            Aqua.copy(alpha = 0.7f),
                            Color.Transparent,
                            coreA.copy(alpha = 0.9f)
                        ),
                        center = Offset(cx, cy)
                    ),
                    startAngle = 0f,
                    sweepAngle = 230f,
                    useCenter = false,
                    topLeft = Offset(cx - pulse * 1.34f, cy - pulse * 1.34f),
                    size = Size(pulse * 2.68f, pulse * 2.68f),
                    style = Stroke(width = size.minDimension * 0.012f, cap = StrokeCap.Round)
                )
            }
            rotate(counterSpin, Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Pink.copy(alpha = 0.85f),
                            Color.Transparent,
                            Aqua.copy(alpha = 0.55f),
                            Pink.copy(alpha = 0.85f)
                        ),
                        center = Offset(cx, cy)
                    ),
                    startAngle = 40f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - pulse * 1.62f, cy - pulse * 1.62f),
                    size = Size(pulse * 3.24f, pulse * 3.24f),
                    style = Stroke(width = size.minDimension * 0.006f, cap = StrokeCap.Round)
                )
            }

            // Audio-reactive ring
            if (active) {
                val bars = 64
                for (i in 0 until bars) {
                    val angle = (i.toFloat() / bars) * 2f * Math.PI
                    val wave = 0.35f + 0.65f * abs(sin(i * 0.7f + spin * 0.02f))
                    val len = pulse * (0.10f + 0.42f * energy * wave)
                    val r0 = pulse * 1.02f
                    val x0 = cx + (r0 * cos(angle)).toFloat()
                    val y0 = cy + (r0 * sin(angle)).toFloat()
                    val x1 = cx + ((r0 + len) * cos(angle)).toFloat()
                    val y1 = cy + ((r0 + len) * sin(angle)).toFloat()
                    val alpha = (0.10f + 0.55f * energy).coerceIn(0.05f, 0.75f)
                    drawLine(
                        color = if (i % 3 == 0) Aqua.copy(alpha = alpha) else coreA.copy(alpha = alpha),
                        start = Offset(x0, y0),
                        end = Offset(x1, y1),
                        strokeWidth = size.minDimension * 0.008f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Core sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        coreA.copy(alpha = 0.95f),
                        coreB.copy(alpha = 0.9f),
                        Color(0xFF141A3A)
                    ),
                    center = Offset(cx - pulse * 0.28f, cy - pulse * 0.32f),
                    radius = pulse * 1.35f
                ),
                radius = pulse,
                center = Offset(cx, cy)
            )

            // Specular highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(cx - pulse * 0.36f, cy - pulse * 0.42f),
                    radius = pulse * 0.55f
                ),
                radius = pulse * 0.55f,
                center = Offset(cx - pulse * 0.36f, cy - pulse * 0.42f)
            )
        }
    }
}
