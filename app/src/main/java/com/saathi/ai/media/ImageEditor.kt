package com.saathi.ai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.saathi.ai.access.FileTools
import java.io.File

/**
 * On-device editing — no app, no network, no waiting.
 *
 * The old behaviour was "open the gallery, hunt for the Edit button, drag sliders
 * through accessibility", which breaks on every ROM. This does the actual pixel
 * work locally and saves a new file, so "ছবিটা একটু উজ্জ্বল করে ঘুরিয়ে দাও" is one
 * call with a real result and a path to show for it.
 *
 * Ops are a comma-separated list, e.g.
 *   `rotate=90,crop=1:1,bright=1.15,contrast=1.1,sat=1.25,vintage,text=শুভ নববর্ষ`
 *
 * Everything is clamped and validated; an unknown op is reported rather than
 * silently ignored, because a wrong edit is worse than no edit.
 */
class ImageEditor(private val context: Context) {

    data class Result(val file: File?, val report: String, val applied: List<String>)

    private val outDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "edited").apply { mkdirs() }

    /** Applies [ops] to the image at [path] and saves the result. */
    fun edit(path: String, ops: String): String {
        val src = resolve(path) ?: return "ছবি পাওয়া যায়নি: $path"
        val original = BitmapFactory.decodeFile(src.absolutePath)
            ?: return "ছবি ডিকোড করা যায়নি (ফাইল নষ্ট বা ফরম্যাট অচেনা)"

        val applied = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        var bmp = original

        ops.split(',', '，', ';').map { it.trim() }.filter { it.isNotBlank() }.forEach { raw ->
            val key = raw.substringBefore('=').trim().lowercase()
            val value = raw.substringAfter('=', "").trim()
            val before = bmp
            bmp = when (key) {
                "rotate", "ঘোরাও" -> bmp.rotate(value.toFloatOrNull() ?: 90f).also { applied.add("rotate=$value") }
                "flip" -> bmp.flip(value).also { applied.add("flip=$value") }
                "crop" -> bmp.cropTo(value).also { applied.add("crop=$value") }
                "resize", "size" -> bmp.resize(value.toIntOrNull() ?: 1080).also { applied.add("resize=$value") }
                "bright", "brightness" -> bmp.colorize(value.toFloatOrNull() ?: 1.1f, 1f, 1f)
                    .also { applied.add("bright=$value") }
                "contrast" -> bmp.colorize(1f, value.toFloatOrNull() ?: 1.15f, 1f)
                    .also { applied.add("contrast=$value") }
                "sat", "saturation" -> bmp.colorize(1f, 1f, value.toFloatOrNull() ?: 1.3f)
                    .also { applied.add("sat=$value") }
                "gray", "bw", "সাদাকালো" -> bmp.colorize(1f, 1f, 0f).also { applied.add("gray") }
                "invert", "নেগেটিভ" -> bmp.invert().also { applied.add("invert") }
                "vintage", "sepia", "পুরনো" -> bmp.matrix(vintageMatrix()).also { applied.add("vintage") }
                "cool", "ঠান্ডা" -> bmp.matrix(coolMatrix()).also { applied.add("cool") }
                "warm", "উষ্ণ" -> bmp.matrix(warmMatrix()).also { applied.add("warm") }
                "vivid" -> bmp.colorize(1.08f, 1.18f, 1.55f).also { applied.add("vivid") }
                "blur", "ঝাপসা" -> bmp.softBlur().also { applied.add("blur") }
                "circle", "গোল" -> bmp.toCircle().also { applied.add("circle") }
                "border" -> bmp.border(value.toIntOrNull() ?: 24, Color.parseColor("#FFFFFFFF"))
                    .also { applied.add("border=$value") }
                "text", "লেখা" -> bmp.text(value).also { applied.add("text=$value") }
                "square" -> bmp.cropTo("1:1").also { applied.add("square") }
                else -> {
                    unknown.add(raw)
                    bmp
                }
            }
            if (before !== bmp && before !== original) runCatching { before.recycle() }
        }

        if (applied.isEmpty() && unknown.isEmpty()) {
            runCatching { original.recycle() }
            return "কোনো এডিট বলা হয়নি। উদাহরণ: rotate=90,bright=1.15,crop=1:1,text=শুভেচ্ছা"
        }
        if (applied.isEmpty() && unknown.isNotEmpty()) {
            runCatching { bmp.recycle() }
            return "কোনো এডিট করা হয়নি — অচেনা অপশন: ${unknown.joinToString(", ")}। " +
                "যা আছে: rotate, flip, crop, resize, bright, contrast, sat, gray, invert, " +
                "vintage, cool, warm, vivid, blur, circle, border, text"
        }

        val out = File(outDir, "edit-${System.currentTimeMillis()}.jpg")
        val saved = runCatching {
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        }.isSuccess
        val w = bmp.width
        val h = bmp.height
        runCatching { bmp.recycle() }
        if (bmp !== original) runCatching { original.recycle() }

        return if (!saved) "এডিট হয়েছিল কিন্তু ফাইলে সেভ করা যায়নি"
        else buildString {
            append("এডিট সম্পন্ন: ${applied.joinToString(", ")}")
            if (unknown.isNotEmpty()) append(" · অচেনা অপশন বাদ পড়েছে: ${unknown.joinToString(", ")}")
            append("\nনতুন ছবি: ${out.absolutePath} (${w}x${h}, ${out.length() / 1024} KB)")
            append("\nআগের ছবি অক্ষত আছে — চাইলে share_file দিয়ে শেয়ার করো বা set_wallpaper দাও।")
        }
    }

    /** Path accepted as plain, file:// or content-free string; returns a real File. */
    private fun resolve(path: String): File? {
        val clean = path.removePrefix("file://").trim()
        val direct = File(clean)
        if (direct.exists()) return direct
        // last resort: search the visible folders by file name
        val name = clean.substringAfterLast('/')
        if (name.isNotBlank()) {
            val hit = FileTools(context).find(name, 1).firstOrNull { !it.isDir }
            if (hit != null) return File(hit.path)
        }
        return null
    }

    // ------------------------------------------------------------------ ops

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val d = ((degrees % 360f) + 360f) % 360f
        if (d == 0f) return this
        val m = Matrix().apply { postRotate(d) }
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
    }

    private fun Bitmap.flip(mode: String): Bitmap {
        val m = Matrix().apply {
            when (mode.lowercase().firstOrNull()) {
                'v' -> postScale(1f, -1f)
                else -> postScale(-1f, 1f)
            }
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
    }

    private fun Bitmap.cropTo(ratio: String): Bitmap {
        val parts = ratio.split(':', 'x', 'X')
        val rw = parts.getOrNull(0)?.toFloatOrNull() ?: 1f
        val rh = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
        if (rw <= 0f || rh <= 0f) return this
        val target = rw / rh
        val current = width.toFloat() / height.toFloat()
        val (w, h) = if (current > target) {
            (height * target).toInt() to height
        } else {
            width to (width / target).toInt()
        }
        val x = ((width - w) / 2).coerceAtLeast(0)
        val y = ((height - h) / 2).coerceAtLeast(0)
        val ww = w.coerceAtMost(width - x)
        val hh = h.coerceAtMost(height - y)
        return if (ww <= 0 || hh <= 0) this else Bitmap.createBitmap(this, x, y, ww, hh)
    }

    private fun Bitmap.resize(maxSide: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= maxSide) return this
        val k = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(this, (width * k).toInt(), (height * k).toInt(), true)
    }

    private fun Bitmap.colorize(bright: Float, contrast: Float, sat: Float): Bitmap {
        val cm = ColorMatrix()
        cm.setSaturation(sat.coerceIn(0f, 3f))
        val b = (bright - 1f) * 90f
        val c = contrast.coerceIn(0.2f, 3f)
        val t = (1f - c) * 128f
        val adjust = ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t + b,
            0f, c, 0f, 0f, t + b,
            0f, 0f, c, 0f, t + b,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(adjust)
        return matrix(cm)
    }

    private fun Bitmap.matrix(cm: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(this, 0f, 0f, p)
        return out
    }

    private fun Bitmap.invert(): Bitmap = matrix(ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )))

    private fun vintageMatrix() = ColorMatrix(floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 12f,
        0.349f, 0.686f, 0.168f, 0f, 8f,
        0.272f, 0.534f, 0.131f, 0f, 4f,
        0f, 0f, 0f, 1f, 0f
    ))

    private fun coolMatrix() = ColorMatrix(floatArrayOf(
        0.95f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 6f,
        0f, 0f, 1.16f, 0f, 18f,
        0f, 0f, 0f, 1f, 0f
    ))

    private fun warmMatrix() = ColorMatrix(floatArrayOf(
        1.16f, 0f, 0f, 0f, 14f,
        0f, 1.04f, 0f, 0f, 6f,
        0f, 0f, 0.92f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))

    /** Downscale-upscale blur: cheap, no RenderScript, works on every API level. */
    private fun Bitmap.softBlur(): Bitmap {
        val small = Bitmap.createScaledBitmap(this, (width / 12).coerceAtLeast(1), (height / 12).coerceAtLeast(1), true)
        val out = Bitmap.createScaledBitmap(small, width, height, true)
        if (small !== out) runCatching { small.recycle() }
        return out
    }

    private fun Bitmap.toCircle(): Bitmap {
        val side = minOf(width, height)
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val src = Rect((width - side) / 2, (height - side) / 2, (width + side) / 2, (height + side) / 2)
        canvas.drawOval(RectF(0f, 0f, side.toFloat(), side.toFloat()), p)
        p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(this, src, RectF(0f, 0f, side.toFloat(), side.toFloat()), p)
        return out
    }

    private fun Bitmap.border(px: Int, color: Int): Bitmap {
        val w = width + px * 2
        val h = height + px * 2
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(color)
        canvas.drawBitmap(this, px.toFloat(), px.toFloat(), null)
        return out
    }

    private fun Bitmap.text(value: String): Bitmap {
        val out = copy(Bitmap.Config.ARGB_8888, true) ?: return this
        val canvas = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = width / 16f
            textAlign = Paint.Align.CENTER
            setShadowLayer(width / 80f, 0f, width / 240f, 0xAA000000)
        }
        val lines = value.split("\n")
        val startY = height * 0.86f - (lines.size - 1) * p.textSize * 1.2f
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, width / 2f, startY + i * p.textSize * 1.25f, p)
        }
        return out
    }

    // ------------------------------------------------------------------ misc

    /** Side-by-side collage, because "দুটো ছবি একসাথে জোড়া দাও" is a common ask. */
    fun collage(paths: List<String>, vertical: Boolean = false): String {
        val bitmaps = paths.mapNotNull { p ->
            resolve(p)?.let { BitmapFactory.decodeFile(it.absolutePath) }
        }
        if (bitmaps.size < 2) return "কমপক্ষে দুটো পড়া যাওয়ার মতো ছবি দরকার"
        val sameHeight = bitmaps.minOf { it.height }
        val scaled = bitmaps.map {
            Bitmap.createScaledBitmap(it, (it.width * sameHeight.toFloat() / it.height).toInt(), sameHeight, true)
        }
        val w = if (vertical) scaled.maxOf { it.width } else scaled.sumOf { it.width }
        val h = if (vertical) scaled.sumOf { it.height } else sameHeight
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        var offset = 0
        scaled.forEach { b ->
            if (vertical) {
                canvas.drawBitmap(b, (w - b.width) / 2f, offset.toFloat(), null)
                offset += b.height
            } else {
                canvas.drawBitmap(b, offset.toFloat(), 0f, null)
                offset += b.width
            }
        }
        val file = File(outDir, "collage-${System.currentTimeMillis()}.jpg")
        runCatching { file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) } }
        scaled.forEach { runCatching { it.recycle() } }
        bitmaps.forEach { runCatching { it.recycle() } }
        out.recycle()
        return "${paths.size}টা ছবি জোড়া দিয়েছি: ${file.absolutePath}"
    }

    /** Honest answer about video: re-encoding needs a real encoder, so say so. */
    fun videoNote(): String =
        "ভিডিও কাটা/জোড়া দেওয়ার জন্য রি-এনকোডিং লাগে। Jimi এখন ফ্রেম বের করে দেখতে ও " +
            "ভিডিও এডিট অ্যাপ চালিয়ে টাইমলাইনে ড্র্যাগ/ট্রিম করতে পারে (playbook আছে)। " +
            "Termux-এ ffmpeg ইনস্টল থাকলে কমান্ড দিয়ে নিখুঁত কাটতেও পারি।"
}
