package com.saathi.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.saathi.ai.ui.theme.Aqua
import com.saathi.ai.ui.theme.Pink
import com.saathi.ai.ui.theme.TextMuted
import com.saathi.ai.ui.theme.TextPrimary
import com.saathi.ai.ui.theme.Violet
import kotlinx.coroutines.delay

private data class PermItem(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val accent: Color,
    val permission: String?
)

/**
 * First-run permission page. Everything Saathi needs is requested here in one
 * flow — the OS still shows its own dialogs, and screen sharing (which Android
 * only allows interactively) is requested when the user first taps the button
 * on the home screen.
 */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val items = remember {
        buildList {
            add(
                PermItem(
                    "মাইক্রোফোন",
                    "তোমার কথা শুনতে ও সরাসরি উত্তর দিতে",
                    SaathiIcons.Mic,
                    Aqua,
                    Manifest.permission.RECORD_AUDIO
                )
            )
            add(
                PermItem(
                    "ক্যামেরা",
                    "সাথীকে চারপাশ দেখাতে (ঐচ্ছিক)",
                    SaathiIcons.PhotoCamera,
                    Violet,
                    Manifest.permission.CAMERA
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermItem(
                        "নোটিফিকেশন",
                        "অ্যাপ বন্ধ থাকলেও সেশন চালু রাখতে",
                        SaathiIcons.Notifications,
                        Pink,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                )
            }
            add(
                PermItem(
                    "স্ক্রিন শেয়ারিং",
                    "প্রয়োজনে সাথীকে তোমার স্ক্রিন দেখাতে — ট্যাপ করার সময়ই অনুমতি চাওয়া হবে",
                    SaathiIcons.ScreenShare,
                    Color(0xFFFFB86B),
                    null
                )
            )
        }
    }

    var granted by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var autoContinue by remember { mutableStateOf(false) }

    fun refresh() {
        granted = items.mapNotNull { it.permission }.associateWith {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // Re-check every time the user comes back from a system dialog.
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
    var revealAll by remember { mutableStateOf(false) }

    fun requestAll() {
        val wanted = items.mapNotNull { it.permission }.filter { granted[it] != true }
        if (wanted.isEmpty()) autoContinue = true else launcher.launch(wanted.toTypedArray())
    }

    // If everything the app strictly needs is granted, move on by itself.
    LaunchedEffect(allRequiredGranted) {
        if (autoContinue && allRequiredGranted) {
            delay(450)
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

            Text("স্বাগতম 👋", fontSize = 13.sp, color = Aqua, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("শুরু করার আগে", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "সাথীকে কথা বলতে, দেখতে আর তোমার পাশে থাকতে এই অনুমতিগুলো দরকার। এগুলো একবারই দিতে হবে।",
                fontSize = 13.5.sp,
                color = TextMuted,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(26.dp))

            items.forEachIndexed { index, item ->
                val ok = item.permission?.let { granted[it] == true } ?: false
                val appear by animateFloatAsState(
                    targetValue = if (revealAll) 1f else 0f,
                    animationSpec = tween(420, delayMillis = index * 70),
                    label = "appear$index"
                )
                Box(modifier = Modifier.scale(0.96f + 0.04f * appear)) {
                    PermissionRow(item = item, granted = ok)
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(10.dp))
            GlassRowNote()
            Spacer(Modifier.height(26.dp))

            GradientButton(
                text = if (allRequiredGranted) "এগিয়ে যাও" else "সব অনুমতি দিন",
                modifier = Modifier.fillMaxWidth(),
                icon = SaathiIcons.Check
            ) {
                if (allRequiredGranted) onDone() else requestAll()
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "ভয় নেই — কোনো ডেটা কোথাও যাচ্ছে না, শুধু Google-এর Gemini Live API-এর সাথে সরাসরি যুক্ত হবে।",
                fontSize = 11.sp,
                color = TextMuted.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    LaunchedEffect(Unit) {
        delay(120)
        revealAll = true
    }
}

@Composable
private fun PermissionRow(item: PermItem, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(
                1.dp,
                if (granted) Aqua.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(22.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(
                    Brush.linearGradient(
                        listOf(item.accent.copy(alpha = 0.30f), item.accent.copy(alpha = 0.10f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, contentDescription = null, tint = item.accent, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(item.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(item.desc, color = TextMuted, fontSize = 11.5.sp, lineHeight = 16.sp)
        }

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (granted) Aqua.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (granted) {
                Icon(
                    SaathiIcons.Check,
                    contentDescription = "granted",
                    tint = Color(0xFF04211F),
                    modifier = Modifier.size(16.dp)
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
        Text(text = "ℹ️", fontSize = 15.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "স্ক্রিন শেয়ারিংয়ের অনুমতি অ্যান্ড্রয়েড প্রতিবার আলাদা করে চায় — হোম স্ক্রিনে \"স্ক্রিন শেয়ার\" বোতামে চাপ দিলেই পপআপ আসবে।",
            color = TextMuted,
            fontSize = 11.5.sp,
            lineHeight = 17.sp
        )
    }
}
