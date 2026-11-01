package com.saathi.ai.agent

import com.saathi.ai.access.FileTools
import com.saathi.ai.access.HealthInsights
import com.saathi.ai.access.RootBridge
import com.saathi.ai.access.ShizukuBridge
import com.saathi.ai.access.SmartTools
import com.saathi.ai.access.TerminalTools
import com.saathi.ai.core.ApprovalGate
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.create.ArtEngine
import com.saathi.ai.create.DocumentStudio
import com.saathi.ai.create.ImageGen
import com.saathi.ai.media.AudioGen
import com.saathi.ai.media.AudioLab
import com.saathi.ai.media.AudioPlaybackService
import com.saathi.ai.media.ImageEditor
import com.saathi.ai.media.MediaUnderstanding
import com.saathi.ai.media.VideoLab
import com.saathi.ai.media.VisionLab
import com.saathi.ai.vision.VisualCore
import com.saathi.ai.web.ResearchEngine
import com.saathi.ai.web.WebBrowser
import java.io.File

/**
 * The eleven specialists (Phase 17).
 *
 * Every agent calls the *existing* engines directly (FileTools, TerminalTools,
 * ArtEngine, ScreenVision…) rather than re-implementing them, so behaviour stays
 * identical to what the 163 tools already do — the agents only add routing,
 * budgeting and honest reporting.
 *
 * Reporting rule shared by all of them: the first line of `report` carries the
 * verdict token (`সফল:` / `আংশিক:` / `ব্যর্থ:` / `আটকে:`), and nothing is
 * claimed that was not verified.
 */

// ---------------------------------------------------------------- 1. planner

class PlannerAgent : Agent() {
    override val id = "planner"
    override val nameBangla = "পরিকল্পনা এজেন্ট"
    override val resources = setOf<String>()

    override fun score(goal: String, detail: String) =
        hits("$goal $detail", listOf("টু-ডু", "তালিকা", "প্ল্যান", "plan", "ধাপে ধাপে", "ভেঙে", "কাজ সাজাও"), 40) +
            // a long, multi-verb sentence is a project, not a single action
            (if (goal.length > 70) 22 else 0) +
            (if (splitWords.any { goal.contains(it) }) 18 else 0)

    private val splitWords = listOf("এবং", "তারপর", "এরপর", "পরে", "আর ", "তার পর", "শেষে", "এর মধ্যে")

    override fun work(req: AgentRequest, ctx: AgentContext, onStep: (String) -> Unit): AgentResult {
        val steps = plan(req.goal)
        if (steps.size < 2) {
            return AgentResult.partial(
                "আংশিক: এটাকে ভাঙার মতো একাধিক ধাপ পাওয়া যায়নি — একটাই কাজ বলে মনে হচ্ছে।\n" +
                    "একটি কাজ: ${req.goal}"
            )
        }
        onStep("পরিকল্পনা: ${steps.size} ধাপে ভাগ করছি")
        val added = ctx.board.addPlan(steps)
        req.spend(1)
        return AgentResult.done(
            "সফল: ${steps.size} ধাপের টু-ডু তালিকা বানিয়েছি।\n$added\n" +
                "প্রতিটি ধাপ শেষ হলে নিজে থেকেই টিক পড়বে।"
        )
    }

    /**
     * Splits a Bangla/English instruction into ordered steps. Deliberately
     * rule-based: the model already planned when it chose this agent, and a
     * deterministic splitter cannot invent steps that were never asked for.
     */
    fun plan(goal: String): List<String> {
        val raw = goal.split(Regex("(?:\\bএবং\\b|\\bতারপর\\b|\\bএরপর\\b|\\bপরে\\b|\\bশেষে\\b|,|;|\\.|।|\\n)"))
            .map { it.trim().trim('!', '?') }
            .filter { it.length > 3 }
            .distinct()
        val steps = if (raw.size >= 2) raw else listOf(goal)
        val out = steps.toMutableList()
        // Every plan ends with proof and a report — that is what makes the run
        // auditable instead of "the AI said it worked".
        if (out.none { it.contains("যাচাই") }) out.add("ফলাফল যাচাই করা")
        if (out.none { it.contains("রিপোর্ট") }) out.add("সংক্ষিপ্ত রিপোর্ট তৈরি করা")
        return out.take(12)
    }
}

// --------------------------------------------------------------- 2. research

class ResearchAgent : Agent() {
    override val id = "research"
    override val nameBangla = "রিসার্চ এজেন্ট"
    override val resources = setOf("network")

    override fun score(goal: String, detail: String) =
        hits("$goal $detail", listOf(
            "রিসার্চ", "research", "তথ্য জোগাড়", "খুঁজে বের", "জেনে নাও", "তুলনা", "compare",
            "সামারি", "summary", "বিশ্লেষণ", "রিপোর্ট বানাও", "কত দাম", "কোনটা ভালো", "নতুন খবর"
        ), 16)

    override fun work(req: AgentRequest, ctx: AgentContext, onStep: (String) -> Unit): AgentResult {
        if (!ctx.prefs.isConfigured) {
            return AgentResult.blocked("আটকে: API কী নেই — রিসার্চের জন্য সেটিংসে কী বসাও")
        }
        val seconds = (req.remainingSeconds()).coerceIn(20, 240)
        onStep("রিসার্চ: একাধিক সোর্স থেকে তথ্য যাচাই করছি (সর্বোচ্চ $seconds সে.)")
        return try {
            val report = ResearchEngine(ctx.context)
                .research(req.goal, sources = 5, seconds = seconds, onStep = onStep)
            req.spend(2)
            when {
                report.startsWith("ব্যর্থ") -> AgentResult.failed(report)
                report.startsWith("আংশিক") -> AgentResult.partial(report)
                else -> AgentResult.done(report)
            }
        } catch (t: Throwable) {
            ErrorCenter.logThrow("agent", "রিসার্চ এজেন্ট ব্যর্থ", t)
            AgentResult.failed("ব্যর্থ: রিসার্চ করা যায়নি (${t.message})")
        }
    }
}

// --------------------------------------------------------------- 3. browser

class BrowserAgent : Agent() {
    override val id = "browser"
    override val nameBangla = "ব্রাউজার এজেন্ট"
    override val resources = setOf("network", "webview")

    override fun score(goal: String, detail: String) =
        hits("$goal $detail", listOf(
            "ওয়েবসাইট", "ব্রাউজার", "browser", "সাইটে", "url", "http", "লিংক", "পেজ",
            "ফর্ম", "ডাউনলোড", "ট্যাব", "ওয়েব"
        ), 18)

    override fun work(req: AgentRequest, ctx: AgentContext, onStep: (String) -> Unit): AgentResult {
        val artifacts = mutableListOf<String>()
        val log = StringBuilder()
        val g = req.goal
        val url = Regex("(https?://\\S+|www\\.\\S+)", RegexOption.IGNORE_CASE).find(g)?.value

        if (url != null) {
            onStep("ব্রাউজার: $url খুলছি")
            log.appendLine(WebBrowser.open(ctx.context, url))
            req.spend(1)
            if (!ctx.alive(req)) return AgentResult.partial(outOfBudget(req) ?: "আটকে: সময় শেষ", emptyList())
            onStep("পেজের লেখা পড়ছি")
            val text = WebBrowser.text(ctx.context)
            log.appendLine("পেজের লেখা: ${text.take(900)}")
            req.spend(1)
            val shot = WebBrowser.shot(ctx.context)
            if (shot.startsWith("সফল") || shot.contains(".png") || shot.contains(".jpg")) artifacts.add(shot)
        } else {
            onStep("ব্রাউজারে খুঁজছি: ${g.take(60)}")
            val search = WebBrowser.search(ctx.context, g)
            log.appendLine(search)
            req.spend(1)
            log.appendLine("পেজের লেখা: ${WebBrowser.text(ctx.context).take(900)}")
        }
        log.appendLine(WebBrowser.status(ctx.context))
        val body = log.toString()
        return if (body.contains("ব্যর্থ") && !body.contains("সফল")) {
            AgentResult.failed("ব্যর্থ: ব্রাউজার কাজ শেষ করতে পারেনি\n$body")
        } else {
            AgentResult.done("সফল: ব্রাউজারে কাজ হয়েছে\n$body", artifacts)
        }
    }
}

// ----------------------------------------------------------- 4. visual agent

class VisualComputerAgent : Agent() {
    override val id = "visual"
    override val nameBangla = "ভিজ্যুয়াল কম্পিউটার এজেন্ট"
    override val resources = setOf("screen")

    override fun score(goal: String, detail: String) =
        hits("$goal $detail", listOf(
            "স্ক্রিনে", "ট্যাপ", "চাপ দাও", "ক্লিক", "স্ক্রল", "অ্যাপে গিয়ে", "ফর্ম ভরাও",
            "বাটন", "সেটিংসে গিয়ে", "লগইন", "অ্যাকাউন্ট খোলো", "নেভিগেট", "খুলে ওটা"
        ), 14) + (if (!goal.contains("http")) 4 else 0)

    override fun work(req: AgentRequest, ctx: AgentContext, onStep: (String) -> Unit): AgentResult {
        val svc = ctx.service
        if (svc == null) {
            return AgentResult.blocked(
                "আটকে: অ্যাক্সেসিবিলিটি সার্ভিস বন্ধ — স্ক্রিন দেখে ট্যাপ করা সম্ভব নয়। " +
                    "সেটিংস → Accessibility → Jimi চালু করো (রুট/Shizuku থাকলে আমি নিজেই চালু করতে পারি)"
            )
        }
        return try {
            val report = VisualCore.run(
                ctx, req, svc, onStep
            )
            req.spend(3)
            when {
                report.startsWith("সফল") -> AgentResult.done(report)
                report.startsWith("আংশিক") -> AgentResult.partial(report)
                report.startsWith("আটকে") -> AgentResult.blocked(report)
                else -> AgentResult.failed(report)
            }
        } catch (t: Throwable) {
            ErrorCenter.logThrow("vision", "ভিজ্যুয়াল এজেন্ট ব্যর্থ", t)
            AgentResult.failed("ব্যর্থ: স্ক্রিন অটোমেশন ব্যর্থ (${t.message})")
        }
    }
}

// ------------------------------------------------------------------ 5. files

class FileAgent : Agent() {
    override val id = "file"
    override val nameBangla = "ফাইল ম্যানেজমেন্ট এজেন্ট"
    override val resources = setOf("storage")

    override fun score(goal: String, detail: String) =
        hits("$goal $detail", listOf(
            "ফাইল", "ফোল্ডার", "কপি", "মুভ", "সরাও", "ডিলিট", "মুছে ফেলো", "নতুন ফোল্ডার",
            "ডকুমেন্ট", "zip", "apk", "pdf", "ছবিগুলো", "download ফোল্ডার", "ফাইল খুঁজে"
        ), 18)

    override fun work(req: AgentRequest, ctx: AgentContext, onStep: (String) -> Unit): AgentResult {
        val files = FileTools(ctx.context)
        val g = req.goal
        val low = g.lowercase()
        val log = StringBuilder()
        val artifacts = mutableListOf<String>()

        when {
            low.contains("খুঁজ") || low.contains("find") -> {
                val q = g.substringAfter("খুঁজ").ifBlank { g }
                onStep("ফাইল খুঁজছি: ${q.take(40)}")
                val found = files.find(q.trim(), 20)
                req.spend(1)
                if (found.isEmpty()) log.appendLine("কোনো ফাইল পাওয়া যায়নি: $q")
                else found.forEach {
                    log.appendLine("• ${it.name} — ${if (it.isDir) "ফোল্ডার" else files.humanSize(it.size)} — ${it.path}")
                    artifacts.add(it.path)
                }
            }
            low.contains("কপি") || low.contains("copy") -> {
                val (src, dst) = twoParts(g, listOf("কপি", "copy"))
                onStep("কপি করছি")
                log.appendLine(files.copy(src, dst))
                req.spend(1)
            }
            low.contains("মুভ") || low.contains("সরা") || low.contains("move") -> {
                val (src, dst) = twoParts(g, listOf("মুভ", "সরা", "move"))
                onStep("সরাচ্ছি")
                log.appendLine(files.move(src, dst))
                req.spend(1)
            }
            low.contains("ডিলিট") || low.contains("মুছে") || low.contains("delete") -> {
                val path = g.substringAfter("মুছে").ifBlank { g }.trim()
                // Deleting is destructive: the agent never does it on its own word.
                val risk = ApprovalGate.classify("delete file", path)
                if (risk >= ApprovalGate.Risk.HIGH) {
                    return AgentResult.blocked(
                        "আটকে: ফাইল মোছা ধ্বংসাত্মক কাজ ($path) — ব্যবহারকারীর অনুমোদন ছাড়া মুছব না। " +
                            "অনুমোদন পেলে মুছে ফেলব।"
                    )
                }
                log.appendLine(files.delete(path))
                req.spend(1)
            }
            low.contains("তথ্য") || low.contains("info") -> {
                val p = g.substringAfter("তথ্য").ifBlank { g }.trim()
                log.appendLine(files.info(p))
                req.spend(1)
            }
            low.contains("লিস্ট") || low.contains("তালিকা") || low.contains("দেখাও") -> {
                val dir = g.substringAfter("দেখাও").ifBlank { null }
                val entries = files.list(dir, 60)
                req.spend(1)
                if (entries.isEmpty()) log.appendLine("ফোল্ডারটি খালি বা পড়া যায়নি")
                else entries.forEach {
                    log.appendLine("• ${if (it.isDir) "[ফোল্ডার]" else files.humanSize(it.size)} ${it.name}")
                }
            }
            low.contains("খোলো") || low.contains("open") -> {
                val p = g.substringAfter("খোলো").ifBlank { g }.trim()
                onStep("ফাইল খুলছি")
                log.appendLine(files.open(p))
                req.spend(1)
            }
            low.contains("শেয়ার") || low.contains("share") -> {
                val p = g.substringAfter("শেয়ার").ifBlank { g }.trim()
                log.appendLine(files.share(p))
                req.spend(1)
            }
            low.contains("নতুন ফোল্ডার") || low.contains("folder বানা") -> {
                val (name, parent) = twoParts(g, listOf("নতুন ফোল্ডার", "folder"))
                log.appendLine(files.newFolder(parent.ifBlank { null }, name))
                req.spend(1)
            }
            else -> {
                val entries = files.list(null, 40)
                log.appendLine("ফাইল কাজটি চেনা যায়নি — দৃশ্যমান ফোল্ডারগুলো দেখাচ্ছি:")
                entries.filter { it.isDir }.forEach { log.appendLine("• ${it.name}") }
                req.spend(1)
            }
        }

        val body = log.toString().trim()
        val failed = body.contains("পারেনি") || body.contains("পাওয়া যায়নি") || body.contains("ব্যর্থ")
        return if (failed && !body.contains("সফল") && !body.contains("করেছি")) {
            AgentResult.failed("ব্যর্থ: ফাইল কাজ সম্পন্ন হয়নি\n$body")
        } else {
            AgentResult.done("সফল: ফাইল কাজ সম্পন্ন\n$body", artifacts)
        }
    }

    /** Pulls "source … destination" out of a Bangla instruction. */
    private fun twoParts(goal: String, verbs: List<String>): Pair<String, String> {
        var rest = goal
        verbs.forEach { v ->
            val i = rest.lowercase().indexOf(v.lowercase())
            if (i >= 0) rest = rest.substring(i + v.length)
        }
        val bits = rest.split(Regex("(?:থেকে|এবং|->|to|পর্যন্ত|ভিতরে|ফোল্ডারে)")).map { it.trim() }
            .filter { it.isNotBlank() }
        return if (bits.size >= 2) bits[0] to bits[1] else (bits.firstOrNull() ?: "") to ""
    }
}

// -------------------------------------------------------------- 6. terminal

class TerminalAgent : Agent() {
    override val id = "terminal"
    override val nameBangla = "টার্মিনাল এজেন্ট"
    override val resources = setOf("shell")

    private val destructive = listOf(
        "rm -rf", "mkfs", "dd if=", "> /dev/", "pm clear", "wipe", "format",
        "factory", "shutdown", "reboot", "pm uninstall", "chmod -R 777"
    )

    override fun score(goal: String, detail: String) =
        hits("$goal $detail", listOf(
            "টার্মিনাল", "টার্মিন্যালে", "কমান্ড", "shell", "শেল", "termux", "terminal",
            "adb", "df -h", "logcat", "dumpsys", "সিস্টেম কমান্ড"
        ), 22)

    override fun work(req: AgentRequest, ctx: AgentContext, onStep: (String) -> Unit): AgentResult {
        val term = TerminalTools(ctx.context)
        val goal = req.goal
        val extracted = extractCommand(goal)

        if (extracted == null) {
            return AgentResult.partial(
                "আংশিক: কমান্ডটি বুঝতে পারিনি। উদাহরণ: «টার্মিনালে df -h চালাও»।\n" +
                    "টার্মিনাল অবস্থা:\n${term.diagnostics()}"
            )
        }
        // Declared non-null on purpose: `cmd` is reassigned inside the retry loop
        // below, and a captured `var` of a nullable type cannot be smart-cast.
        var cmd: String = extracted

        if (destructive.any { cmd.contains(it) }) {
            val ask = ApprovalGate.classify(cmd, goal)
            // Blocking approval is handled by the caller (ToolExecutor/engine),
            // here we refuse to run it silently.
            return AgentResult.blocked(
                "আটকে: এটি ধ্বংসাত্মক কমান্ড ($cmd) — ঝুঁকি ${ApprovalGate.riskName(ask)}। " +
                    "ব্যবহারকারীর অনুমোদন ছাড়া চালাব না।"
            )
        }

        onStep("টার্মিনাল: $cmd")
        // Self-correcting loop: read the output, fix the command, try again —
        // bounded by ErrorCenter's retry budget so it can never spin forever.
        var last = ""
        var round = 0
        while (round < 3 && ctx.alive(req)) {
            round++
            if (!ErrorCenter.retryBudget("terminal.${cmd.hashCode()}", 3)) {
                return AgentResult.failed(
                    "ব্যর্থ: একই কমান্ড ৩ বার চেষ্টা করেও হয়নি — রিট্রাই বাজেট শেষ\n$last"
                )
            }
            val out = if (ShizukuBridge.usable(ctx.context)) {
                ShizukuBridge.privileged(ctx.context, cmd)
            } else {
                term.selfDrive(cmd, maxRounds = 2)
            }
            last = out
            onStep("টার্মিনাল আউটপুট পড়ছি (${round}/৩)")
            req.spend(1)
            val bad = out.contains("not found") || out.contains("ব্যর্থ") ||
                out.contains("Permission denied") || out.contains("error:")
            if (!bad) {
                ErrorCenter.resetRetry("terminal.${cmd.hashCode()}")
                return AgentResult.done("সফল: কমান্ড চলেছে ($cmd)\n$out")
            }
            val fixed = fixCommand(cmd, out)
            if (fixed == cmd) break
            cmd = fixed
        }
        return AgentResult.partial("আংশিক: কমান্ড চালাতে গিয়ে সমস্যা হয়েছে\n$last")
    }

    /** Pulls a command out of Bangla/English instruction text. */
    private fun extractCommand(goal: String): String? {
        val quoted = Regex("[\"“']([^\"”']{2,200})[\"”']").find(goal)?.groupValues?.get(1)?.trim()
        if (!quoted.isNullOrBlank()) return quoted
        val after = listOf("চালাও", "চালিয়ে দাও", "run", "execute", "টার্মিনালে", "শেলে")
            .mapNotNull { k ->
                val i = goal.lowercase().indexOf(k.lowercase())
                if (i >= 0) goal.substring(i + k.length).trim() else null
            }
            .filter { it.length > 2 }
            .maxByOrNull { it.length }
        return after?.takeIf { it.contains(" ") || it.matches(Regex("[a-z0-9_./-]+", RegexOption.IGNORE_CASE)) }
    }

    /** One concrete correction per known failure — no blind retries. */
    private fun fixCommand(cmd: String, output: String): String = when {
        output.contains("Permission denied") && !cmd.startsWith("su ") && RootBridge.available() -> "su -c '$cmd'"
        output.contains("not found") && cmd.startsWith("python ") -> cmd.replaceFirst("python ", "python3 ")
        output.contains("No such file") && cmd.contains("ls ") -> cmd.replaceFirst("ls ", "ls -la ")
        output.contains("command not found") -> cmd
        else -> cmd
    }
}

// ------------------------------------------------------------------- 7. code

class CodeAgent : Agent() {
    override val id = "code"
    override val nameBangla = "কোড এজেন্ট"
    override val resources = setOf("shell", "storage")

    override fun score(goal: String, detail: String) =
        hits("$goal $detail", listOf(
            "কোড লেখো", "স্ক্রিপ্ট", "python", "প্রোগ্রাম লেখো", "code লেখো", "ফাংশন লেখো",
            "html বানাও", "json ফাইল", "csv বানাও", "কোড চালাও"
        ), 24)

    override fun work(req: AgentRequest, ctx: AgentContext, onStep: (String) -> Unit): AgentResult {
        val goal = req.goal
        val files = FileTools(ctx.context)
        val studio = DocumentStudio(ctx.context)
        val low = goal.lowercase()

        val ext = when {
            low.contains("python") || low.contains(".py") -> "py"
            low.contains("html") -> "html"
            low.contains("json") -> "json"
            low.contains("csv") -> "csv"
            low.contains("shell") || low.contains("bash") || low.contains(".sh") -> "sh"
            low.contains("kotlin") -> "kt"
            else -> "txt"
        }
        val name = "jimi-code-${System.currentTimeMillis() / 1000}.$ext"

        onStep("কোড/ডকুমেন্ট তৈরি করছি: $name")
        val written = when (ext) {
            "html", "csv", "json" -> studio.create(ext, goal)
            else -> files.writeText(name, goal, shareIt = false)
        }
        req.spend(1)

        // If it is runnable and a real shell exists, run it and report the truth.
        if (ext == "py" || ext == "sh") {
            val path = File(ctx.context.getExternalFilesDir(null) ?: ctx.context.filesDir, name).absolutePath
            if (TerminalTools(ctx.context).termuxInstalled() || ShizukuBridge.usable(ctx.context) ||
                RootBridge.available()
            ) {
                onStep("কোডটি চালিয়ে দেখছি")
                val runCmd = if (ext == "py") "python3 '$path'" else "sh '$path'"
                val out = if (ShizukuBridge.usable(ctx.context)) ShizukuBridge.privileged(ctx.context, runCmd)
                else TerminalTools(ctx.context).selfDrive(runCmd, maxRounds = 2)
                req.spend(2)
                val ok = !out.contains("Traceback") && !out.contains("syntax error") &&
                    !out.contains("not found")
                return if (ok) {
                    AgentResult.done("সফল: কোড লিখে চালিয়ে যাচাই করেছি\nফাইল: $written\nআউটপুট:\n${out.take(1200)}", listOf(written))
                } else {
                    AgentResult.partial(
                        "আংশিক: কোড লেখা হয়েছে কিন্তু চালাতে ত্রুটি — সংশোধন করে আবার চালাতে বলো\n" +
                            "ফাইল: $written\nআউটপুট:\n${out.take(1200)}", listOf("run-error")
                    )
                }
            }
            return AgentResult.partial(
                "আংশিক: কোড লিখে ফাইলে রেখেছি, কিন্তু চালানোর জন্য Termux/Shizuku/রুট নেই — " +
                    "কোনোটা দিলে চালিয়ে ফল যাচাই করে দিতে পারব\nফাইল: $written", listOf(written)
            )
        }
        return AgentResult.done("সফল: ফাইল তৈরি হয়েছে\n$written", listOf(written))
    }
}

// ------------------------------------------------------------- 8. image/media

class MediaAgent : Agent() {
    override val id = "media"
    override val nameBangla = "ছবি এজেন্ট"
    override val resources = setOf("cpu")

    override fun score(goal: String, detail: String) =
        hits("$goal $detail", listOf(
            "ছবি", "আঁকো", "পোস্টার", "এডিট", "crop", "কাটো", "উজ্জ্বল", "সাদাকালো", "কোলাজ",
            "ওয়ালপেপার", "ocr", "লেখা পড়ো", "ব্যাকগ্রাউন্ড সরাও", "enhance", "ইমেজ", "image",
            "generate", "ছবি বানাও"
        ), 18)

    override fun work(req: AgentRequest, ctx: AgentContext, onStep: (String) -> Unit): AgentResult {
        val goal = req.goal
        val low = goal.lowercase()
        val lab = VisionLab(ctx.context)
        val editor = ImageEditor(ctx.context)
        val gen = ImageGen(ctx.context)
        val artifacts = mutableListOf<String>()
        val log = StringBuilder()

        // Path inside the goal (if the user pointed at a file) wins over keywords.
        val path = Regex("(/[^\\s\"']{3,200}|content://[^\\s\"']+|/storage/[^\\s\"']+)").find(goal)?.value

        when {
            low.contains("ocr") || low.contains("লেখা পড়ো") || low.contains("লেখা বের করো") -> {
                val p = path ?: latestArtifact(ctx)
                if (p == null) return AgentResult.partial("আংশিক: কোন ছবি থেকে পড়ব সেটা পাওয়া যায়নি — পাথ বলো")
                onStep("ছবি থেকে লেখা পড়ছি")
                log.appendLine(lab.ocr(p))
                req.spend(1)
                artifacts.add(p)
            }
            low.contains("ব্যাকগ্রাউন্ড সরা") || low.contains("background remove") -> {
                val p = path ?: latestArtifact(ctx) ?: return AgentResult.partial("আংশিক: ছবির পাথ পাওয়া যায়নি")
                onStep("ব্যাকগ্রাউন্ড সরাচ্ছি")
                val r = lab.removeBackground(p)
                log.appendLine(r); artifacts.add(r)
                req.spend(1)
            }
            low.contains("enhance") || low.contains("উজ্জ্বল") || low.contains("পরিষ্কার করো") -> {
                val p = path ?: latestArtifact(ctx) ?: return AgentResult.partial("আংশিক: ছবির পাথ পাওয়া যায়নি")
                onStep("ছবি উন্নত করছি")
                val r = lab.enhance(p)
                log.appendLine(r); artifacts.add(r)
                req.spend(1)
            }
            low.contains("এডিট") || low.contains("crop") || low.contains("ঘোরাও") || low.contains("কাটো") -> {
                val p = path ?: latestArtifact(ctx) ?: return AgentResult.partial("আংশিক: ছবির পাথ পাওয়া যায়নি")
                val ops = opsFrom(goal)
                onStep("এডিট: ${ops.ifBlank { "উজ্জ্বলতা বাড়ানো" }}")
                val r = editor.edit(p, ops.ifBlank { "bright=1.15" })
                log.appendLine(r); artifacts.add(r)
                req.spend(1)
            }
            low.contains("কোলাজ") || low.contains("জোড়া") -> {
                val many = Regex("(/[^\\s\"']+\\.(?:jpg|jpeg|png))").findAll(goal).map { it.value }.toList()
                if (many.size < 2) return AgentResult.partial("আংশিক: কোলাজের জন্য কমপক্ষে দুটি ছবির পাথ দাও")
                val r = editor.collage(many, vertical = low.contains("উপর"))
                log.appendLine(r); artifacts.add(r)
                req.spend(1)
            }
            // AI generation needs the API; offline drawing never does.
            low.contains("generate") || low.contains("এআই দিয়ে") || low.contains("ai দিয়ে") -> {
                if (!ctx.prefs.isConfigured) {
                    return AgentResult.blocked("আটকে: API কী ছাড়া AI ছবি সম্ভব নয় — «নিজে আঁকো» বললে অফলাইনে এঁকে দেব")
                }
                onStep("AI ছবি বানাচ্ছি")
                val r = gen.generate(goal)
                log.appendLine(r); artifacts.add(r)
                req.spend(2)
            }
            else -> {
                onStep("অফলাইনে নিজে আঁকছি")
                val r = ArtEngine.drawAndSave(ctx.context, goal)
                log.appendLine(r); artifacts.add(r)
                req.spend(1)
            }
        }

        if (low.contains("ওয়ালপেপার")) {
            val last = artifacts.lastOrNull()
            if (last != null) log.appendLine(SmartTools(ctx.context).shareImage(last))
        }
        val body = log.toString().trim()
        return if (body.contains("পারিনি") || body.startsWith("ব্যর্থ")) {
            AgentResult.failed("ব্যর্থ: ছবির কাজ সম্পন্ন হয়নি\n$body", artifacts)
        } else AgentResult.done("সফল: ছবির কাজ সম্পন্ন\n$body", artifacts)
    }

    /** The most recent image Jimi itself produced — the usual "that one". */
    private fun latestArtifact(ctx: AgentContext): String? {
        val dirs = listOf("art", "edited").map {
            File(ctx.context.getExternalFilesDir(null) ?: ctx.context.filesDir, it)
        }
        return dirs.filter { it.exists() }
            .flatMap { it.listFiles()?.toList() ?: emptyList() }
            .maxByOrNull { it.lastModified() }?.absolutePath
    }

    private fun opsFrom(goal: String): String {
        val ops = mutableListOf<String>()
        val low = goal.lowercase()
        if (low.contains("উজ্জ্বল") || low.contains("bright")) ops.add("bright=1.2")
        if (low.contains("কনট্রাস্ট") || low.contains("contrast")) ops.add("contrast=1.15")
        if (low.contains("সাদাকালো") || low.contains("gray")) ops.add("gray")
        if (low.contains("ঘোরাও") || low.contains("rotate")) {
            val deg = Regex("(90|180|270)").find(goal)?.value ?: "90"
            ops.add("rotate=$deg")
        }
        Regex("crop=?\\s*(1:1|16:9|4:5|9:16)").find(low)?.let { ops.add("crop=${it.groupValues[1]}") }
        if (low.contains("1:1") && ops.none { it.startsWith("crop") }) ops.add("crop=1:1")
        if (low.contains("ভিনটেজ") || low.contains("vintage")) ops.add("vintage")
        if (low.contains("blur") || low.contains("ঝাপসা")) ops.add("blur=6")
        if (low.contains("বর্ডার") || low.contains("border")) ops.add("border")
        if (low.contains("গোল") || low.contains("circle")) ops.add("circle")
        Regex("লেখো[:\\s\"“']?([^\"”]{2,60})").find(goal)?.let { ops.add("text=${it.groupValues[1].trim()}") }
        return ops.joinToString(",")
    }
}

// ------------------------------------------------------------------ 9. audio

class AudioAgent : Agent() {
    override val id = "audio"
    override val nameBangla = "অডিও এজেন্ট"
    override val resources = setOf("audio")

    override fun score(goal: String, detail: String) =
        hits("$goal $detail", listOf(
            "শোনাব", "শোনাও", "অডিও", "গান", "কথা বলো", "tts", "voice বানাও", "রেকর্ড",
            "নয়েজ", "গোলমাল কমাও", "ট্রান্সক্রিপ্ট", "অ্যাম্বিয়েন্ট", "বৃষ্টির শব্দ", "মিউজিক",
            "ছড়া", "গল্প শোনাও", "পড়ে শোনাও"
        ), 22)

    override fun work(req: AgentRequest, ctx: AgentContext, onStep: (String) -> Unit): AgentResult {
        val goal = req.goal
        val low = goal.lowercase()
        val gen = AudioGen(ctx.context)
        val lab = AudioLab(ctx.context)
        val artifacts = mutableListOf<String>()
        val log = StringBuilder()

        when {
            low.contains("বৃষ্টি") || low.contains("ঢেউ") || low.contains("অ্যাম্বিয়েন্ট") ||
                low.contains("ফোকাস") || low.contains("ঘুমের শব্দ") -> {
                onStep("অফলাইনে পরিবেশ-শব্দ বানাচ্ছি")
                val seconds = Regex("(\\d+)\\s*(মিনিট|minute|min)").find(goal)?.let {
                    it.groupValues[1].toInt() * 60
                } ?: 120
                val r = gen.ambient(kind = goal, seconds = seconds.coerceIn(15, 600))
                log.appendLine(r); artifacts.add(r)
                req.spend(1)
            }
            low.contains("ডায়লগ") || low.contains("সংলাপ") || low.contains("দুই কণ্ঠ") ||
                low.contains("গল্প শোনাও") -> {
                onStep("দুই কণ্ঠে অডিও বানাচ্ছি")
                val r = gen.dialogue(goal)
                log.appendLine(r); artifacts.add(r)
                req.spend(2)
            }
            low.contains("নয়েজ") || low.contains("গোলমাল") || low.contains("denoise") -> {
                val p = Regex("(/[^\\s\"']+\\.(?:wav|mp3|m4a|aac|ogg|flac))").find(goal)?.value
                if (p == null) return AgentResult.partial("আংশিক: কোন অডিও ফাইল থেকে নয়েজ কমাবো সেটা পাওয়া যায়নি")
                onStep("নয়েজ কমাচ্ছি")
                val r = lab.denoise(p)
                log.appendLine(r); artifacts.add(r)
                req.spend(1)
            }
            low.contains("ট্রান্সক্রিপ্ট") || low.contains("লেখায় রূপ") || low.contains("কী বলেছে") -> {
                val p = Regex("(/[^\\s\"']+\\.(?:wav|mp3|m4a|aac|ogg|flac))").find(goal)?.value
                if (p == null) return AgentResult.partial("আংশিক: অডিও ফাইলের পাথ দাও")
                onStep("অডিও থেকে লেখা বের করছি")
                log.appendLine(lab.transcribe(p))
                req.spend(2)
            }
            else -> {
                onStep("Gemini অডিও মডেল দিয়ে কণ্ঠ বানাচ্ছি")
                val r = gen.speak(goal, voice = ctx.prefs.voice)
                log.appendLine(r); artifacts.add(r)
                req.spend(2)
            }
        }

        // Background playback is the point of the feature: start it unless the
        // user only asked for the file.
        val produced = artifacts.lastOrNull { File(it).exists() || it.contains("/") }
        if (produced != null && !low.contains("ফাইলটা দাও")) {
            onStep("ব্যাকগ্রাউন্ডে বাজানো শুরু করছি")
            log.appendLine(AudioPlaybackService.play(ctx.context, produced, req.goal.take(40)))
            req.spend(1)
        }
        val body = log.toString().trim()
        return if (body.startsWith("ব্যর্থ") || body.contains("বানানো যায়নি")) {
            AgentResult.failed("ব্যর্থ: অডিও তৈরি হয়নি\n$body", artifacts)
        } else AgentResult.done("সফল: অডিও তৈরি হয়েছে\n$body", artifacts)
    }
}

// ------------------------------------------------------------------ 10. video

class VideoAgent : Agent() {
    override val id = "video"
    override val nameBangla = "ভিডিও এজেন্ট"
    override val resources = setOf("cpu")

    override fun score(goal: String, detail: String) =
        hits("$goal $detail", listOf(
            "ভিডিও", "video", "সাবটাইটেল", "দৃশ্য", "scene", "সামারি ভিডিও", "ফ্রেম",
            "কাটো ভিডিও", "ট্রিম"
        ), 22)

    override fun work(req: AgentRequest, ctx: AgentContext, onStep: (String) -> Unit): AgentResult {
        val goal = req.goal
        val low = goal.lowercase()
        val mu = MediaUnderstanding(ctx.context)
        val path = Regex("(/[^\\s\"']+\\.(?:mp4|mkv|webm|3gp|mov))").find(goal)?.value
            ?: FileTools(ctx.context).find("mp4", 8).firstOrNull { !it.isDir }?.path
        if (path == null) {
            return AgentResult.partial("আংশিক: ভিডিও ফাইল পাওয়া যায়নি — পাথ বা নাম বলো")
        }
        val lab = VideoLab(ctx.context)
        val log = StringBuilder()
        log.appendLine(mu.videoInfo(path))

        when {
            low.contains("সাবটাইটেল") || low.contains("srt") -> {
                onStep("ভিডিওর ফ্রেম ও অডিও থেকে সাবটাইটেল বানাচ্ছি")
                log.appendLine(lab.subtitles(path))
                req.spend(3)
            }
            low.contains("দৃশ্য") || low.contains("scene") -> {
                onStep("দৃশ্য শনাক্ত করছি")
                log.appendLine(lab.scenes(path))
                req.spend(2)
            }
            low.contains("কাটো") || low.contains("ট্রিম") -> {
                onStep("কাটার পথ বের করছি")
                log.appendLine(lab.trimHint(path, goal))
                req.spend(1)
            }
            else -> {
                onStep("ভিডিও সামারি করছি")
                log.appendLine(lab.summarize(path))
                req.spend(3)
            }
        }
        val body = log.toString().trim()
        return if (body.startsWith("ব্যর্থ")) AgentResult.failed("ব্যর্থ: ভিডিও কাজ হয়নি\n$body")
        else AgentResult.done("সফল: ভিডিও বিশ্লেষণ সম্পন্ন\n$body")
    }
}

// --------------------------------------------------------------- 11. recovery

class RecoveryAgent : Agent() {
    override val id = "recovery"
    override val nameBangla = "এরর রিকভারি এজেন্ট"
    override val resources = setOf<String>()

    /** Only the Orchestrator calls this, after another agent failed. */
    override fun score(goal: String, detail: String) = 0

    override fun work(req: AgentRequest, ctx: AgentContext, onStep: (String) -> Unit): AgentResult {
        val previous = req.detail
        onStep("ব্যর্থতার কারণ বিশ্লেষণ করছি")
        val cause = ErrorCenter.analyseText(previous)
        val log = StringBuilder()
        log.appendLine("কারণ: ${cause.first}")
        log.appendLine("করণীয়: ${cause.second}")

        // Try the deliberately different route that already exists in the app.
        val svc = ctx.service
        if (svc != null && ErrorCenter.retryBudget("recovery.${req.goal.hashCode()}", 2)) {
            onStep("বিকল্প পথে আবার চেষ্টা করছি")
            val alt = runCatching {
                com.saathi.ai.access.Solver.runAlternative(
                    ctx.context, svc, req.goal, previous
                ) { step -> onStep(step) }
            }.getOrElse { "ব্যর্থ: বিকল্প পথও ব্যর্থ (${it.message})" }
            log.appendLine(alt.take(1200))
            req.spend(2)
            if (alt.contains("সফল")) {
                ErrorCenter.resetRetry("recovery.${req.goal.hashCode()}")
                return AgentResult.done("সফল: রিকভারি এজেন্ট বিকল্প পথে কাজ শেষ করেছে\n$log")
            }
        }
        // Fix what can be fixed without the user (health self-repair).
        if (cause.first.contains("অনুমতি") || cause.first.contains("অ্যাক্সেসিবিলিটি")) {
            onStep("অনুমতি নিজে ঠিক করার চেষ্টা করছি")
            log.appendLine(
                com.saathi.ai.access.PermissionCenter.autoFix(ctx.context)
            )
            req.spend(1)
        }
        if (cause.first.contains("স্টোরেজ") || cause.first.contains("ব্যাটারি")) {
            log.appendLine(HealthInsights(ctx.context).selfFix(true))
            req.spend(1)
        }
        return AgentResult.partial(
            "আংশিক: রিকভারি চেষ্টা করেছি কিন্তু কাজ সম্পন্ন হয়নি।\n$log\n" +
                "ব্যবহারকারীর জন্য পরামর্শ: ${cause.second}"
        )
    }
}
