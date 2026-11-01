package com.saathi.ai.agent

import android.content.Context
import com.saathi.ai.access.JimiAccessibilityService
import com.saathi.ai.data.SecurePrefs
import com.saathi.ai.live.ToolExecutor
import com.saathi.ai.task.TaskBoard

/**
 * Multi-agent contracts (Phase 17).
 *
 * One Orchestrator, eleven specialists. Every agent speaks the same three types
 * so their outputs can be merged into a single answer:
 *
 *  - [AgentRequest]  — what to do, with a budget (steps + deadline) attached.
 *  - [AgentResult]   — what happened, with a report that always starts with one
 *                      of the four verdict tokens the rest of the app already
 *                      understands (`সফল:` `আংশিক:` `ব্যর্থ:` `আটকে:`), so
 *                      `Solver.classify` keeps working across the whole system.
 *  - [resources]     — which device resource the agent needs. Two agents that
 *                      both need `screen` can never run at the same time; the
 *                      TaskEngine serialises them and runs the rest in parallel.
 */

/** Verdict of one agent run. Mirrors the app-wide verdict tokens. */
enum class AgentStatus { DONE, PARTIAL, FAILED, BLOCKED }

fun AgentStatus.token(): String = when (this) {
    AgentStatus.DONE -> "সফল"
    AgentStatus.PARTIAL -> "আংশিক"
    AgentStatus.FAILED -> "ব্যর্থ"
    AgentStatus.BLOCKED -> "আটকে"
}

/** Shared handles so agents never construct their own singletons. */
class AgentContext(
    val context: Context,
    val executor: ToolExecutor,
    val prefs: SecurePrefs,
    val board: TaskBoard
) {
    /** May be null: the accessibility service can be off, agents must cope. */
    val service: JimiAccessibilityService?
        get() = JimiAccessibilityService.instance

    /** Convenience: run a registered tool by name. */
    fun tool(name: String, args: Map<String, Any?> = emptyMap()): String {
        val json = org.json.JSONObject()
        args.forEach { (k, v) -> json.put(k, v ?: "") }
        return runCatching { executor.execute(name, json) }
            .getOrElse { "ব্যর্থ: $name চালানো যায়নি (${it.message})" }
    }

    fun hasService(): Boolean = service != null

    /** Budget-aware pause: true while the run is still allowed to continue. */
    fun alive(req: AgentRequest): Boolean =
        req.stepsUsed < req.budgetSteps && System.currentTimeMillis() < req.deadlineAt
}

data class AgentRequest(
    val goal: String,
    val detail: String = "",
    val budgetSteps: Int = 14,
    val deadlineAt: Long = System.currentTimeMillis() + 10 * 60_000L,
    /** Identifier of the owning task run, so logs and artifacts can be traced. */
    val runId: String = "",
    var stepsUsed: Int = 0
) {
    fun spend(n: Int = 1) {
        stepsUsed += n
    }

    fun remainingSeconds(): Int =
        ((deadlineAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
}

data class AgentResult(
    val status: AgentStatus,
    val report: String,
    val artifacts: List<String> = emptyList(),
    val errors: List<String> = emptyList()
) {
    /** Report line that starts with the app-wide verdict token. */
    fun verdictLine(): String = "${status.token()}: ${report.lineSequence().firstOrNull { it.isNotBlank() } ?: ""}"

    companion object {
        fun done(report: String, artifacts: List<String> = emptyList()) =
            AgentResult(AgentStatus.DONE, report, artifacts)

        fun partial(report: String, errors: List<String> = emptyList()) =
            AgentResult(AgentStatus.PARTIAL, report, emptyList(), errors)

        fun failed(report: String, errors: List<String> = emptyList()) =
            AgentResult(AgentStatus.FAILED, report, emptyList(), errors)

        fun blocked(report: String) = AgentResult(AgentStatus.BLOCKED, report)
    }
}

/**
 * Base class for the eleven specialists.
 *
 * [score] decides routing: the Orchestrator asks every agent how well it fits a
 * goal and picks the highest. 0 means "not my job". Keeping the scoring inside
 * the agent (instead of a giant when-block in the orchestrator) is what makes
 * adding a twelfth agent a one-file change.
 */
abstract class Agent {
    abstract val id: String
    abstract val nameBangla: String

    /** Device resources this agent occupies while running. */
    open val resources: Set<String> = setOf("cpu")

    /** 0..100 fit for a goal; the Orchestrator picks the top scorer. */
    open fun score(goal: String, detail: String = ""): Int = 0

    abstract fun work(req: AgentRequest, ctx: AgentContext, onStep: (String) -> Unit): AgentResult

    /** Keyword scoring helper shared by the specialists. */
    protected fun hits(text: String, words: List<String>, weight: Int = 12): Int {
        val low = text.lowercase()
        return words.sumOf { if (low.contains(it.lowercase())) weight else 0 }
    }

    /** Guard: never keep working past the budget or the deadline. */
    protected fun outOfBudget(req: AgentRequest): String? = when {
        req.stepsUsed >= req.budgetSteps ->
            "আটকে: ধাপের বাজেট (${req.budgetSteps}) শেষ — আর চেষ্টা করিনি, সময়/ধাপ বাড়িয়ে আবার বলো"
        System.currentTimeMillis() >= req.deadlineAt ->
            "আটকে: নির্ধারিত সময় (${req.remainingSeconds() + 1} সে. আগে শেষ) শেষ — যতটুকু হয়েছে তার রিপোর্ট ওপরে"
        else -> null
    }
}
