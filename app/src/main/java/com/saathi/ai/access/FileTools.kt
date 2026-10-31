package com.saathi.ai.access

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * File manager super-powers without root.
 *
 * Direct /storage access is denied to normal apps on Android 10+, so Jimi works the
 * way a person would: it lists what it *can* see (its own sandbox + public media
 * folders), and for anything else it drives the system Files app through the
 * accessibility service (long-press → copy → open folder → paste).
 *
 * Result: "এই ফাইলটা ডাউনলোডসে কপি করো" actually works.
 */
class FileTools(private val context: Context) {

    data class Entry(val name: String, val path: String, val size: Long, val isDir: Boolean)

    // ---------------------------------------------------------------- discovery

    /** Folders a normal app can read without extra permission. */
    fun visibleRoots(): List<File> = buildList {
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES))
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
        add(context.filesDir)
        add(context.cacheDir)
        add(context.getExternalFilesDir(null) ?: context.filesDir)
    }.filter { it != null && (it.exists() || it.parentFile?.exists() == true) }

    fun list(path: String?, limit: Int = 40): List<Entry> {
        val dir = resolveDir(path) ?: return emptyList()
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
        return children
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenByDescending { it.lastModified() })
            .take(limit)
            .map {
                Entry(
                    name = it.name,
                    path = it.absolutePath,
                    size = if (it.isFile) it.length() else 0L,
                    isDir = it.isDirectory
                )
            }
    }

    fun resolveDir(hint: String?): File? {
        if (hint.isNullOrBlank()) return visibleRoots().firstOrNull()
        val cleaned = hint.trim()
        // named shortcuts
        val shortcut = when (cleaned.lowercase(Locale.ROOT)) {
            "download", "downloads", "ডাউনলোড" ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            "dcim", "camera", "ক্যামেরা" ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            "pictures", "photos", "ছবি" ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            "movies", "video", "ভিডিও" ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            "music", "গান" ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            "documents", "ডকুমেন্ট" ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            else -> null
        }
        if (shortcut != null) return shortcut

        val direct = File(cleaned)
        if (direct.exists()) return direct

        // fuzzy: look for the name inside the public roots
        return visibleRoots().asSequence()
            .flatMap { root -> runCatching { root.listFiles()?.asSequence() ?: emptySequence() }.getOrDefault(emptySequence()) }
            .filter { it.isDirectory && it.name.contains(cleaned, ignoreCase = true) }
            .firstOrNull()
    }

    fun find(query: String, limit: Int = 12): List<Entry> {
        val hits = mutableListOf<Entry>()
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return hits
        for (root in visibleRoots()) {
            runCatching {
                root.walkTopDown().maxDepth(4).forEach { f ->
                    if (hits.size < limit && f.name.lowercase(Locale.ROOT).contains(needle)) {
                        hits.add(Entry(f.name, f.absolutePath, if (f.isFile) f.length() else 0L, f.isDirectory))
                    }
                }
            }
        }
        return hits.take(limit)
    }

    // ---------------------------------------------------------------- operations inside the sandbox

    fun copy(sourcePath: String, targetDirHint: String?): String {
        val src = File(sourcePath)
        if (!src.exists()) return "ফাইল পাওয়া যায়নি: $sourcePath"
        val targetDir = resolveDir(targetDirHint) ?: return "গন্তব্য ফোল্ডার পাওয়া যায়নি"
        return try {
            val out = File(targetDir, src.name)
            src.copyTo(out, overwrite = true)
            "কপি হয়েছে → ${out.absolutePath}"
        } catch (t: Throwable) {
            "কপি করা গেল না (${t.javaClass.simpleName}) — অ্যান্ড্রয়েড এই ফোল্ডারে সরাসরি লেখা বন্ধ রাখে, Files অ্যাপ দিয়ে করছি"
        }
    }

    fun move(sourcePath: String, targetDirHint: String?): String {
        val src = File(sourcePath)
        if (!src.exists()) return "ফাইল পাওয়া যায়নি"
        val targetDir = resolveDir(targetDirHint) ?: return "গন্তব্য ফোল্ডার পাওয়া যায়নি"
        return try {
            val out = File(targetDir, src.name)
            if (src.renameTo(out)) "সরানো হয়েছে → ${out.absolutePath}"
            else src.copyTo(out, overwrite = true).let {
                src.delete()
                "সরানো হয়েছে → ${out.absolutePath}"
            }
        } catch (t: Throwable) {
            "সরানো গেল না"
        }
    }

    fun delete(path: String): String {
        val f = File(path)
        return if (f.exists() && f.delete()) "মুছে ফেলেছি: ${f.name}" else "মোছা গেল না"
    }

    fun newFolder(parentHint: String?, name: String): String {
        val dir = resolveDir(parentHint) ?: return "ফোল্ডার পাওয়া যায়নি"
        return if (File(dir, name).mkdirs()) "ফোল্ডার তৈরি হয়েছে: $name" else "তৈরি করা গেল না"
    }

    fun info(path: String): String {
        val f = File(path)
        if (!f.exists()) return "নেই"
        val fmt = SimpleDateFormat("d MMM yyyy, hh:mm a", Locale("bn", "BD"))
        return buildString {
            append(f.name).append('\n')
            append("পাথ: ").append(f.absolutePath).append('\n')
            if (f.isFile) append("সাইজ: ").append(humanSize(f.length())).append('\n')
            append("পরিবর্তিত: ").append(fmt.format(Date(f.lastModified())))
        }
    }

    fun share(path: String): String {
        val f = File(path)
        if (!f.exists()) return "ফাইল নেই"
        return try {
            val uri: Uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                f
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = guessMime(f.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(send, "শেয়ার করো").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "শেয়ার শিট খুলেছি"
        } catch (t: Throwable) {
            "শেয়ার করা গেল না"
        }
    }

    /**
     * Opens a file **directly** — this is what the in-app manager was missing.
     *
     * Order of attempts, each one honest about its result:
     *  1. ACTION_VIEW with the exact MIME type through our FileProvider,
     *  2. the same with `*//*` for exotic types,
     *  3. ACTION_SEND chooser (some viewers only accept a share),
     *  4. the system Files app, so at least the folder is on screen.
     * A directory is opened as a folder view instead.
     */
    fun open(path: String): String {
        val f = File(path)
        if (!f.exists()) return "ফাইল পাওয়া যায়নি: $path"
        if (f.isDirectory) {
            val kids = f.listFiles()?.size ?: 0
            return openInFilesApp(f.absolutePath) + " (ফোল্ডার, $kids টি আইটেম)"
        }
        val uri: Uri = try {
            androidx.core.content.FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", f
            )
        } catch (t: Throwable) {
            Uri.fromFile(f)
        }
        val mime = guessMime(f.name)

        for (type in listOf(mime, "*/*")) {
            val ok = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, type)
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_ACTIVITY_NEW_TASK
                        )
                    }
                )
            }.isSuccess
            if (ok) return "খুলেছি: ${f.name} ($type)"
        }

        val shared = runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(send, "খোলো").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        return if (shared) "সরাসরি খোলার অ্যাপ নেই — কোন অ্যাপে খুলবে বেছে নাও: ${f.name}"
        else "এই ফাইল খোলা গেল না (${f.name}) — ফাইলের জন্য অ্যাপ ইনস্টল নেই। " +
            openInFilesApp(f.parent)
    }

    /** Opens the system Files app at a folder — then accessibility takes over. */
    fun openInFilesApp(hint: String?): String {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (hint != null) {
                resolveDir(hint)?.let { setDataAndType(Uri.fromFile(it), "resource/folder") }
            }
        }
        return try {
            context.startActivity(intent)
            "Files খুলেছি"
        } catch (t: Throwable) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), "*/*")
                )
                "Files খুলেছি"
            } catch (t2: Throwable) {
                "Files অ্যাপ খোলা গেল না"
            }
        }
    }

    /** Copy/paste driven entirely through the accessibility service. */
    fun copyViaUi(fileName: String, destinationFolder: String): String {
        val svc = JimiAccessibilityService.instance ?: return "Accessibility বন্ধ"
        val g = svc.gestures
        val b = svc.boundsOf(fileName) ?: return "স্ক্রিনে «$fileName» পাওয়া যায়নি"
        g.longPress(b.centerX().toFloat(), b.centerY().toFloat(), 700)
        Thread.sleep(600)
        if (svc.tapByText("Copy") || svc.tapByText("কপি")) {
            Thread.sleep(500)
            svc.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(500)
            if (svc.tapByText(destinationFolder)) {
                Thread.sleep(700)
                return if (svc.tapByText("Paste") || svc.tapByText("পেস্ট"))
                    "«$fileName» → «$destinationFolder» কপি করেছি"
                else "ফোল্ডারে ঢুকেছি, পেস্ট বোতাম পাইনি"
            }
            return "গন্তব্য ফোল্ডার পাইনি"
        }
        return "কপি মেনু খোলা গেল না"
    }

    /**
     * Writes text or code Jimi produced to a real file the user can open and share.
     *
     * "কোড লিখে দাও" only counts if the user actually ends up with the code, so this
     * saves it under Downloads/Jimi (created when missing) and reports the exact path
     * and size. If public storage is blocked it falls back to app storage and says so
     * plainly — no pretending the file exists when it does not.
     */
    fun writeText(fileName: String, content: String, shareIt: Boolean = false): String {
        val safe = fileName.trim().ifBlank { "jimi-note.txt" }
            .replace(Regex("[^A-Za-z0-9._\\-\\u0980-\\u09FF]"), "_")
        val name = if (safe.contains('.')) safe else "$safe.txt"

        val targets = buildList {
            resolveDir("downloads")?.let { add(File(it, "Jimi")) }
            context.getExternalFilesDir(null)?.let { add(File(it, "notes")) }
            add(File(context.filesDir, "notes"))
        }

        for (dir in targets) {
            try {
                dir.mkdirs()
                val f = File(dir, name)
                f.writeText(content)
                val where = "${f.absolutePath} (${humanSize(f.length())}, ${content.length} অক্ষর)"
                if (shareIt) {
                    val shared = share(f.absolutePath)
                    return "ফাইলে লিখে সেভ করেছি: $where — $shared"
                }
                return "ফাইলে লিখে সেভ করেছি: $where"
            } catch (t: Throwable) {
                // permission or path problem — try the next location
            }
        }
        return "ফাইলে লেখা যায়নি — জায়গা বা অনুমতি নেই"
    }

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${kb.roundToInt()} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${mb.roundToInt()} MB"
        return String.format(Locale.US, "%.1f GB", mb / 1024.0)
    }

    /** MIME by extension — drives both "open" and "share". */
    fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        "avi" -> "video/x-msvideo"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"
        "pdf" -> "application/pdf"
        "txt", "log", "md" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "html", "htm" -> "text/html"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        else -> "*/*"
    }
}
