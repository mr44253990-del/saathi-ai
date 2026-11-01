package com.saathi.ai.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import com.saathi.ai.MainActivity
import com.saathi.ai.R
import com.saathi.ai.core.ErrorCenter
import com.saathi.ai.core.KillSwitch
import java.io.File

/**
 * Background audio playback (Phase 20).
 *
 * Generated speech and ambience keep playing when the app is closed or the screen
 * is off: a `mediaPlayback` foreground service with a notification the user can
 * pause, resume or stop from, plus audio-focus handling so a phone call ducks it
 * instead of fighting it.
 *
 * Looping is on for ambience (rain/waves/noise are meant to run) and off for
 * speech — a spoken sentence that repeats forever would be a bug, not a feature.
 */
class AudioPlaybackService : Service() {

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var currentPath: String = ""
    private var currentTitle: String = ""
    private var looping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE)?.take(60) ?: File(path).name
                looping = intent.getBooleanExtra(EXTRA_LOOP, false)
                if (path.isBlank() || !File(path).exists()) {
                    ErrorCenter.log("audio", "বাজানোর ফাইল নেই", path)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIF_ID, buildNotification(title, playing = false))
                play(path, title)
            }
            ACTION_PAUSE -> {
                runCatching { player?.takeIf { it.isPlaying }?.pause() }
                isPaused = true
                notifyChange(currentTitle, playing = false)
            }
            ACTION_RESUME -> {
                runCatching { player?.takeIf { !it.isPlaying }?.start() }
                isPaused = false
                notifyChange(currentTitle, playing = true)
            }
            ACTION_STOP, null -> {
                stopPlayback()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun play(path: String, title: String) {
        stopPlayback(keepFocus = true)
        currentPath = path
        currentTitle = title
        try {
            requestFocus()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(
                            if (looping) AudioAttributes.CONTENT_TYPE_SONIFICATION
                            else AudioAttributes.CONTENT_TYPE_SPEECH
                        )
                        .build()
                )
                setDataSource(path)
                isLooping = looping
                setOnPreparedListener {
                    it.start()
                    isPlayingNow = true
                    isPaused = false
                    notifyChange(title, playing = true)
                    ErrorCenter.info("audio", "ব্যাকগ্রাউন্ডে বাজছে: $title")
                }
                setOnCompletionListener {
                    if (!looping) {
                        isPlayingNow = false
                        notifyChange(title, playing = false)
                        stopSelf()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    ErrorCenter.log("audio", "প্লেব্যাক ত্রুটি", "what=$what extra=$extra path=$path")
                    isPlayingNow = false
                    notifyChange(title, playing = false)
                    true
                }
                prepareAsync()
            }
        } catch (t: Throwable) {
            ErrorCenter.logThrow("audio", "প্লেয়ার চালু করা যায়নি", t)
            isPlayingNow = false
            notifyChange(title, playing = false)
            stopSelf()
        }
    }

    private fun stopPlayback(keepFocus: Boolean = false) {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        isPlayingNow = false
        isPaused = false
        if (!keepFocus) abandonFocus()
    }

    override fun onDestroy() {
        stopPlayback()
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------- audio focus

    private fun requestFocus() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val listener = AudioManager.OnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        runCatching { player?.pause() }
                        isPaused = true
                        notifyChange(currentTitle, playing = false)
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        runCatching { player?.takeIf { it.isPlaying }?.pause() }
                        isPaused = true
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        if (isPaused) {
                            runCatching { player?.start() }
                            isPaused = false
                            notifyChange(currentTitle, playing = true)
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(listener)
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                // minSdk is 26 so this branch is effectively dead; kept only so the
                // code still reads correctly if the floor is ever lowered.
                am.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (t: Throwable) {
            ErrorCenter.log("audio", "অডিও ফোকাস নেওয়া যায়নি", t.message)
        }
    }

    private fun abandonFocus() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } catch (t: Throwable) {
            ErrorCenter.log("audio", "অডিও ফোকাস ছাড়া যায়নি", t.message)
        }
        focusRequest = null
    }

    // ----------------------------------------------------------- notification

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Jimi অডিও প্লেব্যাক",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ব্যাকগ্রাউন্ডে তৈরি কণ্ঠ বা পরিবেশ-শব্দ বাজানোর সময় এই নোটিফিকেশন থাকে"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(title: String, playing: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra("screen", "audio"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val act = { action: String, code: Int ->
            PendingIntent.getService(
                this, code,
                Intent(this, AudioPlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val icon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notification)
        val builder = Notification.Builder(this, CHANNEL)
        builder.setContentTitle(if (playing) "বাজছে: $title" else "থেমে আছে: $title")
            .setContentText(
                when {
                    looping -> "পরিবেশ-শব্দ চলছে (লুপে) — থামাতে STOP চাপো"
                    playing -> "ব্যাকগ্রাউন্ডে চলছে · অ্যাপ বন্ধ করলেও থামবে না"
                    else -> "পজ করা — চালাতে আবার বলো"
                }
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    icon,
                    if (playing) "পজ" else "চালাও",
                    act(if (playing) ACTION_PAUSE else ACTION_RESUME, 1)
                ).build()
            )
            .addAction(Notification.Action.Builder(icon, "থামাও", act(ACTION_STOP, 2)).build())
        return builder.build()
    }

    private fun notifyChange(title: String, playing: Boolean) {
        try {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            nm.notify(NOTIF_ID, buildNotification(title, playing))
        } catch (t: Throwable) {
            ErrorCenter.log("audio", "নোটিফিকেশন হালনাগাদ ব্যর্থ", t.message)
        }
    }

    companion object {
        private const val CHANNEL = "jimi_audio"
        private const val NOTIF_ID = 4245

        const val ACTION_PLAY = "com.saathi.ai.audio.PLAY"
        const val ACTION_PAUSE = "com.saathi.ai.audio.PAUSE"
        const val ACTION_RESUME = "com.saathi.ai.audio.RESUME"
        const val ACTION_STOP = "com.saathi.ai.audio.STOP"
        const val EXTRA_PATH = "path"
        const val EXTRA_TITLE = "title"
        const val EXTRA_LOOP = "loop"

        @Volatile var instance: AudioPlaybackService? = null
            private set
        @Volatile var isPlayingNow: Boolean = false
            private set
        @Volatile var isPaused: Boolean = false
            private set

        private var killSwitchDone = false

        /** Starts playback in the background; ambience loops, speech does not. */
        fun play(context: Context, path: String, title: String, loop: Boolean = false): String {
            val f = File(path)
            if (!f.exists()) return "ব্যর্থ: বাজানোর ফাইল পাওয়া যায়নি — $path"
            registerKillSwitch()
            val i = Intent(context, AudioPlaybackService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_PATH, path)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_LOOP, loop || looksLikeAmbience(path))
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
                "সফল: ব্যাকগ্রাউন্ডে বাজানো শুরু হয়েছে — ${f.name}" +
                    if (loop || looksLikeAmbience(path)) " (অবিরত চলবে, থামাতে বললে থামবে)" else ""
            } catch (t: Throwable) {
                ErrorCenter.logThrow("audio", "প্লেব্যাক সার্ভিস চালু ব্যর্থ", t)
                "ব্যর্থ: প্লেব্যাক শুরু করা যায়নি (${t.message}) — নোটিফিকেশন পারমিশন আছে কি না দেখো"
            }
        }

        fun pause(context: Context): String {
            send(context, ACTION_PAUSE)
            return if (isPlayingNow || isPaused) "অডিও পজ করা হয়েছে" else "বাজছিল না — পজ করার কিছু নেই"
        }

        fun resume(context: Context): String {
            send(context, ACTION_RESUME)
            return if (isPaused) "অডিও আবার চলছে" else "পজ করা ছিল না"
        }

        fun stop(context: Context): String {
            send(context, ACTION_STOP)
            return "অডিও থামানো হয়েছে"
        }

        private fun send(context: Context, action: String) {
            runCatching {
                context.startService(
                    Intent(context, AudioPlaybackService::class.java).setAction(action)
                )
            }.onFailure { ErrorCenter.log("audio", "প্লেব্যাক কমান্ড পাঠানো যায়নি", it.message) }
        }

        /** Live state for the dashboard, the bubble and the model. */
        fun status(): String = buildString {
            val inst = instance
            when {
                inst == null -> append("অডিও প্লেয়ার বন্ধ — কিছু বাজছে না")
                isPlayingNow -> append("ব্যাকগ্রাউন্ডে বাজছে (স্ক্রিন বন্ধ করলেও চলবে)")
                isPaused -> append("অডিও পজ করা আছে — «চালাও» বললে আবার চলবে")
                else -> append("প্লেয়ার চালু আছে কিন্তু এখন কিছু বাজছে না")
            }
        }

        private fun looksLikeAmbience(path: String): Boolean = path.contains("ambient")

        private fun registerKillSwitch() {
            if (killSwitchDone) return
            killSwitchDone = true
            KillSwitch.register("audio") { ctx -> stop(ctx) }
        }
    }
}
