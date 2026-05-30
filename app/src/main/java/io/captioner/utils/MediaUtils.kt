package io.captioner.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.nio.ByteBuffer
import java.util.Collections
import java.util.UUID

fun extractAudioAndSave(
    context: Context,
    videoUri: Uri,
    onProgressUpdated: (progress: Float, msg: String) -> Unit = {_, _ ->}
): String? {
    onProgressUpdated(0f, "Preparing to extract audio...")

    var outputPath: String? = null
    val extractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    try {
        extractor.setDataSource(context, videoUri, null)
        onProgressUpdated(0.05f, "Checking audio track...")
        // 1. Find the audio track index
        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                audioTrackIndex = i
                format = trackFormat
                break
            }
        }

        if (audioTrackIndex < 0 || format == null) {
            return null
        }


        onProgressUpdated(0.075f, "Preparing to extract audio...")

        extractor.selectTrack(audioTrackIndex)

        val fileDir = File(
            context.filesDir,
            "${UUID.randomUUID()}.wav"
        )
        // 2. Setup the Muxer to write the audio file
        // Use MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 for .m4a files
        muxer = MediaMuxer(
            fileDir.toString(),
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )

        val writeTrackIndex = muxer.addTrack(format)
        muxer.start()

        // 3. Read data from extractor and write to muxer
        val maxBufferSize = 512 * 1024
        val buffer = ByteBuffer.allocate(maxBufferSize)
        val bufferInfo = MediaCodec.BufferInfo()
        onProgressUpdated(0.1f, "Extracting audio...")
        var extractProgress = 0.1f
        while (true) {
            bufferInfo.offset = 0
            bufferInfo.size = extractor.readSampleData(buffer, 0)

            if (bufferInfo.size < 0) {
                break // End of stream
            }

            bufferInfo.presentationTimeUs = extractor.sampleTime
            @SuppressLint("WrongConstant")
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(writeTrackIndex, buffer, bufferInfo)
            extractor.advance()

            extractProgress += 0.0002f
            onProgressUpdated(extractProgress.coerceAtMost(0.99f), "Extracting audio...")
        }

        outputPath = fileDir.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        extractor.release()
        muxer?.let {
            it.stop()
            it.release()
        }
    }

    onProgressUpdated(1f, "Done extracting audio. Saving...")

    return outputPath
}

data class ThumbnailResult(
    val primaryThumbnailUri: Uri,
    val durationMs: Long,
    val width: Int,
    val height: Int
)


fun extractThumbnails(
    context: Context,
    videoUri: Uri,
    durationMs: Long,
    msPerThumb: Long = 1000L,
    onThumbUriEmerge: (Int, Uri?) -> Unit,
    onProgressUpdated: (progress: Float, msg: String) -> Unit = {_, _ ->},
    expectedCount: Int? = null
): List<Uri?>? {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, videoUri)

        val thumbCount = expectedCount
            ?: if (durationMs % msPerThumb != 0L) {
                (durationMs / msPerThumb).toInt() + 1
            } else {
                (durationMs / msPerThumb).toInt()
            }

        val thumbIndices = (0 until thumbCount)
        var count = 0
        val thumbnailUris = thumbIndices.map { idx ->
            val timeCenterMs = (idx * msPerThumb) + (msPerThumb / 2)
            val timeUs = timeCenterMs * 1000L

            val frame = retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )

            val scaledFrame = frame?.let {
                val ratio = 100f / it.height
                val width = (it.width * ratio).toInt()
                it.scale(width.coerceAtLeast(1), 100)
            }

            val uri = if (scaledFrame == null) {
                null
            } else {
                saveFrame(context.filesDir, scaledFrame)
            }

            onThumbUriEmerge(idx, uri)

            count++
            onProgressUpdated(
                count.toFloat() / thumbCount,
                "Extracting Thumbnails: ${count}/$thumbCount"
            )

            uri
        }

        return thumbnailUris

    } catch (e: Exception) {
        e.printStackTrace()
        retriever.release()
        return null
    }
}

fun extractThumbnail(
    context: Context,
    videoUri: Uri,
): ThumbnailResult? {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, videoUri)
        val durationMs = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLong() ?: 0L

        var primaryThumbnail = retriever.getFrameAtTime(0)

        var attempt = 1
        while (primaryThumbnail == null && attempt <= 3) {
            val timeMs = (attempt * 100).toLong()
            primaryThumbnail = retriever.getFrameAtTime(timeMs * 1000)
            attempt++
        }

        if (primaryThumbnail == null) {
            return null
        }

        val primaryUri = saveFrame(context.filesDir, primaryThumbnail)
        val width = primaryThumbnail.width
        val height = primaryThumbnail.height

        return ThumbnailResult(
            durationMs = durationMs,
            primaryThumbnailUri = primaryUri,
            width = width,
            height = height
        )

    } catch (e: Exception) {
        e.printStackTrace()
        retriever.release()
        return null
    }
}

private fun saveFrame(fileDir: File, frame: Bitmap): Uri {
    val frameFile = File(
        fileDir,
        "${UUID.randomUUID()}.jpg"
    )
    frameFile.outputStream().use { stream ->
        frame.compress(Bitmap.CompressFormat.JPEG, 100, stream)
    }
    return frameFile.toUri()
}