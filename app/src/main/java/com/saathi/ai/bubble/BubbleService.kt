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
 * Keeps the floating Jimi orb alive while the user is in other apps.
 * The bubble itself lives in [JimiBubble]; this service only owns its lifecycle.
 */
class BubbleService : Service() {

    private var bubble: JimiBubble? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundSafely()
        bubble = JimiBubble(
            context = this,
            onTap = {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
            },
            onLongPress = {
                bubble?.hide()
                stopSelf()
            }
        ).also { it.show() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            bubble?.hide()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CAPTION) {
            intent.getStringExtra(EXTRA_TEXT)?.let { bubble?.setCaption(it) }
        }
        return START_STICKY
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
                    "Jimi floating bubble",
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
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Jimi")
            .setContentText("Bubble চালু আছে — ট্যাপ করে অ্যাপে ফিরো")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        private const val CHANNEL_ID = "jimi_bubble"
        private const val NOTIF_ID = 4343
        const val ACTION_HIDE = "com.jimi.HIDE"
        const val ACTION_CAPTION = "com.jimi.CAPTION"
        const val EXTRA_TEXT = "text"

        fun show(context: Context) {
            val intent = Intent(context, BubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            context.startService(
                Intent(context, BubbleService::class.java).setAction(ACTION_HIDE)
            )
        }
    }
}
