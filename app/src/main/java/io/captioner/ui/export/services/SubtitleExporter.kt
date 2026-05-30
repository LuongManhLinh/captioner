package io.captioner.ui.export.services

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.captioner.data.dto.CaptionKaraokeDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun toSrt(captions: List<CaptionKaraokeDto>): String {
    val sb = StringBuilder()
    val sorted = captions.sortedBy { it.caption.startTimeMs }

    sorted.forEachIndexed { index, dto ->
        sb.appendLine(index + 1)
        sb.appendLine("${formatSrtTime(dto.caption.startTimeMs)} --> ${formatSrtTime(dto.caption.endTimeMs)}")
        sb.appendLine(dto.caption.text)
        sb.appendLine()
    }

    return sb.toString()
}

fun toVtt(captions: List<CaptionKaraokeDto>): String {
    val sb = StringBuilder()
    sb.appendLine("WEBVTT")
    sb.appendLine()

    val sorted = captions.sortedBy { it.caption.startTimeMs }
    sorted.forEach { dto ->
        sb.appendLine("${formatVttTime(dto.caption.startTimeMs)} --> ${formatVttTime(dto.caption.endTimeMs)}")
        sb.appendLine(dto.caption.text)
        sb.appendLine()
    }

    return sb.toString()
}

private fun formatSrtTime(ms: Long): String {
    val h = ms / 3600000
    val m = (ms % 3600000) / 60000
    val s = (ms % 60000) / 1000
    val millis = ms % 1000
    return String.format("%02d:%02d:%02d,%03d", h, m, s, millis)
}

private fun formatVttTime(ms: Long): String {
    val h = ms / 3600000
    val m = (ms % 3600000) / 60000
    val s = (ms % 60000) / 1000
    val millis = ms % 1000
    return String.format("%02d:%02d:%02d.%03d", h, m, s, millis)
}

suspend fun exportSubtitleFile(
    context: Context,
    captions: List<CaptionKaraokeDto>,
    fileName: String,
    format: SubtitleFormat = SubtitleFormat.SRT
): Uri? = withContext(Dispatchers.IO) {
    val (content, extension, mimeType) = when (format) {
        SubtitleFormat.SRT -> Triple(toSrt(captions), "srt", "text/plain")
        SubtitleFormat.VTT -> Triple(toVtt(captions), "vtt", "text/vtt")
    }

    val outputFileName = "${fileName.removeSuffix(".mp4")}_${System.currentTimeMillis()}.$extension"

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+ dùng MediaStore Downloads
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, outputFileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Captioner")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null

            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            uri
        } else {
            // API < 29 ghi thẳng ra file
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadsDir, "Captioner")
            if (!appDir.exists()) appDir.mkdirs()
            val file = File(appDir, outputFileName)
            file.writeText(content)
            Uri.fromFile(file)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

enum class SubtitleFormat {
    SRT, VTT
}