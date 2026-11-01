package com.saathi.ai.agent

import android.content.Context
import com.saathi.ai.core.ApprovalGate
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.data.SecurePrefs
import com.saathi.ai.live.ToolExecutor
import com.saathi.ai.task.TaskBoard
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Device-resource arbitration.
 *
 * Four or five tasks may run at once (Phase 4), but only one of them may drive
 * the screen, the shell or the audio output at a time — otherwise two agents
 * would fight over the same finger, the same terminal or the same speaker.
 * Everything else (network research, file work, image work, analysis) genuinely
 * runs in parallel.
 */
object ResourceLocks {
    private val held = ConcurrentHashMap<String, String>()   // resource -> run id

    /** Tries to take every resource a run needs; all-or-nothing. */
    fun acquire(runId: String, resources: Set<String>): Boolean {
        if (resources.isEmpty()) return true
        synchronized(held) {
            val clash = resources.firstOrNull { r -> held[r] != null && held[r] != runId }
            if (clash != null) return false
            resources.forEach { held[it] = runId }
            return true
        }
    }

    fun release(runId: String, resources: Set<String>) {
        synchronized(held) {
            resources.forEach { r -> if (held[r] == runId) held.remove(r) }
        }
    }

    fun holder(resource: String): String? = held[resource]

    fun heldBy(runId: String): Set<String> = held.filterValues { it == runId }.keys

    fun status(): String =
        if (held.isEmpty()) "কোনো ডিভাইস রিসোর্স ব্যস্ত নয়"
        else held.entries.joinToString(", ") { "${it.key} → ${it.value}" }

    fun clear() = held.clear()
}

/**
 * The Orchestrator (Phase 17): routes work to the right specialist, keeps the
 * budget, asks the user before anything sensitive, and calls the recovery agent
 * instead of giving up after one failure.
 *
 * It never promises a result it did not verify — every path ends in one of the
 * four verdict tokens, and the merged report keeps each attempt visible.
 */
class Orchestrator(private val ctx: AgentContext) {

    private val agents: List<Agent> = listOf(
        PlannerAgent(),
        ResearchAgent(),
        BrowserAgent(),
        VisualComputerAgent(),
        FileAgent(),
        TerminalAgent(),
        CodeAgent(),
        MediaAgent(),
        AudioAgent(),
        VideoAgent(),
        RecoveryAgent()
    )

    private val planner = agents.filterIsInstance<PlannerAgent>().first()
    private val recovery = agents.filterIsInstance<RecoveryAgent>().first()

    fun agentList(): List<Agent> = agents

    /** Ranked routing table — exposed so the UI can show *why* a choice was made. */
    fun ranked(goal: String, detail: String = ""): List<Pair<Agent, Int>> =
        agents.map { it to it.score(goal, detail) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

    fun agentFor(goal: String, detail: String = ""): Agent? = ranked(goal, detail).firstOrNull()?.first

    /**
     * Runs one goal end to end: route → (approve if needed) → execute → recover
     * on failure → merge into a single honest report.
     */
    fun run(
        goal: String,
        budgetSteps: Int = 14,
        deadlineAt: Long = System.currentTimeMillis() + 10 * 60_000L,
        runId: String = "",
        detail: String = "",
        onStep: (String) -> Unit = {}
    ): AgentResult {
        val req = AgentRequest(goal, detail, budgetSteps, deadlineAt, runId)
        val ranked = ranked(goal, detail)
        if (ranked.isEmpty()) {
            return AgentResult.blocked(
                "আটকে: এই কাজের জন্য উপযুক্ত এজেন্ট খুঁজে পাইনি — কাজটা আরেকটু স্পষ্ট করে বলো " +
                    "(কোন অ্যাপ/ফাইল/সাইট, কী করতে হবে)"
            )
        }
        val log = StringBuilder()
        var last: AgentResult? = null

        for ((agent, score) in ranked.take(2)) {
            // Sensitive actions stop here and wait for a human.
            val risk = ApprovalGate.classify(agent.id, goal)
            if (risk >= ApprovalGate.Risk.HIGH) {
                onStep("অনুমোদনের অপেক্ষা (${ApprovalGate.riskName(risk)})")
                val outcome = ApprovalGate.askBlocking(ctx.context, "${agent.nameBangla}: $goal", goal)
                if (!outcome.allowed) {
                    log.appendLine("── ${agent.nameBangla} (স্কোর $score)")
                    log.appendLine("বাতিল: ${outcome.note}")
                    last = AgentResult.blocked("আটকে: ${outcome.note}")
                    break
                }
                log.appendLine("অনুমোদন: ${outcome.note}")
            }

            onStep("${agent.nameBangla} কাজ শুরু করছে")
            log.appendLine("── ${agent.nameBangla} (স্কোর $score)")
            val res = try {
                agent.work(req, ctx, onStep)
            } catch (t: Throwable) {
                ErrorCenter.logThrow("agent", "${agent.id} এজেন্ট ক্র্যাশ", t)
                AgentResult.failed("ব্যর্থ: ${agent.nameBangla} থেমে গেছে (${t.message})")
            }
            log.appendLine(res.report.take(1600))
            last = res
            if (res.status == AgentStatus.DONE) break
            if (res.status == AgentStatus.BLOCKED) break
        }

        // One recovery pass — bounded, never a loop.
        if (last != null && last.status == AgentStatus.FAILED &&
            System.currentTimeMillis() < deadlineAt
        ) {
            val rec = AgentRequest(goal, last.report.take(1200), budgetSteps - req.stepsUsed, deadlineAt, runId)
            onStep("রিকভারি এজেন্ট দেখছে")
            log.appendLine("── ${recovery.nameBangla}")
            val r = runCatching { recovery.work(rec, ctx, onStep) }
                .getOrElse { AgentResult.failed("ব্যর্থ: রিকভারি ব্যর্থ (${it.message})") }
            log.appendLine(r.report.take(1200))
            if (r.status == AgentStatus.DONE) last = r
        }

        val merged = last ?: AgentResult.failed("ব্যর্থ: কোনো এজেন্ট কাজ করেনি")
        return merged.copy(report = merged.report + "\n\n" + log.toString().trim().take(2400))
    }

    /**
     * Multi-step run: plan the goal, tick steps off the board as they finish and
     * report the whole thing at the end. This is what the background worker uses.
     */
    fun runPlan(
        goal: String,
        budgetSteps: Int = 22,
        deadlineAt: Long = System.currentTimeMillis() + 30 * 60_000L,
        runId: String = "",
        onStep: (String) -> Unit = {},
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        paused: () -> Boolean = { false },
        cancelled: () -> Boolean = { false }
    ): AgentResult {
        val steps = planner.plan(goal)
        ctx.board.addPlan(steps)
        onStep("পরিকল্পনা: ${steps.size} ধাপ")
        val log = StringBuilder()
        var done = 0
        var failed = 0
        var blocked = 0
        val perStepBudget = (budgetSteps / steps.size).coerceAtLeast(2)

        steps.forEachIndexed { i, step ->
            while (paused() && !cancelled()) {
                onStep("থেমে আছে (পজ) — আবার শুরু করতে রিজিউম চাপো")
                try {
                    Thread.sleep(1200)
                } catch (t: Throwable) {
                    break
                }
            }
            if (cancelled()) {
                log.appendLine("[বাতিল] $step")
                return AgentResult.partial(
                    "আংশিক: কাজটি ব্যবহারকারী বাতিল করেছেন — $done/${steps.size} ধাপ সম্পন্ন হয়েছিল\n$log"
                )
            }
            if (System.currentTimeMillis() >= deadlineAt) {
                log.appendLine("[সময় শেষ] $step")
                return AgentResult.partial(
                    "আংশিক: নির্ধারিত সময় শেষ — $done/${steps.size} ধাপ সম্পন্ন\n$log\n" +
                        "বাকি ধাপ: ${steps.drop(i).joinToString(" · ")}"
                )
            }
            onStep("ধাপ ${i + 1}/${steps.size}: $step")
            onProgress(i, steps.size)
            val res = run(step, perStepBudget, deadlineAt, runId, "মূল লক্ষ্য: $goal", onStep)
            when (res.status) {
                AgentStatus.DONE -> {
                    done++
                    log.appendLine("[ok] $step")
                    ctx.board.update(stepId(i, steps.size), TaskBoard.Status.DONE, res.report.take(120))
                }
                AgentStatus.PARTIAL -> {
                    done++
                    log.appendLine("[আংশিক] $step")
                    ctx.board.update(stepId(i, steps.size), TaskBoard.Status.DONE, "আংশিক: " + res.report.take(100))
                }
                AgentStatus.BLOCKED -> {
                    blocked++
                    log.appendLine("[আটকে] $step — ${res.report.take(140)}")
                    ctx.board.update(stepId(i, steps.size), TaskBoard.Status.BLOCKED, res.report.take(120))
                }
                AgentStatus.FAILED -> {
                    failed++
                    log.appendLine("[ব্যর্থ] $step — ${res.report.take(140)}")
                    ctx.board.update(stepId(i, steps.size), TaskBoard.Status.FAILED, res.report.take(120))
                }
            }
            onProgress(i + 1, steps.size)
        }

        return when {
            failed == 0 && blocked == 0 ->
                AgentResult.done("সফল: $done/${steps.size} ধাপ সম্পন্ন\n$log")
            done > 0 ->
                AgentResult.partial("আংশিক: $done/${steps.size} ধাপ হয়েছে, ব্যর্থ $failed, আটকে $blocked\n$log")
            blocked > 0 ->
                AgentResult.blocked("আটকে: কোনো ধাপই এগোয়নি — অনুমতি/অনুমোদন দরকার\n$log")
            else -> AgentResult.failed("ব্যর্থ: সব ধাপ ব্যর্থ\n$log")
        }
    }

    /** Board ids are the plan order — keeps tick marks aligned with steps. */
    private fun stepId(index: Int, total: Int): String {
        val all = ctx.board.all()
        val recent = all.takeLast(total)
        return recent.getOrNull(index)?.id ?: ""
    }

    /**
     * Runs several goals at once (Phase 4: "৪–৫টি আলাদা টাস্ক"). Resource locks
     * decide what may truly overlap; conflicting runs wait instead of fighting.
     */
    fun parallel(
        goals: List<String>,
        runIds: List<String>,
        budgetSteps: Int = 12,
        deadlineAt: Long = System.currentTimeMillis() + 20 * 60_000L,
        onStep: (String, String) -> Unit = { _, _ -> },
        paused: (String) -> Boolean = { false },
        cancelled: (String) -> Boolean = { false }
    ): List<Pair<String, AgentResult>> {
        val pool = Executors.newFixedThreadPool(minOf(goals.size, 5)) { r ->
            Thread(r, "jimi-agent").apply { isDaemon = true }
        }
        val results = ConcurrentHashMap<String, AgentResult>()
        try {
            goals.forEachIndexed { i, goal ->
                val rid = runIds.getOrNull(i) ?: "p$i"
                pool.submit {
                    val agent = agentFor(goal)
                    val resources = agent?.resources ?: setOf("cpu")
                    // Wait (bounded) for the device resource instead of clashing.
                    var waited = 0
                    while (!ResourceLocks.acquire(rid, resources) && waited < 60_000 && !cancelled(rid)) {
                        waited += 400
                        Thread.sleep(400)
                    }
                    try {
                        while (paused(rid) && !cancelled(rid)) Thread.sleep(1000)
                        val res = if (cancelled(rid)) {
                            AgentResult.partial("আংশিক: বাতিল করা হয়েছে")
                        } else {
                            run(goal, budgetSteps, deadlineAt, rid, "", { s -> onStep(rid, s) })
                        }
                        results[rid] = res
                    } catch (t: Throwable) {
                        ErrorCenter.logThrow("agent", "parallel রান ব্যর্থ $rid", t)
                        results[rid] = AgentResult.failed("ব্যর্থ: ${t.message}")
                    } finally {
                        ResourceLocks.release(rid, resources)
                    }
                }
            }
        } finally {
            pool.shutdown()
            pool.awaitTermination(30, TimeUnit.MINUTES)
        }
        return runIds.mapNotNull { rid -> results[rid]?.let { rid to it } }
    }

    /** Unified answer for several goals — one report, per-goal verdicts inside. */
    fun unified(parts: List<Pair<String, AgentResult>>): String = buildString {
        val ok = parts.count { it.second.status == AgentStatus.DONE }
        appendLine(
            when {
                ok == parts.size -> "সফল: ${parts.size}টি কাজই সম্পন্ন"
                ok > 0 -> "আংশিক: ${parts.size}টির মধ্যে $ok টি সম্পন্ন"
                else -> "ব্যর্থ: কোনো কাজই সম্পন্ন হয়নি"
            }
        )
        parts.forEach { (id, r) ->
            appendLine("── $id → ${r.status.token()}")
            appendLine(r.report.take(700))
        }
    }

    /** What the orchestrator can do right now — used by the model and the UI. */
    fun status(): String = buildString {
        appendLine("এজেন্ট: ${agents.size}টি · ${agents.joinToString(", ") { it.nameBangla }}")
        appendLine("রিসোর্স লক: ${ResourceLocks.status()}")
        appendLine("API কী: ${if (ctx.prefs.isConfigured) "আছে" else "নেই"}")
        append("অ্যাক্সেসিবিলিটি: ${if (ctx.hasService()) "চালু" else "বন্ধ"}")
    }

    companion object {
        /** Shared instance so the engine, the tools and the UI route identically. */
        @Volatile private var shared: Orchestrator? = null

        fun get(context: Context): Orchestrator {
            val app = context.applicationContext
            return shared ?: synchronized(this) {
                shared ?: Orchestrator(
                    AgentContext(
                        app,
                        ToolExecutor(app),
                        SecurePrefs(app),
                        TaskBoard(app)
                    )
                ).also { shared = it }
            }
        }

        fun reset() {
            shared = null
        }
    }
}
