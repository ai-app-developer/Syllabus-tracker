package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.SyllabusDao
import com.example.data.local.dao.TimerDao
import com.example.data.local.entity.ActiveTimerStateEntity
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.ReadingGoalEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.SessionEventEntity

@Database(
  entities =
    [
      BookEntity::class,
      ChapterEntity::class,
      ReadingSessionEntity::class,
      SessionEventEntity::class,
      ReadingGoalEntity::class,
      ActiveTimerStateEntity::class,
    ],
  version = 2,
  exportSchema = false,
)
abstract class SyllabusDatabase : RoomDatabase() {
  abstract fun syllabusDao(): SyllabusDao

  abstract fun timerDao(): TimerDao

  companion object {
    val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_sessions` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `sessionId` TEXT NOT NULL,
              `bookId` INTEGER NOT NULL,
              `chapterId` INTEGER NOT NULL,
              `mode` TEXT NOT NULL,
              `status` TEXT NOT NULL,
              `startWallTimeMs` INTEGER NOT NULL,
              `endWallTimeMs` INTEGER NOT NULL,
              `activeFocusMs` INTEGER NOT NULL,
              `pauseMs` INTEGER NOT NULL,
              `breakMs` INTEGER NOT NULL,
              `goalMs` INTEGER,
              `notes` TEXT NOT NULL,
              `sessionDate` TEXT NOT NULL,
              `createdAt` INTEGER NOT NULL,
              FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
              FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
              .trimIndent()
          )
          db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_reading_sessions_sessionId` ON `reading_sessions` (`sessionId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_sessions_bookId` ON `reading_sessions` (`bookId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_sessions_chapterId` ON `reading_sessions` (`chapterId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_sessions_sessionDate` ON `reading_sessions` (`sessionDate`)"
          )

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `session_events` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `sessionId` TEXT NOT NULL,
              `bookId` INTEGER NOT NULL,
              `chapterId` INTEGER NOT NULL,
              `eventType` TEXT NOT NULL,
              `elapsedAtEventMs` INTEGER NOT NULL,
              `wallTimeMs` INTEGER NOT NULL,
              `label` TEXT,
              FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
              FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
              .trimIndent()
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_events_sessionId` ON `session_events` (`sessionId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_events_bookId` ON `session_events` (`bookId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_events_chapterId` ON `session_events` (`chapterId`)"
          )

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reading_goals` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `periodType` TEXT NOT NULL,
              `targetMinutes` INTEGER NOT NULL,
              `isActive` INTEGER NOT NULL,
              `createdAt` INTEGER NOT NULL
            )
            """
              .trimIndent()
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reading_goals_periodType` ON `reading_goals` (`periodType`)"
          )

          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `active_timer_state` (
              `id` INTEGER NOT NULL,
              `sessionId` TEXT NOT NULL,
              `bookId` INTEGER NOT NULL,
              `chapterId` INTEGER NOT NULL,
              `mode` TEXT NOT NULL,
              `status` TEXT NOT NULL,
              `focusDurationMs` INTEGER NOT NULL,
              `breakDurationMs` INTEGER NOT NULL,
              `goalMs` INTEGER,
              `notes` TEXT NOT NULL,
              `sessionStartWallTimeMs` INTEGER NOT NULL,
              `phaseStartElapsedMs` INTEGER NOT NULL,
              `phaseStartWallTimeMs` INTEGER NOT NULL,
              `accumulatedFocusMs` INTEGER NOT NULL,
              `accumulatedBreakMs` INTEGER NOT NULL,
              `accumulatedPauseMs` INTEGER NOT NULL,
              `pauseStartElapsedMs` INTEGER,
              `pauseStartWallTimeMs` INTEGER,
              `lastKnownWallTimeMs` INTEGER NOT NULL,
              `lapCount` INTEGER NOT NULL,
              `goalNotified` INTEGER NOT NULL,
              `pomodoroCycle` INTEGER NOT NULL,
              `statusBeforeStopSummary` TEXT,
              PRIMARY KEY(`id`),
              FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
              FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """
              .trimIndent()
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_active_timer_state_bookId` ON `active_timer_state` (`bookId`)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_active_timer_state_chapterId` ON `active_timer_state` (`chapterId`)"
          )
        }
      }

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
              .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
              .addMigrations(MIGRATION_1_2)
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
