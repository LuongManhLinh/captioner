package io.captioner.data.dao

import android.net.Uri
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.captioner.data.model.VideoThumbnail

@Dao
interface VideoThumbnailDao {
    @Query("SELECT * FROM video_thumbnails WHERE projectId = :projectId ORDER BY idx")
    fun getALlByProjectId(projectId: String): List<VideoThumbnail>

    @Insert
    fun saveAll(thumbnails: List<VideoThumbnail>)

    @Query("SELECT uri FROM video_thumbnails WHERE projectId in (:projectIds)")
    fun getAllUrisByProjectIds(projectIds: Collection<String>): List<Uri?>
}