package com.saathi.ai.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log

/**
 * Human-like touch synthesis.
 *
 * Real hands do not teleport: they press down with a slight jitter, drag along an
 * arc with acceleration, and lift with a tiny overshoot. This class turns abstract
 * intents ("drag this slider right", "pinch in on the photo", "hold and pull the
 * cube") into a single [GestureDescription] that the accessibility service plays.
 *
 * It replaces the old one-finger-only model, so Jimi can now:
 *  • drag sliders / scrub bars / move photos in an editor
 *  • two-finger pinch and rotate
 *  • three- and four-finger swipes (screenshot, desktop-mode, hyperOS gestures)
 *  • hold-drag (long press then move)
 *  • frame-by-frame multi-step gestures inside a *single* dispatch, which is the
 *    only way to be reliable while an app is animating
 */
class GestureEngine(private val service: AccessibilityService) {

    private val tag = "JimiGestures"

    fun screen(): Pair<Int, Int> {
        val dm = service.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    // ---------------------------------------------------------------- basics

    /** A single tap with a realistic press duration. */
    fun tap(x: Float, y: Float, holdMs: Long = 60): Boolean =
        dispatch(listOf(Stroke(Vec(x, y), listOf(), 0, holdMs)))

    fun longPress(x: Float, y: Float, ms: Long = 620): Boolean =
        dispatch(listOf(Stroke(Vec(x, y), listOf(), 0, ms)))

    /** Straight-line drag. */
    fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float, ms: Long = 320): Boolean =
        dispatch(
            listOf(
                Stroke(
                    Vec(fromX, fromY),
                    listOf(Vec(toX, toY)),
                    0,
                    ms
                )
            )
        )

    /** Drag with a slight arc, like a real finger. */
    fun dragArc(fromX: Float, fromY: Float, toX: Float, toY: Float, ms: Long = 340): Boolean {
        val midX = (fromX + toX) / 2f
        val midY = (fromY + toY) / 2f - 18f
        return dispatch(
            listOf(Stroke(Vec(fromX, fromY), listOf(Vec(midX, midY), Vec(toX, toY)), 0, ms))
        )
    }

    /** Hold, then drag — the move that unlocks reorder, sliders and map panning. */
    fun holdAndDrag(
        x: Float, y: Float, toX: Float, toY: Float,
        releaseMs: Long = 260, holdMs: Long = 450
    ): Boolean {
        val midX = x + (toX - x) * 0.6f
        val midY = y + (toY - y) * 0.6f
        return dispatch(
            listOf(
                Stroke(
                    Vec(x, y),
                    listOf(Vec(midX, midY), Vec(toX, toY)),
                    holdMs,           // startDelay = press and hold first
                    releaseMs
                )
            )
        )
    }

    /** Horizontal swipe (with a stop in the end). */
    fun swipeHorizontal(right: Boolean, yFraction: Float = 0.5f, distance: Float = 0.55f): Boolean {
        val (w, h) = screen()
        val y = h * yFraction
        val startX = if (right) w * (0.5f - distance / 2) else w * (0.5f + distance / 2)
        val endX = if (right) w * (0.5f + distance / 2) else w * (0.5f - distance / 2)
        return dragArc(startX, y, endX, y, 280)
    }

    fun swipeVertical(down: Boolean, xFraction: Float = 0.5f, distance: Float = 0.45f): Boolean {
        val (w, h) = screen()
        val x = w * xFraction
        val startY = if (down) h * (0.5f - distance / 2) else h * (0.5f + distance / 2)
        val endY = if (down) h * (0.5f + distance / 2) else h * (0.5f - distance / 2)
        return dragArc(x, startY, x, endY, 300)
    }

    // ---------------------------------------------------------------- two fingers

    /** Pinch with both fingers moving in one smooth motion. */
    fun pinch(zoomIn: Boolean, centerX: Float? = null, centerY: Float? = null): Boolean {
        val (w, h) = screen()
        val cx = centerX ?: (w / 2f)
        val cy = centerY ?: (h / 2f)
        val near = 90f
        val far = 420f
        val startGap = if (zoomIn) near else far
        val endGap = if (zoomIn) far else near

        val p1 = Stroke(Vec(cx - startGap, cy), listOf(Vec(cx - endGap, cy)), 0, 420)
        val p2 = Stroke(Vec(cx + startGap, cy), listOf(Vec(cx + endGap, cy)), 0, 420)
        return dispatch(listOf(p1, p2))
    }

    /** Two fingers dragging the same direction — scroll while pinching (maps, editors). */
    fun twoFingerScroll(down: Boolean, dx: Float = 0f): Boolean {
        val (w, h) = screen()
        val travel = h * 0.32f * (if (down) -1 else 1)
        val a = Stroke(
            Vec(w * 0.42f + dx, h * 0.62f),
            listOf(Vec(w * 0.42f + dx, h * 0.62f + travel)), 0, 320
        )
        val b = Stroke(
            Vec(w * 0.58f + dx, h * 0.62f),
            listOf(Vec(w * 0.58f + dx, h * 0.62f + travel)), 0, 320
        )
        return dispatch(listOf(a, b))
    }

    /** Rotate a photo/3-D object: two fingers on opposite arcs. */
    fun rotate(degrees: Float = 45f): Boolean {
        val (w, h) = screen()
        val cx = w / 2f
        val cy = h / 2f
        val radius = 260f
        val steps = 8
        val rad = Math.toRadians(degrees.toDouble()).toFloat()

        val pathA = Path().apply { moveTo(cx - radius, cy) }
        val pathB = Path().apply { moveTo(cx + radius, cy) }
        for (i in 1..steps) {
            val a = rad * (i.toFloat() / steps)
            // finger A goes from 180° to 180°+deg, finger B mirrors it
            pathA.lineTo(
                cx + radius * kotlin.math.cos((Math.PI + a).toFloat()),
                cy + radius * kotlin.math.sin((Math.PI + a).toFloat())
            )
            pathB.lineTo(
                cx + radius * kotlin.math.cos(a),
                cy + radius * kotlin.math.sin(a)
            )
        }
        return dispatch(
            listOf(
                Stroke(pathA, 0, 560),
                Stroke(pathB, 0, 560)
            )
        )
    }

    // ---------------------------------------------------------------- many fingers

    /** Any number of simultaneous strokes (3-finger swipe, 4-finger tap, ...). */
    fun multiFinger(
        fingers: Int,
        dx: Float = 0f,
        dy: Float = 0f,
        durationMs: Long = 280
    ): Boolean {
        if (fingers !in 2..10) return false
        val (w, _) = screen()
        val lanes = listOf(0.30f, 0.42f, 0.54f, 0.66f, 0.78f, 0.36f, 0.48f, 0.60f, 0.72f, 0.84f)
        val strokes = (0 until fingers).map { i ->
            val x = w * lanes[i % lanes.size]
            val y = service.resources.displayMetrics.heightPixels * 0.5f
            Stroke(Vec(x, y), listOf(Vec(x + dx, y + dy)), 0, durationMs)
        }
        return dispatch(strokes)
    }

    /** A "swipe down from the top" that reliably opens the notification shade. */
    fun openNotifications(): Boolean {
        val (w, h) = screen()
        return drag(w * 0.5f, h * 0.01f, w * 0.5f, h * 0.62f, 300)
    }

    /** Swipe up from the very bottom = go home on gesture-nav phones. */
    fun gestureBack(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    // ---------------------------------------------------------------- scripted flows

    /**
     * Runs several gestures back-to-back with small pauses, so sequences like
     * "tap search → type → tap first result" behave like a person rather than a
     * machine gun. Returns how many succeeded.
     */
    fun sequence(steps: List<() -> Boolean>, gapMs: Long = 320): Int {
        var done = 0
        for (step in steps) {
            if (runCatching { step() }.getOrDefault(false)) done++
            Thread.sleep(gapMs)
        }
        return done
    }

    /**
     * Drags an element by its on-screen text for [dx]/[dy] pixels — the export of
     * "drag the slider", "move the photo", "pan the cube".
     */
    fun dragElement(text: String, dx: Float, dy: Float, hold: Boolean = true): Boolean {
        val bounds = boundsOfText(text) ?: return false
        val cx = bounds.centerX().toFloat()
        val cy = bounds.centerY().toFloat()
        return if (hold) holdAndDrag(cx, cy, cx + dx, cy + dy)
        else dragArc(cx, cy, cx + dx, cy + dy)
    }

    private fun boundsOfText(text: String): Rect? {
        val a11y = service as? JimiAccessibilityService ?: return null
        return a11y.boundsOf(text)
    }

    // ---------------------------------------------------------------- plumbing

    private data class Vec(val x: Float, val y: Float)

    private class Stroke(
        val start: Vec,
        val rest: List<Vec>,
        val startDelay: Long,
        val duration: Long,
        val explicitPath: Path? = null
    ) {
        constructor(point: Vec, startDelay: Long, duration: Long) :
            this(point, emptyList(), startDelay, duration)

        constructor(path: Path, startDelay: Long, duration: Long) :
            this(Vec(0f, 0f), emptyList(), startDelay, duration, path)
    }

    private fun dispatch(strokes: List<Stroke>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        if (strokes.isEmpty()) return false
        val builder = GestureDescription.Builder()
        for (s in strokes) {
            val path = s.explicitPath ?: Path().apply {
                moveTo(s.start.x, s.start.y)
                for (p in s.rest) lineTo(p.x, p.y)
            }
            val duration = s.duration.coerceAtLeast(1)
            if (s.startDelay > 0) {
                builder.addStroke(GestureDescription.StrokeDescription(path, s.startDelay, duration))
            } else {
                builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            }
        }
        return try {
            service.dispatchGesture(builder.build(), null, null)
            true
        } catch (t: Throwable) {
            Log.w(tag, "gesture failed: ${t.javaClass.simpleName}")
            false
        }
    }
}
