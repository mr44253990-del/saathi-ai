package com.saathi.ai.vision

import android.content.Context
import com.saathi.ai.access.JimiAccessibilityService
import com.saathi.ai.access.ScreenVision
import com.saathi.ai.access.Solver
import com.saathi.ai.agent.AgentContext
import com.saathi.ai.agent.AgentRequest
import com.saathi.ai.core.ApprovalGate
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.data.SecurePrefs

/**
 * The closed visual loop (Phase 3).
 *
 * capture → model decides → execute → capture again → verify → next, until the
 * goal is proven done, the budget runs out, or a sensitive screen appears.
 *
 * Three rules this file never breaks:
 *  1. **Screenshots are temporary.** Each frame is deleted right after the model
 *     has seen it; [ScreenVision.deleteAllShots] runs at the end too. Nothing is
 *     kept unless the user explicitly switched on "keep screenshots".
 *  2. **Sensitive screens stop the run.** If the dump contains password / OTP /
 *     final-payment wording, the loop ends with `আটকে:` and tells the user to do
 *     that part themselves. Jimi never types a password or confirms a payment.
 *  3. **Honest fallback.** When the key has no computer-use model, the loop says
 *     so and drops to the accessibility-tree + escalation ladder that already
 *     worked in v2 — no pretending the fancy path ran.
 */
object VisualCore {

    private const val TAG = "vision"
    private const val MAX_ROUNDS_HARD = 14

    /**
     * Runs the loop. Returns a report whose first line carries a verdict token.
     */
    fun run(
        ctx: AgentContext,
        req: AgentRequest,
        svc: JimiAccessibilityService,
        onStep: (String) -> Unit = {}
    ): String {
        val context = ctx.context
        val prefs = SecurePrefs(context)
        val client = ComputerUseClient(context)
        val goal = req.goal
        val proof = Solver.proofWords(goal)
        val history = mutableListOf<String>()
        val log = StringBuilder()
        var rounds = 0
        var lastText = ""
        var usedApi = false

        onStep("স্ক্রিন দেখে কাজ শুরু করছি: ${goal.take(48)}")

        while (rounds < minOf(req.budgetSteps, MAX_ROUNDS_HARD) && ctx.alive(req)) {
            rounds++

            // ---- 1. look
            val shot = ScreenVision.capture(context, maxSide = 1024, quality = 78)
            if (shot == null) {
                val why = ScreenVision.lastError.ifBlank { "স্ক্রিনশট তোলা যায়নি" }
                ErrorCenter.log(TAG, "স্ক্রিনশট ব্যর্থ", why)
                return fallback(ctx, req, svc, onStep, "স্ক্রিনশট তোলা যায়নি ($why)")
            }
            req.spend(1)

            // ---- 2. read the tree too: it is free, and it catches dialogs the
            //         screenshot alone would need another round to understand.
            val dump = svc.dumpScreen(2600)
            lastText = dump
            val sensitive = ActionMapper.isSensitiveScreen(dump)
            if (sensitive != null) {
                cleanup(context, prefs)
                return "আটকে: স্ক্রিনে সংবেদনশীল ঘর দেখা গেছে («$sensitive»)। পাসওয়ার্ড/OTP/চূড়ান্ত পেমেন্ট " +
                    "জিমি নিজে দেয় না — বাকি সব ভরে দিয়েছি, এই ঘরটি তুমি পূরণ করে কনফার্ম করো।\n" + log
            }

            // ---- 3. ask the model what to do
            val reply = client.ask(goal, shot.jpeg, history, shot.width, shot.height)
            if (!reply.ok) {
                ErrorCenter.log(TAG, "computer_use সাড়া দেয়নি", reply.error)
                if (!usedApi) {
                    log.appendLine("computer_use মডেল পাওয়া যায়নি (${reply.error.take(160)}) — বিকল্প পথে যাচ্ছি")
                    return fallback(ctx, req, svc, onStep, reply.error)
                }
                log.appendLine("মডেল সাড়া দেয়নি: ${reply.error.take(200)}")
                break
            }
            usedApi = true

            if (reply.done && reply.actions.isEmpty()) {
                log.appendLine("মডেল বলছে কাজ শেষ: ${reply.text.take(200)}")
                val verified = verify(svc, proof, goal)
                cleanup(context, prefs)
                return if (verified) {
                    "সফল: স্ক্রিন দেখে কাজ শেষ হয়েছে ও যাচাই করা হয়েছে\n$log\n${reply.text.take(400)}"
                } else {
                    "আংশিক: মডেল শেষ বলেছে, কিন্তু স্ক্রিনে প্রমাণ পাওয়া যায়নি — মিথ্যা বলব না।\n$log\n" +
                        "${reply.text.take(400)}"
                }
            }
            if (reply.actions.isEmpty()) {
                log.appendLine("মডেল কোনো অ্যাকশন দেয়নি: ${reply.text.take(180)}")
                if (reply.text.isNotBlank()) history.add("মডেল: ${reply.text.take(120)}")
                continue
            }

            // ---- 4. execute (at most three actions a round, so the screen can
            //         be re-checked before anything else happens)
            reply.actions.take(3).forEach { action ->
                onStep("${action.describe()} ${if (action.intent.isNotBlank()) "— ${action.intent.take(40)}" else ""}")
                val outcome = ActionMapper.execute(context, svc, action, shot.width, shot.height)
                // Successful actions feed the macro recorder, so a template saved
                // afterwards is a transcript of what really worked on this device.
                if (outcome.ok) com.saathi.ai.auto.MacroRecorder.record(action.name, outcome.report.take(90))
                log.appendLine("• ${action.describe()} → ${outcome.report}")
                history.add("${action.name}: ${outcome.report.take(140)}")
                req.spend(1)
                if (outcome.report.startsWith("আটকে")) {
                    cleanup(context, prefs)
                    return "আটকে: ${outcome.report}\n$log"
                }
                sleep(afterAction(action.name))
            }

            // ---- 5. verify against the real screen, not against the model's word
            val nowText = svc.dumpScreen(2600)
            lastText = nowText
            if (proof.isNotEmpty() && proof.any { nowText.contains(it, ignoreCase = true) }) {
                cleanup(context, prefs)
                return "সফল: কাজ হয়েছে — স্ক্রিনে প্রমাণ পাওয়া গেছে (${proof.first { nowText.contains(it, ignoreCase = true) }})\n$log"
            }
            if (!usedApi) break
        }

        cleanup(context, prefs)
        val verdict = when {
            rounds >= minOf(req.budgetSteps, MAX_ROUNDS_HARD) ->
                "আংশিক: ধাপের সীমা ($rounds রাউন্ড) শেষ — যতটুকু এগিয়েছে তার রিপোর্ট নিচে। আরও ধাপ দিলে শেষ করতে পারি।"
            System.currentTimeMillis() >= req.deadlineAt ->
                "আংশিক: সময় শেষ — $rounds রাউন্ড চলেছে।"
            else -> "ব্যর্থ: $rounds রাউন্ড চেষ্টা করেও লক্ষ্যের প্রমাণ স্ক্রিনে পাওয়া যায়নি।"
        }
        return "$verdict\nশেষ স্ক্রিনের লেখা (অংশ): ${lastText.take(500)}\n$log"
    }

    /**
     * The v2 path, still fully functional: accessibility tree, autopilot, offline
     * packs and the escalation ladder. Used whenever the computer-use model is
     * unavailable — and the report says which path actually ran.
     */
    fun fallback(
        ctx: AgentContext,
        req: AgentRequest,
        svc: JimiAccessibilityService,
        onStep: (String) -> Unit,
        reason: String
    ): String {
        onStep("বিকল্প পথ: অ্যাক্সেসিবিলিটি ট্রি + এসকেলেশন ল্যাডার")
        ErrorCenter.warn(TAG, "computer_use এড়িয়ে বিকল্প পথ ব্যবহার হয়েছে", reason)
        val report = runCatching {
            Solver.runWithEscalation(ctx.context, svc, req.goal, onStep)
        }.getOrElse { "ব্যর্থ: বিকল্প পথেও কাজ হয়নি (${it.message})" }
        req.spend(2)
        cleanup(ctx.context, SecurePrefs(ctx.context))
        val head = when (Solver.classify(report)) {
            Solver.Verdict.DONE -> "সফল"
            Solver.Verdict.PARTIAL -> "আংশিক"
            Solver.Verdict.BLOCKED -> "আটকে"
            Solver.Verdict.FAILED -> "ব্যর্থ"
        }
        return "$head: (computer_use মডেল পাওয়া যায়নি — $reason; অ্যাক্সেসিবিলিটি পথে চেষ্টা করা হয়েছে)\n" +
            report.take(1800)
    }

    /** Confirms a claim against the live screen. */
    private fun verify(svc: JimiAccessibilityService, proof: List<String>, goal: String): Boolean {
        if (proof.isEmpty()) return false
        val text = svc.dumpScreen(2600)
        return proof.any { text.contains(it, ignoreCase = true) } ||
            text.contains("সম্পন্ন", ignoreCase = true) && goal.length < 40
    }

    /** Deletes temporary screenshots unless the user asked to keep them. */
    private fun cleanup(context: Context, prefs: SecurePrefs) {
        if (!prefs.keepScreenshots) {
            runCatching { ScreenVision.deleteAllShots(context) }
        }
    }

    /** Longer waits after actions that change the screen substantially. */
    private fun afterAction(name: String): Long = when (name) {
        "open_app", "go_home" -> 1600L
        "click", "long_press" -> 850L
        "scroll" -> 620L
        "type" -> 500L
        "wait" -> 200L
        else -> 600L
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (t: Throwable) {
            ErrorCenter.log(TAG, "sleep বাধা পেয়েছে", t.message)
        }
    }

    /**
     * Fills a form field the model pointed at, using the restricted-field ladder
     * and never touching password/OTP fields.
     */
    fun fill(context: Context, label: String, text: String): String {
        val svc = JimiAccessibilityService.instance
        val out = ActionMapper.fillField(context, svc, label, text)
        if (!out.ok) ErrorCenter.log(TAG, "ফিল্ড ভরা যায়নি: $label", out.report)
        return out.report
    }

    /** What the model/user should know about this subsystem right now. */
    fun status(context: Context): String = buildString {
        appendLine("ভিজ্যুয়াল এজেন্ট অবস্থা:")
        appendLine("• অ্যাক্সেসিবিলিটি: ${if (JimiAccessibilityService.isEnabled()) "চালু" else "বন্ধ"}")
        appendLine("• স্ক্রিনশট উৎস: ${ScreenVision.lastSource}")
        appendLine("• স্ক্রিনশট রাখা হবে: ${if (SecurePrefs(context).keepScreenshots) "হ্যাঁ (ব্যবহারকারী চেয়েছেন)" else "না — দেখা শেষেই মুছে যায়"}")
        appendLine(ComputerUseClient(context).status())
        append("• সংবেদনশীল স্ক্রিন নীতি: পাসওয়ার্ড/OTP/চূড়ান্ত পেমেন্ট কখনো অটো নয়")
    }

    /** Approval-gated version for the model: sensitive goals pause for the user. */
    fun askIfSensitive(context: Context, goal: String): ApprovalGate.Outcome? {
        val risk = ApprovalGate.classify("visual", goal)
        if (risk < ApprovalGate.Risk.HIGH) return null
        return ApprovalGate.askBlocking(context, "ভিজ্যুয়াল এজেন্ট: $goal", goal)
    }
}
