package com.saathi.ai.access

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Jimi's knowledge of the phone's apps.
 *
 * Two jobs:
 *  1. **Discovery** — turn whatever the user said ("টিকটক", "insta", "গ্যালারি")
 *     into a launchable package, using label + package + banner + a learned alias
 *     table, not just exact matches.
 *  2. **Learning** — after Jimi successfully drives an app, it stores what worked
 *     (which labels were tapped, in what order) so the second time is faster and
 *     more accurate. That is the "এটা কিসের জন্য, এটা কি লাগে" understanding: each
 *     app gets a purpose line and a playbook of known steps.
 */
class AppKnowledge(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val prefs = context.getSharedPreferences("jimi_apps", Context.MODE_PRIVATE)

    data class AppInfo(
        val pkg: String,
        val label: String,
        val purpose: String,
        val canLaunch: Boolean
    )

    // ---------------------------------------------------------------- catalogue

    /** Curated purpose lines for the apps people actually ask about. */
    private val purposes = mapOf(
        "com.google.android.youtube" to "ভিডিও দেখা ও সার্চ করা",
        "com.whatsapp" to "মেসেজ, ভয়েস কল, স্ট্যাটাস",
        "com.instagram.android" to "ছবি/রিলস পোস্ট করা, মেসেজ",
        "com.facebook.katana" to "পোস্ট, স্টোরি, মেসেঞ্জার",
        "com.facebook.orca" to "মেসেঞ্জার চ্যাট",
        "com.zhiliaoapp.musically" to "TikTok ভিডিও দেখা ও আপলোড",
        "com.ssandroid.ss" to "TikTok (অন্য ভ্যারিয়েন্ট)",
        "org.telegram.messenger" to "টেলিগ্রাম মেসেজ ও চ্যানেল",
        "com.google.android.gm" to "ইমেইল",
        "com.android.chrome" to "ওয়েব ব্রাউজিং",
        "com.google.android.apps.photos" to "গ্যালারি ও ছবি ব্যাকআপ",
        "com.android.gallery3d" to "গ্যালারি",
        "com.google.android.apps.maps" to "ম্যাপ ও নেভিগেশন",
        "com.google.android.dialer" to "ফোন কল",
        "com.google.android.apps.messaging" to "SMS",
        "com.android.camera" to "ক্যামেরা",
        "com.google.android.apps.youtube.music" to "গান শোনা",
        "com.spotify.music" to "গান শোনা",
        "com.google.android.apps.docs" to "ডকুমেন্ট",
        "com.google.android.keep" to "নোট",
        "com.google.android.calendar" to "ক্যালেন্ডার",
        "com.google.android.deskclock" to "অ্যালার্ম ও ঘড়ি",
        "com.google.android.apps.nbu.files" to "ফাইল ম্যানেজার",
        "com.android.settings" to "ফোন সেটিংস",
        "com.google.android.apps.tachyon" to "ভিডিও মিটিং",
        "com.microsoft.office.word" to "Word ডকুমেন্ট",
        "com.google.android.videos" to "ফিল্ম",
        "com.android.vending" to "Play Store — অ্যাপ ইনস্টল"
    )

    /** Common Bangla / shorthand names → search hints. */
    private val aliases = mapOf(
        "ইউটিউব" to "youtube", "ইউটিউবে" to "youtube", "youtube" to "youtube",
        "হোয়াটসঅ্যাপ" to "whatsapp", "হোয়াটসএ্যাপ" to "whatsapp", "واتس" to "whatsapp",
        "ফেসবুক" to "facebook", "ফেসবুকে" to "facebook", "fb" to "facebook",
        "ইনস্টাগ্রাম" to "instagram", "ইন্সটাগ্রাম" to "instagram", "insta" to "instagram",
        "টিকটক" to "tiktok", "টিক টক" to "tiktok", "tiktok" to "tiktok",
        "টেলিগ্রাম" to "telegram", "টেলিগ্রামে" to "telegram",
        "গ্যালারি" to "photos", "ছবি" to "photos", "ফটো" to "photos",
        "ক্যামেরা" to "camera", "ম্যাপ" to "maps", "গুগল ম্যাপ" to "maps",
        "সেটিংস" to "settings", "ফোন" to "dialer", "কল" to "dialer",
        "প্লে স্টোর" to "play store", "প্লেস্টোর" to "play store",
        "ইমেইল" to "gmail", "জিমেইল" to "gmail", "নোট" to "keep",
        "ফাইল" to "files", "ফাইল ম্যানেজার" to "files", "ঘড়ি" to "clock",
        "অ্যালার্ম" to "clock", "ব্রাউজার" to "chrome", "ক্রোম" to "chrome"
    )

    // ---------------------------------------------------------------- queries

    /** Every launchable app, once. Cached per process lifetime. */
    fun installedApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(intent, 0)
        val seen = mutableMapOf<String, AppInfo>()
        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (seen.containsKey(pkg)) continue
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            seen[pkg] = AppInfo(
                pkg = pkg,
                label = label,
                purpose = purposes[pkg] ?: learnPurpose(pkg) ?: "অ্যাপ",
                canLaunch = true
            )
        }
        return seen.values.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    /**
     * Finds the best package for a spoken name. Scoring prefers:
     * learned alias → exact label → prefix → contains → package fragment.
     */
    fun resolve(spoken: String): AppInfo? {
        val raw = spoken.lowercase(Locale.ROOT).trim()
        if (raw.isEmpty()) return null
        val aliasTarget = aliases.entries.firstOrNull { raw.contains(it.key) }?.value
        val needle = (aliasTarget ?: raw).replace(" ", "")

        val apps = installedApps()
        var best: AppInfo? = null
        var bestScore = 0

        for (app in apps) {
            val label = app.label.lowercase(Locale.ROOT)
            val pkg = app.pkg.lowercase(Locale.ROOT)
            var score = 0
            if (label == needle) score += 120
            if (label.startsWith(needle)) score += 70
            if (label.contains(needle)) score += 45
            if (pkg.contains(needle)) score += 30
            if (needle.length >= 3 && pkg.replace(".", "").contains(needle)) score += 12
            val learned = learnedAlias(app.pkg)
            if (learned != null && learned.contains(raw)) score += 90
            if (score > bestScore) {
                bestScore = score
                best = app
            }
        }
        return if (bestScore >= 30) best else null
    }

    fun launch(app: AppInfo): Boolean {
        val intent = pm.getLaunchIntentForPackage(app.pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** "এটা কিসের জন্য?" — a one-line purpose for a package. */
    fun purposeOf(pkg: String): String =
        purposes[pkg] ?: learnPurpose(pkg) ?: "Unknown app: $pkg"

    // ---------------------------------------------------------------- learning

    fun rememberPlaybook(pkg: String, goal: String, steps: List<String>) {
        val all = playbooks().toMutableMap()
        val list = all.getOrPut(pkg) { mutableListOf() }
        list.removeAll { it.goal.equals(goal, ignoreCase = true) }
        list.add(0, PlaybookEntry(goal, steps.take(12)))
        all[pkg] = list.take(8).toMutableList()
        persistPlaybooks(all)
    }

    fun playbookFor(pkg: String, goal: String): List<String> {
        val entry = playbooks()[pkg]?.firstOrNull {
            it.goal.equals(goal, ignoreCase = true)
        } ?: return emptyList()
        return entry.steps
    }

    private fun learnedAlias(pkg: String): String? = prefs.getString("alias_$pkg", null)

    private fun learnPurpose(pkg: String): String? = prefs.getString("purpose_$pkg", null)

    data class PlaybookEntry(val goal: String, val steps: List<String>)

    private fun playbooks(): Map<String, MutableList<PlaybookEntry>> {
        val raw = prefs.getString("playbooks", "{}") ?: "{}"
        val out = mutableMapOf<String, MutableList<PlaybookEntry>>()
        runCatching {
            val root = JSONObject(raw)
            root.keys().forEach { pkg ->
                val arr = root.optJSONArray(pkg) ?: JSONArray()
                val list = mutableListOf<PlaybookEntry>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val stepsArr = o.optJSONArray("steps") ?: JSONArray()
                    list.add(
                        PlaybookEntry(
                            o.optString("goal"),
                            (0 until stepsArr.length()).map { stepsArr.optString(it) }
                        )
                    )
                }
                out[pkg] = list
            }
        }
        return out
    }

    private fun persistPlaybooks(map: Map<String, List<PlaybookEntry>>) {
        val root = JSONObject()
        map.forEach { (pkg, list) ->
            val arr = JSONArray()
            list.forEach { entry ->
                arr.put(
                    JSONObject()
                        .put("goal", entry.goal)
                        .put("steps", JSONArray(entry.steps))
                )
            }
            root.put(pkg, arr)
        }
        prefs.edit().putString("playbooks", root.toString()).apply()
    }

    /** Human-readable dump for the Memory page. */
    fun summary(): String {
        val apps = installedApps()
        val withPurpose = apps.count { purposes.containsKey(it.pkg) }
        return apps.size.toString() + "টি অ্যাপ ইনস্টল করা আছে, " + withPurpose +
            "টির কাজ Jimi জানে"
    }
}
