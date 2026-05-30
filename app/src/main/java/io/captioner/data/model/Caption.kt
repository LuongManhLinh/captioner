package io.captioner.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "captions",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class Caption(
    @PrimaryKey override val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    override val text: String = "New Caption",
    override var startTimeMs: Long,
    override var endTimeMs: Long,
    val x: Float = 0f, // Use if Alignment is CUSTOM
    val y: Float = 0f, // Use if Alignment is CUSTOM,
    @Embedded
    val style: CaptionStyle = CaptionStyle()
): Captionable

