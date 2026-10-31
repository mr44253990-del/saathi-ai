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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.task.TaskBoard
import com.saathi.ai.task.TaskRunnerService
import com.saathi.ai.ui.theme.jimi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The to-do board the user asked for: "বড় কাজ হলে টু-ডু লিস্ট বানিয়ে ব্যাকগ্রাউন্ডে
 * কাজ করবে, শেষ হলে টিক দেবে".
 *
 * Everything here is the same state the background worker mutates, so ticks appear by
 * themselves while the app is closed. The timer row is the "কিছু সময় ধরে কাজ করো"
 * mode — Jimi keeps grinding on the goal for that long instead of stopping after one
 * attempt.
 */
@Composable
fun TaskScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val board = remember { TaskBoard(context) }
    val tasks by TaskBoard.live.collectAsState()

    var newTask by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(10) }
    var banner by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(TaskRunnerService.isRunning) }
    var progress by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { board.all() }
        progress = board.progressText()
    }
    LaunchedEffect(tasks) {
        progress = board.progressText()
        running = TaskRunnerService.isRunning
    }

    fun act(block: () -> String) {
        scope.launch {
            banner = withContext(Dispatchers.IO) { runCatching { block() }.getOrElse { "ব্যর্থ: ${it.message}" } }
            running = TaskRunnerService.isRunning
        }
    }

    val percent = if (tasks.isEmpty()) 0 else tasks.count { it.status == TaskBoard.Status.DONE } * 100 / tasks.size

    SaathiBackground {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // ------------------------------------------------------ header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        Text("কাজের তালিকা", color = jimi.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (running) "ব্যাকগ্রাউন্ডে চলছে — টিক পড়ছে"
                            else "${tasks.size}টি কাজ · $percent% শেষ",
                            color = jimi.textMuted, fontSize = 10.5.sp
                        )
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { act { board.clearFinished() } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SaathiIcons.Trash, "শেষ হওয়াগুলো সরাও", tint = jimi.secondary, modifier = Modifier.size(15.dp))
                    }
                }

                Column(Modifier.padding(horizontal = 18.dp)) {
                    // -------------------------------------------------- progress
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(SaathiIcons.Pulse, null, tint = jimi.accent, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                progress.ifBlank { "এখনো কোনো কাজ নেই" },
                                color = jimi.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text("$percent%", color = jimi.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(9.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.09f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(percent / 100f)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(listOf(jimi.primary, jimi.secondary)))
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // -------------------------------------------------- add a task
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newTask,
                            onValueChange = { newTask = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("নতুন কাজ লেখো…", color = jimi.textMuted, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = jimi.textPrimary,
                                unfocusedTextColor = jimi.textPrimary,
                                focusedBorderColor = jimi.secondary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                cursorColor = jimi.secondary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(jimi.primary, jimi.secondary)))
                                .clickable {
                                    if (newTask.isNotBlank()) {
                                        act { board.add(newTask.trim()).let { "যোগ করেছি: ${it.title}" } }
                                        newTask = ""
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(SaathiIcons.Add, "যোগ করো", tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // -------------------------------------------------- timer work
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(jimi.primary.copy(alpha = 0.10f))
                            .border(1.dp, jimi.secondary.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        SectionTitle("ধরে রাখা টাইমার — যতক্ষণ বলবে ততক্ষণ খাটবে", SaathiIcons.Timer)
                        Spacer(Modifier.height(9.dp))
                        OutlinedTextField(
                            value = goal,
                            onValueChange = { goal = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("যেমন: ছবিগুলো ব্যাকআপ নাও, তারপর রিচার্জ করো",
                                    color = jimi.textMuted, fontSize = 12.sp)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = jimi.textPrimary,
                                unfocusedTextColor = jimi.textPrimary,
                                focusedBorderColor = jimi.secondary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                cursorColor = jimi.secondary
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(5, 10, 20, 30, 60).forEach { m ->
                                val on = minutes == m
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (on) jimi.secondary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f))
                                        .border(
                                            1.dp,
                                            if (on) jimi.secondary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.10f),
                                            RoundedCornerShape(50)
                                        )
                                        .clickable { minutes = m }
                                        .padding(horizontal = 13.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        "$m মিনিট",
                                        color = if (on) jimi.textPrimary else jimi.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(11.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            GradientButton(
                                text = if (running) "চলছে…" else "ব্যাকগ্রাউন্ডে শুরু করো",
                                icon = SaathiIcons.Play,
                                enabled = !running,
                                modifier = Modifier.weight(1f)
                            ) {
                                val g = goal.trim()
                                act {
                                    if (g.isNotBlank()) {
                                        TaskRunnerService.workFor(context, g, minutes * 60)
                                        "চালু করেছি — $minutes মিনিট ধরে «$g» নিয়ে কাজ করব। নোটিফিকেশনে টিক পড়বে।"
                                    } else {
                                        TaskRunnerService.start(context, null)
                                        "তালিকার বাকি কাজগুলো ব্যাকগ্রাউন্ডে চালাতে শুরু করেছি।"
                                    }
                                }
                            }
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                    .clickable { act { TaskRunnerService.stop(context); "থামিয়েছি" } },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(SaathiIcons.StopCircle, "থামাও", tint = Color(0xFFFF8A9C), modifier = Modifier.size(19.dp))
                            }
                        }
                    }

                    banner?.let { msg ->
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(jimi.secondary.copy(alpha = 0.12f))
                                .border(1.dp, jimi.secondary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(msg, color = jimi.textPrimary.copy(alpha = 0.92f), fontSize = 11.5.sp,
                                modifier = Modifier.weight(1f))
                            Box(Modifier.clickable { banner = null }.padding(4.dp)) {
                                Icon(SaathiIcons.Close, null, tint = jimi.textMuted, modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }

                // ------------------------------------------------------ list
                if (tasks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 34.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(SaathiIcons.Task, null, tint = jimi.textMuted, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.height(9.dp))
                            Text("তালিকা খালি — কাজ লেখো, অথবা ভয়েসে বলো «এটা টু-ডু লিস্টে রাখো»",
                                color = jimi.textMuted, fontSize = 11.5.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(tasks, key = { it.id }) { t: TaskBoard.Task ->
                            val tone = toneOf(t.status)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(1.dp, tone.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(tone.copy(alpha = 0.20f))
                                            .border(1.dp, tone.copy(alpha = 0.45f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(iconOf(t.status), null, tint = tone, modifier = Modifier.size(13.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            t.title,
                                            color = if (t.status == TaskBoard.Status.DONE)
                                                jimi.textMuted else jimi.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (t.detail.isNotBlank()) {
                                            Text(t.detail, color = jimi.textMuted, fontSize = 10.5.sp)
                                        }
                                    }
                                    Text(
                                        labelOf(t.status),
                                        color = tone, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (t.note.isNotBlank() || t.attempts > 0) {
                                    Spacer(Modifier.height(7.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .width(3.dp)
                                                .height(13.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(tone.copy(alpha = 0.6f))
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Text(
                                            buildString {
                                                if (t.note.isNotBlank()) append(t.note)
                                                if (t.attempts > 0) {
                                                    if (isNotEmpty()) append(" · ")
                                                    append("চেষ্টা ${t.attempts} বার")
                                                }
                                                append(" · ").append(stamp(t.updatedAt))
                                            },
                                            color = jimi.textMuted, fontSize = 10.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                if (t.status == TaskBoard.Status.PENDING || t.status == TaskBoard.Status.FAILED ||
                                    t.status == TaskBoard.Status.BLOCKED
                                ) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Pill(SaathiIcons.Play, "এখনই চালাও") {
                                            act {
                                                TaskRunnerService.start(context, t.id)
                                                "«${t.title}» ব্যাকগ্রাউন্ডে চালু করেছি"
                                            }
                                        }
                                        Pill(SaathiIcons.Close, "মুছে ফেলো") { act { board.remove(t.id) } }
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(Modifier.height(20.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Pill(SaathiIcons.Trash, "পুরো তালিকা মুছো") { act { board.clearAll() } }
                            }
                            Spacer(Modifier.height(26.dp))
                        }
                    }
                }
            }

            SideRail(
                sections = listOf("progress", "add", "timer", "list"),
                active = if (tasks.isEmpty()) 1 else 3,
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }
    }
}

private fun stamp(ms: Long): String =
    runCatching { SimpleDateFormat("d MMM, hh:mm a", Locale("bn", "BD")).format(Date(ms)) }.getOrDefault("")

private fun toneOf(s: TaskBoard.Status): Color = when (s) {
    TaskBoard.Status.DONE -> Color(0xFF34D399)
    TaskBoard.Status.RUNNING -> Color(0xFF35D6C8)
    TaskBoard.Status.FAILED -> Color(0xFFFF8A9C)
    TaskBoard.Status.BLOCKED -> Color(0xFFFBBF24)
    TaskBoard.Status.PENDING -> Color(0xFF94A3B8)
}

private fun iconOf(s: TaskBoard.Status): ImageVector = when (s) {
    TaskBoard.Status.DONE -> SaathiIcons.Check
    TaskBoard.Status.RUNNING -> SaathiIcons.Play
    TaskBoard.Status.FAILED -> SaathiIcons.Close
    TaskBoard.Status.BLOCKED -> SaathiIcons.Warning
    TaskBoard.Status.PENDING -> SaathiIcons.Task
}

private fun labelOf(s: TaskBoard.Status): String = when (s) {
    TaskBoard.Status.DONE -> "শেষ"
    TaskBoard.Status.RUNNING -> "চলছে"
    TaskBoard.Status.FAILED -> "ব্যর্থ"
    TaskBoard.Status.BLOCKED -> "আটকে"
    TaskBoard.Status.PENDING -> "বাকি"
}

@Composable
private fun Pill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = jimi.secondary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = jimi.textPrimary.copy(alpha = 0.9f), fontSize = 11.sp)
    }
}
