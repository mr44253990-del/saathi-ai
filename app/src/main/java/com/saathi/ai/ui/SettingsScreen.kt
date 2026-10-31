package com.saathi.ai.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.saathi.ai.ui.theme.JimiThemeSpec
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.bubble.BubbleService
import com.saathi.ai.data.SecurePrefs
import com.saathi.ai.hotword.HotwordService
import com.saathi.ai.ui.theme.Aqua
import com.saathi.ai.ui.theme.JimiThemes
import com.saathi.ai.ui.theme.jimi
import com.saathi.ai.ui.theme.TextMuted
import com.saathi.ai.ui.theme.TextPrimary
import com.saathi.ai.ui.theme.Violet

@Composable
fun SettingsScreen(
    prefs: SecurePrefs,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenThemes: () -> Unit = {},
    onThemeChanged: (String) -> Unit = {},
    onAccentChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current

    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var model by remember { mutableStateOf(prefs.model) }
    var voice by remember { mutableStateOf(prefs.voice) }
    var language by remember { mutableStateOf(prefs.language) }
    var persona by remember { mutableStateOf(prefs.persona.ifBlank { SecurePrefs.DEFAULT_PERSONA }) }
    var showTranscript by remember { mutableStateOf(prefs.showTranscript) }
    var searchGrounding by remember { mutableStateOf(prefs.searchGrounding) }
    var revealKey by remember { mutableStateOf(false) }
    var memory by remember { mutableStateOf(prefs.recall()) }
    var saved by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf("") }
    var deepInfo by remember { mutableStateOf("রুট ও টার্মিনালের অবস্থা দেখা হচ্ছে…") }
    val scope = rememberCoroutineScope()

    // Both of these touch su / PackageManager, so they never run on the UI thread.
    LaunchedEffect(Unit) {
        deepInfo = withContext(Dispatchers.IO) {
            val root = runCatching { com.saathi.ai.access.RootBridge.available() }.getOrDefault(false)
            val termux = runCatching {
                context.packageManager.getPackageInfo("com.termux", 0); true
            }.getOrElse { false }
            val acc = com.saathi.ai.access.JimiAccessibilityService.instance != null
            "রুট: ${if (root) "আছে (su চলছে)" else "নেই"} · Termux: ${if (termux) "ইনস্টল" else "ইনস্টল নেই"} · " +
                "অ্যাক্সেসিবিলিটি: ${if (acc) "চালু" else "বন্ধ"}"
        }
    }

    fun run(label: String, block: () -> String) {
        report = "$label চলছে…"
        scope.launch {
            report = withContext(Dispatchers.IO) {
                runCatching { block() }.getOrElse { "ব্যর্থ: ${it.message}" }
            }
        }
    }

    val scroll = rememberScrollState()
    val sectionNames = listOf(
        "কী", "মডেল", "কণ্ঠ", "চেহারা", "ডাক",
        "রুট/টার্মিনাল", "ব্যাকগ্রাউন্ড", "অটো রিকানেক্ট", "বাবল", "ভিশন", "হেলথ", "মেমরি", "অ্যাডভান্সড"
    )
    val activeSection = if (scroll.maxValue <= 0) 0 else
        ((scroll.value.toFloat() / scroll.maxValue) * (sectionNames.size - 1)).roundToInt()

    SaathiBackground {
      Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SaathiIcons.ArrowBack, "ফিরে যাও", tint = TextPrimary, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("সেটিংস", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Spacer(Modifier.height(10.dp))

            ProfileHero(
                themeSpec = JimiThemes.get(prefs.themeId),
                themeName = JimiThemes.get(prefs.themeId).bangla,
                model = model,
                voice = voice,
                onOpenThemes = onOpenThemes
            )

            Spacer(Modifier.height(12.dp))

            // ---------- API key
            SectionCard(title = "Gemini API Key", icon = SaathiIcons.Key) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("AIza…", color = TextMuted, fontSize = 13.sp) },
                    visualTransformation = if (revealKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Icon(
                            if (revealKey) SaathiIcons.VisibilityOff else SaathiIcons.Visibility,
                            contentDescription = "key",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { revealKey = !revealKey }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "এটা তোমার ফোনেই এনক্রিপ্ট হয়ে সেভ থাকবে, কোথাও পাঠানো হয় না।",
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Violet.copy(alpha = 0.16f))
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ফ্রি API Key নাও", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Icon(SaathiIcons.OpenInNew, null, tint = Violet, modifier = Modifier.size(13.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Model
            SectionCard(title = "মডেল", icon = SaathiIcons.Brain) {
                ChipRow(
                    options = SecurePrefs.MODELS,
                    selected = model,
                    onSelect = { model = it },
                    labelMapper = { m ->
                        when (m) {
                            "gemini-3.8-live" -> "3.8 Live"
                            "gemini-3.8-live-extended-thinking" -> "3.8 Live Thinking"
                            "gemini-3.1-flash-live-preview" -> "3.1 Flash Live"
                            else -> "2.5 Native Audio"
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text(text = model, color = TextMuted, fontSize = 10.5.sp)
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Voice
            SectionCard(title = "কণ্ঠস্বর", icon = SaathiIcons.Mic) {
                ChipRow(
                    options = SecurePrefs.VOICES,
                    selected = voice,
                    onSelect = { voice = it }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Language
            SectionCard(title = "ভাষা", icon = SaathiIcons.Globe) {
                ChipRow(
                    options = listOf("bn", "en", "auto"),
                    selected = language,
                    onSelect = { language = it },
                    labelMapper = { l ->
                        when (l) {
                            "bn" -> "বাংলা"
                            "en" -> "English"
                            else -> "অটো"
                        }
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Persona
            SectionCard(title = "Jimi-র ব্যক্তিত্ব", icon = SaathiIcons.Heart) {
                OutlinedTextField(
                    value = persona,
                    onValueChange = { persona = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text("সাথী কেমন হবে, কীভাবে কথা বলবে…", color = TextMuted, fontSize = 13.sp) },
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    "রিসেট করো",
                    color = Aqua,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Aqua.copy(alpha = 0.12f))
                        .clickable { persona = SecurePrefs.DEFAULT_PERSONA }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Toggles
            SectionCard(title = "রঙের ছোঁয়া", icon = SaathiIcons.Palette) {
                Text(
                    "যেকোনো থিমের ওপর একটা রঙ চাপিয়ে দাও — অরব, বাটন, গ্লো সব একসাথে বদলে যাবে।",
                    color = TextMuted, fontSize = 10.5.sp, lineHeight = 15.sp
                )
                Spacer(Modifier.height(12.dp))
                AccentSwatches(
                    selected = prefs.accentOverride,
                    onPick = {
                        prefs.accentOverride = it
                        onAccentChanged(it)
                    }
                )
            }

            SectionCard(title = "চেহারা ও থিম", icon = SaathiIcons.Palette) {
                val theme = com.saathi.ai.ui.theme.JimiThemes.get(prefs.themeId)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(theme.palette.bgTop, theme.palette.bgMid)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        ThemeMark(theme, 22.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(theme.name, color = com.saathi.ai.ui.theme.jimi.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${theme.bangla} · ${com.saathi.ai.ui.theme.JimiThemes.all.size}টি থিম উপলব্ধ", color = com.saathi.ai.ui.theme.jimi.textMuted, fontSize = 10.5.sp)
                    }
                    SmallAction("বদলাও") { onOpenThemes() }
                }
            }

            SectionCard(title = "Jimi-কে ডাকো", icon = SaathiIcons.Sparkle) {
                ToggleRow(
                    title = "\"হেই Jimi\" ওয়েক-ওয়ার্ড",
                    subtitle = "মাইক বন্ধ থাকলেও যেকোনো অ্যাপ থেকে ডাকলেই Jimi জেগে উঠবে",
                    checked = prefs.hotwordEnabled,
                    onChange = {
                        prefs.hotwordEnabled = it
                        val ctx = context
                        if (it) HotwordService.start(ctx) else HotwordService.stop(ctx)
                    }
                )
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "ফ্লোটিং বাবল",
                    subtitle = "কাজ করার সময় ছোট গোল আইকনে নেমে সবসময় হাতের কাছে থাকবে",
                    checked = prefs.bubbleEnabled,
                    onChange = {
                        if (!it) {
                            prefs.bubbleEnabled = false
                            BubbleService.hide(context)
                        } else if (Settings.canDrawOverlays(context)) {
                            prefs.bubbleEnabled = true
                            BubbleService.show(context)
                        } else {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName)
                                    )
                                )
                            }
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("Accessibility সেটিংস") {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                    SmallAction("সব অনুমতি") {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + context.packageName)
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SmallAction("ডিজিটাল অ্যাসিস্ট্যান্ট সেট করো") {
                    com.saathi.ai.assist.AssistantSetup.openAssistantSettings(context)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "ডিজিটাল অ্যাসিস্ট্যান্ট বানাতে: Settings → Apps → Default apps → Digital assistant app → Jimi। " +
                        "এরপর হোম বাটন চেপে ধরে বা পাওয়ার বাটন চেপে ধরে Jimi ডাকতে পারবে। Quick Settings-এ Jimi টাইলও যোগ করা যায়।",
                    color = com.saathi.ai.ui.theme.jimi.textMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }

            SectionCard(title = "Jimi-র মনে রাখা কথা", icon = SaathiIcons.Brain) {
                Text(
                    text = if (memory.isBlank()) "এখনো কিছু মনে নেই। কথা বলার সময় Jimi নিজেই মনে রাখার মতো তথ্য সেভ করবে।"
                    else memory,
                    color = if (memory.isBlank()) TextMuted else TextPrimary.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                if (memory.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    SmallAction("সব ভুলে যাও") {
                        prefs.forgetAll()
                        memory = ""
                    }
                }
            }

            // ------------------------------------------------ deep power: root + terminal
            SectionCard(title = "রুট ও টার্মিনাল", icon = SaathiIcons.Root) {
                Text(deepInfo, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "রুট (su) ব্যবহার করো",
                    subtitle = "অনুমতিতে কাজ আটকালে Jimi রুট দিয়ে সেই কাজ শেষ করার চেষ্টা করবে",
                    checked = prefs.rootMode,
                    onChange = { prefs.rootMode = it }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("রুট পরীক্ষা") { run("রুট পরীক্ষা") { com.saathi.ai.access.RootBridge.status(context) } }
                    SmallAction("Termux খোলো") {
                        run("Termux") {
                            com.saathi.ai.access.AppDirectory.launchPackage(context, "com.termux", "termux")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("শেল কমান্ড চালিয়ে দেখো") {
                        run("শেল") { com.saathi.ai.access.TerminalTools(context).shell("id && uname -a && df -h /data | tail -1") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Termux-এ কমান্ড চালাতে হলে Termux-এর ফাইল ~/.termux/termux.properties-এ " +
                        "allow-external-apps=true থাকতে হবে। না থাকলে Jimi রুট থাকলে নিজেই ঠিক করে নেয়, " +
                        "নয়তো তোমাকে একবার মাত্র Termux খুলে সেটিং করতে বলে।",
                    color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp
                )
            }

            // ------------------------------------------------ background work + timer
            SectionCard(title = "ব্যাকগ্রাউন্ড কাজ ও টাইমার", icon = SaathiIcons.Task) {
                ToggleRow(
                    title = "ব্যাকগ্রাউন্ডে কাজ চালিয়ে যাক",
                    subtitle = "বড় কাজ টু-ডু লিস্টে ভেঙে অ্যাপ বন্ধ থাকলেও চলবে, শেষ হলে টিক পড়বে",
                    checked = prefs.backgroundTasks,
                    onChange = { prefs.backgroundTasks = it }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("ওয়ার্কার চালু") {
                        run("ওয়ার্কার") {
                            com.saathi.ai.task.TaskRunnerService.start(context, null)
                            "ব্যাকগ্রাউন্ড ওয়ার্কার চালু — নোটিফিকেশনে অগ্রগতি দেখাবে"
                        }
                    }
                    SmallAction("ওয়ার্কার থামাও") {
                        run("থামানো") {
                            com.saathi.ai.task.TaskRunnerService.stop(context)
                            "ওয়ার্কার থামিয়েছি"
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SmallAction("ব্যাটারি অপটিমাইজেশন থেকে বাদ দাও") {
                    runCatching {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + context.packageName)
                            )
                        )
                        "রিকোয়েস্ট পাঠিয়েছি — Allow চেপে দাও"
                    }.getOrElse { "খোলা যায়নি: ${it.message}" }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "«১০ মিনিট ধরে কাজ করো» বললে Jimi ওই সময় ধরে লক্ষ্য নিয়ে খাটবে — " +
                        "মাঝে স্ক্রিন বন্ধ করলেও থামবে না।",
                    color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp
                )
            }

            // ------------------------------------------------ auto reconnect on voice
            SectionCard(title = "চুপ থাকলে অটো রিকানেক্ট", icon = SaathiIcons.Mic) {
                ToggleRow(
                    title = "গলা শুনলে নিজে যুক্ত হবে",
                    subtitle = "অনেকক্ষণ চুপ থাকলে সেশন পার্ক হয়ে যাবে, তুমি কথা শুরু করলেই আবার যুক্ত হবে",
                    checked = prefs.autoReconnectVoice,
                    onChange = {
                        prefs.autoReconnectVoice = it
                        runCatching {
                            com.saathi.ai.capture.LiveSessionService.instance?.setAutoReconnect(it)
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                Text("কতক্ষণ চুপ থাকলে পার্ক হবে", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(30, 60, 120, 300, 0).forEach { sec ->
                        val on = prefs.idleParkSeconds == sec
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (on) jimi.secondary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f)
                                )
                                .border(
                                    1.dp,
                                    if (on) jimi.secondary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.10f),
                                    RoundedCornerShape(50)
                                )
                                .clickable { prefs.idleParkSeconds = sec }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                if (sec == 0) "কখনো না" else "$sec সে.",
                                color = if (on) TextPrimary else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "পার্ক অবস্থায় শুধু মাইকের শক্তি মাপা হয় — অডিও কোথাও পাঠানো হয় না।",
                    color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp
                )
            }

            // ------------------------------------------------ bubble look
            SectionCard(title = "বাবল", icon = SaathiIcons.Bubble) {
                Text("চেহারা", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("pill" to "গোলক + লেখা", "orb" to "শুধু গোলক").forEach { (id, label) ->
                        val on = prefs.bubbleStyle == id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (on) jimi.secondary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f))
                                .border(
                                    1.dp,
                                    if (on) jimi.secondary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.10f),
                                    RoundedCornerShape(50)
                                )
                                .clickable { prefs.bubbleStyle = id }
                                .padding(horizontal = 13.dp, vertical = 8.dp)
                        ) {
                            Text(label, color = if (on) TextPrimary else TextMuted, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("বাবল দেখাও") {
                        run("বাবল") {
                            com.saathi.ai.bubble.BubbleService.show(context)
                            "বাবল দেখাচ্ছি — ধরে টেনে যেকোনো জায়গায় রাখো"
                        }
                    }
                    SmallAction("অবস্থান রিসেট") {
                        prefs.bubbleX = -1
                        prefs.bubbleY = -1
                        "বাবল আবার ডিফল্ট জায়গায় চলে যাবে"
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "অ্যাপ খোলা থাকলে বাবল নিজেই লুকায়, ব্যাকগ্রাউন্ডে কাজ শুরু হলে নিজেই ফিরে আসে। " +
                        "Jimi যা বলছে সেই লেখা বাবলের ভিতরেই নিচ থেকে উপরে উঠতে থাকে।",
                    color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp
                )
            }

            // ------------------------------------------------ screenshot vision
            SectionCard(title = "স্ক্রিনশট ভিশন (স্ক্রিন শেয়ার ছাড়া)", icon = SaathiIcons.Visibility) {
                ToggleRow(
                    title = "স্ক্রিনশট তুলে দেখে কাজ করো",
                    subtitle = "স্ক্রিন শেয়ার চালু না করেও Jimi স্ক্রিন দেখে, কোন অপশন কোথায় বের করে ট্যাপ করে",
                    checked = prefs.visionMode,
                    onChange = { prefs.visionMode = it }
                )
                Spacer(Modifier.height(12.dp))
                SmallAction("এখনই স্ক্রিনশট নিয়ে দেখাও") {
                    run("স্ক্রিনশট ভিশন") { com.saathi.ai.access.ScreenVision.look(context) }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "ছবি দেখা শেষ হলে তা মুছে ফেলা হয় — শুধু বর্ণনা থেকে যায়। " +
                        "FLAG_SECURE অ্যাপ (ব্যাংকিং/ড্রম) স্ক্রিনশট দেয় না, তখন Jimi অ্যাক্সেসিবিলিটি ট্রি পড়ে কাজ চালায়।",
                    color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp
                )
            }

            // ------------------------------------------------ health + self repair
            SectionCard(title = "স্বাস্থ্য পরীক্ষা ও নিজে সারানো", icon = SaathiIcons.Pulse) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("হেলথ চেক") { run("হেলথ চেক") { com.saathi.ai.access.HealthInsights(context).report() } }
                    SmallAction("নিজে ঠিক করো") { run("নিজে সারানো") { com.saathi.ai.access.HealthInsights(context).selfFix(true) } }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "ব্যাটারি, তাপ, স্টোরেজ, র‍্যাম, নেটওয়ার্ক, অনুমতি, API — সব মেপে বলে, " +
                        "এবং যা সম্ভব নিজেই ঠিক করে।",
                    color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp
                )
            }

            SectionCard(title = "অ্যাডভান্সড", icon = SaathiIcons.Settings) {
                ToggleRow(
                    title = "কথার লিখিত রূপ দেখাও",
                    subtitle = "তুমি আর Jimi কী বলছে, স্ক্রিনে লেখা আকারে দেখাবে",
                    checked = showTranscript,
                    onChange = { showTranscript = it }
                )
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "অটোপাইলট (নিজে কাজ শেষ করবে)",
                    subtitle = "লম্বা কাজে Jimi নিজেই ধাপে ধাপে শেষ করবে, বারবার জিজ্ঞেস করবে না",
                    checked = prefs.autopilotEnabled,
                    onChange = { prefs.autopilotEnabled = it }
                )
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "Google Search গ্রাউন্ডিং",
                    subtitle = "Jimi দরকারমতো লাইভ তথ্য খুঁজে উত্তর দিতে পারবে",
                    checked = searchGrounding,
                    onChange = { searchGrounding = it }
                )
            }

            if (report.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(jimi.secondary.copy(alpha = 0.10f))
                        .border(1.dp, jimi.secondary.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                        .padding(13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(SaathiIcons.Robot, null, tint = jimi.secondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("জিমির রিপোর্ট", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.clickable { report = "" }.padding(3.dp)) {
                            Icon(SaathiIcons.Close, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(report, color = TextPrimary.copy(alpha = 0.9f), fontSize = 11.sp, lineHeight = 16.sp)
                }
            }

            Spacer(Modifier.height(22.dp))

            GradientButton(
                text = if (saved) "সেভ হয়েছে" else "সেভ করো",
                modifier = Modifier.fillMaxWidth(),
                icon = SaathiIcons.Check
            ) {
                prefs.apiKey = apiKey
                prefs.model = model
                prefs.voice = voice
                prefs.language = language
                prefs.persona = if (persona.trim() == SecurePrefs.DEFAULT_PERSONA.trim()) "" else persona
                prefs.showTranscript = showTranscript
                prefs.searchGrounding = searchGrounding
                saved = true
                onSaved()
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "নতুন সেটিং চলতি সেশনে নয়, পরের সেশনে প্রযোজ্য হবে।",
                color = TextMuted.copy(alpha = 0.7f),
                fontSize = 10.5.sp
            )
            Spacer(Modifier.height(26.dp))
        }

        SideRail(
            sections = sectionNames,
            active = activeSection,
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
private fun SmallAction(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = jimi.secondary, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(9.dp))
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labelMapper: (String) -> String = { it }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) {
                            Brush.linearGradient(listOf(Violet, Aqua))
                        } else {
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.04f))
                            )
                        }
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = labelMapper(option),
                    color = if (isSelected) Color.White else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextMuted, fontSize = 10.5.sp, lineHeight = 14.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF04211F),
                checkedTrackColor = Aqua,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f)
            )
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Aqua.copy(alpha = 0.6f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
    cursorColor = Aqua,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = Color.White.copy(alpha = 0.04f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
)

/**
 * The top of Settings used to be a bare title. It is now a small "identity card":
 * the current theme, model and voice are visible at a glance, and tapping it goes
 * straight to the theme gallery. This is the visual anchor for the redesign.
 */
@Composable
private fun ProfileHero(
    themeSpec: JimiThemeSpec,
    themeName: String,
    model: String,
    voice: String,
    onOpenThemes: () -> Unit
) {
    val p = jimi
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(24.dp))
            .clickable { onOpenThemes() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(p.primary, p.secondary))),
            contentAlignment = Alignment.Center
        ) {
            ThemeMark(themeSpec, 30.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Jimi · $themeName", color = p.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(
                "$model  ·  কণ্ঠ $voice",
                color = p.textMuted, fontSize = 10.5.sp, maxLines = 1
            )
        }
        Text("›", color = p.textMuted, fontSize = 22.sp)
    }
}

/** Twelve accent dots plus a "follow theme" option. */
@Composable
private fun AccentSwatches(selected: Int, onPick: (Int) -> Unit) {
    val accents = JimiThemes.accents
    Column {
        accents.chunked(6).forEachIndexed { rowIdx, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEachIndexed { colIdx, color ->
                    val index = rowIdx * 6 + colIdx
                    val isOn = selected == index
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color)
                            .border(
                                width = if (isOn) 2.5.dp else 1.dp,
                                color = if (isOn) Color.White else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onPick(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isOn) {
                            Icon(SaathiIcons.Check, null, tint = Color.Black.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected < 0) Brush.linearGradient(listOf(jimi.primary, jimi.secondary))
                    else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.04f)))
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                .clickable { onPick(-1) }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                "থিমের নিজের রঙ",
                color = if (selected < 0) Color.White else TextMuted,
                fontSize = 11.sp
            )
        }
    }
}
