package com.saathi.ai.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream

/**
 * Screen sharing: mirrors the display into a low-resolution virtual display and
 * pushes ~1 JPEG per second to the Live session, so Saathi can actually *see*
 * what is on your screen and talk about it.
 *
 * A MediaProjection must always be created from a foreground service with type
 * `mediaProjection` on Android 10+, which is exactly how it is used here.
 */
class ScreenFrameSource(private val context: Context) {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private var onFrame: ((ByteArray) -> Unit)? = null
    private var onStoppedCallback: (() -> Unit)? = null

    @Volatile
    private var lastFrameAt = 0L

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(
        resultCode: Int,
        data: Intent,
        onFrame: (ByteArray) -> Unit,
        onStopped: () -> Unit
    ): Boolean {
        if (isRunning) return true
        this.onFrame = onFrame
        this.onStoppedCallback = onStopped

        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = try {
            manager.getMediaProjection(resultCode, data)
        } catch (t: Throwable) {
            null
        } ?: return false

        projection = mediaProjection
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopInternal(notify = true)
            }
        }, Handler(context.mainLooper))

        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val scale = (TARGET_WIDTH.toFloat() / metrics.widthPixels).coerceAtMost(1f)
        val width = ((metrics.widthPixels * scale).toInt().coerceAtLeast(2)) and 1.inv()
        val height = ((metrics.heightPixels * scale).toInt().coerceAtLeast(2)) and 1.inv()

        val bgThread = HandlerThread("saathi-screen").also { it.start() }
        thread = bgThread
        val bgHandler = Handler(bgThread.looper)
        handler = bgHandler

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        reader.setOnImageAvailableListener({ r ->
            val image = try {
                r.acquireLatestImage()
            } catch (t: Throwable) {
                null
            } ?: return@setOnImageAvailableListener

            try {
                val now = System.currentTimeMillis()
                if (now - lastFrameAt >= FRAME_INTERVAL_MS) {
                    lastFrameAt = now
                    val bitmap = imageToBitmap(image)
                    val scaled = scaleDown(bitmap)
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 55, out)
                    if (scaled != bitmap) scaled.recycle()
                    bitmap.recycle()
                    onFrame(out.toByteArray())
                }
            } catch (_: Throwable) {
                // skip broken frame
            } finally {
                image.close()
            }
        }, bgHandler)

        virtualDisplay = try {
            mediaProjection.createVirtualDisplay(
                "saathi-screen",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                bgHandler
            )
        } catch (t: Throwable) {
            null
        }

        isRunning = virtualDisplay != null
        return isRunning
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bmp = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(buffer)
        return if (rowPadding == 0) {
            bmp
        } else {
            val cropped = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
            bmp.recycle()
            cropped
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= TARGET_WIDTH) return bitmap
        val ratio = TARGET_WIDTH.toFloat() / max
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    fun stop() = stopInternal(notify = false)

    private fun stopInternal(notify: Boolean) {
        isRunning = false
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { projection?.stop() }
        projection = null
        handler?.removeCallbacksAndMessages(null)
        handler = null
        runCatching { thread?.quitSafely() }
        thread = null
        if (notify) onStoppedCallback?.invoke()
        onFrame = null
    }

    companion object {
        private const val TARGET_WIDTH = 720
        private const val FRAME_INTERVAL_MS = 1_000L
    }
}
