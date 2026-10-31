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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.access.FileTools
import com.saathi.ai.ui.theme.jimi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Jimi's own file manager — the one that was "ঠিকমতো কাজ করছে না".
 *
 * Three fixes that matter:
 *  • **Listing happens on IO**, never on the composition thread, so opening the
 *    screen does not stutter even with thousands of files.
 *  • **Tapping a file opens it** (ACTION_VIEW through our FileProvider with the right
 *    MIME type), instead of only offering to share it.
 *  • Every action reports what really happened in the banner, including the failure
 *    reason, so nothing looks done when it is not.
 *
 * Folders Android hides from us are still reachable through the system Files app, and
 * the screen says so rather than showing an empty list.
 */
@Composable
fun FilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val tools = remember { FileTools(context) }
    val scope = rememberCoroutineScope()

    var path by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<FileTools.Entry>>(emptyList()) }
    var roots by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var newFolderMode by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var folderName by remember { mutableStateOf("") }

    // Folders are read off the main thread; the screen paints instantly and fills in.
    LaunchedEffect(Unit) {
        roots = withContext(Dispatchers.IO) { tools.visibleRoots() }
    }
    LaunchedEffect(path, query, refreshTick) {
        loading = true
        val list = withContext(Dispatchers.IO) {
            if (query.isBlank()) tools.list(path, 500) else tools.find(query, 250)
        }
        entries = list
        loading = false
    }

    fun act(block: () -> String) {
        scope.launch {
            val out = withContext(Dispatchers.IO) { runCatching { block() }.getOrElse { "ব্যর্থ: ${it.message}" } }
            banner = out
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
                    .padding(horizontal = 16.dp)
            ) {
                // ------------------------------------------------ header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                when {
                                    query.isNotBlank() -> query = ""
                                    path != null -> path = java.io.File(path!!).parent
                                    else -> onBack()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SaathiIcons.ArrowBack, "ফিরে", tint = jimi.textPrimary, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ফাইল", color = jimi.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = when {
                                query.isNotBlank() -> "অনুসন্ধান: $query"
                                path == null -> "জিমির নিজের ম্যানেজার · ফাইলে ট্যাপ করলেই খুলবে"
                                else -> path!!
                            },
                            color = jimi.textMuted, fontSize = 10.5.sp, maxLines = 1
                        )
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { act { tools.openInFilesApp(path) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SaathiIcons.FolderOpen, "সিস্টেম ফাইলস", tint = jimi.secondary, modifier = Modifier.size(17.dp))
                    }
                }

                // ------------------------------------------------ search
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("নাম লিখে খোঁজো…", color = jimi.textMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(SaathiIcons.Search, null, tint = jimi.textMuted, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            Box(Modifier.clickable { query = "" }.padding(6.dp)) {
                                Icon(SaathiIcons.Close, "মুছো", tint = jimi.textMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = jimi.textPrimary,
                        unfocusedTextColor = jimi.textPrimary,
                        focusedBorderColor = jimi.secondary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = jimi.secondary
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(Modifier.height(8.dp))

                // ------------------------------------------------ quick roots
                if (query.isBlank()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(roots) { r ->
                            val active = path == r.absolutePath
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (active) jimi.secondary.copy(alpha = 0.22f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                                    .border(
                                        1.dp,
                                        if (active) jimi.secondary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.09f),
                                        RoundedCornerShape(50)
                                    )
                                    .clickable { path = r.absolutePath }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    SaathiIcons.Folder, null,
                                    tint = if (active) jimi.secondary else jimi.textMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    r.name.ifBlank { "storage" },
                                    color = if (active) jimi.textPrimary else jimi.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ------------------------------------------------ banner
                banner?.let { msg ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(jimi.secondary.copy(alpha = 0.12f))
                            .border(1.dp, jimi.secondary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(SaathiIcons.Check, null, tint = jimi.secondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = jimi.textPrimary.copy(alpha = 0.9f), fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                        Box(Modifier.clickable { banner = null }.padding(4.dp)) {
                            Icon(SaathiIcons.Close, null, tint = jimi.textMuted, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ------------------------------------------------ list
                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 26.dp), contentAlignment = Alignment.Center) {
                        Text("পড়া হচ্ছে…", color = jimi.textMuted, fontSize = 12.sp)
                    }
                } else if (entries.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 26.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(SaathiIcons.Folder, null, tint = jimi.textMuted, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (query.isBlank()) "এই ফোল্ডারে কিছু নেই, অথবা অ্যান্ড্রয়েড পড়তে দিচ্ছে না"
                                else "এই নামে কিছু পাওয়া যায়নি",
                                color = jimi.textMuted, fontSize = 11.5.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(entries, key = { it.path }) { e: FileTools.Entry ->
                            val open = expanded == e.path
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (open) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.05f)
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (e.isDir) {
                                                expanded = null
                                                path = e.path
                                            } else {
                                                act { tools.open(e.path) }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(jimi.primary.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(iconFor(e), null, tint = jimi.secondary, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            e.name, color = jimi.textPrimary, fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium, maxLines = 1
                                        )
                                        Text(
                                            if (e.isDir) "ফোল্ডার" else tools.humanSize(e.size),
                                            color = jimi.textMuted, fontSize = 10.sp
                                        )
                                    }
                                    Box(
                                        Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.06f))
                                            .clickable { expanded = if (open) null else e.path },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (open) SaathiIcons.Close else SaathiIcons.OpenInNew,
                                            "অ্যাকশন",
                                            tint = jimi.textMuted,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                if (open) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        ActionPill(SaathiIcons.Play, "খোলো") { act { tools.open(e.path) } }
                                        ActionPill(SaathiIcons.Share, "শেয়ার") { act { tools.share(e.path) } }
                                        ActionPill(SaathiIcons.Doc, "তথ্য") { act { tools.info(e.path) } }
                                        ActionPill(SaathiIcons.Trash, "মুছো") {
                                            act { tools.delete(e.path) }
                                            expanded = null
                                            refreshTick++
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(96.dp)) }
                    }
                }
            }

            // ------------------------------------------------ side rail
            SideRail(
                sections = listOf("root") + roots.map { it.name },
                active = (roots.indexOfFirst { it.absolutePath == path } + 1).coerceAtLeast(0),
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }

        // ------------------------------------------------ new folder sheet
        if (newFolderMode) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { newFolderMode = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier
                        .padding(26.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(jimi.bgMid)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                        .padding(18.dp)
                ) {
                    Text("নতুন ফোল্ডার", color = jimi.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        singleLine = true,
                        placeholder = { Text("নাম", color = jimi.textMuted, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = jimi.textPrimary,
                            unfocusedTextColor = jimi.textPrimary,
                            focusedBorderColor = jimi.secondary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            cursorColor = jimi.secondary
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    GradientButton(
                        text = "তৈরি করো",
                        icon = SaathiIcons.Add,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (folderName.isNotBlank()) {
                            act { tools.newFolder(path, folderName.trim()) }
                            folderName = ""
                            newFolderMode = false
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ bottom dock
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            DockPill(SaathiIcons.Add, "ফোল্ডার", Modifier.weight(1f)) { newFolderMode = true }
            DockPill(SaathiIcons.Save, "টেক্সট সেভ", Modifier.weight(1f)) {
                act { tools.writeText("jimi-note-${System.currentTimeMillis()}.txt", "জিমির নোট\n") }
            }
            DockPill(SaathiIcons.FolderOpen, "সিস্টেম ফাইলস", Modifier.weight(1.2f)) {
                act { tools.openInFilesApp(path) }
            }
        }
    }
}

private fun iconFor(e: FileTools.Entry): ImageVector {
    if (e.isDir) return SaathiIcons.Folder
    return when (e.name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp" -> SaathiIcons.Gallery
        "mp4", "mkv", "webm", "3gp", "avi" -> SaathiIcons.Videocam
        "mp3", "m4a", "wav", "ogg", "opus", "flac", "aac" -> SaathiIcons.KeyboardVoice
        "apk", "zip" -> SaathiIcons.Save
        "pdf", "doc", "docx", "txt", "md", "csv", "json" -> SaathiIcons.Doc
        else -> SaathiIcons.Doc
    }
}

@Composable
private fun ActionPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = jimi.secondary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = jimi.textPrimary.copy(alpha = 0.9f), fontSize = 11.sp)
    }
}

@Composable
private fun DockPill(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = jimi.secondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = jimi.textPrimary.copy(alpha = 0.9f), fontSize = 11.sp, maxLines = 1)
    }
}
