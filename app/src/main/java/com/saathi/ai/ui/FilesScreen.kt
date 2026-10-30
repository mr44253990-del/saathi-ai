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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.access.FileTools
import com.saathi.ai.ui.theme.jimi

/**
 * A small, fast file browser for the folders Jimi is allowed to read, plus
 * share/copy actions. Anything Android blocks (other apps' private dirs) is
 * handed to the system Files app through the assistant instead of silently
 * failing — that distinction is shown right in the header.
 */
@Composable
fun FilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val tools = remember { FileTools(context) }

    var path by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf(tools.list(null)) }
    var header by remember { mutableStateOf("ডাউনলোড ও অন্যান্য ফোল্ডার") }
    var note by remember { mutableStateOf<String?>(null) }

    SaathiBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
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
                        .clickable {
                            if (path == null) onBack() else {
                                path = null
                                entries = tools.list(null)
                                header = "ডাউনলোড ও অন্যান্য ফোল্ডার"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SaathiIcons.ArrowBack, "ফিরে", tint = jimi.textPrimary, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(header, color = jimi.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${entries.size}টি আইটেম",
                        color = jimi.textMuted, fontSize = 10.sp
                    )
                }
            }

            if (note != null) {
                Text(
                    note!!,
                    color = jimi.secondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(10.dp)
                )
            }

            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(entries) { e: FileTools.Entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.045f))
                            .clickable {
                                if (e.isDir) {
                                    path = e.path
                                    header = e.name
                                    entries = tools.list(e.path)
                                } else {
                                    note = tools.share(e.path)
                                }
                            }
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (e.isDir) "📁" else "📄", fontSize = 17.sp)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                e.name,
                                color = jimi.textPrimary,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                            Text(
                                if (e.isDir) "ফোল্ডার" else tools.humanSize(e.size),
                                color = jimi.textMuted,
                                fontSize = 10.sp
                            )
                        }
                        Text("→", color = jimi.textMuted, fontSize = 14.sp)
                    }
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "অন্য অ্যাপের প্রাইভেট ফোল্ডারে অ্যান্ড্রয়েড সরাসরি ঢুকতে দেয় না। " +
                            "Jimi-কে বলো «এই ফাইলটা ওখানে কপি করো» — সে Files অ্যাপ খুলে নিজেই করবে।",
                        color = jimi.textMuted,
                        fontSize = 10.5.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}
