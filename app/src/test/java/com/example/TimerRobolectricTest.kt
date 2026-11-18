package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SyllabusDatabase
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.repository.TimerRepository
import com.example.timer.ClockProvider
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
import org.robolectric.annotation.Config

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
      // Single active session guard
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

      // Simulate cold app restart / process death with a fresh TimerRepository instance
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
  fun `pomodoro transitions to break at phase end and skipBreak advances cycle`() = runTest {
    val bookId = db.syllabusDao().insertBook(BookEntity(name = "Biology"))
    val chapterId =
      db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Cells", number = 1))

    assertTrue(
      repo.startSession(
        bookId = bookId,
        chapterId = chapterId,
        mode = TimerMode.POMODORO,
        focusMinutes = 25,
        breakMinutes = 5,
      )
    )

    clock.advance(25 * 60_000L)
    repo.advancePomodoroPhaseIfDue()
    val inBreak = db.timerDao().getActiveTimerStateOnce()!!
    assertEquals(TimerStatus.BREAK, inBreak.status)
    assertEquals(25 * 60_000L, inBreak.accumulatedFocusMs)

    clock.advance(60_000L)
    repo.skipBreak()
    val resumedFocus = db.timerDao().getActiveTimerStateOnce()!!
    assertEquals(TimerStatus.RUNNING, resumedFocus.status)
    assertEquals(2, resumedFocus.pomodoroCycle)
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
        // Last week session: 2h 45m -> current week 5h 30m = +100%
        makeSession("s-prev", 1L, "Mathematics", lastWeekWed, 165 * 60_000L),
      )

    val result =
      com.example.timer.TimerAnalyticsCalculator.compute(
        sessions = sessions,
        period = com.example.timer.AnalyticsPeriod.THIS_WEEK,
        today = today,
        zoneId = zone,
      )

    assertEquals(210 * 60_000L, result.todayTotalMs) // 3h 30m today
    assertEquals(330 * 60_000L, result.periodTotalMs) // 5h 30m this week
    assertEquals(100, result.deltaPercent)
    assertEquals(2, result.currentStreakDays) // Today + yesterday
    assertEquals(7, result.bars.size)
    assertEquals("Sat", result.bars.first().label)
    assertEquals("Fri", result.bars.last().label)
    assertTrue(result.bars.first { it.label == "Wed" }.isHighest)

    // 5 books -> top 4 + Others
    assertEquals(5, result.bookSlices.size)
    assertEquals("Mathematics", result.bookSlices[0].bookName)
    assertEquals("Others", result.bookSlices[4].bookName)
    assertEquals(30 * 60_000L, result.bookSlices[4].totalMs)
  }
}
