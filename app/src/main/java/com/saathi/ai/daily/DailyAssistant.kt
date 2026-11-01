package com.saathi.ai.daily

import android.content.Context
import android.content.pm.PackageManager
import com.saathi.ai.access.HealthInsights
import com.saathi.ai.access.SmartTools
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.data.MemoryStore
import com.saathi.ai.data.TaskHistory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Health and daily assistant (Phase 12).
 *
 * Built on data this app can legitimately read: device health (battery, storage,
 * RAM, network), the user's own reminders and task history. Step counts, sleep and
 * heart rate need Health Connect — when that package is absent the report says so
 * instead of inventing numbers.
 *
 * Hard rule: **no medical diagnosis.** Anything health-related ends with advice to
 * consult a doctor, and medication reminders are stored as user text, never as
 * dosage decisions.
 */
class DailyAssistant(private val context: Context) {

    private val health = HealthInsights(context)
    private val memory = MemoryStore(context)

    /** Morning brief: date, device health, today's reminders, yesterday's tasks. */
    fun report(withApiCheck: Boolean = false): String = try {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("EEEE, dd MMMM yyyy · HH:mm", Locale.US)
        buildString {
            appendLine("── আজকের সংক্ষিপ্ত বিবরণ ──")
            appendLine(fmt.format(Date(cal.timeInMillis)))
            appendLine()
            appendLine("ফোন ও সিস্টেম:")
            append(health.headline())
            appendLine()
            val issues = health.issues()
            if (issues.isNotEmpty()) {
                appendLine("যা ঠিক করা দরকার:")
                issues.take(4).forEach { appendLine("• ${it.area}: ${it.detail} → ${it.fix}") }
                appendLine()
            }
            appendLine("আজকের রিমাইন্ডার (${todayReminders().size}টি):")
            val r = todayReminders()
            if (r.isEmpty()) appendLine("• কোনো রিমাইন্ডার নেই")
            else r.take(6).forEach { appendLine("• ${it.time} — ${it.text}") }
            appendLine()
            appendLine("স্বাস্থ্য ডেটা:")
            appendLine(activityBlock())
            appendLine()
            val tasks = TaskHistory(context).recent(3)
            if (tasks.isNotEmpty()) {
                appendLine("সাম্প্রতিক টাস্ক:")
                tasks.forEach { appendLine("• [${it.status}] ${it.goal.take(60)}") }
                appendLine()
            }
            appendLine("পরামর্শ:")
            suggestions().forEach { appendLine("• $it") }
            if (withApiCheck) {
                appendLine()
                // apiReachable() is private inside HealthInsights, so read the line
                // it already prints in the full report instead of duplicating logic.
                val apiLine = health.report(true).lines().firstOrNull {
                    it.contains("API") || it.contains("মডেল") || it.contains("নেটওয়ার্ক")
                }
                appendLine("নেটওয়ার্ক/মডেল: ${apiLine ?: "বিস্তারিত হেলথ রিপোর্টে দেখো"}")
            }
            appendLine()
            append("দ্রষ্টব্য: Jimi চিকিৎসা পরামর্শ দেয় না — শরীর নিয়ে সমস্যা হলে অবশ্যই ডাক্তারের সঙ্গে কথা বলো।")
        }
    } catch (t: Throwable) {
        ErrorCenter.logThrow("system", "দৈনিক রিপোর্ট ব্যর্থ", t)
        "ব্যর্থ: আজকের বিবরণ তৈরি করা যায়নি (${t.message})"
    }

    /**
     * Activity data. Honest by design: without Health Connect installed there is no
     * steps source, and we will not guess a number.
     */
    fun activityBlock(): String {
        val hc = healthConnectPackage()
        val hasPermission = runCatching {
            context.checkSelfPermission("android.permission.health.READ_STEPS") ==
                PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val sleep = memory.all().firstOrNull { it.key.contains("sleep", true) || it.key.contains("ঘুম") }
        return buildString {
            when {
                hc == null -> appendLine("• স্টেপ/হার্ট-রেট: Health Connect অ্যাপ পাওয়া যায়নি, তাই পড়া সম্ভব নয়। " +
                    "Play Store থেকে Health Connect ইনস্টল করলে এই অংশ কাজ করবে।")
                !hasPermission -> appendLine("• স্টেপ: Health Connect আছে ($hc) কিন্তু READ_STEPS অনুমতি দেওয়া নেই — " +
                    "সেটিংসে গিয়ে অনুমতি দাও, তারপর আবার বলো।")
                else -> appendLine("• স্টেপ: অনুমতি আছে, তবে এই বিল্ডে Health Connect রিডার যুক্ত করা হয়নি — " +
                    "সংখ্যা না পেয়ে মিথ্যা বলব না।")
            }
            if (sleep != null) appendLine("• ঘুমের সময় (তোমার লেখা): ${sleep.value}")
            else appendLine("• ঘুম: কোনো তথ্য সংরক্ষিত নেই — «ঘুম ১১টা থেকে ৬টা» বললে মনে রাখব")
            append("• অনুশীলন: কোনো ফিটনেস সোর্ড সংযুক্ত নেই; চাইলে রিমাইন্ডার বসিয়ে দিতে পারি")
        }
    }

    private fun healthConnectPackage(): String? {
        val candidates = listOf("com.google.android.apps.healthdata", "com.google.android.apps.fitness")
        return candidates.firstOrNull { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }
    }

    /** Reminders whose time string mentions today's hour or is empty (all-day). */
    fun todayReminders(): List<MemoryStore.Reminder> {
        val all = memory.reminders()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return all.filter { r ->
            val t = r.time.lowercase()
            t.isBlank() || t.contains("আজ") || t.contains("today") ||
                t.contains(hourLabel(hour)) || Regex("\\d{1,2}:\\d{2}").containsMatchIn(r.time)
        }
    }

    private fun hourLabel(h: Int): String = when {
        h < 5 -> "রাত"
        h < 12 -> "সকাল"
        h < 16 -> "দুপুর"
        h < 19 -> "বিকাল"
        else -> "রাত"
    }

    /** Sleep window: stored as the user's own note, never measured or guessed. */
    fun sleepWindow(text: String = ""): String {
        if (text.isNotBlank()) {
            memory.remember("sleep-window", text.take(120), "preference")
            return "সফল: ঘুমের সময় মনে রাখা হয়েছে — $text (এটি তোমার লেখা তথ্য, ফোন থেকে মাপা নয়)"
        }
        val saved = memory.all().firstOrNull { it.key == "sleep-window" }
        return if (saved != null) "ঘুমের সময় (তোমার লেখা): ${saved.value}"
        else "ঘুমের সময় সংরক্ষিত নেই — «ঘুম ১১টা থেকে ৬টা» বললে রাখব"
    }

    /** Medication reminder — stores the user's words and hands over to SmartTools. */
    fun medReminder(medicine: String, time: String): String {
        if (medicine.isBlank()) return "ব্যর্থ: ওষুধের নাম দাও"
        val r = SmartTools(context).medicineReminder(medicine, time)
        memory.remember("med-$medicine", "সময়: $time", "health")
        return "$r\nদ্রষ্টব্য: ডোজ বা সময় পরিবর্তনের সিদ্ধান্ত ডাক্তারের — Jimi শুধু মনে করিয়ে দেয়।"
    }

    /** Practical, non-medical suggestions derived from real device state. */
    fun suggestions(): List<String> {
        val out = mutableListOf<String>()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..9 -> out.add("সকাল: আজকের ৩টি প্রধান কাজ ঠিক করে নাও — চাইলে টাস্ক ইঞ্জিনে সারি করে দিই")
            in 12..15 -> out.add("দুপুর: ২০ মিনিট চোখ বিশ্রাম দাও (২০-২০-২০ নিয়ম) — স্ক্রিন থেকে দূরে তাকাও")
            in 18..22 -> out.add("রাত: ঘুমের ১ ঘণ্টা আগে স্ক্রিন কমাও; চাইলে «ফোকাস সাউন্ড» বাজিয়ে দিতে পারি")
            else -> out.add("এই সময়ে ফোন ব্যবহার কম রাখা ভালো — প্রয়োজন না হলে ডু-নট-ডিস্টার্ব চালু রাখো")
        }
        val issues = health.issues()
        if (issues.any { it.area.contains("স্টোরেজ") || it.area.contains("storage", true) }) {
            out.add("স্টোরেজ কম — চাইলে বড় ফাইল/ক্যাশ খুঁজে দেখাই, মুছব শুধু তোমার অনুমতিতে")
        }
        if (issues.any { it.area.contains("ব্যাটারি") || it.area.contains("battery", true) }) {
            out.add("ব্যাটারি কম — ব্যাকগ্রাউন্ড টাস্ক সাময়িক বন্ধ রাখি কি?")
        }
        val missing = com.saathi.ai.access.PermissionCenter.missing(context)
        if (missing.isNotEmpty()) {
            out.add("কাজ আটকে আছে: ${missing.take(3).joinToString(", ") { it.name }} অনুমতি দরকার")
        }
        if (TaskHistory(context).all().count { it.status.contains("FAILED") } > 3) {
            out.add("কয়েকটি টাস্ক ব্যর্থ হয়েছে — টাস্ক ড্যাশবোর্ডে কারণ ও সমাধান দেখো")
        }
        out.add("শরীর খারাপ লাগলে অ্যাপের উপর নির্ভর কোরো না — ডাক্তারের পরামর্শ নাও")
        return out
    }

    /** Prepares what to carry to an appointment (user-supplied info, organised). */
    fun appointmentPrep(title: String, notes: String): String {
        val out = StringBuilder()
        out.appendLine("── অ্যাপয়েন্টমেন্ট প্রস্তুতি: $title ──")
        out.appendLine("সময়: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(Date())}")
        out.appendLine()
        out.appendLine("তোমার লেখা নোট:")
        out.appendLine(notes.ifBlank { "(কিছু লেখা নেই)" })
        out.appendLine()
        out.appendLine("সঙ্গে নিতে পারো:")
        out.appendLine("• আগের প্রেসক্রিপশন ও রিপোর্ট (চাইলে ফাইল ম্যানেজার থেকে খুঁজে দিই)")
        out.appendLine("• চলমান ওষুধের তালিকা:")
        memory.all().filter { it.kind == "health" }.take(6).forEach { out.appendLine("  - ${it.key.removePrefix("med-")}: ${it.value}") }
        out.appendLine()
        out.appendLine("ডাক্তারকে যা জিজ্ঞেস করতে পারো:")
        out.appendLine("• লক্ষণ কবে থেকে, কখন বাড়ে/কমে")
        out.appendLine("• ওষুধের ডোজ বা সময় বদলাতে হবে কি না")
        out.appendLine("• পরবর্তী রিপোর্ট/ভিজিট কবে")
        out.append("দ্রষ্টব্য: Jimi রোগ নির্ণয় করে না — এগুলো শুধু প্রস্তুতির তালিকা।")
        memory.remember("appointment-$title", notes.take(400), "health")
        return out.toString()
    }

    fun headline(): String {
        val issues = health.issues()
        val rem = todayReminders().size
        return buildString {
            append("আজ: ${if (issues.isEmpty()) "ফোন ঠিক আছে" else "${issues.size}টি সমস্যা"}")
            if (rem > 0) append(" · $remটি রিমাইন্ডার")
            val t = TaskHistory(context).recent(1).firstOrNull()
            if (t != null) append(" · শেষ টাস্ক: ${t.goal.take(30)}")
        }
    }
}
