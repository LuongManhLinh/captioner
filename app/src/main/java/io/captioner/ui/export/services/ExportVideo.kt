package io.captioner.ui.export.services

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import io.captioner.data.dto.CaptionKaraokeDto
import io.captioner.utils.DynamicCaptionOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Exports the video using Media3 Transformer, tracks progress, and securely copies
 * the result to the public Movies directory using MediaStore (No permissions required).
 * * @return The Content Uri of the saved video in the gallery, or null if saving failed.
 */
@OptIn(UnstableApi::class)
suspend fun exportVideo(
    context: Context,
    inputUri: Uri,
    captions: List<CaptionKaraokeDto>,
    videoWidth: Int,
    videoHeight: Int,
    ratio: Float,
    karaoke: Boolean,
    onProgressChange: (Int) -> Unit = { }
): Uri? = withContext(Dispatchers.Main) { // Must be on Main for Transformer and getProgress
    val rawName = getVideoName(context, inputUri)
    val name = if (rawName != null) {
        if (rawName.endsWith(".mp4")) {
            rawName
        } else {
            "${rawName}.mp4"
        }
    } else {
        "exported_video_${System.currentTimeMillis()}.mp4"
    }

    val outputFile = File(context.cacheDir, name)
    val deferredResult = CompletableDeferred<File>()

    val density = context.resources.displayMetrics.density

    val captionOverlay = DynamicCaptionOverlay(
        captions = captions,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        density = density,
        ratio = ratio,
        karaoke = karaoke
    )

    val overlayEffect = OverlayEffect(listOf(captionOverlay))
    val mediaItem = MediaItem.fromUri(inputUri)
    val editedMediaItem = EditedMediaItem.Builder(mediaItem)
        .setEffects(Effects(emptyList(), listOf(overlayEffect)))
        .build()

    val transformer = Transformer.Builder(context)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                Log.d("Export", "Transformer finished. File size: ${exportResult.fileSizeBytes}")
                deferredResult.complete(outputFile)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                Log.e("Export", "Transformer failed: ${exportException.message}")
                deferredResult.completeExceptionally(exportException)
            }
        })
        .build()

    // 1. Start the export
    transformer.start(editedMediaItem, outputFile.absolutePath)
    Log.d("Export", "Transformer started in background...")

    // 2. Poll progress concurrently
    val progressJob = launch {
        val progressHolder = ProgressHolder()
        while (isActive) {
            val progressState = transformer.getProgress(progressHolder)

            when (progressState) {
                Transformer.PROGRESS_STATE_AVAILABLE -> {
                    Log.d("ExportProgress", "Progress: ${progressHolder.progress}%")
                    onProgressChange(progressHolder.progress)
                }
                Transformer.PROGRESS_STATE_NOT_STARTED -> {
                    // This state is returned when export finishes or hasn't begun.
                    // If we've already polled and it drops to NOT_STARTED, we can break.
                    break
                }
                Transformer.PROGRESS_STATE_UNAVAILABLE -> {
                    Log.d("ExportProgress", "Progress unavailable (e.g., muxing stage)")
                }
                Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY -> {
                    Log.d("ExportProgress", "Waiting for callbacks...")
                }
            }
            delay(500) // Poll twice a second
        }
    }

    // Handle cancellation from the parent coroutine
    deferredResult.invokeOnCompletion { cause ->
        if (cause is CancellationException) {
            Log.d("Export", "Export cancelled.")
            transformer.cancel()
            progressJob.cancel()
            if (outputFile.exists()) outputFile.delete()
        }
    }

    // 3. Wait for completion, then copy to external storage
    try {
        val finalCacheFile = deferredResult.await()
        progressJob.cancel()

        Log.d("Export", "Moving file to MediaStore...")
        return@withContext copyToMediaStore(context, finalCacheFile)

    } catch (e: Exception) {
        progressJob.cancel()
        if (outputFile.exists()) outputFile.delete()
        throw e // Re-throw to be handled by the caller
    }
}

fun getVideoName(
    context: Context,
    uri: Uri
): String? {

    val projection = arrayOf(
        OpenableColumns.DISPLAY_NAME
    )

    context.contentResolver.query(
        uri,
        projection,
        null,
        null,
        null
    )?.use { cursor ->

        val nameIndex =
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

        if (cursor.moveToFirst() && nameIndex != -1) {
            return cursor.getString(nameIndex)
        }
    }

    return null
}


/**
 * Copies a file from the app's internal cache to the shared MediaStore (Gallery).
 * Handles API differences for Android 10+ (Scoped Storage) and older versions.
 */
private suspend fun copyToMediaStore(context: Context, cacheFile: File): Uri? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val fileName = cacheFile.name
    val appFolderName = "Captioner" // Name of the folder in Movies
    Log.d("Export", "Exporting $cacheFile to $fileName")

    val nowMs = System.currentTimeMillis()
    val nowSec = nowMs / 1000

    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.DATE_TAKEN, nowMs)
        put(MediaStore.Video.Media.DATE_ADDED, nowSec)
        put(MediaStore.Video.Media.DATE_MODIFIED, nowSec)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$appFolderName")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        } else {
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val appDir = File(moviesDir, appFolderName)
            if (!appDir.exists()) appDir.mkdirs()

            // ← Thêm timestamp để tránh trùng tên
            val uniqueName = "${fileName}_$nowSec"
            val file = File(appDir, uniqueName)
            put(MediaStore.Video.Media.DATA, file.absolutePath)
            put(MediaStore.Video.Media.DISPLAY_NAME, uniqueName)
        }
    }

    // Crucial Fix: Use VOLUME_EXTERNAL_PRIMARY for Android 10+ to avoid null URIs
    val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    Log.d("Export", "Collection Uri is $collectionUri")

    var targetUri: Uri? = null

    try {
        targetUri = resolver.insert(collectionUri, contentValues)

        if (targetUri == null) {
            Log.e("Export", "Resolver returned null Uri. Check device storage state or volume.")
            return@withContext null
        }

        // Copy the bytes from cache to the MediaStore Uri
        resolver.openOutputStream(targetUri)?.use { outputStream ->
            cacheFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        // If Android 10+, release the IS_PENDING flag so gallery apps can see it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            contentValues.put(MediaStore.Video.Media.DATE_TAKEN, nowMs)
            contentValues.put(MediaStore.Video.Media.DATE_MODIFIED, nowMs)
            resolver.update(targetUri, contentValues, null, null)
        }

        // Clean up the cache file
        if (cacheFile.exists()) {
            cacheFile.delete()
        }

        Log.d("Export", "Successfully copied to MediaStore: $targetUri")
        return@withContext targetUri

    } catch (e: Exception) {
        Log.e("Export", "Failed to copy to MediaStore", e)
        // If something failed during the stream copy, delete the corrupted empty file
        targetUri?.let { resolver.delete(it, null, null) }
        return@withContext null
    }
}
