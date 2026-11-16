package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupBook
import com.example.data.backup.BackupChapter
import com.example.data.backup.BackupImportMode
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupSettings
import com.example.data.backup.BackupValidationResult
import com.example.data.backup.SyllabusBackup
import com.example.data.local.SyllabusDatabase
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.StudyTrendPeriod
import com.example.data.repository.SyllabusRepository
import com.example.notifications.StudyReminderScheduler
import com.example.widgets.WidgetBitmapHelper
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var context: Context
  private lateinit var db: SyllabusDatabase
  private lateinit var repository: SyllabusRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db =
      Room.inMemoryDatabaseBuilder(context, SyllabusDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val prefs = UserPreferencesRepository(context)
    repository = SyllabusRepository(db.syllabusDao(), prefs)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `app name matches Syllabus Tracker`() {
    val appName = context.getString(R.string.app_name)
    assertEquals("Syllabus Tracker", appName)
  }

  @Test
  fun `progress and continue studying are computed accurately from Room DB`() = runTest {
    val mathId = repository.addBook("Mathematics")
    val scienceId = repository.addBook("Science")

    repository.addChapter(mathId, "Sets", 1, isCompleted = true)
    repository.addChapter(mathId, "Algebra", 2, isCompleted = false)
    repository.addChapter(scienceId, "Matter", 1, isCompleted = true)
    repository.addChapter(scienceId, "Forces", 2, isCompleted = false)

    val snapshot = repository.snapshot.first()
    assertEquals(2, snapshot.totalBooks)
    assertEquals(4, snapshot.totalChapters)
    assertEquals(2, snapshot.completedChapters)
    assertEquals(2, snapshot.remainingChapters)
    assertEquals(50, snapshot.overallPercentage)
    assertEquals(2, snapshot.completedToday)
    assertEquals("Science", snapshot.continueStudyingBook?.book?.name)
    assertEquals("Forces", snapshot.continueStudyingBook?.nextIncompleteChapter?.name)
  }

  @Test
  fun `deleting book cascades to chapters`() = runTest {
    val bookId = db.syllabusDao().insertBook(BookEntity(name = "English"))
    db.syllabusDao().insertChapter(ChapterEntity(bookId = bookId, name = "Grammar", number = 1))

    repository.deleteBook(bookId)
    val snapshot = repository.snapshot.first()
    assertEquals(0, snapshot.totalBooks)
    assertEquals(0, snapshot.totalChapters)
  }

  @Test
  fun `weekly trend counts completed chapters on matching dates`() {
    val zone = ZoneId.systemDefault()
    val todayMillis = LocalDate.now(zone).atStartOfDay(zone).plusHours(10).toInstant().toEpochMilli()
    val chapters =
      listOf(
        ChapterEntity(id = 1, bookId = 1, name = "A", number = 1, isCompleted = true, completedAt = todayMillis),
        ChapterEntity(id = 2, bookId = 1, name = "B", number = 2, isCompleted = true, completedAt = todayMillis),
        ChapterEntity(id = 3, bookId = 1, name = "C", number = 3, isCompleted = false, completedAt = null),
      )

    val trend = SyllabusRepository.computeWeeklyTrend(chapters, StudyTrendPeriod.THIS_WEEK, zone)
    assertEquals(7, trend.size)
    val todayEntry = trend.first { it.isToday }
    assertEquals(2, todayEntry.count)
  }

  @Test
  fun `reminder scheduler picks next day if target time already passed`() {
    val zone = ZoneId.of("UTC")
    val now = ZonedDateTime.of(2026, 11, 16, 21, 0, 0, 0, zone)
    val triggerMillis = StudyReminderScheduler.computeNextTriggerMillis(hour = 20, minute = 0, now = now)
    val expected = ZonedDateTime.of(2026, 11, 17, 20, 0, 0, 0, zone).toInstant().toEpochMilli()
    assertEquals(expected, triggerMillis)
    assertTrue(triggerMillis > now.toInstant().toEpochMilli())
    assertFalse(
      StudyReminderScheduler.computeNextTriggerMillis(
        hour = 22,
        minute = 0,
        now = now,
      ) == expected
    )
  }

  @Test
  fun `widget bitmap helper draws valid progress ring and horizontal bar`() {
    val ring =
      com.example.widgets.WidgetBitmapHelper.drawProgressRingBitmap(
        sizePx = 180,
        strokePx = 18f,
        fraction = 0.62f,
        trackColor = 0xFFEEF0FF.toInt(),
        arcColor = 0xFF4F46E5.toInt(),
      )
    assertEquals(180, ring.width)
    assertEquals(180, ring.height)

    val bar =
      com.example.widgets.WidgetBitmapHelper.drawHorizontalBarBitmap(
        widthPx = 240,
        heightPx = 14,
        fraction = 0.5f,
        trackColor = 0xFFEEF0FF.toInt(),
        fillColor = 0xFF4F46E5.toInt(),
      )
    assertEquals(240, bar.width)
    assertEquals(14, bar.height)
  }

  @Test
  fun `repository invokes onDataChanged callback after book or chapter mutations`() = runTest {
    var callbackCount = 0
    val repoWithCallback =
      SyllabusRepository(
        dao = db.syllabusDao(),
        prefsRepo = UserPreferencesRepository(context),
        onDataChanged = { callbackCount++ },
      )
    val bookId = repoWithCallback.addBook("Physics")
    repoWithCallback.addChapter(bookId, "Kinematics", 1, isCompleted = false)
    assertEquals(2, callbackCount)
  }

  @Test
  fun `backup export produces valid schema and validation rejects malformed inputs`() = runTest {
    val bookId = repository.addBook("Chemistry")
    repository.addChapter(bookId, "Atomic Structure", 1, isCompleted = true)
    repository.setDailyGoal(4)
    repository.setReminderTime(19, 30)

    val json = repository.exportBackupJson(appVersion = "1.0")
    val validResult = BackupManager.parseAndValidate(json)
    assertTrue(validResult is BackupValidationResult.Valid)
    val parsed = (validResult as BackupValidationResult.Valid).backup
    assertEquals(BackupManager.CURRENT_SCHEMA_VERSION, parsed.schemaVersion)
    assertEquals(4, parsed.settings.dailyGoal)
    assertEquals(19, parsed.settings.reminderHour)
    assertEquals(30, parsed.settings.reminderMinute)
    assertEquals(1, parsed.books.size)
    assertEquals(1, parsed.chapters.size)

    assertTrue(BackupManager.parseAndValidate("{not json") is BackupValidationResult.Invalid)

    val wrongSchema =
      json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
    assertTrue(BackupManager.parseAndValidate(wrongSchema) is BackupValidationResult.Invalid)

    val brokenBookRef =
      BackupManager.createBackupJson(
        appVersion = "1.0",
        settings = UserPreferences(),
        books = listOf(BookEntity(id = 10L, name = "Biology", createdAt = 1000L)),
        chapters =
          listOf(
            ChapterEntity(
              id = 20L,
              bookId = 999L,
              name = "Cells",
              number = 1,
              isCompleted = false,
              completedAt = null,
            )
          ),
      )
    val brokenResult = BackupManager.parseAndValidate(brokenBookRef)
    assertTrue(brokenResult is BackupValidationResult.Invalid)
  }

  @Test
  fun `backup import supports replace and merge deduplicating by book name and chapter number`() = runTest {
    val existingBookId = repository.addBook("Mathematics")
    repository.addChapter(existingBookId, "Sets", 1, isCompleted = true)

    val incomingBackup =
      SyllabusBackup(
        appVersion = "1.0",
        schemaVersion = 1,
        exportedAt = 2_000_000L,
        settings = BackupSettings(dailyGoal = 5, reminderEnabled = false, reminderHour = 21, reminderMinute = 15),
        books =
          listOf(
            BackupBook(id = 100L, name = "mathematics", createdAt = 1_000L),
            BackupBook(id = 101L, name = "Physics", createdAt = 1_500L),
          ),
        chapters =
          listOf(
            BackupChapter(
              id = 200L,
              bookId = 100L,
              name = "Duplicate Sets",
              number = 1,
              isCompleted = false,
              completedAt = null,
              createdAt = 1_100L,
            ),
            BackupChapter(
              id = 201L,
              bookId = 100L,
              name = "Algebra",
              number = 2,
              isCompleted = false,
              completedAt = null,
              createdAt = 1_200L,
            ),
            BackupChapter(
              id = 202L,
              bookId = 101L,
              name = "Optics",
              number = 1,
              isCompleted = true,
              completedAt = 1_600L,
              createdAt = 1_600L,
            ),
          ),
      )

    repository.importBackup(incomingBackup, BackupImportMode.MERGE)
    val afterMerge = repository.snapshot.first()
    assertEquals(2, afterMerge.totalBooks)
    assertEquals(3, afterMerge.totalChapters)
    assertEquals(5, afterMerge.preferences.dailyGoal)
    assertFalse(afterMerge.preferences.reminderEnabled)

    val replaceBackup =
      SyllabusBackup(
        appVersion = "1.0",
        schemaVersion = 1,
        exportedAt = 3_000_000L,
        settings = BackupSettings(dailyGoal = 3, reminderEnabled = true, reminderHour = 20, reminderMinute = 0),
        books = listOf(BackupBook(id = 50L, name = "History", createdAt = 2_000L)),
        chapters =
          listOf(
            BackupChapter(
              id = 60L,
              bookId = 50L,
              name = "Ancient Civilizations",
              number = 1,
              isCompleted = false,
              completedAt = null,
              createdAt = 2_000L,
            )
          ),
      )

    repository.importBackup(replaceBackup, BackupImportMode.REPLACE)
    val afterReplace = repository.snapshot.first()
    assertEquals(1, afterReplace.totalBooks)
    assertEquals(1, afterReplace.totalChapters)
    assertEquals("History", afterReplace.books.first().book.name)
    assertEquals(3, afterReplace.preferences.dailyGoal)
  }

  @Test
  fun `widget aggregate summary and LruCache bitmap ring reuse work efficiently`() = runTest {
    val bookId = repository.addBook("Algorithms")
    repository.addChapter(bookId, "Sorting", 1, isCompleted = true)
    repository.addChapter(bookId, "Graphs", 2, isCompleted = false)
    repository.setDailyGoal(4)

    val summary = repository.loadWidgetSummary()
    assertEquals(1, summary.totalBooks)
    assertEquals(2, summary.totalChapters)
    assertEquals(1, summary.completedChapters)
    assertEquals(50, summary.overallPercentage)
    assertEquals(1, summary.completedToday)
    assertEquals(4, summary.dailyGoal)
    assertEquals("Algorithms", summary.activeBook?.name)
    assertEquals("Graphs", summary.activeBook?.nextChapterName)

    val ring1 =
      WidgetBitmapHelper.getOrDrawProgressRingBitmap(
        percentage = 50,
        trackColor = 0xFFEEF0FF.toInt(),
        arcColor = 0xFF4F46E5.toInt(),
      )
    val ring2 =
      WidgetBitmapHelper.getOrDrawProgressRingBitmap(
        percentage = 50,
        trackColor = 0xFFEEF0FF.toInt(),
        arcColor = 0xFF4F46E5.toInt(),
      )
    assertEquals(300, ring1.width)
    assertEquals(300, ring1.height)
    assertSame(ring1, ring2)
  }
}
