package com.saathi.ai.assist

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.saathi.ai.MainActivity
import com.saathi.ai.capture.LiveSessionService
import com.saathi.ai.data.SecurePrefs
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionHub
import com.saathi.ai.live.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Digital-assistant integration.
 *
 * Three entry points, from cheapest to deepest:
 *  1. **ASSIST intent** (android.intent.action.ASSIST on MainActivity) — works the
 *     moment the user picks Jimi as the default assistant.
 *  2. **VoiceInteractionService** below — makes Jimi selectable in
 *     Settings → Default apps → Digital assistant app, and gives the system a
 *     session window to show when the user *holds* home / the assistant gesture.
 *  3. **Quick Settings tile** — always available, no setup.
 *
 * Why the session draws its own window: on Android 10+ an app in the background is
 * not allowed to start an Activity, so the old "session → startActivity(MainActivity)"
 * path silently did nothing on most phones — the user held the button and saw no
 * change at all. A [VoiceInteractionSession] window, however, is created *by the
 * system* and is always allowed. So the session now shows Jimi itself, starts the
 * live session, and only then tries to hand over to the full app as a bonus.
 */
class JimiVoiceInteractionService : VoiceInteractionService() {

    private val tag = "JimiAssistant"

    override fun onReady() {
        super.onReady()
        Log.i(tag, "voice interaction ready")
    }
}

/**
 * The window the system shows when Jimi is summoned as the digital assistant.
 *
 * It is a real, self-contained mini Jimi: orb, live status, transcript, mic and
 * stop controls — built with plain views so it works inside the system-owned
 * session window (no Compose, no Activity, no overlay permission).
 */
class JimiVoiceInteractionSession(private val ctx: Context) : VoiceInteractionSession(ctx) {

    private val tag = "JimiAssistant"
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    private lateinit var orb: View
    private lateinit var statusText: TextView
    private lateinit var transcript: TextView
    private lateinit var micButton: Button
    private lateinit var powerButton: Button
    private lateinit var openButton: Button

    // ---------------------------------------------------------------- lifecycle

    override fun onCreateContentView(): View = buildUi()

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        // Wake immediately: this is the "hold the assistant and Jimi connects by
        // itself" behaviour the user asked for. No tap required.
        SessionHub.update { it.copy(phase = Phase.CONNECTING, error = null) }
        startLiveSession()
        observeState()

        // Best effort: if the OS lets us, the full app takes over. If it refuses
        // (background activity launch rules) the session window above still works.
        runCatching {
            val open = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("wake", true)
                putExtra("forceStart", true)
            }
            ctx.startActivity(open)
            // Give the activity a beat to appear before we step aside.
            orb.postDelayed({ runCatching { hide() } }, 700)
        }
    }

    override fun onHide() {
        super.onHide()
        stopObserving()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopObserving()
    }

    // ---------------------------------------------------------------- plumbing

    private fun startLiveSession() {
        val prefs = SecurePrefs(ctx)
        if (!prefs.isConfigured) {
            statusText.text = "API Key সেট করা নেই — «অ্যাপ খোলো» চেপে সেট করো"
            return
        }
        val started = runCatching {
            val svc = Intent(ctx, LiveSessionService::class.java)
                .setAction(LiveSessionService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(svc)
            } else {
                ctx.startService(svc)
            }
            true
        }.getOrDefault(false)
        if (!started) {
            statusText.text = "সেশন শুরু করা যায়নি — অ্যাপ খুলে চেষ্টা করো"
        }
    }

    private fun observeState() {
        if (job?.isActive == true) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        job = s.launch {
            SessionHub.state.collectLatest { render(it) }
        }
    }

    private fun stopObserving() {
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null
    }

    private fun render(st: SessionState) {
        if (!this::statusText.isInitialized) return

        statusText.text = when (st.phase) {
            Phase.CONNECTING -> "সংযোগ হচ্ছে…"
            Phase.LISTENING -> if (st.micOn) "শুনছি — বলো" else "মাইক বন্ধ"
            Phase.THINKING -> "ভাবছি…"
            Phase.SPEAKING -> "বলছি…"
            Phase.RECONNECTING -> "আবার সংযোগ হচ্ছে…"
            Phase.ERROR -> st.error ?: "সমস্যা হয়েছে"
            Phase.ENDED -> "শেষ"
            Phase.IDLE -> "তৈরি"
        }
        statusText.setTextColor(
            when (st.phase) {
                Phase.ERROR -> Color.parseColor("#FF8A9C")
                Phase.LISTENING, Phase.SPEAKING -> Color.parseColor("#35D6C8")
                else -> Color.parseColor("#C9CFE6")
            }
        )

        val said = when {
            st.modelText.isNotBlank() -> st.modelText
            st.userText.isNotBlank() -> "তুমি: " + st.userText
            st.lastTool.isNotBlank() -> st.lastTool
            else -> "হোম বাটন চেপে ধরে আমাকে ডাকলেই আমি জেগে উঠি।"
        }
        transcript.text = said

        val live = st.phase != Phase.IDLE && st.phase != Phase.ENDED
        powerButton.text = if (live) "বন্ধ করো" else "শুরু করো"
        micButton.text = if (st.micOn) "মাইক বন্ধ" else "মাইক চালু"
        micButton.isEnabled = live

        // the orb breathes with the microphone level
        val scale = 1f + (st.level.coerceIn(0f, 1f) * 0.22f)
        orb.scaleX = scale
        orb.scaleY = scale
        orb.alpha = if (live) 1f else 0.55f
    }

    // ------------------------------------------------------------------- the UI

    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics
    ).toInt()

    /** Int overload so call sites can write dp(22) as well as dp(22f). */
    private fun dp(v: Int): Int = dp(v.toFloat())

    private fun buildUi(): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(26))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0B1024"), Color.parseColor("#060A18"))
            ).apply {
                cornerRadii = floatArrayOf(
                    dp(28f).toFloat(), dp(28f).toFloat(),
                    dp(28f).toFloat(), dp(28f).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // ---- orb + title row
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        orb = View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(Color.parseColor("#7C5CFF"), Color.parseColor("#35D6C8"))
                orientation = GradientDrawable.Orientation.TL_BR
            }
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        }
        header.addView(orb)

        val titles = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        }
        titles.addView(
            TextView(ctx).apply {
                text = "Jimi"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
                setTypeface(typeface, Typeface.BOLD)
            }
        )
        statusText = TextView(ctx).apply {
            text = "তৈরি"
            setTextColor(Color.parseColor("#C9CFE6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        }
        titles.addView(statusText)
        header.addView(titles)
        root.addView(header)

        // ---- transcript
        transcript = TextView(ctx).apply {
            text = "হোম বাটন চেপে ধরে আমাকে ডাকলেই আমি জেগে উঠি।"
            setTextColor(Color.parseColor("#E7E9F5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 4
            setLineSpacing(dp(4f).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16); bottomMargin = dp(18) }
        }
        root.addView(transcript)

        // ---- controls
        val buttons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        powerButton = pill("শুরু করো", filled = true) {
            if (SessionHub.isServiceRunning) {
                LiveSessionService.stop(ctx)
            } else {
                startLiveSession()
            }
        }
        micButton = pill("মাইক চালু", filled = false) {
            val on = SessionHub.state.value.micOn
            LiveSessionService.setMic(ctx, !on)
        }
        openButton = pill("অ্যাপ খোলো", filled = false) {
            runCatching {
                ctx.startActivity(
                    Intent(ctx, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("wake", true)
                    }
                )
                hide()
            }
        }
        micButton.isEnabled = false

        buttons.addView(powerButton, weightLp(1.3f))
        buttons.addView(spacer())
        buttons.addView(micButton, weightLp(1f))
        buttons.addView(spacer())
        buttons.addView(openButton, weightLp(1f))
        root.addView(buttons)

        val frame = FrameLayout(ctx).apply {
            addView(root)
            setBackgroundColor(Color.parseColor("#060A18"))
        }
        return frame
    }

    private fun spacer() = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(8), dp(1))
    }

    private fun weightLp(w: Float) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)

    private fun pill(label: String, filled: Boolean, onClick: () -> Unit): Button =
        Button(ctx).apply {
            text = label
            isAllCaps = false
            setTextColor(if (filled) Color.WHITE else Color.parseColor("#DDE2F5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, if (filled) Typeface.BOLD else Typeface.NORMAL)
            minimumHeight = dp(44)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(50).toFloat()
                if (filled) {
                    colors = intArrayOf(Color.parseColor("#7C5CFF"), Color.parseColor("#35D6C8"))
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                } else {
                    setColor(Color.parseColor("#1BFFFFFF"))
                    setStroke(dp(1), Color.parseColor("#22FFFFFF"))
                }
            }
            setOnClickListener { onClick() }
        }
}

class JimiVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return JimiVoiceInteractionSession(this)
    }
}

/**
 * Tiny helper used by the UI to send the user straight to the right system screen.
 * Every OEM hides this behind a different menu, so we try several intents in order.
 */
object AssistantSetup {

    fun openAssistantSettings(context: Context): Boolean {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"))
                add(Intent("com.android.settings.APPLICATION_DEFAULT_APP_SETTINGS"))
            }
            add(Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS))
            add(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
        for (i in candidates) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { context.startActivity(i) }.isSuccess
            if (ok) return true
        }
        return false
    }

    fun openQuickSettingsGuide(context: Context): Boolean {
        // Most ROMs have no public intent for editing the tile list; fall back.
        return openAssistantSettings(context)
    }

    /** True when Jimi is the app the system will hand ASSIST to. */
    fun isOurActivityDefault(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_ASSIST).addCategory(Intent.CATEGORY_DEFAULT)
            val res = context.packageManager.resolveActivity(intent, 0)
            res?.activityInfo?.packageName == context.packageName
        } catch (t: Throwable) {
            false
        }
    }

    /** True when Jimi is selected as the VoiceInteractionService assistant. */
    fun isOurServiceDefault(context: Context): Boolean {
        return try {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver, "assistant"
            )
            flat?.contains(context.packageName) == true
        } catch (t: Throwable) {
            false
        }
    }

    fun overlayAllowed(context: Context): Boolean =
        android.provider.Settings.canDrawOverlays(context)

    fun openOverlaySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.packageName)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
