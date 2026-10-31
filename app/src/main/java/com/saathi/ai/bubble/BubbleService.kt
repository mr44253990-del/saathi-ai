package com.saathi.ai.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.saathi.ai.MainActivity
import com.saathi.ai.R

/**
 * Owns the floating Jimi orb. The visual lives in [JimiBubble]; this service keeps
 * it alive while the user is in other apps and exposes it through a normal
 * foreground notification with two actions (open / dismiss).
 */
class BubbleService : Service() {

    private var bubble: JimiBubble? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundSafely()
        bubble = JimiBubble(this).also { it.show() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                bubble?.hide()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CAPTION -> {
                intent.getStringExtra(EXTRA_TEXT)?.let { bubble?.setCaption(it) }
            }
            ACTION_TASK -> {
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                val progress = intent.getIntExtra(EXTRA_PROGRESS, -1)
                ensureBubble()
                bubble?.setTask(text, progress)
            }
            // Background work started: the bubble comes back on its own.
            ACTION_AUTO_SHOW -> {
                autoHidden = false
                ensureBubble()
                intent.getStringExtra(EXTRA_TEXT)?.let { bubble?.setCaption(it) }
            }
            // The app is in front of the user: get out of the way.
            ACTION_AUTO_HIDE -> {
                autoHidden = true
                bubble?.hide()
            }
        }
        if (!autoHidden) ensureBubble()
        return START_STICKY
    }

    /** Shows the bubble unless the user (or the open app) has asked it to stay away. */
    private fun ensureBubble() {
        if (bubble?.isShowing != true) {
            bubble = JimiBubble(this).also { it.show() }
        }
    }

    override fun onDestroy() {
        bubble?.hide()
        bubble = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Jimi ফ্লোটিং বাবল",
                    NotificationManager.IMPORTANCE_MIN
                ).apply { setShowBadge(false) }
            )
        }
    }

    private fun startForegroundSafely() {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getService(
            this, 1,
            Intent(this, BubbleService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jimi হাতের কাছে")
            .setContentText("ট্যাপ করো অ্যাপে ফিরতে")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .addAction(R.drawable.ic_notification, "বন্ধ", dismiss)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        private const val CHANNEL_ID = "jimi_bubble"
        private const val NOTIF_ID = 4343
        const val ACTION_HIDE = "com.jimi.bubble.HIDE"
        const val ACTION_CAPTION = "com.jimi.bubble.CAPTION"
        const val ACTION_TASK = "com.jimi.bubble.TASK"
        const val ACTION_AUTO_SHOW = "com.jimi.bubble.AUTO_SHOW"
        const val ACTION_AUTO_HIDE = "com.jimi.bubble.AUTO_HIDE"
        const val EXTRA_TEXT = "text"
        const val EXTRA_PROGRESS = "progress"

        /**
         * True while the app itself is in the foreground: the bubble steps aside so it
         * does not cover Jimi's own UI, and comes back the moment background work starts.
         */
        @Volatile
        var autoHidden = false
            private set

        fun show(context: Context) {
            val intent = Intent(context, BubbleService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                // ignore: overlay permission missing
            }
        }

        fun hide(context: Context) {
            runCatching {
                context.startService(Intent(context, BubbleService::class.java).setAction(ACTION_HIDE))
            }
        }

        fun caption(context: Context, text: String) {
            runCatching {
                context.startService(
                    Intent(context, BubbleService::class.java)
                        .setAction(ACTION_CAPTION)
                        .putExtra(EXTRA_TEXT, text)
                )
            }
        }

        /** Background task progress line + ring. */
        fun task(context: Context, text: String, progress: Int) {
            runCatching {
                context.startService(
                    Intent(context, BubbleService::class.java)
                        .setAction(ACTION_TASK)
                        .putExtra(EXTRA_TEXT, text)
                        .putExtra(EXTRA_PROGRESS, progress)
                )
            }
        }

        /**
         * Called when background work starts. Uses startForegroundService because the
         * caller is usually a service itself and the app may be in the background,
         * where a plain startService is rejected on Android 8+.
         */
        fun autoShow(context: Context, text: String? = null) {
            val intent = Intent(context, BubbleService::class.java).setAction(ACTION_AUTO_SHOW)
            if (text != null) intent.putExtra(EXTRA_TEXT, text)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }

        /** Called from MainActivity.onResume — hide while the user is looking at the app. */
        fun autoHide(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, BubbleService::class.java).setAction(ACTION_AUTO_HIDE)
                )
            }
        }
    }
}
