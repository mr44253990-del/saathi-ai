package com.saathi.ai.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionState
import com.saathi.ai.ui.theme.jimi
import com.saathi.ai.ui.theme.LocalJimi

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
    onOpenMemory: () -> Unit = {},
    onOpenDraw: () -> Unit = {},
    onMinimize: () -> Unit
) {
    val p = jimi
    val theme = LocalJimi.current
    var textOpen by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

    val running = state.phase != Phase.IDLE &&
        state.phase != Phase.ENDED &&
        state.phase != Phase.ERROR

    SaathiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {

            // ------------------------------------------------------------ header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Jimi",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = p.textPrimary
                    )
                    Text(
                        text = when {
                            state.model.isBlank() -> theme.bangla + " থিম"
                            else -> state.model
                        },
                        fontSize = 9.5.sp,
                        color = p.textMuted,
                        letterSpacing = 0.3.sp
                    )
                }

                StatusChip(text = phaseLabel(state.phase), dotColor = phaseColor(state.phase))

                Spacer(Modifier.width(8.dp))

                CircleIcon(SaathiIcons.Brain, "মেমরি") { onOpenMemory() }
                Spacer(Modifier.width(6.dp))
                CircleIcon(SaathiIcons.Minimize, "মিনিমাইজ") { onMinimize() }
                Spacer(Modifier.width(6.dp))
                CircleIcon(SaathiIcons.Settings, "সেটিংস") { onOpenSettings() }
            }

            // ------------------------------------------------------------ orb stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val orbScale by animateFloatAsState(
                    targetValue = if (running) 1f else 0.94f,
                    animationSpec = tween(600),
                    label = "orbScale"
                )
                JimiOrb(
                    phase = state.phase,
                    level = state.level,
                    modifier = Modifier.size((306 * orbScale).dp)
                )

                if (state.cameraOn && cameraPreviewView != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(96.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.5.dp, p.secondary.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    ) {
                        AndroidView(factory = { cameraPreviewView }, modifier = Modifier.fillMaxSize())
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xCC000000))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("LIVE", color = p.secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (state.screenOn) {
                    Pill(
                        icon = SaathiIcons.ScreenShare,
                        text = "স্ক্রিন দেখা হচ্ছে",
                        color = p.accent,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 4.dp)
                    )
                }

                // what Jimi is doing right now
                if (running && state.lastTool.isNotBlank()) {
                    Pill(
                        icon = SaathiIcons.Bolt,
                        text = toolLabel(state.lastTool),
                        color = p.secondary,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                    )
                }

                if (!accessibilityOn) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x33FFB86B))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Accessibility বন্ধ — ট্যাপ/স্ক্রল কাজ করবে না",
                            color = Color(0xFFFFC078),
                            fontSize = 9.5.sp
                        )
                    }
                }
            }

            // ------------------------------------------------------------ transcript
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = when {
                        state.error != null -> state.error!!
                        state.modelText.isNotBlank() -> state.modelText
                        state.userText.isNotBlank() -> state.userText
                        else -> hintFor(state.phase, configured, hasMicPermission)
                    },
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    label = "caption"
                ) { shown ->
                    Text(
                        text = shown,
                        color = when {
                            state.error != null -> Color(0xFFFF8A9C)
                            state.userText.isNotBlank() && state.modelText.isBlank() -> p.secondary
                            else -> p.textPrimary.copy(alpha = 0.88f)
                        },
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }

            // ------------------------------------------------------------ quick chips
            if (!running) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickChip("ইউটিউবে গান চালাও") { onSendText("ইউটিউবে গান চালাও") }
                    QuickChip("মাকে ফোন দাও") { onSendText("মাকে ফোন দাও") }
                    QuickChip("আজকের আবহাওয়া") { onSendText("আজকের আবহাওয়া বলো") }
                    QuickChip("নিচে স্ক্রল করো") { onSendText("নিচে স্ক্রল করো") }
                    QuickChip("মনে রাখো আমি সাভারে থাকি") { onSendText("মনে রাখো আমি সাভারে থাকি") }
                    QuickChip("একটা ছবি আঁকো") { onOpenDraw() }
                }
            }

            // ------------------------------------------------------------ dock
            if (!running) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StartButton(
                        label = if (configured) "কথা বলো" else "API Key সেট করো",
                        enabled = true,
                        onClick = if (configured && hasMicPermission) onStart else onOpenSettings
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToggleChip("ফ্লোটিং বাবল", bubbleOn, onToggleBubble)
                        ToggleChip("\"হেই Jimi\"", hotwordOn, onToggleHotword)
                    }
                }
            } else {
                ControlDock(
                    state = state,
                    textOpen = textOpen,
                    text = text,
                    onText = { text = it },
                    onToggleText = { textOpen = !textOpen },
                    onSend = {
                        if (text.isNotBlank()) {
                            onSendText(text); text = ""; textOpen = false
                        }
                    },
                    onMic = onToggleMic,
                    onCamera = onToggleCamera,
                    onSwitchCamera = onSwitchCamera,
                    onScreen = onToggleScreen,
                    onBubble = onMinimize,
                    onEnd = onEnd
                )
            }
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun CircleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = jimi.textPrimary.copy(alpha = 0.85f), modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun Pill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text(label, color = jimi.textMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    val p = jimi
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) p.secondary.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
            .border(
                1.dp,
                if (on) p.secondary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (on) p.secondary else p.textMuted.copy(alpha = 0.6f))
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = if (on) p.secondary else p.textMuted,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StartButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val p = jimi
    val transition = rememberInfiniteTransition(label = "start")
    val glow by transition.animateFloat(
        0.72f, 1f,
        infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        p.primary.copy(alpha = 0.80f + 0.20f * glow),
                        p.secondary.copy(alpha = 0.74f + 0.22f * glow)
                    )
                )
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 34.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SaathiIcons.Mic, null, tint = Color.White, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ControlDock(
    state: SessionState,
    textOpen: Boolean,
    text: String,
    onText: (String) -> Unit,
    onToggleText: () -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onScreen: () -> Unit,
    onBubble: () -> Unit,
    onEnd: () -> Unit
) {
    val p = jimi
    Column(Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = textOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = onText,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = p.textPrimary, fontSize = 14.sp
                    ),
                    cursorBrush = Brush.verticalGradient(listOf(p.secondary, p.primary)),
                    maxLines = 3,
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text("লিখে বলো…", color = p.textMuted, fontSize = 13.sp)
                        }
                        inner()
                    }
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(p.primary, p.secondary)))
                        .clickable { onSend() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SaathiIcons.Send, "পাঠাও", tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundAction(
                icon = if (state.micOn) SaathiIcons.Mic else SaathiIcons.MicOff,
                label = if (state.micOn) "মাইক" else "মিউট",
                active = state.micOn,
                onClick = onMic
            )
            RoundAction(
                icon = if (state.cameraOn) SaathiIcons.Videocam else SaathiIcons.VideocamOff,
                label = "ক্যামেরা",
                active = state.cameraOn,
                onClick = onCamera
            )
            RoundAction(
                icon = SaathiIcons.ScreenShare,
                label = "স্ক্রিন",
                active = state.screenOn,
                onClick = onScreen
            )
            RoundAction(
                icon = SaathiIcons.Keyboard,
                label = "লিখো",
                active = textOpen,
                onClick = onToggleText
            )
            RoundAction(
                icon = SaathiIcons.Bubble,
                label = "ছোট করো",
                active = false,
                onClick = onBubble
            )
            RoundAction(
                icon = SaathiIcons.StopCircle,
                label = "শেষ",
                tint = Color(0xFFFF8A9C),
                onClick = onEnd
            )
        }

        if (state.cameraOn) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { onSwitchCamera() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text("ক্যামেরা বদলাও", color = p.textPrimary, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- text helpers

fun phaseLabel(phase: Phase): String = when (phase) {
    Phase.IDLE -> "প্রস্তুত"
    Phase.CONNECTING -> "সংযোগ…"
    Phase.LISTENING -> "শুনছি"
    Phase.THINKING -> "ভাবছি"
    Phase.SPEAKING -> "বলছি"
    Phase.RECONNECTING -> "রিকানেক্ট"
    Phase.ERROR -> "সমস্যা"
    Phase.ENDED -> "শেষ"
}

@Composable
fun phaseColor(phase: Phase): Color = when (phase) {
    Phase.IDLE -> jimi.textMuted
    Phase.CONNECTING, Phase.RECONNECTING -> Color(0xFFFFB86B)
    Phase.LISTENING -> jimi.secondary
    Phase.THINKING -> jimi.primary
    Phase.SPEAKING -> jimi.accent
    Phase.ERROR -> Color(0xFFFF6B81)
    Phase.ENDED -> jimi.textMuted
}

fun toolLabel(tool: String): String = when (tool) {
    "open_app" -> "অ্যাপ খুলছি"
    "open_url" -> "ওয়েবসাইট খুলছি"
    "search_youtube", "play_on_youtube", "open_youtube_and_search_play" -> "ইউটিউবে খুঁজছি"
    "read_screen" -> "স্ক্রিন পড়ছি"
    "screen_tap", "screen_tap_at", "long_press" -> "ট্যাপ করছি"
    "screen_scroll" -> "স্ক্রল করছি"
    "screen_type" -> "লিখছি"
    "screen_zoom" -> "জুম করছি"
    "call_number", "call_contact" -> "কল দিচ্ছি"
    "send_sms" -> "মেসেজ পাঠাচ্ছি"
    "read_recent_sms", "read_call_log", "read_contacts" -> "তথ্য দেখছি"
    "open_camera", "take_photo" -> "ক্যামেরা"
    "latest_photos" -> "ছবি খুঁজছি"
    "upi_pay", "upi_scan" -> "পেমেন্ট"
    "open_weather" -> "আবহাওয়া"
    "share_text" -> "শেয়ার করছি"
    "medicine_reminder" -> "রিমাইন্ডার"
    "remember", "recall" -> "মনে রাখছি"
    else -> "কাজ করছি"
}

private fun hintFor(phase: Phase, configured: Boolean, mic: Boolean): String = when {
    !configured -> "শুরু করতে সেটিংসে তোমার Gemini API Key দাও।"
    !mic -> "কথা বলার জন্য মাইক্রোফোনের অনুমতি দরকার।"
    phase == Phase.IDLE -> "«হেই Jimi» বলো, বা নিচের বোতামে চাপ দাও।"
    phase == Phase.LISTENING -> "বলো — ইউটিউবে গান, মাকে ফোন, স্ক্রল করো…"
    phase == Phase.SPEAKING -> "চাইলে মাঝপথে থামিয়ে দিতে পারো।"
    phase == Phase.THINKING -> "কাজ করছি…"
    phase == Phase.RECONNECTING -> "সংযোগ আবার জোড়া লাগাচ্ছি…"
    else -> ""
}
