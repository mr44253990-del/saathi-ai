package com.saathi.ai.task

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.saathi.ai.MainActivity
import com.saathi.ai.R
import com.saathi.ai.access.JimiAccessibilityService
import com.saathi.ai.access.Solver
import com.saathi.ai.bubble.BubbleService
import com.saathi.ai.live.SessionHub
import kotlin.concurrent.thread

/**
 * The background worker that actually finishes big jobs.
 *
 * What the user asked for: hand Jimi a large task and it makes a to-do list, works
 * through it in the background, ticks items off, and keeps going until it is done
 * or genuinely blocked. This service is that loop.
 *
 * Honesty is built into the loop, not bolted on:
 *  • a task is marked DONE only when the autopilot's evidence-based verdict starts
 *    with "সফল:" (screen changed *and* the goal's own words are on screen);
 *  • "আংশিক:"/"ব্যর্থ:" goes through [Solver] for a second, different attempt, and if that fails
 *    the task is BLOCKED with the real reason written into its note;
 *  • nothing is ever reported as finished because time ran out.
 *
 * Progress is published to [SessionHub] so the floating bubble and the Tasks screen
 * show the same live line ("কাজ ৩/৭ · ইউটিউবে গান চালাচ্ছি").
 */
class TaskRunnerService : Service() {

    private var worker: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var deadline = 0L

    private lateinit var board: TaskBoard

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        board = TaskBoard(this)
        createChannel()
        startForegroundSafely("টাস্ক তালিকা খালি")
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWork("ব্যবহারকারী থামিয়েছে")
                return START_NOT_STICKY
            }
            ACTION_WORK_FOR -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 120)
                val goal = intent.getStringExtra(EXTRA_GOAL).orEmpty()
                deadline = System.currentTimeMillis() + seconds * 1000L
                if (goal.isNotBlank()) {
                    val t = board.add(goal, "টাইমার: $seconds সেকেন্ড ধরে চেষ্টা চলবে")
                    publish("টাইমার সেট: $seconds সেকেন্ড")
                    startWorker(t.id)
                } else {
                    startWorker(null)
                }
            }
            else -> startWorker(intent?.getStringExtra(EXTRA_TASK_ID))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        isRunning = false
        worker?.interrupt()
        super.onDestroy()
    }

    // ------------------------------------------------------------------- loop

    private fun startWorker(onlyTaskId: String?) {
        if (running && worker?.isAlive == true) return
        running = true
        worker = thread(name = "jimi-task-runner", isDaemon = true) {
            try {
                if (onlyTaskId != null) runOne(onlyTaskId)
                drain()
            } catch (t: Throwable) {
                publish("টাস্ক রানার থেমেছে: ${t.message}")
            } finally {
                running = false
                val left = board.all().count { it.status == TaskBoard.Status.PENDING }
                publish(if (left == 0) "সব কাজ শেষ — তালিকা পরিষ্কার" else "বাকি ${left}টি কাজ")
                if (left == 0) stopSelf()
            }
        }
    }

    private fun drain() {
        var guard = 0
        while (running && guard++ < 60) {
            if (deadline in 1 until System.currentTimeMillis()) {
                publish("টাইমার শেষ — যা হয়েছিল তার রিপোর্ট দিচ্ছি")
                break
            }
            val next = board.nextPending() ?: break
            runOne(next.id)
        }
    }

    private fun runOne(id: String) {
        val task = board.all().firstOrNull { it.id == id } ?: return
        val goal = listOf(task.title, task.detail).filter { it.isNotBlank() }.joinToString(" — ")

        val svc = JimiAccessibilityService.instance
        if (svc == null) {
            board.update(id, TaskBoard.Status.BLOCKED, "Accessibility বন্ধ — স্ক্রিন চালানো যাচ্ছে না")
            publish("আটকেছে: Accessibility বন্ধ")
            return
        }

        board.update(id, TaskBoard.Status.RUNNING, "")
        publish("চলছে: ${task.title}")
        notify("কাজ চলছে", task.title)

        var report = Solver.runWithEscalation(this, svc, goal) { step ->
            publish(step)
            notify("কাজ চলছে", step)
        }

        val verdict = Solver.classify(report)
        when (verdict) {
            Solver.Verdict.DONE -> {
                board.update(id, TaskBoard.Status.DONE, Solver.oneLine(report))
                publish("সম্পন্ন: ${task.title}")
            }
            Solver.Verdict.PARTIAL, Solver.Verdict.FAILED -> {
                // one genuinely different attempt before giving up
                if (task.attempts < 2) {
                    publish("আবার অন্য পথে চেষ্টা করছি: ${task.title}")
                    report = Solver.runAlternative(this, svc, goal, report) { step -> publish(step) }
                    if (Solver.classify(report) == Solver.Verdict.DONE) {
                        board.update(id, TaskBoard.Status.DONE, Solver.oneLine(report))
                        publish("সম্পন্ন (দ্বিতীয় চেষ্টায়): ${task.title}")
                        return
                    }
                }
                board.update(id, TaskBoard.Status.FAILED, Solver.oneLine(report))
                publish("হয়নি: ${task.title} — ${Solver.oneLine(report)}")
            }
            Solver.Verdict.BLOCKED -> {
                board.update(id, TaskBoard.Status.BLOCKED, Solver.oneLine(report))
                publish("আটকেছে: ${task.title}")
            }
        }
        notify("টাস্ক রানার", board.progressText())
    }

    private fun stopWork(reason: String) {
        running = false
        board.running()?.let { board.update(it.id, TaskBoard.Status.PENDING, reason) }
        publish("থামানো হয়েছে: $reason")
        stopSelf()
    }

    // ----------------------------------------------------------------- output

    private fun publish(line: String) {
        val progress = board.progressPercent()
        SessionHub.update {
            it.copy(taskText = line, taskProgress = progress)
        }
        // the bubble is how background work stays visible while the user is in
        // another app — it turns itself on and prints the step inside itself
        BubbleService.autoShow(this, null)
        BubbleService.task(this, line, progress)
    }

    private fun notify(title: String, text: String) {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra("screen", "tasks"),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, board.progressPercent(), false)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Jimi ব্যাকগ্রাউন্ড কাজ", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
    }

    private fun startForegroundSafely(text: String) {
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jimi কাজ করছে")
            .setContentText(text)
            .setSilent(true)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
            } else {
                startForeground(NOTIF_ID, n)
            }
        }
    }

    companion object {
        private const val CHANNEL = "jimi_tasks"
        private const val NOTIF_ID = 4243

        const val ACTION_STOP = "com.saathi.ai.task.STOP"
        const val ACTION_WORK_FOR = "com.saathi.ai.task.WORK_FOR"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_GOAL = "goal"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context, taskId: String? = null) {
            val i = Intent(context, TaskRunnerService::class.java)
            if (taskId != null) i.putExtra(EXTRA_TASK_ID, taskId)
            launch(context, i)
        }

        /** Keeps working on a goal for [seconds] — the "set a timer and grind" mode. */
        fun workFor(context: Context, goal: String, seconds: Int) {
            val i = Intent(context, TaskRunnerService::class.java)
                .setAction(ACTION_WORK_FOR)
                .putExtra(EXTRA_GOAL, goal)
                .putExtra(EXTRA_SECONDS, seconds.coerceIn(15, 3600))
            launch(context, i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TaskRunnerService::class.java).setAction(ACTION_STOP))
        }

        private fun launch(context: Context, intent: Intent) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }
    }
}
