package com.saathi.ai.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.saathi.ai.task.TaskEngine

/**
 * Task recovery after a reboot (Phase 4 and 19).
 *
 * The task engine persists its queue, so a restart does not silently drop work the
 * user asked for. On boot this receiver reloads the error log, restores the queue
 * (interrupted runs become QUEUED again, or PAUSED when the user has switched
 * background work off) and re-registers the browser kill switch.
 *
 * Nothing here starts work on its own beyond restoring what was already accepted,
 * and every step is wrapped so a failure in one cannot block the others.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != QUICKBOOT
        ) return

        val app = context.applicationContext
        // onReceive must return quickly; the restore work is small but touches disk,
        // so it runs on its own daemon thread instead of the main one.
        Thread {
            val report = mutableListOf<String>()
            runCatching { ErrorCenter.load(app) }
                .onFailure { report.add("এরর লগ ফেরানো যায়নি: ${it.message}") }
            runCatching { report.add(TaskEngine.recover(app)) }
                .onFailure { report.add("টাস্ক রিকভারি ব্যর্থ: ${it.message}") }
            runCatching { com.saathi.ai.web.WebBrowser.registerKillSwitch() }
            runCatching { com.saathi.ai.bubble.BubbleService.publishShowing() }
            ErrorCenter.info(
                "task",
                "রিস্টার্টের পরে টাস্ক ফেরানো হয়েছে",
                report.joinToString(" · ").take(240)
            )
        }.apply { isDaemon = true; name = "jimi-boot" }.start()
    }

    private companion object {
        const val QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    }
}
