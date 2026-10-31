package com.saathi.ai.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Jimi's own drawing hand — offline, no API, no assets.
 *
 * When someone says "একটা সূর্যাস্তের ছবি আঁকো" and there is no network (or the image
 * model refuses), Jimi can still *actually draw*: everything here is generated with
 * Canvas paths and shaders at runtime. The prompt is parsed into a scene, the scene
 * is composed from layers (sky → sun/moon → hills → water → subjects → details →
 * texture), and randomness is seeded from the prompt so the same words give a
 * consistent picture while different wording gives a different one.
 *
 * This is also what makes "নিজেই ছবি আঁকতে পারবে" true without any emoji, sticker
 * or bundled PNG — pure code, which is what the user asked for.
 */
object ArtEngine {

    private const val SCENES = "sunset sun rise night moon star mountain hill river water house " +
        "tree flower face cat bird mosque mandala city rain boat heart flag pattern geometric " +
        "abstract aurora ocean beach road car bike fire flame snow winter"

    /** Draws and saves; returns an honest report with the file path. */
    fun drawAndSave(context: Context, prompt: String, size: Int = 1080): String {
        val bmp = draw(prompt, size)
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "art").apply { mkdirs() }
        val f = File(dir, "jimi-draw-${System.currentTimeMillis()}.png")
        val ok = runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) } }.isSuccess
        bmp.recycle()
        return if (ok) "নিজে হাতে এঁকেছি (${sceneOf(prompt)}, ${size}x${size}) — ${f.absolutePath}"
        else "আঁকা শেষ, কিন্তু ফাইলে সেভ করা যায়নি"
    }

    /** Parses the prompt into a scene key so the report can say what was drawn. */
    fun sceneOf(prompt: String): String {
        val p = prompt.lowercase()
        val words = SCENES.split(' ')
        return words.firstOrNull { p.contains(it) } ?: "abstract"
    }

    fun draw(prompt: String, size: Int = 1080): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = java.util.Random(prompt.hashCode().toLong())
        val scene = sceneOf(prompt)

        when (scene) {
            "night", "moon", "star" -> {
                sky(c, size, 0xFF04061A.toInt(), 0xFF1B2450.toInt(), 0xFF0A0E2A.toInt())
                stars(c, size, rnd, 150)
                moon(c, size, rnd)
                hills(c, size, rnd, 0xFF0B1030.toInt(), 3)
                fireflies(c, size, rnd)
            }
            "sunset", "sun", "rise" -> {
                sky(c, size, 0xFF2B0F3A.toInt(), 0xFFFF7A45.toInt(), 0xFFFFD166.toInt())
                sun(c, size, rnd, 0.62f)
                hills(c, size, rnd, 0xFF3A1230.toInt(), 3)
                water(c, size, rnd, 0.78f, 0xFFFF9E6B.toInt())
                birds(c, size, rnd, 5)
            }
            "mountain", "hill" -> {
                sky(c, size, 0xFF0E2A47.toInt(), 0xFF7FB2E5.toInt(), 0xFFDCE9F7.toInt())
                sun(c, size, rnd, 0.22f)
                mountains(c, size, rnd)
                water(c, size, rnd, 0.84f, 0xFF9CC6E8.toInt())
            }
            "river", "ocean", "beach", "water" -> {
                sky(c, size, 0xFF123A5A.toInt(), 0xFF8FD3F4.toInt(), 0xFFE8F7FF.toInt())
                sun(c, size, rnd, 0.25f)
                hills(c, size, rnd, 0xFF2E6B4F.toInt(), 2)
                water(c, size, rnd, 0.62f, 0xFF4FB8E8.toInt())
                boat(c, size, rnd)
            }
            "house" -> {
                sky(c, size, 0xFF2A6FA8.toInt(), 0xFF9ED8F5.toInt(), 0xFFE6F5FF.toInt())
                sun(c, size, rnd, 0.2f)
                grass(c, size, 0.72f)
                house(c, size, rnd)
                tree(c, size, rnd, 0.14f, 0.72f, 0.30f)
                tree(c, size, rnd, 0.88f, 0.74f, 0.22f)
                birds(c, size, rnd, 3)
            }
            "tree" -> {
                sky(c, size, 0xFF3E86C1.toInt(), 0xFFBFE6F7.toInt(), 0xFFEFF9FF.toInt())
                grass(c, size, 0.78f)
                tree(c, size, rnd, 0.5f, 0.78f, 0.62f)
                flowerBed(c, size, rnd)
            }
            "flower" -> {
                sky(c, size, 0xFF1F5F3F.toInt(), 0xFF79C79A.toInt(), 0xFFDFF7E6.toInt())
                flowers(c, size, rnd, 9)
            }
            "face" -> {
                gradientBg(c, size, 0xFF2C1B47.toInt(), 0xFF7A4FBF.toInt())
                face(c, size, rnd)
            }
            "cat" -> {
                gradientBg(c, size, 0xFF1B2A44.toInt(), 0xFF4E7CA8.toInt())
                cat(c, size, rnd)
            }
            "bird" -> {
                sky(c, size, 0xFF1D3E63.toInt(), 0xFF9FD0EE.toInt(), 0xFFEAF6FF.toInt())
                birds(c, size, rnd, 11)
                hills(c, size, rnd, 0xFF2A4A3A.toInt(), 2)
            }
            "mosque" -> {
                sky(c, size, 0xFF0B2B3A.toInt(), 0xFF2E8B8B.toInt(), 0xFFE3C07B.toInt())
                sun(c, size, rnd, 0.3f)
                mosque(c, size, rnd)
                stars(c, size, rnd, 40)
            }
            "mandala" -> {
                gradientBg(c, size, 0xFF10081F.toInt(), 0xFF2B1352.toInt())
                mandala(c, size, rnd)
            }
            "city" -> {
                sky(c, size, 0xFF0A1024.toInt(), 0xFF33224F.toInt(), 0xFF7A4A6B.toInt())
                stars(c, size, rnd, 60)
                skyline(c, size, rnd)
            }
            "rain", "snow", "winter" -> {
                sky(c, size, 0xFF2B3440.toInt(), 0xFF6B7A8A.toInt(), 0xFFAEBAC6.toInt())
                clouds(c, size, rnd)
                if (scene == "snow") snow(c, size, rnd) else rain(c, size, rnd)
                hills(c, size, rnd, 0xFF31413A.toInt(), 2)
            }
            "boat" -> {
                sky(c, size, 0xFF123A5A.toInt(), 0xFF8FD3F4.toInt(), 0xFFE8F7FF.toInt())
                water(c, size, rnd, 0.6f, 0xFF3FA9DC.toInt())
                boat(c, size, rnd)
            }
            "heart" -> {
                gradientBg(c, size, 0xFF3A0A22.toInt(), 0xFF8E1B4B.toInt())
                heart(c, size)
                sparkles(c, size, rnd)
            }
            "flag" -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = 0xFF006A4E.toInt()
                c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
                p.color = 0xFFF42A41.toInt()
                c.drawCircle(size * 0.45f, size * 0.5f, size * 0.18f, p)
            }
            "pattern", "geometric" -> {
                gradientBg(c, size, 0xFF0A0F22.toInt(), 0xFF1D2A55.toInt())
                tessellation(c, size, rnd)
            }
            "car", "road" -> {
                sky(c, size, 0xFF2A6FA8.toInt(), 0xFFBFE6F7.toInt(), 0xFFEFF9FF.toInt())
                grass(c, size, 0.62f)
                road(c, size)
                car(c, size, rnd)
            }
            "fire", "flame" -> {
                gradientBg(c, size, 0xFF180404.toInt(), 0xFF4A0E0A.toInt())
                flames(c, size, rnd)
                sparkles(c, size, rnd)
            }
            else -> {
                gradientBg(c, size, 0xFF070B1E.toInt(), 0xFF1B1140.toInt())
                aurora(c, size, rnd)
                orbs(c, size, rnd)
                sparkles(c, size, rnd)
            }
        }
        grain(c, size, rnd)
        return bmp
    }

    // ---------------------------------------------------------------- layers

    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.style = style }

    private fun sky(c: Canvas, s: Int, top: Int, mid: Int, bottom: Int) {
        val p = paint(0).apply {
            shader = LinearGradient(
                0f, 0f, 0f, s.toFloat(),
                intArrayOf(top, mid, bottom), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
    }

    private fun gradientBg(c: Canvas, s: Int, a: Int, b: Int) {
        val p = paint(0).apply {
            shader = LinearGradient(
                0f, 0f, s.toFloat(), s.toFloat(),
                intArrayOf(a, b), null, Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
    }

    private fun stars(c: Canvas, s: Int, rnd: java.util.Random, n: Int) {
        val p = paint(Color.WHITE)
        repeat(n) {
            val x = rnd.nextFloat() * s
            val y = rnd.nextFloat() * s * 0.7f
            val r = (rnd.nextFloat() * 2.2f + 0.6f) * s / 900f
            p.alpha = (90 + rnd.nextInt(165)).coerceAtMost(255)
            c.drawCircle(x, y, r, p)
        }
        p.alpha = 255
    }

    private fun moon(c: Canvas, s: Int, rnd: java.util.Random) {
        val cx = s * (0.2f + rnd.nextFloat() * 0.6f)
        val cy = s * (0.16f + rnd.nextFloat() * 0.18f)
        val r = s * 0.11f
        val glow = paint(0).apply {
            shader = RadialGradient(cx, cy, r * 3f,
                intArrayOf(0x66FFF6D5, 0x00FFF6D5), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, r * 3f, glow)
        c.drawCircle(cx, cy, r, paint(0xFFFFF3D0.toInt()))
        val crater = paint(0x22B9A97F)
        repeat(5) {
            c.drawCircle(
                cx + (rnd.nextFloat() - 0.5f) * r, cy + (rnd.nextFloat() - 0.5f) * r,
                r * (0.08f + rnd.nextFloat() * 0.14f), crater
            )
        }
    }

    private fun sun(c: Canvas, s: Int, rnd: java.util.Random, yFrac: Float) {
        val cx = s * (0.3f + rnd.nextFloat() * 0.4f)
        val cy = s * yFrac
        val r = s * 0.12f
        val glow = paint(0).apply {
            shader = RadialGradient(cx, cy, r * 4f,
                intArrayOf(0x88FFD166.toInt(), 0x22FF9E6B, 0x00FF7A45),
                floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, r * 4f, glow)
        c.drawCircle(cx, cy, r, paint(0xFFFFE9A8.toInt()))
    }

    private fun hills(c: Canvas, s: Int, rnd: java.util.Random, color: Int, layers: Int) {
        for (l in 0 until layers) {
            val base = s * (0.62f + l * 0.09f)
            val shade = Color.argb(
                255,
                (Color.red(color) * (1f - l * 0.16f)).toInt(),
                (Color.green(color) * (1f - l * 0.16f)).toInt(),
                (Color.blue(color) * (1f - l * 0.16f)).toInt()
            )
            val path = Path().apply { moveTo(0f, base) }
            var x = 0f
            while (x < s) {
                val w = s * (0.18f + rnd.nextFloat() * 0.2f)
                val h = s * (0.05f + rnd.nextFloat() * 0.10f) * (1f - l * 0.2f)
                path.quadTo(x + w / 2f, base - h, x + w, base)
                x += w
            }
            path.lineTo(s.toFloat(), s.toFloat())
            path.lineTo(0f, s.toFloat())
            path.close()
            c.drawPath(path, paint(shade))
        }
    }

    private fun mountains(c: Canvas, s: Int, rnd: java.util.Random) {
        for (layer in 0..2) {
            val base = s * (0.66f + layer * 0.06f)
            val tone = intArrayOf(0xFF31536E.toInt(), 0xFF26415A.toInt(), 0xFF1B2F45.toInt())[layer]
            val path = Path().apply { moveTo(0f, base) }
            var x = 0f
            while (x < s) {
                val w = s * (0.22f + rnd.nextFloat() * 0.18f)
                val h = s * (0.20f + rnd.nextFloat() * 0.16f) * (1f - layer * 0.22f)
                path.lineTo(x + w / 2f, base - h)
                path.lineTo(x + w, base)
                x += w
            }
            path.lineTo(s.toFloat(), s.toFloat())
            path.lineTo(0f, s.toFloat())
            path.close()
            c.drawPath(path, paint(tone))
            // snow caps
            val cap = paint(0xE6FFFFFF.toInt())
            var cx2 = 0f
            val rnd2 = java.util.Random(path.hashCode().toLong())
            while (cx2 < s) {
                val w = s * 0.3f
                val h = s * 0.18f * (1f - layer * 0.22f)
                val tipX = cx2 + w / 2f
                val tipY = base - h
                if (rnd2.nextBoolean()) {
                    val pth = Path().apply {
                        moveTo(tipX, tipY)
                        lineTo(tipX + w * 0.10f, tipY + h * 0.34f)
                        lineTo(tipX - w * 0.10f, tipY + h * 0.34f)
                        close()
                    }
                    c.drawPath(pth, cap)
                }
                cx2 += w
            }
        }
    }

    private fun water(c: Canvas, s: Int, rnd: java.util.Random, yFrac: Float, tone: Int) {
        val top = s * yFrac
        val p = paint(0).apply {
            shader = LinearGradient(0f, top, 0f, s.toFloat(),
                intArrayOf(tone, 0xFF08243A.toInt()), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, top, s.toFloat(), s.toFloat(), p)
        val line = paint(0x55FFFFFF, Paint.Style.STROKE).apply { strokeWidth = s / 420f }
        repeat(26) {
            val y = top + rnd.nextFloat() * (s - top)
            val x = rnd.nextFloat() * s
            val w = s * (0.05f + rnd.nextFloat() * 0.16f)
            c.drawLine(x, y, x + w, y, line)
        }
    }

    private fun grass(c: Canvas, s: Int, yFrac: Float) {
        val top = s * yFrac
        val p = paint(0).apply {
            shader = LinearGradient(0f, top, 0f, s.toFloat(),
                intArrayOf(0xFF3E8E5A.toInt(), 0xFF1D4A31.toInt()), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, top, s.toFloat(), s.toFloat(), p)
    }

    private fun house(c: Canvas, s: Int, rnd: java.util.Random) {
        val w = s * 0.42f
        val h = s * 0.24f
        val left = s * 0.29f
        val top = s * 0.50f
        c.drawRect(left, top, left + w, top + h, paint(0xFFE8D9C0.toInt()))
        val roof = Path().apply {
            moveTo(left - w * 0.08f, top)
            lineTo(left + w / 2f, top - h * 0.62f)
            lineTo(left + w * 1.08f, top)
            close()
        }
        c.drawPath(roof, paint(0xFF9C3B2E.toInt()))
        c.drawRect(left + w * 0.40f, top + h * 0.42f, left + w * 0.60f, top + h, paint(0xFF6B3B22.toInt()))
        val win = paint(0xFFBFE6F7.toInt())
        c.drawRect(left + w * 0.10f, top + h * 0.20f, left + w * 0.28f, top + h * 0.42f, win)
        c.drawRect(left + w * 0.72f, top + h * 0.20f, left + w * 0.90f, top + h * 0.42f, win)
        c.drawRect(left + w * 0.78f, top - h * 0.30f, left + w * 0.86f, top - h * 0.02f, paint(0xFF7A4A3A.toInt()))
        // smoke
        val smoke = paint(0x55FFFFFF)
        repeat(4) { i ->
            c.drawCircle(left + w * 0.82f + rnd.nextFloat() * 8f, top - h * (0.4f + i * 0.12f), s * 0.012f * (i + 1), smoke)
        }
    }

    private fun tree(c: Canvas, s: Int, rnd: java.util.Random, xFrac: Float, baseY: Float, scale: Float) {
        val x = s * xFrac
        val base = s * baseY
        val trunkH = s * scale * 0.42f
        val trunkW = s * scale * 0.09f
        c.drawRect(x - trunkW / 2f, base - trunkH, x + trunkW / 2f, base, paint(0xFF6B4326.toInt()))
        val leafColors = intArrayOf(0xFF2F7D46.toInt(), 0xFF3E9C57.toInt(), 0xFF57B86E.toInt())
        repeat(5) { i ->
            val r = s * scale * (0.22f - i * 0.022f)
            val cx = x + (rnd.nextFloat() - 0.5f) * s * scale * 0.20f
            val cy = base - trunkH - r * (0.55f + i * 0.28f)
            c.drawCircle(cx, cy, r, paint(leafColors[i % leafColors.size]))
        }
    }

    private fun flowers(c: Canvas, s: Int, rnd: java.util.Random, n: Int) {
        repeat(n) {
            val cx = s * (0.1f + rnd.nextFloat() * 0.8f)
            val cy = s * (0.3f + rnd.nextFloat() * 0.6f)
            val r = s * (0.035f + rnd.nextFloat() * 0.05f)
            val petal = intArrayOf(0xFFFF6F91.toInt(), 0xFFFFD166.toInt(), 0xFFB388FF.toInt(),
                0xFFFF8A65.toInt(), 0xFFF8BBD0.toInt())[rnd.nextInt(5)]
            val stem = paint(0xFF2F7D46.toInt(), Paint.Style.STROKE).apply { strokeWidth = r * 0.22f }
            c.drawLine(cx, cy, cx + (rnd.nextFloat() - 0.5f) * r, cy + r * 2.6f, stem)
            repeat(6) { k ->
                val a = k * 60f * Math.PI.toFloat() / 180f
                c.drawCircle(cx + cos(a) * r * 0.8f, cy + sin(a) * r * 0.8f, r * 0.62f, paint(petal))
            }
            c.drawCircle(cx, cy, r * 0.5f, paint(0xFFFFF3C4.toInt()))
        }
    }

    private fun flowerBed(c: Canvas, s: Int, rnd: java.util.Random) {
        repeat(7) {
            val cx = s * (0.06f + rnd.nextFloat() * 0.88f)
            val cy = s * (0.84f + rnd.nextFloat() * 0.10f)
            val r = s * 0.022f
            repeat(5) { k ->
                val a = k * 72f * Math.PI.toFloat() / 180f
                c.drawCircle(cx + cos(a) * r, cy + sin(a) * r, r * 0.7f, paint(0xFFFF6F91.toInt()))
            }
            c.drawCircle(cx, cy, r * 0.55f, paint(0xFFFFE082.toInt()))
        }
    }

    private fun birds(c: Canvas, s: Int, rnd: java.util.Random, n: Int) {
        val p = paint(0xCC111820.toInt(), Paint.Style.STROKE).apply {
            strokeWidth = s / 320f; strokeCap = Paint.Cap.ROUND
        }
        repeat(n) {
            val x = s * (0.1f + rnd.nextFloat() * 0.8f)
            val y = s * (0.1f + rnd.nextFloat() * 0.35f)
            val w = s * (0.03f + rnd.nextFloat() * 0.03f)
            val path = Path().apply {
                moveTo(x - w, y)
                quadTo(x - w / 2f, y - w * 0.6f, x, y)
                quadTo(x + w / 2f, y - w * 0.6f, x + w, y)
            }
            c.drawPath(path, p)
        }
    }

    private fun mosque(c: Canvas, s: Int, rnd: java.util.Random) {
        val base = s * 0.82f
        val bodyW = s * 0.46f
        val left = s * 0.27f
        c.drawRect(left, base - s * 0.22f, left + bodyW, base, paint(0xFFF2E7D0.toInt()))
        val dome = paint(0xFFE0C88A.toInt())
        val domeR = bodyW * 0.42f
        c.drawCircle(left + bodyW / 2f, base - s * 0.22f, domeR, dome)
        c.drawRect(left + bodyW / 2f - s * 0.006f, base - s * 0.22f - domeR - s * 0.06f,
            left + bodyW / 2f + s * 0.006f, base - s * 0.22f - domeR, paint(0xFFC8A45C.toInt()))
        // minarets
        for (side in listOf(-1f, 1f)) {
            val mx = left + bodyW / 2f + side * bodyW * 0.72f
            c.drawRect(mx - s * 0.02f, base - s * 0.38f, mx + s * 0.02f, base, paint(0xFFF2E7D0.toInt()))
            c.drawCircle(mx, base - s * 0.38f, s * 0.026f, paint(0xFFE0C88A.toInt()))
        }
        // arch door
        val door = Path().apply {
            val dx = left + bodyW / 2f
            val dw = bodyW * 0.16f
            moveTo(dx - dw, base)
            lineTo(dx - dw, base - s * 0.10f)
            quadTo(dx, base - s * 0.19f, dx + dw, base - s * 0.10f)
            lineTo(dx + dw, base)
            close()
        }
        c.drawPath(door, paint(0xFF6B4A2A.toInt()))
        c.drawRect(0f, base, s.toFloat(), s.toFloat(), paint(0xFFBFA77A.toInt()))
    }

    private fun mandala(c: Canvas, s: Int, rnd: java.util.Random) {
        val cx = s / 2f
        val cy = s / 2f
        val rings = 7
        val petals = 12 + rnd.nextInt(8)
        for (r in rings downTo 1) {
            val radius = s * 0.06f * r
            val hue = (r * 42 + rnd.nextInt(30)) % 360
            val col = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.62f, 0.95f))
            val p = paint(col, Paint.Style.STROKE).apply { strokeWidth = s / 340f }
            c.drawCircle(cx, cy, radius, p)
            for (k in 0 until petals) {
                val a = (k * 360f / petals) * Math.PI.toFloat() / 180f
                val x = cx + cos(a) * radius
                val y = cy + sin(a) * radius
                c.drawCircle(x, y, s * 0.012f * (rings - r + 1) / 2f, paint(col))
            }
        }
        c.drawCircle(cx, cy, s * 0.035f, paint(0xFFFFE082.toInt()))
    }

    private fun skyline(c: Canvas, s: Int, rnd: java.util.Random) {
        var x = 0f
        while (x < s) {
            val w = s * (0.05f + rnd.nextFloat() * 0.09f)
            val h = s * (0.18f + rnd.nextFloat() * 0.42f)
            val top = s - h
            c.drawRect(x, top, x + w, s.toFloat(), paint(0xFF141B2E.toInt()))
            val win = paint(0xFFFFD88A.toInt())
            var wy = top + s * 0.02f
            while (wy < s - s * 0.02f) {
                var wx = x + w * 0.18f
                while (wx < x + w * 0.82f) {
                    if (rnd.nextFloat() > 0.42f) c.drawRect(wx, wy, wx + w * 0.14f, wy + s * 0.012f, win)
                    wx += w * 0.24f
                }
                wy += s * 0.028f
            }
            x += w
        }
    }

    private fun clouds(c: Canvas, s: Int, rnd: java.util.Random) {
        val p = paint(0x99B8C4CE.toInt())
        repeat(5) {
            val cx = rnd.nextFloat() * s
            val cy = s * (0.08f + rnd.nextFloat() * 0.28f)
            repeat(5) { k ->
                c.drawCircle(cx + k * s * 0.045f - s * 0.09f, cy + (k % 2) * s * 0.012f,
                    s * (0.045f + rnd.nextFloat() * 0.02f), p)
            }
        }
    }

    private fun rain(c: Canvas, s: Int, rnd: java.util.Random) {
        val p = paint(0x88CFE8FF.toInt(), Paint.Style.STROKE).apply { strokeWidth = s / 620f }
        repeat(220) {
            val x = rnd.nextFloat() * s
            val y = rnd.nextFloat() * s
            val l = s * (0.02f + rnd.nextFloat() * 0.035f)
            c.drawLine(x, y, x - l * 0.18f, y + l, p)
        }
    }

    private fun snow(c: Canvas, s: Int, rnd: java.util.Random) {
        val p = paint(0xE6FFFFFF.toInt())
        repeat(200) {
            c.drawCircle(rnd.nextFloat() * s, rnd.nextFloat() * s, s * (0.003f + rnd.nextFloat() * 0.006f), p)
        }
    }

    private fun boat(c: Canvas, s: Int, rnd: java.util.Random) {
        val bx = s * (0.3f + rnd.nextFloat() * 0.4f)
        val by = s * 0.72f
        val hull = Path().apply {
            moveTo(bx - s * 0.10f, by)
            lineTo(bx + s * 0.10f, by)
            lineTo(bx + s * 0.06f, by + s * 0.045f)
            lineTo(bx - s * 0.06f, by + s * 0.045f)
            close()
        }
        c.drawPath(hull, paint(0xFF6B4326.toInt()))
        c.drawLine(bx, by, bx, by - s * 0.17f, paint(0xFF3E2A18.toInt(), Paint.Style.STROKE).apply { strokeWidth = s / 300f })
        val sail = Path().apply {
            moveTo(bx, by - s * 0.17f)
            lineTo(bx + s * 0.09f, by - s * 0.02f)
            lineTo(bx, by - s * 0.02f)
            close()
        }
        c.drawPath(sail, paint(0xFFF5F0E6.toInt()))
    }

    private fun heart(c: Canvas, s: Int) {
        val cx = s / 2f
        val cy = s * 0.48f
        val r = s * 0.16f
        val p = paint(0).apply {
            shader = RadialGradient(cx, cy - r * 0.4f, r * 2.4f,
                intArrayOf(0xFFFF8FA3.toInt(), 0xFFE01E5A.toInt()), null, Shader.TileMode.CLAMP)
        }
        val path = Path().apply {
            moveTo(cx, cy + r * 1.1f)
            cubicTo(cx - r * 2.1f, cy - r * 0.3f, cx - r * 0.8f, cy - r * 1.8f, cx, cy - r * 0.6f)
            cubicTo(cx + r * 0.8f, cy - r * 1.8f, cx + r * 2.1f, cy - r * 0.3f, cx, cy + r * 1.1f)
            close()
        }
        c.drawPath(path, p)
    }

    private fun road(c: Canvas, s: Int) {
        val path = Path().apply {
            moveTo(s * 0.42f, s * 0.62f)
            lineTo(s * 0.58f, s * 0.62f)
            lineTo(s * 0.92f, s.toFloat())
            lineTo(s * 0.08f, s.toFloat())
            close()
        }
        c.drawPath(path, paint(0xFF3A3F4A.toInt()))
        val dash = paint(0xFFFFE082.toInt())
        var y = s * 0.66f
        var w = s * 0.006f
        while (y < s) {
            c.drawRect(s / 2f - w, y, s / 2f + w, y + s * 0.03f, dash)
            y += s * 0.055f
            w *= 1.18f
        }
    }

    private fun car(c: Canvas, s: Int, rnd: java.util.Random) {
        val cx = s * (0.35f + rnd.nextFloat() * 0.3f)
        val cy = s * 0.86f
        val w = s * 0.22f
        val h = s * 0.07f
        c.drawRoundRect(RectF(cx - w / 2f, cy - h, cx + w / 2f, cy + h * 0.4f), h * 0.5f, h * 0.5f, paint(0xFFD84343.toInt()))
        c.drawRoundRect(RectF(cx - w * 0.28f, cy - h * 2.0f, cx + w * 0.30f, cy - h * 0.7f), h * 0.4f, h * 0.4f, paint(0xFFBFE6F7.toInt()))
        val tyre = paint(0xFF1A1A1A.toInt())
        c.drawCircle(cx - w * 0.28f, cy + h * 0.4f, h * 0.42f, tyre)
        c.drawCircle(cx + w * 0.28f, cy + h * 0.4f, h * 0.42f, tyre)
    }

    private fun flames(c: Canvas, s: Int, rnd: java.util.Random) {
        val cx = s / 2f
        val base = s * 0.86f
        for (i in 0 until 26) {
            val h = s * (0.10f + rnd.nextFloat() * 0.36f)
            val w = s * (0.02f + rnd.nextFloat() * 0.05f)
            val x = cx + (rnd.nextFloat() - 0.5f) * s * 0.22f
            val col = intArrayOf(0xFFFFD166.toInt(), 0xFFFF8A45.toInt(), 0xFFE23E23.toInt())[i % 3]
            val path = Path().apply {
                moveTo(x - w, base)
                quadTo(x - w * 0.4f, base - h * 0.6f, x, base - h)
                quadTo(x + w * 0.4f, base - h * 0.6f, x + w, base)
                close()
            }
            c.drawPath(path, paint(col))
        }
    }

    private fun aurora(c: Canvas, s: Int, rnd: java.util.Random) {
        for (band in 0..4) {
            val p = paint(0, Paint.Style.STROKE).apply {
                strokeWidth = s * (0.03f + rnd.nextFloat() * 0.05f)
                val hue = 140f + band * 38f + rnd.nextFloat() * 20f
                color = Color.HSVToColor(70, floatArrayOf(hue % 360f, 0.8f, 1f))
                strokeCap = Paint.Cap.ROUND
            }
            val path = Path()
            val y0 = s * (0.14f + band * 0.13f)
            path.moveTo(-s * 0.1f, y0)
            var x = -s * 0.1f
            while (x < s * 1.1f) {
                val w = s * 0.24f
                path.quadTo(x + w / 2f, y0 + (rnd.nextFloat() - 0.5f) * s * 0.16f, x + w, y0)
                x += w
            }
            c.drawPath(path, p)
        }
    }

    private fun orbs(c: Canvas, s: Int, rnd: java.util.Random) {
        repeat(9) {
            val cx = rnd.nextFloat() * s
            val cy = rnd.nextFloat() * s
            val r = s * (0.03f + rnd.nextFloat() * 0.10f)
            val hue = rnd.nextFloat() * 360f
            val col = Color.HSVToColor(floatArrayOf(hue, 0.75f, 1f))
            val p = paint(0).apply {
                shader = RadialGradient(cx, cy, r, intArrayOf(col, 0x00000000),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            }
            c.drawCircle(cx, cy, r, p)
        }
    }

    private fun fireflies(c: Canvas, s: Int, rnd: java.util.Random) {
        repeat(26) {
            val x = rnd.nextFloat() * s
            val y = s * (0.55f + rnd.nextFloat() * 0.4f)
            val r = s * 0.006f
            val glow = paint(0).apply {
                shader = RadialGradient(x, y, r * 9f, intArrayOf(0x88FFF3A0.toInt(), 0x00FFF3A0),
                    floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            }
            c.drawCircle(x, y, r * 9f, glow)
            c.drawCircle(x, y, r, paint(0xFFFFF6BE.toInt()))
        }
    }

    private fun sparkles(c: Canvas, s: Int, rnd: java.util.Random) {
        val p = paint(0xAAFFFFFF.toInt(), Paint.Style.STROKE).apply { strokeWidth = s / 500f }
        repeat(22) {
            val x = rnd.nextFloat() * s
            val y = rnd.nextFloat() * s
            val r = s * (0.006f + rnd.nextFloat() * 0.014f)
            c.drawLine(x - r, y, x + r, y, p)
            c.drawLine(x, y - r, x, y + r, p)
        }
    }

    private fun face(c: Canvas, s: Int, rnd: java.util.Random) {
        val cx = s / 2f
        val cy = s * 0.5f
        val r = s * 0.26f
        c.drawCircle(cx, cy, r, paint(0xFFF2C9A0.toInt()))
        // hair
        val hair = Path().apply {
            addArc(RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f)
            lineTo(cx - r, cy - r * 0.1f)
            close()
        }
        c.drawPath(hair, paint(0xFF2A1B12.toInt()))
        val eye = paint(0xFF22283A.toInt())
        c.drawCircle(cx - r * 0.36f, cy - r * 0.06f, r * 0.08f, eye)
        c.drawCircle(cx + r * 0.36f, cy - r * 0.06f, r * 0.08f, eye)
        val smile = paint(0xFFB4553F.toInt(), Paint.Style.STROKE).apply {
            strokeWidth = r * 0.07f; strokeCap = Paint.Cap.ROUND
        }
        c.drawArc(RectF(cx - r * 0.34f, cy + r * 0.10f, cx + r * 0.34f, cy + r * 0.62f), 20f, 140f, false, smile)
        c.drawCircle(cx, cy + r * 0.14f, r * 0.05f, paint(0xFFD79A78.toInt()))
    }

    private fun cat(c: Canvas, s: Int, rnd: java.util.Random) {
        val cx = s / 2f
        val cy = s * 0.55f
        val r = s * 0.22f
        val fur = intArrayOf(0xFF4A4A55.toInt(), 0xFF8A6A4A.toInt(), 0xFF2E2E36.toInt())[rnd.nextInt(3)]
        // ears
        for (side in listOf(-1f, 1f)) {
            val ear = Path().apply {
                moveTo(cx + side * r * 0.86f, cy - r * 0.24f)
                lineTo(cx + side * r * 0.42f, cy - r * 1.16f)
                lineTo(cx + side * r * 0.10f, cy - r * 0.62f)
                close()
            }
            c.drawPath(ear, paint(fur))
        }
        c.drawCircle(cx, cy, r, paint(fur))
        val eye = paint(0xFF7CE3A8.toInt())
        c.drawCircle(cx - r * 0.38f, cy - r * 0.14f, r * 0.16f, eye)
        c.drawCircle(cx + r * 0.38f, cy - r * 0.14f, r * 0.16f, eye)
        c.drawCircle(cx - r * 0.38f, cy - r * 0.14f, r * 0.06f, paint(0xFF111111.toInt()))
        c.drawCircle(cx + r * 0.38f, cy - r * 0.14f, r * 0.06f, paint(0xFF111111.toInt()))
        c.drawCircle(cx, cy + r * 0.22f, r * 0.07f, paint(0xFFFF9EB5.toInt()))
        val whisker = paint(0xAAFFFFFF.toInt(), Paint.Style.STROKE).apply { strokeWidth = s / 600f }
        for (side in listOf(-1f, 1f)) for (k in -1..1) {
            c.drawLine(cx + side * r * 0.22f, cy + r * 0.26f + k * r * 0.10f,
                cx + side * r * 1.05f, cy + r * 0.20f + k * r * 0.22f, whisker)
        }
    }

    private fun tessellation(c: Canvas, s: Int, rnd: java.util.Random) {
        val step = s / 9
        for (y in 0 until 9) for (x in 0 until 9) {
            val hue = ((x + y) * 22 + rnd.nextInt(14)) % 360
            val col = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.55f, 0.95f))
            val p = paint(col, if ((x + y) % 2 == 0) Paint.Style.FILL else Paint.Style.STROKE).apply {
                strokeWidth = s / 300f
            }
            val l = x * step.toFloat()
            val t = y * step.toFloat()
            when ((x + y) % 3) {
                0 -> c.drawRect(l + step * 0.16f, t + step * 0.16f, l + step * 0.84f, t + step * 0.84f, p)
                1 -> c.drawCircle(l + step / 2f, t + step / 2f, step * 0.34f, p)
                else -> {
                    val tri = Path().apply {
                        moveTo(l + step / 2f, t + step * 0.14f)
                        lineTo(l + step * 0.86f, t + step * 0.86f)
                        lineTo(l + step * 0.14f, t + step * 0.86f)
                        close()
                    }
                    c.drawPath(tri, p)
                }
            }
        }
    }

    /** Adds a faint film grain so gradients do not band on cheap panels. */
    private fun grain(c: Canvas, s: Int, rnd: java.util.Random) {
        val p = paint(0x14FFFFFF)
        repeat(s / 3) {
            c.drawPoint(rnd.nextFloat() * s, rnd.nextFloat() * s, p)
        }
    }

    /** Poster text over any drawing — used for banners and greetings. */
    fun withText(bmp: Bitmap, text: String, color: Int = 0xFFFFFFFF.toInt()): Bitmap {
        val c = Canvas(bmp)
        val p = paint(color).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = bmp.width / 14f
            textAlign = Paint.Align.CENTER
            setShadowLayer(bmp.width / 90f, 0f, bmp.width / 260f, 0x88000000.toInt())
        }
        val lines = text.split("\n")
        val startY = bmp.height * 0.5f - (lines.size - 1) * p.textSize * 0.6f
        lines.forEachIndexed { i, line ->
            c.drawText(line, bmp.width / 2f, startY + i * p.textSize * 1.25f, p)
        }
        return bmp
    }

    /** Quick sanity helper used by tests/tools to prove a bitmap was produced. */
    fun sizeOf(bmp: Bitmap): String = "${bmp.width}x${bmp.height}"

    /** Distance helper kept for geometry experiments in future scenes. */
    fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(abs(x2 - x1), abs(y2 - y1))
}
