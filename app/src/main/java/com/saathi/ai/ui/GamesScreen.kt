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
import com.saathi.ai.access.GameApps
import com.saathi.ai.create.GameEngine
import com.saathi.ai.ui.theme.jimi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Games — **on the phone's own apps first**.
 *
 * The instruction was blunt: Ludo and chess must be the real games installed on the
 * device, not a board drawn inside Jimi. So the top of this screen drives those apps:
 * open the installed game, throw the dice, move a piece by dragging real pixels on the
 * real board, and read the screen back to check the move landed.
 *
 * The in-app boards are still here, but demoted to "অনুশীলন" — useful when no game is
 * installed, and never presented as the real thing.
 */
@Composable
fun GamesScreen(onBack: () -> Unit, onPlayCarrom: () -> Unit, onStartGame: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val games = remember { GameApps(context) }

    var status by remember { mutableStateOf("ফোনে কী কী গেম আছে দেখা হচ্ছে…") }
    var report by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var ludoBoard by remember { mutableStateOf("") }
    var chessBoard by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        status = withContext(Dispatchers.IO) { games.status() }
    }

    fun run(label: String, block: () -> String) {
        if (busy) return
        busy = true
        report = "$label চলছে…"
        scope.launch {
            val out = withContext(Dispatchers.IO) { runCatching { block() }.getOrElse { "ব্যর্থ: ${it.message}" } }
            report = out
            busy = false
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 10.dp),
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
                        Text("গেম", color = jimi.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("ফোনের আসল অ্যাপে খেলে — মুখে মুখে না", color = jimi.textMuted, fontSize = 10.5.sp)
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { scope.launch { status = withContext(Dispatchers.IO) { games.status() } } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SaathiIcons.Refresh, "আবার দেখো", tint = jimi.secondary, modifier = Modifier.size(16.dp))
                    }
                }

                StatusBanner(status)

                Spacer(Modifier.height(10.dp))

                SectionHead("ফোনের গেম — Jimi চালায়", SaathiIcons.Board)

                GameCard(
                    icon = SaathiIcons.Dice,
                    title = "লুডু — ইনস্টল করা অ্যাপে",
                    body = "লুডু অ্যাপ খুলে ছক্কায় ট্যাপ করে, তারপর স্ক্রিনশট দেখে গুটি বেছে নেয়। " +
                        "গুটির লেবেল থাকলে সরাসরি ট্যাপ, না থাকলে কোঅর্ডিনেট দেখে চাল দেয়।",
                    cta = "লুডু খোলো ও ছক্কা ফেলো",
                    busy = busy
                ) { run("লুডু") { games.open("ludo") + "\n" + games.play("ludo", "roll") } }

                GameCard(
                    icon = SaathiIcons.Chess,
                    title = "দাবা — ইনস্টল করা অ্যাপে",
                    body = "বোর্ডের এলাকা মেপে a1–h8 ঘরের পিক্সেল বের করে, তারপর আসল ড্র্যাগে চাল দেয়। " +
                        "চাল বসেছে কি না স্ক্রিনশট দেখে নিশ্চিত হয়।",
                    cta = "দাবা খোলো",
                    busy = busy
                ) { run("দাবা") { games.open("chess") } }

                GameCard(
                    icon = SaathiIcons.Games,
                    title = "ক্যারম — আসল বোর্ডে স্ট্রাইক",
                    body = "ক্যারম অ্যাপের বোর্ড পড়ে কোণ ও জোর হিসাব করে স্ট্রাইকার ফ্লিক করে।",
                    cta = "ক্যারম বোর্ড খোলো",
                    busy = busy
                ) { onPlayCarrom() }

                report?.let { r ->
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(jimi.secondary.copy(alpha = 0.10f))
                            .border(1.dp, jimi.secondary.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                            .padding(13.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(SaathiIcons.Robot, null, tint = jimi.secondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("জিমি কী করল", color = jimi.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(r, color = jimi.textPrimary.copy(alpha = 0.9f), fontSize = 11.5.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionHead("অনুশীলন বোর্ড (গেম ইনস্টল না থাকলে)", SaathiIcons.Grid)

                GameCard(
                    icon = SaathiIcons.Dice,
                    title = "লুডু — নিয়ম কোডে লেখা",
                    body = "৪ জন, ছক্কা, কাটা, হোম পথ — ভুল চাল হয় না। ফোনে লুডু অ্যাপ না থাকলে এটা দিয়ে খেলা যায়।",
                    cta = "অনুশীলন লুডু শুরু",
                    busy = false
                ) {
                    val st = GameEngine.LudoState.new()
                    GameEngine.ludo = st
                    ludoBoard = GameEngine.ludoBoard(st)
                    onStartGame("ludo")
                }

                GameCard(
                    icon = SaathiIcons.Chess,
                    title = "দাবা — বৈধ চাল ও মাত শনাক্ত",
                    body = "পূর্ণ বৈধ চাল, মাত/স্টেলমেট শনাক্ত, বোর্ড সবসময় স্পষ্ট।",
                    cta = "অনুশীলন দাবা শুরু",
                    busy = false
                ) {
                    val st = GameEngine.ChessState()
                    GameEngine.chess = st
                    chessBoard = GameEngine.chessBoard(st)
                    onStartGame("chess")
                }

                if (ludoBoard.isNotBlank()) BoardView(ludoBoard, "লুডু বোর্ড")
                if (chessBoard.isNotBlank()) BoardView(chessBoard, "দাবা বোর্ড")

                Spacer(Modifier.height(20.dp))
                Text(
                    "খেলার সময় শুধু বলো — «লুডু খেলো», «ছক্কা ফেলো», «টোকেন ২», «e2e4», «স্ট্রাইক করো»। " +
                        "গেম ইনস্টল না থাকলে Jimi মিথ্যা বলবে না — সেটা স্পষ্ট করে জানাবে।",
                    color = jimi.textMuted, fontSize = 11.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(28.dp))
            }

            SideRail(
                sections = listOf("status", "phone games", "report", "practice", "boards"),
                active = if (report == null) 1 else 2,
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun SectionHead(title: String, icon: ImageVector) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(jimi.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = jimi.secondary, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(title, color = jimi.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusBanner(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(SaathiIcons.Pulse, null, tint = jimi.accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(9.dp))
        Text(text, color = jimi.textMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GameCard(
    icon: ImageVector,
    title: String,
    body: String,
    cta: String,
    busy: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(jimi.primary.copy(alpha = 0.30f), jimi.secondary.copy(alpha = 0.18f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = jimi.textPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(11.dp))
            Text(title, color = jimi.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(9.dp))
        Text(body, color = jimi.textMuted, fontSize = 11.5.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(11.dp))
        GradientButton(
            text = if (busy) "চলছে…" else cta,
            icon = SaathiIcons.Play,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { onClick() }
    }
}

@Composable
private fun BoardView(board: String, title: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.30f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(13.dp)
    ) {
        Text(title, color = jimi.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(
            board,
            color = jimi.textPrimary.copy(alpha = 0.88f),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}
