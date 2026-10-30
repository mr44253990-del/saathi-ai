package com.saathi.ai.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionState
import com.saathi.ai.ui.theme.Aqua
import com.saathi.ai.ui.theme.Pink
import com.saathi.ai.ui.theme.TextMuted
import com.saathi.ai.ui.theme.TextPrimary
import com.saathi.ai.ui.theme.Violet

@Composable
fun HomeScreen(
    state: SessionState,
    configured: Boolean,
    hasMicPermission: Boolean,
    accessibilityOn: Boolean,
    bubbleOn: Boolean,
    hotwordOn: Boolean,
    cameraPreviewView: PreviewView?,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleScreen: () -> Unit,
    onToggleBubble: () -> Unit,
    onToggleHotword: () -> Unit,
    onSendText: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onMinimize: () -> Unit
) {
    var showText by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current

    val running = state.phase != Phase.IDLE &&
        state.phase != Phase.ENDED &&
        state.phase != Phase.ERROR

    SaathiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {

            // ------------------------------ top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // JIMI wordmark with animated gradient dot
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "JIMI",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(6.dp))
                        val t = rememberInfiniteTransition(label = "dot")
                        val a by t.animateFloat(
                            0.35f, 1f,
                            infiniteRepeatable(tween(900), RepeatMode.Reverse),
                            label = "a"
                        )
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Aqua.copy(alpha = a))
                        )
                    }
                    Text(
                        text = if (state.model.isBlank()) "Gemini Live" else state.model,
                        fontSize = 10.sp,
                        color = TextMuted,
                        letterSpacing = 0.4.sp
                    )
                }

                StatusChip(text = phaseLabel(state.phase), dotColor = phaseColor(state.phase))

                Spacer(Modifier.width(8.dp))

                // minimise into the floating bubble
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onMinimize() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SaathiIcons.Minimize, "ছোট করো", tint = Aqua, modifier = Modifier.size(18.dp))
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        SaathiIcons.Settings,
                        "সেটিংস",
                        tint = TextPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // ------------------------------ orb + overlays
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                JimiOrb(
                    phase = state.phase,
                    level = state.level,
                    modifier = Modifier.size(320.dp)
                )

                if (state.cameraOn && cameraPreviewView != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(104.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.5.dp, Aqua.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    ) {
                        AndroidView(factory = { cameraPreviewView }, modifier = Modifier.fillMaxSize())
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xCC04121A))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("LIVE", color = Aqua, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (state.screenOn) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(50))
                            .background(Violet.copy(alpha = 0.22f))
                            .border(1.dp, Violet.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(SaathiIcons.ScreenShare, null, tint = Violet, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("স্ক্রিন শেয়ারিং", color = TextPrimary.copy(alpha = 0.9f), fontSize = 10.sp)
                    }
                }

                if (!accessibilityOn) {
                    Text(
                        text = "Accessibility বন্ধ — Jimi টাচ/স্ক্রিন দেখতে পারবে না",
                        color = Color(0xFFFFB86B),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp)
                    )
                }
            }

            // ------------------------------ transcript / hint
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val shown = when {
                    state.error != null -> state.error!!
                    state.modelText.isNotBlank() -> state.modelText
                    state.userText.isNotBlank() -> state.userText
                    else -> hintFor(state.phase, configured, hasMicPermission)
                }
                val isUser = state.modelText.isBlank() &&
                    state.userText.isNotBlank() &&
                    state.error == null

                Text(
                    text = shown,
                    color = when {
                        state.error != null -> Color(0xFFFF8A9C)
                        isUser -> Aqua.copy(alpha = 0.9f)
                        else -> TextPrimary.copy(alpha = 0.82f)
                    },
                    fontSize = if (state.error != null) 12.5.sp else 13.5.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (state.userText.isNotBlank() && state.modelText.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "তুমি: " + state.userText.takeLast(90),
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ------------------------------ controls
            if (!running) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StartPill(
                        label = if (configured) "Jimi-র সাথে কথা বলো" else "আগে API Key সেট করো",
                        onClick = if (configured && hasMicPermission) onStart else onOpenSettings
                    )
                }
                // quick toggles row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 22.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    ToggleChip("Floating bubble", bubbleOn, onToggleBubble)
                    Spacer(Modifier.width(8.dp))
                    ToggleChip("\"হেই Jimi\"", hotwordOn, onToggleHotword)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundAction(
                        icon = if (state.micOn) SaathiIcons.Mic else SaathiIcons.MicOff,
                        label = if (state.micOn) "মাইক" else "বন্ধ",
                        active = state.micOn,
                        onClick = onToggleMic
                    )
                    RoundAction(
                        icon = if (state.cameraOn) SaathiIcons.Videocam else SaathiIcons.VideocamOff,
                        label = "ক্যামেরা",
                        active = state.cameraOn,
                        onClick = onToggleCamera
                    )
                    RoundAction(
                        icon = SaathiIcons.ScreenShare,
                        label = "স্ক্রিন",
                        active = state.screenOn,
                        onClick = onToggleScreen
                    )
                    RoundAction(
                        icon = SaathiIcons.Keyboard,
                        label = "লিখো",
                        active = showText,
                        onClick = { showText = !showText }
                    )
                    RoundAction(
                        icon = SaathiIcons.Bubble,
                        label = "ছোট করো",
                        active = bubbleOn,
                        onClick = onMinimize
                    )
                    RoundAction(
                        icon = SaathiIcons.StopCircle,
                        label = "শেষ",
                        tint = Color(0xFFFF8A9C),
                        onClick = onEnd
                    )
                }

                if (state.cameraOn) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.07f))
                                .clickable { onSwitchCamera() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                SaathiIcons.Cameraswitch,
                                null,
                                tint = TextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("ক্যামেরা বদলাও", color = TextPrimary, fontSize = 11.5.sp)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showText,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                            .padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("লিখে বলো…", color = TextMuted, fontSize = 13.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = Aqua,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            maxLines = 3
                        )
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Violet, Aqua)))
                                .clickable {
                                    if (text.isNotBlank()) {
                                        onSendText(text)
                                        text = ""
                                        focus.clearFocus()
                                        showText = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                SaathiIcons.Send,
                                "পাঠাও",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) Aqua.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (on) Aqua.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (on) Aqua else TextMuted)
        )
        Spacer(Modifier.width(7.dp))
        Text(label, color = if (on) Aqua else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StartPill(label: String, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pill")
    val glow by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Violet.copy(alpha = 0.75f + 0.25f * glow),
                        Aqua.copy(alpha = 0.69f + 0.23f * glow)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SaathiIcons.Mic, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

fun phaseLabel(phase: Phase): String = when (phase) {
    Phase.IDLE -> "প্রস্তুত"
    Phase.CONNECTING -> "সংযোগ হচ্ছে…"
    Phase.LISTENING -> "শুনছি"
    Phase.THINKING -> "ভাবছি"
    Phase.SPEAKING -> "বলছি"
    Phase.RECONNECTING -> "রিকানেক্ট"
    Phase.ERROR -> "সমস্যা"
    Phase.ENDED -> "শেষ"
}

fun phaseColor(phase: Phase): Color = when (phase) {
    Phase.IDLE -> TextMuted
    Phase.CONNECTING, Phase.RECONNECTING -> Color(0xFFFFB86B)
    Phase.LISTENING -> Aqua
    Phase.THINKING -> Violet
    Phase.SPEAKING -> Pink
    Phase.ERROR -> Color(0xFFFF6B81)
    Phase.ENDED -> TextMuted
}

private fun hintFor(phase: Phase, configured: Boolean, mic: Boolean): String = when {
    !configured -> "শুরু করতে সেটিংসে তোমার Gemini API Key দাও।"
    !mic -> "কথা বলার জন্য মাইক্রোফোনের অনুমতি দরকার।"
    phase == Phase.IDLE -> "\"হেই Jimi\" বলো, বা নিচের বোতামে চাপ দাও।"
    phase == Phase.LISTENING -> "বলো — ইউটিউব খুলো, গান চালাও, ফোন দাও…"
    phase == Phase.SPEAKING -> "তুমি চাইলে মাঝপথে থামিয়ে দিতে পারো।"
    phase == Phase.THINKING -> "কাজ করছি…"
    phase == Phase.RECONNECTING -> "সংযোগ আবার জোড়া লাগাচ্ছি…"
    else -> ""
}
