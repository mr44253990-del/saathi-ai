package com.saathi.ai.capture

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

/**
 * Streams JPEG stills from the phone camera into the Live session (max ~1 fps,
 * which is the Live API limit). Front camera is the default so the assistant
 * can see the user; switching to the back camera is one tap.
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    var onFrame: ((ByteArray) -> Unit)? = null

    private val executor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null

    @Volatile
    private var lastFrameAt = 0L

    @Volatile
    private var running = false

    val isRunning: Boolean get() = running

    fun start(front: Boolean = true, onReady: (() -> Unit)? = null, onError: (String) -> Unit = {}) {
        running = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider

                val resolution = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { proxy -> analyze(proxy) }
                analysis = imageAnalysis

                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, imageAnalysis)
                onReady?.invoke()
            } catch (t: Throwable) {
                running = false
                onError(t.message ?: "ক্যামেরা চালু করা যায়নি")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (!running || now - lastFrameAt < FRAME_INTERVAL_MS) return
            lastFrameAt = now
            val image = proxy.image ?: return
            val jpeg = FrameEncoder.yuv420ToJpeg(image, proxy.imageInfo.rotationDegrees)
            if (jpeg != null) onFrame?.invoke(jpeg)
        } catch (_: Throwable) {
            // ignore a broken frame
        } finally {
            proxy.close()
        }
    }

    fun stop() {
        running = false
        runCatching { analysis?.clearAnalyzer() }
        runCatching { provider?.unbindAll() }
        analysis = null
    }

    companion object {
        private const val FRAME_INTERVAL_MS = 1_100L
    }
}
