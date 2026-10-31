package com.saathi.ai.access

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Terminal power — Jimi gets a real shell.
 *
 * Three levels, always tried in this order:
 *  1. **Plain `sh`** — what any app can do (`ls`, `getprop`, `dumpsys` if allowed).
 *  2. **`su -c`** through [RootBridge] — everything: `pm`, `am`, `input`, `settings`,
 *     `screencap`, `logcat`, package installs, editing other apps' files.
 *  3. **Termux** through its documented `RUN_COMMAND` intent — a full Linux userland
 *     (apt, python, node, ffmpeg, git, yt-dlp…) without rooting the phone at all.
 *
 * The point is not "run a command"; it is that Jimi **reads the result and keeps
 * going**: [selfDrive] inspects the output, recognises the usual failures
 * (command not found → install it, permission denied → retry as root, empty
 * result → widen the search) and issues the next command itself, up to a limit.
 */
class TerminalTools(private val context: Context) {

    data class Job(val id: Int, val command: String, val startedAt: Long)

    private val jobIds = AtomicInteger(1)
    private val jobs = ConcurrentLinkedDeque<Job>()

    // ------------------------------------------------------------------ shell

    /** Runs one command and returns an honest, trimmed report. */
    fun shell(command: String, timeoutMs: Long = 25_000, preferRoot: Boolean = true): String {
        val rooted = RootBridge.available()
        val useRoot = preferRoot && rooted
        val r = RootBridge.exec(command, timeoutMs, useRoot)
        val mode = when {
            useRoot -> "su"
            rooted -> "sh (রুট আছে কিন্তু এটা সাধারণ শেল)"
            else -> "sh"
        }
        val body = r.report(2600)
        return "[$mode] \$ $command\n$body"
    }

    /** Background job: returns immediately, output lands in a file we can read later. */
    fun background(command: String, tag: String): String {
        val id = jobIds.getAndIncrement()
        val out = File(context.getExternalFilesDir(null) ?: context.filesDir, "term/$tag-$id.log")
        out.parentFile?.mkdirs()
        val wrapped = "nohup sh -c \"${command.replace("\"", "\\\"")}\" > '${out.absolutePath}' 2>&1 & echo \$!"
        val r = RootBridge.exec(wrapped, 12_000, RootBridge.available())
        val pid = r.out.trim().lines().lastOrNull()?.takeWhile { it.isDigit() }.orEmpty()
        jobs.addLast(Job(id, command, System.currentTimeMillis()))
        while (jobs.size > 12) jobs.removeFirst()
        return if (pid.isBlank()) {
            "ব্যাকগ্রাউন্ডে চালানো যায়নি: ${r.err.ifBlank { r.out }}"
        } else {
            "ব্যাকগ্রাউন্ডে চলছে (job #$id, pid $pid) — লগ: ${out.absolutePath}\n" +
                "পরে job_output($id) দিয়ে ফল দেখো।"
        }
    }

    fun jobOutput(id: Int, tail: Int = 40): String {
        val job = jobs.firstOrNull { it.id == id } ?: return "job #$id পাওয়া যায়নি"
        val f = File(context.getExternalFilesDir(null) ?: context.filesDir, "term")
        val log = f.listFiles()?.firstOrNull { it.name.startsWith("job-$id") || it.name.contains("-$id.") }
        val text = log?.let {
            RootBridge.exec("tail -n $tail '${it.absolutePath}'", 8_000, false).out
        }.orEmpty()
        return "job #$id: ${job.command}\n${text.ifBlank { "(এখনো আউটপুট নেই)" }}"
    }

    fun listJobs(): String =
        if (jobs.isEmpty()) "কোনো ব্যাকগ্রাউন্ড জব নেই"
        else jobs.joinToString("\n") { "#${it.id} · ${it.command.take(90)}" }

    // ----------------------------------------------------------------- Termux

    fun termuxInstalled(): Boolean =
        context.packageManager.getLaunchIntentForPackage(TERMUX_PKG) != null ||
            File("/data/data/com.termux/files/usr/bin").exists()

    /**
     * Runs a command inside Termux using its official RUN_COMMAND API.
     * Results come back asynchronously to [TermuxResultReceiver]; read them with
     * [termuxResult]. Needs `allow-external-apps=true` in Termux's properties —
     * [enableTermuxExternalApps] can set that when root is available.
     */
    fun runInTermux(command: String, workdir: String? = null, background: Boolean = true): String {
        if (!termuxInstalled()) {
            return "Termux ইনস্টল নেই। Play Store/F-Droid থেকে Termux ইনস্টল করো, " +
                "নইলে রুট শেল ব্যবহার করব।"
        }
        val binDir = "/data/data/com.termux/files/usr/bin"
        val exe = when {
            command.startsWith("/") -> command.substringBefore(' ')
            else -> "$binDir/bash"
        }
        val args = when {
            command.startsWith("/") -> command.substringAfter(' ', "").let {
                if (it.isBlank()) arrayOf<String>() else it.split(' ').toTypedArray()
            }
            else -> arrayOf("-c", command)
        }

        return try {
            val intent = Intent(RUN_COMMAND_ACTION).apply {
                setClassName(TERMUX_PKG, "$TERMUX_PKG.app.RunCommandService")
                putExtra("$TERMUX_PKG.RUN_COMMAND_PATH", exe)
                putExtra("$TERMUX_PKG.RUN_COMMAND_ARGUMENTS", args)
                putExtra(
                    "$TERMUX_PKG.RUN_COMMAND_WORKDIR",
                    workdir ?: "/data/data/com.termux/files/home"
                )
                putExtra("$TERMUX_PKG.RUN_COMMAND_BACKGROUND", background)
                putExtra("$TERMUX_PKG.RUN_COMMAND_SESSION_ACTION", "0")
                putExtra("$TERMUX_PKG.RUN_COMMAND_PENDING_INTENT", resultPendingIntent())
                putExtra("$TERMUX_PKG.RUN_COMMAND_PLUGIN_API_HELP", HELP_URL)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            TermuxResults.expect(command)
            "Termux-এ পাঠিয়েছি: $command\nফল পেতে termux_result ডাকো " +
                "(প্রথমবার Termux-এ allow-external-apps=true লাগতে পারে)।"
        } catch (t: Throwable) {
            val msg = t.message.orEmpty()
            if (msg.contains("permission", true)) {
                "Termux অনুমতি দেয়নি — AndroidManifest-এ com.termux.permission.RUN_COMMAND লাগবে " +
                    "এবং Termux-এ allow-external-apps=true। এখন রুট/সাধারণ শেলে চালাচ্ছি:\n" +
                    shell(command)
            } else {
                "Termux-এ চালানো যায়নি ($msg) — বিকল্প শেলে চালালাম:\n" + shell(command)
            }
        }
    }

    private fun resultPendingIntent(): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            context, 7781, Intent(context, TermuxResultReceiver::class.java), flags
        )
    }

    fun termuxResult(): String = TermuxResults.latest()

    /**
     * Termux refuses external intents unless the user opted in. With root we can
     * flip that switch for them — that is the difference between "install Termux
     * and edit a config file" and "it just works".
     */
    fun enableTermuxExternalApps(): String {
        if (!RootBridge.available()) {
            return "রুট নেই। Termux-এ নিজে করো: ~/.termux/termux.properties ফাইলে " +
                "allow-external-apps=true লিখে termux-reload-settings চালাও।"
        }
        val props = "/data/data/com.termux/files/home/.termux/termux.properties"
        RootBridge.exec("mkdir -p /data/data/com.termux/files/home/.termux", 8_000)
        val has = RootBridge.exec("grep -c '^allow-external-apps=true' '$props' 2>/dev/null || echo 0", 8_000)
        if (has.out.trim().startsWith("0")) {
            RootBridge.exec(
                "sed -i 's/^#\\?allow-external-apps=.*/allow-external-apps=true/' '$props' 2>/dev/null; " +
                    "grep -q '^allow-external-apps=true' '$props' 2>/dev/null || " +
                    "echo 'allow-external-apps=true' >> '$props'", 10_000
            )
            RootBridge.exec("chmod 600 '$props'; chown -R $(stat -c %u:%g /data/data/com.termux) " +
                "/data/data/com.termux/files/home/.termux 2>/dev/null || true", 8_000)
        }
        val check = RootBridge.exec("grep '^allow-external-apps' '$props' 2>/dev/null", 6_000).out.trim()
        return if (check.contains("true")) {
            "Termux-এ allow-external-apps=true করে দিয়েছি (রুট দিয়ে)। " +
                "এবার Termux একবার খুলে বন্ধ করলেই বাইরের কমান্ড চলবে।"
        } else {
            "টার্গেট ফাইল পাওয়া যায়নি — Termux একবার খোলো, তারপর আবার চেষ্টা করব।"
        }
    }

    fun openTermux(): String {
        val i = context.packageManager.getLaunchIntentForPackage(TERMUX_PKG)
            ?: return "Termux ইনস্টল নেই"
        return try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Termux খুলেছি"
        } catch (t: Throwable) {
            "Termux খোলা যায়নি (${t.message})"
        }
    }

    /** Types a command into a visible Termux window through accessibility. */
    fun typeIntoTermux(command: String, pressEnter: Boolean = true): String {
        val svc = JimiAccessibilityService.instance ?: return "Accessibility বন্ধ"
        openTermux()
        Thread.sleep(1600)
        val typed = svc.typeText(command)
        if (!typed) return "টার্মিনালে লেখা যায়নি"
        Thread.sleep(300)
        if (pressEnter) svc.pressEnter()
        return "টার্মিনালে লিখে এন্টার দিয়েছি: $command"
    }

    // ------------------------------------------------------------- self-drive

    /**
     * Runs a goal in the shell the way a person would: execute, read the output,
     * fix the obvious problem, try again — up to [maxRounds] times. It never stops
     * at the first "not found" or "permission denied".
     */
    fun selfDrive(command: String, maxRounds: Int = 4): String {
        val log = StringBuilder()
        var cmd = command
        var round = 0
        var asRoot = RootBridge.available()

        while (round < maxRounds) {
            round++
            val r = RootBridge.exec(cmd, 25_000, asRoot)
            log.appendLine("── রাউন্ড $round: \$ $cmd")
            log.appendLine(r.report(1200))

            if (r.ok && r.out.isNotBlank()) {
                log.appendLine("ফলাফল পেয়েছি, থামছি।")
                break
            }
            val text = (r.out + " " + r.err).lowercase()
            val next = when {
                text.contains("not found") && !asRoot && RootBridge.available() -> {
                    asRoot = true
                    "রুট দিয়ে আবার চালাচ্ছি"
                }
                text.contains("not found") && cmd.contains("apt") -> {
                    cmd = cmd.replace("apt ", "apt-get -y ").replace("apt-get -y install", "pkg install -y")
                    "বিকল্প প্যাকেজ কমান্ড"
                }
                text.contains("not found") -> {
                    val bin = text.substringBefore(":").substringAfterLast(' ').trim()
                    if (bin.isNotBlank() && !bin.contains('/')) {
                        cmd = "command -v $bin || ls /system/bin/$bin /data/data/com.termux/files/usr/bin/$bin 2>&1"
                        "কমান্ডটা কোথায় আছে খুঁজছি"
                    } else null
                }
                text.contains("permission denied") && !asRoot && RootBridge.available() -> {
                    asRoot = true; "রুট দিয়ে আবার"
                }
                text.contains("operation not permitted") -> {
                    cmd = "setenforce 0 2>/dev/null; $cmd"; "SELinux থিমিয়ে আবার"
                }
                text.contains("no space left") -> {
                    cmd = "df -h /data | tail -1; du -sh /data/local/tmp 2>/dev/null"
                    "জায়গা দেখছি"
                }
                r.out.isBlank() && r.err.isBlank() -> {
                    cmd = "$cmd; echo EXIT=\$?"
                    "আউটপুট খালি — exit code দেখছি"
                }
                else -> null
            }
            if (next == null) {
                log.appendLine("আর বুদ্ধি খাটানোর মতো কিছু নেই — সত্যি রিপোর্ট দিচ্ছি।")
                break
            }
            log.appendLine("পরবর্তী চেষ্টা: $next")
        }
        return log.toString().take(3600)
    }

    // ------------------------------------------------------------- diagnostics

    /** Everything Jimi can learn about the phone from a shell — used by health/diag. */
    fun diagnostics(): String = buildString {
        appendLine("রুট: ${if (RootBridge.available()) "আছে" else "নেই"}")
        appendLine("Termux: ${if (termuxInstalled()) "ইনস্টল আছে" else "নেই"}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), মডেল ${Build.MODEL}")
        appendLine(RootBridge.exec("df -h /data 2>/dev/null | tail -1", 8_000, false).out.trim())
        appendLine(RootBridge.exec("cat /proc/meminfo 2>/dev/null | head -3", 8_000, false).out.trim())
        appendLine(RootBridge.exec("dumpsys battery 2>/dev/null | grep -E 'level|status|temperature'", 10_000, false).out.trim())
    }

    companion object {
        const val TERMUX_PKG = "com.termux"
        const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        const val HELP_URL = "https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent"
    }
}

/** Collects the async results Termux sends back through our PendingIntent. */
class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        val result = intent.getBundleExtra("com.termux.RUN_COMMAND_RESULT")
        val stdout = result?.getString("com.termux.RUN_COMMAND_STDOUT").orEmpty()
        val stderr = result?.getString("com.termux.RUN_COMMAND_STDERR").orEmpty()
        val code = result?.getInt("com.termux.RUN_COMMAND_EXIT_CODE", -1) ?: -1
        val err = result?.getString("com.termux.RUN_COMMAND_ERRMSG").orEmpty()
        TermuxResults.store(stdout, stderr, code, err)
    }
}

/** Small ring buffer so a later tool call can read what Termux returned. */
object TermuxResults {
    private data class Entry(
        val command: String,
        val at: Long,
        val stdout: String,
        val stderr: String,
        val code: Int,
        val errmsg: String
    )

    private val entries = ConcurrentLinkedDeque<Entry>()
    @Volatile
    private var pendingCommand: String = ""

    fun expect(command: String) {
        pendingCommand = command
    }

    fun store(stdout: String, stderr: String, code: Int, errmsg: String) {
        entries.addLast(
            Entry(pendingCommand, System.currentTimeMillis(), stdout, stderr, code, errmsg)
        )
        while (entries.size > 8) entries.removeFirst()
    }

    fun latest(): String {
        val e = entries.lastOrNull()
            ?: return "Termux থেকে এখনো কোনো ফল আসেনি। কমান্ড পাঠানোর পর ২-৩ সেকেন্ড লাগতে পারে; " +
                "নতুবা allow-external-apps=true সেট আছে কি না দেখো।"
        val head = "Termux ফল (exit ${e.code}) · ${e.command.take(80)}"
        val body = buildString {
            if (e.stdout.isNotBlank()) appendLine(e.stdout.trim().take(2000))
            if (e.stderr.isNotBlank()) appendLine("stderr: " + e.stderr.trim().take(800))
            if (e.errmsg.isNotBlank()) appendLine("বার্তা: " + e.errmsg)
        }
        return "$head\n${body.ifBlank { "(আউটপুট খালি)" }}"
    }
}
