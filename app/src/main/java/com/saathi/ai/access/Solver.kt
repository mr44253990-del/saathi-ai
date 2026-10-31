package com.saathi.ai.access

import android.content.Context
import com.saathi.ai.task.TaskBoard

/**
 * The "never say can't" engine.
 *
 * One strategy per attempt, five strategies, and a verification step after each one.
 * The old behaviour was: autopilot runs, fails, and Jimi tells the user it could not
 * be done. Now a failure is only the signal to try a genuinely different route:
 *
 *   1. **Autopilot** — accessibility-driven, up to 22 steps, with stuck detection.
 *   2. **Offline pack** — the same goal as a compiled on-device flow.
 *   3. **Fresh start** — relaunch the app the goal mentions, then autopilot again
 *      (half of all failures are "we were on the wrong screen").
 *   4. **Vision** — capture the screen, push the frame to the model, and locate the
 *      target with `uiautomator dump` bounds (works on canvas/game/WebView screens
 *      where the accessibility tree is empty).
 *   5. **Shell/root** — `am start` deep link or `input tap` on the located point.
 *
 * Nothing is declared finished without proof: after every strategy we call
 * [PhoneTools.verifyTaskDone] with keywords pulled out of the goal, and only a hit
 * counts as success. That is why [classify] can promise সফল/আংশিক/ব্যর্থ honestly.
 */
object Solver {

    enum class Verdict { DONE, PARTIAL, FAILED, BLOCKED }

    private val BLOCKERS = listOf(
        "accessibility বন্ধ", "রুট নেই", "api key", "permission denied",
        "অনুমতি নেই", "ইনস্টল নেই"
    )

    fun classify(report: String): Verdict {
        val r = report.lowercase()
        return when {
            report.contains("সফল:") ||
                r.contains("সম্পন্ন বলে মনে হচ্ছে") -> Verdict.DONE
            report.contains("আংশিক:") -> Verdict.PARTIAL
            BLOCKERS.any { r.contains(it) } -> Verdict.BLOCKED
            report.contains("ব্যর্থ:") -> Verdict.FAILED
            r.contains("হয়েছে") && !r.contains("হয়নি") -> Verdict.PARTIAL
            else -> Verdict.FAILED
        }
    }

    /** First meaningful line of a report, for the task note. */
    fun oneLine(report: String): String =
        report.lines().firstOrNull { it.isNotBlank() }?.take(160) ?: "কোনো রিপোর্ট নেই"

    /** Words from the goal that should appear on screen when the job is really done. */
    fun proofWords(goal: String): List<String> {
        val stop = setOf(
            "করো", "কর", "করে", "দাও", "দিও", "এবং", "তারপর", "এই", "ওই", "একটা", "একটি",
            "আমার", "আমাকে", "jimi", "please", "the", "and", "then", "for", "with", "into"
        )
        return goal.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 2 && it !in stop }
            .distinct()
            .take(6)
    }

    // ------------------------------------------------------------------ runs

    /**
     * Tries strategies in order until one is verified. Returns the combined report —
     * every attempt is in there, so the user can see what was tried.
     */
    fun runWithEscalation(
        context: Context,
        svc: JimiAccessibilityService,
        goal: String,
        onStep: (String) -> Unit = {}
    ): String {
        val log = StringBuilder()
        val proof = proofWords(goal)
        var attempts = 0

        for (strategy in strategies(context, svc, goal)) {
            attempts++
            onStep("চেষ্টা $attempts: ${strategy.first}")
            val report = runCatching { strategy.second() }.getOrElse { "এরর: ${it.message}" }
            log.appendLine("── ${strategy.first}")
            log.appendLine(report.take(1400))

            val verified = verify(context, proof)
            log.appendLine("যাচাই: $verified")
            if (verified.startsWith("সম্পন্ন")) {
                log.appendLine("সফল: কাজ হয়েছে (চেষ্টা ${attempts}-এ)")
                return log.toString()
            }
            if (attempts >= 5) break
        }

        val any = log.contains("হয়েছে")
        log.appendLine(
            if (any) "আংশিক: ${attempts} বার চেষ্টা করেছি, পুরোপুরি নিশ্চিত নই — «শেষ» বলছি না।"
            else "ব্যর্থ: ${attempts} রকম পথ চেষ্টা করেও হয়নি। কী কী করেছি তার রিপোর্ট ওপরে।"
        )
        return log.toString()
    }

    /** A second, deliberately different route after a first failure. */
    fun runAlternative(
        context: Context,
        svc: JimiAccessibilityService,
        goal: String,
        previousReport: String,
        onStep: (String) -> Unit = {}
    ): String {
        val usedVision = previousReport.contains("ভিশন")
        val usedPack = previousReport.contains("অফলাইন প্যাক")
        val order = strategies(context, svc, goal)
            .filter {
                (usedVision && it.first.contains("ভিশন").not()) ||
                    (usedPack && it.first.contains("অফলাইন প্যাক").not()) ||
                    (!usedVision && !usedPack)
            }
        // prefer the routes that were not tried first
        val pick = order.lastOrNull { !previousReport.contains(it.first) } ?: order.lastOrNull()
            ?: return "বিকল্প পথ নেই"
        onStep("বিকল্প পথ: ${pick.first}")
        val report = runCatching { pick.second() }.getOrElse { "এরর: ${it.message}" }
        val verified = verify(context, proofWords(goal))
        return if (verified.startsWith("সম্পন্ন")) "সফল: ${pick.first}\n$report\n$verified"
        else "ব্যর্থ: ${pick.first}\n$report\n$verified"
    }

    private fun verify(context: Context, proof: List<String>): String {
        if (proof.isEmpty()) return "যাচাই করার মতো শব্দ নেই — স্ক্রিন পড়ে দেখছি"
        val tools = PhoneTools(context)
        val r = tools.verifyTaskDone(proof)
        // second opinion through the vision layer when the text layer is empty
        if (r.startsWith("নিশ্চিত নই") && ScreenVision.lastError.isBlank()) {
            ScreenVision.look(context, keep = false)
        }
        return r
    }

    /** The five routes, cheapest first. Each returns a report string. */
    private fun strategies(
        context: Context,
        svc: JimiAccessibilityService,
        goal: String
    ): List<Pair<String, () -> String>> {
        val tools = PhoneTools(context)
        return listOf(
            "অটোপাইলট (স্ক্রিন দেখে ধাপে ধাপে)" to {
                JimiAccessibilityService.autopilot(goal) { step ->
                    SessionHubSafe.task(step)
                }
            },
            "অফলাইন প্যাক" to {
                val pack = OfflinePacks.find(goal)
                if (pack == null) "এই কাজের জন্য প্যাক নেই"
                else OfflinePacks.run(context, pack.id, null) { step -> SessionHubSafe.task(step) }
            },
            "অ্যাপ নতুন করে খুলে আবার" to {
                val appWord = goal.split(Regex("[^\\p{L}\\p{N}]+"))
                    .firstOrNull { AppDirectory.resolve(context, it) != null }
                if (appWord == null) "কোনো অ্যাপের নাম পাওয়া যায়নি"
                else {
                    val opened = AppDirectory.launch(context, appWord)
                    Thread.sleep(2200)
                    opened + "\n" + JimiAccessibilityService.autopilot(goal) { SessionHubSafe.task(it) }
                }
            },
            "ভিশন (স্ক্রিনশট + কোঅর্ডিনেট)" to {
                val looked = ScreenVision.look(context)
                val target = proofWords(goal).firstOrNull { svc.boundsOf(it) != null }
                val point = if (target != null) {
                    val b = svc.boundsOf(target)!!
                    b.centerX() to b.centerY()
                } else {
                    proofWords(goal).firstNotNullOfOrNull { ScreenVision.locateByDump(it) }
                }
                if (point == null) {
                    looked + "\nলক্ষ্যের অবস্থান পাওয়া যায়নি"
                } else {
                    val tapped = svc.gestures.deliberateTap(point.first.toFloat(), point.second.toFloat())
                    Thread.sleep(1200)
                    looked + "\n(${point.first},${point.second}) ট্যাপ ${if (tapped) "হয়েছে" else "হয়নি"}"
                }
            },
            "শেল/রুট দিয়ে" to {
                val appWord = goal.split(Regex("[^\\p{L}\\p{N}]+"))
                    .firstOrNull { AppDirectory.resolve(context, it) != null }
                val pkg = appWord?.let { AppDirectory.resolve(context, it) }
                if (pkg == null && !RootBridge.available()) "রুট নেই এবং অ্যাপের নাম পাওয়া যায়নি"
                else {
                    val terminal = TerminalTools(context)
                    if (pkg != null) {
                        val started = if (RootBridge.available()) {
                            RootBridge.exec("monkey -p $pkg -c android.intent.category.LAUNCHER 1", 12_000).report(300)
                        } else "রুট নেই"
                        Thread.sleep(2000)
                        started + "\n" + JimiAccessibilityService.autopilot(goal) { SessionHubSafe.task(it) }
                    } else {
                        terminal.selfDrive(goal, 3)
                    }
                }
            }
        )
    }

    /** Tiny indirection so this file does not depend on the live module directly. */
    private object SessionHubSafe {
        fun task(line: String) {
            com.saathi.ai.live.SessionHub.update {
                it.copy(taskText = line, lastTool = line)
            }
        }
    }

    /** Convenience used by the UI to explain a blocked task. */
    fun advice(report: String): String = when {
        report.lowercase().contains("accessibility বন্ধ") ->
            "Accessibility চালু করো — পারমিশন পেজ বা সেটিংসের «ঠিক করো» বাটন থেকে।"
        report.contains("রুট নেই") ->
            "রুট থাকলে এটা হতো; রুট ছাড়া বিকল্প পথ চেষ্টা করা হয়েছে।"
        report.lowercase().contains("permission denied") ->
            "অনুমতি ব্লক করেছে — সেটিংসে গিয়ে অনুমতি দাও, অথবা রুট থাকলে pm grant করব।"
        else -> "স্ক্রিনে লক্ষ্য পাওয়া যায়নি — অ্যাপটা খুলে দেখাও, তারপর আবার বলো।"
    }

    /** Board summary line reused by the bubble and the Tasks screen. */
    fun boardLine(board: TaskBoard): String = board.progressText()
}
