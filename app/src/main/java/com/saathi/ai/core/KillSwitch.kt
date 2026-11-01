package com.saathi.ai.core

import android.content.Context
import android.content.Intent
import com.saathi.ai.bubble.BubbleService
import com.saathi.ai.capture.LiveSessionService
import com.saathi.ai.task.TaskRunnerService

/**
 * Emergency stop (Phase 19).
 *
 * One call silences everything Jimi is doing: the voice session, the mic, the
 * screen capture, background tasks, scheduled work, generated audio and the
 * floating bubble. It exists because an assistant with this much reach must have
 * a brake the user can find without reading a manual.
 *
 * Modules that own their own resources (audio player, browser tabs, agents)
 * register a stopper with [register] during init, so this file never has to
 * import classes that may not exist yet and new subsystems are covered
 * automatically.
 */
object KillSwitch {

    private val stoppers = LinkedHashMap<String, (Context) -> String>()

    @Volatile var lastStopAt: Long = 0L
        private set

    @Volatile var lastReport: String = ""
        private set

    /**
     * Registers a named stop routine. Called once per subsystem from its own
     * init (idempotent — re-registering replaces the previous entry).
     */
    fun register(name: String, stop: (Context) -> String) {
        synchronized(stoppers) { stoppers[name] = stop }
    }

    fun unregister(name: String) {
        synchronized(stoppers) { stoppers.remove(name) }
    }

    fun registered(): List<String> = synchronized(stoppers) { stoppers.keys.toList() }

    /** Stops everything. Returns a Bangla report of what was actually stopped. */
    fun stopAll(context: Context): String {
        val ctx = context.applicationContext
        val done = mutableListOf<String>()
        val failed = mutableListOf<String>()

        // 1. Voice session (also releases the mic and any screen capture).
        step("ভয়েস সেশন", {
            LiveSessionService.setMic(ctx, false)
            LiveSessionService.stop(ctx)
            if (LiveSessionService.instance != null) "সেশন থামানোর নির্দেশ দেওয়া হয়েছে" else "সেশন চলছিল না"
        }, done, failed)

        // 2. Background tasks.
        step("ব্যাকগ্রাউন্ড টাস্ক", {
            TaskRunnerService.stop(ctx)
            "ওয়ার্কার থামানো হয়েছে"
        }, done, failed)

        // 3. Scheduled / deferred work.
        step("সময়সূচি", {
            try {
                val wm = Class.forName("androidx.work.WorkManager")
                val getInstance = wm.getMethod("getInstance", Context::class.java)
                val inst = getInstance.invoke(null, ctx)
                inst.javaClass.getMethod("cancelAllWork").invoke(inst)
                "সব নির্ধারিত কাজ বাতিল"
            } catch (t: Throwable) {
                "WorkManager নেই বা বাতিল করা যায়নি (${t.javaClass.simpleName})"
            }
        }, done, failed)

        // 4. Hotword listener.
        step("হটওয়ার্ড", {
            try {
                val cls = Class.forName("com.saathi.ai.hotword.HotwordService")
                ctx.stopService(Intent(ctx, cls))
                "হটওয়ার্ড শোনা বন্ধ"
            } catch (t: Throwable) {
                "হটওয়ার্ড সার্ভিস বন্ধ করা যায়নি"
            }
        }, done, failed)

        // 5. Bubble (hidden, not destroyed — the user can bring it back).
        step("বাবল", {
            BubbleService.autoHide(ctx)
            BubbleService.hide(ctx)
            "বাবল লুকানো হয়েছে"
        }, done, failed)

        // 6. Every subsystem that registered its own brake.
        synchronized(stoppers) { stoppers.toList() }.forEach { (name, fn) ->
            step(name, { fn(ctx) }, done, failed)
        }

        lastStopAt = System.currentTimeMillis()
        ErrorCenter.log(
            "system",
            "ইমার্জেন্সি স্টপ চাপা হয়েছে",
            "থামানো: ${done.joinToString(", ")}" + if (failed.isNotEmpty()) " | ব্যর্থ: ${failed.joinToString(", ")}" else "",
            ErrorCenter.Severity.WARNING
        )
        ErrorCenter.persist(ctx)

        lastReport = buildString {
            appendLine("সব থামানো হয়েছে।")
            if (done.isNotEmpty()) appendLine("বন্ধ হয়েছে: ${done.joinToString(" · ")}")
            if (failed.isNotEmpty()) appendLine("যা বন্ধ করা যায়নি: ${failed.joinToString(" · ")}")
            append("মাইক, স্ক্রিন ক্যাপচার ও ব্যাকগ্রাউন্ড কাজ এখন নিষ্ক্রিয়। আবার শুরু করতে হোম স্ক্রিনের গোলকে চাপ দাও।")
        }
        return lastReport
    }

    private fun step(
        name: String,
        block: () -> String,
        done: MutableList<String>,
        failed: MutableList<String>
    ) {
        try {
            val r = block()
            done.add("$name ($r)")
        } catch (t: Throwable) {
            failed.add("$name: ${t.message ?: t.javaClass.simpleName}")
            ErrorCenter.log("system", "ইমার্জেন্সি স্টপে $name থামানো যায়নি", t.message, ErrorCenter.Severity.ERROR)
        }
    }

    /** What is alive right now — used by the dashboard and the model. */
    fun status(context: Context): String = buildString {
        appendLine("চলমান অবস্থা:")
        appendLine("• ভয়েস সেশন: ${if (LiveSessionService.instance != null) "চলছে" else "বন্ধ"}")
        appendLine("• ব্যাকগ্রাউন্ড টাস্ক: ${if (TaskRunnerService.isRunning) "চলছে" else "বন্ধ"}")
        appendLine(
            "• বাবল: " + when {
                BubbleService.showing -> "দৃশ্যমান"
                BubbleService.autoHidden -> "অ্যাপ খোলা থাকায় লুকানো"
                else -> "লুকানো"
            }
        )
        appendLine("• নিবন্ধিত ব্রেক: ${registered().joinToString(", ").ifBlank { "নেই" }}")
        append(
            "• সর্বশেষ ইমার্জেন্সি স্টপ: " +
                if (lastStopAt == 0L) "কখনো নয়" else java.text.SimpleDateFormat(
                    "dd MMM HH:mm", java.util.Locale.US
                ).format(java.util.Date(lastStopAt))
        )
    }
}
