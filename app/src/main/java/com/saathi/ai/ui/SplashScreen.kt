package com.saathi.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.saathi.ai.live.Phase
import kotlinx.coroutines.delay

/**
 * JIMI splash — a single word, one living orb, and a heartbeat line.
 * Deliberately minimal: the orb does the talking.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val appear = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(900))
        delay(720)
        lift.animateTo(1f, tween(380))
        onFinished()
    }

    SaathiBackground {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(250.dp)
                    .scale(0.78f + 0.22f * appear.value)
                    .alpha(appear.value),
                contentAlignment = Alignment.Center
            ) {
                JimiOrb(phase = Phase.LISTENING, level = 0.22f, modifier = Modifier.fillMaxSize())
            }

            // wordmark sits just under the orb, inside the same optical centre
            Text(
                text = "J I M I",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp)
                    .alpha(appear.value)
            )

            Text(
                text = "শুধু বলো — বাকিটা আমি করব",
                fontSize = 12.sp,
                color = Color(0xFF9AA3C0),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .alpha(appear.value * 0.9f)
            )
        }
    }
}
