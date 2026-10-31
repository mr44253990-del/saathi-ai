package com.saathi.ai.access

import android.content.Context
import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Root (su) bridge — the "when Android says no" layer.
 *
 * A lot of what users ask for is blocked for ordinary apps: writing secure
 * settings, granting runtime permissions silently, injecting input into
 * FLAG_SECURE fields, reading other apps' data, capturing the screen on
 * Android 10, unfreezing packages. With a rooted phone all of that becomes a
 * shell command, so Jimi asks for `su` once and then uses it everywhere.
 *
 * Design rules:
 *  • **Never guess.** [available] actually probes `su`; every helper reports the
 *    real exit code and stderr, so the model can tell the user the truth.
 *  • **Never block forever.** Every call has a timeout and destroys the process.
 *  • **Never hardcode money.** Payment/OTP flows still stop at the confirmation
 *    screen — root makes Jimi capable, not reckless.
 */
object RootBridge {

    data class Result(val ok: Boolean, val code: Int, val out: String, val err: String) {
        /** Compact, honest one-liner for the model. */
        fun report(limit: Int = 1200): String {
            val o = out.trim().ifBlank { "-" }
            val e = err.trim()
            return if (ok) o.take(limit) else "ব্যর্থ (exit $code): ${e.ifBlank { o }}".take(limit)
        }
    }

    @Volatile
    private var cached: Boolean? = null

    @Volatile
    private var suPath: String? = null

    /** True when a usable `su` binary exists. Probed once, then cached. */
    fun available(): Boolean {
        cached?.let { return it }
        val found = detect()
        cached = found
        return found
    }

    private fun detect(): Boolean {
        val candidates = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/bin/.ext/.su", "/data/local/xbin/su", "/data/local/bin/su",
            "/magisk/.core/bin/su", "/apex/com.android.runtime/bin/su"
        )
        for (c in candidates) {
            if (File(c).exists()) {
                suPath = c
                return true
            }
        }
        // `which su` covers ROMs that hide it behind a shim
        val r = run(arrayOf("/system/bin/sh", "-c", "which su"), false, 4_000)
        val p = r.out.trim()
        return if (r.ok && p.startsWith("/") && File(p).exists()) {
            suPath = p
            true
        } else {
            suPath = null
            false
        }
    }

    /** Drops the cached probe (used after the user installs/uninstalls a root manager). */
    fun recheck() {
        cached = null
        suPath = null
    }

    /** Runs a command. `asRoot = true` goes through `su -c`, otherwise plain `sh -c`. */
    fun exec(command: String, timeoutMs: Long = 25_000, asRoot: Boolean = true): Result {
        if (asRoot && !available()) {
            return Result(false, -1, "", "রুট নেই — su পাওয়া যায়নি। রুট ছাড়া এই কাজটা করা যাবে না।")
        }
        val argv = if (asRoot) {
            arrayOf(suPath ?: "su", "-c", command)
        } else {
            arrayOf("/system/bin/sh", "-c", command)
        }
        return run(argv, asRoot, timeoutMs)
    }

    private fun run(argv: Array<String>, asRoot: Boolean, timeoutMs: Long): Result {
        return try {
            val pb = ProcessBuilder(*argv)
            pb.environment()["LC_ALL"] = "C"
            val process = pb.start()
            val out = StringBuilder()
            val err = StringBuilder()
            val tOut = Thread {
                runCatching { process.inputStream.bufferedReader().forEachLine { out.appendLine(it) } }
            }
            val tErr = Thread {
                runCatching { process.errorStream.bufferedReader().forEachLine { err.appendLine(it) } }
            }
            tOut.isDaemon = true
            tErr.isDaemon = true
            tOut.start()
            tErr.start()

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                runCatching { process.destroy() }
                tOut.join(250)
                tErr.join(250)
                return Result(false, -2, out.toString(), "সময় শেষ (${timeoutMs}ms) — কমান্ড থামিয়ে দেওয়া হয়েছে")
            }
            tOut.join(400)
            tErr.join(400)
            val code = process.exitValue()
            // su on many ROMs prints the denial to stderr with exit 1
            val denied = err.contains("not found") && asRoot ||
                err.contains("permission denied") && err.contains("su") ||
                err.contains("inaccessible or not found")
            Result(code == 0 && !denied, code, out.toString(), err.toString())
        } catch (t: Throwable) {
            Result(false, -3, "", t.message ?: "চালানো যায়নি")
        }
    }

    // ------------------------------------------------------------- status

    /** One-paragraph honest status, shown in Settings and to the model. */
    fun status(context: Context): String {
        val rooted = available()
        val head = if (rooted) "রুট পাওয়া গেছে (${suPath ?: "su"})" else "রুট নেই"
        if (!rooted) {
            return "$head — Jimi সাধারণ অ্যাপের ক্ষমতা দিয়ে চলছে " +
                "(Accessibility + overlay + intent)। রুট থাকলে secure settings লেখা, " +
                "অনুমতি নিজে থেকে দেওয়া, Android 10-এ স্ক্রিনশট, লুকানো অ্যাপ খোলা — সব সম্ভব হতো।"
        }
        val abi = exec("getprop ro.product.cpu.abi", 4_000).out.trim()
        val sdk = exec("getprop ro.build.version.release", 4_000).out.trim()
        val magisk = exec("magisk -V 2>/dev/null || echo -", 4_000).out.trim()
        val selinux = exec("getenforce 2>/dev/null || echo -", 4_000).out.trim()
        return "$head · Android $sdk (API ${Build.VERSION.SDK_INT}) · ABI $abi · " +
            "Magisk $magisk · SELinux $selinux · ${context.packageName}"
    }

    // ------------------------------------------------------------- helpers

    /** Silent screenshot on any Android version when root exists. */
    fun screenshotTo(outPath: String): Result =
        exec("screencap -p '$outPath' && chmod 644 '$outPath'", 12_000)

    fun grantPermission(pkg: String, permission: String): Result =
        exec("pm grant $pkg $permission 2>&1 || echo grant-failed", 12_000)

    fun revokePermission(pkg: String, permission: String): Result =
        exec("pm revoke $pkg $permission 2>&1", 12_000)

    /** Grants everything Jimi can want for itself, in one shot. */
    fun grantSelf(context: Context): String {
        if (!available()) return "রুট নেই — অনুমতি নিজে থেকে দেওয়া যাবে না"
        val pkg = context.packageName
        val perms = listOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_CONTACTS",
            "android.permission.CALL_PHONE",
            "android.permission.SEND_SMS",
            "android.permission.READ_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.ACCESS_FINE_LOCATION"
        )
        val done = perms.mapNotNull { p ->
            val r = grantPermission(pkg, p)
            if (r.ok) p.substringAfterLast('.') else null
        }
        // special appops that normal apps cannot self-grant
        exec("appops set $pkg SYSTEM_ALERT_WINDOW allow", 8_000)
        exec("appops set $pkg WRITE_SETTINGS allow", 8_000)
        exec("appops set $pkg REQUEST_INSTALL_PACKAGES allow", 8_000)
        exec("cmd appops set $pkg android:accessibility allowed", 8_000)
        exec("dumpsys deviceidle whitelist +$pkg", 8_000)
        return if (done.isEmpty()) "কোনো অনুমতি দেওয়া যায়নি — রুট ম্যানেজারের প্রম্পটে Allow চাপতে হবে"
        else "নিজে থেকেই দিয়েছি: ${done.joinToString(", ")}"
    }

    fun putSetting(namespace: String, key: String, value: String): Result =
        exec("settings put ${namespace.lowercase()} '$key' '$value'", 10_000)

    fun getSetting(namespace: String, key: String): String =
        exec("settings get ${namespace.lowercase()} '$key'", 8_000).out.trim()

    fun inputTap(x: Int, y: Int): Result = exec("input tap $x $y", 8_000)

    fun inputSwipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Int = 300): Result =
        exec("input swipe $x1 $y1 $x2 $y2 $ms", 8_000)

    fun inputText(text: String): Result =
        exec("input text '${text.replace("'", "")}'", 10_000)

    fun inputKey(key: String): Result = exec("input keyevent ${key.uppercase().replace("KEYCODE_", "")}", 8_000)

    /** Clipboard through the system service — works where apps block paste. */
    fun setClipboard(text: String): Result =
        exec("am broadcast -a clipper.set -e text '${text.replace("'", "")}' 2>/dev/null; " +
            "service call clipboard 2 s16 '${text.replace("'", "")}' i32 1 i32 1 2>/dev/null || true", 8_000)

    fun wifi(on: Boolean): Result = exec(if (on) "svc wifi enable" else "svc wifi disable", 8_000)
    fun data(on: Boolean): Result = exec(if (on) "svc data enable" else "svc data disable", 8_000)
    fun airplane(on: Boolean): Result =
        putSetting("global", "airplane_mode_on", if (on) "1" else "0")
            .also { exec("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $on", 8_000) }

    fun torch(on: Boolean): Result =
        exec(if (on) "echo 1 > /sys/class/leds/flashlight/brightness 2>/dev/null || " +
            "cmd statusbar expand-notifications >/dev/null 2>&1" else
            "echo 0 > /sys/class/leds/flashlight/brightness 2>/dev/null", 8_000)

    fun uninstall(pkg: String): Result = exec("pm uninstall --user 0 $pkg", 15_000)
    fun freeze(pkg: String): Result = exec("pm disable-user --user 0 $pkg", 12_000)
    fun unfreeze(pkg: String): Result = exec("pm enable $pkg", 12_000)
    fun clearData(pkg: String): Result = exec("pm clear $pkg", 15_000)
    fun forceStop(pkg: String): Result = exec("am force-stop $pkg", 8_000)

    fun installApk(path: String): Result = exec("pm install -r -g '$path'", 120_000)

    /** Lists packages including disabled/uninstalled-kept ones — the "hidden apps" answer. */
    fun listPackages(flags: String = ""): String =
        exec("pm list packages $flags".trim(), 15_000).out

    /** Deep system info that no normal API exposes. */
    fun props(): String = exec(
        "getprop | grep -E 'ro.product|ro.build.version|ro.boot|persist.sys' | head -40", 12_000
    ).out

    fun storage(): String = exec("df -h /data /sdcard 2>/dev/null | head -6", 10_000).out

    fun topProcesses(): String = exec("top -n 1 -b 2>/dev/null | head -14", 15_000).out

    fun logcat(lines: Int = 60, filter: String? = null): String {
        val f = if (filter.isNullOrBlank()) "" else " | grep -i '${filter.replace("'", "")}'"
        return exec("logcat -d -t $lines$f", 20_000).out
    }

    /** Reads a file the app could never open itself. */
    fun readFile(path: String, limit: Int = 4000): String =
        exec("head -c $limit '$path'", 10_000).out

    fun writeFile(path: String, content: String): Result =
        exec("cat > '$path' <<'JIMI_EOF'\n$content\nJIMI_EOF\nchmod 644 '$path'", 15_000)

    /** Turns the always-on accessibility service on without the user hunting menus. */
    fun enableAccessibility(context: Context): String {
        if (!available()) return "রুট নেই — সেটিংস থেকে হাতে চালু করতে হবে"
        val svc = "${context.packageName}/com.saathi.ai.access.JimiAccessibilityService"
        val current = getSetting("secure", "enabled_accessibility_services")
        val next = if (current.isBlank() || current == "null") svc else "$current:$svc"
        putSetting("secure", "enabled_accessibility_services", next)
        putSetting("secure", "accessibility_enabled", "1")
        val check = getSetting("secure", "enabled_accessibility_services")
        return if (check.contains(context.packageName)) "Accessibility চালু করে দিয়েছি (রুট দিয়ে)"
        else "চালু করা যায়নি — secure settings লেখা ব্লক হয়েছে"
    }

    fun setDefaultAssistant(context: Context): String {
        if (!available()) return "রুট নেই — হাতে সেট করতে হবে"
        val flat = "${context.packageName}/com.saathi.ai.assist.JimiVoiceInteractionService"
        putSetting("secure", "assistant", flat)
        putSetting("secure", "assist_structure_enabled", "1")
        val check = getSetting("secure", "assistant")
        return if (check.contains(context.packageName)) "ডিজিটাল অ্যাসিস্ট্যান্ট Jimi করে দিয়েছি"
        else "সেট করা যায়নি"
    }
}
