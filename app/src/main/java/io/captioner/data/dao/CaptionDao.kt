package io.captioner.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.captioner.data.model.Caption

@Dao
interface CaptionDao {
    @Query("SELECT * FROM captions WHERE projectId = :projectId")
    fun getALlByProjectId(projectId: String): List<Caption>

    @Insert
    fun save(caption: Caption)
    @Insert
    fun saveAll(captions: List<Caption>)

    @Update
    fun update(caption: Caption)
    @Update
    fun updateAll(captions: List<Caption>)

    @Query("DELETE FROM captions WHERE id = :id")
    fun deleteById(id: String)

    @Query("DELETE FROM captions WHERE id IN (:ids)")
    fun deleteAllByIds(ids: List<String>)

    @Delete
    fun deleteAll(captions: List<Caption>)
}