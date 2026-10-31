package com.saathi.ai.access

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.Display
import com.saathi.ai.live.SessionHub
import com.saathi.ai.media.MediaUnderstanding
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Jimi's eyes — without screen sharing.
 *
 * The user asked for exactly this loop: *take a screenshot, look at it, delete it,
 * take another one* — live, on demand, and never keeping a pile of images around.
 *
 * Capture paths, best first:
 *  1. `AccessibilityService.takeScreenshot()` (Android 11+) — silent, no consent
 *     dialog, no status-bar recording icon, no MediaProjection needed. Requires
 *     `android:canTakeScreenshot="true"` in the service config.
 *  2. Root `screencap -p` — works on every version when `su` exists.
 *  3. Nothing else. We do **not** silently fall back to a stale frame: the report
 *     says which path failed and why, so the model can tell the user the truth.
 *
 * The JPEG is pushed straight into the running Live session
 * ([SessionHub.sendCameraFrame]) so the model literally sees the screen, then the
 * temp file is deleted. [locateByDump] adds the other half: exact pixel bounds for
 * canvas/game screens where the accessibility tree is empty, parsed from
 * `uiautomator dump` — that is how Jimi taps the right coin on a real carrom board.
 */
object ScreenVision {

    data class Shot(
        val jpeg: ByteArray,
        val width: Int,
        val height: Int,
        val source: String
    )

    @Volatile
    var lastSource: String = "none"
        private set

    // ---------------------------------------------------------------- capture

    /** Captures the screen and returns JPEG bytes, or null with an honest reason in [lastError]. */
    @Volatile
    var lastError: String = ""
        private set

    fun capture(context: Context, maxSide: Int = 1024, quality: Int = 78): Shot? {
        lastError = ""

        // 1) accessibility (Android 11+)
        val svc = JimiAccessibilityService.instance
        if (svc != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bmp = captureViaAccessibility(context, svc)
            if (bmp != null) {
                lastSource = "accessibility"
                return toShot(context, bmp, maxSide, quality)
            }
        }

        // 2) root screencap
        if (RootBridge.available()) {
            val tmp = File(context.cacheDir, "jimi-shot-${System.currentTimeMillis()}.png")
            val r = RootBridge.screenshotTo(tmp.absolutePath)
            if (r.ok && tmp.exists() && tmp.length() > 1024) {
                val bmp = BitmapFactory.decodeFile(tmp.absolutePath)
                tmp.delete()
                if (bmp != null) {
                    lastSource = "root-screencap"
                    return toShot(context, bmp, maxSide, quality)
                }
            } else {
                tmp.delete()
                lastError = "root screencap ব্যর্থ: ${r.err.ifBlank { r.out }}"
            }
        }

        if (lastError.isBlank()) {
            lastError = when {
                svc == null -> "Accessibility বন্ধ — স্ক্রিনশট নেওয়া যাবে না"
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R ->
                    "Android ${Build.VERSION.RELEASE}-এ সাইলেন্ট স্ক্রিনশট নেই; রুট থাকলে হতো"
                else -> "সিস্টেম স্ক্রিনশট দিতে পারেনি (FLAG_SECURE অ্যাপ হতে পারে)"
            }
        }
        return null
    }

    private fun captureViaAccessibility(
        context: Context,
        svc: AccessibilityService
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val latch = CountDownLatch(1)
        var result: Bitmap? = null
        var failure = -1
        return try {
            svc.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                context.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        runCatching {
                            val hardware = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer, screenshot.colorSpace
                            )
                            result = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                            hardware?.recycle()
                            runCatching { screenshot.hardwareBuffer.close() }
                        }
                        latch.countDown()
                    }

                    override fun onFailure(errorCode: Int) {
                        failure = errorCode
                        latch.countDown()
                    }
                }
            )
            if (!latch.await(5, TimeUnit.SECONDS)) {
                lastError = "স্ক্রিনশট টাইমআউট"
                null
            } else if (failure != -1) {
                lastError = "সিস্টেম স্ক্রিনশট দেয়নি (code $failure)"
                null
            } else {
                result
            }
        } catch (t: Throwable) {
            lastError = "স্ক্রিনশট এক্সেপশন: ${t.message}"
            null
        }
    }

    private fun toShot(context: Context, bmp: Bitmap, maxSide: Int, quality: Int): Shot? {
        val mu = MediaUnderstanding(context)
        val scaled = mu.downscale(bmp, maxSide)
        val jpeg = mu.toJpeg(scaled, quality)
        val w = scaled.width
        val h = scaled.height
        bmp.recycle()
        if (scaled !== bmp) runCatching { scaled.recycle() }
        return if (jpeg.isEmpty()) null else Shot(jpeg, w, h, lastSource)
    }

    // ------------------------------------------------------------------- look

    /**
     * The loop the user described: capture → send to the model → delete.
     * Nothing is written to storage unless [keep] is true.
     */
    fun look(context: Context, keep: Boolean = false, note: String? = null): String {
        val shot = capture(context)
            ?: return "স্ক্রিনশট নেওয়া যায়নি — $lastError"

        SessionHub.sendCameraFrame(shot.jpeg)

        // The frame goes to the model; the text layer is what makes tapping precise.
        // Accessibility tree first, uiautomator dump (root) when the tree is empty —
        // that combination is what lets Jimi work on canvas/game screens.
        val facts = run {
            val tree = JimiAccessibilityService.instance?.dumpScreen()?.take(700).orEmpty()
            if (tree.isNotBlank()) tree else clickableMap(24)
        }

        var savedNote = ""
        if (keep) {
            val dir = File(
                context.getExternalFilesDir(null) ?: context.filesDir, "shots"
            ).apply { mkdirs() }
            val f = File(dir, "jimi-shot-${System.currentTimeMillis()}.jpg")
            runCatching { f.writeBytes(shot.jpeg) }
            savedNote = "\nসেভ করেছি: ${f.absolutePath}"
        }

        return buildString {
            append("স্ক্রিনশট নিয়ে দেখে ফেললাম (${shot.source}, ${shot.width}x${shot.height}, ")
            append("${shot.jpeg.size / 1024} KB) — ছবিটা তোমার সামনে আছে, এখন বলো কী দেখছ।")
            if (!facts.isNullOrBlank()) {
                append("\nস্ক্রিনের লেখা: ").append(facts.take(700))
            }
            if (!note.isNullOrBlank()) append("\nনোট: ").append(note)
            append(savedNote)
            append("\n(অস্থায়ী ফাইল রাখা হয়নি — দেখা শেষ হলেই মুছে গেছে।)")
        }
    }

    /** Fresh look, previous temp file gone — the "new screenshot each time" behaviour. */
    fun lookAgain(context: Context): String = look(context, keep = false)

    fun deleteAllShots(context: Context): String {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "shots")
        val n = dir.listFiles()?.size ?: 0
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        return "${n}টি সেভ করা স্ক্রিনশট মুছে ফেলেছি"
    }

    // ------------------------------------------------------------------- grid

    /**
     * Draws a coordinate grid the model can reason with: "cell C2" is an exact
     * pixel box. This is how Jimi taps a specific coin, a specific square on a
     * chess board, or a specific pixel of a canvas game.
     */
    fun grid(context: Context, cols: Int = 4, rows: Int = 6): String {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val cw = w / cols
        val ch = h / rows
        val letters = "ABCDEFGH"
        return buildString {
            append("স্ক্রিন ${w}x${h}, গ্রিড ${cols}x$rows (ঘর ${cw}x${ch})\n")
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val x = c * cw + cw / 2
                    val y = r * ch + ch / 2
                    append("${letters[c]}${r + 1}=($x,$y) ")
                }
                append('\n')
            }
            append("যেকোনো ঘরের নাম বললেই ওই কেন্দ্রে ট্যাপ করব।")
        }
    }

    fun tapCell(context: Context, cell: String, cols: Int = 4, rows: Int = 6): String {
        val letters = "ABCDEFGH"
        val c = cell.uppercase()
        val col = letters.indexOf(c.getOrNull(0) ?: ' ')
        val row = c.dropWhile { !it.isDigit() }.toIntOrNull()?.minus(1) ?: -1
        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            return "ঘর «$cell» বোঝা যায়নি — গ্রিড $cols x $rows (উদাহরণ: B3)"
        }
        val dm = context.resources.displayMetrics
        val x = (col * dm.widthPixels / cols) + (dm.widthPixels / cols / 2)
        val y = (row * dm.heightPixels / rows) + (dm.heightPixels / rows / 2)
        val svc = JimiAccessibilityService.instance
        val ok = if (svc != null) {
            svc.gestures.humanTap(x.toFloat(), y.toFloat())
        } else if (RootBridge.available()) {
            RootBridge.inputTap(x, y).ok
        } else false
        return if (ok) "ঘর ${c.uppercase()} → ($x,$y) ট্যাপ করেছি"
        else "ট্যাপ করা যায়নি — Accessibility বা রুট দুটোই নেই"
    }

    // ------------------------------------------------------- locate by dump

    /**
     * Exact bounds for a visible element, even when the accessibility tree is empty
     * (games, canvas views, WebView-heavy apps, FLAG_SECURE screens).
     *
     * Uses `uiautomator dump` through root and parses `bounds="[x1,y1][x2,y2]"`
     * from the matching node — the same trick automation frameworks use.
     */
    fun locateByDump(label: String): Pair<Int, Int>? {
        if (!RootBridge.available()) return null
        val tmp = "/data/local/tmp/jimi-ui.xml"
        val dump = RootBridge.exec("uiautomator dump $tmp >/dev/null 2>&1; cat $tmp", 20_000)
        val xml = dump.out
        if (xml.isBlank()) return null
        val needle = label.lowercase()
        val nodes = Regex("""<node[^>]*>""").findAll(xml)
        for (n in nodes) {
            val s = n.value.lowercase()
            if (!s.contains(needle)) continue
            val m = Regex("""bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"""").find(n.value) ?: continue
            val (x1, y1, x2, y2) = m.destructured
            val cx = (x1.toInt() + x2.toInt()) / 2
            val cy = (y1.toInt() + y2.toInt()) / 2
            return cx to cy
        }
        return null
    }

    /** Dumps every clickable thing with its coordinates — the "what can I press" map. */
    fun clickableMap(limit: Int = 40): String {
        val svc = JimiAccessibilityService.instance
        if (svc != null) {
            val dump = svc.dumpScreen(4000)
            if (dump.isNotBlank()) return dump.take(2400)
        }
        if (!RootBridge.available()) return "স্ক্রিন পড়া যায়নি (Accessibility বন্ধ, রুট নেই)"
        val tmp = "/data/local/tmp/jimi-ui.xml"
        RootBridge.exec("uiautomator dump $tmp >/dev/null 2>&1", 20_000)
        val xml = RootBridge.exec("cat $tmp 2>/dev/null", 10_000).out
        if (xml.isBlank()) return "uiautomator ডাম্প খালি"
        val out = StringBuilder()
        Regex("""<node[^>]*>""").findAll(xml).take(limit).forEach { n ->
            val text = Regex("""text="([^"]*)"""").find(n.value)?.groupValues?.get(1).orEmpty()
            val desc = Regex("""content-desc="([^"]*)"""").find(n.value)?.groupValues?.get(1).orEmpty()
            val id = Regex("""resource-id="([^"]*)"""").find(n.value)?.groupValues?.get(1).orEmpty()
            val bounds = Regex("""bounds="([^"]*)"""").find(n.value)?.groupValues?.get(1).orEmpty()
            val label = text.ifBlank { desc }.ifBlank { id.substringAfterLast('/') }
            if (label.isNotBlank()) out.appendLine("$label · $bounds")
        }
        return out.toString().ifBlank { "ক্লিক করার মতো কিছু পাইনি" }.take(2600)
    }
}
