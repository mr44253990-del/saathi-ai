package com.saathi.ai.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import com.saathi.ai.bubble.BubbleService
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Human-in-the-loop gate for sensitive actions (Phase 19).
 *
 * Jimi can reach deep into the phone, so some steps must never happen on the
 * model's word alone. This gate classifies an action, and when the risk is high
 * it *blocks* until the user answers — through the bubble, a notification line or
 * the in-app dialog that observes [pending].
 *
 * Guarantees:
 *  - No answer within the timeout ⇒ **denied**, never approved. Silence is a no.
 *  - `Risk.BLOCKED` actions are refused outright (final payment confirmation,
 *    reading/typing OTP codes, changing account passwords). Jimi prepares them
 *    and hands the last tap to the user.
 *  - Every decision is logged, so "who authorised this" is always answerable.
 */
object ApprovalGate {

    enum class Risk { LOW, MEDIUM, HIGH, BLOCKED }
    enum class Decision { APPROVED, DENIED, TIMEOUT, REFUSED_BY_POLICY }

    data class Request(
        val id: Long,
        val action: String,
        val detail: String,
        val risk: Risk,
        val createdAt: Long,
        val expiresAt: Long,
        val bangla: String
    )

    data class Outcome(val decision: Decision, val note: String) {
        val allowed: Boolean get() = decision == Decision.APPROVED
    }

    data class Record(val at: Long, val action: String, val risk: Risk, val decision: Decision)

    private const val DEFAULT_TIMEOUT_MS = 120_000L
    private const val MAX_HISTORY = 60
    private val seq = AtomicLong(1)

    private val _pending = MutableStateFlow<Request?>(null)
    /** The UI (and the bubble) observe this to draw the approve/deny prompt. */
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    private val _history = MutableStateFlow<List<Record>>(emptyList())
    val history: StateFlow<List<Record>> = _history.asStateFlow()

    private var waiter: ((Decision) -> Unit)? = null
    @Volatile private var currentId: Long = -1

    // --------------------------------------------------------------- classify

    private val blockedWords = listOf(
        "otp", "one time password", "ওয়ান টাইম", "পাসওয়ার্ড বদল", "change password",
        "reset password", "পেমেন্ট কনফার্ম", "final payment", "confirm payment", "pin কোড",
        "cvv", "card number", "বায়োমেট্রিক রিসেট", "factory reset"
    )

    private val highWords = listOf(
        "payment", "pay", "bkash", "nagad", "rocket", "upi", "transfer", "টাকা পাঠা",
        "লেনদেন", "delete", "মুছে", "uninstall", "format", "pm clear", "wipe",
        "send sms", "sms পাঠা", "message পাঠা", "install apk", "grant", "revoke",
        "settings put", "default assistant", "root", "su ", "disable-user", "suspend"
    )

    private val mediumWords = listOf(
        "post", "publish", "upload", "শেয়ার", "share", "call", "কল", "email", "মেইল",
        "subscribe", "order", "booking", "location share", "clipboard", "settings"
    )

    /** Risk of an action, judged from the tool name and its human-readable detail. */
    fun classify(action: String, detail: String = ""): Risk {
        val hay = "$action $detail".lowercase()
        return when {
            blockedWords.any { hay.contains(it) } -> Risk.BLOCKED
            highWords.any { hay.contains(it) } -> Risk.HIGH
            mediumWords.any { hay.contains(it) } -> Risk.MEDIUM
            else -> Risk.LOW
        }
    }

    fun needsApproval(action: String, detail: String = ""): Boolean =
        classify(action, detail) >= Risk.HIGH

    fun riskName(r: Risk): String = when (r) {
        Risk.LOW -> "সাধারণ"
        Risk.MEDIUM -> "সতর্কতা"
        Risk.HIGH -> "উচ্চ ঝুঁকি"
        Risk.BLOCKED -> "নিষিদ্ধ"
    }

    // ------------------------------------------------------------------- gate

    /**
     * Suspends until the user answers or the timeout passes.
     * LOW/MEDIUM pass through immediately (with a log line for MEDIUM).
     */
    suspend fun ask(
        context: Context,
        action: String,
        detail: String = "",
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Outcome {
        val risk = classify(action, detail)
        when (risk) {
            Risk.LOW -> return Outcome(Decision.APPROVED, "সাধারণ কাজ — অনুমোদন লাগে না")
            Risk.MEDIUM -> {
                ErrorCenter.info("permission", "সতর্কতাস্তরের কাজ: $action", detail)
                return Outcome(Decision.APPROVED, "সতর্কতাস্তরের কাজ — লগ রাখা হয়েছে")
            }
            Risk.BLOCKED -> {
                record(action, risk, Decision.REFUSED_BY_POLICY)
                ErrorCenter.log(
                    "permission", "নিষিদ্ধ কাজ প্রত্যাখ্যান: $action", detail, ErrorCenter.Severity.WARNING
                )
                return Outcome(
                    Decision.REFUSED_BY_POLICY,
                    "এই ধাপটি Jimi কখনো নিজে করে না ($detail) — আমি সব প্রস্তুত করে দিচ্ছি, শেষ ট্যাপটা তুমি দাও"
                )
            }
            Risk.HIGH -> Unit
        }

        val req = Request(
            id = seq.getAndIncrement(),
            action = action,
            detail = detail,
            risk = risk,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + timeoutMs,
            bangla = promptText(action, detail)
        )
        currentId = req.id
        _pending.value = req
        ErrorCenter.warn("permission", "অনুমোদনের অপেক্ষা: $action", detail)

        // Tell the user out loud / on the bubble — the app may be in background.
        runCatching { BubbleService.caption(context, req.bangla) }
        runCatching { BubbleService.autoShow(context, req.bangla) }

        val decision = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                waiter = { d -> if (cont.isActive) cont.resume(d) }
                cont.invokeOnCancellation { waiter = null }
            }
        } ?: Decision.TIMEOUT

        waiter = null
        _pending.value = null
        currentId = -1
        record(action, risk, decision)

        val note = when (decision) {
            Decision.APPROVED -> "ব্যবহারকারী অনুমোদন দিয়েছেন"
            Decision.DENIED -> "ব্যবহারকারী না করেছেন"
            Decision.TIMEOUT -> "নির্ধারিত সময়ে কোনো সাড়া আসেনি — নিরাপত্তার জন্য বাতিল"
            Decision.REFUSED_BY_POLICY -> "নীতিমালা অনুযায়ী নিষিদ্ধ"
            else -> "কারণ জানা যায়নি"
        }
        ErrorCenter.log(
            "permission",
            "অনুমোদনের ফল: ${if (decision == Decision.APPROVED) "হ্যাঁ" else "না"}",
            "$action — $note",
            if (decision == Decision.APPROVED) ErrorCenter.Severity.INFO else ErrorCenter.Severity.WARNING
        )
        return Outcome(decision, note)
    }

    /**
     * Blocking variant for non-suspend callers (the agent engine runs on its own
     * worker threads, never on the main thread, so waiting here is safe).
     */
    fun askBlocking(
        context: Context,
        action: String,
        detail: String = "",
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Outcome = kotlinx.coroutines.runBlocking { ask(context, action, detail, timeoutMs) }

    /** Called from the dialog, the bubble button or a notification action. */
    fun decide(id: Long, approved: Boolean) {
        if (id != currentId) return
        val w = waiter ?: return
        waiter = null
        w(if (approved) Decision.APPROVED else Decision.DENIED)
    }

    /** Convenience for single-pending UIs. */
    fun decideCurrent(approved: Boolean) {
        val r = _pending.value ?: return
        decide(r.id, approved)
    }

    fun cancelCurrent(reason: String = "বাতিল") {
        val r = _pending.value ?: return
        _pending.value = null
        val w = waiter
        waiter = null
        w?.invoke(Decision.DENIED)
        record(r.action, r.risk, Decision.DENIED)
        ErrorCenter.warn("permission", "অনুমোদন বাতিল: ${r.action}", reason)
    }

    private fun record(action: String, risk: Risk, decision: Decision) {
        val list = _history.value.toMutableList()
        list.add(0, Record(System.currentTimeMillis(), action, risk, decision))
        _history.value = list.take(MAX_HISTORY)
    }

    private fun promptText(action: String, detail: String): String = buildString {
        append("অনুমোদন দরকার: ")
        append(action)
        if (detail.isNotBlank()) append(" — ").append(detail.take(160))
        append(" (২ মিনিটের মধ্যে হ্যাঁ/না না বললে কাজটি বাতিল হবে)")
    }

    fun historyText(limit: Int = 15): String {
        val h = _history.value.take(limit)
        if (h.isEmpty()) return "এখনো কোনো অনুমোদনের ইতিহাস নেই"
        return buildString {
            appendLine("অনুমোদনের ইতিহাস (সর্বশেষ ${h.size}টি):")
            h.forEach {
                val mark = when (it.decision) {
                    Decision.APPROVED -> "[হ্যাঁ]"
                    Decision.DENIED -> "[না]"
                    Decision.TIMEOUT -> "[সময় শেষ]"
                    Decision.REFUSED_BY_POLICY -> "[নীতিতে নিষিদ্ধ]"
                    else -> "[অজানা]"
                }
                appendLine("• $mark ${it.action} (${riskName(it.risk)})")
            }
        }
    }

    /** Everything the dashboard needs on one screen. */
    fun status(): String = buildString {
        val p = _pending.value
        appendLine(if (p != null) "এখন অনুমোদনের অপেক্ষায়: ${p.action}" else "অনুমোদনের অপেক্ষায় কিছু নেই")
        append("মোট সিদ্ধান্ত: ${_history.value.size}টি · ")
        append("হ্যাঁ: ${_history.value.count { it.decision == Decision.APPROVED }} · ")
        append("না: ${_history.value.count { it.decision == Decision.DENIED }} · ")
        append("সময় শেষ: ${_history.value.count { it.decision == Decision.TIMEOUT }} · ")
        append("নীতিতে নিষিদ্ধ: ${_history.value.count { it.decision == Decision.REFUSED_BY_POLICY }}")
    }
}
