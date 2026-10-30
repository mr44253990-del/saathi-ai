package com.saathi.ai.assist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log
import com.saathi.ai.MainActivity
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionHub

/**
 * Digital-assistant integration.
 *
 * Three entry points, from cheapest to deepest:
 *  1. **ASSIST intent** (android.intent.action.ASSIST on MainActivity) — works the
 *     moment the user picks Jimi as the default assistant.
 *  2. **VoiceInteractionService** below — makes Jimi selectable in
 *     Settings → Default apps → Digital assistant app, and gives the system a
 *     session to show. Real wake-word keyphrases need a dedicated DSP model, so
 *     the session starts on the system voice trigger instead.
 *  3. **Quick Settings tile** — always available, no setup.
 */
class JimiVoiceInteractionService : VoiceInteractionService() {

    private val TAG = "JimiAssistant"

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "voice interaction ready")
    }
}

/** Session shell: Jimi's real UI is the activity, so the session just launches it. */
class JimiVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        // 1. mark the session as waking, so the live service knows it is wanted
        SessionHub.update { it.copy(phase = Phase.CONNECTING, error = null) }

        // 2. start the Live session directly — this is the "hold the assistant button
        //    and it connects by itself" behaviour. If the OS refuses a background
        //    FGS start we fall through to the activity, which does the same thing
        //    while visible.
        val started = runCatching {
            val svc = Intent(context, com.saathi.ai.capture.LiveSessionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
            true
        }.getOrDefault(false)

        // 3. show the UI on top so the user can see and steer Jimi
        runCatching {
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("wake", true)
                if (!started) putExtra("forceStart", true)
            }
            context.startActivity(open)
        }
        hide()
    }
}

class JimiVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return JimiVoiceInteractionSession(this)
    }
}

/**
 * Tiny helper used by the UI to send the user straight to the right system screen.
 * Every OEM hides this behind a different menu, so we try three intents in order.
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

    fun isOurActivityDefault(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_ASSIST).addCategory(Intent.CATEGORY_DEFAULT)
            val res = context.packageManager.resolveActivity(intent, 0)
            res?.activityInfo?.packageName == context.packageName
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
