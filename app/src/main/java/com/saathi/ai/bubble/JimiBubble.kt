package com.saathi.ai.bubble

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionHub
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min

/**
 * The floating JIMI orb.
 *
 * Design goals that the first version missed:
 *  • it has to look like the app, not like a debug circle — same palette, same glow
 *  • it has to *tell* you what is happening — a live caption pill that grows out of
 *    the orb, with the current action in Bangla
 *  • it has to be pleasant to grab — the pill expands on drag, snaps to the nearest
 *    edge, remembers where you left it, and never blocks a big part of the screen
 */
class JimiBubble(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var root: FrameLayout? = null
    private var orb: OrbView? = null
    private var caption: TextView? = null
    private var pill: LinearLayout? = null

    private var params: WindowManager.LayoutParams? = null
    private var captionParams: WindowManager.LayoutParams? = null

    private var lastCaption = ""
    private var collapseRunnable: Runnable? = null

    val isShowing: Boolean get() = root != null

    // ---------------------------------------------------------------- lifecycle

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) return
        val palette = BubblePalette.from(context)

        val frame = FrameLayout(context)
        val orbView = OrbView(context, palette)
        val size = dp(64)
        frame.addView(orbView, FrameLayout.LayoutParams(size, size))

        val lp = WindowManager.LayoutParams(
            size, size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = BubblePalette.from(context).savedX
            y = BubblePalette.from(context).savedY
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        frame.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    orbView.pressed = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (hypot(dx, dy) > 12f) moved = true
                    if (moved) {
                        lp.x = (startX + dx).toInt()
                        lp.y = (startY + dy).toInt()
                        runCatching { wm.updateViewLayout(frame, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    orbView.pressed = false
                    if (moved) {
                        snapToEdge(lp, size)
                        runCatching { wm.updateViewLayout(frame, lp) }
                        BubblePalette.from(context).savePosition(lp.x, lp.y)
                    } else {
                        onClick()
                    }
                    true
                }
                else -> false
            }
        }

        runCatching {
            wm.addView(frame, lp)
            root = frame
            orb = orbView
            params = lp
        }
        startObserving()
    }

    private fun onClick() {
        val state = SessionHub.state.value
        when (state.phase) {
            Phase.IDLE, Phase.ENDED, Phase.ERROR -> {
                SessionHub.update { it.copy(phase = Phase.CONNECTING) }
                runCatching {
                    val intent = android.content.Intent(
                        context, com.saathi.ai.capture.LiveSessionService::class.java
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
                setCaption("Jimi জাগছে…")
            }
            else -> openApp()
        }
    }

    private fun openApp() {
        runCatching {
            context.startActivity(
                android.content.Intent(context, com.saathi.ai.MainActivity::class.java)
                    .addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
            )
        }
    }

    fun hide() {
        collapseRunnable?.let { handler.removeCallbacks(it) }
        stopObserving()
        runCatching { caption?.let { wm.removeView(it) } }
        runCatching { root?.let { wm.removeView(it) } }
        caption = null
        root = null
        orb = null
        params = null
        captionParams = null
    }

    // ---------------------------------------------------------------- caption

    /** Shows what Jimi is doing, in a compact pill next to the orb. */
    fun setCaption(text: String) {
        val clean = text.trim().take(70)
        if (clean.isEmpty() || clean == lastCaption) return
        lastCaption = clean

        handler.post {
            val frame = root ?: return@post
            val palette = BubblePalette.from(context)
            if (caption == null) {
                val tv = TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 11.5f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(14), dp(9), dp(14), dp(9))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        setColor(Color.argb(232, 14, 18, 32))
                        setStroke(dp(1), Color.argb(90, Color.red(palette.primary), Color.green(palette.primary), Color.blue(palette.primary)))
                    }
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }
                caption = tv
                captionParams = lp
                runCatching { wm.addView(tv, lp) }
            }
            caption?.text = clean
            positionCaption()

            // auto-collapse the text after a while so the screen stays clean
            collapseRunnable?.let { handler.removeCallbacks(it) }
            collapseRunnable = Runnable {
                runCatching {
                    (caption?.layoutParams as? WindowManager.LayoutParams)?.let {
                        caption?.alpha = 0f
                    }
                }
            }.also { handler.postDelayed(it, 6000) }
            caption?.alpha = 1f
        }
    }

    private fun positionCaption() {
        val lp = params ?: return
        val clp = captionParams ?: return
        val tv = caption ?: return
        val orbSize = dp(64)
        val screenW = context.resources.displayMetrics.widthPixels
        val onLeft = lp.x < screenW / 2
        tv.measure(
            View.MeasureSpec.makeMeasureSpec(screenW - dp(140), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(dp(120), View.MeasureSpec.AT_MOST)
        )
        val w = tv.measuredWidth
        clp.x = if (onLeft) lp.x + orbSize + dp(6) else lp.x - w - dp(6)
        clp.y = lp.y + (orbSize - tv.measuredHeight) / 2
        runCatching { wm.updateViewLayout(tv, clp) }
    }

    private fun snapToEdge(lp: WindowManager.LayoutParams, size: Int) {
        val dm = context.resources.displayMetrics
        val margin = dp(8)
        lp.x = lp.x.coerceIn(margin, dm.widthPixels - size - margin)
        lp.y = lp.y.coerceIn(dp(40), dm.heightPixels - size - dp(120))
        // magnetise horizontally to the nearer edge, keeping the vertical spot
        lp.x = if (lp.x + size / 2 < dm.widthPixels / 2) dp(10) else dm.widthPixels - size - dp(10)
        positionCaption()
    }

    // ---------------------------------------------------------------- session stream

    private var observeJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
    )

    private fun startObserving() {
        observeJob = scope.launch {
            SessionHub.state.collect { st ->
                orb?.phase = st.phase
                orb?.level = st.level

                val captionText = when {
                    st.bubbleMsg.isNotBlank() -> st.bubbleMsg
                    st.lastTool.isNotBlank() -> toolLabel(st.lastTool)
                    else -> when (st.phase) {
                        Phase.LISTENING -> "শুনছি…"
                        Phase.THINKING -> "ভাবছি…"
                        Phase.SPEAKING -> st.modelText.take(60)
                        Phase.CONNECTING -> "সংযোগ হচ্ছে…"
                        Phase.RECONNECTING -> "আবার জোড়া লাগাচ্ছি…"
                        else -> ""
                    }
                }
                if (captionText.isNotBlank()) setCaption(captionText)
            }
        }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    private fun toolLabel(tool: String): String = when (tool) {
        "open_app" -> "অ্যাপ খুলছি…"
        "open_url", "open_web" -> "ওয়েবসাইট খুলছি…"
        "search_youtube", "play_on_youtube", "open_youtube_and_search_play" -> "ইউটিউবে খুঁজছি…"
        "read_screen" -> "স্ক্রিন পড়ছি…"
        "screen_tap", "screen_tap_at", "long_press" -> "ট্যাপ করছি…"
        "screen_scroll" -> "স্ক্রল করছি…"
        "screen_type" -> "লিখছি…"
        "screen_zoom" -> "জুম করছি…"
        "call_number", "call_contact" -> "কল দিচ্ছি…"
        "send_sms" -> "মেসেজ পাঠাচ্ছি…"
        "read_recent_sms", "read_call_log", "read_contacts" -> "তথ্য দেখছি…"
        "open_camera", "take_photo" -> "ক্যামেরা…"
        "latest_photos" -> "ছবি খুঁজছি…"
        "upi_pay", "upi_scan" -> "পেমেন্ট স্ক্রিন…"
        "open_weather" -> "আবহাওয়া দেখছি…"
        "copy_text", "paste_text", "write_clipboard", "read_clipboard" -> "ক্লিপবোর্ড…"
        "medicine_reminder" -> "রিমাইন্ডার সেট করছি…"
        "remember", "recall" -> "মনে রাখছি…"
        else -> "কাজ করছি…"
    }

    // ---------------------------------------------------------------- helpers

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- the orb itself

    private class OrbView(
        context: Context,
        private val palette: BubblePalette
    ) : View(context) {

        var phase: Phase = Phase.IDLE
            set(value) {
                field = value
                invalidate()
            }

        var level: Float = 0f
            set(value) {
                field = value
                invalidate()
            }

        var pressed = false
            set(value) {
                field = value
                invalidate()
            }

        private val core = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val oval = RectF()
        private var t = 0f

        private val ticker = object : Runnable {
            override fun run() {
                t += 0.055f
                invalidate()
                postDelayed(this, 33)
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            post(ticker)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val r = min(w, h) / 2f
            val scale = if (pressed) 0.9f else 1f
            val mood = when (phase) {
                Phase.LISTENING -> 1f
                Phase.SPEAKING -> 1f
                Phase.THINKING -> 0.6f
                Phase.CONNECTING -> 0.4f
                Phase.RECONNECTING -> 0.4f
                Phase.ERROR -> 0.15f
                else -> 0.35f
            }

            // outer bloom
            glow.shader = RadialGradient(
                cx, cy, r * (1.15f + 0.1f * level),
                intArrayOf(
                    alpha(palette.primary, 0.55f * mood + 0.18f),
                    alpha(palette.secondary, 0.22f * mood),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.35f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r * (1.15f + 0.1f * level), glow)

            // glass body
            oval.set(cx - r * 0.86f * scale, cy - r * 0.86f * scale, cx + r * 0.86f * scale, cy + r * 0.86f * scale)
            core.shader = RadialGradient(
                cx - r * 0.3f, cy - r * 0.35f, r * 1.6f,
                intArrayOf(
                    alpha(palette.secondary, 0.95f),
                    alpha(palette.primary, 0.92f),
                    alpha(0xFF10152A.toInt(), 0.98f)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r * 0.86f * scale, core)

            // audio-reactive ring
            val ringR = r * (0.94f + level * 0.10f)
            ring.shader = null
            ring.strokeWidth = r * 0.10f
            ring.color = alpha(palette.accent, 0.75f * mood + 0.1f)
            canvas.drawCircle(cx, cy, ringR, ring)

            // 12 spokes that beat with the voice
            bar.color = alpha(Color.WHITE, 0.55f)
            bar.strokeWidth = r * 0.055f
            bar.strokeCap = Paint.Cap.ROUND
            val bars = 12
            for (i in 0 until bars) {
                val ang = (i.toFloat() / bars) * 6.2831f + t * 0.6f
                val wobble = 0.55f + 0.45f * kotlin.math.sin(t * 2.2f + i * 0.9f)
                val len = r * (0.06f + 0.22f * level * wobble * mood)
                val sx = cx + kotlin.math.cos(ang) * ringR
                val sy = cy + kotlin.math.sin(ang) * ringR
                val ex = cx + kotlin.math.cos(ang) * (ringR + len)
                val ey = cy + kotlin.math.sin(ang) * (ringR + len)
                canvas.drawLine(sx, sy, ex, ey, bar)
            }

            // little nucleus highlight
            core.shader = null
            core.color = alpha(Color.WHITE, 0.30f + 0.25f * mood)
            canvas.drawCircle(cx - r * 0.22f, cy - r * 0.24f, r * 0.16f, core)

            // drawing a subtle smile keeps it friendly at rest
            if (mood < 0.5f) {
                val smile = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = r * 0.07f
                    strokeCap = Paint.Cap.ROUND
                    color = alpha(Color.WHITE, 0.85f)
                }
                path.reset()
                path.moveTo(cx - r * 0.26f, cy + r * 0.10f)
                path.quadTo(cx, cy + r * 0.40f, cx + r * 0.26f, cy + r * 0.10f)
                canvas.drawPath(path, smile)
            }
        }

        private fun alpha(color: Int, a: Float): Int {
            val al = (a.coerceIn(0f, 1f) * 255).toInt()
            return Color.argb(al, Color.red(color), Color.green(color), Color.blue(color))
        }
    }

    // ---------------------------------------------------------------- palette bridge

    /** Reads the user's active theme without pulling Compose into the overlay. */
    class BubblePalette(
        val primary: Int,
        val secondary: Int,
        val accent: Int,
        val savedX: Int,
        val savedY: Int
    ) {
        var contextRef: Context? = null

        fun savePosition(x: Int, y: Int) {
            contextRef?.getSharedPreferences("jimi_bubble", Context.MODE_PRIVATE)
                ?.edit()
                ?.putInt("x", x)
                ?.putInt("y", y)
                ?.apply()
        }

        companion object {
            private fun argb(c: androidx.compose.ui.graphics.Color): Int =
                android.graphics.Color.argb(
                    (c.alpha * 255f).toInt(),
                    (c.red * 255f).toInt(),
                    (c.green * 255f).toInt(),
                    (c.blue * 255f).toInt()
                )

            fun from(context: Context): BubblePalette {
                val spec = com.saathi.ai.ui.theme.JimiThemes.get(
                    com.saathi.ai.data.SecurePrefs(context).themeId
                )
                val p = spec.palette
                val sp = context.getSharedPreferences("jimi_bubble", Context.MODE_PRIVATE)
                val dm = context.resources.displayMetrics
                return BubblePalette(
                    primary = argb(p.primary),
                    secondary = argb(p.secondary),
                    accent = argb(p.accent),
                    savedX = sp.getInt("x", 20),
                    savedY = sp.getInt("y", (dm.heightPixels * 0.62f).toInt())
                ).also { it.contextRef = context }
            }
        }
    }
}
