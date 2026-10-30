package com.saathi.ai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.live.Phase
import com.saathi.ai.ui.theme.Aqua
import com.saathi.ai.ui.theme.TextMuted
import com.saathi.ai.ui.theme.Violet
import kotlinx.coroutines.delay

/**
 * Splash screen: the orb "wakes up" while the app prepares its first state.
 * [onFinished] fires after the intro animation and a short hold.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val appear = remember { Animatable(0f) }
    val fadeOut = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        appear.animateTo(1f, animationSpec = tween(1000, easing = LinearOutSlowInEasing))
        delay(600)
        fadeOut.animateTo(0f, animationSpec = tween(380, easing = LinearEasing))
        onFinished()
    }

    SaathiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .alpha(fadeOut.value)
                .scale(0.98f + 0.02f * fadeOut.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .scale(0.72f + 0.28f * appear.value)
                    .alpha(appear.value),
                contentAlignment = Alignment.Center
            ) {
                SaathiOrb(phase = Phase.LISTENING, level = 0.25f, modifier = Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "সাথী",
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.alpha(appear.value)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "তোমার সবসময়ের AI সঙ্গী",
                fontSize = 14.sp,
                color = TextMuted,
                modifier = Modifier.alpha(appear.value)
            )

            Spacer(Modifier.height(34.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { i ->
                    val a = remember { Animatable(0.25f) }
                    LaunchedEffect(i) {
                        delay(i * 160L)
                        while (true) {
                            a.animateTo(1f, tween(420))
                            a.animateTo(0.25f, tween(420))
                        }
                    }
                    Box(
                        Modifier
                            .size(7.dp)
                            .alpha(a.value)
                            .background(
                                Brush.linearGradient(listOf(Violet, Aqua)),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "প্রস্তুত হচ্ছে…",
                fontSize = 11.sp,
                color = TextMuted.copy(alpha = 0.7f),
                modifier = Modifier.alpha(appear.value * 0.9f)
            )
        }

        Text(
            text = "Gemini Live · powered",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.22f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .alpha(fadeOut.value)
        )
    }
}
