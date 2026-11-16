package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SyllabusDatabase
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.StudyTrendPeriod
import com.example.data.repository.SyllabusRepository
import com.example.notifications.StudyReminderScheduler
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
