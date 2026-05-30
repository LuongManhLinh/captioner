package io.captioner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.captioner.data.dao.CaptionDao
import io.captioner.data.dao.KaraokeWordDao
import io.captioner.data.dao.ProjectDao
import io.captioner.data.dao.VideoThumbnailDao
import io.captioner.data.model.Caption
import io.captioner.data.model.KaraokeWord
import io.captioner.data.model.Project
import io.captioner.data.model.VideoThumbnail


@Database(
    entities = [Project::class, Caption::class, KaraokeWord::class, VideoThumbnail::class],
    version = 2,  // ← tăng từ 1 lên 2
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CaptionerDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun captionDao(): CaptionDao
    abstract fun karaokeWordDao(): KaraokeWordDao
    abstract fun videoThumbnailDao(): VideoThumbnailDao

    companion object {
        @Volatile
        private var INSTANCE: CaptionerDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE projects ADD COLUMN displayRatio REAL NOT NULL DEFAULT 1.0"
                )
            }
        }

        fun getDatabase(context: Context): CaptionerDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context = context.applicationContext,
                    klass = CaptionerDatabase::class.java,
                    name = "captioner"
                )
                    .addMigrations(MIGRATION_1_2)  // ← thêm dòng này
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}