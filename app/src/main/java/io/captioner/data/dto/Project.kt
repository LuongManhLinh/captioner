package io.captioner.data.dto

import android.net.Uri
import io.captioner.data.model.CaptionStyle
import io.captioner.data.model.Project
import java.time.LocalDateTime

data class ProjectThumbnailDto(
    val id: String,
    val primaryThumbnailUri: Uri,
    val createdAt: LocalDateTime,
    val karaoke: Boolean,
    val durationMs: Long
) {
    constructor(project: Project) : this(
        id = project.id,
        primaryThumbnailUri = project.primaryThumbnailUri,
        createdAt = project.createdAt,
        karaoke = project.karaoke,
        durationMs = project.durationMs
    )
}

data class ProjectDto(
    val id: String,
    val karaoke: Boolean,
    val videoUri: Uri,
    val audioPath: String,
    val createdAt: LocalDateTime,
    val viewWidth: Int,
    val viewHeight: Int,
    val captionStyle: CaptionStyle
) {
    constructor(project: Project) : this(
        id = project.id,
        videoUri = project.videoUri,
        audioPath = project.audioPath,
        createdAt = project.createdAt,
        karaoke = project.karaoke,
        viewWidth = project.videoWidth,
        viewHeight = project.videoHeight,
        captionStyle = project.captionStyle
    )
}