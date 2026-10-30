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

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${kb.roundToInt()} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${mb.roundToInt()} MB"
        return String.format(Locale.US, "%.1f GB", mb / 1024.0)
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> "*/*"
    }
}
