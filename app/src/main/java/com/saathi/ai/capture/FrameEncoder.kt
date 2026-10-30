package com.saathi.ai.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream

/**
 * Converts camera YUV_420_888 buffers into small rotated JPEGs (the Live API
 * accepts JPEG/PNG stills at max 1 frame per second).
 */
object FrameEncoder {

    private const val MAX_DIMENSION = 768
    private const val JPEG_QUALITY = 60

    fun yuv420ToJpeg(image: Image, rotationDegrees: Int): ByteArray? {
        return try {
            val nv21 = yuv420ToNv21(image)
            val w = image.width
            val h = image.height
            val yuv = YuvImage(nv21, ImageFormat.NV21, w, h, null)
            val out = ByteArrayOutputStream()
            yuv.compressToJpeg(Rect(0, 0, w, h), 70, out)
            downscaleAndRotate(out.toByteArray(), rotationDegrees)
        } catch (t: Throwable) {
            null
        }
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixStride = yPlane.pixelStride

        var pos = 0
        if (yPixStride == 1 && yRowStride == width) {
            yBuf.get(nv21, 0, ySize)
            pos = ySize
        } else {
            for (row in 0 until height) {
                var col = 0
                var idx = row * yRowStride
                while (col < width) {
                    nv21[pos++] = yBuf.get(idx)
                    idx += yPixStride
                    col++
                }
            }
        }

        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val uPixStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixStride = vPlane.pixelStride

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val uIdx = row * uRowStride + col * uPixStride
                val vIdx = row * vRowStride + col * vPixStride
                nv21[pos++] = vBuf.get(vIdx) // V
                nv21[pos++] = uBuf.get(uIdx) // U
            }
        }
        return nv21
    }

    private fun downscaleAndRotate(jpeg: ByteArray, rotationDegrees: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return jpeg

        var sample = 1
        while (w / (sample * 2) >= MAX_DIMENSION || h / (sample * 2) >= MAX_DIMENSION) sample *= 2

        val decoded = BitmapFactory.decodeByteArray(
            jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return jpeg

        val matrix = Matrix()
        val scale = (MAX_DIMENSION.toFloat() / maxOf(decoded.width, decoded.height)).coerceAtMost(1f)
        matrix.postScale(scale, scale)
        if (rotationDegrees != 0) matrix.postRotate(rotationDegrees.toFloat())

        val outBitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val out = ByteArrayOutputStream()
        outBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (outBitmap != decoded) outBitmap.recycle()
        decoded.recycle()
        return out.toByteArray()
    }
}
