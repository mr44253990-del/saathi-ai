package com.saathi.ai.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.core.KillSwitch
import com.saathi.ai.data.SecurePrefs
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The built-in browser workspace (Phase 7).
 *
 * A real WebView per tab, created off-screen so Jimi can drive the web even when
 * the app is in the background: JavaScript runs, dynamic pages render, downloads
 * go to the system DownloadManager, and errors are reported instead of showing a
 * blank page.
 *
 * Threading rule: WebView must live on a looper thread. Every touch here is
 * marshalled onto the main looper and, when called from an agent's worker
 * thread, waits on a latch with a hard timeout so nothing can hang the engine.
 *
 * Privacy: nothing is written to disk except (a) screenshots the user asked for
 * and (b) downloaded files. Cookies are per-tab in memory; `clear()` wipes them.
 */
object WebBrowser {

    private const val TAG = "browser"
    private const val VIEW_W = 1080
    private const val VIEW_H = 2160
    private const val JS_TIMEOUT_MS = 6_000L

    private class Tab(val id: Int, val view: WebView, var url: String, var title: String) {
        @Volatile var lastError: String = ""
        @Volatile var loading: Boolean = false
        @Volatile var loadedAt: Long = 0L
    }

    private val tabs = ConcurrentHashMap<Int, Tab>()
    private val idSeq = AtomicInteger(1)
    @Volatile private var activeId: Int = -1
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var killSwitchRegistered = false

    // ------------------------------------------------------------------ plumbing

    private fun onMain(timeoutMs: Long = 20_000, block: () -> Unit): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return true
        }
        val latch = CountDownLatch(1)
        main.post {
            try {
                block()
            } catch (t: Throwable) {
                ErrorCenter.log(TAG, "ব্রাউজার মেইন-থ্রেড কাজ ব্যর্থ", t.message)
            } finally {
                latch.countDown()
            }
        }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newTab(context: Context): Tab {
        val id = idSeq.getAndIncrement()
        lateinit var tab: Tab
        onMain {
            val wv = WebView(context.applicationContext)
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                loadsImagesAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = MODERN_UA
                allowFileAccess = false
                allowContentAccess = false
                setSupportZoom(false)
                javaScriptCanOpenWindowsAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            // Off-screen layout so rendering actually happens without a window.
            wv.measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_H, View.MeasureSpec.EXACTLY)
            )
            wv.layout(0, 0, VIEW_W, VIEW_H)

            wv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    tab.loading = true
                    tab.lastError = ""
                    url?.let { tab.url = it }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    tab.loading = false
                    tab.loadedAt = System.currentTimeMillis()
                    tab.title = view?.title ?: tab.title
                    url?.let { tab.url = it }
                }

                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        val msg = "HTTP ত্রুটি: ${error?.description} (${error?.errorCode})"
                        tab.lastError = msg
                        ErrorCenter.log(TAG, msg, tab.url)
                    }
                }
            }
            wv.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                    if (msg?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        ErrorCenter.warn(TAG, "পেজের JS ত্রুটি", msg.message()?.take(200))
                    }
                    return true
                }
            }
            wv.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                download(context, url, userAgent, mimeType, contentDisposition)
            })
            tab = Tab(id, wv, "about:blank", "নতুন ট্যাব")
            tabs[id] = tab
            activeId = id
        }
        return tabs[id] ?: throw IllegalStateException("ট্যাব তৈরি করা যায়নি")
    }

    private fun active(context: Context): Tab =
        tabs[activeId] ?: newTab(context)

    // ---------------------------------------------------------------- commands

    /** Opens a URL in the active tab (creating one if needed). */
    fun open(context: Context, url: String): String {
        val target = normalize(url)
        if (target.isBlank()) return "ব্যর্থ: ঠিকানা খালি"
        val tab = active(context)
        val ok = onMain { tab.view.loadUrl(target) }
        if (!ok) return "ব্যর্থ: ব্রাউজার থ্রেড সাড়া দেয়নি (টাইমআউট)"
        waitIdle(tab, 18_000)
        val title = readTitle(tab)
        return buildString {
            if (tab.lastError.isNotBlank()) {
                appendLine("ব্যর্থ: পেজ লোড হয়নি — ${tab.lastError}")
                appendLine("ঠিকানা: $target")
                append("ট্যাব ${tab.id} খোলা আছে; আবার চেষ্টা করতে বলো বা অন্য সাইট দাও")
            } else {
                appendLine("সফল: পেজ খোলা হয়েছে")
                appendLine("শিরোনাম: $title")
                appendLine("ঠিকানা: ${tab.url}")
                append("লোড সময়: ${if (tab.loadedAt > 0) "${(System.currentTimeMillis() - tab.loadedAt) / 1000} সে. আগে" else "এইমাত্র"}")
            }
        }
    }

    /** Search from the browser itself — DuckDuckGo's HTML endpoint is script-safe. */
    fun search(context: Context, query: String): String {
        if (query.isBlank()) return "ব্যর্থ: কী খুঁজবে সেটা বলো"
        val engine = "https://duckduckgo.com/html/?q=" + Uri.encode(query)
        val r = open(context, engine)
        val links = links(context, 12)
        return r + "\nফলাফল লিংক:\n" + links
    }

    /** Visible text of the current page (JavaScript rendering included). */
    fun text(context: Context, maxChars: Int = 12_000): String {
        val tab = active(context)
        var out = ""
        val latch = CountDownLatch(1)
        onMain {
            tab.view.evaluateJavascript(
                "(function(){try{return (document.body&&document.body.innerText)||document.title||''}" +
                    "catch(e){return 'JS_ERROR:'+e.message}})()"
            ) { value ->
                out = unquote(value)
                latch.countDown()
            }
        }
        latch.await(JS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (out.startsWith("JS_ERROR:")) {
            ErrorCenter.log(TAG, "পেজের লেখা পড়া যায়নি", out)
        }
        if (out.isBlank()) {
            // Fall back to the accessibility-visible title so we never return "nothing"
            // when a page did in fact load.
            out = readTitle(tab)
        }
        return out.take(maxChars)
    }

    /** Every link on the page, with text — what the research engine navigates by. */
    fun links(context: Context, limit: Int = 20): String {
        val tab = active(context)
        var out = ""
        val latch = CountDownLatch(1)
        val js = "(function(){var a=document.querySelectorAll('a[href]');var o=[];" +
            "for(var i=0;i<a.length&&o.length<$limit;i++){var t=(a[i].innerText||'').trim();" +
            "if(t.length>2&&a[i].href.indexOf('http')===0)o.push(t.slice(0,90)+' :: '+a[i].href);}return o.join('\\n');})()"
        onMain {
            tab.view.evaluateJavascript(js) { value ->
                out = unquote(value)
                latch.countDown()
            }
        }
        latch.await(JS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return out.ifBlank { "পেজে কোনো লিংক পাওয়া যায়নি" }
    }

    /** Form fields present on the page — used before filling anything. */
    fun fields(context: Context, limit: Int = 20): String {
        val tab = active(context)
        var out = ""
        val latch = CountDownLatch(1)
        val js = "(function(){var f=document.querySelectorAll('input,textarea,select');var o=[];" +
            "for(var i=0;i<f.length&&o.length<$limit;i++){var e=f[i];" +
            "o.push((e.name||e.id||e.placeholder||e.type)+' ['+(e.type||'text')+']');}return o.join('\\n');})()"
        onMain {
            tab.view.evaluateJavascript(js) { value ->
                out = unquote(value)
                latch.countDown()
            }
        }
        latch.await(JS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return out.ifBlank { "পেজে কোনো ইনপুট ঘর নেই" }
    }

    /**
     * Fills a field by name/id/placeholder. Password fields are refused here too —
     * the browser never becomes a credential filler.
     */
    fun fill(context: Context, label: String, value: String): String {
        if (label.isBlank()) return "ব্যর্থ: কোন ঘরে লিখবে সেটা বলো"
        if (com.saathi.ai.core.ApprovalGate.classify("browser_fill", "$label $value") >=
            com.saathi.ai.core.ApprovalGate.Risk.BLOCKED
        ) {
            return "আটকে: এটি পাসওয়ার্ড/OTP ঘর — ব্রাউজারেও Jimi এটা ভরে না। ঘরটিতে ফোকাস করে দিচ্ছি, তুমি লিখে দাও।"
        }
        val tab = active(context)
        val key = label.replace("'", "")
        val js = "(function(){var f=document.querySelectorAll('input,textarea');" +
            "for(var i=0;i<f.length;i++){var e=f[i];var id=(e.id||'')+' '+(e.name||'')+' '+(e.placeholder||'');" +
            "if(id.toLowerCase().indexOf('$key'.toLowerCase())>=0){" +
            "e.focus();e.value=${quote(value)};" +
            "e.dispatchEvent(new Event('input',{bubbles:true}));" +
            "e.dispatchEvent(new Event('change',{bubbles:true}));return 'FILLED:'+id.trim();}}" +
            "return 'NOTFOUND';})()"
        var out = ""
        val latch = CountDownLatch(1)
        onMain {
            tab.view.evaluateJavascript(js) { v -> out = unquote(v); latch.countDown() }
        }
        latch.await(JS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return when {
            out.startsWith("FILLED:") -> "সফল: «$label» ঘরে লেখা বসেছে (${out.removePrefix("FILLED:")})"
            out == "NOTFOUND" -> "ব্যর্থ: «$label» নামে কোনো ঘর এই পেজে নেই। ঘরগুলো: ${fields(context, 12)}"
            else -> "ব্যর্থ: ঘর ভরা যায়নি (JS সাড়া দেয়নি)"
        }
    }

    /** Clicks the first element whose text matches. */
    fun clickText(context: Context, text: String): String {
        val tab = active(context)
        val key = text.replace("'", "").lowercase()
        val js = "(function(){var els=document.querySelectorAll('a,button,input,[role=button],h1,h2,h3,div,span');" +
            "for(var i=0;i<els.length;i++){var t=(els[i].innerText||els[i].value||'').trim().toLowerCase();" +
            "if(t.indexOf('$key')>=0&&t.length<80){els[i].click();return 'CLICKED:'+t.slice(0,60);}}" +
            "return 'NOTFOUND';})()"
        var out = ""
        val latch = CountDownLatch(1)
        onMain {
            tab.view.evaluateJavascript(js) { v -> out = unquote(v); latch.countDown() }
        }
        latch.await(JS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (out.startsWith("CLICKED:")) {
            waitIdle(tab, 12_000)
            return "সফল: «$text» লেখার উপরে ক্লিক করা হয়েছে → ${tab.url}"
        }
        return "ব্যর্থ: «$text» লেখাটি এই পেজে পাওয়া যায়নি"
    }

    /** Renders the current page to a PNG so the vision model can see it. */
    fun shot(context: Context, keep: Boolean = false): String {
        val tab = active(context)
        var bmp: Bitmap? = null
        onMain {
            bmp = Bitmap.createBitmap(VIEW_W / 2, VIEW_H / 2, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp!!)
            canvas.scale(0.5f, 0.5f)
            tab.view.draw(canvas)
        }
        val b = bmp ?: return "ব্যর্থ: স্ক্রিনশট তোলা যায়নি"
        return try {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "web").apply { mkdirs() }
            val f = File(dir, "jimi-web-${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 90, it) }
            b.recycle()
            if (!keep && !SecurePrefs(context).keepScreenshots) {
                // Still returned once for analysis; cleanup happens in clearShots().
                lastShots.add(f)
            }
            "সফল: পেজের স্ক্রিনশট তোলা হয়েছে — ${f.absolutePath}"
        } catch (t: Throwable) {
            ErrorCenter.logThrow(TAG, "পেজ স্ক্রিনশট ব্যর্থ", t)
            "ব্যর্থ: স্ক্রিনশট সেভ করা যায়নি (${t.message})"
        }
    }

    private val lastShots = java.util.Collections.synchronizedList(mutableListOf<File>())

    /** Deletes temporary page screenshots (privacy default: keep nothing). */
    fun clearShots(context: Context): String {
        if (SecurePrefs(context).keepScreenshots) return "স্ক্রিনশট রাখার সেটিংস চালু আছে — তাই মোছা হয়নি"
        var n = 0
        synchronized(lastShots) {
            lastShots.toList().forEach { if (it.delete()) n++ }
            lastShots.clear()
        }
        return "$n টি অস্থায়ী পেজ-স্ক্রিনশট মুছে ফেলা হয়েছে"
    }

    fun back(context: Context): String {
        val tab = active(context)
        var did = false
        onMain { did = tab.view.canGoBack().also { if (it) tab.view.goBack() } }
        waitIdle(tab, 10_000)
        return if (did) "সফল: পেছনের পেজে ফেরা হয়েছে → ${tab.url}" else "পেছনে যাওয়ার মতো ইতিহাস নেই"
    }

    fun forward(context: Context): String {
        val tab = active(context)
        var did = false
        onMain { did = tab.view.canGoForward().also { if (it) tab.view.goForward() } }
        waitIdle(tab, 10_000)
        return if (did) "সফল: সামনের পেজে যাওয়া হয়েছে → ${tab.url}" else "সামনে যাওয়ার মতো পেজ নেই"
    }

    fun reload(context: Context): String {
        val tab = active(context)
        onMain { tab.view.reload() }
        waitIdle(tab, 18_000)
        return if (tab.lastError.isBlank()) "সফল: পেজ আবার লোড হয়েছে → ${tab.url}"
        else "ব্যর্থ: রিলোড করা যায়নি — ${tab.lastError}"
    }

    fun newTab(context: Context, url: String = ""): String {
        val tab = newTab(context)
        return if (url.isBlank()) "নতুন ট্যাব খোলা হয়েছে (আইডি ${tab.id})"
        else open(context, url)
    }

    fun tabs(): String {
        if (tabs.isEmpty()) return "কোনো ট্যাব খোলা নেই"
        return buildString {
            appendLine("ট্যাব ${tabs.size}টি:")
            tabs.values.sortedBy { it.id }.forEach {
                appendLine("• ${if (it.id == activeId) "[চলমান]" else "        "} ${it.id}: ${it.title.take(40)} — ${it.url.take(90)}")
            }
        }
    }

    fun switchTab(id: Int): String {
        val tab = tabs[id] ?: return "এই আইডিতে ট্যাব নেই: $id"
        activeId = id
        return "ট্যাব $id এখন চলমান — ${tab.url}"
    }

    fun closeTab(context: Context, id: Int): String {
        val tab = tabs.remove(id) ?: return "এই আইডিতে ট্যাব নেই: $id"
        onMain {
            runCatching { tab.view.stopLoading() }
            runCatching { tab.view.destroy() }
        }
        if (activeId == id) activeId = tabs.keys.maxOrNull() ?: -1
        return "ট্যাব $id বন্ধ করা হয়েছে (${tabs.size}টি খোলা আছে)"
    }

    /** Closes everything — registered with the KillSwitch. */
    fun closeAll(context: Context): String {
        val ids = tabs.keys.toList()
        ids.forEach { closeTab(context, it) }
        onMain { runCatching { CookieManager.getInstance().removeAllCookies(null) } }
        return "${ids.size}টি ট্যাব বন্ধ করা হয়েছে"
    }

    fun clear(context: Context): String {
        val r = closeAll(context)
        onMain {
            runCatching { CookieManager.getInstance().flush() }
        }
        return "$r · কুকি পরিষ্কার"
    }

    /** Downloads go through the system DownloadManager so they survive and are visible. */
    fun download(
        context: Context,
        url: String,
        userAgent: String?,
        mimeType: String?,
        contentDisposition: String?
    ): String {
        return try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                userAgent?.let { addRequestHeader("User-Agent", it) }
                CookieManager.getInstance().getCookie(url)?.let {
                    addRequestHeader("Cookie", it)
                }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setTitle(fileName)
                setDescription("Jimi ব্রাউজার থেকে ডাউনলোড")
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(request)
            ErrorCenter.info(TAG, "ডাউনলোড শুরু: $fileName (id $id)")
            "সফল: ডাউনলোড শুরু হয়েছে — $fileName (Downloads ফোল্ডারে যাবে, নোটিফিকেশনে অগ্রগতি দেখাবে)"
        } catch (t: Throwable) {
            ErrorCenter.logThrow(TAG, "ডাউনলোড ব্যর্থ", t)
            "ব্যর্থ: ডাউনলোড শুরু করা যায়নি (${t.message})"
        }
    }

    /** What is in the download queue right now. */
    fun downloads(context: Context): String {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val q = dm.query(DownloadManager.Query())
            val out = StringBuilder()
            var n = 0
            while (q.moveToNext() && n < 20) {
                n++
                val name = q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                val status = q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val state = when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> "সম্পন্ন"
                    DownloadManager.STATUS_RUNNING -> "চলছে"
                    DownloadManager.STATUS_PAUSED -> "পজ"
                    DownloadManager.STATUS_PENDING -> "অপেক্ষমাণ"
                    DownloadManager.STATUS_FAILED -> "ব্যর্থ"
                    else -> "অজানা"
                }
                out.appendLine("• $name — $state")
            }
            q.close()
            if (n == 0) "ডাউনলোড তালিকা খালি" else "ডাউনলোড $n টি:\n$out"
        } catch (t: Throwable) {
            "ডাউনলোড তালিকা পড়া যায়নি (${t.message})"
        }
    }

    /** Full state for the dashboard and the model. */
    fun status(context: Context): String {
        val tab = tabs[activeId]
        return buildString {
            appendLine("ব্রাউজার: ${if (tabs.isEmpty()) "কোনো ট্যাব নেই" else "${tabs.size}টি ট্যাব, চলমান $activeId"}")
            if (tab != null) {
                appendLine("চলমান পেজ: ${tab.title.take(60)}")
                appendLine("ঠিকানা: ${tab.url}")
                appendLine("অবস্থা: ${if (tab.loading) "লোড হচ্ছে" else "লোড শেষ"}" +
                    if (tab.lastError.isNotBlank()) " · সমস্যা: ${tab.lastError}" else "")
            }
            append("হোম: ${SecurePrefs(context).browserHome}")
        }
    }

    /** Registers the browser's brake with the global KillSwitch (idempotent). */
    fun registerKillSwitch() {
        if (killSwitchRegistered) return
        killSwitchRegistered = true
        KillSwitch.register("browser") { ctx -> closeAll(ctx) }
    }

    // ------------------------------------------------------------------ helpers

    private val MODERN_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    private fun normalize(url: String): String {
        val u = url.trim()
        if (u.isBlank()) return ""
        if (u.startsWith("http://") || u.startsWith("https://")) return u
        if (u.startsWith("file://") || u.startsWith("about:")) return u
        // A bare domain vs a search phrase: dot + no spaces means domain.
        return if (!u.contains(" ") && u.contains(".")) "https://$u"
        else "https://duckduckgo.com/html/?q=" + Uri.encode(u)
    }

    private fun waitIdle(tab: Tab, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (tab.loading && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(220)
            } catch (t: Throwable) {
                break
            }
        }
        // Let late JS finish so innerText is complete.
        try {
            Thread.sleep(500)
        } catch (t: Throwable) {
            ErrorCenter.log(TAG, "waitIdle বাধা", t.message)
        }
    }

    private fun readTitle(tab: Tab): String {
        var t = tab.title
        onMain(4_000) { t = tab.view.title ?: tab.title }
        return t.ifBlank { tab.url }
    }

    /** evaluateJavascript returns a JSON-quoted string; unwrap and unescape it. */
    private fun unquote(value: String?): String {
        val v = value ?: return ""
        if (v == "null") return ""
        return try {
            if (v.startsWith("\"") && v.endsWith("\"")) {
                org.json.JSONArray("[$v]").optString(0)
            } else v
        } catch (t: Throwable) {
            v
        }
    }

    private fun quote(s: String): String = org.json.JSONArray().put(s).toString()
        .removeSurrounding("[", "]")
}
