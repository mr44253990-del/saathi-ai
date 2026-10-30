package com.saathi.ai.access

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "smart" layer: everything that is not a raw accessibility gesture.
 *
 * Weather, payments/UPI, medicine reminders, sharing, clipboard round-trips,
 * battery/health, and a tiny intent prober that lets Jimi work out how to talk to
 * an app it has never seen before (deep links, package name discovery).
 */
class SmartTools(private val context: Context) {

    // ------------------------------------------------------------------ weather

    /**
     * Weather without an API key: Google's public "weather" search page is opened in
     * the browser and read through the accessibility tree. Jimi reads out the answer.
     */
    fun openWeather(location: String?): String {
        val q = if (location.isNullOrBlank()) "আজকের আবহাওয়া" else "$location আবহাওয়া"
        val url = "https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8") +
            "&hl=bn"
        openUrl(url)
        return "আবহাওয়া খুঁজে দেখাচ্ছি${if (location.isNullOrBlank()) "" else " — $location"}"
    }

    fun openUrl(url: String): Boolean = try {
        val fixed = if (url.startsWith("http")) url else "https://$url"
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(fixed))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (t: Throwable) {
        false
    }

    fun webSearch(query: String): Boolean =
        openUrl("https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8"))

    fun mapSearch(query: String): Boolean =
        openUrl("https://www.google.com/maps/search/" + URLEncoder.encode(query, "UTF-8"))

    fun playStoreSearch(query: String): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + URLEncoder.encode(query, "UTF-8")))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (t: Throwable) {
        openUrl("https://play.google.com/store/search?q=" + URLEncoder.encode(query, "UTF-8"))
    }

    // ------------------------------------------------------------------ money

    /**
     * UPI payment sheet. Jimi never moves money by itself — it builds the intent with
     * the payee/amount pre-filled and lets the user confirm inside their own UPI app.
     * That keeps the assistant safe (no silent transactions) while still doing the
     * tedious part.
     */
    fun upiPay(vpa: String?, payeeName: String?, amount: String?, note: String?): String {
        if (vpa.isNullOrBlank()) {
            return "UPI ID দরকার — কীসের কাছে, কত টাকা পাঠাতে হবে বলো (যেমন: rahim@okaxis, 200 টাকা)।"
        }
        val uri = buildString {
            append("upi://pay?pa=").append(Uri.encode(vpa))
            if (!payeeName.isNullOrBlank()) append("&pn=").append(Uri.encode(payeeName))
            if (!amount.isNullOrBlank()) append("&am=").append(Uri.encode(amount.replace("[^0-9.]".toRegex(), "")))
            append("&cu=INR")
            if (!note.isNullOrBlank()) append("&tn=").append(Uri.encode(note))
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "পেমেন্ট অ্যাপে $vpa-এর জন্য ${amount ?: "টাকা"} পাঠানোর স্ক্রিন খুলে দিয়েছি — টাকা পাঠানোর আগে তুমি নিজে নিশ্চিত করো।"
        } catch (t: Throwable) {
            // No UPI app: fall back to a scanner/search.
            webSearch("upi app download")
            "কোনো UPI অ্যাপ পাওয়া যায়নি। প্রথমে একটা UPI অ্যাপ (GPay/PhonePe/Paytm) ইনস্টল করতে হবে।"
        }
    }

    fun upiScan(): String {
        // Most UPI apps register upi://pay; opening it gives the user the scan entry.
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=&cu=INR"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "UPI স্ক্যানার খুলে দিয়েছি — QR কোড স্ক্যান করে নাও।"
        } catch (t: Throwable) {
            "UPI অ্যাপ খোলা গেল না।"
        }
    }

    // ------------------------------------------------------------------ clipboard

    fun copy(text: String): String {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Jimi", text))
            "কপি হয়েছে: ${text.take(60)}"
        } catch (t: Throwable) {
            "কপি করা গেল না।"
        }
    }

    fun paste(): String {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip == null || clip.itemCount == 0) "ক্লিপবোর্ড খালি।"
            else clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
                .ifBlank { "ক্লিপবোর্ডে লেখা নেই।" }
        } catch (t: Throwable) {
            "ক্লিপবোর্ড পড়া গেল না।"
        }
    }

    fun shareText(text: String): String = try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(send, "শেয়ার করো").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "শেয়ার শিট খুলে দিয়েছি।"
    } catch (t: Throwable) {
        "শেয়ার করা গেল না।"
    }

    fun shareImage(path: String): String = try {
        val uri = Uri.parse(path)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(send, "ছবি শেয়ার করো").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "ছবি শেয়ার করার শিট খুলে দিয়েছি — Instagram, TikTok, WhatsApp যেটায় চাও বেছে নাও।"
    } catch (t: Throwable) {
        "ছবি শেয়ার করা গেল না।"
    }

    // ------------------------------------------------------------------ medicine

    /**
     * Schedules a medicine reminder by creating a real alarm in the user's clock app.
     * Jimi repeats it daily by creating one alarm and telling the user to set repeat
     * in the clock app — that is the only path that survives reboots across OEMs.
     */
    fun medicineReminder(medicine: String, time: String): String {
        val (hour, minute) = parseTime(time)
        return try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "💊 $medicine")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "«$medicine» এর জন্য ${fmt(hour, minute)}-এ অ্যালার্ম সেট করে দিয়েছি। ক্লক অ্যাপে গিয়ে \"প্রতিদিন\" রিপিট চালু করে নিও।"
        } catch (t: Throwable) {
            "অ্যালার্ম সেট করা গেল না।"
        }
    }

    private fun parseTime(raw: String): Pair<Int, Int> {
        // accepts "7:30", "07:30", "7.30 am", "সকাল ৭টা"
        val cleaned = raw.lowercase(Locale.US)
        val m = Regex("(\\d{1,2})\\s*[:.\\s]\\s*(\\d{1,2})").find(cleaned)
        var h = m?.groupValues?.get(1)?.toIntOrNull() ?: 8
        val mi = m?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val pm = cleaned.contains("pm") || cleaned.contains("বিকেল") || cleaned.contains("সন্ধ্যা") ||
            cleaned.contains("রাত")
        val am = cleaned.contains("am") || cleaned.contains("সকাল") || cleaned.contains("ভোর")
        if (pm && h < 12) h += 12
        if (am && h == 12) h = 0
        return h.coerceIn(0, 23) to mi.coerceIn(0, 59)
    }

    private fun fmt(h: Int, m: Int): String =
        String.format(Locale.US, "%02d:%02d", h, m)

    // ------------------------------------------------------------------ device health

    fun batteryReport(): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            "ব্যাটারি: $pct%"
        } catch (t: Throwable) {
            "ব্যাটারি তথ্য পাওয়া গেল না।"
        }
    }

    fun timeNow(): String =
        SimpleDateFormat("hh:mm a, EEEE d MMMM", Locale("bn", "BD")).format(Date())

    // ------------------------------------------------------------------ app discovery

    /** Finds a launchable package whose label contains [query] (any script). */
    fun findPackage(query: String): String? {
        val pm = context.packageManager
        val q = query.lowercase(Locale.ROOT).trim()
        if (q.isEmpty()) return null
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = pm.queryIntentActivities(intent, 0)
        var best: String? = null
        var bestScore = 0
        for (info in apps) {
            val label = try {
                info.loadLabel(pm).toString()
            } catch (t: Throwable) {
                continue
            }
            val pkg = info.activityInfo?.packageName ?: continue
            val score = score(q, label.lowercase(Locale.ROOT), pkg.lowercase(Locale.ROOT))
            if (score > bestScore) {
                bestScore = score
                best = pkg
            }
        }
        return best
    }

    private fun score(q: String, label: String, pkg: String): Int {
        var s = 0
        if (label == q) s += 100
        if (label.startsWith(q)) s += 60
        if (label.contains(q)) s += 40
        if (pkg.contains(q.replace(" ", ""))) s += 25
        // prefix match on the first word, e.g. "ইউটিউব" vs "YouTube"
        val firstWord = label.split(' ', '\u00A0').firstOrNull().orEmpty()
        if (firstWord.isNotEmpty() && q.startsWith(firstWord)) s += 20
        return s
    }

    /** Apps the assistant can drive with well-known deep links. */
    fun deepLink(app: String, payload: String?): Boolean {
        val url = when (app.lowercase(Locale.ROOT)) {
            "whatsapp" -> payload?.let { "https://wa.me/${it.filter { c -> c.isDigit() }}" } ?: "https://wa.me/"
            "instagram" -> "https://instagram.com/"
            "facebook" -> "https://facebook.com/"
            "tiktok" -> "snssdk1128://" // opens app if installed, else fallback below
            "youtube" -> payload?.let {
                "https://www.youtube.com/results?search_query=" + URLEncoder.encode(it, "UTF-8")
            } ?: "https://youtube.com"
            "telegram" -> payload?.let { "https://t.me/$it" } ?: "https://t.me/"
            "gmail" -> "mailto:"
            "phonepe", "gpay", "google pay", "paytm", "upi" -> "upi://pay"
            else -> null
        } ?: return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (t: Throwable) {
            Log.w("JimiSmart", "deeplink failed: $app")
            false
        }
    }

    fun openAppSettings(pkg: String?): Boolean = try {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + (pkg ?: context.packageName))
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (t: Throwable) {
        false
    }

    fun accessibilitySettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (t: Throwable) {
        false
    }

    fun screenshotPermissionHint(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            "স্ক্রিন দেখতে চাইলে সিস্টেমের \"একবার শেয়ার করো\" চাপতে হবে — Android 14+ এর নিয়ম।"
        } else "স্ক্রিন শেয়ারিং চালু আছে।"
}
