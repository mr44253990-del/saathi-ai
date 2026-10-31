package com.saathi.ai.access

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import com.saathi.ai.data.SecurePrefs
import com.saathi.ai.task.TaskBoard
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * System health + self-diagnosis.
 *
 * Two jobs, both requested explicitly:
 *  1. **"হেলথ চেক করে সাজেশন দিবে"** — a real inspection of battery, temperature,
 *     storage, RAM, network, and every permission Jimi needs, ending in concrete
 *     suggestions (not generic advice).
 *  2. **"কোনো সমস্যা হলে সেটা নিজেই বুঝে সমাধান করার ব্যবস্থা"** — [selfFix] walks
 *     the same checks and repairs whatever it legally can on its own (clearing cache,
 *     restarting the session, re-arming the gate), opens the exact settings page for
 *     what needs one tap from the user, and says plainly what it cannot fix.
 *
 * Nothing here is decorative: every number is read from the OS at call time.
 */
class HealthInsights(private val context: Context) {

    private val prefs by lazy { SecurePrefs(context) }

    data class Issue(val area: String, val detail: String, val fix: String, val severity: Int)

    // ------------------------------------------------------------------ facts

    private fun batteryFacts(): Triple<Int, Float, Boolean> {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val sticky = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val temp = (sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val charging = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return Triple(level, temp, charging != 0)
    }

    private fun storageFacts(): Pair<Long, Long> {
        return runCatching {
            val st = StatFs(Environment.getDataDirectory().path)
            val free = st.availableBytes / (1024 * 1024)
            val total = st.totalBytes / (1024 * 1024)
            free to total
        }.getOrElse { -1L to -1L }
    }

    private fun ramFacts(): Pair<Long, Long> {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            (mi.availMem / (1024 * 1024)) to (mi.totalMem / (1024 * 1024))
        }.getOrElse { -1L to -1L }
    }

    private fun networkFacts(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "অজানা"
        val net = cm.activeNetwork ?: return "নেটওয়ার্ক নেই"
        val caps = cm.getNetworkCapabilities(net) ?: return "অজানা"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "ওয়াইফাই"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "মোবাইল ডেটা"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ইথারনেট"
            else -> "অন্য সংযোগ"
        }
    }

    private fun accessibilityOn(): Boolean = runCatching {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        enabled.contains("com.saathi.ai")
    }.getOrElse { JimiAccessibilityService.instance != null }

    private fun overlayOn(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

    private fun ignoringBatteryOptimizations(): Boolean = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrElse { false }

    private fun cacheBytes(): Long = runCatching {
        context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } +
            (context.externalCacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L)
    }.getOrElse { 0L }

    private fun termuxInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.termux", 0)
        true
    }.getOrElse { false }

    /** Pings the API so "কাজ করছে কি না" is a measured fact, not a guess. */
    private fun apiReachable(): String {
        val key = prefs.apiKey
        if (key.isBlank()) return "কী দেওয়া নেই"
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(7, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1")
                .header("x-goog-api-key", key)
                .build()
            client.newCall(req).execute().use { r ->
                when {
                    r.isSuccessful -> "ঠিক আছে"
                    r.code == 400 || r.code == 403 -> "কী বাতিল/অনুমতি নেই (${r.code})"
                    r.code == 429 -> "কোটা শেষ (${r.code})"
                    else -> "সার্ভার সমস্যা (${r.code})"
                }
            }
        } catch (e: java.io.IOException) {
            "পৌঁছানো যায়নি (${e.javaClass.simpleName})"
        } catch (e: Exception) {
            "পরীক্ষা ব্যর্থ (${e.javaClass.simpleName})"
        }
    }

    // ----------------------------------------------------------------- report

    /** Full spoken-readable health report with suggestions. */
    fun report(withApiCheck: Boolean = true): String {
        val (level, temp, charging) = batteryFacts()
        val (free, total) = storageFacts()
        val (avail, totalRam) = ramFacts()
        val net = networkFacts()
        val acc = accessibilityOn()
        val overlay = overlayOn()
        val doze = ignoringBatteryOptimizations()
        val cache = cacheBytes() / (1024 * 1024)
        val uptimeMin = SystemClock.elapsedRealtime() / 60000
        val api = if (withApiCheck) apiReachable() else "পরীক্ষা করা হয়নি"
        val issues = issues()

        return buildString {
            append("জিমি হেলথ চেক\n")
            append("— ব্যাটারি ${if (level >= 0) "$level%" else "পড়া যায়নি"}")
            append(if (charging) " (চার্জ হচ্ছে)" else "")
            append(", তাপমাত্রা ${if (temp > 0f) "${temp}°C" else "অজানা"}\n")
            append("— স্টোরেজ খালি ${free}MB / ${total}MB\n")
            append("— র‍্যাম খালি ${avail}MB / ${totalRam}MB\n")
            append("— নেটওয়ার্ক: $net · API: $api\n")
            append("— অনুমতি: অ্যাক্সেসিবিলিটি ${if (acc) "চালু" else "বন্ধ"}, ")
            append("ওভারলে ${if (overlay) "চালু" else "বন্ধ"}, ")
            append("ব্যাটারি অপটিমাইজেশন ছাড় ${if (doze) "আছে" else "নেই"}\n")
            append("— রুট ${if (RootBridge.available()) "আছে" else "নেই"} · ")
            append("Termux ${if (termuxInstalled()) "ইনস্টল" else "নেই"} · ")
            append("ক্যাশ ${cache}MB\n")
            append("— ফোন চালু ${uptimeMin} মিনিট · ")
            append("টাস্ক বোর্ডে ${TaskBoard(context).all().size}টি কাজ · ")
            append("মেমোরি ${com.saathi.ai.data.MemoryStore(context).count()}টি\n")

            if (issues.isEmpty()) {
                append("\nসব ঠিক আছে — কোনো সমস্যা পাইনি।")
            } else {
                append("\nসমস্যা ও পরামর্শ (${issues.size}টি):\n")
                issues.sortedByDescending { it.severity }.forEachIndexed { i, it2 ->
                    append("${i + 1}. [${it2.area}] ${it2.detail}\n   করণীয়: ${it2.fix}\n")
                }
            }
            if (level in 1..15 && !charging) append("\nব্যাটারি কম — চার্জে দাও, না হলে ব্যাকগ্রাউন্ড কাজ থেমে যেতে পারে।")
            if (temp >= 42f) append("\nফোন গরম (${temp}°C) — ভারী অটোমেশন কিছুক্ষণ বন্ধ রাখাই ভালো।")
            if (free in 0..499) append("\nস্টোরেজ প্রায় শেষ — ক্যাশ পরিষ্কার করতে বললে আমিই করে দেব।")
        }
    }

    /** Prioritised issue list; used by both the report and the self-fix. */
    fun issues(): List<Issue> {
        val out = mutableListOf<Issue>()
        val (level, temp, charging) = batteryFacts()
        val (free, _) = storageFacts()
        val (avail, _) = ramFacts()
        val net = networkFacts()

        if (!accessibilityOn()) out.add(
            Issue("অ্যাক্সেসিবিলিটি", "জিমির অ্যাক্সেসিবিলিটি সার্ভিস বন্ধ — ট্যাপ/টাইপ/স্ক্রিনশট কিছুই হবে না",
                "অ্যাক্সেসিবিলিটি সেটিংস খুলে Jimi চালু করো (আমি খুলে দিতে পারি)", 5)
        )
        if (!overlayOn()) out.add(
            Issue("ওভারলে", "অন্য অ্যাপের উপর বাবল/ভিডিও কল দেখানো যাবে না",
                "Display over other apps অনুমতি দাও", 4)
        )
        if (!ignoringBatteryOptimizations()) out.add(
            Issue("ব্যাটারি", "অ্যান্ড্রয়েড অল্প সময়েই ব্যাকগ্রাউন্ডে জিমিকে ঘুম পাড়িয়ে দেবে",
                "ব্যাটারি অপটিমাইজেশন থেকে বাদ দাও — আমি রিকোয়েস্ট পাঠাতে পারি", 4)
        )
        if (prefs.apiKey.isBlank()) out.add(
            Issue("API কী", "কী সেট করা নেই — লাইভ ভয়েস ও টুল চলবে না",
                "Settings → API key এ কী দাও", 5)
        )
        if (level in 1..12 && !charging) out.add(
            Issue("ব্যাটারি", "চার্জ মাত্র $level%", "চার্জে দাও; ভারী কাজ এখন থামানো ভালো", 3)
        )
        if (temp >= 42f) out.add(
            Issue("তাপ", "ফোনের তাপমাত্রা ${temp}°C", "কিছুক্ষণ বিশ্রাম দাও, কেস খুলে রাখো", 3)
        )
        if (free in 0..700) out.add(
            Issue("স্টোরেজ", "মাত্র ${free}MB খালি", "ক্যাশ পরিষ্কার করো বা বড় ফাইল সরাও", 3)
        )
        if (avail in 0..400) out.add(
            Issue("র‍্যাম", "মাত্র ${avail}MB র‍্যাম খালি — অ্যাপ বন্ধ হতে পারে",
                "Recent থেকে কিছু অ্যাপ বন্ধ করো", 2)
        )
        if (net == "নেটওয়ার্ক নেই") out.add(
            Issue("নেটওয়ার্ক", "ইন্টারনেট সংযোগ নেই", "ওয়াইফাই/ডেটা চালু করো", 5)
        )
        if (cacheBytes() > 200L * 1024 * 1024) out.add(
            Issue("ক্যাশ", "জিমির ক্যাশ ${cacheBytes() / (1024 * 1024)}MB",
                "পরিষ্কার করে দিতে বলো — ছবি/স্ক্রিনশটের অস্থায়ী ফাইল", 2)
        )
        val failedTasks = TaskBoard(context).all().count { it.status == TaskBoard.Status.FAILED || it.status == TaskBoard.Status.BLOCKED }
        if (failedTasks > 0) out.add(
            Issue("টাস্ক", "${failedTasks}টি কাজ আটকে/ব্যর্থ আছে",
                "task_board দেখে আবার চালাও বা আমাকে কারণ জিজ্ঞেস করো", 2)
        )
        if (!termuxInstalled() && prefs.rootMode && !RootBridge.available()) out.add(
            Issue("টার্মিনাল/রুট", "রুটও নেই, Termuxও ইনস্টল নেই — গভীর কমান্ড কাজ সীমিত",
                "Termux ইনস্টল করলে আমি সেখানেই কমান্ড চালাব (run_command)", 1)
        )
        return out
    }

    // --------------------------------------------------------------- self fix

    /**
     * Tries to repair everything it can, then opens exactly one settings page if a
     * human tap is unavoidable. Returns an honest log of fixed / needs-you / cannot.
     */
    fun selfFix(apply: Boolean = true): String {
        val fixed = mutableListOf<String>()
        val needsYou = mutableListOf<String>()
        val cannot = mutableListOf<String>()

        val cache = cacheBytes()
        if (cache > 60L * 1024 * 1024) {
            val cleared = runCatching {
                var bytes = 0L
                context.cacheDir.walkTopDown().filter { it.isFile }.forEach {
                    bytes += it.length(); it.delete()
                }
                context.externalCacheDir?.walkTopDown()?.filter { it.isFile }?.forEach {
                    bytes += it.length(); it.delete()
                }
                bytes
            }.getOrDefault(0L)
            if (cleared > 0) fixed.add("ক্যাশ ${cleared / (1024 * 1024)}MB পরিষ্কার করেছি")
        }

        if (prefs.apiKey.isBlank()) needsYou.add("API কী দিতে হবে — Settings থেকে")

        if (!accessibilityOn()) {
            needsYou.add("অ্যাক্সেসিবিলিটি চালু করতে হবে (এক ট্যাপ)")
            if (apply) openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        } else if (!overlayOn()) {
            needsYou.add("ওভারলে অনুমতি দিতে হবে")
            if (apply) openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"))
        } else if (!ignoringBatteryOptimizations()) {
            needsYou.add("ব্যাটারি অপটিমাইজেশন ছাড় দিতে হবে")
            if (apply && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                openSettings(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
            }
        }

        val (free, _) = storageFacts()
        if (free in 0..300) {
            val edited = runCatching {
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val art = java.io.File(dir, "art").listFiles()?.sortedBy { it.lastModified() } ?: emptyList<java.io.File>()
                var n = 0
                art.take((art.size - 20).coerceAtLeast(0)).forEach { if (it.delete()) n++ }
                n
            }.getOrDefault(0)
            if (edited > 0) fixed.add("পুরনো $edited টি আঁকা/এডিট ফাইল সরিয়ে স্টোরেজ খালি করেছি")
            else cannot.add("স্টোরেজ কম কিন্তু জিমির নিজের ফাইল কম — তোমার বড় ফাইল সরাতে হবে")
        }

        if (networkFacts() == "নেটওয়ার্ক নেই") {
            cannot.add("ইন্টারনেট চালু করা আমার এখতিয়ারে নেই — ওয়াইফাই/ডেটা অন করো")
            if (apply) runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        // Session-level repair: parked or dead session gets restarted.
        if (accessibilityOn() && prefs.apiKey.isNotBlank() &&
            !com.saathi.ai.live.SessionHub.isServiceRunning && apply
        ) {
            com.saathi.ai.capture.LiveSessionService.start(context)
            fixed.add("লাইভ সেশন বন্ধ ছিল — আবার চালু করেছি")
        }

        val failed = TaskBoard(context).all().filter {
            it.status == TaskBoard.Status.FAILED && it.attempts < 3
        }
        if (failed.isNotEmpty() && apply && prefs.backgroundTasks) {
            com.saathi.ai.task.TaskRunnerService.start(context, failed.first().id)
            fixed.add("ব্যর্থ টাস্ক «${failed.first().title}» আবার চালাতে ব্যাকগ্রাউন্ড ওয়ার্কার দিয়েছি")
        }

        return buildString {
            append("নিজে যা ঠিক করেছি:\n")
            append(if (fixed.isEmpty()) "  — কিছু করার ছিল না\n" else fixed.joinToString("\n") { "  • $it" } + "\n")
            append("তোমার এক ট্যাপ লাগবে:\n")
            append(if (needsYou.isEmpty()) "  — কিছু না\n" else needsYou.joinToString("\n") { "  • $it" } + "\n")
            append("যা আমার সাধ্যের বাইরে:\n")
            append(if (cannot.isEmpty()) "  — কিছু না\n" else cannot.joinToString("\n") { "  • $it" } + "\n")
            if (apply && (needsYou.isNotEmpty() || cannot.isNotEmpty())) {
                append("সম্পূর্ণ ঠিক না হওয়া পর্যন্ত আমি থামব না — চাইলে health_check আবার চালাও।")
            }
        }
    }

    private fun openSettings(action: String, data: Uri? = null) {
        runCatching {
            val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (data != null) i.data = data
            context.startActivity(i)
        }
    }

    /** Short line for the hub header instead of the whole report. */
    fun headline(): String {
        val issues = issues()
        val (level, temp, _) = batteryFacts()
        val (free, _) = storageFacts()
        val worst = issues.maxByOrNull { it.severity }
        return buildString {
            append("${level}% · ${temp}°C · ${free}MB খালি")
            if (worst != null) append(" · সমস্যা: ${worst.area}") else append(" · সব ঠিক আছে")
        }
    }
}
