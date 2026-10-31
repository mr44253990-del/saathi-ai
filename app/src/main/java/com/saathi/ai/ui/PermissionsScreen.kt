package com.saathi.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.saathi.ai.access.JimiAccessibilityService
import com.saathi.ai.ui.theme.Aqua
import com.saathi.ai.ui.theme.Pink
import com.saathi.ai.ui.theme.TextMuted
import com.saathi.ai.ui.theme.TextPrimary
import com.saathi.ai.ui.theme.Violet

private data class PermItem(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val accent: Color,
    val permission: String?
)

/**
 * First-run permission page. Everything Jimi needs is requested here in one flow.
 * Android still shows its own dialogs; special access (accessibility / overlay)
 * deep-links straight to the right settings page.
 */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val items = remember {
        buildList {
            add(PermItem("মাইক্রোফোন", "কথা শুনতে ও সরাসরি উত্তর দিতে", SaathiIcons.Mic, Aqua, Manifest.permission.RECORD_AUDIO))
            add(PermItem("ক্যামেরা", "জিমিকে চারপাশ দেখাতে ও ছবি তুলতে", SaathiIcons.PhotoCamera, Violet, Manifest.permission.CAMERA))
            add(PermItem("ফোন কল", "নাম ধরে বললেই সত্যিকারের কল দিতে", SaathiIcons.Call, Color(0xFF34D399), Manifest.permission.CALL_PHONE))
            add(PermItem("কন্টাক্ট", "নাম দেখে নম্বর বের করে কল দিতে", SaathiIcons.Contacts, Color(0xFF38BDF8), Manifest.permission.READ_CONTACTS))
            add(PermItem("এসএমএস পাঠানো", "তোমার হয়ে মেসেজ পাঠাতে", SaathiIcons.Sms, Color(0xFFFBBF24), Manifest.permission.SEND_SMS))
            add(PermItem("এসএমএস পড়া", "কে কবে কী মেসেজ দিয়েছে বলতে", SaathiIcons.Sms, Color(0xFFF59E0B), Manifest.permission.READ_SMS))
            add(PermItem("কল হিস্টোরি", "কে কবে ফোন দিয়েছিল বলতে", SaathiIcons.Call, Color(0xFF22D3EE), Manifest.permission.READ_CALL_LOG))
            add(PermItem("ফাইল / ছবি", "গ্যালারি থেকে ছবি খুঁজে দেখাতে", SaathiIcons.Gallery, Color(0xFFF472B6), Manifest.permission.READ_MEDIA_IMAGES))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermItem("নোটিফিকেশন", "অ্যাপ বন্ধ থাকলেও সেশন চালু রাখতে", SaathiIcons.Notifications, Pink, Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    var granted by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var overlayOn by remember { mutableStateOf(false) }
    var accessibilityOn by remember { mutableStateOf(false) }
    var autoContinue by remember { mutableStateOf(false) }
    var revealAll by remember { mutableStateOf(false) }

    fun refresh() {
        granted = items.mapNotNull { it.permission }.associateWith {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        overlayOn = Settings.canDrawOverlays(context)
        accessibilityOn = JimiAccessibilityService.isEnabled()
    }

    LaunchedEffect(Unit) { refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = granted + result
        refresh()
    }

    val allRequiredGranted = granted[Manifest.permission.RECORD_AUDIO] == true

    fun requestAll() {
        val wanted = items.mapNotNull { it.permission }.filter { granted[it] != true }
        if (wanted.isEmpty()) autoContinue = true else launcher.launch(wanted.toTypedArray())
    }

    LaunchedEffect(allRequiredGranted) {
        if (autoContinue && allRequiredGranted) {
            kotlinx.coroutines.delay(450)
            onDone()
        }
    }

    SaathiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 30.dp)
        ) {
            Spacer(Modifier.height(18.dp))

            Text("স্বাগতম", fontSize = 13.sp, color = com.saathi.ai.ui.theme.jimi.secondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Jimi-কে সব ক্ষমতা দাও", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = com.saathi.ai.ui.theme.jimi.textPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                "Jimi কথা বলে, স্ক্রিন দেখে, টাচ করে, কল দেয়, মেসেজ পাঠায় — সব কিছুর জন্য এই অনুমতিগুলো একবারই দিতে হবে।",
                fontSize = 13.5.sp,
                color = TextMuted,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(22.dp))

            // ---------- special access (accessibility + overlay)
            Text("বিশেষ ক্ষমতা", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            val accAppear by animateFloatAsState(if (revealAll) 1f else 0f, tween(420), label = "acc")
            Box(Modifier.scale(0.96f + 0.04f * accAppear)) {
                val a11yHealth = remember { com.saathi.ai.access.AccessHealth.status(context) }
                SpecialRow(
                    title = "Accessibility — Jimi-র হাত",
                    desc = when {
                        a11yHealth.connected -> "চালু আছে (${a11yHealth.uptimeMs / 60000} মিনিট)"
                        a11yHealth.enabledInSettings ->
                            "সেটিংসে চালু কিন্তু সার্ভিস থেমে গেছে — বন্ধ করে আবার চালু করো"
                        else -> "স্ক্রিন পড়া, ট্যাপ, স্ক্রল, টাইপ, ড্র্যাগ — সব এর উপর নির্ভর করে"
                    },
                    enabled = a11yHealth.connected,
                    accent = com.saathi.ai.ui.theme.jimi.secondary,
                    onClick = {
                        com.saathi.ai.access.AccessHealth.tryRepair(context)
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            }
            Spacer(Modifier.height(10.dp))

            val ovAppear by animateFloatAsState(if (revealAll) 1f else 0f, tween(520), label = "ov")
            Box(Modifier.scale(0.96f + 0.04f * ovAppear)) {
                SpecialRow(
                    title = "Floating bubble",
                    desc = "অ্যাপ ছোট করে দিলেও ছোট গোল আইকন ভেসে থাকবে আর কাজ দেখাবে",
                    enabled = overlayOn,
                    accent = Violet,
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + context.packageName)
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }

            Spacer(Modifier.height(10.dp))
            SpecialRow(
                title = "Digital Assistant + Quick Settings",
                desc = "হোম বাটন চেপে ধরে বা Quick Settings টাইল থেকে Jimi ডাকো",
                enabled = accessibilityOn || overlayOn,
                accent = com.saathi.ai.ui.theme.jimi.accent,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )

            Spacer(Modifier.height(10.dp))
            // battery optimisation is what kills the background wake word
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val unrestricted = runCatching {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            }.getOrDefault(false)
            SpecialRow(
                title = "ব্যাটারি আটকাবে না",
                desc = "এটা না দিলে Android ব্যাকগ্রাউন্ডে \"হেই Jimi\" মেরে ফেলবে",
                enabled = unrestricted,
                accent = Color(0xFFFFB86B),
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + context.packageName)
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )

            Spacer(Modifier.height(22.dp))

            // ---------- runtime permissions
            Text("পারমিশন", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            items.forEachIndexed { index, item ->
                val ok = item.permission?.let { granted[it] == true } ?: false
                val appear by animateFloatAsState(
                    targetValue = if (revealAll) 1f else 0f,
                    animationSpec = tween(420, delayMillis = 60 + index * 50),
                    label = "appear$index"
                )
                Box(modifier = Modifier.scale(0.96f + 0.04f * appear)) {
                    PermissionRow(item = item, granted = ok)
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(12.dp))
            GlassRowNote()
            Spacer(Modifier.height(24.dp))

            GradientButton(
                text = if (allRequiredGranted) "এগিয়ে যাও" else "সব অনুমতি দিন",
                modifier = Modifier.fillMaxWidth(),
                icon = SaathiIcons.Check
            ) {
                if (allRequiredGranted) onDone() else requestAll()
            }

            Spacer(Modifier.height(10.dp))

            if (!accessibilityOn) {
                Text(
                    "Accessibility চালু না করলে Jimi দেখতে/টাচ করতে পারবে না — পরে সেটিংস থেকেও চালু করা যাবে।",
                    fontSize = 11.sp,
                    color = Color(0xFFFFB86B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "কোনো ডেটা কোথাও যাচ্ছে না — শুধু Google-এর Gemini Live API-এর সাথে সরাসরি যুক্ত হবে।",
                fontSize = 11.sp,
                color = TextMuted.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        revealAll = true
    }
}

@Composable
private fun SpecialRow(
    title: String,
    desc: String,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
                )
            )
            .border(
                1.dp,
                if (enabled) Aqua.copy(alpha = 0.5f) else accent.copy(alpha = 0.35f),
                RoundedCornerShape(22.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (enabled) SaathiIcons.Check else SaathiIcons.Accessibility,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(desc, color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (enabled) "চালু" else "খোলো",
            color = if (enabled) Aqua else accent,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PermissionRow(item: PermItem, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                1.dp,
                if (granted) Aqua.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(20.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(item.accent.copy(alpha = 0.30f), item.accent.copy(alpha = 0.10f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, contentDescription = null, tint = item.accent, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.width(13.dp))

        Column(Modifier.weight(1f)) {
            Text(item.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(item.desc, color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (granted) Aqua.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (granted) {
                Icon(
                    SaathiIcons.Check,
                    contentDescription = "granted",
                    tint = Color(0xFF04211F),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun GlassRowNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFB86B).copy(alpha = 0.08f))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(SaathiIcons.Mic, null, tint = com.saathi.ai.ui.theme.jimi.secondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = "বাংলায় \"হেই জিমি\" বা \"Hey Jimi\" বলে ডাকলেই Jimi সাড়া দেবে — মাইক্রোফোন চালু না থাকলেও হটওয়ার্ড লিসেনার কাজ করবে (পরে হোম স্ক্রিন থেকে বন্ধ করা যাবে)।",
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}
