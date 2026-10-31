package com.saathi.ai.access

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Knowing what is actually installed — including what Android hides.
 *
 * Three real problems this fixes:
 *  • **"Play Store খুঁজে পাচ্ছে না"** — the store's package is `com.android.vending`
 *    and its label changes per language/ROM ("Play Store", "প্লে স্টোর", "Google Play").
 *    A spoken-name lookup alone misses it, so every common app now has an explicit
 *    alias table checked *before* any label matching.
 *  • **Hidden / renamed / frozen apps** — [hidden] and [systemApps] include disabled
 *    and uninstalled-but-kept packages (`MATCH_DISABLED_COMPONENTS`), and with root
 *    also `pm list packages -d/-u`, which is the only way to see truly hidden ones.
 *  • **Widgets** — [widgets] lists every registered widget provider, so "কোন কোন
 *    উইজেট আছে" has a real answer.
 *
 * Launching has four fallbacks and never gives up on the first failure:
 * launch intent → root `monkey` → root `am start` with a resolved component →
 * store page. Each attempt is reported honestly.
 */
object AppDirectory {

    /** Spoken name (or its Bangla/short form) → package candidates, most likely first. */
    val aliases: Map<String, List<String>> = mapOf(
        "play store" to listOf("com.android.vending", "com.google.android.finsky"),
        "playstore" to listOf("com.android.vending"),
        "প্লে স্টোর" to listOf("com.android.vending"),
        "store" to listOf("com.android.vending"),
        "youtube" to listOf("com.google.android.youtube", "com.google.android.youtube.tv"),
        "ইউটিউব" to listOf("com.google.android.youtube"),
        "chrome" to listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev"),
        "browser" to listOf(
            "com.android.chrome", "com.google.android.apps.chrome", "org.mozilla.firefox",
            "com.sec.android.app.sbrowser", "com.mi.globalbrowser", "com.opera.browser",
            "com.brave.browser", "com.vivaldi.browser"
        ),
        "ব্রাউজার" to listOf("com.android.chrome", "org.mozilla.firefox"),
        "gallery" to listOf(
            "com.google.android.apps.photos", "com.android.gallery3d",
            "com.sec.android.gallery3d", "com.miui.gallery", "com.simplemobiletools.gallery.pro",
            "com.oneplus.gallery", "com.asus.gallery"
        ),
        "গ্যালারি" to listOf("com.google.android.apps.photos", "com.miui.gallery"),
        "photos" to listOf("com.google.android.apps.photos", "com.miui.gallery"),
        "ফটো" to listOf("com.google.android.apps.photos"),
        "camera" to listOf(
            "com.android.camera", "com.google.android.GoogleCamera", "com.sec.android.app.camera",
            "com.miui.camera", "com.oneplus.camera", "org.codeaurora.snapcam"
        ),
        "ক্যামেরা" to listOf("com.google.android.GoogleCamera", "com.miui.camera"),
        "files" to listOf(
            "com.google.android.documentsui", "com.android.files", "com.miui.filemanager",
            "com.sec.android.app.myfiles", "com.coloros.filemanager", "com.oneplus.filemanager",
            "com.simplemobiletools.filemanager.pro"
        ),
        "ফাইল" to listOf("com.google.android.documentsui", "com.miui.filemanager"),
        "file manager" to listOf("com.google.android.documentsui", "com.miui.filemanager"),
        "settings" to listOf("com.android.settings", "com.miui.securitycenter"),
        "সেটিংস" to listOf("com.android.settings"),
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "হোয়াটসঅ্যাপ" to listOf("com.whatsapp"),
        "facebook" to listOf("com.facebook.katana", "com.facebook.lite", "com.facebook.mlite"),
        "ফেসবুক" to listOf("com.facebook.katana", "com.facebook.lite"),
        "messenger" to listOf("com.facebook.orca", "com.facebook.mlite"),
        "instagram" to listOf("com.instagram.android", "com.instagram.lite"),
        "telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"),
        "টেলিগ্রাম" to listOf("org.telegram.messenger"),
        "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        "টিকটক" to listOf("com.zhiliaoapp.musically"),
        "gmail" to listOf("com.google.android.gm", "com.google.android.gm.lite"),
        "maps" to listOf("com.google.android.apps.maps", "com.google.android.apps.mapslite"),
        "ম্যাপ" to listOf("com.google.android.apps.maps"),
        "clock" to listOf("com.google.android.deskclock", "com.android.deskclock", "com.miui.weather2"),
        "ঘড়ি" to listOf("com.google.android.deskclock", "com.android.deskclock"),
        "calculator" to listOf("com.google.android.calculator", "com.android.calculator2", "com.miui.calculator"),
        "ক্যালকুলেটর" to listOf("com.android.calculator2", "com.miui.calculator"),
        "calendar" to listOf("com.google.android.calendar", "com.android.calendar"),
        "ক্যালেন্ডার" to listOf("com.google.android.calendar"),
        "contacts" to listOf("com.google.android.contacts", "com.android.contacts"),
        "কন্টাক্ট" to listOf("com.google.android.contacts", "com.android.contacts"),
        "phone" to listOf("com.google.android.dialer", "com.android.dialer", "com.android.phone"),
        "ফোন" to listOf("com.google.android.dialer", "com.android.dialer"),
        "messages" to listOf("com.google.android.apps.messaging", "com.android.mms"),
        "মেসেজ" to listOf("com.google.android.apps.messaging", "com.android.mms"),
        "termux" to listOf("com.termux"),
        "টার্মিনাল" to listOf("com.termux"),
        "terminal" to listOf("com.termux"),
        "magisk" to listOf("com.topjohnwu.magisk"),
        "root" to listOf("com.topjohnwu.magisk", "com.koushikdutta.superuser", "eu.chainfire.supersu"),
        "carrom" to listOf(
            "com.miniclip.carrom", "com.carrom.pool.disc.pool", "com.carrom.board.game",
            "com.eivaagames.CarromDiscPool", "com.carrom.clash", "com.gameon.carrom"
        ),
        "ক্যারম" to listOf("com.miniclip.carrom", "com.carrom.pool.disc.pool"),
        "ludo" to listOf(
            "com.ludo.king", "com.miraclegames.ludo", "com.blackbean.ludo", "com.ludo.master",
            "com.games24x7.ludo", "com.ludo.kingpro"
        ),
        "লুডু" to listOf("com.ludo.king", "com.miraclegames.ludo"),
        "chess" to listOf(
            "com.chess", "com.leagem.chesslive", "com.jetstartgames.chess", "com.chessgenius.android.chessfree"
        ),
        "দাবা" to listOf("com.chess", "com.leagem.chesslive"),
        "bkash" to listOf("com.bKash.customerapp"),
        "বিকাশ" to listOf("com.bKash.customerapp"),
        "nagad" to listOf("com.konasl.nagad"),
        "নগদ" to listOf("com.konasl.nagad"),
        "rocket" to listOf("com.dbbl.mbs.apps.main"),
        "upay" to listOf("com.upay.app"),
        "daraz" to listOf("com.daraz.android"),
        "foodpanda" to listOf("com.global.foodpanda.android"),
        "pathao" to listOf("com.pathao.user"),
        "uber" to listOf("com.ubercab"),
        "drive" to listOf("com.google.android.apps.docs"),
        "sheets" to listOf("com.google.android.apps.docs.editors.sheets"),
        "docs" to listOf("com.google.android.apps.docs.editors.docs"),
        "netflix" to listOf("com.netflix.mediaclient"),
        "spotify" to listOf("com.spotify.music"),
        "vlc" to listOf("org.videolan.vlc"),
        "mx player" to listOf("com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro"),
        "recorder" to listOf("com.google.android.apps.recorder", "com.android.soundrecorder"),
        "রেকর্ডার" to listOf("com.google.android.apps.recorder", "com.android.soundrecorder")
    )

    // ---------------------------------------------------------------- resolve

    private fun normalize(spoken: String): String = spoken.trim().lowercase()
        .removeSuffix(" অ্যাপ").removeSuffix(" app").removeSuffix(" টা").removeSuffix(" টি")
        .replace("ওপেন", "").replace("খোলো", "").replace("open", "").trim()

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (t: Throwable) {
        false
    }

    /** Best package name for a spoken app name, or null when nothing matches. */
    fun resolve(context: Context, spoken: String): String? {
        val key = normalize(spoken)
        if (key.isBlank()) return null

        // 1) explicit alias table (fixes Play Store & friends)
        for ((alias, pkgs) in aliases) {
            if (key == alias || key.contains(alias) || alias.contains(key)) {
                val hit = pkgs.firstOrNull { isInstalled(context, it) }
                if (hit != null) return hit
            }
        }
        // 2) exact package name given verbatim
        if (key.contains('.') && isInstalled(context, key)) return key

        // 3) label match over everything installed, including disabled ones
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        val installed = runCatching { context.packageManager.getInstalledApplications(flags) }
            .getOrDefault(emptyList())
        val byLabel = installed.firstOrNull { info ->
            val label = runCatching {
                context.packageManager.getApplicationLabel(info).toString().lowercase()
            }.getOrDefault("")
            label.isNotBlank() && (label == key || label.contains(key) || key.contains(label))
        }
        if (byLabel != null) return byLabel.packageName

        // 4) root can see what the PackageManager hides
        if (RootBridge.available()) {
            val guess = key.replace(" ", ".")
            val out = RootBridge.exec(
                "pm list packages -f 2>/dev/null | grep -i -E '${key.replace(" ", "|")}|$guess' | head -5",
                12_000
            ).out
            val pkg = Regex("package:.*?=([\\w.]+)").find(out)?.groupValues?.get(1)
            if (pkg != null) return pkg
        }
        return null
    }

    // ----------------------------------------------------------------- launch

    /**
     * Launches an app with four fallbacks. Returns a report that says exactly which
     * path worked — or that the app is not installed and the store page is open.
     */
    fun launch(context: Context, spoken: String): String {
        val pkg = resolve(context, spoken)
        if (pkg == null) {
            val openedStore = openStoreSearch(context, spoken)
            return "«$spoken» নামে কোনো অ্যাপ ফোনে পাওয়া যায়নি " +
                "(লুকানো/ডিসেবল থাকলে রুট দরকার)।" +
                if (openedStore) " Play Store-এ সার্চ দেখাচ্ছি।" else ""
        }
        return launchPackage(context, pkg, spoken)
    }

    fun launchPackage(context: Context, pkg: String, spoken: String = pkg): String {
        // 1) normal launch intent
        val intent = runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
        if (intent != null) {
            val ok = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (ok) return "«$spoken» খুলেছি ($pkg)"
        }
        // 2) root monkey (works for frozen/background-restricted apps)
        if (RootBridge.available()) {
            val r1 = RootBridge.exec("monkey -p $pkg -c android.intent.category.LAUNCHER 1", 12_000)
            if (r1.ok) return "«$spoken» খুলেছি (রুট দিয়ে, $pkg)"
            // 3) resolve the real launcher component and start it
            val comp = RootBridge.exec(
                "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $pkg 2>/dev/null | tail -1",
                12_000
            ).out.trim()
            if (comp.contains('/')) {
                val r2 = RootBridge.exec("am start -n $comp", 12_000)
                if (r2.ok) return "«$spoken» খুলেছি (কম্পোনেন্ট $comp)"
            }
            // unfreeze then retry, because "not found" usually means disabled
            RootBridge.exec("pm enable $pkg; pm unhide $pkg 2>/dev/null", 10_000)
            val retry = runCatching {
                context.packageManager.getLaunchIntentForPackage(pkg)
            }.getOrNull()
            if (retry != null && runCatching {
                    context.startActivity(retry.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
            ) return "«$spoken» ডিসেবল ছিল — চালু করে খুলেছি"
        }
        return "«$spoken» ($pkg) খোলা গেল না — অ্যাপটা ডিসেবল/লুকানো হতে পারে"
    }

    fun openStoreSearch(context: Context, query: String): Boolean {
        val q = Uri.encode(query)
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q"))
                .setPackage("com.android.vending"),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q")),
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=$q")
            ).setPackage("com.android.chrome"),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q"))
        )
        for (i in intents) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(i) }.isSuccess) return true
        }
        return false
    }

    /** Installs from the store page (or an APK path with root). */
    fun install(context: Context, spokenOrPath: String): String {
        if (spokenOrPath.endsWith(".apk") && RootBridge.available()) {
            val r = RootBridge.installApk(spokenOrPath)
            return if (r.ok) "APK ইনস্টল করেছি: $spokenOrPath"
            else "ইনস্টল ব্যর্থ: ${r.err.ifBlank { r.out }}"
        }
        return if (openStoreSearch(context, spokenOrPath))
            "Play Store-এ «$spokenOrPath» সার্চ খুলেছি — Install বাটনে চাপ দিতে বললে আমিই দেব।"
        else "Play Store খোলা যায়নি"
    }

    // ------------------------------------------------------------------ lists

    data class AppItem(val label: String, val pkg: String, val system: Boolean, val enabled: Boolean)

    private var cache: List<AppItem>? = null
    private var cachedAt = 0L

    fun all(context: Context, refresh: Boolean = false): List<AppItem> {
        val now = System.currentTimeMillis()
        if (!refresh && cache != null && now - cachedAt < 60_000) return cache!!
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        val infos = runCatching { context.packageManager.getInstalledApplications(flags) }
            .getOrDefault(emptyList())
        val list = infos.map { info ->
            val label = runCatching {
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(info.packageName)
            AppItem(
                label = label,
                pkg = info.packageName,
                system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                enabled = info.enabled
            )
        }.sortedBy { it.label.lowercase() }
        cache = list
        cachedAt = now
        return list
    }

    fun summary(context: Context): String {
        val list = all(context)
        val user = list.count { !it.system }
        val disabled = list.count { !it.enabled }
        return "মোট ${list.size}টি অ্যাপ · ব্যবহারকারীর $user · সিস্টেম ${list.size - user} · " +
            "ডিসেবল/লুকানো $disabled · Android ${Build.VERSION.RELEASE}"
    }

    /** The "hidden apps" answer: disabled, frozen, and uninstalled-but-kept. */
    fun hidden(context: Context): String {
        val list = all(context, refresh = true)
        val disabled = list.filter { !it.enabled }
        val out = StringBuilder()
        out.appendLine("ডিসেবল/ফ্রিজ করা অ্যাপ: ${disabled.size}টি")
        disabled.take(25).forEach { out.appendLine("• ${it.label} — ${it.pkg}") }
        if (RootBridge.available()) {
            val d = RootBridge.listPackages("-d").lines().filter { it.isNotBlank() }
            val u = RootBridge.listPackages("-u").lines().filter { it.isNotBlank() }
            out.appendLine("রুট দিয়ে দেখা: ডিসেবল ${d.size}, আনইনস্টল-কিন্তু-রেখে-দেওয়া ${u.size}")
            val extra = u.filterNot { p -> list.any { "package:" + it.pkg == p } }
            extra.take(15).forEach { out.appendLine("  গোপন: $it") }
        } else {
            out.appendLine("(রুট থাকলে আনইনস্টল করা কিন্তু ডেটা রেখে দেওয়া অ্যাপগুলোও দেখা যেত)")
        }
        return out.toString().take(2600)
    }

    fun systemApps(context: Context): String {
        val sys = all(context).filter { it.system }
        return buildString {
            appendLine("সিস্টেম অ্যাপ ${sys.size}টি:")
            sys.take(40).forEach { appendLine("• ${it.label} — ${it.pkg}") }
        }.take(2600)
    }

    /** Apps whose label looks renamed or blank — the "renamed app" hunt. */
    fun suspiciousLabels(context: Context): String {
        val list = all(context)
        val odd = list.filter {
            val l = it.label.trim()
            l.isBlank() || l == it.pkg || !l.any { ch -> ch.isLetterOrDigit() } ||
                l.length <= 1 || Regex("^[\\w.]+$").matches(l) && l.contains('.')
        }
        return if (odd.isEmpty()) "সব অ্যাপের নাম স্বাভাবিক দেখাচ্ছে"
        else buildString {
            appendLine("নাম বদলানো/অদ্ভুত অ্যাপ ${odd.size}টি:")
            odd.take(30).forEach { appendLine("• «${it.label}» — ${it.pkg}") }
        }.take(2400)
    }

    /** Every registered home-screen widget provider. */
    fun widgets(context: Context): String {
        return try {
            val manager = AppWidgetManager.getInstance(context)
            val providers: List<android.appwidget.AppWidgetProviderInfo> =
                try { manager.installedProviders.toList() } catch (t: Throwable) { emptyList() }
            if (providers.isEmpty()) "কোনো উইজেট প্রোভাইডার পাওয়া যায়নি"
            else buildString {
                appendLine("উইজেট প্রোভাইডার ${providers.size}টি:")
                providers.take(40).forEach { info ->
                    val label = runCatching { info.loadLabel(context.packageManager) }
                        .getOrDefault(info.provider.className.substringAfterLast('.'))
                    appendLine("• $label — ${info.provider.packageName}")
                }
            }.take(2600)
        } catch (t: Throwable) {
            "উইজেট তালিকা পড়া যায়নি (${t.message})"
        }
    }

    /** Bound widget instances currently on the home screen (needs no permission). */
    fun widgetInstances(context: Context): String {
        return try {
            val manager = AppWidgetManager.getInstance(context)
            val providers: List<android.appwidget.AppWidgetProviderInfo> =
                try { manager.installedProviders.toList() } catch (t: Throwable) { emptyList() }
            val bound = mutableListOf<String>()
            providers.forEach { info ->
                // Only the launcher (or a privileged app) can see ids bound to *other*
                // providers, so an empty result is normal and must not be reported as
                // "no widgets". The message says exactly that instead of guessing.
                val ids: IntArray =
                    try { manager.getAppWidgetIds(info.provider) } catch (t: Throwable) { intArrayOf() }
                if (ids.isNotEmpty()) {
                    val label = runCatching { info.loadLabel(context.packageManager) }
                        .getOrDefault(info.provider.className.substringAfterLast('.'))
                    bound.add("$label ×${ids.size} — ${info.provider.packageName}")
                }
            }
            when {
                bound.isNotEmpty() -> "বাঁধা উইজেট ${bound.size} রকম:\n" +
                    bound.take(30).joinToString("\n") { "• $it" }
                providers.isEmpty() -> "উইজেট প্রোভাইডারই পাওয়া যায়নি"
                else -> "উইজেট প্রোভাইডার ${providers.size}টি আছে, কিন্তু কোনগুলো হোম স্ক্রিনে বাঁধা " +
                    "সেটা Android শুধু লঞ্চারকে বলে — Jimi সাধারণ অ্যাপ হিসেবে সেটা দেখতে পায় না। " +
                    "রুট থাকলে root_exec দিয়ে dumpsys appwidget পড়ে বের করতে পারি।"
            }
        } catch (t: Throwable) {
            "উইজেট ইনস্ট্যান্স পড়া যায়নি"
        }
    }

    // ------------------------------------------------------------- deep links

    private val deepLinks: Map<String, (String) -> String> = mapOf(
        "whatsapp" to { p -> "https://wa.me/${p.filter { it.isDigit() }}" },
        "telegram" to { p -> "https://t.me/$p" },
        "youtube" to { q -> "https://www.youtube.com/results?search_query=" + Uri.encode(q) },
        "play" to { q -> "https://play.google.com/store/search?q=" + Uri.encode(q) },
        "maps" to { q -> "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q) },
        "search" to { q -> "https://www.google.com/search?q=" + Uri.encode(q) },
        "mail" to { a -> "mailto:$a" },
        "call" to { n -> "tel:$n" },
        "sms" to { n -> "sms:$n" },
        "upi" to { v -> "upi://pay?pa=$v&cu=BDT" }
    )

    fun openDeepLink(context: Context, kind: String, payload: String): String {
        val builder = deepLinks[kind.lowercase()]
            ?: return "চেনা লিংক নয়। আছে: ${deepLinks.keys.joinToString(", ")}"
        val url = builder(payload)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (runCatching { context.startActivity(intent) }.isSuccess) "লিংক খুলেছি: $url"
        else {
            val browser = resolve(context, "chrome") ?: resolve(context, "browser")
            if (browser != null) {
                intent.setPackage(browser)
                if (runCatching { context.startActivity(intent) }.isSuccess) return "ব্রাউজারে খুলেছি: $url"
            }
            "লিংক খোলা যায়নি: $url"
        }
    }
}
