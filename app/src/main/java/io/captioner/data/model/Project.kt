package io.captioner.data.model

import android.net.Uri
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

@Entity(
    tableName = "projects",
    indices = [Index(value = ["createdAt"])]
)
data class Project(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val karaoke: Boolean = false,
    val videoUri: Uri,
    val primaryThumbnailUri: Uri,
    val audioPath: String,
    val createdAt: LocalDateTime,
    val videoWidth: Int,
    val videoHeight: Int,
    val durationMs: Long,

    @Embedded
    val captionStyle: CaptionStyle = CaptionStyle()
)