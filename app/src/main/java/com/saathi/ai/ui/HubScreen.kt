package com.saathi.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.access.HealthInsights
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionHub
import com.saathi.ai.ui.theme.jimi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The hub — and the fix for "Jimi হাব খুলতে সময় লাগে".
 *
 * The old version counted installed packages, memories, reminders and chats **inside
 * the composition**, so every recomposition hit PackageManager and disk on the UI
 * thread. All of that now runs once on [Dispatchers.IO] behind [LaunchedEffect], the
 * screen paints immediately with placeholders, and the numbers drop in when they are
 * ready.
 *
 * It also carries the live work bar: whatever Jimi is doing right now (task, tool,
 * spoken line) shows at the top of the app, mirroring what the bubble shows outside.
 */
@Composable
fun HubScreen(
    accessibilityHealthy: Boolean,
    onOpenMemory: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenDraw: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenTasks: () -> Unit,
    onBack: () -> Unit,
    // v3 control centre (task engine, browser, audio, memory, health, automations).
    // Optional with a no-op default so existing callers keep compiling unchanged.
    onOpenControl: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by SessionHub.state.collectAsState()

    var installedApps by remember { mutableStateOf(-1) }
    var knownApps by remember { mutableStateOf(-1) }
    var memoryCount by remember { mutableStateOf(-1) }
    var reminders by remember { mutableStateOf(-1) }
    var chats by remember { mutableStateOf(-1) }
    var taskCount by remember { mutableStateOf(-1) }
    var taskProgress by remember { mutableStateOf("") }
    var health by remember { mutableStateOf<String?>(null) }
    var healthBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val stats = withContext(Dispatchers.IO) {
            val apps = runCatching { context.packageManager.getInstalledApplications(0).size }.getOrDefault(0)
            val known = runCatching { com.saathi.ai.access.AppKnowledge(context).installedApps().size }.getOrDefault(0)
            val mem = com.saathi.ai.data.MemoryStore(context)
            val board = com.saathi.ai.task.TaskBoard(context)
            val tasks = board.all()
            Stats(
                apps = apps,
                known = known,
                memory = mem.count(),
                reminders = mem.reminders().size,
                chats = com.saathi.ai.data.ChatStore(context).chats().size,
                tasks = tasks.size,
                progress = board.progressText()
            )
        }
        installedApps = stats.apps
        knownApps = stats.known
        memoryCount = stats.memory
        reminders = stats.reminders
        chats = stats.chats
        taskCount = stats.tasks
        taskProgress = stats.progress
        health = runCatching { HealthInsights(context).headline() }.getOrNull()
    }

    fun runHealthCheck() {
        if (healthBusy) return
        healthBusy = true
        health = "পরীক্ষা চলছে…"
        scope.launch {
            health = withContext(Dispatchers.IO) {
                runCatching { HealthInsights(context).report(withApiCheck = true) }
                    .getOrElse { "হেলথ চেক ব্যর্থ: ${it.message}" }
            }
            healthBusy = false
        }
    }

    SaathiBackground {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SaathiIcons.ArrowBack, "ফিরে", tint = jimi.textPrimary, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Jimi হাব", color = jimi.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (accessibilityHealthy) "সব সিস্টেম চালু" else "Accessibility বন্ধ — কিছু কাজ আটকাবে",
                            color = if (accessibilityHealthy) jimi.secondary else Color(0xFFFFB86B),
                            fontSize = 11.sp
                        )
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { runHealthCheck() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SaathiIcons.Pulse, "হেলথ চেক", tint = jimi.accent, modifier = Modifier.size(17.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ------------------------------------------------ live work bar
                NowBar(state, Modifier.fillMaxWidth())

                Spacer(Modifier.height(12.dp))

                // ---------------------------------------------------- hero orb
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.verticalGradient(listOf(jimi.bgMid, jimi.bgBottom)))
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(26.dp))
                ) {
                    JimiOrb(
                        phase = state.phase,
                        level = state.level,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(170.dp)
                            .padding(end = 4.dp)
                    )
                    Column(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 20.dp)
                    ) {
                        Text("সব এক জায়গায়", color = jimi.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "কথা বলো, বাকিটা\nJimi করে দেবে",
                            color = jimi.textMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ---------------------------------------------------- stats
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Stat("অ্যাপ", num(installedApps), "চেনে ${num(knownApps)}", Modifier.weight(1f))
                    Stat("মেমরি", num(memoryCount), "রিমাইন্ডার ${num(reminders)}", Modifier.weight(1f))
                    Stat("কাজ", num(taskCount), taskProgress.ifBlank { "চ্যাট ${num(chats)}" }, Modifier.weight(1f))
                }

                health?.let { h ->
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, jimi.accent.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
                            .padding(13.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(SaathiIcons.Pulse, null, tint = jimi.accent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("হেলথ", color = jimi.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            if (!healthBusy) {
                                Box(Modifier.clickable { health = null }.padding(3.dp)) {
                                    Icon(SaathiIcons.Close, null, tint = jimi.textMuted, modifier = Modifier.size(11.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(h, color = jimi.textMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("পেজ", color = jimi.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                // ---------------------------------------------------- nav tiles
                HubTile("কাজের তালিকা", "বড় কাজ ভেঙে ব্যাকগ্রাউন্ডে চলে, টিক পড়ে", SaathiIcons.Task, onOpenTasks)
                HubTile(
                    "কন্ট্রোল সেন্টার",
                    "টাস্ক ইঞ্জিন, ব্রাউজার, অডিও, মেমোরি, হেলথ ও নিজের অটোমেশন — এক জায়গায়",
                    SaathiIcons.Bolt, onOpenControl
                )
                HubTile("ফাইল", "খোঁজা, খোলা, শেয়ার, মুছে ফেলা — নিজের ম্যানেজারে", SaathiIcons.Folder, onOpenFiles)
                HubTile("গেম", "ফোনের আসল লুডু/দাবা/ক্যারম অ্যাপে খেলে", SaathiIcons.Games, onOpenGames)
                HubTile("মেমরি ও রিমাইন্ডার", "কী মনে আছে, কী করতে হবে", SaathiIcons.Brain, onOpenMemory)
                HubTile("আঁকা ও ডিজাইন", "নিজে হাতে আঁকে, ছবি বানায় ও এডিট করে", SaathiIcons.Draw, onOpenDraw)
                HubTile("চেহারা ও থিম", "১২টি থিম, সাথে কাস্টম রঙ", SaathiIcons.Palette, onOpenThemes)

                Spacer(Modifier.height(18.dp))
                Text("সক্ষমতা", color = jimi.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                CapabilityGrid()

                Spacer(Modifier.height(28.dp))
            }

            SideRail(
                sections = listOf("work", "orb", "stats", "pages", "skills"),
                active = if (health == null) 3 else 2,
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }
    }
}

private data class Stats(
    val apps: Int,
    val known: Int,
    val memory: Int,
    val reminders: Int,
    val chats: Int,
    val tasks: Int,
    val progress: String
)

private fun num(v: Int): String = if (v < 0) "—" else "$v"

@Composable
private fun Stat(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = jimi.secondary, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Text(label, color = jimi.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(sub, color = jimi.textMuted, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun HubTile(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.linearGradient(
                        listOf(jimi.primary.copy(alpha = 0.85f), jimi.secondary.copy(alpha = 0.85f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = jimi.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = jimi.textMuted, fontSize = 10.5.sp)
        }
        Icon(SaathiIcons.ArrowRight, null, tint = jimi.textMuted, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun CapabilityGrid() {
    val items = listOf(
        "রুট ও টার্মিনাল" to "su কমান্ড, Termux, শেল আউটপুট পড়ে সিদ্ধান্ত",
        "স্ক্রিনশট ভিশন" to "স্ক্রিন শেয়ার ছাড়াই ছবি তুলে দেখে, ঘর চিনে ট্যাপ",
        "ব্যাকগ্রাউন্ড টাস্ক" to "টু-ডু লিস্ট, টাইমার ধরে খাটা, টিক পড়া",
        "ছবি আঁকা/এডিট" to "নিজে এঁকে ও API দিয়ে বানিয়ে, ঘুরিয়ে-কেটে লেখা বসায়",
        "কল ও SMS" to "নাম ধরে কল, মেসেজ পড়া/পাঠানো",
        "স্ক্রিন অটোমেশন" to "ট্যাপ, ড্র্যাগ, মাল্টি-টাচ, ঘোরানো, স্লাইডার",
        "অ্যাপ খোঁজা" to "লুকানো/নাম বদলানো অ্যাপ, উইজেট, ডিপ লিংক",
        "ছবি/ভিডিও/অডিও" to "দেখে বোঝে, ফ্রেম বের করে, ট্রান্সক্রাইব করে",
        "ফাইল" to "কপি/মুভ/শেয়ার, আর ট্যাপ করলেই খোলে",
        "পেমেন্ট" to "স্ক্রিন খুলে থেমে যায় — চূড়ান্ত ট্যাপ তোমার",
        "স্বাস্থ্য ও পরামর্শ" to "ব্যাটারি/স্টোরেজ/র‍্যাম দেখে পরামর্শ, নিজে সারায়",
        "গেম" to "ফোনের আসল লুডু, দাবা, ক্যারম বোর্ড"
    )
    items.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            row.forEach { (title, sub) ->
                Column(
                    Modifier
                        .weight(1f)
                        .padding(bottom = 9.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(12.dp)
                ) {
                    Text(title, color = jimi.secondary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(sub, color = jimi.textMuted, fontSize = 9.5.sp, lineHeight = 14.sp)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
