package com.example

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import android.appwidget.AppWidgetManager
import com.example.data.backup.BackupBook
import com.example.data.backup.BackupChapter
import com.example.data.backup.BackupImportMode
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupSettings
import com.example.data.backup.BackupValidationResult
import com.example.data.backup.SyllabusBackup
import com.example.data.local.SyllabusDatabase
import com.example.data.local.entity.ActiveTimerStateEntity
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.ReadingGoalEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.SessionEventEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.StudyTrendPeriod
import com.example.data.repository.SyllabusRepository
import com.example.data.repository.TimerRepository
import com.example.notifications.NotificationHelper
import com.example.timer.AlarmAudioBackend
import com.example.timer.AlarmDiagnosticLog
import com.example.timer.AlarmFireSource
import com.example.timer.AnalyticsPeriod
import com.example.timer.ClockProvider
import com.example.timer.GoalPeriodType
import com.example.timer.PrefsAlarmDeduplicator
import com.example.timer.ScheduledAlarmTarget
import com.example.timer.SessionEventType
import com.example.timer.SoundAttemptResult
import com.example.timer.TimerAlarmEventType
import com.example.timer.TimerAlarmPlayer
import com.example.timer.TimerAlarmScheduler
import com.example.timer.TimerAnalyticsCalculator
import com.example.timer.TimerMath
import com.example.timer.TimerMode
import com.example.timer.TimerStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowLog

private class FakeClock(
  var elapsedMs: Long = 10_000L,
  var wallMs: Long = 1_763_320_000_000L,
) : ClockProvider {
  override fun elapsedRealtime(): Long = elapsedMs

  override fun currentTimeMillis(): Long = wallMs

  fun advance(deltaMs: Long) {
    elapsedMs += deltaMs
    wallMs += deltaMs
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerRobolectricTest {

  private lateinit var context: Context
  private lateinit var db: SyllabusDatabase
  private lateinit var clock: FakeClock
  private lateinit var repo: TimerRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, SyllabusDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    clock = FakeClock()
    repo =
      TimerRepository(
        timerDao = db.timerDao(),
        syllabusDao = db.syllabusDao(),
        runInTransaction = { block -> db.withTransaction { block() } },
        clock = clock,
        zoneId = ZoneId.of("UTC"),
      )
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `migration 1 to 2 preserves existing books and chapters and creates timer tables`() = runTest {
    val dbFile = context.getDatabasePath("migration_v1_test.db")
    dbFile.parentFile?.mkdirs()
    if (dbFile.exists()) dbFile.delete()

    val rawDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    rawDb.execSQL(
      "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
    )
    rawDb.execSQL(
      "CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `name` TEXT NOT NULL, `number` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `completedAt` INTEGER, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
    )
    rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_bookId` ON `chapters` (`bookId`)")
    rawDb.execSQL("INSERT INTO `books` (`id`, `name`, `createdAt`) VALUES (1, 'Mathematics', 1000)")
    rawDb.execSQL(
      "INSERT INTO `chapters` (`id`, `bookId`, `name`, `number`, `isCompleted`, `completedAt`) VALUES (10, 1, 'Algebra', 1, 1, 2000)"
    )
    rawDb.version = 1
    rawDb.close()

    val migrated =
      Room.databaseBuilder(context, SyllabusDatabase::class.java, "migration_v1_test.db")
        .allowMainThreadQueries()
        .addMigrations(
          SyllabusDatabase.MIGRATION_1_2,
          SyllabusDatabase.MIGRATION_2_3,
          SyllabusDatabase.MIGRATION_3_4,
        )
        .build()

    val books = migrated.syllabusDao().getAllBooksOnce()
    val chapters = migrated.syllabusDao().getAllChaptersOnce()
    assertEquals(1, books.size)
    assertEquals("Mathematics", books.first().name)
    assertEquals(1, chapters.size)
    assertEquals("Algebra", chapters.first().name)
    assertTrue(migrated.timerDao().getAllSessionsOnce().isEmpty())

    val indexCursor =
      migrated.openHelper.readableDatabase.query(
        "SELECT name FROM sqlite_master WHERE type = 'index'"
      )
    val indexNames = mutableSetOf<String>()
    indexCursor.use { cursor ->
      while (cursor.moveToNext()) {
        indexNames.add(cursor.getString(0))
      }
    }
    assertTrue("index_chapters_bookId_isCompleted" in indexNames)
    assertTrue("index_chapters_completedAt" in indexNames)
    assertTrue("index_reading_sessions_sessionDate_startWallTimeMs" in indexNames)
    assertTrue("index_reading_sessions_startWallTimeMs" in indexNames)

    migrated.close()
    dbFile.delete()
  }

  @Test
  fun `migration 2 to 3 adds performance indexes without losing existing sessions or chapters`() =
    runTest {
      val dbFile = context.getDatabasePath("migration_v2_test.db")
      dbFile.parentFile?.mkdirs()
      if (dbFile.exists()) dbFile.delete()

      val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
      val helper =
        factory.create(
          androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration_v2_test.db")
            .callback(
              object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                  db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                  )
                  db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `name` TEXT NOT NULL, `number` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `completedAt` INTEGER, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                  )
                  db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_bookId` ON `chapters` (`bookId`)")
                  SyllabusDatabase.MIGRATION_1_2.migrate(db)
                  db.execSQL("INSERT INTO `books` (`id`, `name`, `createdAt`) VALUES (1, 'Biology', 1000)")
                  db.execSQL(
                    "INSERT INTO `chapters` (`id`, `bookId`, `name`, `number`, `isCompleted`, `completedAt`) VALUES (10, 1, 'Genetics', 2, 1, 5000)"
                  )
                  db.execSQL(
                    """
                    INSERT INTO `reading_sessions` (
                      `id`, `sessionId`, `bookId`, `chapterId`, `mode`, `status`,
                      `startWallTimeMs`, `endWallTimeMs`, `activeFocusMs`, `pauseMs`,
                      `breakMs`, `goalMs`, `notes`, `sessionDate`, `createdAt`
                    ) VALUES (
                      100, 'sess-v2', 1, 10, 'STOPWATCH', 'SAVED',
                      4000, 10000, 6000, 0, 0, NULL, 'v2 note', '2026-11-18', 10000
                    )
                    """
                      .trimIndent()
                  )
                }

                override fun onUpgrade(
                  db: androidx.sqlite.db.SupportSQLiteDatabase,
                  oldVersion: Int,
                  newVersion: Int,
                ) = Unit
              }
            )
            .build()
        )
      helper.writableDatabase.close()
      helper.close()

      val migrated =
        Room.databaseBuilder(context, SyllabusDatabase::class.java, "migration_v2_test.db")
          .allowMainThreadQueries()
          .addMigrations(
            SyllabusDatabase.MIGRATION_1_2,
            SyllabusDatabase.MIGRATION_2_3,
            SyllabusDatabase.MIGRATION_3_4,
          )
          .build()

      assertEquals(1, migrated.syllabusDao().getAllBooksOnce().size)
      assertEquals(1, migrated.syllabusDao().getAllChaptersOnce().size)
      val sessions = migrated.timerDao().getAllSessionsOnce()
      assertEquals(1, sessions.size)
      assertEquals("sess-v2", sessions.first().sessionId)

      val indexCursor =
        migrated.openHelper.readableDatabase.query(
          "SELECT name FROM sqlite_master WHERE type = 'index'"
        )
      val indexNames = mutableSetOf<String>()
      indexCursor.use { cursor ->
        while (cursor.moveToNext()) {
          indexNames.add(cursor.getString(0))
        }
      }
      assertTrue("index_chapters_bookId_isCompleted" in indexNames)
      assertTrue("index_chapters_completedAt" in indexNames)
      assertTrue("index_reading_sessions_sessionDate_startWallTimeMs" in indexNames)
      assertTrue("index_reading_sessions_startWallTimeMs" in indexNames)

      migrated.close()
      dbFile.delete()
    }

  @Test
  fun `migration 3 to 4 adds phaseCarryMs and pausedFromStatus without losing active timer or sessions`() =
    runTest {
      val dbFile = context.getDatabasePath("migration_v3_test.db")
      dbFile.parentFile?.mkdirs()
      if (dbFile.exists()) dbFile.delete()

      val factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
      val helper =
        factory.create(
          androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration_v3_test.db")
            .callback(
              object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                  db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                  )
                  db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `name` TEXT NOT NULL, `number` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `completedAt` INTEGER, FOREIGN KEY(`bookId`) REFERENCES `books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                  )
                  db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_bookId` ON `chapters` (`bookId`)")
                  SyllabusDatabase.MIGRATION_1_2.migrate(db)
                  SyllabusDatabase.MIGRATION_2_3.migrate(db)
                  db.execSQL("INSERT INTO `books` (`id`, `name`, `createdAt`) VALUES (1, 'Chemistry', 1000)")
                  db.execSQL(
                    "INSERT INTO `chapters` (`id`, `bookId`, `name`, `number`, `isCompleted`, `completedAt`) VALUES (10, 1, 'Kinetics', 1, 0, NULL)"
                  )
                  db.execSQL(
                    """
                    INSERT INTO `active_timer_state` (
                      `id`, `sessionId`, `bookId`, `chapterId`, `mode`, `status`,
                      `focusDurationMs`, `breakDurationMs`, `goalMs`, `notes`,
                      `sessionStartWallTimeMs`, `phaseStartElapsedMs`, `phaseStartWallTimeMs`,
                      `accumulatedFocusMs`, `accumulatedBreakMs`, `accumulatedPauseMs`,
                      `pauseStartElapsedMs`, `pauseStartWallTimeMs`, `lastKnownWallTimeMs`,
                      `lapCount`, `goalNotified`, `pomodoroCycle`, `statusBeforeStopSummary`
                    ) VALUES (
                      1, 'sess-v3', 1, 10, 'POMODORO', 'RUNNING',
                      1500000, 300000, NULL, '',
                      1000, 1000, 1000, 0, 0, 0, NULL, NULL, 2000, 0, 0, 1, NULL
                    )
                    """
                      .trimIndent()
                  )
                }

                override fun onUpgrade(
                  db: androidx.sqlite.db.SupportSQLiteDatabase,
                  oldVersion: Int,
                  newVersion: Int,
                ) = Unit
              }
            )
            .build()
        )
      helper.writableDatabase.close()
      helper.close()

      val migrated =
        Room.databaseBuilder(context, SyllabusDatabase::class.java, "migration_v3_test.db")
          .allowMainThreadQueries()
          .addMigrations(
            SyllabusDatabase.MIGRATION_1_2,
            SyllabusDatabase.MIGRATION_2_3,
            SyllabusDatabase.MIGRATION_3_4,
          )
          .build()

      val active = migrated.timerDao().getActiveTimerStateOnce()
      assertNotNull(active)
      assertEquals("sess-v3", active!!.sessionId)
      assertEquals(0L, active.phaseCarryMs)
      assertNull(active.pausedFromStatus)

      migrated.close()
      dbFile.delete()
    }

  @Test
  fun `stopwatch start pause resume lap save tracks exact focus and pause times and blocks duplicate session`() =
    runTest {
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "Physics"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Optics", number = 3))

      assertTrue(
        repo.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.STOPWATCH,
          goalMinutes = 30,
          notes = "Wave optics",
        )
      )
      assertFalse(
        repo.startSession(bookId = bookId, chapterId = chapterId, mode = TimerMode.STOPWATCH)
      )

      clock.advance(120_000L) // +2m focus
      repo.recordLap()
      repo.pauseSession()

      clock.advance(30_000L) // +30s paused
      repo.resumeSession()

      clock.advance(60_000L) // +1m focus
      assertTrue(repo.saveCurrentSession("Wave optics + lens"))

      val sessions = db.timerDao().getAllSessionsOnce()
      assertEquals(1, sessions.size)
      val saved = sessions.first()
      assertEquals(180_000L, saved.activeFocusMs)
      assertEquals(30_000L, saved.pauseMs)
      assertEquals("Wave optics + lens", saved.notes)
      assertNull(db.timerDao().getActiveTimerStateOnce())

      val events = db.timerDao().getEventsForSessionOnce(saved.sessionId.substringBefore("#"))
      assertTrue(events.any { it.eventType == "LAP" && it.elapsedAtEventMs == 120_000L })
      assertTrue(events.any { it.eventType == "STOP" })
    }

  @Test
  fun `midnight split apportions duration across both dates without losing milliseconds or duplicating`() =
    runTest {
      val zone = ZoneId.of("UTC")
      val startWall = ZonedDateTime.of(2026, 11, 18, 23, 50, 0, 0, zone).toInstant().toEpochMilli()
      val endWall = ZonedDateTime.of(2026, 11, 19, 0, 10, 0, 0, zone).toInstant().toEpochMilli()

      val slices =
        TimerMath.splitSessionAcrossDays(
          baseSessionId = "sess-midnight",
          bookId = 1L,
          chapterId = 2L,
          mode = TimerMode.STOPWATCH,
          startWallTimeMs = startWall,
          endWallTimeMs = endWall,
          activeFocusMs = 1_200_000L,
          pauseMs = 120_000L,
          breakMs = 0L,
          goalMs = null,
          notes = "Late night study",
          zoneId = zone,
        )

      assertEquals(2, slices.size)
      assertEquals("2026-11-18", slices[0].sessionDate)
      assertEquals("2026-11-19", slices[1].sessionDate)
      assertEquals(600_000L, slices[0].activeFocusMs)
      assertEquals(600_000L, slices[1].activeFocusMs)
      assertEquals(1_200_000L, slices.sumOf { it.activeFocusMs })
      assertEquals(120_000L, slices.sumOf { it.pauseMs })
    }

  @Test
  fun `crash recovery detects unfinished session on launch and supports save up to last known time`() =
    runTest {
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "Chemistry"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Bonds", number = 2))

      assertTrue(repo.startSession(bookId = bookId, chapterId = chapterId, mode = TimerMode.STOPWATCH))
      clock.advance(45_000L)
      repo.touchLastKnownWallTime()

      clock.advance(5_000_000L)
      val freshRepo =
        TimerRepository(
          timerDao = db.timerDao(),
          syllabusDao = db.syllabusDao(),
          runInTransaction = { block -> db.withTransaction { block() } },
          clock = clock,
          zoneId = ZoneId.of("UTC"),
        )
      freshRepo.checkUnfinishedSessionOnLaunch()
      val prompt = freshRepo.recoveryPrompt.first()
      assertNotNull(prompt)
      assertEquals(45_000L, prompt!!.recoveredMetrics.activeFocusMs)

      assertTrue(freshRepo.saveRecoveredSessionUpToLastKnownTime())
      assertNull(freshRepo.recoveryPrompt.first())
      val saved = db.timerDao().getAllSessionsOnce()
      assertEquals(1, saved.size)
      assertEquals(45_000L, saved.first().activeFocusMs)
    }

  @Test
  fun `pomodoro transitions via fireEvent chain next phase and skipBreak suppresses break end`() =
    runTest {
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "Biology"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Cells", number = 1))

      val syncedTargets = mutableListOf<List<ScheduledAlarmTarget>>()
      val dedup = PrefsAlarmDeduplicator(context)
      val alarmRepo =
        TimerRepository(
          timerDao = db.timerDao(),
          syllabusDao = db.syllabusDao(),
          runInTransaction = { block -> db.withTransaction { block() } },
          clock = clock,
          zoneId = ZoneId.of("UTC"),
          alarmDeduplicator = dedup,
          onSyncAlarm = { state ->
            syncedTargets.add(
              TimerAlarmScheduler.computeScheduledTargets(
                state = state,
                nowElapsedMs = clock.elapsedMs,
                nowWallMs = clock.wallMs,
              )
            )
          },
        )

      assertTrue(
        alarmRepo.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.POMODORO,
          focusMinutes = 25,
          breakMinutes = 5,
        )
      )
      val startTargets = syncedTargets.last()
      assertEquals(1, startTargets.size)
      assertEquals(TimerAlarmEventType.FOCUS_END, startTargets[0].eventType)
      val focusTriggerWall = clock.wallMs + 25 * 60_000L
      assertEquals(focusTriggerWall, startTargets[0].triggerWallMs)
      val sessionId = db.timerDao().getActiveTimerStateOnce()!!.sessionId

      // Focus ends via PRIMARY -> enters BREAK, returns outcome once, and immediately chains BREAK_END
      clock.advance(25 * 60_000L)
      val focusOutcome =
        alarmRepo.fireEvent(
          source = AlarmFireSource.PRIMARY,
          eventType = TimerAlarmEventType.FOCUS_END,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = focusTriggerWall,
        )
      assertNotNull(focusOutcome)
      assertEquals("Break started", focusOutcome!!.title)
      assertEquals(0L, focusOutcome.delayMs)
      assertEquals(TimerStatus.BREAK, focusOutcome.updatedState.status)
      assertEquals(25 * 60_000L, focusOutcome.updatedState.accumulatedFocusMs)

      // Duplicate BACKUP arrival 2000ms later is ignored
      clock.advance(TimerAlarmScheduler.BACKUP_OFFSET_MS)
      assertNull(
        alarmRepo.fireEvent(
          source = AlarmFireSource.BACKUP,
          eventType = TimerAlarmEventType.FOCUS_END,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = focusTriggerWall,
        )
      )

      val breakTargets = syncedTargets.last()
      assertEquals(1, breakTargets.size)
      assertEquals(TimerAlarmEventType.BREAK_END, breakTargets[0].eventType)
      val breakTriggerWall = breakTargets[0].triggerWallMs

      // Break ends via BACKUP -> advances to cycle 2 RUNNING and rejects late PRIMARY duplicate
      clock.advance(5 * 60_000L)
      val breakOutcome =
        alarmRepo.fireEvent(
          source = AlarmFireSource.BACKUP,
          eventType = TimerAlarmEventType.BREAK_END,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = breakTriggerWall,
        )
      assertNotNull(breakOutcome)
      assertEquals("Break over, back to focus", breakOutcome!!.title)
      assertEquals(TimerStatus.RUNNING, breakOutcome.updatedState.status)
      assertEquals(2, breakOutcome.updatedState.pomodoroCycle)

      assertNull(
        alarmRepo.fireEvent(
          source = AlarmFireSource.PRIMARY,
          eventType = TimerAlarmEventType.BREAK_END,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = breakTriggerWall,
        )
      )

      val cycle2Targets = syncedTargets.last()
      assertEquals(1, cycle2Targets.size)
      assertEquals(TimerAlarmEventType.FOCUS_END, cycle2Targets[0].eventType)
      val cycle2TriggerWall = clock.wallMs + 25 * 60_000L

      // Reach cycle 2 break, then skipBreak -> skips to cycle 3 and rejects stale cycle-2 BREAK_END
      clock.advance(25 * 60_000L)
      assertNotNull(
        alarmRepo.fireEvent(
          source = AlarmFireSource.PRIMARY,
          eventType = TimerAlarmEventType.FOCUS_END,
          sessionId = sessionId,
          phaseIndex = 2,
          expectedEventWallMs = cycle2TriggerWall,
        )
      )
      alarmRepo.skipBreak()
      val afterSkipState = db.timerDao().getActiveTimerStateOnce()!!
      assertEquals(TimerStatus.RUNNING, afterSkipState.status)
      assertEquals(3, afterSkipState.pomodoroCycle)
      assertEquals(TimerAlarmEventType.FOCUS_END, syncedTargets.last().single().eventType)

      clock.advance(5 * 60_000L)
      assertNull(
        alarmRepo.fireEvent(
          source = AlarmFireSource.BACKUP,
          eventType = TimerAlarmEventType.BREAK_END,
          sessionId = sessionId,
          phaseIndex = 2,
          expectedEventWallMs = clock.wallMs,
        )
      )
    }

  @Test
  fun `pomodoro with goal schedules both alarms separately, cancels goal during break, and reschedules on goal edit`() =
    runTest {
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "Math"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Calculus", number = 1))

      val syncedTargets = mutableListOf<List<ScheduledAlarmTarget>>()
      val alarmRepo =
        TimerRepository(
          timerDao = db.timerDao(),
          syllabusDao = db.syllabusDao(),
          runInTransaction = { block -> db.withTransaction { block() } },
          clock = clock,
          zoneId = ZoneId.of("UTC"),
          onSyncAlarm = { state ->
            syncedTargets.add(
              TimerAlarmScheduler.computeScheduledTargets(
                state = state,
                nowElapsedMs = clock.elapsedMs,
                nowWallMs = clock.wallMs,
              )
            )
          },
        )

      assertTrue(
        alarmRepo.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.POMODORO,
          focusMinutes = 25,
          breakMinutes = 5,
          goalMinutes = 40,
        )
      )

      val initialTargets = syncedTargets.last()
      assertEquals(2, initialTargets.size)
      assertEquals(clock.wallMs + 25 * 60_000L, initialTargets.first { it.eventType == TimerAlarmEventType.FOCUS_END }.triggerWallMs)
      assertEquals(clock.wallMs + 40 * 60_000L, initialTargets.first { it.eventType == TimerAlarmEventType.GOAL_REACHED }.triggerWallMs)

      val sessionId = db.timerDao().getActiveTimerStateOnce()!!.sessionId
      clock.advance(25 * 60_000L)
      assertNotNull(
        alarmRepo.fireEvent(
          source = AlarmFireSource.PRIMARY,
          eventType = TimerAlarmEventType.FOCUS_END,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = clock.wallMs,
        )
      )
      // During BREAK, only BREAK_END is scheduled (goal paused during break)
      assertEquals(listOf(TimerAlarmEventType.BREAK_END), syncedTargets.last().map { it.eventType })

      clock.advance(5 * 60_000L)
      assertNotNull(
        alarmRepo.fireEvent(
          source = AlarmFireSource.PRIMARY,
          eventType = TimerAlarmEventType.BREAK_END,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = clock.wallMs,
        )
      )
      // Cycle 2 RUNNING: 25m already accumulated toward 40m goal -> GOAL_REACHED at +15m
      val cycle2Targets = syncedTargets.last()
      assertEquals(2, cycle2Targets.size)
      assertEquals(clock.wallMs + 25 * 60_000L, cycle2Targets.first { it.eventType == TimerAlarmEventType.FOCUS_END }.triggerWallMs)
      assertEquals(clock.wallMs + 15 * 60_000L, cycle2Targets.first { it.eventType == TimerAlarmEventType.GOAL_REACHED }.triggerWallMs)

      // Update goal mid-session from 40m to 60m -> GOAL_REACHED shifts to +35m remaining
      alarmRepo.updateActiveGoal(60)
      assertEquals(
        clock.wallMs + 35 * 60_000L,
        syncedTargets.last().first { it.eventType == TimerAlarmEventType.GOAL_REACHED }.triggerWallMs,
      )

      // Fire GOAL_REACHED at +35m
      clock.advance(35 * 60_000L)
      val goalOutcome =
        alarmRepo.fireEvent(
          source = AlarmFireSource.PRIMARY,
          eventType = TimerAlarmEventType.GOAL_REACHED,
          sessionId = sessionId,
          phaseIndex = 2,
          expectedEventWallMs = clock.wallMs,
        )
      assertNotNull(goalOutcome)
      assertEquals("Goal completed", goalOutcome!!.title)
      assertTrue(goalOutcome.updatedState.goalNotified)

      // Raising goal above 60m after goal already fired clears dedup and schedules new goal
      alarmRepo.updateActiveGoal(70)
      val raisedGoalTarget =
        syncedTargets.last().firstOrNull { it.eventType == TimerAlarmEventType.GOAL_REACHED }
      assertNotNull(raisedGoalTarget)
      assertEquals(clock.wallMs + 10 * 60_000L, raisedGoalTarget!!.triggerWallMs)
    }

  @Test
  fun `stopwatch goal pause cancels alarms resume reschedules remaining and 15s lateness is reported`() =
    runTest {
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "Chemistry"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Acids", number = 1))

      val syncedTargets = mutableListOf<List<ScheduledAlarmTarget>>()
      val alarmRepo =
        TimerRepository(
          timerDao = db.timerDao(),
          syllabusDao = db.syllabusDao(),
          runInTransaction = { block -> db.withTransaction { block() } },
          clock = clock,
          zoneId = ZoneId.of("UTC"),
          onSyncAlarm = { state ->
            syncedTargets.add(
              TimerAlarmScheduler.computeScheduledTargets(
                state = state,
                nowElapsedMs = clock.elapsedMs,
                nowWallMs = clock.wallMs,
              )
            )
          },
        )

      assertTrue(
        alarmRepo.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.STOPWATCH,
          goalMinutes = 10,
        )
      )
      val sessionId = db.timerDao().getActiveTimerStateOnce()!!.sessionId
      assertEquals(clock.wallMs + 10 * 60_000L, syncedTargets.last().single().triggerWallMs)

      // Pause after 4m -> cancels all alarms
      clock.advance(4 * 60_000L)
      alarmRepo.pauseSession()
      assertTrue(syncedTargets.last().isEmpty())

      // Firing while paused returns null
      clock.advance(20 * 60_000L)
      assertNull(
        alarmRepo.fireEvent(
          source = AlarmFireSource.BACKUP,
          eventType = TimerAlarmEventType.GOAL_REACHED,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = clock.wallMs - 14 * 60_000L,
        )
      )

      // Resume -> reschedules remaining 6m from current wall time
      alarmRepo.resumeSession()
      val resumedTriggerWall = clock.wallMs + 6 * 60_000L
      assertEquals(resumedTriggerWall, syncedTargets.last().single().triggerWallMs)

      // Fire 18s late -> player uses outcome.delayMs > 15_000L to skip sound/vibration
      clock.advance(6 * 60_000L + 18_000L)
      val lateOutcome =
        alarmRepo.fireEvent(
          source = AlarmFireSource.BACKUP,
          eventType = TimerAlarmEventType.GOAL_REACHED,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = resumedTriggerWall,
        )
      assertNotNull(lateOutcome)
      assertEquals(18_000L, lateOutcome!!.delayMs)
      assertTrue(lateOutcome.delayMs > TimerAlarmScheduler.LATE_THRESHOLD_MS)
    }

  @Test
  fun `alarm scheduler schedules AlarmManager setAlarmClock backup at eventTime plus 2000ms and cancels cleanly`() {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val shadowAlarmManager = Shadows.shadowOf(alarmManager)
    ShadowAlarmManager.setCanScheduleExactAlarms(true)

    val state =
      ActiveTimerStateEntity(
        id = 1,
        sessionId = "sess-alarm-clock",
        bookId = 1L,
        chapterId = 1L,
        mode = TimerMode.POMODORO,
        status = TimerStatus.RUNNING,
        focusDurationMs = 25 * 60_000L,
        breakDurationMs = 5 * 60_000L,
        goalMs = 60 * 60_000L,
        notes = "",
        sessionStartWallTimeMs = clock.wallMs,
        phaseStartElapsedMs = clock.elapsedMs,
        phaseStartWallTimeMs = clock.wallMs,
        accumulatedFocusMs = 0L,
        accumulatedBreakMs = 0L,
        accumulatedPauseMs = 0L,
        pauseStartElapsedMs = null,
        pauseStartWallTimeMs = null,
        lastKnownWallTimeMs = clock.wallMs,
        lapCount = 0,
        goalNotified = false,
        pomodoroCycle = 1,
        statusBeforeStopSummary = null,
      )

    TimerAlarmScheduler.syncAlarms(context, state, clock)
    val timerActions = TimerAlarmEventType.entries.map { it.action }.toSet()
    val scheduledTimerAlarms =
      shadowAlarmManager.scheduledAlarms.filter {
        Shadows.shadowOf(it.operation).savedIntent.action in timerActions
      }
    assertEquals(2, scheduledTimerAlarms.size)
    assertNotNull(alarmManager.nextAlarmClock)

    val focusAlarm =
      scheduledTimerAlarms.first {
        Shadows.shadowOf(it.operation).savedIntent.action == TimerAlarmEventType.FOCUS_END.action
      }
    val expectedFocusExactWall = clock.wallMs + 25 * 60_000L
    assertEquals(
      expectedFocusExactWall + TimerAlarmScheduler.BACKUP_OFFSET_MS,
      focusAlarm.triggerAtTime,
    )
    assertEquals(
      expectedFocusExactWall,
      Shadows.shadowOf(focusAlarm.operation).savedIntent.getLongExtra(
        TimerAlarmScheduler.EXTRA_TRIGGER_WALL_MS,
        -1L,
      ),
    )

    TimerAlarmScheduler.cancelAllAlarms(context)
    val remainingTimerAlarms =
      shadowAlarmManager.scheduledAlarms.filter {
        Shadows.shadowOf(it.operation).savedIntent.action in timerActions
      }
    assertTrue(remainingTimerAlarms.isEmpty())
    assertNull(alarmManager.nextAlarmClock)
    ShadowAlarmManager.setCanScheduleExactAlarms(false)
  }

  @Test
  fun `boot and time change reschedule behavior handles stale suppression and future wall times`() =
    runTest {
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "History"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Empires", number = 1))

      val syncedWallTargets = mutableListOf<List<ScheduledAlarmTarget>>()
      val syncedNormalTargets = mutableListOf<List<ScheduledAlarmTarget>>()
      val bootRepo =
        TimerRepository(
          timerDao = db.timerDao(),
          syllabusDao = db.syllabusDao(),
          runInTransaction = { block -> db.withTransaction { block() } },
          clock = clock,
          zoneId = ZoneId.of("UTC"),
          onSyncAlarm = { state ->
            syncedNormalTargets.add(
              TimerAlarmScheduler.computeScheduledTargets(
                state = state,
                nowElapsedMs = clock.elapsedMs,
                nowWallMs = clock.wallMs,
                fromStoredWallTimesOnly = false,
              )
            )
          },
          onSyncWallAlarms = { state ->
            syncedWallTargets.add(
              TimerAlarmScheduler.computeScheduledTargets(
                state = state,
                nowElapsedMs = clock.elapsedMs,
                nowWallMs = clock.wallMs,
                fromStoredWallTimesOnly = true,
              )
            )
          },
        )

      // 1. Reboot with >15s past focus end -> stale sound/notification suppressed
      assertTrue(
        bootRepo.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.POMODORO,
          focusMinutes = 25,
          breakMinutes = 5,
        )
      )
      clock.advance(26 * 60_000L)
      val staleNotifications = bootRepo.handleSystemTimeOrBootReschedule(isReboot = true)
      assertTrue(staleNotifications.isEmpty())
      bootRepo.discardCurrentSession()

      // 2. Reboot with <=15s past goal -> emits silent BootLateNotification & marks goalNotified
      assertTrue(
        bootRepo.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.STOPWATCH,
          goalMinutes = 5,
        )
      )
      clock.advance(5 * 60_000L + 5_000L)
      val recentNotifications = bootRepo.handleSystemTimeOrBootReschedule(isReboot = true)
      assertEquals(1, recentNotifications.size)
      assertEquals("Goal completed", recentNotifications.first().title)
      assertTrue(db.timerDao().getActiveTimerStateOnce()!!.goalNotified)
      bootRepo.discardCurrentSession()

      // 3. Time change (non-reboot) reschedules from monotonic elapsed remaining time
      assertTrue(
        bootRepo.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.POMODORO,
          focusMinutes = 25,
          breakMinutes = 5,
        )
      )
      clock.elapsedMs += 10 * 60_000L
      clock.wallMs += 3_600_000L // system clock jumped 1 hour ahead
      bootRepo.handleSystemTimeOrBootReschedule(isReboot = false)
      assertEquals(
        clock.wallMs + 15 * 60_000L,
        syncedNormalTargets.last().single().triggerWallMs,
      )
    }

  @Test
  fun `analytics calculator computes Sat-Fri bars, delta percent, top-4 plus Others slices, and streak from real sessions`() {
    val zone = ZoneId.of("UTC")
    val today = java.time.LocalDate.of(2026, 11, 18) // Wednesday; Sat-Fri week is Nov 14..20
    val yesterday = today.minusDays(1) // Tuesday Nov 17
    val lastWeekWed = today.minusDays(7) // Nov 11

    fun makeSession(
      id: String,
      bookId: Long,
      bookName: String,
      date: java.time.LocalDate,
      focusMs: Long,
    ): com.example.data.local.dao.ReadingSessionWithMeta {
      val wall = date.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L
      return com.example.data.local.dao.ReadingSessionWithMeta(
        session =
          com.example.data.local.entity.ReadingSessionEntity(
            sessionId = id,
            bookId = bookId,
            chapterId = 1L,
            mode = TimerMode.STOPWATCH,
            status = "SAVED",
            startWallTimeMs = wall,
            endWallTimeMs = wall + focusMs,
            activeFocusMs = focusMs,
            pauseMs = 0L,
            breakMs = 0L,
            goalMs = null,
            notes = "",
            sessionDate = date.toString(),
            createdAt = wall + focusMs,
          ),
        bookName = bookName,
        chapterName = "Ch 1",
        chapterNumber = 1,
      )
    }

    val sessions =
      listOf(
        makeSession("s1", 1L, "Mathematics", today, 2 * 3_600_000L),
        makeSession("s2", 2L, "Bangla", today, 90 * 60_000L),
        makeSession("s3", 3L, "English", yesterday, 60 * 60_000L),
        makeSession("s4", 4L, "Science", yesterday, 30 * 60_000L),
        makeSession("s5", 5L, "History", yesterday, 30 * 60_000L),
        makeSession("s-prev", 1L, "Mathematics", lastWeekWed, 165 * 60_000L),
      )

    val result =
      com.example.timer.TimerAnalyticsCalculator.compute(
        sessions = sessions,
        period = com.example.timer.AnalyticsPeriod.THIS_WEEK,
        today = today,
        zoneId = zone,
      )

    assertEquals(210 * 60_000L, result.todayTotalMs)
    assertEquals(330 * 60_000L, result.periodTotalMs)
    assertEquals(100, result.deltaPercent)
    assertEquals(2, result.currentStreakDays)
    assertEquals(7, result.bars.size)
    assertEquals("Sat", result.bars.first().label)
    assertEquals("Fri", result.bars.last().label)
    assertTrue(result.bars.first { it.label == "Wed" }.isHighest)

    assertEquals(5, result.bookSlices.size)
    assertEquals("Mathematics", result.bookSlices[0].bookName)
    assertEquals("Others", result.bookSlices[4].bookName)
    assertEquals(30 * 60_000L, result.bookSlices[4].totalMs)
  }

  @Test
  fun `dnd interruption filter allows alarms only when policy permits alarms`() {
    assertFalse(
      TimerAlarmPlayer.isBlockedByDoNotDisturb(
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALL,
        priorityCategories = 0,
      )
    )
    assertTrue(
      TimerAlarmPlayer.isBlockedByDoNotDisturb(
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_NONE,
        priorityCategories = NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
      )
    )
    assertFalse(
      TimerAlarmPlayer.isBlockedByDoNotDisturb(
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS,
        priorityCategories = 0,
      )
    )
    assertTrue(
      TimerAlarmPlayer.isBlockedByDoNotDisturb(
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        priorityCategories = 0,
      )
    )
    assertFalse(
      TimerAlarmPlayer.isBlockedByDoNotDisturb(
        interruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        priorityCategories = NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
      )
    )
  }

  @Test
  fun `backup supports timer alarm and vibration toggles and imports legacy v1 JSON cleanly`() =
    runTest {
      val prefs = UserPreferencesRepository(context)
      val syllabusRepo = SyllabusRepository(db.syllabusDao(), prefs)
      val bookId = syllabusRepo.addBook("English")
      syllabusRepo.addChapter(bookId, "Essay", 1, false)
      syllabusRepo.setTimerAlarmEnabled(false)
      syllabusRepo.setTimerVibrationEnabled(false)

      val exportedJson = syllabusRepo.exportBackupJson("1.0")
      val valid = BackupManager.parseAndValidate(exportedJson)
      assertTrue(valid is BackupValidationResult.Valid)
      val parsed = (valid as BackupValidationResult.Valid).backup
      assertFalse(parsed.settings.timerAlarmEnabled)
      assertFalse(parsed.settings.timerVibrationEnabled)

      val legacyJson =
        """
        {
          "appVersion": "1.0",
          "schemaVersion": 1,
          "exportedAt": 1763320000000,
          "settings": {
            "dailyGoal": 2,
            "reminderEnabled": true,
            "reminderHour": 20,
            "reminderMinute": 0
          },
          "books": [
            { "id": 1, "name": "Math", "createdAt": 1763310000000 }
          ],
          "chapters": [
            { "id": 10, "bookId": 1, "name": "Sets", "number": 1, "isCompleted": false, "completedAt": null, "createdAt": 1763310000000 }
          ]
        }
        """.trimIndent()
      val legacyValid = BackupManager.parseAndValidate(legacyJson)
      assertTrue(legacyValid is BackupValidationResult.Valid)
      val restoredPrefs =
        syllabusRepo.importBackup((legacyValid as BackupValidationResult.Valid).backup, BackupImportMode.REPLACE)
      assertTrue(restoredPrefs.timerAlarmEnabled)
      assertTrue(restoredPrefs.timerVibrationEnabled)
    }

  @Test
  fun `primary vs backup duplicate prevention logs skipped duplicate and launch check restores future wall alarms`() =
    runTest {
      ShadowLog.clear()
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "Physics"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Waves", number = 1))

      var wallSyncCount = 0
      var normalCancelCount = 0
      val dedup = PrefsAlarmDeduplicator(context)
      val sharedRepo =
        TimerRepository(
          timerDao = db.timerDao(),
          syllabusDao = db.syllabusDao(),
          runInTransaction = { block -> db.withTransaction { block() } },
          clock = clock,
          zoneId = ZoneId.of("UTC"),
          alarmDeduplicator = dedup,
          onSyncAlarm = { state -> if (state == null) normalCancelCount++ },
          onSyncWallAlarms = { wallSyncCount++ },
        )

      assertTrue(
        sharedRepo.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.POMODORO,
          focusMinutes = 25,
          breakMinutes = 5,
        )
      )
      val sessionId = db.timerDao().getActiveTimerStateOnce()!!.sessionId
      val focusWall = clock.wallMs + 25 * 60_000L

      // Primary fires at exact time
      clock.advance(25 * 60_000L)
      assertNotNull(
        sharedRepo.fireEvent(
          source = AlarmFireSource.PRIMARY,
          eventType = TimerAlarmEventType.FOCUS_END,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = focusWall,
        )
      )

      // Backup fires 2000ms later -> returns null and logs "skipped duplicate" under TimerAlarm
      clock.advance(TimerAlarmScheduler.BACKUP_OFFSET_MS)
      assertNull(
        sharedRepo.fireEvent(
          source = AlarmFireSource.BACKUP,
          eventType = TimerAlarmEventType.FOCUS_END,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = focusWall,
        )
      )
      val timerAlarmLogs = ShadowLog.getLogsForTag("TimerAlarm").map { it.msg }
      assertTrue(timerAlarmLogs.any { it.contains("skipped duplicate") })

      // Simulate cold process restart while BREAK has future BREAK_END target -> checkUnfinishedSessionOnLaunch restores wall alarm
      val freshRepo =
        TimerRepository(
          timerDao = db.timerDao(),
          syllabusDao = db.syllabusDao(),
          runInTransaction = { block -> db.withTransaction { block() } },
          clock = clock,
          zoneId = ZoneId.of("UTC"),
          alarmDeduplicator = dedup,
          onSyncAlarm = { state -> if (state == null) normalCancelCount++ },
          onSyncWallAlarms = { wallSyncCount++ },
        )
      freshRepo.checkUnfinishedSessionOnLaunch()
      assertEquals(1, wallSyncCount)
    }

  @Test
  fun `permission checklist preference persists and timer_alarm channel sound check is true by default`() =
    runTest {
      val prefs = UserPreferencesRepository(context)
      assertFalse(prefs.getCurrentPreferences().timerPermissionChecklistShown)
      prefs.setTimerPermissionChecklistShown(true)
      assertTrue(prefs.getCurrentPreferences().timerPermissionChecklistShown)

      assertTrue(NotificationHelper.isTimerAlarmChannelSoundAllowed(context))
    }

  @Test
  fun `sql analytics aggregate queries match in-memory compute across all periods`() = runTest {
    val zone = ZoneId.systemDefault()
    val today = java.time.LocalDate.of(2026, 11, 18)
    val yesterday = today.minusDays(1)
    val lastWeek = today.minusDays(7)

    val dao = db.syllabusDao()
    val timerDao = db.timerDao()
    val bookIds = (1..5).map { idx -> dao.insertBook(BookEntity(name = "Subject $idx")) }
    val chapterIds =
      bookIds.map { bId -> dao.insertChapter(ChapterEntity(bookId = bId, name = "Ch 1", number = 1)) }

    suspend fun insertSaved(id: String, bookIdx: Int, date: java.time.LocalDate, focusMs: Long) {
      val wall = date.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L
      timerDao.insertSession(
        ReadingSessionEntity(
          sessionId = id,
          bookId = bookIds[bookIdx],
          chapterId = chapterIds[bookIdx],
          mode = TimerMode.STOPWATCH,
          status = "SAVED",
          startWallTimeMs = wall,
          endWallTimeMs = wall + focusMs,
          activeFocusMs = focusMs,
          pauseMs = 0L,
          breakMs = 0L,
          goalMs = null,
          notes = "",
          sessionDate = date.toString(),
          createdAt = wall + focusMs,
        )
      )
    }

    insertSaved("sql-1", 0, today, 2 * 3_600_000L)
    insertSaved("sql-2", 1, today, 90 * 60_000L)
    insertSaved("sql-3", 2, yesterday, 60 * 60_000L)
    insertSaved("sql-4", 3, yesterday, 30 * 60_000L)
    insertSaved("sql-5", 4, yesterday, 30 * 60_000L)
    insertSaved("sql-prev", 0, lastWeek, 165 * 60_000L)

    val allSessions = timerDao.observeAllSessionsWithMeta().first()
    val todayMs = timerDao.observeTodayFocusMs(today.toString()).first()
    val activeDates = timerDao.observeDistinctActiveDatesDescending().first()

    for (period in AnalyticsPeriod.entries) {
      val bounds = TimerAnalyticsCalculator.periodBounds(period, today)
      val periodTotal =
        timerDao.observeTotalFocusMsBetween(bounds.start.toString(), bounds.end.toString()).first()
      val prevTotal =
        timerDao
          .observeTotalFocusMsBetween(bounds.prevStart.toString(), bounds.prevEnd.toString())
          .first()
      val dailyRows =
        timerDao.observeDailyFocusTotalsBetween(bounds.start.toString(), bounds.end.toString()).first()
      val bookRows =
        timerDao.observeBookFocusTotalsBetween(bounds.start.toString(), bounds.end.toString()).first()

      val sqlResult =
        TimerAnalyticsCalculator.computeFromAggregates(
          period = period,
          todayTotalMs = todayMs,
          periodTotalMs = periodTotal,
          previousPeriodTotalMs = prevTotal,
          dailyRows = dailyRows,
          bookRows = bookRows,
          activeDatesDesc = activeDates,
          today = today,
        )
      val memoryResult =
        TimerAnalyticsCalculator.compute(
          sessions = allSessions,
          period = period,
          today = today,
          zoneId = zone,
        )
      assertEquals(memoryResult, sqlResult)
    }
  }

  @Test
  fun `sql syllabus projections and batched 500-chapter backup import preserve data accurately`() =
    runTest {
      val prefs = UserPreferencesRepository(context)
      val syllabusRepo =
        SyllabusRepository(
          dao = db.syllabusDao(),
          prefsRepo = prefs,
          runInTransaction = { block -> db.withTransaction { block() } },
        )

      val books =
        (1..5).map { bIdx ->
          BackupBook(id = bIdx.toLong(), name = "Book $bIdx", createdAt = 1_000L + bIdx)
        }
      val nowMillis = System.currentTimeMillis()
      val chapters =
        (1..500).map { idx ->
          val bookIdx = ((idx - 1) % 5) + 1
          val completed = idx % 2 == 0
          BackupChapter(
            id = idx.toLong(),
            bookId = bookIdx.toLong(),
            name = "Chapter $idx",
            number = idx,
            isCompleted = completed,
            completedAt = if (completed) nowMillis else null,
            createdAt = 1_000L + idx,
          )
        }
      val backup =
        SyllabusBackup(
          appVersion = "1.0",
          schemaVersion = 1,
          exportedAt = nowMillis,
          settings = BackupSettings(dailyGoal = 5, reminderEnabled = true, reminderHour = 20, reminderMinute = 0),
          books = books,
          chapters = chapters,
        )

      syllabusRepo.importBackup(backup, BackupImportMode.REPLACE)

      val overall = syllabusRepo.observeOverallStats().first()
      val summaries = syllabusRepo.observeBookSummaries().first()
      assertEquals(5, overall.totalBooks)
      assertEquals(500, overall.totalChapters)
      assertEquals(250, overall.completedChapters)
      assertEquals(250, overall.remainingChapters)
      assertEquals(50, overall.overallPercentage)
      assertEquals(250, overall.completedToday)
      assertEquals(5, summaries.size)
      assertTrue(summaries.all { it.totalChapters == 100 && it.completedChapters == 50 })

      val trend = syllabusRepo.observeWeeklyTrend(StudyTrendPeriod.THIS_WEEK).first()
      assertEquals(7, trend.size)
      assertEquals(250, trend.first { it.isToday }.count)

      val cumulative = syllabusRepo.observeCumulativeSeries(StudyTrendPeriod.THIS_WEEK).first()
      assertEquals(7, cumulative.size)
      assertEquals(0.5f, cumulative.last(), 0.001f)
    }

  @Test
  fun `widget updater coalesces burst requests and skips redundant unchanged updates`() = runTest {
    val app = context.applicationContext as SyllabusApplication
    val appWidgetManager = AppWidgetManager.getInstance(app)
    val shadowManager = Shadows.shadowOf(appWidgetManager)
    shadowManager.createWidgets(
      com.example.widgets.OverviewWidgetReceiver::class.java,
      R.layout.widget_overview,
      1,
    )

    val bookId = app.syllabusRepository.addBook("Algorithms")
    try {
      app.syllabusRepository.addChapter(bookId, "Sorting", 1, isCompleted = true)

      ShadowLog.clear()
      com.example.widgets.WidgetUpdater.clearLastSummary()
      com.example.widgets.WidgetUpdater.updateNow(app, force = true)
      com.example.widgets.WidgetUpdater.updateNow(app, force = false)

      repeat(10) {
        com.example.widgets.WidgetUpdater.requestUpdate(app)
      }
      kotlinx.coroutines.delay(250L)

      val updateLogs = ShadowLog.getLogsForTag("WidgetUpdater").map { it.msg }
      assertTrue(updateLogs.any { it.startsWith("updateNow took ") })
    } finally {
      app.syllabusRepository.deleteBook(bookId)
    }
  }

  @Test
  fun `backup v2 exports all saved study history, events, goals, and settings while excluding running active timer`() =
    runTest {
      val prefs = UserPreferencesRepository(context)
      val syllabusRepo =
        SyllabusRepository(
          dao = db.syllabusDao(),
          timerDao = db.timerDao(),
          prefsRepo = prefs,
          runInTransaction = { block -> db.withTransaction { block() } },
        )

      val bookId = syllabusRepo.addBook("Physics")
      val chId = syllabusRepo.addChapter(bookId, "Mechanics", 1, isCompleted = true)
      syllabusRepo.setDailyGoal(4)
      syllabusRepo.setTimerAlarmEnabled(false)
      syllabusRepo.setTimerVibrationEnabled(true)
      syllabusRepo.setTimerPermissionChecklistShown(true)
      syllabusRepo.setPomodoroDurations(45, 15)

      // 1. Complete a session with pause, lap, and midnight crossing
      val zone = ZoneId.of("UTC")
      val startMidnight = ZonedDateTime.of(2026, 11, 17, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
      val endMidnight = ZonedDateTime.of(2026, 11, 18, 1, 30, 0, 0, zone).toInstant().toEpochMilli()
      val slices =
        TimerMath.splitSessionAcrossDays(
          baseSessionId = "sess-long-midnight",
          bookId = bookId,
          chapterId = chId,
          mode = TimerMode.POMODORO,
          startWallTimeMs = startMidnight,
          endWallTimeMs = endMidnight,
          activeFocusMs = 6_600_000L, // 1h 50m active
          pauseMs = 600_000L, // 10m pause
          breakMs = 300_000L, // 5m break
          goalMs = 7_200_000L,
          notes = "Worked derivations",
          zoneId = zone,
        )
      assertEquals(2, slices.size)
      db.timerDao().insertSessions(slices)
      db.timerDao().insertEvents(
        listOf(
          SessionEventEntity(
            sessionId = "sess-long-midnight",
            bookId = bookId,
            chapterId = chId,
            eventType = SessionEventType.START,
            elapsedAtEventMs = 0L,
            wallTimeMs = startMidnight,
          ),
          SessionEventEntity(
            sessionId = "sess-long-midnight",
            bookId = bookId,
            chapterId = chId,
            eventType = SessionEventType.LAP,
            elapsedAtEventMs = 3_600_000L,
            wallTimeMs = startMidnight + 3_600_000L,
            label = "Lap 1",
          ),
        )
      )
      db.timerDao().insertGoal(
        ReadingGoalEntity(
          periodType = GoalPeriodType.DAILY,
          targetMinutes = 120,
          isActive = true,
          createdAt = startMidnight,
        )
      )

      // 2. Start an unfinished/running timer session that must NOT be exported
      assertTrue(repo.startSession(bookId = bookId, chapterId = chId, mode = TimerMode.STOPWATCH))
      val activeSessionId = db.timerDao().getActiveTimerStateOnce()!!.sessionId

      val exportedJson = syllabusRepo.exportBackupJson("2.0")
      val validation = BackupManager.parseAndValidate(exportedJson)
      assertTrue(validation is BackupValidationResult.Valid)
      val backup = (validation as BackupValidationResult.Valid).backup

      assertEquals(2, backup.schemaVersion)
      assertEquals(1, backup.counts?.books)
      assertEquals(1, backup.counts?.chapters)
      assertEquals(2, backup.counts?.sessions)
      assertEquals(2, backup.counts?.sessionEvents)
      assertEquals(1, backup.counts?.goals)
      assertEquals(45, backup.settings.pomodoroFocusMinutes)
      assertEquals(15, backup.settings.pomodoroBreakMinutes)
      assertTrue(backup.settings.timerPermissionChecklistShown)
      assertFalse(backup.settings.timerAlarmEnabled)
      assertEquals(2, backup.sessions.size)
      assertTrue(backup.sessions.all { it.bookName == "Physics" && it.chapterName == "Mechanics" })
      assertEquals(6_600_000L, backup.sessions.sumOf { it.activeFocusMs })
      assertEquals(600_000L, backup.sessions.sumOf { it.pauseMs })
      assertEquals(300_000L, backup.sessions.sumOf { it.breakMs })
      assertFalse(backup.sessions.any { it.sessionId.contains(activeSessionId) })
      assertFalse(backup.sessionEvents.any { it.sessionId == activeSessionId })

      // 3. Simulate fresh install (clear all data & reset settings) and import in REPLACE mode
      repo.discardCurrentSession()
      db.timerDao().deleteAllSessionEvents()
      db.timerDao().deleteAllReadingSessions()
      db.timerDao().deleteAllReadingGoals()
      db.syllabusDao().deleteAllChapters()
      db.syllabusDao().deleteAllBooks()
      syllabusRepo.setPomodoroDurations(25, 5)
      syllabusRepo.setDailyGoal(2)

      val replaceResult = syllabusRepo.importBackupWithResult(backup, BackupImportMode.REPLACE)
      assertEquals(1, replaceResult.booksImported)
      assertEquals(1, replaceResult.chaptersImported)
      assertEquals(2, replaceResult.sessionsImported)
      assertEquals(2, replaceResult.sessionEventsImported)
      assertEquals(1, replaceResult.goalsImported)
      assertEquals(45, replaceResult.preferences.pomodoroFocusMinutes)
      assertEquals(15, replaceResult.preferences.pomodoroBreakMinutes)
      assertEquals(4, replaceResult.preferences.dailyGoal)

      val restoredSessions = db.timerDao().getAllSessionsOnce()
      assertEquals(2, restoredSessions.size)
      assertEquals(6_600_000L, restoredSessions.sumOf { it.activeFocusMs })
      assertEquals(listOf("2026-11-17", "2026-11-18"), restoredSessions.map { it.sessionDate }.sorted())
      assertEquals(2, db.timerDao().getAllEventsOnce().size)
      assertEquals(1, db.timerDao().getAllGoalsOnce().size)

      // 4. Re-import same backup in MERGE mode -> zero new rows and duplicate skips reported
      val mergeSameResult = syllabusRepo.importBackupWithResult(backup, BackupImportMode.MERGE)
      assertEquals(0, mergeSameResult.booksImported)
      assertEquals(0, mergeSameResult.chaptersImported)
      assertEquals(0, mergeSameResult.sessionsImported)
      assertEquals(0, mergeSameResult.sessionEventsImported)
      assertEquals(0, mergeSameResult.goalsImported)
      assertTrue(mergeSameResult.skippedDuplicates > 0)
      assertEquals(2, db.timerDao().getAllSessionsOnce().size)
    }

  @Test
  fun `merge import remaps differing bookId and chapterId while preventing duplicate sessions`() =
    runTest {
      val prefs = UserPreferencesRepository(context)
      val syllabusRepo =
        SyllabusRepository(
          dao = db.syllabusDao(),
          timerDao = db.timerDao(),
          prefsRepo = prefs,
          runInTransaction = { block -> db.withTransaction { block() } },
        )

      // Existing local DB has Book "Biology" with id=100 and Chapter #1 with id=500
      db.syllabusDao().insertBooks(listOf(BookEntity(id = 100L, name = "Biology", createdAt = 1000L)))
      db.syllabusDao()
        .insertChapters(
          listOf(
            ChapterEntity(
              id = 500L,
              bookId = 100L,
              name = "Cell Structure",
              number = 1,
              isCompleted = false,
              completedAt = null,
            )
          )
        )

      // Incoming backup has Book "biology" with id=1 and Chapter #1 with id=10
      val incomingJson =
        """
        {
          "appVersion": "2.0",
          "schemaVersion": 2,
          "exportedAt": 1763400000000,
          "settings": {
            "dailyGoal": 3,
            "reminderEnabled": true,
            "reminderHour": 19,
            "reminderMinute": 30,
            "pomodoroFocusMinutes": 50,
            "pomodoroBreakMinutes": 10
          },
          "books": [
            { "id": 1, "name": "biology", "createdAt": 1000 }
          ],
          "chapters": [
            { "id": 10, "bookId": 1, "name": "Cell Structure", "number": 1, "isCompleted": false, "completedAt": null, "createdAt": 1000 }
          ],
          "sessions": [
            {
              "id": 1,
              "sessionId": "remapped-sess-1#2026-11-18",
              "bookId": 1,
              "bookName": "biology",
              "chapterId": 10,
              "chapterName": "Cell Structure",
              "mode": "STOPWATCH",
              "status": "SAVED",
              "startWallTimeMs": 1763350000000,
              "endWallTimeMs": 1763353600000,
              "activeFocusMs": 3600000,
              "pauseMs": 0,
              "breakMs": 0,
              "activeDurationSeconds": 3600,
              "pauseDurationSeconds": 0,
              "goalMs": null,
              "notes": "Merged study",
              "sessionDate": "2026-11-18",
              "createdAt": 1763353600000
            }
          ],
          "sessionEvents": [
            {
              "id": 1,
              "sessionId": "remapped-sess-1",
              "bookId": 1,
              "chapterId": 10,
              "eventType": "START",
              "elapsedAtEventMs": 0,
              "wallTimeMs": 1763350000000,
              "label": null
            }
          ],
          "goals": [
            { "id": 1, "periodType": "WEEKLY", "targetMinutes": 600, "isActive": true, "createdAt": 1763300000000 }
          ]
        }
        """.trimIndent()

      val (existingBookIds, existingChapterIds) = syllabusRepo.getExistingBookAndChapterIds()
      val validation = BackupManager.parseAndValidate(incomingJson, existingBookIds, existingChapterIds)
      assertTrue(validation is BackupValidationResult.Valid)
      val backup = (validation as BackupValidationResult.Valid).backup

      val firstMerge = syllabusRepo.importBackupWithResult(backup, BackupImportMode.MERGE)
      assertEquals(0, firstMerge.booksImported)
      assertEquals(0, firstMerge.chaptersImported)
      assertEquals(1, firstMerge.sessionsImported)
      assertEquals(1, firstMerge.sessionEventsImported)
      assertEquals(1, firstMerge.goalsImported)

      val sessions = db.timerDao().getAllSessionsOnce()
      assertEquals(1, sessions.size)
      assertEquals(100L, sessions.first().bookId)
      assertEquals(500L, sessions.first().chapterId)
      assertEquals(3_600_000L, sessions.first().activeFocusMs)

      val events = db.timerDao().getAllEventsOnce()
      assertEquals(1, events.size)
      assertEquals(100L, events.first().bookId)
      assertEquals(500L, events.first().chapterId)

      // Importing a copy with a different sessionId but identical (startWallTimeMs, targetChapterId, activeFocusMs) skips duplicate
      val duplicateContentBackup =
        backup.copy(
          sessions = backup.sessions.map { it.copy(sessionId = "different-uuid#2026-11-18") }
        )
      val secondMerge = syllabusRepo.importBackupWithResult(duplicateContentBackup, BackupImportMode.MERGE)
      assertEquals(0, secondMerge.sessionsImported)
      assertEquals(1, db.timerDao().getAllSessionsOnce().size)
    }

  @Test
  fun `resetAfterBackupImport clears recovery prompt and alarms on replace`() = runTest {
    val bookId = db.syllabusDao().insertBook(BookEntity(name = "Chemistry"))
    val chapterId =
      db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Reactions", number = 1))

    var cancelCount = 0
    val testRepo =
      TimerRepository(
        timerDao = db.timerDao(),
        syllabusDao = db.syllabusDao(),
        runInTransaction = { block -> db.withTransaction { block() } },
        clock = clock,
        zoneId = ZoneId.of("UTC"),
        onSyncAlarm = { state -> if (state == null) cancelCount++ },
      )

    assertTrue(testRepo.startSession(bookId = bookId, chapterId = chapterId, mode = TimerMode.STOPWATCH))
    clock.advance(10_000L)

    val coldLaunchRepo =
      TimerRepository(
        timerDao = db.timerDao(),
        syllabusDao = db.syllabusDao(),
        runInTransaction = { block -> db.withTransaction { block() } },
        clock = clock,
        zoneId = ZoneId.of("UTC"),
        onSyncAlarm = { state -> if (state == null) cancelCount++ },
      )
    coldLaunchRepo.checkUnfinishedSessionOnLaunch()
    assertNotNull(coldLaunchRepo.recoveryPrompt.first())

    db.timerDao().clearActiveTimerState()
    coldLaunchRepo.resetAfterBackupImport(wasReplaceMode = true)
    assertNull(coldLaunchRepo.recoveryPrompt.first())
    assertTrue(cancelCount > 0)
  }

  @Test
  fun `pause freezes stopwatch elapsed for 20s and resume continues seamlessly without resetting`() =
    runTest {
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "Physics"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Kinematics", number = 1))

      assertTrue(repo.startSession(bookId = bookId, chapterId = chapterId, mode = TimerMode.STOPWATCH))
      clock.advance(30_000L) // Run 30s
      repo.pause()

      val pausedState = db.timerDao().getActiveTimerStateOnce()!!
      assertEquals(TimerStatus.PAUSED, pausedState.status)
      val atPauseMetrics = TimerMath.computeMetrics(pausedState, clock.elapsedMs, clock.wallMs)
      assertEquals(30_000L, atPauseMetrics.activeFocusMs)
      assertEquals(0L, atPauseMetrics.pauseMs)

      // Wait 20s while paused -> active focus MUST stay frozen at 30s!
      clock.advance(20_000L)
      val frozenMetrics = TimerMath.computeMetrics(pausedState, clock.elapsedMs, clock.wallMs)
      assertEquals(30_000L, frozenMetrics.activeFocusMs)
      assertEquals("00:00:30", TimerMath.formatHms(frozenMetrics.activeFocusMs))
      assertEquals(20_000L, frozenMetrics.pauseMs)

      // Calling pause() again is idempotent
      repo.pause()
      assertEquals(pausedState.pauseStartElapsedMs, db.timerDao().getActiveTimerStateOnce()!!.pauseStartElapsedMs)

      // Resume continues from 30s and tracks 20s pause
      repo.resume()
      val resumedState = db.timerDao().getActiveTimerStateOnce()!!
      assertEquals(TimerStatus.RUNNING, resumedState.status)
      val atResumeMetrics = TimerMath.computeMetrics(resumedState, clock.elapsedMs, clock.wallMs)
      assertEquals(30_000L, atResumeMetrics.activeFocusMs)
      assertEquals(20_000L, atResumeMetrics.pauseMs)

      // Study 10s more -> total 40s active focus, 20s pause
      clock.advance(10_000L)
      val laterMetrics = TimerMath.computeMetrics(resumedState, clock.elapsedMs, clock.wallMs)
      assertEquals(40_000L, laterMetrics.activeFocusMs)
      assertEquals(20_000L, laterMetrics.pauseMs)
    }

  @Test
  fun `pause survives process kill and reboot as PAUSED without recovery dialog and restores notification state`() =
    runTest {
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "Biology"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Enzymes", number = 4))

      var serviceSyncCount = 0
      var alarmCancelCount = 0
      val testRepo =
        TimerRepository(
          timerDao = db.timerDao(),
          syllabusDao = db.syllabusDao(),
          runInTransaction = { block -> db.withTransaction { block() } },
          clock = clock,
          zoneId = ZoneId.of("UTC"),
          onSyncService = { serviceSyncCount++ },
          onSyncAlarm = { state -> if (state == null) alarmCancelCount++ },
        )

      assertTrue(testRepo.startSession(bookId = bookId, chapterId = chapterId, mode = TimerMode.STOPWATCH))
      clock.advance(45_000L)
      testRepo.pause()

      // Verify PAUSED ongoing notification disables chronometer and calls ACTION_TOGGLE_PAUSE_RESUME
      val pausedState = db.timerDao().getActiveTimerStateOnce()!!
      assertEquals(45_000L, pausedState.phaseCarryMs)
      assertEquals(TimerStatus.RUNNING, pausedState.pausedFromStatus)
      val pausedMetrics = TimerMath.computeMetrics(pausedState, clock.elapsedMs, clock.wallMs)
      val notif =
        NotificationHelper.buildOngoingTimerNotification(
          context = context,
          state = pausedState,
          bookName = "Biology",
          chapterLabel = "Chapter 4: Enzymes",
          metrics = pausedMetrics,
          nowWallTimeMs = clock.wallMs,
        )
      assertFalse((notif.extras.getBoolean(android.app.Notification.EXTRA_SHOW_CHRONOMETER, false)))
      assertEquals("Paused • 00:00:45", notif.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
      assertEquals("Resume", notif.actions[0].title.toString())
      assertEquals(
        com.example.timer.TimerForegroundService.ACTION_TOGGLE_PAUSE_RESUME,
        Shadows.shadowOf(notif.actions[0].actionIntent).savedIntent.action,
      )

      // Same-boot kill & relaunch while PAUSED -> restored PAUSED at 45s with NO recovery dialog
      clock.advance(60_000L) // killed for 60s while paused
      serviceSyncCount = 0
      val relaunchRepo =
        TimerRepository(
          timerDao = db.timerDao(),
          syllabusDao = db.syllabusDao(),
          runInTransaction = { block -> db.withTransaction { block() } },
          clock = clock,
          zoneId = ZoneId.of("UTC"),
          onSyncService = { serviceSyncCount++ },
          onSyncAlarm = { state -> if (state == null) alarmCancelCount++ },
        )
      relaunchRepo.checkUnfinishedSessionOnLaunch()
      assertNull(relaunchRepo.recoveryPrompt.first())
      assertEquals(1, serviceSyncCount)

      val restoredAfterKill = db.timerDao().getActiveTimerStateOnce()!!
      assertEquals(TimerStatus.PAUSED, restoredAfterKill.status)
      assertEquals(45_000L, restoredAfterKill.phaseCarryMs)
      val metricsAfterKill = TimerMath.computeMetrics(restoredAfterKill, clock.elapsedMs, clock.wallMs)
      assertEquals(45_000L, metricsAfterKill.activeFocusMs)
      assertEquals(60_000L, metricsAfterKill.pauseMs)

      // Reboot while PAUSED (elapsedRealtime resets to 5s, wall clock advanced +120s)
      clock.elapsedMs = 5_000L
      clock.wallMs += 120_000L
      val rebootRepo =
        TimerRepository(
          timerDao = db.timerDao(),
          syllabusDao = db.syllabusDao(),
          runInTransaction = { block -> db.withTransaction { block() } },
          clock = clock,
          zoneId = ZoneId.of("UTC"),
        )
      rebootRepo.checkUnfinishedSessionOnLaunch()
      assertNull(rebootRepo.recoveryPrompt.first())

      val rebased = db.timerDao().getActiveTimerStateOnce()!!
      assertEquals(TimerStatus.PAUSED, rebased.status)
      assertEquals(45_000L, rebased.phaseCarryMs)
      val metricsAfterReboot = TimerMath.computeMetrics(rebased, clock.elapsedMs, clock.wallMs)
      assertEquals(45_000L, metricsAfterReboot.activeFocusMs)
      assertEquals(180_000L, metricsAfterReboot.pauseMs)

      // Resuming after reboot starts cleanly at 45s
      rebootRepo.resume()
      clock.advance(15_000L)
      assertTrue(rebootRepo.saveCurrentSession("Post-reboot study"))
      val saved = db.timerDao().getAllSessionsOnce().single()
      assertEquals(60_000L, saved.activeFocusMs)
      assertEquals(180_000L, saved.pauseMs)
    }

  @Test
  fun `stop and save while paused preserves frozen active focus and full open pause duration`() =
    runTest {
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "Literature"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Poetry", number = 2))

      assertTrue(repo.startSession(bookId = bookId, chapterId = chapterId, mode = TimerMode.STOPWATCH))
      clock.advance(50_000L)
      repo.pause()
      clock.advance(25_000L) // open pause 25s

      // Open Stop Summary dialog -> active focus stays 50s, pause captures 25s
      repo.requestStopSummary()
      val summaryState = db.timerDao().getActiveTimerStateOnce()!!
      assertEquals(TimerStatus.STOP_SUMMARY, summaryState.status)
      assertEquals(50_000L, summaryState.phaseCarryMs)
      assertEquals(0L, summaryState.accumulatedFocusMs)
      val summaryMetrics = TimerMath.computeMetrics(summaryState, clock.elapsedMs, clock.wallMs)
      assertEquals(50_000L, summaryMetrics.activeFocusMs)
      assertEquals(25_000L, summaryMetrics.pauseMs)

      clock.advance(5_000L) // user reads summary for 5s before pressing Save
      assertTrue(repo.saveCurrentSession("Finished chapter"))
      val saved = db.timerDao().getAllSessionsOnce().single()
      assertEquals(50_000L, saved.activeFocusMs)
      assertEquals(25_000L, saved.pauseMs)
    }

  @Test
  fun `pomodoro 25-5 pause 10m+5m freeze and resume +5m keeps exact remaining, progress, and zero double counting`() =
    runTest {
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "Chemistry"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Thermodynamics", number = 5))

      val syncedTargets = mutableListOf<List<ScheduledAlarmTarget>>()
      val pomRepo =
        TimerRepository(
          timerDao = db.timerDao(),
          syllabusDao = db.syllabusDao(),
          runInTransaction = { block -> db.withTransaction { block() } },
          clock = clock,
          zoneId = ZoneId.of("UTC"),
          onSyncAlarm = { state ->
            syncedTargets.add(
              TimerAlarmScheduler.computeScheduledTargets(
                state = state,
                nowElapsedMs = clock.elapsedMs,
                nowWallMs = clock.wallMs,
              )
            )
          },
        )

      assertTrue(
        pomRepo.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.POMODORO,
          focusMinutes = 25,
          breakMinutes = 5,
        )
      )
      val sessionId = db.timerDao().getActiveTimerStateOnce()!!.sessionId

      // Test 1: Run 10m of 25m focus -> 15m left (progress = 0.4)
      clock.advance(10 * 60_000L)
      pomRepo.pause()
      assertTrue(syncedTargets.last().isEmpty())

      // Wait 5m while paused: remaining MUST stay frozen at 15:00 (progress 0.4f), phaseCarryMs = 10m, accumulatedFocusMs = 0
      clock.advance(5 * 60_000L)
      val pausedFocusState = db.timerDao().getActiveTimerStateOnce()!!
      assertEquals(10 * 60_000L, pausedFocusState.phaseCarryMs)
      assertEquals(0L, pausedFocusState.accumulatedFocusMs)
      assertEquals(TimerStatus.RUNNING, pausedFocusState.pausedFromStatus)
      val pausedFocusMetrics = TimerMath.computeMetrics(pausedFocusState, clock.elapsedMs, clock.wallMs)
      assertEquals(10 * 60_000L, pausedFocusMetrics.activeFocusMs)
      assertEquals(15 * 60_000L, pausedFocusMetrics.currentPhaseRemainingMs)
      assertEquals("00:15:00", TimerMath.formatHms(pausedFocusMetrics.currentPhaseRemainingMs))
      assertEquals(0.4f, pausedFocusMetrics.progressFraction, 0.001f)
      assertEquals(5 * 60_000L, pausedFocusMetrics.pauseMs)

      // Resume -> schedules FOCUS_END at +15m; advance 5m -> remaining 10:00, progress = 0.6f
      pomRepo.resume()
      val resumedFocus = syncedTargets.last().single()
      assertEquals(TimerAlarmEventType.FOCUS_END, resumedFocus.eventType)
      assertEquals(clock.wallMs + 15 * 60_000L, resumedFocus.triggerWallMs)

      clock.advance(5 * 60_000L)
      val after5mState = db.timerDao().getActiveTimerStateOnce()!!
      val after5mMetrics = TimerMath.computeMetrics(after5mState, clock.elapsedMs, clock.wallMs)
      assertEquals(15 * 60_000L, after5mMetrics.activeFocusMs)
      assertEquals(10 * 60_000L, after5mMetrics.currentPhaseRemainingMs)
      assertEquals("00:10:00", TimerMath.formatHms(after5mMetrics.currentPhaseRemainingMs))
      assertEquals(0.6f, after5mMetrics.progressFraction, 0.001f)

      // Test 2: Advance remaining 10m (total 25m active) -> fire FOCUS_END -> no double counting!
      clock.advance(10 * 60_000L)
      val focusEndOutcome =
        pomRepo.fireEvent(
          source = AlarmFireSource.PRIMARY,
          eventType = TimerAlarmEventType.FOCUS_END,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = resumedFocus.triggerWallMs,
        )
      assertNotNull(focusEndOutcome)
      assertEquals(TimerStatus.BREAK, focusEndOutcome!!.updatedState.status)
      assertEquals(0L, focusEndOutcome.updatedState.phaseCarryMs)
      assertEquals(25 * 60_000L, focusEndOutcome.updatedState.accumulatedFocusMs)

      val breakStartMetrics = TimerMath.computeMetrics(focusEndOutcome.updatedState, clock.elapsedMs, clock.wallMs)
      assertEquals(25 * 60_000L, breakStartMetrics.activeFocusMs)
      assertEquals(5 * 60_000L, breakStartMetrics.currentPhaseRemainingMs)

      // Test 4: Run 2m of 5m Break, Pause 3m -> break remaining frozen at 3m, progress = 0.4f
      clock.advance(2 * 60_000L)
      pomRepo.pause()
      clock.advance(3 * 60_000L)
      val pausedBreakState = db.timerDao().getActiveTimerStateOnce()!!
      assertEquals(TimerStatus.PAUSED, pausedBreakState.status)
      assertEquals(TimerStatus.BREAK, pausedBreakState.pausedFromStatus)
      assertEquals(2 * 60_000L, pausedBreakState.phaseCarryMs)
      val pausedBreakMetrics = TimerMath.computeMetrics(pausedBreakState, clock.elapsedMs, clock.wallMs)
      assertEquals(25 * 60_000L, pausedBreakMetrics.activeFocusMs)
      assertEquals(2 * 60_000L, pausedBreakMetrics.breakMs)
      assertEquals(3 * 60_000L, pausedBreakMetrics.currentPhaseRemainingMs)
      assertEquals(0.4f, pausedBreakMetrics.progressFraction, 0.001f)

      // Resume from paused Break -> restores BREAK status (not RUNNING) with 3m remaining!
      pomRepo.resume()
      val resumedBreakState = db.timerDao().getActiveTimerStateOnce()!!
      assertEquals(TimerStatus.BREAK, resumedBreakState.status)
      assertEquals(2 * 60_000L, resumedBreakState.phaseCarryMs)
      val resumedBreakTarget = syncedTargets.last().single()
      assertEquals(TimerAlarmEventType.BREAK_END, resumedBreakTarget.eventType)
      assertEquals(clock.wallMs + 3 * 60_000L, resumedBreakTarget.triggerWallMs)

      // Complete remaining 3m break -> total accumulatedBreakMs = 5m, activeFocusMs remains 25m
      clock.advance(3 * 60_000L)
      val breakEndOutcome =
        pomRepo.fireEvent(
          source = AlarmFireSource.PRIMARY,
          eventType = TimerAlarmEventType.BREAK_END,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = resumedBreakTarget.triggerWallMs,
        )
      assertNotNull(breakEndOutcome)
      assertEquals(TimerStatus.RUNNING, breakEndOutcome!!.updatedState.status)
      assertEquals(5 * 60_000L, breakEndOutcome.updatedState.accumulatedBreakMs)
      assertEquals(25 * 60_000L, breakEndOutcome.updatedState.accumulatedFocusMs)
      assertEquals(0L, breakEndOutcome.updatedState.phaseCarryMs)

      assertTrue(pomRepo.saveCurrentSession("Exact Pomodoro cycle"))
      val saved = db.timerDao().getAllSessionsOnce().single()
      assertEquals(25 * 60_000L, saved.activeFocusMs)
      assertEquals(5 * 60_000L, saved.breakMs)
      assertEquals(8 * 60_000L, saved.pauseMs)
    }

  @Test
  fun `rapid 10 pause-resume cycles accumulate exact active focus and pause without drift and notif uses toggle action`() =
    runTest {
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "Math"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Vectors", number = 1))

      assertTrue(
        repo.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.POMODORO,
          focusMinutes = 25,
          breakMinutes = 5,
        )
      )
      clock.advance(20_000L)
      val runningState = db.timerDao().getActiveTimerStateOnce()!!
      val runningMetrics = TimerMath.computeMetrics(runningState, clock.elapsedMs, clock.wallMs)
      val runningNotif =
        NotificationHelper.buildOngoingTimerNotification(
          context = context,
          state = runningState,
          bookName = "Math",
          chapterLabel = "Chapter 1: Vectors",
          metrics = runningMetrics,
          nowWallTimeMs = clock.wallMs,
        )
      assertTrue(runningNotif.extras.getBoolean(android.app.Notification.EXTRA_SHOW_CHRONOMETER, false))
      assertEquals("Pause", runningNotif.actions[0].title.toString())
      assertEquals(
        com.example.timer.TimerForegroundService.ACTION_TOGGLE_PAUSE_RESUME,
        Shadows.shadowOf(runningNotif.actions[0].actionIntent).savedIntent.action,
      )

      // Test 7: Rapidly pause/resume 10 times (20s initial + 10 * 5s active = 70s active, 10 * 3s = 30s pause)
      repeat(10) {
        repo.togglePauseResume()
        clock.advance(3_000L)
        repo.togglePauseResume()
        clock.advance(5_000L)
      }

      val midState = db.timerDao().getActiveTimerStateOnce()!!
      val midMetrics = TimerMath.computeMetrics(midState, clock.elapsedMs, clock.wallMs)
      assertEquals(70_000L, midMetrics.activeFocusMs)
      assertEquals(30_000L, midMetrics.pauseMs)
      assertEquals(25 * 60_000L - 70_000L, midMetrics.currentPhaseRemainingMs)

      assertTrue(repo.saveCurrentSession("10x rapid pause/resume"))
      val saved = db.timerDao().getAllSessionsOnce().single()
      assertEquals(70_000L, saved.activeFocusMs)
      assertEquals(30_000L, saved.pauseMs)
    }

  @Test
  fun `process death recovery of running pomodoro rebases phaseCarryMs without double counting accumulatedFocusMs`() =
    runTest {
      val bookId = db.syllabusDao().insertBook(BookEntity(name = "History"))
      val chapterId =
        db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Modern Era", number = 1))

      assertTrue(
        repo.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.POMODORO,
          focusMinutes = 25,
          breakMinutes = 5,
        )
      )
      clock.advance(10 * 60_000L)
      repo.touchLastKnownWallTime()

      // Simulate process death & cold relaunch 2m later
      clock.advance(2 * 60_000L)
      val recoveryRepo =
        TimerRepository(
          timerDao = db.timerDao(),
          syllabusDao = db.syllabusDao(),
          runInTransaction = { block -> db.withTransaction { block() } },
          clock = clock,
          zoneId = ZoneId.of("UTC"),
        )
      recoveryRepo.checkUnfinishedSessionOnLaunch()
      val prompt = recoveryRepo.recoveryPrompt.first()
      assertNotNull(prompt)
      assertEquals(10 * 60_000L, prompt!!.recoveredMetrics.activeFocusMs)
      assertEquals(15 * 60_000L, prompt.recoveredMetrics.currentPhaseRemainingMs)

      recoveryRepo.resumeRecoveredSession()
      val resumed = db.timerDao().getActiveTimerStateOnce()!!
      assertEquals(TimerStatus.RUNNING, resumed.status)
      assertEquals(0L, resumed.accumulatedFocusMs)
      assertEquals(10 * 60_000L, resumed.phaseCarryMs)

      clock.advance(15 * 60_000L)
      val outcome =
        recoveryRepo.fireEvent(
          source = AlarmFireSource.PRIMARY,
          eventType = TimerAlarmEventType.FOCUS_END,
          sessionId = resumed.sessionId,
          phaseIndex = 1,
          expectedEventWallMs = clock.wallMs,
        )
      assertNotNull(outcome)
      assertEquals(25 * 60_000L, outcome!!.updatedState.accumulatedFocusMs)
      assertEquals(0L, outcome.updatedState.phaseCarryMs)
    }

  @Test
  fun `cancelling caller job during fire still plays sound and posts notification`() = runTest {
    val app = context.applicationContext as SyllabusApplication
    Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    AlarmDiagnosticLog.clear(app)
    val notifManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notifManager.cancelAll()

    val bookId = app.database.syllabusDao().insertBook(BookEntity(name = "Physics"))
    val chapterId =
      app.database.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Optics", number = 1))

    val enteredPlay = CompletableDeferred<Unit>()
    var soundPlayed = false
    var vibrated = false
    TimerAlarmPlayer.setAudioBackendForTest(
      object : AlarmAudioBackend {
        override suspend fun playSound(context: Context): SoundAttemptResult {
          enteredPlay.complete(Unit)
          delay(120L)
          soundPlayed = true
          return SoundAttemptResult(started = true, method = TimerAlarmPlayer.METHOD_MEDIA_PLAYER_RAW)
        }

        override fun playVibration(context: Context): Boolean {
          vibrated = true
          return true
        }

        override fun stopAll() = Unit
      }
    )

    try {
      app.timerRepository.discardCurrentSession()
      assertTrue(
        app.timerRepository.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.POMODORO,
          focusMinutes = 25,
          breakMinutes = 5,
        )
      )
      val sessionId = app.database.timerDao().getActiveTimerStateOnce()!!.sessionId

      val callerJob =
        launch {
          TimerAlarmPlayer.fireEvent(
            context = app,
            source = AlarmFireSource.PRIMARY,
            eventType = TimerAlarmEventType.FOCUS_END,
            sessionId = sessionId,
            phaseIndex = 1,
            expectedEventWallMs = System.currentTimeMillis(),
          )
        }
      enteredPlay.await()
      // Cancel caller (simulating schedulePrimaryBoundaryAlarm cancelling boundaryJob mid-fire)
      callerJob.cancel()
      callerJob.join()

      assertTrue(soundPlayed)
      assertTrue(vibrated)
      val posted =
        Shadows.shadowOf(notifManager).allNotifications.any {
          it.channelId == NotificationHelper.TIMER_ALARM_CHANNEL_ID
        }
      assertTrue(posted)
      assertEquals(TimerStatus.BREAK, app.database.timerDao().getActiveTimerStateOnce()!!.status)
    } finally {
      TimerAlarmPlayer.setAudioBackendForTest(null)
      app.timerRepository.discardCurrentSession()
    }
  }

  @Test
  fun `alarm event is not lost when UserPreferencesRepository cache is empty`() = runTest {
    val freshPrefs = UserPreferencesRepository(context)
    // Persist disabled -> false -> enabled -> true via a separate instance, then read with cold cache
    freshPrefs.setTimerAlarmEnabled(false)
    freshPrefs.setTimerVibrationEnabled(true)

    val coldPrefs = UserPreferencesRepository(context)
    // Without calling .userPreferences.first() or any suspend read, snapshot reflects persisted mirror
    val snapshot = coldPrefs.getCachedPreferencesSnapshot()
    assertFalse(snapshot.timerAlarmEnabled)
    assertTrue(snapshot.timerVibrationEnabled)

    // Restore defaults and verify fire works immediately with cold cache
    val app = context.applicationContext as SyllabusApplication
    Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    app.preferencesRepository.setTimerAlarmEnabled(true)
    app.preferencesRepository.setTimerVibrationEnabled(true)

    var soundPlayed = false
    TimerAlarmPlayer.setAudioBackendForTest(
      object : AlarmAudioBackend {
        override suspend fun playSound(context: Context): SoundAttemptResult {
          soundPlayed = true
          return SoundAttemptResult(started = true, method = TimerAlarmPlayer.METHOD_MEDIA_PLAYER_RAW)
        }

        override fun playVibration(context: Context): Boolean = true

        override fun stopAll() = Unit
      }
    )

    try {
      val bookId = app.database.syllabusDao().insertBook(BookEntity(name = "Cold Cache"))
      val chapterId =
        app.database.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Ch1", number = 1))
      app.timerRepository.discardCurrentSession()
      assertTrue(
        app.timerRepository.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.POMODORO,
          focusMinutes = 25,
          breakMinutes = 5,
        )
      )
      val sessionId = app.database.timerDao().getActiveTimerStateOnce()!!.sessionId
      val outcome =
        TimerAlarmPlayer.fireEvent(
          context = app,
          source = AlarmFireSource.BACKUP,
          eventType = TimerAlarmEventType.FOCUS_END,
          sessionId = sessionId,
          phaseIndex = 1,
          expectedEventWallMs = System.currentTimeMillis(),
        )
      assertNotNull(outcome)
      assertTrue(soundPlayed)
    } finally {
      TimerAlarmPlayer.setAudioBackendForTest(null)
      app.timerRepository.discardCurrentSession()
    }
  }

  @Test
  fun `sound fallback posts non-silent channel notification when all direct audio candidates fail`() = runTest {
    val app = context.applicationContext as SyllabusApplication
    Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    val notifManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notifManager.cancelAll()
    AlarmDiagnosticLog.clear(app)

    // 1. Robolectric has no audio hardware: MediaPlayer/Ringtone candidates fail/report !isPlaying cleanly
    TimerAlarmPlayer.setAudioBackendForTest(null)
    val reportRealFailureFallback =
      TimerAlarmPlayer.deliverAlarmAlert(
        context = app,
        eventLabel = "FOCUS_END",
        sourceLabel = AlarmFireSource.PRIMARY.logLabel,
        delayMs = 0L,
        title = "Break started",
        message = "Fallback test",
        notificationId = NotificationHelper.TIMER_ALERT_PHASE_NOTIFICATION_ID,
        playSound = true,
        playVibration = true,
      )
    assertTrue(reportRealFailureFallback.channelEnabled)
    assertTrue(reportRealFailureFallback.notificationPosted)
    assertEquals(TimerAlarmPlayer.METHOD_CHANNEL_FALLBACK, reportRealFailureFallback.soundMethod)
    assertTrue(reportRealFailureFallback.soundStarted)

    // 2. When a candidate succeeds (e.g. Ringtone TYPE_ALARM fallback), method reports it
    TimerAlarmPlayer.setAudioBackendForTest(
      object : AlarmAudioBackend {
        override suspend fun playSound(context: Context): SoundAttemptResult =
          SoundAttemptResult(started = true, method = TimerAlarmPlayer.METHOD_RINGTONE_ALARM)

        override fun playVibration(context: Context): Boolean = true

        override fun stopAll() = Unit
      }
    )
    try {
      val reportRingtoneFallback = TimerAlarmPlayer.runTestAlarmNow(app)
      assertTrue(reportRingtoneFallback.soundStarted)
      assertEquals(TimerAlarmPlayer.METHOD_RINGTONE_ALARM, reportRingtoneFallback.soundMethod)
    } finally {
      TimerAlarmPlayer.setAudioBackendForTest(null)
    }
  }

  @Test
  fun `backup alarm still rings if primary threw before playing`() = runTest {
    val app = context.applicationContext as SyllabusApplication
    Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    AlarmDiagnosticLog.clear(app)

    var backupPlayed = false
    TimerAlarmPlayer.setAudioBackendForTest(
      object : AlarmAudioBackend {
        override suspend fun playSound(context: Context): SoundAttemptResult {
          backupPlayed = true
          return SoundAttemptResult(started = true, method = TimerAlarmPlayer.METHOD_MEDIA_PLAYER_RAW)
        }

        override fun playVibration(context: Context): Boolean = true

        override fun stopAll() = Unit
      }
    )

    try {
      val bookId = app.database.syllabusDao().insertBook(BookEntity(name = "Backup Rescue"))
      val chapterId =
        app.database.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Ch 1", number = 1))
      app.timerRepository.discardCurrentSession()
      assertTrue(
        app.timerRepository.startSession(
          bookId = bookId,
          chapterId = chapterId,
          mode = TimerMode.POMODORO,
          focusMinutes = 25,
          breakMinutes = 5,
        )
      )
      val state = app.database.timerDao().getActiveTimerStateOnce()!!
      val expectedWall = state.phaseStartWallTimeMs + state.focusDurationMs

      // Primary throws BEFORE audio/notify finishes -> fired mark & Room transition must NOT remain committed
      val primaryFailure =
        runCatching {
          app.timerRepository.fireEvent(
            source = AlarmFireSource.PRIMARY,
            eventType = TimerAlarmEventType.FOCUS_END,
            sessionId = state.sessionId,
            phaseIndex = 1,
            expectedEventWallMs = expectedWall,
            onBeforeCommitAttempt = { _, _, _, _ ->
              error("Simulated primary crash before play")
            },
          )
        }
      assertTrue(primaryFailure.isFailure)
      assertEquals(TimerStatus.RUNNING, app.database.timerDao().getActiveTimerStateOnce()!!.status)

      // Backup fires 2s later -> still rings and transitions phase!
      val backupOutcome =
        TimerAlarmPlayer.fireEvent(
          context = app,
          source = AlarmFireSource.BACKUP,
          eventType = TimerAlarmEventType.FOCUS_END,
          sessionId = state.sessionId,
          phaseIndex = 1,
          expectedEventWallMs = expectedWall,
        )
      assertNotNull(backupOutcome)
      assertTrue(backupPlayed)
      assertEquals(TimerStatus.BREAK, app.database.timerDao().getActiveTimerStateOnce()!!.status)
    } finally {
      TimerAlarmPlayer.setAudioBackendForTest(null)
      app.timerRepository.discardCurrentSession()
    }
  }

  @Test
  fun `notification channel upgrades to timer_alarm_v2 deletes legacy channel and diag log caps at 20`() =
    runTest {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val legacyChannel =
        android.app.NotificationChannel(
          NotificationHelper.LEGACY_TIMER_ALARM_CHANNEL_ID,
          "Legacy",
          NotificationManager.IMPORTANCE_HIGH,
        )
      manager.createNotificationChannel(legacyChannel)
      assertNotNull(manager.getNotificationChannel(NotificationHelper.LEGACY_TIMER_ALARM_CHANNEL_ID))

      NotificationHelper.ensureNotificationChannel(context)
      assertNull(manager.getNotificationChannel(NotificationHelper.LEGACY_TIMER_ALARM_CHANNEL_ID))
      val v2 = manager.getNotificationChannel(NotificationHelper.TIMER_ALARM_CHANNEL_ID)
      assertNotNull(v2)
      assertEquals("timer_alarm_v2", NotificationHelper.TIMER_ALARM_CHANNEL_ID)
      assertEquals(NotificationManager.IMPORTANCE_HIGH, v2!!.importance)

      AlarmDiagnosticLog.clear(context)
      for (i in 1..25) {
        AlarmDiagnosticLog.record(
          context = context,
          eventType = "EVT_$i",
          source = "primary",
          outcome = AlarmDiagnosticLog.OUTCOME_PLAYED,
          timestampMs = 1000L + i,
        )
      }
      val entries = AlarmDiagnosticLog.getEntries(context)
      assertEquals(20, entries.size)
      assertEquals("EVT_25", entries.first().eventType)
      assertEquals("EVT_6", entries.last().eventType)

      // Test-in-10s schedules setAlarmClock and deduplicates primary vs backup cleanly
      val app = context.applicationContext as SyllabusApplication
      Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
      val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      ShadowAlarmManager.setCanScheduleExactAlarms(true)
      val testId = TimerAlarmScheduler.scheduleTestAlarmIn10Seconds(app)
      val scheduledTestAlarm =
        Shadows.shadowOf(alarmManager).scheduledAlarms.firstOrNull {
          Shadows.shadowOf(it.operation).savedIntent.action == TimerAlarmScheduler.ACTION_TEST_ALARM
        }
      assertNotNull(scheduledTestAlarm)
      TimerAlarmPlayer.setAudioBackendForTest(
        object : AlarmAudioBackend {
          override suspend fun playSound(context: Context): SoundAttemptResult =
            SoundAttemptResult(true, TimerAlarmPlayer.METHOD_MEDIA_PLAYER_RAW)
          override fun playVibration(context: Context): Boolean = true
          override fun stopAll() = Unit
        }
      )
      try {
        val first =
          TimerAlarmPlayer.fireTestAlarm(
            app,
            AlarmFireSource.PRIMARY,
            testId,
            System.currentTimeMillis(),
          )
        val duplicate =
          TimerAlarmPlayer.fireTestAlarm(
            app,
            AlarmFireSource.BACKUP,
            testId,
            System.currentTimeMillis(),
          )
        assertNotNull(first)
        assertNull(duplicate)
      } finally {
        TimerAlarmPlayer.setAudioBackendForTest(null)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
      }
    }
}


