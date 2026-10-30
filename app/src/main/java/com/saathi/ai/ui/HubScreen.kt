package com.saathi.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.live.Phase
import com.saathi.ai.ui.theme.jimi

/**
 * The hub: one screen that shows everything Jimi can do right now, grouped, plus
 * shortcuts into the specialised pages (memory, themes, drawing, games, files).
 *
 * Design language: soft glass cards on a gradient wash, one accent per row, big
 * touch targets, no dense paragraphs — the previous screens read like a settings
 * dump, this one reads like a product.
 */
@Composable
fun HubScreen(
    installedApps: Int,
    knownApps: Int,
    memoryCount: Int,
    reminders: Int,
    chats: Int,
    accessibilityHealthy: Boolean,
    onOpenMemory: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenDraw: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenGames: () -> Unit,
    onBack: () -> Unit
) {
    SaathiBackground {
        Column(
            Modifier
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
                Column {
                    Text("Jimi হাব", color = jimi.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (accessibilityHealthy) "সব সিস্টেম চালু" else "Accessibility বন্ধ — কিছু কাজ আটকাবে",
                        color = if (accessibilityHealthy) jimi.secondary else Color(0xFFFFB86B),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------------------------------------------------- hero orb
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(jimi.bgMid, jimi.bgBottom)
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(26.dp))
            ) {
                JimiOrb(
                    phase = Phase.LISTENING,
                    level = 0.3f,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(180.dp)
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("অ্যাপ", "$installedApps", "চেনে $knownApps", Modifier.weight(1f))
                Stat("মেমরি", "$memoryCount", "রিমাইন্ডার $reminders", Modifier.weight(1f))
                Stat("চ্যাট", "$chats", "সেভ আছে", Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))
            Text("পেজ", color = jimi.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            // ---------------------------------------------------- nav tiles
            HubTile("মেমরি ও রিমাইন্ডার", "কী মনে আছে, কী করতে হবে", SaathiIcons.Brain, onOpenMemory)
            HubTile("চেহারা ও থিম", "১২টি থিম, সাথে কাস্টম", SaathiIcons.Palette, onOpenThemes)
            HubTile("আঁকা ও ডিজাইন", "নিজে হাতে আঁকো, তারপর শেয়ার", SaathiIcons.Draw, onOpenDraw)
            HubTile("ফাইল", "কপি, মুভ, খোঁজা, শেয়ার", SaathiIcons.Folder, onOpenFiles)
            HubTile("গেম", "লুডু, দাবা, আসল ক্যারম বোর্ড", SaathiIcons.Games, onOpenGames)

            Spacer(Modifier.height(20.dp))
            Text("সক্ষমতা", color = jimi.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            CapabilityGrid()

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp, horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = jimi.secondary, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(label, color = jimi.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(sub, color = jimi.textMuted, fontSize = 9.sp)
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
                .background(Brush.linearGradient(listOf(jimi.primary.copy(alpha = 0.85f), jimi.secondary.copy(alpha = 0.85f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = jimi.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = jimi.textMuted, fontSize = 10.5.sp)
        }
        Text("›", color = jimi.textMuted, fontSize = 20.sp)
    }
}

@Composable
private fun CapabilityGrid() {
    val items = listOf(
        "কল ও SMS" to "নাম ধরে কল, মেসেজ পড়া/পাঠানো",
        "স্ক্রিন" to "পড়া, ট্যাপ, ড্র্যাগ, মাল্টি-টাচ",
        "অ্যাপ" to "যেকোনো অ্যাপ, অচেনাও",
        "ছবি/ভিডিও" to "দেখে বোঝে, ফ্রেম বের করে",
        "ওয়েব" to "সার্চ, ম্যাপ, আবহাওয়া",
        "ফাইল" to "কপি/মুভ/শেয়ার",
        "পেমেন্ট" to "UPI স্ক্রিন খোলে, টাকা পাঠায় না",
        "ওষুধ" to "রিমাইন্ডার সেট করে",
        "গেম" to "লুডু, দাবা, আসল ক্যারম",
        "অটোপাইলট" to "২২ ধাপ পর্যন্ত নিজে"
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
