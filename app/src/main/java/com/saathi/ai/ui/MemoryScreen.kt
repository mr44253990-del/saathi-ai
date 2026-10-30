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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.data.ChatStore
import com.saathi.ai.data.MemoryStore
import com.saathi.ai.ui.theme.jimi

/**
 * The Memory Centre: everything Jimi has stored about the user, all editable.
 *
 * Sections:
 *  • সেভ করা কথা — facts (add / delete / wipe)
 *  • রিমাইন্ডার — medicine and task reminders
 *  • চ্যাট হিস্টোরি — previous conversations, searchable
 *  • কী কী কাজ করতে পারে — a live capability list read from the tool registry
 */
@Composable
fun MemoryScreen(
    memory: MemoryStore,
    chats: ChatStore,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    var facts by remember { mutableStateOf(memory.all()) }
    var reminders by remember { mutableStateOf(memory.reminders()) }
    var chatList by remember { mutableStateOf(chats.search("")) }

    SaathiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SaathiIcons.ArrowBack, "ফিরে যাও", tint = jimi.textPrimary, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("মেমরি", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = jimi.textPrimary)
                    Text(
                        "${facts.size}টি কথা · ${reminders.size}টি রিমাইন্ডার · ${chatList.size}টি চ্যাট",
                        fontSize = 10.5.sp, color = jimi.textMuted
                    )
                }
            }

            // ---------------------------------------------------- facts
            Section("মনে রাখা কথা", "🧠") {
                if (facts.isEmpty()) {
                    Hint("এখনো কিছু মনে নেই। কথা বলার সময় Jimi নিজেই সেভ করবে — অথবা নিচে লিখে যোগ করো।")
                }
                facts.forEach { f ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(f.key, color = jimi.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(f.value, color = jimi.textPrimary, fontSize = 13.sp)
                            Text(memory.humanDate(f.createdAt), color = jimi.textMuted, fontSize = 9.5.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FF6B81))
                                .clickable {
                                    memory.forget(f.id)
                                    facts = memory.all()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color(0xFFFF8A9C), fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("কী (যেমন: বাসা)", color = jimi.textMuted, fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = jimi.secondary.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedTextColor = jimi.textPrimary,
                        unfocusedTextColor = jimi.textPrimary
                    )
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("তথ্য (যেমন: সাভার, ঢাকা)", color = jimi.textMuted, fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = jimi.secondary.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedTextColor = jimi.textPrimary,
                        unfocusedTextColor = jimi.textPrimary
                    )
                )
                Spacer(Modifier.height(8.dp))
                GradientButton(
                    text = "মনে রাখো",
                    modifier = Modifier.fillMaxWidth(),
                    icon = SaathiIcons.Check
                ) {
                    if (newKey.isNotBlank() && newValue.isNotBlank()) {
                        memory.remember(newKey, newValue)
                        facts = memory.all()
                        newKey = ""
                        newValue = ""
                    }
                }
                if (facts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x22FF6B81))
                            .clickable {
                                memory.forgetAll()
                                facts = emptyList()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("সব ভুলে যাও", color = Color(0xFFFF8A9C), fontSize = 11.sp)
                    }
                }
            }

            // ---------------------------------------------------- reminders
            Section("রিমাইন্ডার", "⏰") {
                if (reminders.isEmpty()) {
                    Hint("কোনো রিমাইন্ডার নেই। Jimi-কে বলো: «সকাল ৮টায় নাপা খাওয়ার কথা মনে করিয়ে দিও»।")
                }
                reminders.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.text, color = jimi.textPrimary, fontSize = 13.sp)
                            Text("⏰ ${r.time}", color = jimi.secondary, fontSize = 11.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FF6B81))
                                .clickable {
                                    memory.removeReminder(r.id)
                                    reminders = memory.reminders()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color(0xFFFF8A9C), fontSize = 13.sp)
                        }
                    }
                }
            }

            // ---------------------------------------------------- chats
            Section("চ্যাট হিস্টোরি", "💬") {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        chatList = chats.search(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("খোঁজো…", color = jimi.textMuted, fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = jimi.secondary.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedTextColor = jimi.textPrimary,
                        unfocusedTextColor = jimi.textPrimary
                    )
                )
                Spacer(Modifier.height(8.dp))
                if (chatList.isEmpty()) {
                    Hint("কোনো চ্যাট নেই।")
                }
                chatList.take(12).forEach { c ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(14.dp)
                    ) {
                        Text(
                            c.title,
                            color = jimi.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${c.messages.size}টি মেসেজ · ${memory.humanDate(c.created)}",
                            color = jimi.textMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // ---------------------------------------------------- capabilities
            Section("Jimi কী কী পারে", "⚡") {
                CapabilityList()
            }

            Spacer(Modifier.height(26.dp))
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun Section(title: String, emoji: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Text(title, color = jimi.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = jimi.textMuted, fontSize = 11.5.sp, lineHeight = 17.sp)
}

@Composable
private fun CapabilityList() {
    val groups = listOf(
        "ফোন" to listOf("কল করা", "SMS পড়া/পাঠানো", "কল হিস্টোরি", "কন্টাক্ট", "ফ্ল্যাশলাইট", "ভলিউম", "অ্যালার্ম/টাইমার"),
        "স্ক্রিন" to listOf("পড়া", "ট্যাপ", "লং-প্রেস", "স্ক্রল", "টাইপ", "ড্র্যাগ", "পিঞ্চ", "রোটেট", "৩-৪ আঙুল"),
        "অ্যাপ" to listOf("যেকোনো অ্যাপ খোলা", "নতুন অ্যাপ চেনা", "YouTube সার্চ/প্লে", "WhatsApp", "TikTok", "Instagram", "Facebook"),
        "ইন্টারনেট" to listOf("ওয়েব সার্চ", "আবহাওয়া", "ম্যাপ", "Play Store"),
        "ফাইল/মিডিয়া" to listOf("গ্যালারি", "ছবি তোলা", "ছবি শেয়ার", "ফাইল কপি/পেস্ট"),
        "টাকা" to listOf("UPI পেমেন্ট স্ক্রিন", "QR স্ক্যান"),
        "স্মার্ট" to listOf("দীর্ঘমেয়াদি মেমরি", "রিমাইন্ডার", "অটোপাইলট", "মাল্টি-স্টেপ কাজ", "গেম (লুডু/দাবা)")
    )
    groups.forEach { (title, items) ->
        Column(Modifier.padding(bottom = 10.dp)) {
            Text(title, color = jimi.secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            items.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { item ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(item, color = jimi.textPrimary.copy(alpha = 0.85f), fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}
