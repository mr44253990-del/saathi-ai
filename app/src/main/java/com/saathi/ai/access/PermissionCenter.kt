package com.saathi.ai.access

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.data.SecurePrefs

/**
 * Single source of truth for "what can Jimi do on this phone?" (Phase 2).
 *
 * Five states per capability — GRANTED, NOT_GRANTED, RESTRICTED, REQUIRED,
 * CONNECTION_LOST — plus NOT_APPLICABLE for things this Android version does not
 * have. Every row carries *why* it matters and the exact tap-path to fix it, so
 * neither the user nor the model has to guess.
 *
 * Privilege ladder (highest wins): root → Shizuku → WRITE_SECURE_SETTINGS →
 * accessibility → plain runtime permissions. [effectiveLevel] reports which rung
 * the phone is actually standing on, which is what decides whether a request is
 * even possible.
 */
object PermissionCenter {

    enum class Level {
        GRANTED, NOT_GRANTED, RESTRICTED, REQUIRED, CONNECTION_LOST, NOT_APPLICABLE
    }

    /** How much the feature set suffers without this capability. */
    enum class Need { CORE, FEATURE, POWER, OPTIONAL }

    data class Row(
        val id: String,
        val name: String,
        val why: String,
        val level: Level,
        val need: Need,
        val steps: String,
        val fixable: Boolean
    )

    fun levelName(l: Level): String = when (l) {
        Level.GRANTED -> "দেওয়া আছে"
        Level.NOT_GRANTED -> "দেওয়া নেই"
        Level.RESTRICTED -> "সীমিত"
        Level.REQUIRED -> "আবশ্যক কিন্তু নেই"
        Level.CONNECTION_LOST -> "সংযোগ হারিয়েছে"
        Level.NOT_APPLICABLE -> "প্রযোজ্য নয়"
    }

    fun needName(n: Need): String = when (n) {
        Need.CORE -> "মূল"
        Need.FEATURE -> "ফিচার"
        Need.POWER -> "গভীর কন্ট্রোল"
        Need.OPTIONAL -> "ঐচ্ছিক"
    }

    private fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------- rows

    fun rows(context: Context): List<Row> {
        ShizukuBridge.attach(context)
        val list = mutableListOf<Row>()
        val sdk = Build.VERSION.SDK_INT

        fun runtime(
            id: String, permission: String, name: String, why: String, need: Need, steps: String
        ) {
            val granted = if (permission.isBlank()) false else has(context, permission)
            list.add(
                Row(
                    id, name, why,
                    if (granted) Level.GRANTED else if (need == Need.CORE) Level.REQUIRED else Level.NOT_GRANTED,
                    need, steps, true
                )
            )
        }

        runtime(
            "mic", android.Manifest.permission.RECORD_AUDIO, "মাইক",
            "কথা শোনা ও ভয়েস সেশন — এটা ছাড়া Jimi কালা", Need.CORE,
            "সেটিংস → অ্যাপ → Jimi → Permissions → Microphone → Allow"
        )
        if (sdk >= 33) runtime(
            "notifications", android.Manifest.permission.POST_NOTIFICATIONS, "নোটিফিকেশন",
            "ব্যাকগ্রাউন্ড টাস্ক, অনুমোদনের অনুরোধ ও অগ্রগতি দেখানো", Need.CORE,
            "সেটিংস → অ্যাপ → Jimi → Notifications → Allow"
        )
        runtime(
            "camera", android.Manifest.permission.CAMERA, "ক্যামেরা",
            "ছবি/ভিডিও দেখে বোঝা, লাইভ ভিশন", Need.FEATURE,
            "সেটিংস → অ্যাপ → Jimi → Permissions → Camera → Allow"
        )
        runtime(
            "call", android.Manifest.permission.CALL_PHONE, "কল করা",
            "নির্দেশ দিলে কল করা", Need.FEATURE,
            "সেটিংস → অ্যাপ → Jimi → Permissions → Phone → Allow"
        )
        runtime(
            "contacts", android.Manifest.permission.READ_CONTACTS, "কনট্যাক্ট",
            "নাম ধরে কল/মেসেজ করা", Need.FEATURE,
            "সেটিংস → অ্যাপ → Jimi → Permissions → Contacts → Allow"
        )
        runtime(
            "sms", android.Manifest.permission.SEND_SMS, "SMS পাঠানো",
            "নির্দেশ দিলে মেসেজ পাঠানো (চূড়ান্ত পাঠানোয় অনুমোদন লাগে)", Need.FEATURE,
            "সেটিংস → অ্যাপ → Jimi → Permissions → SMS → Allow"
        )
        runtime(
            "calllog", android.Manifest.permission.READ_CALL_LOG, "কল লগ",
            "কে কল করেছিল জেনে কাজ করা", Need.OPTIONAL,
            "সেটিংস → অ্যাপ → Jimi → Permissions → Call logs → Allow"
        )
        if (sdk >= 33) {
            runtime(
                "images", android.Manifest.permission.READ_MEDIA_IMAGES, "ছবি পড়া",
                "গ্যালারির ছবি এডিট/বর্ণনা/কোলাজ", Need.FEATURE,
                "সেটিংস → অ্যাপ → Jimi → Permissions → Photos → Allow all"
            )
            runtime(
                "videos", android.Manifest.permission.READ_MEDIA_VIDEO, "ভিডিও পড়া",
                "ভিডিও ফ্রেম বিশ্লেষণ ও সামারি", Need.FEATURE,
                "সেটিংস → অ্যাপ → Jimi → Permissions → Videos → Allow all"
            )
        } else runtime(
            "storage", android.Manifest.permission.READ_EXTERNAL_STORAGE, "স্টোরেজ",
            "ফাইল পড়া ও এডিট", Need.FEATURE,
            "সেটিংস → অ্যাপ → Jimi → Permissions → Storage → Allow"
        )
        if (sdk >= 29) runtime(
            "activity", android.Manifest.permission.ACTIVITY_RECOGNITION, "অ্যাক্টিভিটি",
            "হাঁটাচলা/আইডল বোঝা, ডেইলি অ্যাসিস্ট্যান্ট", Need.OPTIONAL,
            "সেটিংস → অ্যাপ → Jimi → Permissions → Physical activity → Allow"
        )

        // Overlay — a Settings toggle, not a runtime permission.
        val overlay = Settings.canDrawOverlays(context)
        list.add(
            Row(
                "overlay", "অন্য অ্যাপের উপরে দেখানো", "ফ্লোটিং বাবল ও হোল্ড-টু-অ্যাসিস্ট",
                if (overlay) Level.GRANTED else Level.REQUIRED, Need.CORE,
                "সেটিংস → Apps → Special access → Display over other apps → Jimi চালু", true
            )
        )

        // Accessibility.
        val a11y = JimiAccessibilityService.isEnabled()
        list.add(
            Row(
                "accessibility", "অ্যাক্সেসিবিলিটি সার্ভিস",
                "স্ক্রিন পড়া, ট্যাপ/টাইপ/স্ক্রল, অটোপাইলট ও স্ক্রিনশট ভিশন",
                if (a11y) Level.GRANTED else Level.REQUIRED, Need.CORE,
                "সেটিংস → Accessibility → Downloaded services → Jimi → চালু করো", true
            )
        )

        // Battery optimisation — otherwise background tasks die silently.
        val ignoring = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (t: Throwable) {
            false
        }
        list.add(
            Row(
                "battery", "ব্যাটারি অপটিমাইজেশন ছাড়",
                "ব্যাকগ্রাউন্ড টাস্ক ও অটো-রিকানেক্ট বেঁচে থাকার জন্য",
                if (ignoring) Level.GRANTED else Level.NOT_GRANTED, Need.FEATURE,
                "সেটিংস → Apps → Jimi → Battery → Unrestricted", true
            )
        )

        // Exact alarms for timers/medication reminders.
        if (sdk >= 31) {
            val exact = try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.canScheduleExactAlarms()
            } catch (t: Throwable) {
                false
            }
            list.add(
                Row(
                    "alarm", "নির্দিষ্ট সময়ের অ্যালার্ম",
                    "টাইমার টাস্ক, ওষুধের রিমাইন্ডার ও শিডিউল",
                    if (exact) Level.GRANTED else Level.NOT_GRANTED, Need.FEATURE,
                    "সেটিংস → Apps → Jimi → Alarms & reminders → Allow", true
                )
            )
        }

        // Shizuku.
        val sh = ShizukuBridge.state(context)
        list.add(
            Row(
                "shizuku", "Shizuku (ADB-স্তরের কন্ট্রোল)",
                "রুট ছাড়াই pm/am/settings/input চালানো ও নিজেকে WRITE_SECURE_SETTINGS দেওয়া",
                when (sh) {
                    ShizukuBridge.State.READY_ADB, ShizukuBridge.State.READY_ROOT -> Level.GRANTED
                    ShizukuBridge.State.NO_PERMISSION -> Level.NOT_GRANTED
                    ShizukuBridge.State.NOT_RUNNING, ShizukuBridge.State.NOT_INSTALLED -> Level.NOT_GRANTED
                    ShizukuBridge.State.CONNECTION_LOST -> Level.CONNECTION_LOST
                    ShizukuBridge.State.UNSUPPORTED -> Level.RESTRICTED
                },
                Need.POWER,
                when (sh) {
                    ShizukuBridge.State.NOT_INSTALLED ->
                        "Shizuku ইনস্টল করো (Play Store) → খুলে Start চাপো (ওয়্যারলেস ডিবাগিং বা রুট) → Jimi-তে অনুমতি দাও"
                    ShizukuBridge.State.NOT_RUNNING ->
                        "Shizuku অ্যাপ খোলো → Start → ফিরে এসে Jimi-তে «আবার সংযুক্ত করো» চাপো"
                    ShizukuBridge.State.CONNECTION_LOST ->
                        "Shizuku আবার চালু করো; Jimi নিজেই binder ধরবে, না হলে «আবার সংযুক্ত করো» চাপো"
                    else -> "সেটিংস → রুট ও টার্মিনাল → Shizuku অনুমতি চাও"
                },
                true
            )
        )

        // Root.
        val root = RootBridge.available()
        list.add(
            Row(
                "root", "রুট অ্যাক্সেস",
                "সবচেয়ে গভীর কন্ট্রোল (রুট না থাকলেও Shizuku/অ্যাক্সেসিবিলিটি দিয়ে বেশিরভাগ কাজ হয়)",
                if (root) Level.GRANTED else Level.NOT_GRANTED, Need.POWER,
                "রুট করা ফোনে su অনুমতি দাও; Jimi রুট চায় না, পেলে ব্যবহার করে", false
            )
        )

        // WRITE_SECURE_SETTINGS — restricted for normal apps by design.
        val wss = ShizukuBridge.hasWriteSecureSettings(context)
        list.add(
            Row(
                "writesecure", "WRITE_SECURE_SETTINGS",
                "নিজের অ্যাক্সেসিবিলিটি চালু করা, ডিজিটাল অ্যাসিস্ট্যান্ট হওয়া, secure settings লেখা",
                when {
                    wss -> Level.GRANTED
                    root || sh == ShizukuBridge.State.READY_ROOT || sh == ShizukuBridge.State.READY_ADB ->
                        Level.NOT_GRANTED
                    else -> Level.RESTRICTED
                },
                Need.POWER,
                "রুট বা Shizuku সংযুক্ত থাকলে Jimi নিজেই grant করতে পারে — সেটিংসে «নিজে নেও» বাটন আছে", true
            )
        )

        // Default assistant.
        val assistant = try {
            val v = Settings.Secure.getString(context.contentResolver, "assistant") ?: ""
            v.contains("saathi")
        } catch (t: Throwable) {
            false
        }
        list.add(
            Row(
                "assistant", "ডিজিটাল অ্যাসিস্ট্যান্ট",
                "হোম বাটন চেপে ধরে Jimi ডাকা",
                if (assistant) Level.GRANTED else Level.NOT_GRANTED, Need.FEATURE,
                "সেটিংস → Apps → Default apps → Digital assistant → Jimi", true
            )
        )

        // Termux (optional but unlocks a real shell, python, ffmpeg).
        val term = TerminalTools(context).termuxInstalled()
        list.add(
            Row(
                "termux", "Termux",
                "আসল শেল, python/ffmpeg/git — কোড এজেন্ট ও ভিডিও কাজের জন্য",
                if (term) Level.GRANTED else Level.NOT_GRANTED, Need.OPTIONAL,
                "Termux ইনস্টল করো, তারপর সেটিংস → allow-external-apps=true (Jimi নিজেই দিতে পারে)", true
            )
        )

        // API key — not a system permission, but nothing AI works without it.
        val configured = try {
            SecurePrefs(context).isConfigured
        } catch (t: Throwable) {
            false
        }
        list.add(
            Row(
                "apikey", "Gemini API কী",
                "মডেল কল, ছবি/অডিও জেনারেশন, রিসার্চ",
                if (configured) Level.GRANTED else Level.REQUIRED, Need.CORE,
                "সেটিংস → API Key → কী বসাও → সেভ করো", true
            )
        )

        return list
    }

    // ----------------------------------------------------------------- report

    /** Full dashboard text — also what the model gets when it asks. */
    fun dashboard(context: Context): String {
        val rows = rows(context)
        return buildString {
            appendLine("পারমিশন ও ক্ষমতার ড্যাশবোর্ড")
            appendLine("কার্যকর কন্ট্রোল স্তর: ${effectiveLevel(context)}")
            appendLine("স্কোর: ${score(context)}% · আবশ্যক বাকি: ${rows.count { it.level == Level.REQUIRED }}টি")
            appendLine()
            Need.values().forEach { need ->
                val group = rows.filter { it.need == need }
                if (group.isEmpty()) return@forEach
                appendLine("— ${needName(need)} —")
                group.forEach { r ->
                    appendLine("• ${r.name}: ${levelName(r.level)}")
                    if (r.level != Level.GRANTED && r.level != Level.NOT_APPLICABLE) {
                        appendLine("   কেন দরকার: ${r.why}")
                        appendLine("   কীভাবে দেবে: ${r.steps}")
                    }
                }
            }
        }
    }

    /** Missing capabilities, ordered by how much they hurt. */
    fun missing(context: Context): List<Row> = rows(context)
        .filter { it.level == Level.REQUIRED || it.level == Level.NOT_GRANTED || it.level == Level.CONNECTION_LOST }
        .sortedBy {
            when (it.need) {
                Need.CORE -> 0
                Need.POWER -> 1
                Need.FEATURE -> 2
                Need.OPTIONAL -> 3
            }
        }

    /** Short spoken summary: what is blocking work right now. */
    fun headline(context: Context): String {
        val miss = missing(context)
        return when {
            miss.isEmpty() -> "সব অনুমতি ঠিক আছে — পূর্ণ ক্ষমতায় চলছি"
            else -> {
                val core = miss.filter { it.need == Need.CORE }
                if (core.isNotEmpty()) "আবশ্যিক অনুমতি বাকি: ${core.joinToString(", ") { it.name }} — এগুলো ছাড়া মূল কাজ চলবে না"
                else "${miss.size}টি অনুমতি বাকি: ${miss.take(4).joinToString(", ") { it.name }}"
            }
        }
    }

    fun score(context: Context): Int {
        val rows = rows(context).filter { it.level != Level.NOT_APPLICABLE && it.need != Need.OPTIONAL }
        if (rows.isEmpty()) return 0
        val granted = rows.count { it.level == Level.GRANTED }
        return (granted * 100) / rows.size
    }

    /** Which rung of the privilege ladder this phone is standing on. */
    fun effectiveLevel(context: Context): String {
        ShizukuBridge.attach(context)
        val root = RootBridge.available()
        val sh = ShizukuBridge.state(context)
        return when {
            root -> "রুট (সর্বোচ্চ) — সব সিস্টেম কমান্ড চালানো যাবে"
            sh == ShizukuBridge.State.READY_ROOT -> "Shizuku রুট-স্তর — প্রায় সব কন্ট্রোল"
            sh == ShizukuBridge.State.READY_ADB -> "Shizuku ADB-স্তর — pm/am/settings/input/dumpsys"
            ShizukuBridge.hasWriteSecureSettings(context) -> "WRITE_SECURE_SETTINGS — সেটিংস ও অ্যাক্সেসিবিলিটি কন্ট্রোল"
            JimiAccessibilityService.isEnabled() -> "অ্যাক্সেসিবিলিটি — স্ক্রিন পড়া ও UI অটোমেশন"
            else -> "সাধারণ অ্যাপ — শুধু অনুমতিপ্রাপ্ত কাজ"
        }
    }

    // -------------------------------------------------------------------- fix

    /**
     * Does whatever is possible for one capability: opens the exact screen, or
     * grants it directly when a privileged path exists. Returns honest Bangla.
     */
    fun fix(context: Context, id: String): String {
        ShizukuBridge.attach(context)
        return try {
            when (id) {
                "accessibility" -> {
                    val direct = ShizukuBridge.enableAccessibility(context)
                    if (direct.startsWith("অ্যাক্সেসিবিলিটি সার্ভিস চালু")) direct
                    else {
                        openSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        "সেটিংস খুলে দিয়েছি: Downloaded services → Jimi → চালু করো ($direct)"
                    }
                }
                "overlay" -> {
                    openSettings(
                        context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    "ওভারলে সেটিংস খুলে দিয়েছি — Jimi চালু করে দাও"
                }
                "battery" -> {
                    val ok = runCatching {
                        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(i)
                        true
                    }.getOrElse {
                        openSettings(context, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        false
                    }
                    if (ok) "অপটিমাইজেশন ছাড়ের ডায়ালগ খুলে দিয়েছি — Allow চাপো"
                    else "ব্যাটারি সেটিংস খুলে দিয়েছি — Jimi-কে Unrestricted করো"
                }
                "alarm" -> {
                    if (Build.VERSION.SDK_INT >= 31) {
                        openSettings(
                            context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}")
                        )
                    }
                    "অ্যালার্ম সেটিংস খুলে দিয়েছি — Allow চাপো"
                }
                "assistant" -> {
                    val direct = ShizukuBridge.setDefaultAssistant(context)
                    if (direct.contains("সেট হয়েছে")) direct
                    else {
                        openSettings(context, Settings.ACTION_VOICE_INPUT_SETTINGS)
                        "ডিজিটাল অ্যাসিস্ট্যান্ট সেটিংস খুলে দিয়েছি ($direct)"
                    }
                }
                "shizuku" -> ShizukuBridge.requestPermission(context).let { perm ->
                    if (perm.contains("চালু করতে হবে") || perm.contains("ইনস্টল")) ShizukuBridge.openShizuku(context)
                    else perm
                }
                "writesecure" -> ShizukuBridge.grantWriteSecureSettings(context)
                "termux" -> TerminalTools(context).enableTermuxExternalApps()
                "apikey" -> "সেটিংস স্ক্রিনে গিয়ে API Key বসাও — কী ছাড়া মডেল কল সম্ভব নয়"
                "root" -> "রুট অনুমতি Jimi নিজে নিতে পারে না — ফোন রুট করা থাকলে su ডায়ালগে Allow দিতে হবে"
                else -> {
                    val row = rows(context).firstOrNull { it.id == id }
                    if (row == null) "অজানা অনুমতি: $id"
                    else if (runtimePermissionId(id) != null) {
                        openAppDetails(context)
                        "অ্যাপের পারমিশন পেজ খুলে দিয়েছি — ${row.name}: ${row.steps}"
                    } else {
                        openAppDetails(context)
                        row.steps
                    }
                }
            }
        } catch (t: Throwable) {
            ErrorCenter.log("permission", "অনুমতি ঠিক করা যায়নি: $id", t.message)
            "ঠিক করা যায়নি (${t.message}) — হাতে দিতে হবে: ${rows(context).firstOrNull { it.id == id }?.steps}"
        }
    }

    private fun runtimePermissionId(id: String): String? = when (id) {
        "mic" -> android.Manifest.permission.RECORD_AUDIO
        "notifications" -> android.Manifest.permission.POST_NOTIFICATIONS
        "camera" -> android.Manifest.permission.CAMERA
        "call" -> android.Manifest.permission.CALL_PHONE
        "contacts" -> android.Manifest.permission.READ_CONTACTS
        "sms" -> android.Manifest.permission.SEND_SMS
        "calllog" -> android.Manifest.permission.READ_CALL_LOG
        "images" -> android.Manifest.permission.READ_MEDIA_IMAGES
        "videos" -> android.Manifest.permission.READ_MEDIA_VIDEO
        "storage" -> android.Manifest.permission.READ_EXTERNAL_STORAGE
        "activity" -> android.Manifest.permission.ACTIVITY_RECOGNITION
        else -> null
    }

    /** Runtime permissions the UI can ask for in one go. */
    fun requestableRuntime(context: Context): Array<String> =
        runtimeIds().filter { !has(context, it) }.toTypedArray()

    private fun runtimeIds(): List<String> {
        val ids = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_CALL_LOG
        )
        if (Build.VERSION.SDK_INT >= 33) {
            ids.add(android.Manifest.permission.POST_NOTIFICATIONS)
            ids.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            ids.add(android.Manifest.permission.READ_MEDIA_VIDEO)
        } else ids.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= 29) ids.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
        return ids
    }

    /** Step-by-step Bangla guide for one capability (spoken or shown). */
    fun guide(context: Context, id: String): String {
        val row = rows(context).firstOrNull { it.id == id }
            ?: return "এই নামে কোনো অনুমতি নেই: $id"
        return buildString {
            appendLine("${row.name} — ${levelName(row.level)}")
            appendLine("কেন দরকার: ${row.why}")
            appendLine("গুরুত্ব: ${needName(row.need)}")
            appendLine("ধাপ: ${row.steps}")
            if (!row.fixable) appendLine("নোট: এটা Jimi নিজে দিতে পারে না — তোমাকে হাতে দিতে হবে")
        }
    }

    /** Fixes everything that can be fixed without user interaction. */
    fun autoFix(context: Context): String {
        val done = mutableListOf<String>()
        val cannot = mutableListOf<String>()
        listOf("writesecure", "accessibility", "termux", "assistant").forEach { id ->
            val row = rows(context).firstOrNull { it.id == id } ?: return@forEach
            if (row.level == Level.GRANTED) return@forEach
            val r = fix(context, id)
            val now = rows(context).firstOrNull { it.id == id }?.level
            if (now == Level.GRANTED) done.add(row.name) else cannot.add("${row.name}: ${r.take(120)}")
        }
        return buildString {
            if (done.isNotEmpty()) appendLine("নিজে ঠিক করেছি: ${done.joinToString(", ")}")
            if (cannot.isNotEmpty()) {
                appendLine("নিজে পারিনি (তোমার হাতে দিতে হবে):")
                cannot.forEach { appendLine("• $it") }
            }
            if (done.isEmpty() && cannot.isEmpty()) append("সব ঠিক আছে — করার মতো কিছু ছিল না")
        }
    }

    // --------------------------------------------------------------- helpers

    private fun openSettings(context: Context, action: String, data: Uri? = null) {
        val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (data != null) i.data = data
        runCatching { context.startActivity(i) }
            .onFailure { runCatching { context.startActivity(openAppDetailsIntent(context)) } }
    }

    private fun openAppDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openAppDetails(context: Context) {
        runCatching { context.startActivity(openAppDetailsIntent(context)) }
    }
}
