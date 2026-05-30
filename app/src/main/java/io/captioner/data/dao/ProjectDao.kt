package io.captioner.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.captioner.data.dto.ProjectThumbnailDto
import io.captioner.data.model.Project

@Dao
interface ProjectDao {
    @Insert
    fun save(project: Project)

    @Query("""
        SELECT id, primaryThumbnailUri, createdAt, karaoke, durationMs 
        FROM projects 
        ORDER BY createdAt DESC 
    """)
    fun getAllThumbnails(): List<ProjectThumbnailDto>

    @Update
    fun update(project: Project)

    @Query("DELETE FROM projects WHERE id = :id")
    fun deleteById(id: String)

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getById(id: String): Project?


    @Query("DELETE FROM projects WHERE id IN (:ids)")
    fun deleteAllByIds(ids: Collection<String>)
}