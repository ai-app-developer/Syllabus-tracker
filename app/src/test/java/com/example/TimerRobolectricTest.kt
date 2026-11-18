package com.example

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupImportMode
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupValidationResult
import com.example.data.local.SyllabusDatabase
import com.example.data.local.entity.ActiveTimerStateEntity
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.SyllabusRepository
import com.example.data.repository.TimerRepository
import com.example.notifications.NotificationHelper
import com.example.timer.AlarmFireSource
import com.example.timer.ClockProvider
import com.example.timer.PrefsAlarmDeduplicator
import com.example.timer.ScheduledAlarmTarget
import com.example.timer.TimerAlarmEventType
import com.example.timer.TimerAlarmPlayer
import com.example.timer.TimerAlarmScheduler
import com.example.timer.TimerMath
import com.example.timer.TimerMode
import com.example.timer.TimerStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
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
        .addMigrations(SyllabusDatabase.MIGRATION_1_2)
        .build()

    val books = migrated.syllabusDao().getAllBooksOnce()
    val chapters = migrated.syllabusDao().getAllChaptersOnce()
    assertEquals(1, books.size)
    assertEquals("Mathematics", books.first().name)
    assertEquals(1, chapters.size)
    assertEquals("Algebra", chapters.first().name)
    assertTrue(migrated.timerDao().getAllSessionsOnce().isEmpty())

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
}

