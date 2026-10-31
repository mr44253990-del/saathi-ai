package com.saathi.ai.access

import android.content.Context

/**
 * Offline action packs — the "no round trip" speed layer.
 *
 * Every normal action costs a model turn: the model asks for a tool, waits for the
 * result, thinks, asks for the next one. For the ten things people ask Jimi to do
 * every day that is wasteful, so those flows are compiled here into fixed step
 * lists that run **entirely on the device** in one go. The model only has to pick
 * the pack and supply one argument ("গানটা চালাও" → youtube_play, arg = গানের নাম).
 *
 * Two rules shaped this file:
 *  • **No pretending.** Every pack ends with a proof check against the real screen,
 *    and the report says exactly which steps succeeded and which did not.
 *  • **Human motion.** Taps inside a pack go through [GestureEngine.humanTap] /
 *    [GestureEngine.deliberateTap] and scrolls through [GestureEngine.humanScroll],
 *    so packs look like a person using the phone, not a teleporting cursor.
 */
object OfflinePacks {

    // ------------------------------------------------------------------- model

    sealed class Step {
        data class Open(val app: String) : Step()
        data class Url(val url: String) : Step()
        data class Tap(val text: String, val human: Boolean = true) : Step()
        data class LongPress(val text: String) : Step()
        data class Type(val text: String) : Step()
        data class Scroll(val down: Boolean = true, val bursts: Int = 1) : Step()
        data class Sleep(val ms: Long) : Step()
        data class Call(val nameArg: Boolean) : Step()
        data class Carrom(val strikes: Int) : Step()
        data class CopyFile(val destArg: Boolean) : Step()
        object Back : Step()
        /** Proof: at least one of these words must be on screen afterwards. */
        data class Proof(val words: List<String>) : Step()
    }

    data class Pack(
        val id: String,
        val bangla: String,
        val needsArg: Boolean,
        val argHint: String,
        val steps: List<Step>
    )

    // ------------------------------------------------------------------ packs

    val all: List<Pack> = listOf(
        Pack(
            "youtube_play", "ইউটিউবে গান/ভিডিও চালাও", true, "কী চালাবে",
            listOf(
                Step.Open("youtube"), Step.Sleep(1500),
                Step.Tap("search"), Step.Sleep(600),
                Step.Type("{arg}"), Step.Sleep(400),
                Step.Tap("enter", human = false), Step.Sleep(1800),
                Step.Tap("filter"), Step.Sleep(700),
                Step.Proof(listOf("{arg}", "views", "ভিউ"))
            )
        ),
        Pack(
            "whatsapp_msg", "হোয়াটসঅ্যাপে মেসেজ পাঠাও", true, "কাকে ও কী লিখবে (নাম|মেসেজ)",
            listOf(
                Step.Open("whatsapp"), Step.Sleep(1600),
                Step.Tap("search"), Step.Sleep(700),
                Step.Type("{name}"), Step.Sleep(1200),
                Step.Tap("{name}"), Step.Sleep(1300),
                Step.Tap("message"), Step.Sleep(500),
                Step.Type("{text}"), Step.Sleep(400),
                Step.Tap("send"), Step.Sleep(900),
                Step.Proof(listOf("{text}"))
            )
        ),
        Pack(
            "telegram_voice", "টেলিগ্রামে ভয়েস নোট পাঠাও", true, "কাকে",
            listOf(
                Step.Open("telegram"), Step.Sleep(1600),
                Step.Tap("search"), Step.Sleep(700),
                Step.Type("{arg}"), Step.Sleep(1200),
                Step.Tap("{arg}"), Step.Sleep(1300),
                Step.LongPress("voice"), Step.Sleep(600),
                Step.Proof(listOf("{arg}", "voice", "audio"))
            )
        ),
        Pack(
            "fb_story", "ফেসবুকে স্টোরি দাও", false, "",
            listOf(
                Step.Open("facebook"), Step.Sleep(1900),
                Step.Tap("mind"), Step.Sleep(900),
                Step.Tap("photo"), Step.Sleep(1400),
                Step.Tap("share"), Step.Sleep(1200),
                Step.Proof(listOf("story", "stories", "your story"))
            )
        ),
        Pack(
            "tiktok_upload", "টিকটকে ভিডিও আপলোড করো", false, "",
            listOf(
                Step.Open("tiktok"), Step.Sleep(1900),
                Step.Tap("add"), Step.Sleep(1400),
                Step.Tap("upload"), Step.Sleep(1500),
                Step.Tap("next"), Step.Sleep(1600),
                Step.Tap("post"), Step.Sleep(1600),
                Step.Proof(listOf("post", "posted", "your video"))
            )
        ),
        Pack(
            "gallery_edit", "গ্যালারির ছবি এডিট করে সেভ করো", false, "",
            listOf(
                Step.Open("photos"), Step.Sleep(1800),
                Step.Scroll(true), Step.Sleep(600),
                Step.Tap("edit"), Step.Sleep(1300),
                Step.Tap("crop"), Step.Sleep(900),
                Step.Tap("done"), Step.Sleep(900),
                Step.Tap("save"), Step.Sleep(1200),
                Step.Proof(listOf("saved", "editing", "edit"))
            )
        ),
        Pack(
            "recharge", "মোবাইল রিচার্জ সাইট খোলো", true, "নম্বর (ঐচ্ছিক)",
            listOf(
                Step.Url("https://www.bkash.com/en/personal/recharge"), Step.Sleep(2600),
                Step.Proof(listOf("recharge", "mobile", "amount"))
            )
        ),
        Pack(
            "call_contact", "কন্টাক্ট থেকে ফোন দাও", true, "কার নাম",
            listOf(Step.Call(true), Step.Proof(listOf("calling", "call", "end call")))
        ),
        Pack(
            "carrom_play", "আসল ক্যারম বোর্ডে খেলো", false, "",
            listOf(Step.Carrom(3), Step.Proof(listOf("carrom", "score", "turn", "striker")))
        ),
        Pack(
            "copy_file", "ফাইল কপি করো", true, "কোন ফাইল → কোথায় (নাম|ফোল্ডার)",
            listOf(Step.CopyFile(true), Step.Proof(listOf("copied", "copy", "1 selected")))
        ),
        Pack(
            "open_settings", "ফোনের সেটিংসের একটা পেজ খোলো", true, "কোন সেটিংস",
            listOf(Step.Open("settings"), Step.Sleep(1400), Step.Tap("{arg}"),
                Step.Proof(listOf("{arg}")))
        ),
        Pack(
            "download_manager", "ডাউনলোড ফোল্ডার খোলো", false, "",
            listOf(
                Step.Open("files"), Step.Sleep(1600),
                Step.Tap("downloads"), Step.Sleep(1100),
                Step.Proof(listOf("downloads", "download"))
            )
        )
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): Pack? = byId[id]

    /** Matches spoken Bangla/English to a pack id. */
    fun find(query: String): Pack? {
        val q = query.lowercase()
        val score = all.map { pack ->
            var s = 0
            pack.bangla.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }
                .forEach { if (q.contains(it.lowercase())) s += 3 }
            pack.id.split('_', '-').forEach { if (q.contains(it) && it.length > 2) s += 2 }
            pack to s
        }
        return score.maxByOrNull { it.second }?.takeIf { it.second >= 3 }?.first
    }

    fun catalogue(): String = all.joinToString("\n") { p ->
        "• ${p.id} — ${p.bangla}" + if (p.needsArg) " (দরকার: ${p.argHint})" else ""
    }

    // ------------------------------------------------------------- interpreter

    /**
     * Runs a pack start to finish on-device. Returns an honest report: which steps
     * landed, what the screen proves, and what is still missing.
     */
    fun run(
        context: Context,
        packId: String,
        arg: String? = null,
        onStep: (String) -> Unit = {}
    ): String {
        val pack = get(packId) ?: find(packId)
            ?: return "প্যাক পাওয়া যায়নি। যা আছে:\n${catalogue()}"

        val svc = JimiAccessibilityService.instance
        val tools = PhoneTools(context)
        val files = FileTools(context)
        val g = svc?.gestures

        // split a "name|text" style argument
        val parts = (arg ?: "").split("|", "→", "->")
        val name = parts.getOrNull(0)?.trim().orEmpty()
        val text = parts.getOrNull(1)?.trim().orEmpty()
        val filled: (String) -> String = { s ->
            s.replace("{arg}", arg ?: "")
                .replace("{name}", name)
                .replace("{text}", if (text.isBlank()) name else text)
        }

        val log = StringBuilder()
        var proofWords: List<String> = emptyList()
        var index = 0

        for (step in pack.steps) {
            index++
            val result: String = when (step) {
                is Step.Open -> {
                    onStep(filled(step.app) + " খুলছি")
                    tools.openApp(step.app)
                }
                is Step.Url -> {
                    onStep("লিংক খুলছি")
                    tools.openUrl(step.url)
                }
                is Step.Tap -> {
                    val label = filled(step.text)
                    onStep("«$label» চাপছি")
                    val ok = when {
                        label == "enter" -> svc?.pressEnter() == true
                        step.human -> {
                            val b = svc?.boundsOf(label)
                            if (b != null && g != null) {
                                g.humanTap(b.centerX().toFloat(), b.centerY().toFloat())
                            } else svc?.tapByText(label) == true
                        }
                        else -> svc?.tapByText(label) == true
                    }
                    if (ok) "ট্যাপ হয়েছে: $label" else "পাইনি: $label"
                }
                is Step.LongPress -> {
                    val label = filled(step.text)
                    onStep("«$label» চেপে ধরছি")
                    val b = svc?.boundsOf(label)
                    val ok = if (b != null && g != null) {
                        g.longPress(b.centerX().toFloat(), b.centerY().toFloat())
                    } else svc?.screenLongPressOk(label) == true
                    if (ok) "চেপে ধরেছি: $label" else "পাইনি: $label"
                }
                is Step.Type -> {
                    val value = filled(step.text)
                    onStep("লিখছি: $value")
                    if (svc?.typeText(value) == true) "লিখেছি" else "লেখা যায়নি"
                }
                is Step.Scroll -> {
                    onStep("স্ক্রল করছি")
                    val ok = if (g != null) g.humanScroll(step.down, step.bursts)
                    else svc?.scroll(step.down) == true
                    if (ok) "স্ক্রল করেছি" else "স্ক্রল হয়নি"
                }
                is Step.Sleep -> {
                    runCatching { Thread.sleep(step.ms) }
                    "অপেক্ষা"
                }
                is Step.Back -> {
                    if (g?.gestureBack() == true) "পিছিয়েছি" else "পিছোনো যায়নি"
                }
                is Step.Call -> {
                    onStep("ফোন দিচ্ছি")
                    tools.callContact(name.ifBlank { arg ?: "" })
                }
                is Step.Carrom -> {
                    onStep("ক্যারম বোর্ড খুলছি")
                    val opened = tools.openCarromBoard()
                    val strikes = StringBuilder(opened)
                    runCatching { Thread.sleep(2600) }
                    repeat(step.strikes) { i ->
                        onStep("স্ট্রাইক ${i + 1}")
                        strikes.append("\n").append(tools.strikeCarrom())
                        runCatching { Thread.sleep(1400) }
                    }
                    strikes.toString()
                }
                is Step.CopyFile -> {
                    onStep("ফাইল কপি করছি")
                    files.copyViaUi(name.ifBlank { arg ?: "" }, text.ifBlank { "Downloads" })
                }
                is Step.Proof -> {
                    proofWords = step.words.map(filled)
                    ""
                }
            }
            if (result.isNotBlank()) log.appendLine("ধাপ $index: $result")
        }

        return verdict(pack, svc?.dumpScreen()?.lowercase().orEmpty(), proofWords, log.toString())
    }

    /** Same honesty rule as the autopilot: prove it or say it is not done. */
    private fun verdict(pack: Pack, screen: String, proof: List<String>, log: String): String {
        if (proof.isEmpty()) return "«${pack.bangla}» প্যাক চালিয়েছি:\n$log"

        val found = proof.filter { it.isNotBlank() && screen.contains(it.lowercase()) }
        val failed = log.lines().count { it.contains("পাইনি") || it.contains("যায়নি") || it.contains("হয়নি") }

        val head = when {
            found.isNotEmpty() && failed == 0 ->
                "সফল: «${pack.bangla}» হয়ে গেছে — স্ক্রিনে মিল: ${found.joinToString(", ")}"
            found.isNotEmpty() ->
                "আংশিক: «${pack.bangla}» বেশিরভাগ হয়েছে, কিন্তু ${failed}টি ধাপ ঠিকমতো বসেনি। স্ক্রিনে মিল: ${found.joinToString(", ")}"
            else ->
                "ব্যর্থ: «${pack.bangla}» শেষ হয়নি — স্ক্রিনে প্রমাণ পাওয়া যায়নি। যা করেছি তার রিপোর্ট নিচে।"
        }
        val missing = proof.filter { it.isNotBlank() && !screen.contains(it.lowercase()) }
        val tail = if (missing.isNotEmpty()) "\nস্ক্রিনে এখনো নেই: ${missing.joinToString(", ")}" else ""
        return "$head$tail\n\n$log"
    }

    private fun JimiAccessibilityService.screenLongPressOk(text: String): Boolean {
        val b = boundsOf(text) ?: return false
        return longPress(b.centerX().toFloat(), b.centerY().toFloat())
    }
}
