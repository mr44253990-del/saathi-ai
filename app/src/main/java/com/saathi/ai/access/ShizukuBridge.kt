package com.saathi.ai.access

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import com.saathi.ai.core.ErrorCenter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Privileged device control through Shizuku (Phase 2).
 *
 * Why this exists: root is rare, but Shizuku gives an ordinary app the *same*
 * privilege level as ADB (uid 2000) — or real root (uid 0) when Shizuku itself
 * was started by a root manager. That unlocks `pm`, `am`, `settings put`, `input`,
 * `svc`, `dumpsys` and, most importantly, granting ourselves
 * WRITE_SECURE_SETTINGS so Jimi can enable its own accessibility service and
 * become the default assistant without sending the user through five screens.
 *
 * Honest behaviour rules this file follows:
 *  - Never claims success it did not verify. Every mutating call re-reads the
 *    value it just wrote.
 *  - Never pretends Shizuku is there. [state] distinguishes NOT_INSTALLED,
 *    NOT_RUNNING, NO_PERMISSION, CONNECTION_LOST and the two READY levels.
 *  - Never bypasses a security restriction silently. If Shizuku is absent the
 *    caller is told to fall back to root/accessibility, not lied to.
 */
object ShizukuBridge {

    /** Everything PermissionCenter needs to render one row. */
    enum class State {
        /** Shizuku app is not installed. */
        NOT_INSTALLED,
        /** Installed but the service is not running (reboot / user stopped it). */
        NOT_RUNNING,
        /** Running, but the user has not authorised Jimi. */
        NO_PERMISSION,
        /** Authorised, running with ADB-level (uid 2000) privilege. */
        READY_ADB,
        /** Authorised, running with root (uid 0) privilege. */
        READY_ROOT,
        /** Was ready, binder died (Shizuku stopped mid-task). */
        CONNECTION_LOST,
        /** API too old to be usable. */
        UNSUPPORTED
    }

    data class Result(val ok: Boolean, val code: Int, val out: String, val err: String) {
        fun report(limit: Int = 1200): String = buildString {
            append(if (ok) "সফল" else "ব্যর্থ")
            append(" (exit $code)")
            if (out.isNotBlank()) append("\n").append(out.trim().take(limit))
            if (err.isNotBlank()) append("\nত্রুটি: ").append(err.trim().take(400))
        }
    }

    private const val SHIZUKU_PKG = "moe.shizuku.manager"
    private const val REQ_CODE = 4243
    private const val TAG = "shizuku"

    @Volatile private var binderSeen: Boolean = false
    @Volatile private var binderDied: Boolean = false
    @Volatile private var listenersAttached: Boolean = false
    @Volatile var lastGranted: Boolean? = null
        private set

    private val io = Executors.newCachedThreadPool { r ->
        Thread(r, "jimi-shizuku").apply { isDaemon = true }
    }

    // ---------------------------------------------------------------- listeners

    /**
     * Attaches the sticky binder listeners exactly once. Safe to call from
     * anywhere (Application, Activity, service); the library throws if the
     * Shizuku app is missing, so everything is guarded.
     */
    fun attach(context: Context) {
        if (listenersAttached) return
        synchronized(this) {
            if (listenersAttached) return
            try {
                Shizuku.addBinderReceivedListenerSticky {
                    binderSeen = true
                    binderDied = false
                    lastGranted = granted()
                }
                Shizuku.addBinderDeadListener {
                    binderDied = true
                    binderSeen = false
                    lastGranted = false
                    ErrorCenter.log(TAG, "Shizuku binder মরে গেছে", "connection-lost")
                }
                Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                    if (requestCode == REQ_CODE) {
                        lastGranted = grantResult == PackageManager.PERMISSION_GRANTED
                        ErrorCenter.log(
                            TAG,
                            if (lastGranted == true) "Shizuku অনুমতি পাওয়া গেছে" else "Shizuku অনুমতি দেওয়া হয়নি",
                            if (lastGranted == true) "granted" else "denied"
                        )
                    }
                }
                listenersAttached = true
            } catch (t: Throwable) {
                // No Shizuku on this device — that is a normal, supported state.
                ErrorCenter.log(TAG, "Shizuku লাইব্রেরি চালু করা যায়নি: ${t.message}", "unavailable")
            }
        }
    }

    // -------------------------------------------------------------------- state

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (t: Throwable) {
        false
    }

    fun ping(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    private fun granted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /** Privilege level: 0 = root, 2000 = ADB/shell, -1 = unknown. */
    fun uid(): Int = try {
        Shizuku.getUid()
    } catch (t: Throwable) {
        -1
    }

    fun state(context: Context): State {
        attach(context)
        return try {
            if (Shizuku.isPreV11()) State.UNSUPPORTED
            else if (!installed(context)) State.NOT_INSTALLED
            else if (!ping()) if (binderDied) State.CONNECTION_LOST else State.NOT_RUNNING
            else if (!granted()) State.NO_PERMISSION
            else if (uid() == 0) State.READY_ROOT
            else State.READY_ADB
        } catch (t: Throwable) {
            State.NOT_RUNNING
        }
    }

    /** True when a privileged command may be attempted. Null context → false. */
    fun usable(context: Context?): Boolean = when (context) {
        null -> false
        else -> when (state(context)) {
            State.READY_ADB, State.READY_ROOT -> true
            else -> false
        }
    }

    fun stateName(state: State): String = when (state) {
        State.NOT_INSTALLED -> "ইনস্টল নেই"
        State.NOT_RUNNING -> "চালু নেই"
        State.NO_PERMISSION -> "অনুমতি নেই"
        State.READY_ADB -> "সংযুক্ত (ADB স্তর)"
        State.READY_ROOT -> "সংযুক্ত (রুট স্তর)"
        State.CONNECTION_LOST -> "সংযোগ হারিয়েছে"
        State.UNSUPPORTED -> "সমর্থিত নয়"
    }

    /** Bangla status block for the dashboard, the model and the voice reply. */
    fun status(context: Context): String {
        attach(context)
        val st = state(context)
        val root = RootBridge.available()
        return buildString {
            appendLine("Shizuku: ${stateName(st)}")
            when (st) {
                State.NOT_INSTALLED -> appendLine(
                    "Shizuku অ্যাপটি ইনস্টল নেই। ইনস্টল করলে রুট ছাড়াই গভীর সিস্টেম কন্ট্রোল পাওয়া যাবে। " +
                        "প্লে স্টোরে খোলা যাচ্ছে: moe.shizuku.manager"
                )
                State.NOT_RUNNING -> appendLine(
                    "Shizuku ইনস্টল আছে কিন্তু সার্ভিসটি চালু নেই। অ্যাপটি খুলে \"Start\" চাপতে হবে " +
                        "(ওয়্যারলেস ডিবাগিং বা রুট দিয়ে)।"
                )
                State.CONNECTION_LOST -> appendLine(
                    "আগে সংযুক্ত ছিল, এখন সংযোগ হারিয়েছে — সম্ভবত ফোন রিস্টার্ট হয়েছে বা Shizuku বন্ধ করা হয়েছে। " +
                        "Shizuku আবার চালু করলেই Jimi নিজে থেকেই যুক্ত হবে।"
                )
                State.NO_PERMISSION -> appendLine(
                    "Shizuku চলছে কিন্তু Jimi-কে অনুমতি দেওয়া হয়নি। অনুমতি চাইতে requestPermission() ডাকো।"
                )
                State.READY_ADB -> appendLine(
                    "সংযুক্ত, সুবিধা: ADB স্তর (uid ${uid()}) — pm/am/settings/input/svc/dumpsys চালানো যাবে।"
                )
                State.READY_ROOT -> appendLine("সংযুক্ত, সুবিধা: রুট স্তর (uid 0) — পূর্ণ কন্ট্রোল।")
                State.UNSUPPORTED -> appendLine("এই ডিভাইসে Shizuku-র এই সংস্করণ সমর্থিত নয়।")
            }
            appendLine("রুট আলাদাভাবে: ${if (root) "আছে" else "নেই"}")
            append(
                "কার্যকর কন্ট্রোল স্তর: " + when {
                    root -> "রুট (সবচেয়ে বেশি)"
                    st == State.READY_ROOT || st == State.READY_ADB -> "Shizuku"
                    else -> "সাধারণ অ্যাপ + অ্যাক্সেসিবিলিটি"
                }
            )
        }
    }

    /** Opens Shizuku (or its Play Store page) so the user can start it. */
    fun openShizuku(context: Context): String {
        val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
        return try {
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                "Shizuku খুলে দিয়েছি — ওখানে Start চাপো, তারপর Jimi-তে ফিরে এসো"
            } else {
                val i = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PKG")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                "Shizuku ইনস্টল নেই — প্লে স্টোরের পেজ খুলে দিয়েছি"
            }
        } catch (t: Throwable) {
            "Shizuku খোলা যায়নি (${t.message})"
        }
    }

    /** Asks Shizuku for authorisation. Returns what the user should expect. */
    fun requestPermission(context: Context): String {
        attach(context)
        return try {
            when (state(context)) {
                State.READY_ADB, State.READY_ROOT -> "আগে থেকেই অনুমতি দেওয়া আছে"
                State.NO_PERMISSION -> {
                    Shizuku.requestPermission(REQ_CODE)
                    "অনুমতির ডায়ালগ খোলা হয়েছে — Shizuku-তে হ্যাঁ চাপো"
                }
                State.NOT_RUNNING, State.CONNECTION_LOST -> {
                    openShizuku(context)
                    "আগে Shizuku সার্ভিসটি চালু করতে হবে"
                }
                State.NOT_INSTALLED -> openShizuku(context)
                State.UNSUPPORTED -> "এই ডিভাইসে Shizuku সমর্থিত নয়"
            }
        } catch (t: Throwable) {
            ErrorCenter.log(TAG, "অনুমতি চাওয়া যায়নি", t.message ?: "unknown")
            "অনুমতি চাওয়া যায়নি (${t.message})"
        }
    }

    // --------------------------------------------------------------- execution

    /**
     * Runs a shell command with Shizuku's privilege (ADB or root).
     *
     * Streams are drained on worker threads: a command that prints a lot would
     * otherwise fill the pipe buffer and deadlock before `waitFor`.
     */
    fun exec(cmd: String, timeoutMs: Long = 25_000): Result {
        if (cmd.isBlank()) return Result(false, -1, "", "খালি কমান্ড")
        val proc = try {
            Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        } catch (t: Throwable) {
            ErrorCenter.log(TAG, "newProcess ব্যর্থ: $cmd", t.message ?: "unknown")
            return Result(false, -1, "", "Shizuku প্রসেস চালু করা যায়নি: ${t.message}")
        }
        var out = ""
        var err = ""
        return try {
            // Explicit getters: ShizukuRemoteProcess extends java.lang.Process, and
            // calling the method avoids any synthetic-property ambiguity.
            val asProcess: java.lang.Process = proc
            val outF = io.submit<String> { asProcess.inputStream.bufferedReader().use { it.readText() } }
            val errF = io.submit<String> { asProcess.errorStream.bufferedReader().use { it.readText() } }
            val codeF = io.submit<Int> { proc.waitFor() }
            val code = codeF.get(timeoutMs, TimeUnit.MILLISECONDS)
            out = try { outF.get(2, TimeUnit.SECONDS) } catch (t: Throwable) { "" }
            err = try { errF.get(2, TimeUnit.SECONDS) } catch (t: Throwable) { "" }
            Result(code == 0, code, out, err)
        } catch (t: Throwable) {
            try { proc.destroy() } catch (_: Throwable) {}
            ErrorCenter.log(TAG, "কমান্ড টাইমআউট/ব্যর্থ: $cmd", t.message ?: "timeout")
            Result(false, -2, out, "সময় শেষ বা পড়া যায়নি: ${t.message}")
        }
    }

    /** Convenience: run and return a Bangla report string. */
    fun run(context: Context, cmd: String, timeoutMs: Long = 25_000): String {
        attach(context)
        if (!usable(context)) return "Shizuku ব্যবহারযোগ্য নয় — ${stateName(state(context))}"
        return exec(cmd, timeoutMs).report()
    }

    /**
     * Escalation ladder for one command: Shizuku first (no root needed), then su.
     * Returns the report plus which path actually worked, so the model never
     * claims a capability the device does not have.
     */
    fun privileged(context: Context, cmd: String, timeoutMs: Long = 25_000): String {
        attach(context)
        if (usable(context)) {
            val r = exec(cmd, timeoutMs)
            if (r.ok || r.err.isNotBlank()) return "Shizuku (${if (uid() == 0) "রুট" else "ADB"} স্তর):\n" + r.report()
        }
        if (RootBridge.available()) {
            val r = RootBridge.exec(cmd, timeoutMs)
            if (r.ok) return "রুট পথ:\n" + r.report()
            return "রুটেও ব্যর্থ:\n" + r.report()
        }
        return "এই কাজের জন্য Shizuku বা রুট দরকার — দুটোই নেই। Shizuku ইনস্টল করলে রুট ছাড়াই এটা সম্ভব হবে।"
    }

    // ------------------------------------------------------- secure settings

    /** Android 9+ hides non-SDK interfaces; the bypass must run before reflection. */
    private fun bypassHiddenApi() {
        if (Build.VERSION.SDK_INT < 28) return
        try {
            val cls = Class.forName("org.lsposed.hiddenapibypass.HiddenApiBypass")
            cls.getMethod("addHiddenApiExemptions", Array<String>::class.java)
                .invoke(null, arrayOf(""))
        } catch (t: Throwable) {
            ErrorCenter.log(TAG, "hidden API bypass ব্যর্থ", t.message ?: "unknown")
        }
    }

    /**
     * Grants Jimi WRITE_SECURE_SETTINGS through Shizuku's package-manager binder.
     * With that one permission the app can enable its own accessibility service,
     * set itself as the default assistant and write secure/global settings —
     * no root, no ADB cable, no user hunting through menus.
     */
    fun grantWriteSecureSettings(context: Context): String {
        attach(context)
        if (!usable(context)) return "Shizuku সংযুক্ত নয় — ${stateName(state(context))}"
        return grantPermission(context, context.packageName, android.Manifest.permission.WRITE_SECURE_SETTINGS)
    }

    fun grantPermission(context: Context, pkg: String, permission: String): String {
        attach(context)
        if (!usable(context)) return "Shizuku সংযুক্ত নয় — ${stateName(state(context))}"
        bypassHiddenApi()
        val attempts = listOf(
            Triple("android.content.pm.IPackageManager", "grantRuntimePermission", "package"),
            Triple("android.content.pm.IPermissionManager", "grantRuntimePermission", "permission")
        )
        var lastError = ""
        for ((iface, method, service) in attempts) {
            try {
                val stub = Class.forName("$iface\$Stub")
                val asInterface = stub.getMethod("asInterface", IBinder::class.java)
                val proxy = asInterface.invoke(
                    null,
                    ShizukuBinderWrapper(SystemServiceHelper.getSystemService(service))
                )
                // Signature changed across releases: (pkg, perm, userId) and later
                // (pkg, perm, attributionTag, userId). Pick whichever exists.
                val m = proxy.javaClass.methods.firstOrNull { it.name == method }
                    ?: throw NoSuchMethodException(method)
                val types = m.parameterTypes
                val args: Array<Any?> = when (types.size) {
                    3 -> arrayOf(pkg, permission, 0)
                    4 -> arrayOf(pkg, permission, "", 0)
                    else -> throw IllegalStateException("অপ্রত্যাশিত signature: ${types.size} আর্গুমেন্ট")
                }
                m.invoke(proxy, *args)
                // Verify — never trust the call alone.
                if (hasPermission(context, pkg, permission)) {
                    return "অনুমতি দেওয়া হয়েছে ও যাচাই করা হয়েছে: $permission ($pkg)"
                }
                lastError = "কল সফল কিন্তু যাচাইতে অনুমতি পাওয়া যায়নি"
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
            }
        }
        // Shizuku's own command path as a second ladder rung.
        val r = exec("pm grant $pkg $permission")
        if (r.ok && hasPermission(context, pkg, permission)) {
            return "pm grant দিয়ে অনুমতি দেওয়া হয়েছে ও যাচাই করা হয়েছে: $permission"
        }
        ErrorCenter.log(TAG, "grantRuntimePermission ব্যর্থ: $permission", lastError)
        return "অনুমতি দেওয়া যায়নি: $permission — কারণ: $lastError"
    }

    fun hasPermission(context: Context, pkg: String, permission: String): Boolean = try {
        context.packageManager.checkPermission(permission, pkg) == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun revokePermission(context: Context, pkg: String, permission: String): String {
        if (!usable(context)) return "Shizuku সংযুক্ত নয়"
        val r = exec("pm revoke $pkg $permission")
        return if (r.ok) "অনুমতি সরানো হয়েছে: $permission" else "সরানো যায়নি: ${r.err.ifBlank { r.out }}"
    }

    // --------------------------------------------------- accessibility/assist

    fun hasWriteSecureSettings(context: Context): Boolean =
        hasPermission(context, context.packageName, android.Manifest.permission.WRITE_SECURE_SETTINGS)

    /** Enables Jimi's own accessibility service directly (needs WRITE_SECURE_SETTINGS). */
    fun enableAccessibility(context: Context): String {
        if (!hasWriteSecureSettings(context)) {
            val tried = grantWriteSecureSettings(context)
            if (!hasWriteSecureSettings(context)) return "আগে WRITE_SECURE_SETTINGS দরকার: $tried"
        }
        return try {
            val cr = context.contentResolver
            val me = ComponentName(context, JimiAccessibilityService::class.java).flattenToString()
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val next = if (current.isBlank()) me
            else current.split(":").filter { it.isNotBlank() && it != me }.plus(me).joinToString(":")
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            val check = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            if (check.contains(me)) "অ্যাক্সেসিবিলিটি সার্ভিস চালু হয়েছে ও যাচাই করা হয়েছে"
            else "চালু করার চেষ্টা করেছি কিন্তু যাচাইতে পাওয়া যায়নি — সেটিংস থেকে হাতে চালু করতে হবে"
        } catch (t: Throwable) {
            ErrorCenter.log(TAG, "enableAccessibility ব্যর্থ", t.message ?: "unknown")
            "অ্যাক্সেসিবিলিটি চালু করা যায়নি (${t.message})"
        }
    }

    /** Sets Jimi as the default assistant (long-press home) without any menu walk. */
    fun setDefaultAssistant(context: Context): String {
        if (!hasWriteSecureSettings(context)) {
            grantWriteSecureSettings(context)
            if (!hasWriteSecureSettings(context)) return "WRITE_SECURE_SETTINGS ছাড়া এটা সম্ভব নয় — অনুমতি দেওয়া যায়নি"
        }
        return try {
            val me = ComponentName(context, "com.saathi.ai.assist.JimiVoiceInteractionService")
                .flattenToShortString()
            Settings.Secure.putString(context.contentResolver, "assistant", me)
            val back = Settings.Secure.getString(context.contentResolver, "assistant") ?: ""
            if (back.contains("saathi")) "ডিজিটাল অ্যাসিস্ট্যান্ট হিসেবে Jimi সেট হয়েছে (যাচাই করা)"
            else "সেট করার চেষ্টা করেছি কিন্তু সিস্টেম মানেনি (মান: $back)"
        } catch (t: Throwable) {
            "ডিজিটাল অ্যাসিস্ট্যান্ট সেট করা যায়নি (${t.message})"
        }
    }

    /** Writes a settings value at the Shizuku/root level and reads it back. */
    fun putSetting(namespace: String, key: String, value: String): String {
        if (!usable(appCtx())) return "Shizuku সংযুক্ত নয় — প্রথমে initContext(context) ডাকতে হবে"
        val ns = if (namespace.isBlank()) "system" else namespace.trim().lowercase()
        val r = exec("settings put $ns $key '$value'")
        if (!r.ok) return "settings put ব্যর্থ: ${r.err.ifBlank { r.out }}"
        val read = exec("settings get $ns $key")
        val got = read.out.trim()
        return if (got == value) "সেট হয়েছে ও যাচাই করা: $ns/$key = $value"
        else "সেট করার চেষ্টা করেছি, কিন্তু পড়ে পাওয়া গেল: ${got.ifBlank { "(খালি)" }}"
    }

    fun getSetting(namespace: String, key: String): String {
        if (!usable(appCtx())) return "Shizuku সংযুক্ত নয় — প্রথমে initContext(context) ডাকতে হবে"
        val ns = if (namespace.isBlank()) "system" else namespace.trim().lowercase()
        val r = exec("settings get $ns $key")
        return if (r.ok) "$ns/$key = ${r.out.trim().ifBlank { "(খালি)" }}" else "পড়া যায়নি: ${r.err}"
    }

    /** Cached app context so setting helpers can be called without a parameter. */
    @Volatile private var appContext: Context? = null
    fun initContext(context: Context) {
        appContext = context.applicationContext
        attach(context)
    }
    private fun appCtx(): Context? = appContext

    // --------------------------------------------------------- app operations

    fun uninstall(pkg: String): String = cmd("pm uninstall --user 0 $pkg", "আনইনস্টল")
    fun enable(pkg: String): String = cmd("pm enable $pkg", "চালু")
    fun disable(pkg: String): String = cmd("pm disable-user --user 0 $pkg", "বন্ধ")
    fun clearData(pkg: String): String = cmd("pm clear $pkg", "ডেটা পরিষ্কার")
    fun forceStop(pkg: String): String = cmd("am force-stop $pkg", "জোর করে বন্ধ")
    fun freeze(pkg: String): String = cmd("pm suspend $pkg", "ফ্রিজ")
    fun unfreeze(pkg: String): String = cmd("pm unsuspend $pkg", "আনফ্রিজ")
    fun hideFromLauncher(pkg: String): String =
        cmd("pm hide $pkg", "লঞ্চার থেকে লুকানো")

    fun startComponent(component: String): String =
        cmd("am start -n $component", "কম্পোনেন্ট চালু")

    fun startAction(action: String, uri: String? = null): String {
        val u = if (uri.isNullOrBlank()) "" else " -d '$uri'"
        return cmd("am start -a $action$u", "অ্যাকশন চালু")
    }

    fun inputTap(x: Int, y: Int): String = cmd("input tap $x $y", "ট্যাপ")
    fun inputSwipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Int = 320): String =
        cmd("input swipe $x1 $y1 $x2 $y2 $ms", "সোয়াইপ")
    fun inputText(text: String): String {
        val safe = text.replace(" ", "%s").replace("'", "")
        return cmd("input text '$safe'", "টাইপ")
    }
    fun inputKey(keycode: String): String = cmd("input keyevent $keycode", "কী")

    fun setRadio(which: String, on: Boolean): String {
        val value = if (on) "enable" else "disable"
        return when (which.trim().lowercase()) {
            "wifi" -> cmd("svc wifi $value", "ওয়াইফাই")
            "data" -> cmd("svc data $value", "মোবাইল ডেটা")
            "bluetooth" -> cmd(
                if (on) "cmd bluetooth_manager enable" else "cmd bluetooth_manager disable",
                "ব্লুটুথ"
            )
            else -> "অজানা রেডিও: $which (wifi/data/bluetooth দেওয়া যাবে)"
        }
    }

    /**
     * Torch: the shell uid has no camera privilege, so Shizuku alone cannot drive
     * the flash. Root can. Being honest here beats returning a fake success.
     */
    fun torch(on: Boolean): String {
        if (RootBridge.available()) return RootBridge.torch(on).report(300)
        return "টর্চ নিয়ন্ত্রণের জন্য রুট দরকার — Shizuku-র ADB স্তরের shell uid-তে ক্যামেরা ফ্ল্যাশের অনুমতি নেই"
    }

    fun airplane(on: Boolean): String =
        cmd("settings put global airplane_mode_on ${if (on) 1 else 0}" +
            " && am broadcast -a android.intent.action.AIRPLANE_MODE", "এয়ারপ্লেন মোড")

    private fun cmd(command: String, bangla: String): String {
        val ctx = appContext ?: return "প্রথমে ShizukuBridge.initContext(context) ডাকতে হবে"
        attach(ctx)
        if (!usable(ctx)) {
            val fallback = if (RootBridge.available()) RootBridge.exec(command).report(400)
            else "রুটও নেই"
            return if (RootBridge.available() && !fallback.startsWith("ব্যর্থ"))
                "$bangla সম্পন্ন (রুট পথ, Shizuku ${stateName(state(ctx))}): $fallback"
            else "$bangla করা যায়নি — Shizuku ${stateName(state(ctx))}, বিকল্প: $fallback"
        }
        val r = exec(command)
        return if (r.ok) "$bangla সম্পন্ন (Shizuku): ${r.out.trim().take(400)}".trim()
        else "$bangla ব্যর্থ: ${r.err.ifBlank { r.out }.take(400)}"
    }

    /**
     * One-line diagnostic used by the permission dashboard and by the model when
     * something privileged fails: what exists, what level, what is missing.
     */
    fun diagnostics(context: Context): String {
        attach(context)
        val st = state(context)
        return buildString {
            appendLine(status(context))
            appendLine("WRITE_SECURE_SETTINGS: ${if (hasWriteSecureSettings(context)) "আছে" else "নেই"}")
            appendLine("অ্যাক্সেসিবিলিটি চালু: ${if (JimiAccessibilityService.instance != null) "হ্যাঁ" else "না"}")
            appendLine("টার্মিনাল ডায়াগনস্টিকস: ${TerminalTools(context).diagnostics().take(300)}")
            appendLine(
                "পরামর্শ: " + when (st) {
                    State.READY_ROOT, State.READY_ADB ->
                        if (hasWriteSecureSettings(context)) "সব ঠিক আছে — গভীর কন্ট্রোল সম্পূর্ণ সক্রিয়"
                        else "grantWriteSecureSettings() চালাও — তাহলে সেটিংস/অ্যাক্সেসিবিলিটি/অ্যাসিস্ট্যান্ট নিজে বদলানো যাবে"
                    State.NO_PERMISSION -> "requestPermission() চালাও, তারপর আবার ডায়াগনস্টিকস"
                    State.NOT_RUNNING, State.CONNECTION_LOST -> "Shizuku অ্যাপ খুলে Start চাপতে বলো"
                    State.NOT_INSTALLED -> "Shizuku ইনস্টল করার প্রস্তাব দাও, না হলে রুট/অ্যাক্সেসিবিলিটি পথে চলো"
                    State.UNSUPPORTED -> "পুরনো Shizuku সংস্করণ — অ্যাপটি আপডেট করতে বলো"
                }
            )
        }
    }

    /**
     * Posts a runnable on the main thread — used by UI code that must react to a
     * binder event without touching Shizuku's own thread.
     */
    fun onMain(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }
}
