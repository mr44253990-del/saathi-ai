package com.saathi.ai.task

import android.content.Context
import com.saathi.ai.agent.AgentStatus
import com.saathi.ai.agent.Orchestrator
import com.saathi.ai.agent.ResourceLocks
import com.saathi.ai.bubble.BubbleService
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.data.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Background task execution engine (Phase 4 + Phase 16).
 *
 * What it adds over the old single-shot worker:
 *  - up to [MAX_CONCURRENT] tasks at once, with [ResourceLocks] making sure two
 *    tasks never drive the screen, shell or audio at the same time;
 *  - priority (1 low … 5 urgent) and a queue, so an urgent task overtakes idle ones;
 *  - pause / resume / cancel that actually work mid-run (cooperative flags checked
 *    between steps);
 *  - per-run history, error detail, artifacts and estimated time;
 *  - persistence + recovery: after the app or the phone dies, unfinished runs come
 *    back as QUEUED instead of vanishing.
 *
 * Honesty rule: a run only becomes DONE when the agent reported `সফল:` — partial
 * results stay PARTIAL/FAILED in the dashboard, never silently promoted.
 */
object TaskEngine {

    enum class RunState { QUEUED, RUNNING, PAUSED, DONE, PARTIAL, FAILED, CANCELLED, BLOCKED }

    data class Run(
        val id: String,
        val goal: String,
        val priority: Int,
        var state: RunState,
        val agentId: String,
        val createdAt: Long,
        var startedAt: Long,
        var updatedAt: Long,
        val deadlineAt: Long,
        val budgetSteps: Int,
        var stepsDone: Int,
        var stepsTotal: Int,
        var lastError: String,
        var report: String,
        val history: MutableList<String> = mutableListOf(),
        val artifacts: MutableList<String> = mutableListOf()
    ) {
        fun progress(): Int = if (stepsTotal <= 0) 0 else (stepsDone * 100 / stepsTotal).coerceIn(0, 100)

        fun stateBangla(): String = when (state) {
            RunState.QUEUED -> "লাইনে আছে"
            RunState.RUNNING -> "চলছে"
            RunState.PAUSED -> "থেমে আছে"
            RunState.DONE -> "সম্পন্ন"
            RunState.PARTIAL -> "আংশিক"
            RunState.FAILED -> "ব্যর্থ"
            RunState.CANCELLED -> "বাতিল"
            RunState.BLOCKED -> "আটকে"
        }

        fun remainingSeconds(): Int =
            if (deadlineAt <= 0) -1 else ((deadlineAt - System.currentTimeMillis()) / 1000).toInt()

        fun line(): String = "${stateBangla()} · অগ্রগতি ${progress()}% · $goal".take(160)
    }

    const val MAX_CONCURRENT = 5
    private const val MAX_STORED = 60
    private const val TAG = "task"
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _runs = MutableStateFlow<List<Run>>(emptyList())
    /** Task dashboard and bubble observe this. */
    val runs: StateFlow<List<Run>> = _runs.asStateFlow()

    private val byId = ConcurrentHashMap<String, Run>()
    private val paused = ConcurrentHashMap.newKeySet<String>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    private val pool = Executors.newFixedThreadPool(MAX_CONCURRENT) { r ->
        Thread(r, "jimi-task").apply { isDaemon = true }
    }

    @Volatile var appContext: Context? = null
        private set

    private fun ctx(): Context? = appContext

    /** Call once from the service/activity that owns the engine. */
    fun init(context: Context) {
        appContext = context.applicationContext
        recover(context)
    }

    // ------------------------------------------------------------------- start

    fun start(
        context: Context,
        goal: String,
        priority: Int = 3,
        deadlineSeconds: Int = 0,
        budgetSteps: Int = 14
    ): String {
        init(context)
        if (goal.isBlank()) return "কাজের বর্ণনা খালি — কী করতে হবে সেটা বলো"
        val running = _runs.value.count { it.state == RunState.RUNNING || it.state == RunState.QUEUED }
        if (running >= MAX_CONCURRENT + 3) {
            return "এখন $running টি কাজ লাইনে আছে (সর্বোচ্চ ${MAX_CONCURRENT}টি একসাথে চলে) — কিছু শেষ হলে আবার বলো"
        }
        val orchestrator = Orchestrator.get(context)
        val agent = orchestrator.agentFor(goal)
        val id = "run-${System.currentTimeMillis() / 1000}-${_runs.value.size + 1}"
        val deadline = if (deadlineSeconds > 0) System.currentTimeMillis() + deadlineSeconds * 1000L
        else System.currentTimeMillis() + 30 * 60_000L
        val run = Run(
            id, goal.trim(), priority.coerceIn(1, 5), RunState.QUEUED,
            agent?.id ?: "auto", System.currentTimeMillis(), 0L, System.currentTimeMillis(),
            deadline, budgetSteps.coerceIn(2, 60), 0, 0, "", "",
            mutableListOf("${fmt.format(Date())} লাইনে যোগ হয়েছে (অগ্রাধিকার $priority)"),
            mutableListOf()
        )
        byId[id] = run
        publish()
        persist(context)
        schedule()
        return "কাজ লাইনে যোগ হয়েছে: ${run.goal.take(80)}\n" +
            "আইডি: $id · এজেন্ট: ${agent?.nameBangla ?: "অটো"} · অগ্রাধিকার: ${run.priority} · " +
            "একসাথে চলতে পারে: $MAX_CONCURRENTটি\n" +
            "বলো «থামাও $id» / «পজ করো» / «অগ্রগতি দেখাও» যখন খুশি।"
    }

    /**
     * Timer-based execution (Phase 4): the goal is queued after a delay instead of
     * immediately. A daemon thread sleeps and then calls [start], so nothing is held
     * on the main thread; if the process is killed first, the task is simply not
     * started — the report tells the user a timer was set, never that it ran.
     */
    fun startAfter(context: Context, goal: String, delaySeconds: Int, priority: Int = 3): String {
        init(context)
        if (goal.isBlank()) return "ব্যর্থ: কাজের বর্ণনা খালি"
        if (delaySeconds <= 0) return start(context, goal, priority)
        val app = context.applicationContext
        val mins = delaySeconds / 60
        kotlin.concurrent.thread(name = "jimi-task-timer", isDaemon = true) {
            try {
                var waited = 0
                while (waited < delaySeconds) {
                    val slice = minOf(30, delaySeconds - waited)
                    Thread.sleep(slice * 1000L)
                    waited += slice
                    val live = _runs.value
                    if (live.any { it.goal == goal.trim() && it.state == RunState.CANCELLED }) {
                        ErrorCenter.info("task", "টাইমার বাতিল: $goal")
                        return@thread
                    }
                }
                start(app, goal, priority)
            } catch (t: Throwable) {
                ErrorCenter.log("task", "টাইমার টাস্ক চালু করা যায়নি", t.message)
            }
        }
        return "সফল: টাইমার সেট হয়েছে — $mins মিনিট পর «${goal.take(60)}» শুরু হবে\n" +
            "ফোন রিস্টার্ট হলে বা অ্যাপ মেরে ফেললে টাইমারটি হারিয়ে যাবে; তখন আবার বলো।"
    }

    /** Starts several goals at once — the "৪–৫টি টাস্ক একসাথে" case. */
    fun startMany(context: Context, goals: List<String>, priority: Int = 3, deadlineSeconds: Int = 0): String {
        val wanted = goals.filter { it.isNotBlank() }
        val started = wanted.map { start(context, it, priority, deadlineSeconds) }
        return "${started.size}টি কাজ লাইনে যোগ হয়েছে (একসাথে সর্বোচ্চ $MAX_CONCURRENT টি চলবে):\n" +
            wanted.joinToString("\n") { "• ${it.take(90)}" }
    }

    // ------------------------------------------------------- pause/resume/etc

    fun pause(id: String): String {
        val run = byId[id] ?: return "এই আইডিতে কোনো কাজ নেই: $id"
        return when (run.state) {
            RunState.RUNNING -> {
                paused.add(id)
                run.state = RunState.PAUSED
                run.updatedAt = System.currentTimeMillis()
                run.history.add("${fmt.format(Date())} পজ করা হয়েছে")
                publish(); persistSafe()
                "কাজ থামানো হয়েছে (পজ): ${run.goal.take(60)} — রিজিউম বললে ওই ধাপ থেকেই চলবে"
            }
            RunState.QUEUED -> {
                paused.add(id)
                run.state = RunState.PAUSED
                publish(); persistSafe()
                "লাইনে থাকা কাজ পজ করা হয়েছে: ${run.goal.take(60)}"
            }
            else -> "কাজটি এখন ${run.stateBangla()} — পজ করা যাবে না"
        }
    }

    fun resume(context: Context, id: String): String {
        init(context)
        val run = byId[id] ?: return "এই আইডিতে কোনো কাজ নেই: $id"
        if (run.state != RunState.PAUSED) return "কাজটি ${run.stateBangla()} — রিজিউমের দরকার নেই"
        paused.remove(id)
        run.state = RunState.QUEUED
        run.updatedAt = System.currentTimeMillis()
        run.history.add("${fmt.format(Date())} রিজিউম")
        publish(); persistSafe(); schedule()
        return "কাজ আবার চলছে: ${run.goal.take(60)}"
    }

    fun cancel(id: String): String {
        val run = byId[id] ?: return "এই আইডিতে কোনো কাজ নেই: $id"
        cancelled.add(id)
        paused.remove(id)
        if (run.state == RunState.DONE || run.state == RunState.CANCELLED) {
            return "কাজটি আগেই ${run.stateBangla()} — বাতিল করার কিছু নেই"
        }
        run.state = RunState.CANCELLED
        run.updatedAt = System.currentTimeMillis()
        run.history.add("${fmt.format(Date())} ব্যবহারকারী বাতিল করেছেন")
        ResourceLocks.release(id, ResourceLocks.heldBy(id))
        publish(); persistSafe()
        ErrorCenter.warn(TAG, "টাস্ক বাতিল: ${run.goal.take(60)}", id)
        return "কাজ বাতিল করা হয়েছে: ${run.goal.take(60)}"
    }

    /** Cancels everything at once — used by the KillSwitch. */
    fun cancelAll(): String {
        val active = _runs.value.filter { it.state == RunState.RUNNING || it.state == RunState.QUEUED || it.state == RunState.PAUSED }
        active.forEach { cancel(it.id) }
        return if (active.isEmpty()) "চলমান কোনো কাজ ছিল না" else "${active.size}টি কাজ বাতিল করা হয়েছে"
    }

    fun retry(context: Context, id: String): String {
        val run = byId[id] ?: return "এই আইডিতে কোনো কাজ নেই: $id"
        if (run.state == RunState.RUNNING) return "কাজটি এখনো চলছে — রিট্রাইয়ের দরকার নেই"
        cancelled.remove(id); paused.remove(id)
        ErrorCenter.resetRetry("run.$id")
        return start(context, run.goal, run.priority, run.remainingSeconds().coerceAtLeast(0), run.budgetSteps)
    }

    // --------------------------------------------------------------- execution

    private fun schedule() {
        val context = ctx() ?: return
        val free = MAX_CONCURRENT - _runs.value.count { it.state == RunState.RUNNING }
        if (free <= 0) return
        val queue = _runs.value
            .filter { it.state == RunState.QUEUED && !paused.contains(it.id) && !cancelled.contains(it.id) }
            .sortedWith(compareByDescending<Run> { it.priority }.thenBy { it.createdAt })
            .take(free)
        queue.forEach { run -> launch(context, run) }
    }

    private fun launch(context: Context, run: Run) {
        run.state = RunState.RUNNING
        run.startedAt = System.currentTimeMillis()
        run.updatedAt = run.startedAt
        run.history.add("${fmt.format(Date())} শুরু (এজেন্ট: ${run.agentId})")
        publish(); persistSafe()
        BubbleService.task(context, run.goal.take(48), 0)

        pool.submit {
            val orchestrator = Orchestrator.get(context)
            val result = try {
                orchestrator.runPlan(
                    goal = run.goal,
                    budgetSteps = run.budgetSteps,
                    deadlineAt = run.deadlineAt,
                    runId = run.id,
                    onStep = { step ->
                        run.updatedAt = System.currentTimeMillis()
                        run.history.add("${fmt.format(Date())} $step")
                        if (run.history.size > 120) run.history.removeAt(0)
                        run.lastError = if (step.contains("ব্যর্থ") || step.contains("আটকে")) step else run.lastError
                        BubbleService.task(context, step.take(48), run.progress())
                        publish()
                    },
                    onProgress = { done, total ->
                        run.stepsDone = done
                        run.stepsTotal = total
                        publish(); persistSafe()
                    },
                    paused = { paused.contains(run.id) },
                    cancelled = { cancelled.contains(run.id) }
                )
            } catch (t: Throwable) {
                ErrorCenter.logThrow(TAG, "টাস্ক ক্র্যাশ: ${run.goal.take(40)}", t)
                com.saathi.ai.agent.AgentResult.failed("ব্যর্থ: টাস্ক থেমে গেছে (${t.message})")
            }

            run.report = result.report
            run.stepsTotal = if (run.stepsTotal == 0) 1 else run.stepsTotal
            run.stepsDone = when (result.status) {
                AgentStatus.DONE -> run.stepsTotal
                AgentStatus.PARTIAL -> run.stepsDone
                else -> run.stepsDone
            }
            run.state = when (result.status) {
                AgentStatus.DONE -> RunState.DONE
                AgentStatus.PARTIAL -> RunState.PARTIAL
                AgentStatus.BLOCKED -> RunState.BLOCKED
                AgentStatus.FAILED -> RunState.FAILED
            }
            if (cancelled.contains(run.id)) run.state = RunState.CANCELLED
            run.updatedAt = System.currentTimeMillis()
            run.lastError = if (result.status == AgentStatus.FAILED || result.status == AgentStatus.BLOCKED) {
                result.errors.firstOrNull() ?: result.report.lineSequence()
                    .firstOrNull { it.contains("ব্যর্থ") || it.contains("আটকে") } ?: ""
            } else ""
            run.artifacts.addAll(result.artifacts)
            run.history.add("${fmt.format(Date())} ${run.stateBangla()} — ${result.status.token()}")
            ResourceLocks.release(run.id, ResourceLocks.heldBy(run.id))
            publish(); persistSafe()

            // Keep the user informed even when the app is closed.
            BubbleService.task(context, "${run.stateBangla()}: ${run.goal.take(36)}", run.progress())
            if (result.status != AgentStatus.DONE) {
                ErrorCenter.log(TAG, "টাস্ক ${run.stateBangla()}: ${run.goal.take(60)}", run.lastError)
            } else ErrorCenter.info(TAG, "টাস্ক সম্পন্ন: ${run.goal.take(60)}")
            schedule()
        }
    }

    // ------------------------------------------------------------------ output

    fun run(id: String): Run? = byId[id]

    fun active(): List<Run> = _runs.value.filter {
        it.state == RunState.RUNNING || it.state == RunState.QUEUED || it.state == RunState.PAUSED
    }

    fun statusText(): String {
        val all = _runs.value
        if (all.isEmpty()) return "টাস্ক ইঞ্জিন খালি — কোনো কাজ লাইনে নেই"
        val running = all.count { it.state == RunState.RUNNING }
        return buildString {
            appendLine("চলছে $running টি · লাইনে ${all.count { it.state == RunState.QUEUED }} · " +
                "পজ ${all.count { it.state == RunState.PAUSED }} · সম্পন্ন ${all.count { it.state == RunState.DONE }} · " +
                "ব্যর্থ ${all.count { it.state == RunState.FAILED }}")
            appendLine("রিসোর্স লক: ${ResourceLocks.status()}")
            all.take(12).forEach { appendLine("• [${it.id}] ${it.line()}") }
        }
    }

    /** Full detail for one task — the dashboard's expanded view. */
    fun report(id: String): String {
        val run = byId[id] ?: return "এই আইডিতে কোনো কাজ নেই: $id"
        return buildString {
            appendLine("কাজ: ${run.goal}")
            appendLine("আইডি: ${run.id} · এজেন্ট: ${run.agentId} · অগ্রাধিকার: ${run.priority}")
            appendLine("অবস্থা: ${run.stateBangla()} · অগ্রগতি: ${run.progress()}% (${run.stepsDone}/${run.stepsTotal} ধাপ)")
            appendLine("শুরু: ${if (run.startedAt == 0L) "এখনো নয়" else fmt.format(Date(run.startedAt))} · " +
                "সর্বশেষ হালনাগাদ: ${fmt.format(Date(run.updatedAt))}")
            val rem = run.remainingSeconds()
            appendLine("বাকি সময়: ${if (rem < 0) "সীমা নেই" else "$rem সেকেন্ড"}")
            if (run.lastError.isNotBlank()) appendLine("সমস্যা: ${run.lastError.take(300)}")
            if (run.artifacts.isNotEmpty()) appendLine("ফলাফল ফাইল: ${run.artifacts.joinToString(", ")}")
            appendLine("ইতিহাস:")
            run.history.takeLast(40).forEach { appendLine("  • $it") }
            if (run.report.isNotBlank()) {
                appendLine("রিপোর্ট:")
                appendLine(run.report.take(2000))
            }
        }
    }

    /** Short spoken update — what the voice reply uses. */
    fun headline(): String {
        val active = active()
        return when {
            active.isEmpty() -> {
                val done = _runs.value.count { it.state == RunState.DONE }
                if (done > 0) "চলমান কাজ নেই; শেষ হয়েছে $done টি" else "এখন কোনো ব্যাকগ্রাউন্ড কাজ চলছে না"
            }
            else -> {
                val r = active.first()
                "${active.size}টি কাজ চলছে; প্রধান কাজ: ${r.goal.take(50)} — ${r.progress()}% (${r.stateBangla()})"
            }
        }
    }

    private fun publish() {
        _runs.value = byId.values.toList()
            .sortedWith(
                compareBy<Run> {
                    when (it.state) {
                        RunState.RUNNING -> 0
                        RunState.QUEUED -> 1
                        RunState.PAUSED -> 2
                        RunState.BLOCKED -> 3
                        RunState.PARTIAL -> 4
                        RunState.FAILED -> 5
                        RunState.DONE -> 6
                        RunState.CANCELLED -> 7
                    }
                }.thenByDescending { it.priority }.thenByDescending { it.updatedAt }
            )
            .take(MAX_STORED)
    }

    // ------------------------------------------------------------ persistence

    private fun persistSafe() {
        val c = ctx() ?: return
        try {
            persist(c)
        } catch (_: Throwable) {
        }
    }

    /** Saves runs so a reboot or a killed process does not lose progress. */
    fun persist(context: Context) {
        try {
            val arr = JSONArray()
            _runs.value.take(MAX_STORED).forEach { r ->
                arr.put(
                    JSONObject()
                        .put("id", r.id).put("goal", r.goal).put("priority", r.priority)
                        .put("state", r.state.name).put("agentId", r.agentId)
                        .put("createdAt", r.createdAt).put("startedAt", r.startedAt)
                        .put("updatedAt", r.updatedAt).put("deadlineAt", r.deadlineAt)
                        .put("budgetSteps", r.budgetSteps).put("stepsDone", r.stepsDone)
                        .put("stepsTotal", r.stepsTotal).put("lastError", r.lastError)
                        .put("report", r.report.take(4000))
                        .put("history", JSONArray(r.history.take(60)))
                        .put("artifacts", JSONArray(r.artifacts.take(20)))
                )
            }
            File(context.filesDir, "jimi-task-engine.json").writeText(arr.toString())
        } catch (t: Throwable) {
            ErrorCenter.log(TAG, "টাস্ক ইঞ্জিন সংরক্ষণ ব্যর্থ", t.message)
        }
    }

    /**
     * Reloads after a restart. Runs that were mid-flight become QUEUED (they were
     * interrupted, so re-running is the only honest option) — unless the user has
     * background work switched off, in which case they stay paused and say so.
     */
    fun recover(context: Context): String {
        appContext = context.applicationContext
        if (byId.isNotEmpty()) return "টাস্ক ইঞ্জিন আগে থেকেই চালু"
        val f = File(context.filesDir, "jimi-task-engine.json")
        if (!f.exists()) return "সংরক্ষিত কোনো টাস্ক নেই"
        var resumed = 0
        var parked = 0
        try {
            val arr = JSONArray(f.readText())
            val background = SecurePrefs(context).backgroundTasks
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val state = runCatching { RunState.valueOf(o.optString("state", "QUEUED")) }
                    .getOrDefault(RunState.QUEUED)
                val effective = when (state) {
                    RunState.RUNNING, RunState.QUEUED -> if (background) RunState.QUEUED else RunState.PAUSED
                    else -> state
                }
                val run = Run(
                    o.optString("id"), o.optString("goal"), o.optInt("priority", 3), effective,
                    o.optString("agentId", "auto"), o.optLong("createdAt"), o.optLong("startedAt"),
                    System.currentTimeMillis(), o.optLong("deadlineAt"),
                    o.optInt("budgetSteps", 14), o.optInt("stepsDone"), o.optInt("stepsTotal"),
                    o.optString("lastError"), o.optString("report"),
                    (o.optJSONArray("history") ?: JSONArray()).let { h ->
                        (0 until h.length()).map { h.optString(it) }.toMutableList()
                    },
                    (o.optJSONArray("artifacts") ?: JSONArray()).let { a ->
                        (0 until a.length()).map { a.optString(it) }.toMutableList()
                    }
                )
                run.history.add("${fmt.format(Date())} রিস্টার্টের পরে ফিরে এসেছে (${run.stateBangla()})")
                byId[run.id] = run
                if (run.state == RunState.QUEUED) resumed++ else if (run.state == RunState.PAUSED) parked++
            }
            publish()
            if (resumed > 0) schedule()
        } catch (t: Throwable) {
            ErrorCenter.log(TAG, "টাস্ক রিকভারি ব্যর্থ", t.message)
            return "সংরক্ষিত টাস্ক পড়া যায়নি (${t.message})"
        }
        return when {
            resumed > 0 -> "রিস্টার্টের পরে $resumed টি অসম্পূর্ণ কাজ আবার লাইনে নেওয়া হয়েছে"
            parked > 0 -> "$parked টি অসম্পূর্ণ কাজ পজ অবস্থায় রাখা হয়েছে (ব্যাকগ্রাউন্ড কাজ বন্ধ আছে)"
            else -> "সংরক্ষিত কোনো অসম্পূর্ণ কাজ নেই"
        }
    }

    fun clearFinished(): String {
        val gone = byId.values.filter {
            it.state == RunState.DONE || it.state == RunState.CANCELLED || it.state == RunState.FAILED
        }
        gone.forEach { byId.remove(it.id) }
        publish(); persistSafe()
        return if (gone.isEmpty()) "সরানোর মতো শেষ হওয়া কাজ নেই" else "${gone.size}টি শেষ হওয়া কাজের রেকর্ড সরানো হয়েছে"
    }
}
