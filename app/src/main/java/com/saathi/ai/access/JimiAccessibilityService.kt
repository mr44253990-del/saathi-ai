package com.saathi.ai.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Jimi's "hands". This accessibility service is what lets the assistant actually
 * *operate* the phone: tap things on screen, scroll, type, go back/home, open the
 * notification shade, zoom (pinch), and read what is currently visible.
 *
 * It is registered in the manifest and must be enabled once by the user in
 * Settings → Accessibility → Jimi (the app deep-links there from the
 * permissions page).
 */
class JimiAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We work on demand, so events are only used to track the foreground package.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let { lastPackage = it.toString() }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ helpers

    private fun rootNode(): AccessibilityNodeInfo? =
        try {
            rootInActiveWindow
        } catch (t: Throwable) {
            null
        }

    fun dumpScreen(limit: Int = 300): String {
        val root = rootNode() ?: return ""
        val sb = StringBuilder()
        walk(root, sb, 0, limit)
        return sb.toString().trim()
    }

    private fun walk(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int, limit: Int) {
        if (node == null || depth > limit / 3 || sb.length > 6000) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.isNotEmpty() || desc.isNotEmpty()) {
            val label = if (text.isNotEmpty()) text else desc
            if (label.length in 1..120) {
                sb.append(if (node.isClickable) "• " else "- ")
                sb.append(label)
                sb.append('\n')
            }
        }
        for (i in 0 until node.childCount) {
            walk(node.getChild(i), sb, depth + 1, limit)
        }
    }

    fun findAllByText(query: String): List<AccessibilityNodeInfo> {
        val root = rootNode() ?: return emptyList()
        val q = query.lowercase().trim()
        if (q.isEmpty()) return emptyList()
        val found = mutableListOf<AccessibilityNodeInfo>()
        collect(root, q, found, 0)
        return found
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        q: String,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int
    ) {
        if (node == null || depth > 40 || out.size > 12) return
        val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").lowercase()
        if (text.contains(q)) out.add(node)
        for (i in 0 until node.childCount) collect(node.getChild(i), q, out, depth + 1)
    }

    /** Taps the first node whose text/description contains [query]. */
    fun tapByText(query: String): Boolean {
        findAllByText(query).forEach { node ->
            if (clickNode(node)) return true
        }
        return false
    }

    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        var n = node
        var guard = 0
        while (n != null && guard < 6) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            guard++
        }
        // fall back to a gesture in the middle of the node bounds
        val src = node ?: return false
        val rect = android.graphics.Rect()
        src.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return false
        return tap(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return gesture(
            GestureDescription.StrokeDescription(path, 0, 60)
        )
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 260): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return gesture(GestureDescription.StrokeDescription(path, 0, durationMs))
    }

    fun scroll(down: Boolean): Boolean {
        val root = rootNode()
        val scrollable = findScrollable(root, 0)
        if (scrollable != null) {
            val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (scrollable.performAction(action)) return true
        }
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        return if (down) {
            swipe(cx, dm.heightPixels * 0.72f, cx, dm.heightPixels * 0.28f)
        } else {
            swipe(cx, dm.heightPixels * 0.28f, cx, dm.heightPixels * 0.72f)
        }
    }

    private fun findScrollable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 25) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val r = findScrollable(node.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    /** Pinch-out / pinch-in — works in the camera, maps and browsers alike. */
    fun pinch(zoomIn: Boolean): Boolean {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val cy = dm.heightPixels / 2f
        val start = if (zoomIn) 120f else 520f
        val end = if (zoomIn) 520f else 120f

        val p1 = Path().apply { moveTo(cx - start, cy); lineTo(cx - end, cy) }
        val p2 = Path().apply { moveTo(cx + start, cy); lineTo(cx + end, cy) }
        val stroke1 = GestureDescription.StrokeDescription(p1, 0, 320)
        val stroke2 = GestureDescription.StrokeDescription(p2, 0, 320)
        return gesture(stroke1, stroke2)
    }

    private fun gesture(vararg strokes: GestureDescription.StrokeDescription): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val builder = GestureDescription.Builder()
        strokes.forEach { builder.addStroke(it) }
        return try {
            dispatchGesture(builder.build(), null, null)
        } catch (t: Throwable) {
            false
        }
    }

    fun typeText(text: String): Boolean {
        val root = rootNode() ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        // No focused field: tap the first editable node, then type.
        val editable = findEditable(root, 0) ?: return false
        editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        editable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 25) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val r = findEditable(node.getChild(i), depth + 1)
            if (r != null) return r
        }
        return null
    }

    fun globalAction(action: Int): Boolean = try {
        performGlobalAction(action)
    } catch (t: Throwable) {
        false
    }

    companion object {
        private const val TAG = "JimiA11y"

        @Volatile
        var instance: JimiAccessibilityService? = null
            private set

        @Volatile
        var lastPackage: String = ""

        fun isEnabled(): Boolean = instance != null
    }
}
