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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.create.GameEngine
import com.saathi.ai.ui.theme.jimi

/**
 * Games hub. Two very different experiences, clearly separated:
 *
 *  • **বোর্ডে (with Jimi)** — Ludo and chess are played in conversation: the rules
 *    are enforced in code, so the game is real and always terminates.
 *  • **ফোনের অ্যাপে (on your phone)** — Carrom is played on an actual installed
 *    carrom game: Jimi opens the board and strikes through accessibility. That is
 *    the difference the user asked for: no more "playing in its head".
 */
@Composable
fun GamesScreen(onBack: () -> Unit, onPlayCarrom: () -> Unit, onStartGame: (String) -> Unit) {
    var ludoBoard by remember { mutableStateOf("") }
    var chessBoard by remember { mutableStateOf("") }

    SaathiBackground {
        Column(
            Modifier
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
                Column {
                    Text("গেম", color = jimi.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Jimi সত্যি খেলে — নিয়ম মানে", color = jimi.textMuted, fontSize = 10.5.sp)
                }
            }

            Spacer(Modifier.height(6.dp))

            GameCard(
                emoji = "🎯",
                title = "ক্যারম — আসল বোর্ডে",
                body = "Jimi ফোনের ক্যারম অ্যাপ খুলে বোর্ড দেখে, কোণ হিসাব করে স্ট্রাইকার মারে। " +
                    "স্ট্রাইকের পর বোর্ড আবার পড়ে পরের শট ঠিক করে।",
                cta = "বোর্ড খুলে খেলো"
            ) { onPlayCarrom() }

            GameCard(
                emoji = "🎲",
                title = "লুডু",
                body = "৪ জন, ছক্কা, কাটা, হোম পথ — সব নিয়ম কোডে লেখা, তাই ভুল চাল হয় না।",
                cta = "লুডু শুরু"
            ) {
                val st = GameEngine.LudoState.new()
                GameEngine.ludo = st
                ludoBoard = GameEngine.ludoBoard(st)
                onStartGame("ludo")
            }

            GameCard(
                emoji = "♟️",
                title = "দাবা",
                body = "পূর্ণ বৈধ চাল, মাত শনাক্ত, বোর্ড সবসময় স্পষ্ট।",
                cta = "দাবা শুরু"
            ) {
                val st = GameEngine.ChessState()
                GameEngine.chess = st
                chessBoard = GameEngine.chessBoard(st)
                onStartGame("chess")
            }

            if (ludoBoard.isNotBlank()) {
                BoardView(ludoBoard, "লুডু বোর্ড")
            }
            if (chessBoard.isNotBlank()) {
                BoardView(chessBoard, "দাবা বোর্ড")
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "খেলার সময় শুধু বলো — «ছক্কা», «টোকেন ২», «e2e4»। Jimi নিয়ম মেনে চলে দেবে।",
                color = jimi.textMuted,
                fontSize = 10.5.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GameCard(
    emoji: String,
    title: String,
    body: String,
    cta: String,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Text(title, color = jimi.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, color = jimi.textMuted, fontSize = 11.5.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(jimi.primary, jimi.secondary)))
                .clickable { onClick() }
                .padding(horizontal = 18.dp, vertical = 9.dp)
        ) {
            Text(cta, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BoardView(content: String, label: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(label, color = jimi.secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            content,
            color = jimi.textPrimary.copy(alpha = 0.9f),
            fontSize = 10.5.sp,
            lineHeight = 16.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}
