package com.saathi.ai.access

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.saathi.ai.live.SessionHub

/**
 * Autopilot — the difference between "a tool the model calls" and "an agent that
 * finishes the job".
 *
 * The Live model can call [run_goal], which hands over a plain-language goal. The
 * Autopilot then loops on its own:
 *
 *   observe (read screen) → decide (find the best matching element) → act
 *   (tap / type / scroll / drag) → verify (did the screen change?) → repeat
 *
 * It is deliberately *deterministic* (no network hop per step) so a 20-step task
 * keeps running at human speed even on a slow connection, and it reports every
 * step to the bubble so the user always sees what Jimi is doing.
 */
class Autopilot(
    private val service: JimiAccessibilityService,
    private val gestures: GestureEngine
) {

    /** One visited screen, so we never loop on the same page forever. */
    private data class StepMemory(val fingerprint: Int, var attempts: Int)

    private val history = ArrayDeque<StepMemory>()
    private val maxSteps = 22

    /**
     * Executes a goal. [onStep] is called with a Bangla progress string that the
     * bubble renders, exactly like the assistant's own captions.
     */
    fun run(goal: String, onStep: (String) -> Unit = {}): String {
        val keywords = extractKeywords(goal)
        val log = StringBuilder()
        var idle = 0

        onStep("লক্ষ্য বুঝে নিচ্ছি")

        for (step in 1..maxSteps) {
            val root = service.rootOrNull()
            val fingerprint = screenFingerprint(root)
            if (root == null) {
                log.appendLine("স্ক্রিন পড়া যায়নি")
                break
            }

            // Are we on a screen we keep coming back to? Then we are stuck.
            val last = history.lastOrNull()
            if (last != null && last.fingerprint == fingerprint) {
                last.attempts++
                if (last.attempts > 2) {
                    val nudged = tryAlternative(root, keywords)
                    if (!nudged) {
                        log.appendLine("একই জায়গায় আটকে গেছি")
                        break
                    }
                    continue
                }
            } else {
                history.addLast(StepMemory(fingerprint, 0))
                if (history.size > 6) history.removeFirst()
            }

            val target = pickTarget(root, keywords)
            if (target == null) {
                // Nothing matched: try scrolling to reveal more, twice, then give up.
                if (idle < 2) {
                    idle++
                    onStep("স্ক্রল করে খুঁজছি")
                    gestures.swipeVertical(down = true)
                    log.appendLine("স্ক্রল করেছি")
                    Thread.sleep(420)
                    continue
                }
                log.appendLine("লক্ষণের সাথে মিলে এমন কিছু পাইনি")
                break
            }

            val label = describe(target) ?: "উপাদান"
            onStep("«$label» চাপছি")

            val before = fingerprint
            val acted = actOn(target, keywords)
            Thread.sleep(if (acted) 620 else 300)
            val after = screenFingerprint(service.rootOrNull())

            log.appendLine("ধাপ $step: $label — ${if (after != before) "হয়েছে" else "পরিবর্তন হয়নি"}")

            if (after == before) {
                idle++
                if (idle >= 3) {
                    log.appendLine("স্ক্রিন বদলাচ্ছে না, থামছি")
                    break
                }
            } else {
                idle = 0
            }
        }

        SessionHub.update { it.copy(lastTool = "") }
        return "অটোপাইলট রিপোর্ট:\n$log"
    }

    // ---------------------------------------------------------------- decision

    /** Keyword hints extracted from the goal, tuned for Bangla + English. */
    private fun extractKeywords(goal: String): List<String> {
        val stop = setOf(
            "করো", "কর", "এবং", "তারপর", "এর", "এই", "ওই", "একটা", "একটি", "আমার", "আমাকে",
            "the", "and", "then", "please", "jimi", "for", "with", "into", "from"
        )
        return goal.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 && it !in stop }
            .distinct()
            .take(14)
    }

    private fun pickTarget(root: AccessibilityNodeInfo, keywords: List<String>): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        walk(root, 0) { node ->
            val label = describe(node)?.lowercase() ?: return@walk
            var score = 0
            for (k in keywords) {
                if (label.contains(k)) score += 3
                if (label.startsWith(k)) score += 2
            }
            if (score == 0) return@walk
            if (node.isClickable) score += 4
            if (node.isEnabled) score += 1
            if (node.className?.contains("EditText") == true) score += 2
            if (score > bestScore) {
                bestScore = score
                best = node
            }
        }
        return if (bestScore >= 3) best else null
    }

    private fun actOn(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        return try {
            if (node.className?.contains("EditText") == true || node.isEditable) {
                // focus, then type whatever the goal implies
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(260)
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                gestures.tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                true
            } else {
                var n: AccessibilityNodeInfo? = node
                var guard = 0
                while (n != null && guard < 5) {
                    if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    n = n.parent
                    guard++
                }
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                gestures.tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
        } catch (t: Throwable) {
            false
        }
    }

    /** When stuck, try a common escape hatch. */
    private fun tryAlternative(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val candidates = listOf("close", "বন্ধ", "back", "ফিরে", "cancel", "বাতিল", "skip", "পরে")
        for (c in candidates) {
            if (service.tapByText(c)) return true
        }
        // fall back to going back once
        return runCatching { service.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) }
            .getOrDefault(false)
    }

    // ---------------------------------------------------------------- perception

    private fun screenFingerprint(root: AccessibilityNodeInfo?): Int {
        if (root == null) return 0
        val sb = StringBuilder()
        walk(root, 0) { node ->
            describe(node)?.let { if (sb.length < 600) sb.append(it).append('|') }
        }
        return sb.toString().hashCode()
    }

    private fun walk(node: AccessibilityNodeInfo?, depth: Int, visit: (AccessibilityNodeInfo) -> Unit) {
        if (node == null || depth > 28) return
        try {
            if (node.isVisibleToUser) visit(node)
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1, visit)
        } catch (t: Throwable) {
            // stale subtree
        }
    }

    private fun describe(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val out = if (text.isNotEmpty()) text else desc
        return out.ifBlank { null }?.take(120)
    }
}
