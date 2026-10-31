package com.saathi.ai.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.saathi.ai.MainActivity
import com.saathi.ai.R
import com.saathi.ai.audio.MicStream
import com.saathi.ai.audio.PcmPlayer
import com.saathi.ai.data.SecurePrefs
import com.saathi.ai.live.LiveClient
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionHub
import com.saathi.ai.live.ToolExecutor
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Owns the whole realtime pipeline:
 *   mic (16 kHz PCM)  ──►  Gemini Live WebSocket  ──►  speaker (24 kHz PCM)
 *   camera / screen (JPEG ≤1 fps) ──► same WebSocket
 *
 * It is a foreground service so the conversation keeps running while the user
 * opens other apps or shares the screen.
 */
class LiveSessionService : Service(), LiveClient.Listener {

    private lateinit var prefs: SecurePrefs
    private var client: LiveClient? = null
    private var mic: MicStream? = null
    private var player: PcmPlayer? = null
    private var screenSource: ScreenFrameSource? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var toolExecutor: ToolExecutor
    private val toolPool = Executors.newSingleThreadExecutor()

    private var resumeHandle: String? = null

    /**
     * Idle parking: a silent session is closed on purpose and a cheap microphone
     * energy gate ([com.saathi.ai.live.VoiceGate]) waits for speech. The moment
     * someone talks, the session reconnects by itself — that is the
     * "অনেকক্ষণ চুপ থাকলে ডিসকানেক্ট, বললেই আবার অটো কানেক্ট" setting.
     */
    private var parked = false
    private var lastVoiceAt = System.currentTimeMillis()
    private var gate: com.saathi.ai.live.VoiceGate? = null
    private val idleCheck = object : Runnable {
        override fun run() {
            maybePark()
            handler.postDelayed(this, 5000)
        }
    }
    private var reconnectAttempts = 0
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null

    @Volatile
    private var ending = false

    @Volatile
    private var wantMic = true

    @Volatile
    private var projectionTypeAdded = false

    @Volatile
    private var cameraTypeAdded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = SecurePrefs(this)
        SessionHub.isServiceRunning = true
        createNotificationChannel()
        startForegroundSafely()

        toolExecutor = ToolExecutor(this)

        SessionHub.cameraFrameSink = { jpeg ->
            ensureCameraType()
            client?.sendVideoFrame(jpeg)
        }
        SessionHub.update { it.copy(model = prefs.model) }

        player = PcmPlayer().also { it.start() }
        mic = MicStream(
            onChunk = { chunk -> client?.sendAudio(chunk) },
            onLevel = { level ->
                SessionHub.update { st ->
                    if (st.phase == Phase.LISTENING) st.copy(level = level)
                    else st.copy(level = level * 0.6f)
                }
            }
        )

        connect()
        startWatcher()
    }

    private fun connect() {
        val apiKey = prefs.apiKey
        if (apiKey.isBlank()) {
            fail(getString(R.string.error_no_key))
            return
        }

        SessionHub.update { it.copy(phase = Phase.CONNECTING, error = null, model = prefs.model) }
        updateNotification(getString(R.string.status_connecting))

        val cfg = LiveClient.Config(
            apiKey = apiKey,
            model = prefs.model,
            voice = prefs.voice,
            systemPrompt = prefs.buildSystemPrompt(),
            resumeHandle = resumeHandle,
            searchGrounding = prefs.searchGrounding,
            enableTools = true
        )

        client?.close()
        client = LiveClient(this).also { it.connect(cfg) }
    }

    private fun startWatcher() {
        // Keeps the UI phase in sync with the actual speaker queue.
        handler.postDelayed(object : Runnable {
            override fun run() {
                val p = player ?: return
                SessionHub.update { st ->
                    if (st.phase == Phase.SPEAKING && !p.isSpeaking) {
                        st.copy(phase = Phase.LISTENING, level = 0f)
                    } else st
                }
                handler.postDelayed(this, 300)
            }
        }, 300)
    }

    // ---------------------------------------------------------------- LiveClient.Listener

    override fun onSetupComplete() {
        reconnectAttempts = 0
        parked = false
        lastVoiceAt = System.currentTimeMillis()
        gate?.stop()
        gate = null
        SessionHub.update { it.copy(phase = Phase.LISTENING, error = null, micOn = wantMic) }
        updateNotification(getString(R.string.status_listening))
        syncMic()
        if (prefs.autoReconnectVoice && prefs.idleParkSeconds > 0) {
            handler.removeCallbacks(idleCheck)
            handler.postDelayed(idleCheck, 5000)
        }
    }

    private var currentChatId: String? = null

    override fun onAudio(pcm: ByteArray) {
        player?.enqueue(pcm)
        SessionHub.update { st ->
            if (st.phase != Phase.SPEAKING) st.copy(phase = Phase.SPEAKING) else st
        }
    }

    override fun onInputTranscript(text: String) {
        lastVoiceAt = System.currentTimeMillis()
        SessionHub.update { st ->
            val merged = merge(st.userText, text)
            st.copy(userText = merged, phase = if (st.phase == Phase.IDLE) Phase.LISTENING else st.phase)
        }
    }

    override fun onOutputTranscript(text: String) {
        SessionHub.update { st -> st.copy(modelText = merge(st.modelText, text)) }
    }

    override fun onTurnComplete() {
        lastVoiceAt = System.currentTimeMillis()
        player?.flush()

        // Persist the exchange before clearing it — this is what fills the chat
        // history screen and what "নতুন চ্যাট" starts fresh from.
        val st = SessionHub.state.value
        runCatching {
            val store = com.saathi.ai.data.ChatStore(this)
            val id = currentChatId ?: store.newChat().id.also { currentChatId = it }
            if (st.userText.isNotBlank()) store.append(id, "user", st.userText)
            if (st.modelText.isNotBlank()) store.append(id, "assistant", st.modelText)
        }

        SessionHub.update { it.copy(phase = Phase.LISTENING, userText = "", modelText = "", level = 0f) }
        updateNotification(getString(R.string.status_listening))
    }

    override fun onInterrupted() {
        player?.flush()
        SessionHub.update { it.copy(phase = Phase.LISTENING) }
    }

    override fun onToolCall(id: String, name: String, args: JSONObject) {
        // Tools can block (sleep while a screen renders), so never run them on
        // the WebSocket reader thread.
        SessionHub.update { it.copy(lastTool = name) }
        toolPool.execute {
            val result = toolExecutor.execute(name, args)
            client?.sendToolResponse(id, name, result)
        }
    }

    override fun onResumptionToken(token: String) {
        resumeHandle = token
    }

    override fun onGoAway() {
        // The server is about to drop the socket; reconnect with the resume handle.
        scheduleReconnect(delayMs = 300)
    }

    override fun onError(message: String) {
        SessionHub.update { it.copy(phase = Phase.ERROR, error = message) }
        updateNotification(getString(R.string.status_error))
    }

    override fun onClosed(code: Int, reason: String) {
        player?.flush()
        if (ending) {
            SessionHub.update { it.copy(phase = Phase.ENDED) }
            stopSelf()
            return
        }
        // ~10 minute connection limit or a network blip: resume transparently.
        scheduleReconnect(delayMs = 900L + reconnectAttempts * 700L)
    }

    private fun scheduleReconnect(delayMs: Long) {
        if (ending) return
        reconnectAttempts++
        SessionHub.update { it.copy(phase = Phase.RECONNECTING) }
        updateNotification(getString(R.string.status_reconnecting))
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (!ending) {
                SessionHub.update { it.copy(phase = Phase.CONNECTING) }
                connect()
            }
        }
        reconnectRunnable = r
        handler.postDelayed(r, delayMs)
    }

    private fun fail(message: String) {
        SessionHub.update { it.copy(phase = Phase.ERROR, error = message) }
        updateNotification(getString(R.string.status_error))
    }

    // ---------------------------------------------------------------- controls

    fun setMicEnabled(enabled: Boolean) {
        wantMic = enabled
        if (enabled) lastVoiceAt = System.currentTimeMillis()
        syncMic()
    }

    /** Closes an idle connection and starts listening for the first words. */
    private fun maybePark() {
        if (parked || ending) return
        val limit = prefs.idleParkSeconds
        if (!prefs.autoReconnectVoice || limit <= 0) return
        if (System.currentTimeMillis() - lastVoiceAt < limit * 1000L) return
        if (SessionHub.state.value.phase == Phase.SPEAKING) return

        parked = true
        mic?.stop()          // release the mic so the gate can own it
        client?.close()
        client = null
        SessionHub.update {
            it.copy(
                phase = Phase.IDLE, micOn = false, level = 0f,
                bubbleMsg = "চুপ আছি — তুমি বললেই আবার যুক্ত হচ্ছি"
            )
        }
        updateNotification("পার্ক করা · বললেই আবার শুনব")
        gate = com.saathi.ai.live.VoiceGate(this) {
            handler.post { resumeFromPark() }
        }.also { it.start() }
        if (gate?.isRunning != true) {
            // Microphone unavailable — do not pretend to be waiting.
            parked = false
            updateNotification(getString(R.string.status_error))
        }
    }

    /** Re-opens the session the instant speech energy is detected. */
    private fun resumeFromPark() {
        if (!parked || ending) return
        parked = false
        gate?.stop()
        gate = null
        lastVoiceAt = System.currentTimeMillis()
        handler.removeCallbacks(idleCheck)
        SessionHub.update { it.copy(phase = Phase.CONNECTING, error = null) }
        updateNotification(getString(R.string.status_reconnecting))
        connect()
        handler.postDelayed(idleCheck, 8000)
    }

    /** Called from Settings: toggling the option re-arms or disarms parking. */
    fun setAutoReconnect(enabled: Boolean) {
        if (enabled) {
            lastVoiceAt = System.currentTimeMillis()
            if (client != null && !parked) {
                handler.removeCallbacks(idleCheck)
                handler.postDelayed(idleCheck, 5000)
            } else if (parked) {
                resumeFromPark()
            }
        } else {
            handler.removeCallbacks(idleCheck)
            gate?.stop()
            gate = null
            if (parked) {
                parked = false
                connect()
            }
        }
    }

    private fun syncMic() {
        val enabled = wantMic
        SessionHub.update { it.copy(micOn = enabled) }
        if (enabled) {
            if (client?.isReady == true) mic?.start()
        } else {
            mic?.stop()
            client?.sendAudioStreamEnd()
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        SessionHub.update { it.copy(userText = text, phase = Phase.THINKING) }
        client?.sendText(text)
    }

    fun startScreenShare(resultCode: Int, data: Intent?): Boolean {
        if (data == null) return false
        ensureProjectionType()
        val source = ScreenFrameSource(this)
        val ok = source.start(
            resultCode,
            data,
            onFrame = { jpeg -> client?.sendVideoFrame(jpeg) },
            onStopped = {
                SessionHub.update { it.copy(screenOn = false) }
                screenSource = null
            }
        )
        if (ok) {
            screenSource = source
            SessionHub.update { it.copy(screenOn = true) }
        }
        return ok
    }

    fun stopScreenShare() {
        screenSource?.stop()
        screenSource = null
        SessionHub.update { it.copy(screenOn = false) }
    }

    fun endSession() {
        ending = true
        teardown()
        SessionHub.update { it.copy(phase = Phase.ENDED, micOn = false, cameraOn = false, screenOn = false) }
        stopSelf()
    }

    private fun teardown() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacks(idleCheck)
        handler.removeCallbacksAndMessages(null)
        gate?.stop()
        gate = null
        parked = false
        mic?.stop()
        mic = null
        player?.stop()
        player = null
        screenSource?.stop()
        screenSource = null
        client?.close()
        client = null
        runCatching { toolPool.shutdownNow() }
        SessionHub.cameraFrameSink = null
        SessionHub.isServiceRunning = false
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        runCatching { teardown() }
        instance = null
        super.onDestroy()
    }

    private fun merge(existing: String, chunk: String): String {
        if (chunk.isEmpty()) return existing
        // Gemini streams transcription deltas; append with a space when needed.
        val needsSpace = existing.isNotEmpty() &&
            !existing.endsWith(" ") && !chunk.startsWith(" ") &&
            !existing.endsWith("\n") && chunk.first().isLetterOrDigit()
        val joined = if (needsSpace) existing + " " + chunk.trimStart() else existing + chunk
        return joined.takeLast(MAX_TRANSCRIPT_CHARS).trimStart()
    }

    // ---------------------------------------------------------------- foreground notification

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel)
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun startForegroundSafely() {
        val notification = buildNotification(getString(R.string.status_connecting))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Only declare the microphone type up-front. Projection/camera types are
            // added later by [ensureProjectionType] / [ensureCameraType] — on Android 14+
            // declaring a type before its permission/consent exists throws.
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "saathi:session").apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L)
        }
    }

    private fun updateNotification(text: String) {
        runCatching {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.notify(NOTIF_ID, buildNotification(text))
        }
    }

    /**
     * Add the `mediaProjection` foreground type once the user has actually given
     * consent (required on Android 14+ before a MediaProjection can be created).
     */
    private fun ensureProjectionType() {
        if (projectionTypeAdded) return
        projectionTypeAdded = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                startForeground(
                    NOTIF_ID,
                    buildNotification(getString(R.string.status_listening)),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }
        }
    }

    /** Add the `camera` foreground type while camera frames are being streamed. */
    private fun ensureCameraType() {
        if (cameraTypeAdded) return
        cameraTypeAdded = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                startForeground(
                    NOTIF_ID,
                    buildNotification(getString(R.string.status_listening)),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, LiveSessionService::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_stop), stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_END -> endSession()
            ACTION_START -> if (client == null) connect()
            ACTION_MIC_OFF -> setMicEnabled(false)
            ACTION_MIC_ON -> setMicEnabled(true)
            ACTION_SCREEN_STOP -> stopScreenShare()
            ACTION_SCREEN_START -> {
                val code = intent.getIntExtra(EXTRA_SCREEN_CODE, 0)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(EXTRA_SCREEN_DATA)
                startScreenShare(code, data)
            }
            ACTION_TEXT -> sendText(intent.getStringExtra(EXTRA_TEXT).orEmpty())
            // A bare start — digital assistant long-press, hotword, bubble tap,
            // Quick Settings tile — carries no action, and it MUST connect by itself.
            null -> if (client == null) connect()
        }
        return START_STICKY
    }

    companion object {
        /** Lets Settings toggle live behaviour (auto-reconnect) without restarting the service. */
        @Volatile
        var instance: LiveSessionService? = null
            private set

        private const val CHANNEL_ID = "saathi_session"
        private const val NOTIF_ID = 4242
        private const val MAX_TRANSCRIPT_CHARS = 1_400

        const val ACTION_START = "com.saathi.ai.START"
        const val ACTION_END = "com.saathi.ai.END"
        const val ACTION_MIC_ON = "com.saathi.ai.MIC_ON"
        const val ACTION_MIC_OFF = "com.saathi.ai.MIC_OFF"
        const val ACTION_SCREEN_START = "com.saathi.ai.SCREEN_START"
        const val ACTION_SCREEN_STOP = "com.saathi.ai.SCREEN_STOP"
        const val ACTION_TEXT = "com.saathi.ai.TEXT"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_SCREEN_CODE = "screen_result_code"
        const val EXTRA_SCREEN_DATA = "screen_data"

        fun start(context: Context) {
            val intent = Intent(context, LiveSessionService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LiveSessionService::class.java).setAction(ACTION_END))
        }

        fun setMic(context: Context, enabled: Boolean) {
            context.startService(
                Intent(context, LiveSessionService::class.java)
                    .setAction(if (enabled) ACTION_MIC_ON else ACTION_MIC_OFF)
            )
        }

        fun sendText(context: Context, text: String) {
            context.startService(
                Intent(context, LiveSessionService::class.java)
                    .setAction(ACTION_TEXT)
                    .putExtra(EXTRA_TEXT, text)
            )
        }

        fun stopScreen(context: Context) {
            context.startService(
                Intent(context, LiveSessionService::class.java).setAction(ACTION_SCREEN_STOP)
            )
        }

        fun startScreen(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, LiveSessionService::class.java).apply {
                action = ACTION_SCREEN_START
                putExtra(EXTRA_SCREEN_CODE, resultCode)
                putExtra(EXTRA_SCREEN_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
