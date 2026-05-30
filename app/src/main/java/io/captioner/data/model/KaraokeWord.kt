package io.captioner.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID


@Entity(
    tableName = "karaoke_words",
    foreignKeys = [
        ForeignKey(
            entity = Caption::class,
            parentColumns = ["id"],
            childColumns = ["captionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["captionId"]), Index(value = ["startTimeMs"])]
)
data class KaraokeWord(
    @PrimaryKey override val id: String = UUID.randomUUID().toString(),
    val captionId: String,
    override val text: String,
    override var startTimeMs: Long,
    override var endTimeMs: Long,
): Captionable
