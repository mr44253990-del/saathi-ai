package com.saathi.ai.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.saathi.ai.live.SessionHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Jimi's hands.
 *
 * Hard-won rules for staying alive on real devices:
 *  1. Never block the accessibility thread — every node walk is depth- and
 *     time-bounded, and nothing loops unboundedly.
 *  2. Never crash on a stale node. OEM ROMs hand out nodes that die the moment
 *     you touch them; every access is wrapped.
 *  3. Advertise the smallest event surface possible (see the XML config). A busy
 *     event stream is what gets a service marked "malfunctioning".
 *  4. Recover by ourselves: if `rootInActiveWindow` fails we retry on the next
 *     call instead of tearing down the service.
 */
class JimiAccessibilityService : AccessibilityService() {

    private val startedAt = System.currentTimeMillis()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connected.value = true
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        try {
            when (e.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    e.packageName?.toString()?.let { pkg ->
                        if (pkg.isNotBlank() && pkg != "com.android.systemui") {
                            lastPackage = pkg
                            appLabel = appLabelFor(pkg)
                            _currentApp.value = appLabel
                        }
                    }
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // throttled snapshot of what is on screen, for the bubble caption
                    val now = System.currentTimeMillis()
                    if (now - lastSnapshotAt > 1200) {
                        lastSnapshotAt = now
                        screenHash.value = safeRoot()?.let { hashOf(it) } ?: 0
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "event skipped: ${t.javaClass.simpleName}")
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        connected.value = false
        super.onDestroy()
    }

    // ------------------------------------------------------------------ reading

    private fun safeRoot(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (t: Throwable) {
        null
    }

    /** A compact, agent-friendly description of the visible screen. */
    fun dumpScreen(maxChars: Int = 3200): String {
        val root = safeRoot() ?: return "(স্ক্রিন পড়া যাচ্ছে না — Accessibility আবার চালু করতে হবে)"
        val sb = StringBuilder()
        try {
            sb.append("অ্যাপ: ").append(appLabel.ifBlank { lastPackage }).append('\n')
            walk(root, sb, 0, maxChars)
        } catch (t: Throwable) {
            Log.w(TAG, "dump failed", t)
        }
        return sb.toString().trim().ifBlank { "(স্ক্রিনে পড়ার মতো লেখা নেই)" }
    }

    private fun walk(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int, maxChars: Int) {
        if (node == null || depth > 26 || sb.length > maxChars) return
        try {
            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val label = if (text.isNotEmpty()) text else desc
                if (label.length in 1..140) {
                    val marker = when {
                        node.isEditable -> "✎ "
                        node.isClickable -> "• "
                        node.isScrollable -> "↕ "
                        else -> "- "
                    }
                    sb.append(marker).append(label.replace('\n', ' ')).append('\n')
                }
            }
            for (i in 0 until node.childCount) walk(node.getChild(i), sb, depth + 1, maxChars)
        } catch (t: Throwable) {
            // stale node — stop this branch, never the whole walk
        }
    }

    fun findAllByText(query: String): List<AccessibilityNodeInfo> {
        val root = safeRoot() ?: return emptyList()
        val q = query.lowercase().trim()
        if (q.isEmpty()) return emptyList()
        val out = mutableListOf<AccessibilityNodeInfo>()
        try {
            collect(root, q, out, 0)
        } catch (t: Throwable) {
            Log.w(TAG, "find failed", t)
        }
        return out
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        q: String,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int
    ) {
        if (node == null || depth > 32 || out.size >= 10) return
        try {
            val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").lowercase()
            if (text.isNotBlank() && text.contains(q)) out.add(node)
            for (i in 0 until node.childCount) collect(node.getChild(i), q, out, depth + 1)
        } catch (t: Throwable) {
            // stale
        }
    }

    // ------------------------------------------------------------------ acting

    fun tapByText(query: String): Boolean {
        val hits = findAllByText(query)
        for (node in hits) if (clickNode(node)) return true
        return false
    }

    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        try {
            var n = node
            var guard = 0
            while (n != null && guard < 6) {
                if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                n = n.parent
                guard++
            }
        } catch (t: Throwable) {
            // fall through to gesture
        }
        val src = node ?: return false
        return try {
            val rect = Rect()
            src.getBoundsInScreen(rect)
            if (rect.width() <= 0 || rect.height() <= 0) false
            else tap(rect.centerX().toFloat(), rect.centerY().toFloat())
        } catch (t: Throwable) {
            false
        }
    }

    /** Tap in the middle of the first node whose text matches, else false. */
    fun tapCenterOf(query: String): Boolean {
        val node = findAllByText(query).firstOrNull() ?: return false
        return try {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            tap(rect.centerX().toFloat(), rect.centerY().toFloat())
        } catch (t: Throwable) {
            false
        }
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return gesture(GestureDescription.StrokeDescription(path, 0, 55))
    }

    fun longPress(x: Float, y: Float, durationMs: Long = 650): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return gesture(GestureDescription.StrokeDescription(path, 0, durationMs))
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 260): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return gesture(GestureDescription.StrokeDescription(path, 0, durationMs))
    }

    fun scroll(down: Boolean): Boolean {
        val scrollable = findScrollable(safeRoot(), 0)
        if (scrollable != null) {
            val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            try {
                if (scrollable.performAction(action)) return true
            } catch (t: Throwable) {
                // fall back to a swipe
            }
        }
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val h = dm.heightPixels.toFloat()
        return if (down) {
            swipe(cx, h * 0.74f, cx, h * 0.26f, 240)
        } else {
            swipe(cx, h * 0.26f, cx, h * 0.74f, 240)
        }
    }

    private fun findScrollable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 22) return null
        return try {
            if (node.isScrollable && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) {
                val r = findScrollable(node.getChild(i), depth + 1)
                if (r != null) return r
            }
            null
        } catch (t: Throwable) {
            null
        }
    }

    /** Pinch — camera zoom, maps, browsers. */
    fun pinch(zoomIn: Boolean): Boolean {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val cy = dm.heightPixels / 2f
        val start = if (zoomIn) 130f else 540f
        val end = if (zoomIn) 540f else 130f
        val p1 = Path().apply { moveTo(cx - start, cy); lineTo(cx - end, cy) }
        val p2 = Path().apply { moveTo(cx + start, cy); lineTo(cx + end, cy) }
        return gesture(
            GestureDescription.StrokeDescription(p1, 0, 320),
            GestureDescription.StrokeDescription(p2, 0, 320)
        )
    }

    private fun gesture(vararg strokes: GestureDescription.StrokeDescription): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val intent = AtomicBoolean(false)
        return try {
            val builder = GestureDescription.Builder()
            strokes.forEach { builder.addStroke(it) }
            dispatchGesture(
                builder.build(),
                object : GestureResultCallback() {
                    override fun onCompleted(d: GestureDescription?) = intent.set(true)
                    override fun onCancelled(d: GestureDescription?) = intent.set(false)
                },
                null
            )
            true
        } catch (t: Throwable) {
            Log.w(TAG, "gesture failed", t)
            false
        }
    }

    fun typeText(text: String): Boolean {
        val root = safeRoot() ?: return false
        try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) return setText(focused, text)
            val editable = findEditable(root, 0) ?: return false
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            editable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return setText(editable, text)
        } catch (t: Throwable) {
            return false
        }
    }

    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (t: Throwable) {
            false
        }
    }

    /** Types into whatever field is focused right now (used after open_app+search UIs). */
    fun typeIntoFocused(text: String): Boolean = typeText(text)

    /** Presses the keyboard's search/enter key by tapping the IME action node. */
    fun pressEnter(): Boolean {
        val root = safeRoot() ?: return false
        val labels = listOf("search", "খুঁজুন", "সার্চ", "go", "done", "enter", "→")
        try {
            for (label in labels) {
                if (tapByText(label)) return true
            }
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                return focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id)
            }
        } catch (t: Throwable) {
            // ignore
        }
        return false
    }

    private fun findEditable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 22) return null
        return try {
            if (node.isEditable && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) {
                val r = findEditable(node.getChild(i), depth + 1)
                if (r != null) return r
            }
            null
        } catch (t: Throwable) {
            null
        }
    }

    fun globalAction(action: Int): Boolean = try {
        performGlobalAction(action)
    } catch (t: Throwable) {
        false
    }

    /** What the user has selected / copied most recently. */
    fun clipboardText(): String {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
        return try {
            cm.primaryClip?.let { clip ->
                if (clip.itemCount == 0) "" else clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
            } ?: ""
        } catch (t: Throwable) {
            ""
        }
    }

    fun copyToClipboard(text: String): Boolean = try {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        cm.setPrimaryClip(ClipData.newPlainText("Jimi", text))
        true
    } catch (t: Throwable) {
        false
    }

    // ------------------------------------------------------------------ screen geometry

    fun screenWidth(): Int = resources.displayMetrics.widthPixels
    fun screenHeight(): Int = resources.displayMetrics.heightPixels

    /** Bounds of the first node matching [query] — lets Jimi tap precisely. */
    fun boundsOf(query: String): Rect? = try {
        findAllByText(query).firstOrNull()?.let { node ->
            Rect().also { node.getBoundsInScreen(it) }
        }
    } catch (t: Throwable) {
        null
    }

    private fun hashOf(node: AccessibilityNodeInfo?): Int = try {
        var h = 17
        val sb = StringBuilder()
        walk(node, sb, 0, 400)
        h = 31 * h + sb.toString().hashCode()
        h
    } catch (t: Throwable) {
        0
    }

    private fun appLabelFor(pkg: String?): String {
        if (pkg.isNullOrBlank()) return ""
        return try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (t: Throwable) {
            pkg
        }
    }

    companion object {
        private const val TAG = "JimiA11y"

        @Volatile
        var instance: JimiAccessibilityService? = null
            private set

        @Volatile
        var lastPackage: String = ""

        @Volatile
        var appLabel: String = ""

        private var lastSnapshotAt = 0L

        val connected = MutableStateFlow(false)
        val screenHash = MutableStateFlow(0)

        /** Live view of what the user is looking at; the bubble reads this. */
        private val _currentApp = MutableStateFlow("")
        val currentApp: StateFlow<String> get() = _currentApp

        fun isEnabled(): Boolean = instance != null

        fun screenText(maxChars: Int = 3200): String =
            instance?.dumpScreen(maxChars) ?: "(Accessibility বন্ধ)"

        fun awakeFor(): Long = instance?.startedAt?.let { System.currentTimeMillis() - it } ?: 0L
    }
}
