package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.SyllabusDao
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity

@Database(
  entities = [BookEntity::class, ChapterEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class SyllabusDatabase : RoomDatabase() {
  abstract fun syllabusDao(): SyllabusDao

  companion object {
    @Volatile
    private var INSTANCE: SyllabusDatabase? = null

    fun getInstance(context: Context): SyllabusDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                SyllabusDatabase::class.java,
                "syllabus_tracker.db",
              )
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
