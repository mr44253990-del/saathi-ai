package com.saathi.ai.vision

import android.content.Context
import com.saathi.ai.access.AppDirectory
import com.saathi.ai.access.JimiAccessibilityService
import com.saathi.ai.access.RootBridge
import com.saathi.ai.access.ShizukuBridge
import com.saathi.ai.core.ApprovalGate
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.data.SecurePrefs

/**
 * Turns the model's normalised actions into real touches (Phase 3).
 *
 * Coordinates come in on a 0–999 grid, so the mapping is simply
 * `px = value / 1000 * screenWidth`. Execution tries the accessibility service
 * first (no extra privilege needed, and it is the most reliable), then root or
 * Shizuku `input` when the service cannot do it.
 *
 * Safety: an action that would type into a password/OTP field or press a final
 * payment confirmation is **never executed** — it is reported back so the model
 * can tell the user to do that part themselves.
 */
object ActionMapper {

    /** Words that mark a screen as sensitive (watch mode). */
    private val sensitiveWords = listOf(
        "password", "পাসওয়ার্ড", "otp", "one-time", "pin", "cvv", "card number",
        "পেমেন্ট কনফার্ম", "confirm payment", "pay now", "পেমেন্ট করুন", "বায়োমেট্রিক",
        "উপায় পাসওয়ার্ড", "secret", "private key"
    )

    fun isSensitiveScreen(screenText: String): String? {
        val low = screenText.lowercase()
        return sensitiveWords.firstOrNull { low.contains(it.lowercase()) }
    }

    /** Denormalises one axis from the 0–999 grid. */
    fun px(value: Int, size: Int): Int =
        ((value.coerceIn(0, 1000) / 1000f) * size).toInt().coerceIn(0, size - 1)

    data class Outcome(val ok: Boolean, val report: String)

    /**
     * Executes one action. Returns what actually happened, including which path
     * (accessibility / root / Shizuku) was used — the model learns from this.
     */
    fun execute(
        context: Context,
        svc: JimiAccessibilityService?,
        action: ComputerUseClient.Action,
        screenW: Int,
        screenH: Int
    ): Outcome {
        val intent = action.intent
        if (intent.isNotBlank()) ErrorCenter.info("vision", "অ্যাকশন: ${action.describe()} — $intent")

        if (svc == null) {
            // No accessibility service: fall back to privileged input if available.
            return privilegedOnly(context, action, screenW, screenH)
        }

        return when (action.name) {
            "click" -> {
                val x = px(action.int("x"), screenW).toFloat()
                val y = px(action.int("y"), screenH).toFloat()
                val ok = svc.gestures.humanTap(x, y) || svc.tap(x, y)
                Outcome(ok, "${if (ok) "সফল" else "ব্যর্থ"}: ট্যাপ ($x, $y) — ${intent.take(80)}")
            }
            "long_press" -> {
                val x = px(action.int("x"), screenW).toFloat()
                val y = px(action.int("y"), screenH).toFloat()
                val seconds = action.int("seconds", 2).coerceIn(1, 6)
                val ok = svc.longPress(x, y, seconds * 1000L)
                Outcome(ok, "${if (ok) "সফল" else "ব্যর্থ"}: লং প্রেস ($x, $y) ${seconds} সে.")
            }
            "type" -> {
                val text = action.str("text")
                if (text.isBlank()) return Outcome(false, "ব্যর্থ: টাইপ করার লেখা খালি")
                if (ApprovalGate.classify("type", text) >= ApprovalGate.Risk.BLOCKED) {
                    return Outcome(
                        false,
                        "আটকে: এটি পাসওয়ার্ড/OTP ধরনের লেখা — Jimi এটা নিজে টাইপ করে না। ঘরটিতে ফোকাস করে দিচ্ছি, তুমি লিখে দাও।"
                    )
                }
                var ok = svc.typeText(text)
                if (!ok && RootBridge.available()) ok = RootBridge.inputText(text).ok
                if (!ok && ShizukuBridge.usable(context)) {
                    ok = ShizukuBridge.exec("input text '${text.replace(" ", "%s")}'").ok
                }
                if (ok && action.bool("press_enter")) svc.pressEnter()
                Outcome(ok, "${if (ok) "সফল" else "ব্যর্থ"}: টাইপ করা হয়েছে «${text.take(40)}»" +
                    if (action.bool("press_enter")) " + এন্টার" else "")
            }
            "scroll" -> {
                val dir = action.str("direction", "down").lowercase()
                val down = dir == "down" || dir == "right"
                val magnitude = action.int("magnitude_in_pixels", 300)
                val bursts = (magnitude / 600).coerceIn(1, 4)
                val ok = when (dir) {
                    "left", "right" -> svc.gestures.swipeHorizontal(right = dir == "right")
                    else -> svc.gestures.humanScroll(down, bursts)
                }
                Outcome(ok, "${if (ok) "সফল" else "ব্যর্থ"}: স্ক্রল $dir ($bursts বার)")
            }
            "drag_and_drop" -> {
                val x1 = px(action.int("start_x"), screenW).toFloat()
                val y1 = px(action.int("start_y"), screenH).toFloat()
                val x2 = px(action.int("end_x"), screenW).toFloat()
                val y2 = px(action.int("end_y"), screenH).toFloat()
                val ok = svc.swipe(x1, y1, x2, y2, 420) || svc.gestures.drag(x1, y1, x2, y2, 420)
                Outcome(ok, "${if (ok) "সফল" else "ব্যর্থ"}: ড্র্যাগ ($x1,$y1) → ($x2,$y2)")
            }
            "press_key", "hotkey" -> {
                val key = action.str("key").ifBlank { action.args.optJSONArray("keys")?.optString(0) ?: "" }
                val mapped = mapKey(key)
                val ok = when {
                    mapped == "BACK" -> svc.gestures.gestureBack()
                    mapped == "ENTER" -> svc.pressEnter()
                    RootBridge.available() -> RootBridge.inputKey(mapped).ok
                    ShizukuBridge.usable(context) -> ShizukuBridge.exec("input keyevent $mapped").ok
                    else -> false
                }
                Outcome(ok, "${if (ok) "সফল" else "ব্যর্থ"}: কী চাপা হয়েছে ($key → $mapped)")
            }
            "wait" -> {
                val seconds = action.int("seconds", 1).coerceIn(1, 10)
                try {
                    Thread.sleep(seconds * 1000L)
                } catch (t: Throwable) {
                    ErrorCenter.log("vision", "wait বাধা পেয়েছে", t.message)
                }
                Outcome(true, "সফল: $seconds সে. অপেক্ষা করা হয়েছে")
            }
            "take_screenshot" -> Outcome(true, "সফল: পরের ধাপে নতুন স্ক্রিনশট নেওয়া হবে")
            "open_app" -> {
                val name = action.str("app").ifBlank { action.str("text") }
                if (name.isBlank()) return Outcome(false, "ব্যর্থ: কোন অ্যাপ খুলতে হবে বলা নেই")
                val r = AppDirectory.launchPackage(context, AppDirectory.resolve(context, name) ?: name, name)
                Outcome(!r.startsWith("ব্যর্থ"), "অ্যাপ খোলা: $name — $r")
            }
            "go_back" -> Outcome(svc.gestures.gestureBack(), "সফল: পেছনে যাওয়া হয়েছে")
            "go_home" -> {
                val ok = runCatching {
                    svc.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                }.getOrDefault(false)
                Outcome(ok, if (ok) "সফল: হোম স্ক্রিনে ফেরা হয়েছে" else "ব্যর্থ: হোমে ফেরা যায়নি")
            }
            "open_notifications" ->
                Outcome(svc.gestures.openNotifications(), "নোটিফিকেশন প্যানেল খোলা হয়েছে")
            else -> Outcome(
                false,
                "ব্যর্থ: অজানা অ্যাকশন «${action.name}» — এই অ্যাকশনটি Jimi চালাতে পারে না"
            )
        }
    }

    /** Accessibility-less fallback: only privileged input, and it says so. */
    private fun privilegedOnly(
        context: Context,
        action: ComputerUseClient.Action,
        screenW: Int,
        screenH: Int
    ): Outcome {
        val root = RootBridge.available()
        val shiz = ShizukuBridge.usable(context)
        if (!root && !shiz) {
            return Outcome(
                false,
                "আটকে: অ্যাক্সেসিবিলিটি সার্ভিস বন্ধ এবং রুট/Shizuku নেই — স্ক্রিনে ট্যাপ করার কোনো অনুমোদিত পথ নেই। " +
                    "সেটিংস → Accessibility → Jimi চালু করো।"
            )
        }
        val prefix = if (root) "রুট" else "Shizuku"
        return when (action.name) {
            "click" -> {
                val x = px(action.int("x"), screenW)
                val y = px(action.int("y"), screenH)
                val r = if (root) RootBridge.inputTap(x, y).ok
                else ShizukuBridge.exec("input tap $x $y").ok
                Outcome(r, "$prefix ইনপুট দিয়ে ট্যাপ ($x,$y)")
            }
            "type" -> {
                val text = action.str("text")
                val r = if (root) RootBridge.inputText(text).ok
                else ShizukuBridge.exec("input text '${text.replace(" ", "%s")}'").ok
                Outcome(r, "$prefix ইনপুট দিয়ে টাইপ «${text.take(40)}»")
            }
            "scroll" -> {
                val down = action.str("direction", "down") == "down"
                val midX = screenW / 2
                val (y1, y2) = if (down) (screenH * 0.72).toInt() to (screenH * 0.32).toInt()
                else (screenH * 0.32).toInt() to (screenH * 0.72).toInt()
                val cmd = "input swipe $midX $y1 $midX $y2 380"
                val r = if (root) RootBridge.exec(cmd).ok else ShizukuBridge.exec(cmd).ok
                Outcome(r, "$prefix দিয়ে স্ক্রল")
            }
            "go_back" -> {
                val r = if (root) RootBridge.inputKey("BACK").ok else ShizukuBridge.exec("input keyevent BACK").ok
                Outcome(r, "$prefix দিয়ে পেছনে")
            }
            else -> Outcome(false, "আটকে: $prefix পথে «${action.name}» অ্যাকশন চালানো যায় না")
        }
    }

    /** Model key names → Android keycodes. */
    private fun mapKey(key: String): String = when (key.lowercase()) {
        "enter", "return" -> "ENTER"
        "backspace", "delete" -> "DEL"
        "tab" -> "TAB"
        "escape", "esc" -> "ESCAPE"
        "home" -> "HOME"
        "back" -> "BACK"
        "space" -> "SPACE"
        "up" -> "DPAD_UP"
        "down" -> "DPAD_DOWN"
        "left" -> "DPAD_LEFT"
        "right" -> "DPAD_RIGHT"
        "search" -> "SEARCH"
        else -> key.uppercase().removePrefix("KEYCODE_")
    }

    /**
     * Restricted-field ladder for forms the model wants filled: focus by label,
     * then accessibility typing, then privileged input, then clipboard+paste.
     * Never used for password/OTP fields — those are handed to the user.
     */
    fun fillField(
        context: Context,
        svc: JimiAccessibilityService?,
        label: String,
        text: String
    ): Outcome {
        if (ApprovalGate.classify("fill_field", "$label $text") >= ApprovalGate.Risk.BLOCKED) {
            return Outcome(false, "আটকে: «$label» পাসওয়ার্ড/OTP ধরনের ঘর — এটা তুমি নিজে লিখবে, আমি ফোকাস করে দিতে পারি")
        }
        val prefs = SecurePrefs(context)
        if (svc != null) {
            val bounds = svc.boundsOf(label)
            if (bounds != null) {
                svc.tap(bounds.exactCenterX(), bounds.exactCenterY())
                Thread.sleep(260)
            }
            if (svc.tapByText(label) || bounds != null) {
                if (svc.typeText(text)) return Outcome(true, "সফল: «$label» ঘরে লেখা বসেছে (অ্যাক্সেসিবিলিটি পথ)")
            }
        }
        if (prefs.rootMode && RootBridge.available()) {
            if (RootBridge.inputText(text).ok) {
                return Outcome(true, "সফল: «$label» ঘরে লেখা বসেছে (রুট ইনপুট পথ)")
            }
        }
        if (ShizukuBridge.usable(context)) {
            val r = ShizukuBridge.exec("input text '${text.replace(" ", "%s")}'")
            if (r.ok) return Outcome(true, "সফল: «$label» ঘরে লেখা বসেছে (Shizuku ইনপুট পথ)")
        }
        return Outcome(
            false,
            "ব্যর্থ: «$label» ঘরে লেখা বসানো যায়নি — অ্যাপটি সুরক্ষিত ইনপুট ব্যবহার করছে। " +
                "ক্লিপবোর্ডে কপি করে রাখতে পারি, তুমি চেপে ধরে পেস্ট করো।"
        )
    }
}
