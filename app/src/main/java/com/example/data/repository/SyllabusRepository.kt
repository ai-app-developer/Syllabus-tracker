package com.example.data.repository

import com.example.data.backup.BackupImportMode
import com.example.data.backup.BackupManager
import com.example.data.backup.SyllabusBackup
import com.example.data.local.dao.SyllabusDao
import com.example.data.local.dao.WidgetSummaryRow
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

data class WidgetActiveBook(
  val id: Long,
  val name: String,
  val totalChapters: Int,
  val completedChapters: Int,
  val nextChapterNumber: Int?,
  val nextChapterName: String?,
) {
  val percentage: Int
    get() = if (totalChapters == 0) 0 else ((completedChapters * 100f) / totalChapters).roundToInt()

  val progressFraction: Float
    get() = if (totalChapters == 0) 0f else completedChapters.toFloat() / totalChapters.toFloat()
}

data class WidgetSummaryData(
  val totalBooks: Int,
  val totalChapters: Int,
  val completedChapters: Int,
  val overallPercentage: Int,
  val completedToday: Int,
  val dailyGoal: Int,
  val activeBook: WidgetActiveBook?,
)

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
  private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
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

  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeWidgetSummaryRow(): Flow<WidgetSummaryRow> =
    prefsRepo.userPreferences
      .map { it.lastOpenedBookId to it.dailyGoal.coerceAtLeast(1) }
      .distinctUntilChanged()
      .flatMapLatest { (lastOpenedBookId, dailyGoal) ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfTodayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfTodayMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        dao.observeWidgetSummaryRow(
          startOfTodayMillis = startOfTodayMillis,
          endOfTodayMillis = endOfTodayMillis,
          lastOpenedBookId = lastOpenedBookId,
          dailyGoal = dailyGoal,
        )
      }
      .conflate()

  suspend fun loadWidgetSummary(): WidgetSummaryData {
    val prefs = prefsRepo.getCurrentPreferences()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val startOfTodayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfTodayMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val row =
      dao.getWidgetSummaryRow(
        startOfTodayMillis = startOfTodayMillis,
        endOfTodayMillis = endOfTodayMillis,
        lastOpenedBookId = prefs.lastOpenedBookId,
        dailyGoal = prefs.dailyGoal.coerceAtLeast(1),
      )

    val activeBook =
      row.activeBookId?.let { bookId ->
        WidgetActiveBook(
          id = bookId,
          name = row.activeBookName.orEmpty(),
          totalChapters = row.activeBookTotalChapters,
          completedChapters = row.activeBookCompletedChapters,
          nextChapterNumber = row.nextChapterNumber,
          nextChapterName = row.nextChapterName,
        )
      }

    return WidgetSummaryData(
      totalBooks = row.totalBooks,
      totalChapters = row.totalChapters,
      completedChapters = row.completedChapters,
      overallPercentage = row.overallPercentage,
      completedToday = row.completedToday,
      dailyGoal = row.dailyGoal,
      activeBook = activeBook,
    )
  }

  suspend fun completeNextIncompleteChapterFromWidget(): Boolean {
    val prefs = prefsRepo.getCurrentPreferences()
    val lastOpened =
      if (prefs.lastOpenedBookId > 0L) dao.getBookByIdOnce(prefs.lastOpenedBookId) else null
    val candidateChapter =
      lastOpened?.let { dao.getNextIncompleteChapterOnce(it.id) }
        ?: dao.getFirstIncompleteBookOnce()?.let { dao.getNextIncompleteChapterOnce(it.id) }
        ?: return false

    dao.updateChapterCompletion(
      chapterId = candidateChapter.id,
      isCompleted = true,
      completedAt = System.currentTimeMillis(),
    )
    prefsRepo.setLastOpenedBookId(candidateChapter.bookId)
    return true
  }

  suspend fun exportBackupJson(appVersion: String): String {
    val books = dao.getAllBooksOnce()
    val chapters = dao.getAllChaptersOnce()
    val prefs = prefsRepo.getCurrentPreferences()
    return BackupManager.createBackupJson(
      appVersion = appVersion,
      settings = prefs,
      books = books,
      chapters = chapters,
    )
  }

  suspend fun importBackup(
    backup: SyllabusBackup,
    mode: BackupImportMode,
  ): UserPreferences {
    var firstInsertedBookId = -1L

    runInTransaction {
      when (mode) {
        BackupImportMode.REPLACE -> {
          dao.deleteAllChapters()
          dao.deleteAllBooks()

          val bookIdMap = HashMap<Long, Long>(backup.books.size)
          for (book in backup.books) {
            val insertedId =
              dao.insertBook(
                BookEntity(
                  id = 0L,
                  name = book.name.trim(),
                  createdAt = book.createdAt,
                )
              )
            if (firstInsertedBookId <= 0L) {
              firstInsertedBookId = insertedId
            }
            bookIdMap[book.id] = insertedId
          }

          for (chapter in backup.chapters) {
            val targetBookId = bookIdMap[chapter.bookId] ?: continue
            dao.insertChapter(
              ChapterEntity(
                id = 0L,
                bookId = targetBookId,
                name = chapter.name.trim(),
                number = chapter.number,
                isCompleted = chapter.isCompleted,
                completedAt =
                  if (chapter.isCompleted) (chapter.completedAt ?: chapter.createdAt) else null,
              )
            )
          }
        }
        BackupImportMode.MERGE -> {
          val existingBooks = dao.getAllBooksOnce()
          val existingChapters = dao.getAllChaptersOnce()

          val bookIdByNormalizedName = HashMap<String, Long>(existingBooks.size + backup.books.size)
          for (existing in existingBooks) {
            bookIdByNormalizedName.putIfAbsent(existing.name.trim().lowercase(), existing.id)
          }

          val existingChapterKeys = HashSet<Pair<Long, Int>>(existingChapters.size + backup.chapters.size)
          for (ch in existingChapters) {
            existingChapterKeys.add(ch.bookId to ch.number)
          }

          val backupBookIdToTargetId = HashMap<Long, Long>(backup.books.size)
          for (book in backup.books) {
            val key = book.name.trim().lowercase()
            val targetBookId =
              bookIdByNormalizedName[key]
                ?: dao.insertBook(
                  BookEntity(
                    id = 0L,
                    name = book.name.trim(),
                    createdAt = book.createdAt,
                  )
                ).also { newId ->
                  bookIdByNormalizedName[key] = newId
                }
            if (firstInsertedBookId <= 0L) {
              firstInsertedBookId = targetBookId
            }
            backupBookIdToTargetId[book.id] = targetBookId
          }

          for (chapter in backup.chapters) {
            val targetBookId = backupBookIdToTargetId[chapter.bookId] ?: continue
            val dedupKey = targetBookId to chapter.number
            if (!existingChapterKeys.add(dedupKey)) {
              continue
            }
            dao.insertChapter(
              ChapterEntity(
                id = 0L,
                bookId = targetBookId,
                name = chapter.name.trim(),
                number = chapter.number,
                isCompleted = chapter.isCompleted,
                completedAt =
                  if (chapter.isCompleted) (chapter.completedAt ?: chapter.createdAt) else null,
              )
            )
          }
        }
      }
    }

    prefsRepo.setDailyGoal(backup.settings.dailyGoal)
    prefsRepo.setReminderEnabled(backup.settings.reminderEnabled)
    prefsRepo.setReminderTime(backup.settings.reminderHour, backup.settings.reminderMinute)
    prefsRepo.setTimerAlarmEnabled(backup.settings.timerAlarmEnabled)
    prefsRepo.setTimerVibrationEnabled(backup.settings.timerVibrationEnabled)
    if (mode == BackupImportMode.REPLACE) {
      prefsRepo.setLastOpenedBookId(firstInsertedBookId)
    }

    val updatedPrefs = prefsRepo.getCurrentPreferences()
    onDataChanged()
    return updatedPrefs
  }

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

  suspend fun toggleChapterCompleted(
    chapter: ChapterEntity,
    targetCompleted: Boolean = !chapter.isCompleted,
    targetCompletedAt: Long? = if (targetCompleted) System.currentTimeMillis() else null,
  ) {
    dao.updateChapterCompletion(chapter.id, targetCompleted, targetCompletedAt)
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

  suspend fun setTimerAlarmEnabled(enabled: Boolean) {
    prefsRepo.setTimerAlarmEnabled(enabled)
  }

  suspend fun setTimerVibrationEnabled(enabled: Boolean) {
    prefsRepo.setTimerVibrationEnabled(enabled)
  }

  suspend fun setTimerPermissionChecklistShown(shown: Boolean) {
    prefsRepo.setTimerPermissionChecklistShown(shown)
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
