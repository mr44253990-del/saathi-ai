package com.saathi.ai.live

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal client for the Gemini Live API over WebSocket.
 *
 * Endpoint:
 *   wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta
 *       .GenerativeService.BidiGenerateContent?key=API_KEY
 *
 *  - Setup message first, wait for `setupComplete`.
 *  - Audio in : raw 16-bit PCM, mono, 16 kHz, base64 in `realtimeInput.audio`
 *  - Audio out: raw 16-bit PCM, mono, 24 kHz in `serverContent.modelTurn.parts[].inlineData`
 *  - Vision in: JPEG frames (max 1 fps) in `realtimeInput.video`
 *  - Text     : `clientContent.turns[]` with turnComplete = true
 */
class LiveClient(private val listener: Listener) {

    interface Listener {
        fun onSetupComplete()
        fun onAudio(pcm: ByteArray)
        fun onInputTranscript(text: String)
        fun onOutputTranscript(text: String)
        fun onTurnComplete()
        fun onInterrupted()
        fun onResumptionToken(token: String)
        fun onGoAway()
        fun onError(message: String)
        fun onClosed(code: Int, reason: String)
    }

    data class Config(
        val apiKey: String,
        val model: String,
        val voice: String,
        val systemPrompt: String,
        val resumeHandle: String? = null,
        val searchGrounding: Boolean = true
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)   // no timeout for the live stream
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var socket: WebSocket? = null

    @Volatile
    private var ready = false

    @Volatile
    private var userClosed = false

    val isReady: Boolean get() = ready

    fun connect(cfg: Config) {
        ready = false
        userClosed = false
        socket?.cancel()

        val request = Request.Builder().url("$WS_BASE?key=${cfg.apiKey}").build()

        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(buildSetup(cfg))
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handleMessage(bytes.utf8())

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ready = false
                if (!userClosed) {
                    val detail = response?.let { " (HTTP ${it.code})" } ?: ""
                    listener.onError((t.message ?: t.javaClass.simpleName) + detail)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ready = false
                if (!userClosed) listener.onClosed(code, reason)
            }
        })
    }

    fun sendAudio(pcm16k: ByteArray) {
        if (!ready) return
        val b64 = Base64.encodeToString(pcm16k, Base64.NO_WRAP)
        send(
            JSONObject().put(
                "realtimeInput",
                JSONObject().put(
                    "audio",
                    JSONObject().put("data", b64).put("mimeType", "audio/pcm;rate=16000")
                )
            )
        )
    }

    fun sendVideoFrame(jpeg: ByteArray) {
        if (!ready) return
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        send(
            JSONObject().put(
                "realtimeInput",
                JSONObject().put(
                    "video",
                    JSONObject().put("data", b64).put("mimeType", "image/jpeg")
                )
            )
        )
    }

    fun sendText(text: String) {
        if (!ready) return
        val turns = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))
        )
        send(JSONObject().put("clientContent", JSONObject().put("turns", turns).put("turnComplete", true)))
    }

    /** Tells the model that the mic stream paused so buffered audio is flushed. */
    fun sendAudioStreamEnd() {
        if (!ready) return
        send(JSONObject().put("realtimeInput", JSONObject().put("audioStreamEnd", true)))
    }

    fun close() {
        userClosed = true
        ready = false
        socket?.close(1000, "client closed")
        socket = null
    }

    private fun send(json: JSONObject) {
        socket?.send(json.toString())
    }

    private fun handleMessage(text: String) {
        try {
            val msg = JSONObject(text)

            if (msg.has("setupComplete")) {
                ready = true
                listener.onSetupComplete()
                return
            }

            if (msg.has("error")) {
                val err = msg.optJSONObject("error")
                listener.onError(err?.optString("message") ?: text.take(300))
                return
            }

            if (msg.has("goAway")) {
                listener.onGoAway()
                return
            }

            if (msg.has("sessionResumptionUpdate")) {
                val upd = msg.getJSONObject("sessionResumptionUpdate")
                if (upd.optBoolean("resumable", false)) {
                    val handle = upd.optString("newHandle", "")
                    if (handle.isNotBlank()) listener.onResumptionToken(handle)
                }
                return
            }

            val content = msg.optJSONObject("serverContent") ?: return

            if (content.optBoolean("interrupted", false)) listener.onInterrupted()

            content.optJSONObject("inputTranscription")?.optString("text")
                ?.takeIf { it.isNotEmpty() }?.let(listener::onInputTranscript)

            content.optJSONObject("outputTranscription")?.optString("text")
                ?.takeIf { it.isNotEmpty() }?.let(listener::onOutputTranscript)

            content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (part.has("text")) {
                        val t = part.optString("text", "")
                        if (t.isNotEmpty()) listener.onOutputTranscript(t)
                    }
                    val inline = part.optJSONObject("inlineData") ?: continue
                    val data = inline.optString("data", "")
                    val mime = inline.optString("mimeType", "")
                    if (data.isNotEmpty() && mime.startsWith("audio/")) {
                        val pcm = Base64.decode(data, Base64.NO_WRAP)
                        if (pcm.isNotEmpty()) listener.onAudio(pcm)
                    }
                }
            }

            if (content.optBoolean("turnComplete", false)) listener.onTurnComplete()
        } catch (t: Throwable) {
            // A malformed frame must never kill the session.
            listener.onError("parse: ${t.message}")
        }
    }

    private fun buildSetup(cfg: Config): String {
        val generationConfig = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put(
                "speechConfig",
                JSONObject().put(
                    "voiceConfig",
                    JSONObject().put(
                        "prebuiltVoiceConfig",
                        JSONObject().put("voiceName", cfg.voice)
                    )
                )
            )

        val setup = JSONObject()
            .put("model", "models/${cfg.model}")
            .put("generationConfig", generationConfig)
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", cfg.systemPrompt)))
            )
            // Live transcript for the UI.
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            // Unlimited session length: compress old context instead of dying at 15 min.
            .put(
                "contextWindowCompression",
                JSONObject()
                    .put("triggerTokens", 25_600)
                    .put("slidingWindow", JSONObject().put("targetTokens", 12_800))
            )
            // Session survives WebSocket reconnects (~10 min connection limit).
            .put(
                "sessionResumption",
                JSONObject().apply {
                    if (!cfg.resumeHandle.isNullOrBlank()) put("handle", cfg.resumeHandle)
                }
            )

        if (cfg.searchGrounding) {
            setup.put("tools", JSONArray().put(JSONObject().put("googleSearch", JSONObject())))
        }

        return JSONObject().put("setup", setup).toString()
    }

    companion object {
        private const val WS_BASE =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }
}
