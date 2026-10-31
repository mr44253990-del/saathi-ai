package com.saathi.ai.live

import kotlinx.coroutines.flow.MutableStateFlow

enum class Phase { IDLE, CONNECTING, LISTENING, THINKING, SPEAKING, RECONNECTING, ERROR, ENDED }

data class SessionState(
    val phase: Phase = Phase.IDLE,
    val micOn: Boolean = false,
    val cameraOn: Boolean = false,
    val screenOn: Boolean = false,
    val level: Float = 0f,
    val userText: String = "",
    val modelText: String = "",
    val error: String? = null,
    val model: String = "",
    val lastTool: String = "",
    val bubbleMsg: String = "",
    /** Live line from the background task runner, shown inside the bubble. */
    val taskText: String = "",
    /** 0..100 progress across the whole to-do board. */
    val taskProgress: Int = 0
)

/**
 * Single source of truth shared by the foreground service and the Compose UI.
 */
object SessionHub {

    val state = MutableStateFlow(SessionState())

    /** Set by the running service so the camera can push JPEG frames into the live session. */
    @Volatile
    var cameraFrameSink: ((ByteArray) -> Unit)? = null

    @Volatile
    var isServiceRunning: Boolean = false

    fun sendCameraFrame(jpeg: ByteArray) {
        cameraFrameSink?.invoke(jpeg)
    }

    fun update(block: (SessionState) -> SessionState) {
        state.value = block(state.value)
    }

    fun reset() {
        state.value = SessionState()
    }
}
