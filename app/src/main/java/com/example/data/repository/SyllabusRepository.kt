package com.example.data.repository

import com.example.data.local.dao.SyllabusDao
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt

data class BookProgressSummary(
  val book: BookEntity,
  val totalChapters: Int,
  val completedChapters: Int,
  val nextIncompleteChapter: ChapterEntity?,
) {
  val percentage: Int
    get() = if (totalChapters == 0) 0 else ((completedChapters * 100f) / totalChapters).roundToInt()

  val progressFraction: Float
    get() = if (totalChapters == 0) 0f else completedChapters.toFloat() / totalChapters.toFloat()

  val isCompleted: Boolean
    get() = totalChapters > 0 && completedChapters == totalChapters
}

data class DailyStudyCount(
  val date: LocalDate,
  val dayLabel: String,
  val count: Int,
  val isToday: Boolean,
)

enum class StudyTrendPeriod {
  THIS_WEEK,
  LAST_WEEK,
}

data class SyllabusSnapshot(
  val books: List<BookProgressSummary>,
  val allChapters: List<ChapterEntity>,
  val totalBooks: Int,
  val totalChapters: Int,
  val completedChapters: Int,
  val remainingChapters: Int,
  val overallPercentage: Int,
  val completedToday: Int,
  val continueStudyingBook: BookProgressSummary?,
  val preferences: UserPreferences,
)

class SyllabusRepository(
  private val dao: SyllabusDao,
  private val prefsRepo: UserPreferencesRepository,
  private val onDataChanged: suspend () -> Unit = {},
) {
  val snapshot: Flow<SyllabusSnapshot> =
    combine(dao.observeBooks(), dao.observeAllChapters(), prefsRepo.userPreferences) {
      books,
      chapters,
      prefs ->
      val chaptersByBook = chapters.groupBy { it.bookId }
      val summaries =
        books.map { book ->
          val bookChapters = chaptersByBook[book.id].orEmpty().sortedWith(compareBy({ it.number }, { it.id }))
          val completedCount = bookChapters.count { it.isCompleted }
          val nextIncomplete = bookChapters.firstOrNull { !it.isCompleted }
          BookProgressSummary(
            book = book,
            totalChapters = bookChapters.size,
            completedChapters = completedCount,
            nextIncompleteChapter = nextIncomplete,
          )
        }

      val totalChapters = chapters.size
      val completedChapters = chapters.count { it.isCompleted }
      val remainingChapters = (totalChapters - completedChapters).coerceAtLeast(0)
      val overallPercentage =
        if (totalChapters == 0) 0 else ((completedChapters * 100f) / totalChapters).roundToInt()

      val zone = ZoneId.systemDefault()
      val today = LocalDate.now(zone)
      val startOfTodayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
      val endOfTodayMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

      val completedToday =
        chapters.count { ch ->
          val completedAt = ch.completedAt
          ch.isCompleted &&
            completedAt != null &&
            completedAt >= startOfTodayMillis &&
            completedAt < endOfTodayMillis
        }

      val lastOpenedSummary = summaries.firstOrNull { it.book.id == prefs.lastOpenedBookId }
      val continueBook =
        when {
          lastOpenedSummary != null && lastOpenedSummary.nextIncompleteChapter != null ->
            lastOpenedSummary
          else ->
            summaries.firstOrNull { it.nextIncompleteChapter != null } ?: lastOpenedSummary ?: summaries.firstOrNull()
        }

      SyllabusSnapshot(
        books = summaries,
        allChapters = chapters,
        totalBooks = summaries.size,
        totalChapters = totalChapters,
        completedChapters = completedChapters,
        remainingChapters = remainingChapters,
        overallPercentage = overallPercentage,
        completedToday = completedToday,
        continueStudyingBook = continueBook,
        preferences = prefs,
      )
    }

  fun observeBookById(bookId: Long): Flow<BookEntity?> = dao.observeBookById(bookId)

  fun observeChaptersForBook(bookId: Long): Flow<List<ChapterEntity>> = dao.observeChaptersForBook(bookId)

  suspend fun addBook(name: String): Long {
    val id = dao.insertBook(BookEntity(name = name.trim()))
    onDataChanged()
    return id
  }

  suspend fun updateBook(book: BookEntity) {
    dao.updateBook(book.copy(name = book.name.trim()))
    onDataChanged()
  }

  suspend fun deleteBook(bookId: Long) {
    dao.deleteBookById(bookId)
    onDataChanged()
  }

  suspend fun addChapter(
    bookId: Long,
    name: String,
    number: Int,
    isCompleted: Boolean,
  ): Long {
    val completedAt = if (isCompleted) System.currentTimeMillis() else null
    prefsRepo.setLastOpenedBookId(bookId)
    val id =
      dao.insertChapter(
        ChapterEntity(
          bookId = bookId,
          name = name.trim(),
          number = number,
          isCompleted = isCompleted,
          completedAt = completedAt,
        )
      )
    onDataChanged()
    return id
  }

  suspend fun updateChapter(chapter: ChapterEntity, isCompleted: Boolean) {
    val newCompletedAt =
      when {
        isCompleted && chapter.completedAt != null -> chapter.completedAt
        isCompleted -> System.currentTimeMillis()
        else -> null
      }
    dao.updateChapter(chapter.copy(name = chapter.name.trim(), isCompleted = isCompleted, completedAt = newCompletedAt))
    onDataChanged()
  }

  suspend fun toggleChapterCompleted(chapter: ChapterEntity) {
    val nextState = !chapter.isCompleted
    val completedAt = if (nextState) System.currentTimeMillis() else null
    dao.updateChapterCompletion(chapter.id, nextState, completedAt)
    prefsRepo.setLastOpenedBookId(chapter.bookId)
    onDataChanged()
  }

  suspend fun deleteChapter(chapterId: Long) {
    dao.deleteChapterById(chapterId)
    onDataChanged()
  }

  suspend fun markBookOpened(bookId: Long) {
    prefsRepo.setLastOpenedBookId(bookId)
    onDataChanged()
  }

  suspend fun setDailyGoal(goal: Int) {
    prefsRepo.setDailyGoal(goal)
    onDataChanged()
  }

  suspend fun setReminderEnabled(enabled: Boolean) {
    prefsRepo.setReminderEnabled(enabled)
  }

  suspend fun setReminderTime(hour: Int, minute: Int) {
    prefsRepo.setReminderTime(hour, minute)
  }

  suspend fun getCompletedTodayCount(): Int {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val startMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return dao.getCompletedCountBetween(startMillis, endMillis)
  }

  suspend fun getTotalChapterCount(): Int = dao.getTotalChapterCount()

  companion object {
    fun computeWeeklyTrend(
      chapters: List<ChapterEntity>,
      period: StudyTrendPeriod,
      zone: ZoneId = ZoneId.systemDefault(),
    ): List<DailyStudyCount> {
      val today = LocalDate.now(zone)
      val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
      val weekStart =
        when (period) {
          StudyTrendPeriod.THIS_WEEK -> currentWeekStart
          StudyTrendPeriod.LAST_WEEK -> currentWeekStart.minusWeeks(1)
        }

      val countsByDate =
        chapters
          .asSequence()
          .filter { it.isCompleted && it.completedAt != null }
          .groupingBy { ch -> Instant.ofEpochMilli(ch.completedAt!!).atZone(zone).toLocalDate() }
          .eachCount()

      return (0 until 7).map { offset ->
        val date = weekStart.plusDays(offset.toLong())
        val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        DailyStudyCount(
          date = date,
          dayLabel = label,
          count = countsByDate[date] ?: 0,
          isToday = date == today,
        )
      }
    }

    fun computeCumulativeSeries(
      chapters: List<ChapterEntity>,
      period: StudyTrendPeriod,
      zone: ZoneId = ZoneId.systemDefault(),
    ): List<Float> {
      val total = chapters.size
      if (total == 0) return List(7) { 0f }

      val today = LocalDate.now(zone)
      val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
      val weekStart =
        when (period) {
          StudyTrendPeriod.THIS_WEEK -> currentWeekStart
          StudyTrendPeriod.LAST_WEEK -> currentWeekStart.minusWeeks(1)
        }

      val completedDates =
        chapters.mapNotNull { ch ->
          if (ch.isCompleted && ch.completedAt != null) {
            Instant.ofEpochMilli(ch.completedAt).atZone(zone).toLocalDate()
          } else {
            null
          }
        }

      val fallbackCompletedWithoutDate = chapters.count { it.isCompleted && it.completedAt == null }

      return (0 until 7).map { offset ->
        val dayEnd = weekStart.plusDays(offset.toLong())
        val effectiveDate = if (dayEnd.isAfter(today)) today else dayEnd
        val completedByDay =
          completedDates.count { !it.isAfter(effectiveDate) } + fallbackCompletedWithoutDate
        (completedByDay.toFloat() / total.toFloat()).coerceIn(0f, 1f)
      }
    }
  }
}
