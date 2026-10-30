package com.saathi.ai.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/**
 * Lets Jimi actually *understand* the media on the phone.
 *
 *  • **Images** — downscaled to a sensible JPEG and described locally (colours,
 *    orientation, dominant tones) plus handed to the Live model when the session is
 *    running, so "এই ছবিতে কী আছে" works without screen sharing.
 *  • **Video** — frames are extracted with [MediaMetadataRetriever] at intervals, so
 *    Jimi can look at a clip rather than guessing from the filename.
 *  • **Audio** — duration/bitrate/format are read, and the Live model is told to
 *    treat the next microphone stream as that audio if the user plays it out loud.
 *
 * Everything here is on-device; nothing is uploaded except the frames the live
 * session already streams.
 */
class MediaUnderstanding(private val context: Context) {

    data class ImageFacts(
        val name: String,
        val width: Int,
        val height: Int,
        val orientation: String,
        val brightness: String,
        val dominant: List<String>,
        val jpeg: ByteArray
    )

    // ---------------------------------------------------------------- images

    fun describeImage(pathOrUri: String): ImageFacts? {
        val bitmap = loadBitmap(pathOrUri) ?: return null
        val scaled = downscale(bitmap, 768)
        val facts = analyse(scaled, pathOrUri.substringAfterLast('/'))
        bitmap.recycle()
        return facts
    }

    private fun analyse(bmp: Bitmap, name: String): ImageFacts {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        val stepX = (bmp.width / 24).coerceAtLeast(1)
        val stepY = (bmp.height / 24).coerceAtLeast(1)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                count++
                x += stepX
            }
            y += stepY
        }
        val avgR = (r / count.coerceAtLeast(1)).toInt()
        val avgG = (g / count.coerceAtLeast(1)).toInt()
        val avgB = (b / count.coerceAtLeast(1)).toInt()
        val lum = (avgR * 299 + avgG * 587 + avgB * 114) / 1000

        val brightness = when {
            lum < 60 -> "খুব অন্ধকার"
            lum < 110 -> "অন্ধকার/সন্ধ্যা"
            lum < 180 -> "স্বাভাবিক আলো"
            else -> "উজ্জ্বল"
        }
        val dominant = buildList {
            if (avgR > avgG && avgR > avgB) add("লাল/কমলা আভা")
            if (avgG > avgR && avgG > avgB) add("সবুজ আভা")
            if (avgB > avgR && avgB > avgG) add("নীল আভা")
            if (kotlin.math.abs(avgR - avgG) < 18 && kotlin.math.abs(avgG - avgB) < 18) add("সাদাকালো/নিরপেক্ষ")
        }.ifEmpty { listOf("মিশ্র রং") }

        return ImageFacts(
            name = name,
            width = bmp.width,
            height = bmp.height,
            orientation = when {
                bmp.width > bmp.height * 1.2f -> "ল্যান্ডস্কেপ"
                bmp.height > bmp.width * 1.2f -> "পোর্ট্রেট"
                else -> "স্কয়ার"
            },
            brightness = brightness,
            dominant = dominant,
            jpeg = toJpeg(bmp, 78)
        )
    }

    fun loadBitmap(pathOrUri: String): Bitmap? = try {
        when {
            pathOrUri.startsWith("content://") ->
                context.contentResolver.openInputStream(Uri.parse(pathOrUri))?.use {
                    BitmapFactory.decodeStream(it)
                }
            else -> BitmapFactory.decodeFile(pathOrUri)
        }
    } catch (t: Throwable) {
        null
    }

    fun downscale(bmp: Bitmap, maxSide: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val scale = minOf(maxSide.toFloat() / w, maxSide.toFloat() / h, 1f)
        if (scale >= 1f) return bmp
        return Bitmap.createScaledBitmap(
            bmp,
            (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    fun toJpeg(bmp: Bitmap, quality: Int = 75): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun toBase64(jpeg: ByteArray): String = Base64.encodeToString(jpeg, Base64.NO_WRAP)

    // ---------------------------------------------------------------- video

    /** Extracts up to [frames] stills from a video so they can be looked at. */
    fun videoFrames(path: String, frames: Int = 3, maxSide: Int = 640): List<ByteArray> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            if (durationMs <= 0) return emptyList()
            (0 until frames.coerceIn(1, 6)).mapNotNull { i ->
                val at = durationMs * (i + 1) / (frames + 1) * 1000
                val frame = retriever.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@mapNotNull null
                val scaled = downscale(frame, maxSide)
                toJpeg(scaled, 72)
            }
        } catch (t: Throwable) {
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun videoInfo(path: String): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val name = File(path).name
            val dur = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val secs = dur / 1000
            "$name — ${secs / 60}মি ${secs % 60}সে, ${w}x${h}, " +
                "${(bitrate?.toLongOrNull() ?: 0L) / 1000} kbps"
        } catch (t: Throwable) {
            "ভিডিও পড়া গেল না"
        } finally {
            runCatching { retriever.release() }
        }
    }

    // ---------------------------------------------------------------- audio

    fun audioInfo(path: String): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val dur = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val secs = dur / 1000
            buildString {
                if (!title.isNullOrBlank()) append("«").append(title).append("» ")
                if (!artist.isNullOrBlank()) append("— ").append(artist).append(' ')
                append("(${secs / 60}মি ${secs % 60}সে")
                if (!mime.isNullOrBlank()) append(", ").append(mime)
                append(')')
            }.trim().ifBlank { "অডিও তথ্য পাওয়া গেল না" }
        } catch (t: Throwable) {
            "অডিও পড়া গেল না"
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** A compact description the model can read out loud. */
    fun describe(facts: ImageFacts): String = String.format(
        Locale.US,
        "ছবি «%s»: %dx%d (%s), আলো %s, প্রধান রং: %s",
        facts.name, facts.width, facts.height, facts.orientation,
        facts.brightness, facts.dominant.joinToString(", ")
    )
}
