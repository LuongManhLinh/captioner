package io.captioner.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.captioner.data.model.KaraokeWord


@Dao
interface KaraokeWordDao {
    @Query("SELECT * FROM karaoke_words WHERE captionId = :captionId ORDER BY startTimeMs")
    fun getAllByCaptionId(captionId: String): List<KaraokeWord>

    @Insert
    fun saveAll(words: List<KaraokeWord>)

    @Insert
    fun save(word: KaraokeWord)

    @Query("DELETE FROM karaoke_words WHERE captionId = :captionId")
    fun deleteAllByCaptionId(captionId: String)

    @Query("DELETE FROM karaoke_words WHERE id = :id")
    fun deleteById(id: String)

    @Update
    fun update(word: KaraokeWord)

    @Update
    fun updateAll(words: List<KaraokeWord>)

}