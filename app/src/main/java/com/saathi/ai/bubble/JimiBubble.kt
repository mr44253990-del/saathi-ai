package com.saathi.ai.bubble

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The small floating JIMI orb. It sits above every other app, shows what the
 * assistant is saying in a compact bubble, and taps back into the full UI.
 *
 * Implemented with plain views (no Compose in the overlay window) so it works on
 * every API level we support and never fights with the window manager.
 */
class JimiBubble(
    private val context: Context,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var captionView: TextView? = null
    private var captionRoot: View? = null

    var isShowing: Boolean = false
        private set

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) return
        val size = dp(62)

        val container = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1B1035"))
                setStroke(dp(2), Color.parseColor("#8B5CF6"))
            }
            elevation = dp(8).toFloat()
        }

        val orb = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#A78BFA"), Color.parseColor("#22D3EE"))
            ).apply { shape = GradientDrawable.OVAL }
        }
        val orbSize = dp(38)
        container.addView(
            orb,
            FrameLayout.LayoutParams(orbSize, orbSize, Gravity.CENTER)
        )

        val holdRing = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RING
                setStroke(dp(2), Color.parseColor("#22D3EE"))
            }
            visibility = View.GONE
        }
        container.addView(holdRing, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = context.resources.displayMetrics.widthPixels - size - dp(12)
            y = context.resources.displayMetrics.heightPixels / 2 - size / 2
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var downAt = 0L

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    downAt = System.currentTimeMillis()
                    container.animate().scaleX(1.12f).scaleY(1.12f).setDuration(90).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)) moved = true
                    lp.x = startX + dx.toInt()
                    lp.y = startY + dy.toInt()
                    runCatching { windowManager.updateViewLayout(container, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    container.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    if (!moved) {
                        if (System.currentTimeMillis() - downAt > 650) onLongPress() else onTap()
                    }
                    true
                }
                else -> false
            }
        }

        runCatching {
            windowManager.addView(container, lp)
            root = container
            params = lp
            this.captionView = null
            isShowing = true
            startRipple(orb, holdRing)
            observeSession()
        }
    }

    /** Shows a compact caption above the bubble — what Jimi is saying/doing. */
    fun setCaption(text: String) {
        if (!isShowing || text.isBlank()) return
        handler.post {
            if (captionView == null) createCaption()
            captionView?.text = text.take(90)
            captionRoot?.visibility = View.VISIBLE
            handler.removeCallbacks(hideCaption)
            handler.postDelayed(hideCaption, 5200)
        }
    }

    private val hideCaption = Runnable {
        captionRoot?.visibility = View.GONE
    }

    private fun createCaption() {
        val lp = params ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val cap = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 2
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#E6101830"))
                setStroke(dp(1), Color.parseColor("#447C5CFF"))
            }
        }
        val capLp = WindowManager.LayoutParams(
            dp(210),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (context.resources.displayMetrics.widthPixels - dp(210) - dp(12)).coerceAtLeast(0)
            y = (lp.y - dp(58)).coerceAtLeast(0)
        }
        runCatching {
            windowManager.addView(cap, capLp)
            captionView = cap
            captionRoot = cap
        }
    }

    private fun startRipple(orb: View, ring: View) {
        handler.post(object : Runnable {
            var grow = true
            override fun run() {
                val phase = SessionHub.state.value.phase
                val speaking = phase == Phase.SPEAKING || phase == Phase.THINKING
                ring.visibility = if (speaking) View.VISIBLE else View.GONE
                if (speaking) {
                    grow = !grow
                    ring.animate().scaleX(if (grow) 1.35f else 1f).scaleY(if (grow) 1.35f else 1f)
                        .setDuration(if (phase == Phase.SPEAKING) 420 else 900).start()
                }
                handler.postDelayed(this, if (speaking) 460 else 1400)
            }
        })
    }

    private fun observeSession() {
        scope.launch {
            SessionHub.state.collectLatest { st ->
                if (st.modelText.isNotBlank()) setCaption(st.modelText)
                else if (st.lastTool.isNotBlank()) setCaption(toolLabel(st.lastTool))
            }
        }
    }

    private fun toolLabel(tool: String): String = when (tool) {
        "open_app" -> "অ্যাপ খুলছি…"
        "search_youtube", "play_on_youtube" -> "ইউটিউবে খুঁজছি…"
        "call_number", "call_contact" -> "কল দিচ্ছি…"
        "send_sms" -> "মেসেজ পাঠাচ্ছি…"
        "read_screen" -> "স্ক্রিন পড়ছি…"
        "screen_tap", "screen_tap_at" -> "ট্যাপ করছি…"
        "screen_scroll" -> "স্ক্রল করছি…"
        "take_photo" -> "ক্যামেরা খুলছি…"
        else -> "কাজ করছি…"
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        runCatching { scope.cancel() }
        captionRoot?.let { runCatching { windowManager.removeView(it) } }
        root?.let { runCatching { windowManager.removeView(it) } }
        captionRoot = null
        captionView = null
        root = null
        isShowing = false
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
