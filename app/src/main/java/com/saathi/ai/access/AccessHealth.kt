package com.saathi.ai.access

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import com.saathi.ai.R

/**
 * Keeps the accessibility service healthy.
 *
 * The "This service is malfunctioning / Jimi has stopped" dialog is Android's own
 * watchdog: it fires when a service throws, blocks the main thread, or loses its
 * connection. Three defences live here:
 *
 *  1. **Self-check** — [status] tells the UI precisely whether the service is
 *     enabled by the OS, merely connected, or was killed after being enabled.
 *  2. **One-tap repair** — [openSettings] deep-links to the exact page, and
 *     [reEnableHint] tells the user the two-tap fix when the ROM forgot the setting.
 *  3. **Model-specific advice** — MIUI/HyperOS need "Autostart" + "no battery
 *     restriction"; ColorOS needs "Allow background activity". Without those the
 *     OS kills the service after a few minutes, which is exactly the symptom the
 *     user reported.
 */
object AccessHealth {

    data class Status(
        val connected: Boolean,
        val enabledInSettings: Boolean,
        val uptimeMs: Long,
        val oem: String,
        val advice: String?
    ) {
        val healthy: Boolean get() = connected && enabledInSettings
    }

    fun status(context: Context): Status {
        val connected = JimiAccessibilityService.isEnabled()
        val enabled = JimiAccessibilityService.isFlaggedEnabled(context)
        val uptime = JimiAccessibilityService.uptimeMs()
        val oem = (Build.MANUFACTURER + " " + Build.MODEL).lowercase()

        val advice = when {
            !enabled -> "Accessibility আবার চালু করো — সেটিংস → Accessibility → Jimi → চালু"
            enabled && !connected ->
                "Android সার্ভিসটা বন্ধ করে দিয়েছে। একবার বন্ধ করে আবার চালু করলে ঠিক হয়ে যাবে।"
            oem.contains("xiaomi") || oem.contains("redmi") || oem.contains("poco") ->
                "MIUI/HyperOS: Jimi-কে Autostart দাও এবং Battery saver → No restrictions করো, নইলে সিস্টেম কয়েক মিনিট পরেই বন্ধ করে দেয়।"
            oem.contains("oppo") || oem.contains("realme") || oem.contains("oneplus") ->
                "ColorOS: Battery → Jimi → Allow background activity চালু করো।"
            oem.contains("vivo") || oem.contains("iqoo") ->
                "Funtouch OS: Background power consumption management → Jimi → Allow।"
            oem.contains("samsung") ->
                "One UI: Battery → Background usage limits → Jimi কে \"Never sleeping apps\"-এ দাও।"
            else -> null
        }

        return Status(connected, enabled, uptime, oem, advice)
    }

    fun openAccessibilitySettings(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (t: Throwable) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (t2: Throwable) {
            false
        }
    }

    /**
     * Force-rewrites the ENABLED_ACCESSIBILITY_SERVICES entry. Some ROMs drop the
     * component from the list but leave the toggle "on", which makes the service
     * impossible to start until the user toggles it twice. Only works on devices
     * where the app holds WRITE_SECURE_SETTINGS (granted via ADB), so it is a
     * best-effort silent repair — and we ignore failures.
     */
    fun tryRepair(context: Context): Boolean {
        return try {
            val expected = context.packageName + "/" +
                JimiAccessibilityService::class.java.canonicalName
            val current = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val has = TextUtils.SimpleStringSplitter(':').let { splitter ->
                splitter.setString(current)
                var found = false
                while (splitter.hasNext()) {
                    if (splitter.next().equals(expected, ignoreCase = true)) {
                        found = true
                        break
                    }
                }
                found
            }
            if (has) return false
            val updated = if (current.isBlank()) expected else "$current:$expected"
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                updated
            )
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun labelFor(context: Context): String = context.getString(R.string.app_name)
}
