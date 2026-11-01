package com.saathi.ai.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.task.TaskEngine
import com.saathi.ai.ui.theme.jimi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Control centre for everything v3 added: the autonomous task engine, the browser
 * workspace, audio generation and playback, memory, health and user automations.
 *
 * Every button here calls the same engine code the model calls through tools — there
 * is no mock state, so what the screen shows is what the background really did. Long
 * work runs on Dispatchers.IO and the result text is shown verbatim, including
 * "ব্যর্থ:" lines, because a dashboard that hides failures is worse than none.
 */
@Composable
fun ControlCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tabs = listOf("টাস্ক", "ব্রাউজার", "অডিও", "মেমোরি", "হেলথ", "অটোমেশন")
    var tab by remember { mutableStateOf(0) }
    var banner by remember { mutableStateOf<String?>(null) }
    val runs by TaskEngine.runs.collectAsState()

    fun act(block: () -> String) {
        scope.launch {
            banner = withContext(Dispatchers.IO) {
                runCatching { block() }.getOrElse { "ব্যর্থ: ${it.message}" }
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { TaskEngine.init(context) }
    }

    SaathiBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ------------------------------------------------------------- header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        SaathiIcons.ArrowBack, "ফিরে",
                        tint = jimi.textPrimary, modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "কন্ট্রোল সেন্টার",
                        color = jimi.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        TaskEngine.headline(),
                        color = jimi.textMuted, fontSize = 10.5.sp
                    )
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable {
                            act { com.saathi.ai.core.KillSwitch.stopAll(context) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        SaathiIcons.StopCircle, "সব থামাও (জরুরি)",
                        tint = jimi.accent, modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------------------------------------------------------------- tabs
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { i, label ->
                    val on = i == tab
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (on) Brush.horizontalGradient(listOf(jimi.primary, jimi.secondary))
                                else Brush.horizontalGradient(
                                    listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.05f))
                                )
                            )
                            .border(
                                1.dp,
                                if (on) Color.Transparent else Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(50)
                            )
                            .clickable { tab = i; banner = null }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            label,
                            color = if (on) Color.White else jimi.textMuted,
                            fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            banner?.let { text ->
                GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp, contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            SaathiIcons.Pulse, null,
                            tint = if (text.startsWith("ব্যর্থ") || text.startsWith("আটকে")) jimi.accent
                            else jimi.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text.take(1400),
                            color = jimi.textPrimary, fontSize = 11.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when (tab) {
                    0 -> TaskTab(context, scope, runs, ::act)
                    1 -> BrowserTab(context, scope, ::act)
                    2 -> AudioTab(context, scope, ::act)
                    3 -> MemoryTab(context, scope, ::act)
                    4 -> HealthTab(context, scope, ::act)
                    else -> AutoTab(context, scope, ::act)
                }
            }
        }
    }
}

// --------------------------------------------------------------------- 1. tasks

@Composable
private fun TaskTab(
    context: android.content.Context,
    scope: CoroutineScope,
    runs: List<TaskEngine.Run>,
    act: (() -> String) -> Unit
) {
    var goal by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(3) }

    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp, contentPadding = 16.dp) {
        SectionTitle("নতুন কাজ ইঞ্জিনে দাও", SaathiIcons.Task)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = goal,
            onValueChange = { goal = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            placeholder = {
                Text(
                    "উদাহরণ: ইউটিউবে গান খুঁজে প্লেলিস্টে যোগ করো",
                    color = jimi.textMuted, fontSize = 12.sp
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = jimi.textPrimary,
                unfocusedTextColor = jimi.textPrimary,
                focusedBorderColor = jimi.secondary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                cursorColor = jimi.secondary
            ),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("অগ্রাধিকার", color = jimi.textMuted, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            (1..5).forEach { p ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (p == priority) Brush.horizontalGradient(listOf(jimi.primary, jimi.secondary))
                            else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f)))
                        )
                        .clickable { priority = p },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$p",
                        color = if (p == priority) Color.White else jimi.textMuted,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        GradientButton(
            text = "কাজ শুরু করো",
            icon = SaathiIcons.Bolt,
            enabled = goal.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            val g = goal.trim()
            goal = ""
            act { TaskEngine.start(context, g, priority) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton(
                text = "সব বাতিল",
                icon = SaathiIcons.StopCircle,
                modifier = Modifier.weight(1f)
            ) { act { TaskEngine.cancelAll() } }
            GradientButton(
                text = "শেষগুলো সরাও",
                modifier = Modifier.weight(1f)
            ) { act { TaskEngine.clearFinished() } }
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionTitle("চলমান ও সংরক্ষিত কাজ (${runs.size})", SaathiIcons.Timer)
    Spacer(Modifier.height(10.dp))

    if (runs.isEmpty()) {
        Text(
            "ইঞ্জিন খালি — উপরে কাজ লিখে শুরু করো। একসাথে সর্বোচ্চ ${TaskEngine.MAX_CONCURRENT}টি কাজ চলে।",
            color = jimi.textMuted, fontSize = 12.sp
        )
    }
    runs.take(20).forEach { run ->
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            corner = 18.dp, contentPadding = 14.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                            when (run.state) {
                                TaskEngine.RunState.RUNNING -> jimi.secondary
                                TaskEngine.RunState.QUEUED -> jimi.primary
                                TaskEngine.RunState.PAUSED -> Color(0xFFFBBF24)
                                TaskEngine.RunState.DONE -> Color(0xFF34D399)
                                TaskEngine.RunState.FAILED, TaskEngine.RunState.BLOCKED -> jimi.accent
                                else -> jimi.textMuted
                            }
                        )
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    run.goal.take(70),
                    color = jimi.textPrimary, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                )
                Text(
                    "${run.progress()}%",
                    color = jimi.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${run.stateBangla()} · এজেন্ট ${run.agentId} · অগ্রাধিকার ${run.priority} · " +
                    "ধাপ ${run.stepsDone}/${run.stepsTotal} · ${run.id}",
                color = jimi.textMuted, fontSize = 10.5.sp
            )
            if (run.lastError.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "সমস্যা: ${run.lastError.take(140)}",
                    color = jimi.accent, fontSize = 10.5.sp
                )
            }
            if (run.progress() > 0) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(run.progress() / 100f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(jimi.primary, jimi.secondary)))
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SmallButton("পজ") { act { TaskEngine.pause(run.id) } }
                SmallButton("চালু") { act { TaskEngine.resume(context, run.id) } }
                SmallButton("বাতিল") { act { TaskEngine.cancel(run.id) } }
                SmallButton("রিপোর্ট") { act { TaskEngine.report(run.id) } }
            }
        }
    }
}

// ------------------------------------------------------------------- 2. browser

@Composable
private fun BrowserTab(
    context: android.content.Context,
    scope: CoroutineScope,
    act: (() -> String) -> Unit
) {
    var target by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("ব্রাউজার খালি") }

    LaunchedEffect(Unit) {
        status = withContext(Dispatchers.IO) { com.saathi.ai.web.WebBrowser.status(context) }
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp, contentPadding = 16.dp) {
        SectionTitle("ব্রাউজার ওয়ার্কস্পেস", SaathiIcons.Globe)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = target,
            onValueChange = { target = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text("URL বা সার্চ লেখো…", color = jimi.textMuted, fontSize = 12.sp)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = jimi.textPrimary,
                unfocusedTextColor = jimi.textPrimary,
                focusedBorderColor = jimi.secondary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                cursorColor = jimi.secondary
            ),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton(
                text = "খুলো",
                enabled = target.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                val t = target.trim()
                act {
                    val out = if (t.startsWith("http")) com.saathi.ai.web.WebBrowser.open(context, t)
                    else com.saathi.ai.web.WebBrowser.search(context, t)
                    status = com.saathi.ai.web.WebBrowser.status(context)
                    out
                }
            }
            GradientButton(
                text = "ট্যাব",
                modifier = Modifier.weight(1f)
            ) { act { com.saathi.ai.web.WebBrowser.tabs() } }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallButton("টেক্সট") { act { com.saathi.ai.web.WebBrowser.text(context) } }
            SmallButton("লিংক") { act { com.saathi.ai.web.WebBrowser.links(context) } }
            SmallButton("ফিল্ড") { act { com.saathi.ai.web.WebBrowser.fields(context) } }
            SmallButton("শট") { act { com.saathi.ai.web.WebBrowser.shot(context, false) } }
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallButton("পেছনে") { act { com.saathi.ai.web.WebBrowser.back(context) } }
            SmallButton("সামনে") { act { com.saathi.ai.web.WebBrowser.forward(context) } }
            SmallButton("রিলোড") { act { com.saathi.ai.web.WebBrowser.reload(context) } }
            SmallButton("বন্ধ") {
                act {
                    val out = com.saathi.ai.web.WebBrowser.closeAll(context)
                    status = com.saathi.ai.web.WebBrowser.status(context)
                    out
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallButton("ডাউনলোড তালিকা") { act { com.saathi.ai.web.WebBrowser.downloads(context) } }
            SmallButton("অবস্থা") {
                act {
                    status = com.saathi.ai.web.WebBrowser.status(context)
                    status
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp, contentPadding = 14.dp) {
        SectionTitle("বর্তমান অবস্থা", SaathiIcons.Pulse)
        Spacer(Modifier.height(8.dp))
        Text(status.take(900), color = jimi.textPrimary, fontSize = 11.5.sp)
    }

    Spacer(Modifier.height(14.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp, contentPadding = 14.dp) {
        SectionTitle("গবেষণা ইঞ্জিন", SaathiIcons.Brain)
        Spacer(Modifier.height(8.dp))
        Text(
            "একাধিক সোর্স থেকে খোঁজা, মিলিয়ে দেখা ও রিপোর্ট — ক্যাশ ২৪ ঘণ্টা থাকে।",
            color = jimi.textMuted, fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton(
                text = "গবেষণা চালাও",
                enabled = target.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                val q = target.trim()
                act { com.saathi.ai.web.ResearchEngine(context).research(q) }
            }
            GradientButton(
                text = "ক্যাশ",
                modifier = Modifier.weight(1f)
            ) { act { com.saathi.ai.web.ResearchEngine(context).cacheStats() } }
        }
    }
}

// --------------------------------------------------------------------- 3. audio

@Composable
private fun AudioTab(
    context: android.content.Context,
    scope: CoroutineScope,
    act: (() -> String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(com.saathi.ai.media.AudioPlaybackService.status()) }

    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp, contentPadding = 16.dp) {
        SectionTitle("কণ্ঠ তৈরি (Gemini TTS)", SaathiIcons.Mic)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("যা বলাবে তা লেখো…", color = jimi.textMuted, fontSize = 12.sp)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = jimi.textPrimary,
                unfocusedTextColor = jimi.textPrimary,
                focusedBorderColor = jimi.secondary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                cursorColor = jimi.secondary
            ),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(12.dp))
        GradientButton(
            text = "তৈরি করে ব্যাকগ্রাউন্ডে শোনাও",
            icon = SaathiIcons.Bolt,
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            val t = text.trim()
            act {
                val made = com.saathi.ai.media.AudioGen(context).speak(t)
                val path = Regex("/[^\\s:]+\\.wav").find(made)?.value
                if (path != null) {
                    val out = made + "\n" +
                        com.saathi.ai.media.AudioPlaybackService.play(
                            context, path, java.io.File(path).name
                        )
                    playing = com.saathi.ai.media.AudioPlaybackService.status()
                    out
                } else {
                    made + "\n(ব্যাকগ্রাউন্ডে চালানো যায়নি — WAV পাথ পাওয়া যায়নি)"
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("অফলাইন পরিবেশ-শব্দ", color = jimi.textMuted, fontSize = 11.sp)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("rain" to "বৃষ্টি", "waves" to "ঢেউ", "focus" to "ফোকাস", "night" to "রাত")
                .forEach { (kind, label) ->
                    SmallButton(label) {
                        act {
                            val made = com.saathi.ai.media.AudioGen(context).ambient(kind, 180)
                            val path = Regex("/[^\\s:]+\\.wav").find(made)?.value
                            if (path != null) {
                                val out = made + "\n" +
                                    com.saathi.ai.media.AudioPlaybackService.play(
                                        context, path, java.io.File(path).name, true
                                    )
                                playing = com.saathi.ai.media.AudioPlaybackService.status()
                                out
                            } else made
                        }
                    }
                }
        }
    }

    Spacer(Modifier.height(14.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp, contentPadding = 14.dp) {
        SectionTitle("প্লেব্যাক", SaathiIcons.Bolt)
        Spacer(Modifier.height(8.dp))
        Text(playing, color = jimi.textPrimary, fontSize = 11.5.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallButton("পজ") {
                act {
                    val out = com.saathi.ai.media.AudioPlaybackService.pause(context)
                    playing = com.saathi.ai.media.AudioPlaybackService.status()
                    out
                }
            }
            SmallButton("চালু") {
                act {
                    val out = com.saathi.ai.media.AudioPlaybackService.resume(context)
                    playing = com.saathi.ai.media.AudioPlaybackService.status()
                    out
                }
            }
            SmallButton("থামাও") {
                act {
                    val out = com.saathi.ai.media.AudioPlaybackService.stop(context)
                    playing = com.saathi.ai.media.AudioPlaybackService.status()
                    out
                }
            }
            SmallButton("লাইব্রেরি") { act { com.saathi.ai.media.AudioGen(context).library() } }
        }
    }

    Spacer(Modifier.height(14.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp, contentPadding = 14.dp) {
        SectionTitle("কণ্ঠ তালিকা", SaathiIcons.KeyboardVoice)
        Spacer(Modifier.height(10.dp))
        GradientButton(text = "ভয়েস দেখাও", modifier = Modifier.fillMaxWidth()) {
            act { com.saathi.ai.media.AudioGen(context).voices() }
        }
    }
}

// -------------------------------------------------------------------- 4. memory

@Composable
private fun MemoryTab(
    context: android.content.Context,
    scope: CoroutineScope,
    act: (() -> String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var overview by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        overview = withContext(Dispatchers.IO) { com.saathi.ai.data.MemorySearch(context).overview() }
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp, contentPadding = 16.dp) {
        SectionTitle("মেমোরি ড্যাশবোর্ড", SaathiIcons.Brain)
        Spacer(Modifier.height(8.dp))
        Text(
            overview.ifBlank { "পড়া হচ্ছে…" },
            color = jimi.textPrimary, fontSize = 11.5.sp
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text("মেমোরিতে খোঁজো…", color = jimi.textMuted, fontSize = 12.sp)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = jimi.textPrimary,
                unfocusedTextColor = jimi.textPrimary,
                focusedBorderColor = jimi.secondary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                cursorColor = jimi.secondary
            ),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(12.dp))
        GradientButton(
            text = "খুঁজো",
            icon = SaathiIcons.OpenInNew,
            enabled = query.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            val q = query.trim()
            act { com.saathi.ai.data.MemorySearch(context).report(q) }
        }
    }

    Spacer(Modifier.height(14.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp, contentPadding = 14.dp) {
        SectionTitle("প্রজেক্ট ওয়ার্কস্পেস", SaathiIcons.Folder)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallButton("তালিকা") { act { com.saathi.ai.data.ProjectWorkspace(context).list() } }
            SmallButton("বর্তমান") { act { com.saathi.ai.data.ProjectWorkspace(context).detail() } }
        }
    }

    Spacer(Modifier.height(14.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp, contentPadding = 14.dp) {
        SectionTitle("ইতিহাস", SaathiIcons.Timer)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallButton("সাম্প্রতিক টাস্ক") { act { com.saathi.ai.data.TaskHistory(context).report(8) } }
            SmallButton("পরিসংখ্যান") { act { com.saathi.ai.data.TaskHistory(context).stats() } }
        }
    }
}

// --------------------------------------------------------------------- 5. health

@Composable
private fun HealthTab(
    context: android.content.Context,
    scope: CoroutineScope,
    act: (() -> String) -> Unit
) {
    var report by remember { mutableStateOf("") }
    var perms by remember { mutableStateOf("") }
    var shiz by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            report = runCatching { com.saathi.ai.access.HealthInsights(context).headline() }
                .getOrElse { "পড়া যায়নি: ${it.message}" }
            perms = runCatching { com.saathi.ai.access.PermissionCenter.score(context).toString() + "%" }
                .getOrElse { "?" }
            shiz = runCatching {
                    com.saathi.ai.access.ShizukuBridge.stateName(
                        com.saathi.ai.access.ShizukuBridge.state(context)
                    )
                }
                .getOrElse { "অজানা" }
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp, contentPadding = 16.dp) {
        SectionTitle("ফোন ও সিস্টেম স্বাস্থ্য", SaathiIcons.Heart)
        Spacer(Modifier.height(8.dp))
        Text(report, color = jimi.textPrimary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip("অনুমতি স্কোর $perms", jimi.secondary)
            Spacer(Modifier.width(8.dp))
            StatusChip("Shizuku: $shiz", jimi.primary)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallButton("পুরো রিপোর্ট") { act { com.saathi.ai.access.HealthInsights(context).report(false) } }
            SmallButton("নিজে ঠিক করো") { act { com.saathi.ai.access.HealthInsights(context).selfFix(true) } }
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallButton("অনুমতি ড্যাশবোর্ড") {
                act { com.saathi.ai.access.PermissionCenter.dashboard(context) }
            }
            SmallButton("Shizuku অবস্থা") { act { com.saathi.ai.access.ShizukuBridge.status(context) } }
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallButton("এরর লগ") { act { com.saathi.ai.core.ErrorCenter.report(18) } }
            SmallButton("মডেল রাউটার") { act { com.saathi.ai.live.ModelRouter.status(context) } }
        }
    }

    Spacer(Modifier.height(14.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp, contentPadding = 14.dp) {
        SectionTitle("দৈনন্দিন সহকারী", SaathiIcons.Pulse)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallButton("আজকের বিবরণ") { act { com.saathi.ai.daily.DailyAssistant(context).report(false) } }
            SmallButton("অ্যাক্টিভিটি") { act { com.saathi.ai.daily.DailyAssistant(context).activityBlock() } }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Jimi রোগ নির্ণয় করে না — শারীরিক সমস্যায় ডাক্তারের পরামর্শ নাও।",
            color = jimi.textMuted, fontSize = 10.5.sp
        )
    }
}

// ---------------------------------------------------------------- 6. automation

@Composable
private fun AutoTab(
    context: android.content.Context,
    scope: CoroutineScope,
    act: (() -> String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(com.saathi.ai.auto.MacroRecorder.status()) }

    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 20.dp, contentPadding = 16.dp) {
        SectionTitle("নিজের অটোমেশন বানাও", SaathiIcons.Bolt)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("টেমপ্লেটের নাম…", color = jimi.textMuted, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = jimi.textPrimary,
                unfocusedTextColor = jimi.textPrimary,
                focusedBorderColor = jimi.secondary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                cursorColor = jimi.secondary
            ),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = steps,
            onValueChange = { steps = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("ধাপগুলো ' | ' দিয়ে আলাদা করো", color = jimi.textMuted, fontSize = 12.sp)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = jimi.textPrimary,
                unfocusedTextColor = jimi.textPrimary,
                focusedBorderColor = jimi.secondary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                cursorColor = jimi.secondary
            ),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(12.dp))
        GradientButton(
            text = "টেমপ্লেট সংরক্ষণ করো",
            enabled = name.isNotBlank() && steps.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            val n = name.trim()
            val s = steps.split("|").map { it.trim() }.filter { it.isNotBlank() }
            name = ""; steps = ""
            act { com.saathi.ai.auto.TemplateStore(context).save(n, "", s) }
        }
    }

    Spacer(Modifier.height(14.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp, contentPadding = 14.dp) {
        SectionTitle("ম্যাক্রো রেকর্ডিং", SaathiIcons.ScreenShare)
        Spacer(Modifier.height(8.dp))
        Text(recording, color = jimi.textMuted, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallButton("রেকর্ড শুরু") {
                act {
                    val out = com.saathi.ai.auto.MacroRecorder.start()
                    recording = com.saathi.ai.auto.MacroRecorder.status()
                    out
                }
            }
            SmallButton("থামাও") {
                act {
                    val out = com.saathi.ai.auto.MacroRecorder.stop()
                    recording = com.saathi.ai.auto.MacroRecorder.status()
                    out
                }
            }
            SmallButton("ধাপ দেখো") { act { com.saathi.ai.auto.MacroRecorder.preview() } }
        }
        Spacer(Modifier.height(10.dp))
        GradientButton(
            text = "রেকর্ডিং টেমপ্লেট হিসেবে রাখো",
            modifier = Modifier.fillMaxWidth()
        ) {
            act {
                val out = com.saathi.ai.auto.MacroRecorder.saveAs(
                    context,
                    name.ifBlank { "রেকর্ডিং" }
                )
                recording = com.saathi.ai.auto.MacroRecorder.status()
                out
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    GlassCard(modifier = Modifier.fillMaxWidth(), corner = 18.dp, contentPadding = 14.dp) {
        SectionTitle("সংরক্ষিত টেমপ্লেট", SaathiIcons.Folder)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallButton("তালিকা") { act { com.saathi.ai.auto.TemplateStore(context).list() } }
            SmallButton("এক্সপোর্ট") { act { com.saathi.ai.auto.TemplateStore(context).export() } }
        }
    }
}

/** Small glass pill button used across the control centre tabs. */
@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = jimi.textPrimary, fontSize = 11.sp)
    }
}
