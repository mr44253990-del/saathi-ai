package com.saathi.ai.bubble

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.saathi.ai.MainActivity
import com.saathi.ai.data.SecurePrefs
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionHub
import com.saathi.ai.live.SessionState
import com.saathi.ai.ui.theme.JimiThemes
import com.saathi.ai.ui.theme.withAccent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * The floating Jimi — completely redrawn, no emoji, no image assets.
 *
 * Every pixel here is drawn in code ([OrbView] uses gradients and arcs; the panel
 * is a clipped column), which is what makes it follow the active theme instantly
 * and stay crisp on any density.
 *
 * Behaviours the user asked for, one by one:
 *  • **Transcript rises inside the bubble.** Lines are added at the bottom of a
 *    fixed-height, clipped column; when the column is full the whole strip slides up
 *    by one line and the oldest line disappears *behind the clip* — text never
 *    escapes the bubble and never covers the screen.
 *  • **Draggable anywhere.** Move it with a finger; the exact position is stored in
 *    [SecurePrefs.bubbleX]/[bubbleY] and restored next time. A tap (no movement)
 *    still opens the app, so the gesture does not steal the shortcut.
 *  • **Auto show / auto hide.** [BubbleService.autoShow] is called when background
 *    work starts, [BubbleService.autoHide] when the app comes to the foreground, so
 *    the bubble is there while Jimi works and gone while you are looking at Jimi.
 *  • **Live task progress.** The outer arc is the to-do board percentage and the
 *    panel prints the runner's current step, so background work is visible.
 */
class JimiBubble(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = SecurePrefs(context)
    private val palette = JimiThemes.get(prefs.themeId).withAccent(prefs.accentOverride).palette

    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private lateinit var orb: OrbView
    private lateinit var panel: FrameLayout
    private lateinit var strip: LinearLayout

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null
    private var collapseJob: Job? = null

    private var expanded = false
    private val lineHeight by lazy { dp(19) }
    private val maxLines = 3

    val isShowing: Boolean get() = root != null

    // ------------------------------------------------------------------ window

    fun show() {
        if (isShowing) return
        if (!android.provider.Settings.canDrawOverlays(context)) return
        // hide() cancels the scope; a re-shown instance needs a fresh one
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        orb = OrbView(context).apply {
            setColors(palette.glowA.toArgb(), palette.glowB.toArgb(), palette.accent.toArgb())
        }
        panel = FrameLayout(context).apply {
            clipChildren = true
            clipToPadding = true
            visibility = View.GONE
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0xCC0A0D1C.toInt())
                setStroke(dp(1), 0x1CFFFFFF)
            }
        }
        strip = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(11), dp(7), dp(11), dp(7))
        }
        panel.addView(
            strip,
            FrameLayout.LayoutParams(dp(196), lineHeight * maxLines + dp(14))
        )

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(orb, LinearLayout.LayoutParams(dp(50), dp(50)))
        row.addView(panel, LinearLayout.LayoutParams(dp(196), lineHeight * maxLines + dp(14)))

        val frame = FrameLayout(context).apply {
            addView(row, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = restoredX()
            y = restoredY()
        }
        params = lp

        attachDrag(frame, lp)

        runCatching { wm.addView(frame, lp) }
        root = frame
        observe()
        orb.start()
    }

    fun hide() {
        collector?.cancel()
        collector = null
        collapseJob?.cancel()
        collapseJob = null
        scope.cancel()
        runCatching { orb.stop() }
        runCatching { root?.let { wm.removeView(it) } }
        root = null
        params = null
    }

    private fun restoredX(): Int {
        val saved = prefs.bubbleX
        val w = context.resources.displayMetrics.widthPixels
        return if (saved in 0..w) saved else w - dp(66)
    }

    private fun restoredY(): Int {
        val saved = prefs.bubbleY
        val h = context.resources.displayMetrics.heightPixels
        return if (saved in 0..h) saved else (h * 0.38f).toInt()
    }

    // -------------------------------------------------------------------- drag

    private fun attachDrag(view: View, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var downAt = 0L

        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    downAt = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moved = true
                    if (moved) {
                        val maxX = context.resources.displayMetrics.widthPixels - dp(46)
                        val maxY = context.resources.displayMetrics.heightPixels - dp(60)
                        lp.x = (startX + dx).coerceIn(0, maxX)
                        lp.y = (startY + dy).coerceIn(dp(24), maxY)
                        runCatching { wm.updateViewLayout(view, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        // remember exactly where the user left it
                        prefs.bubbleX = lp.x
                        prefs.bubbleY = lp.y
                    } else if (System.currentTimeMillis() - downAt < 400) {
                        openApp()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openApp() {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }
    }

    // ------------------------------------------------------------------- state

    private fun observe() {
        collector?.cancel()
        collector = scope.launch {
            SessionHub.state.collectLatest { render(it) }
        }
    }

    private fun render(st: SessionState) {
        if (root == null) return
        orb.phase = st.phase
        orb.level = st.level
        orb.progress = st.taskProgress

        val line = when {
            st.taskText.isNotBlank() -> st.taskText
            st.error != null -> st.error!!
            st.modelText.isNotBlank() -> st.modelText
            st.userText.isNotBlank() -> st.userText
            st.lastTool.isNotBlank() -> st.lastTool
            else -> ""
        }
        if (line.isNotBlank() && line != lastLine) {
            lastLine = line
            val color = when {
                st.error != null -> 0xFFFF8A9C.toInt()
                st.taskText.isNotBlank() -> palette.secondary.toArgb()
                st.userText.isNotBlank() && st.modelText.isBlank() -> 0xFFB9C4E8.toInt()
                else -> 0xFFEDEFFB.toInt()
            }
            addLine(line, color)
        }
        if (st.phase == Phase.IDLE || st.phase == Phase.ENDED) scheduleCollapse()
    }

    private var lastLine = ""

    // ------------------------------------------------------- rising transcript

    /**
     * Adds one line at the bottom. When the column is full, the strip slides up by
     * exactly one line and the oldest view is dropped after the animation, so the
     * text visibly rises and vanishes *inside* the bubble.
     */
    private fun addLine(text: String, color: Int) {
        if (root == null) return
        if (prefs.bubbleStyle == "orb") return
        expand()

        val tv = TextView(context).apply {
            this.text = text.take(120)
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, lineHeight
            )
        }
        strip.addView(tv)

        while (strip.childCount > maxLines + 1) strip.removeViewAt(0)

        if (strip.childCount > maxLines) {
            strip.animate().cancel()
            strip.translationY = 0f
            strip.animate()
                .translationY(-lineHeight.toFloat())
                .setDuration(320)
                .withEndAction {
                    if (strip.childCount > 0) strip.removeViewAt(0)
                    strip.translationY = 0f
                }
                .start()
        }
        scheduleCollapse()
    }

    /** External caption entry point, kept for BubbleService compatibility. */
    fun setCaption(text: String) {
        if (text.isBlank()) return
        addLine(text, 0xFFEDEFFB.toInt())
    }

    /** Background task progress line + ring. */
    fun setTask(text: String, progress: Int) {
        orb.progress = progress.coerceIn(0, 100)
        if (text.isNotBlank()) addLine(text, palette.secondary.toArgb())
    }

    private fun expand() {
        if (expanded) return
        expanded = true
        panel.visibility = View.VISIBLE
        panel.alpha = 0f
        panel.animate().alpha(1f).setDuration(220).start()
    }

    private fun scheduleCollapse() {
        collapseJob?.cancel()
        collapseJob = scope.launch {
            kotlinx.coroutines.delay(7_000)
            if (!expanded) return@launch
            expanded = false
            panel.animate().alpha(0f).setDuration(260).withEndAction {
                panel.visibility = View.GONE
            }.start()
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
    ).toInt()

    // -------------------------------------------------------------------- orb

    /**
     * The orb, drawn entirely with shaders — no bitmaps, no emoji.
     *
     * Layers, back to front: soft radial glow, a rotating sweep (the "thinking"
     * motion), a level arc that follows the microphone, and a thin outer arc that is
     * the to-do board progress. Phase decides the colour pair and the pulse speed.
     */
    class OrbView(context: Context) : View(context) {

        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val rect = RectF()

        private var colorA = 0xFF7C5CFF.toInt()
        private var colorB = 0xFF35D6C8.toInt()
        private var colorAccent = 0xFFFF7AC6.toInt()

        var phase: Phase = Phase.IDLE
            set(value) {
                if (field != value) {
                    field = value
                    restartPulse()
                    invalidate()
                }
            }
        var level: Float = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }
        var progress: Int = 0
            set(value) {
                field = value.coerceIn(0, 100)
                invalidate()
            }

        private var spin = 0f
        private var pulse = 0f
        private var animator: ValueAnimator? = null

        fun setColors(a: Int, b: Int, accent: Int) {
            colorA = a
            colorB = b
            colorAccent = accent
            invalidate()
        }

        fun start() {
            if (animator?.isRunning == true) return
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2600
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val f = it.animatedFraction
                    spin = f * 360f
                    pulse = (1f - abs(f * 2f - 1f))
                    invalidate()
                }
                start()
            }
        }

        fun stop() {
            animator?.cancel()
            animator = null
        }

        private fun restartPulse() {
            val a = animator ?: return
            a.duration = when (phase) {
                Phase.LISTENING -> 1500
                Phase.THINKING -> 900
                Phase.SPEAKING -> 1100
                Phase.CONNECTING, Phase.RECONNECTING -> 700
                Phase.ERROR -> 2200
                else -> 3000
            }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w / 2f
            val cy = h / 2f
            val r = min(w, h) / 2f

            val breathe = when (phase) {
                Phase.IDLE, Phase.ENDED -> 0.90f + pulse * 0.03f
                Phase.LISTENING -> 0.92f + pulse * 0.06f + level * 0.10f
                Phase.THINKING -> 0.93f + pulse * 0.05f
                Phase.SPEAKING -> 0.94f + pulse * 0.07f
                Phase.CONNECTING, Phase.RECONNECTING -> 0.90f + pulse * 0.08f
                Phase.ERROR -> 0.88f + pulse * 0.04f
            }
            val coreR = r * breathe

            // outer glow
            glowPaint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(withAlpha(colorA, 120), withAlpha(colorB, 40), Color.TRANSPARENT),
                floatArrayOf(0.25f, 0.62f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, glowPaint)

            // rotating sweep
            val sweep = SweepGradient(
                cx, cy,
                intArrayOf(withAlpha(colorA, 0), colorA, colorB, withAlpha(colorB, 0)),
                floatArrayOf(0f, 0.28f, 0.62f, 1f)
            )
            val m = android.graphics.Matrix()
            m.postRotate(spin * (if (phase == Phase.THINKING) 2f else 1f), cx, cy)
            sweep.setLocalMatrix(m)
            sweepPaint.shader = sweep
            canvas.drawCircle(cx, cy, coreR * 0.86f, sweepPaint)

            // core
            corePaint.shader = RadialGradient(
                cx - coreR * 0.25f, cy - coreR * 0.3f, coreR,
                intArrayOf(Color.WHITE, colorB, colorA),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, coreR * 0.62f, corePaint)

            // microphone level arc
            if (level > 0.02f) {
                levelPaint.strokeWidth = r * 0.09f
                levelPaint.color = colorAccent
                val inset = r * 0.20f
                rect.set(cx - coreR + inset, cy - coreR + inset, cx + coreR - inset, cy + coreR - inset)
                canvas.drawArc(rect, -90f, 360f * level, false, levelPaint)
            }

            // to-do progress ring
            if (progress > 0) {
                progressPaint.strokeWidth = r * 0.075f
                progressPaint.color = withAlpha(colorB, 235)
                val inset = r * 0.06f
                rect.set(cx - r + inset, cy - r + inset, cx + r - inset, cy + r - inset)
                canvas.drawArc(rect, -90f, 3.6f * progress, false, progressPaint)
            }

            // error tint
            if (phase == Phase.ERROR) {
                corePaint.shader = null
                corePaint.color = withAlpha(0xFFFF5C7A.toInt(), 90)
                canvas.drawCircle(cx, cy, coreR * 0.66f, corePaint)
            }
        }

        private fun withAlpha(color: Int, alpha: Int): Int =
            Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}

/** Compose Color → ARGB int, so the bubble can follow the active theme. */
private fun androidx.compose.ui.graphics.Color.toArgb(): Int =
    Color.argb(
        (alpha * 255).toInt().coerceIn(0, 255),
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255)
    )
