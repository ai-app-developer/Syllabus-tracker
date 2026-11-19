package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.example.data.backup.BackupExportResult
import com.example.data.backup.BackupImportMode
import com.example.data.backup.BackupImportResult
import com.example.data.backup.BackupManager
import com.example.data.backup.SyllabusBackup
import com.example.data.local.dao.BookProgressRow
import com.example.data.local.dao.OverallProgressRow
import com.example.data.local.dao.SyllabusDao
import com.example.data.local.dao.TimerDao
import com.example.data.local.dao.WidgetSummaryRow
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.ReadingGoalEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.SessionEventEntity
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

@Immutable
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

@Immutable
data class OverallStatsSummary(
  val totalBooks: Int = 0,
  val totalChapters: Int = 0,
  val completedChapters: Int = 0,
  val remainingChapters: Int = 0,
  val overallPercentage: Int = 0,
  val completedToday: Int = 0,
  val preferences: UserPreferences = UserPreferences(),
)

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

@Immutable
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

@Immutable
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
  private val timerDao: TimerDao? = null,
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

  fun observeBooks(): Flow<List<BookEntity>> = dao.observeBooks().distinctUntilChanged()

  fun observePreferences(): Flow<UserPreferences> = prefsRepo.userPreferences.distinctUntilChanged()

  suspend fun getCurrentPreferences(): UserPreferences = prefsRepo.getCurrentPreferences()

  fun observeBookSummaries(): Flow<List<BookProgressSummary>> =
    dao.observeBookProgressRows()
      .distinctUntilChanged()
      .map { rows -> rows.map(::mapBookProgressRow) }
      .flowOn(Dispatchers.Default)

  fun observeOverallStats(zone: ZoneId = ZoneId.systemDefault()): Flow<OverallStatsSummary> {
    val today = LocalDate.now(zone)
    val startOfTodayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfTodayMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return combine(
      dao.observeOverallProgressRow(startOfTodayMillis, endOfTodayMillis).distinctUntilChanged(),
      prefsRepo.userPreferences.distinctUntilChanged(),
    ) { row: OverallProgressRow, prefs ->
      val remaining = (row.totalChapters - row.completedChapters).coerceAtLeast(0)
      val percentage =
        if (row.totalChapters == 0) 0 else ((row.completedChapters * 100f) / row.totalChapters).roundToInt()
      OverallStatsSummary(
        totalBooks = row.totalBooks,
        totalChapters = row.totalChapters,
        completedChapters = row.completedChapters,
        remainingChapters = remaining,
        overallPercentage = percentage,
        completedToday = row.completedToday,
        preferences = prefs,
      )
    }.distinctUntilChanged().flowOn(Dispatchers.Default)
  }

  fun observeWeeklyTrend(
    period: StudyTrendPeriod,
    zone: ZoneId = ZoneId.systemDefault(),
  ): Flow<List<DailyStudyCount>> {
    val today = LocalDate.now(zone)
    val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
    val weekStart =
      when (period) {
        StudyTrendPeriod.THIS_WEEK -> currentWeekStart
        StudyTrendPeriod.LAST_WEEK -> currentWeekStart.minusWeeks(1)
      }
    val weekStartMillis = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val weekEndMillis = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()

    return dao.observeCompletedTimestampsBetween(weekStartMillis, weekEndMillis)
      .distinctUntilChanged()
      .map { timestamps ->
        val countsByDate =
          timestamps
            .groupingBy { ts -> Instant.ofEpochMilli(ts).atZone(zone).toLocalDate() }
            .eachCount()
        (0 until 7).map { offset ->
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
      .flowOn(Dispatchers.Default)
  }

  fun observeCumulativeSeries(
    period: StudyTrendPeriod,
    zone: ZoneId = ZoneId.systemDefault(),
  ): Flow<List<Float>> {
    val today = LocalDate.now(zone)
    val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
    val weekStart =
      when (period) {
        StudyTrendPeriod.THIS_WEEK -> currentWeekStart
        StudyTrendPeriod.LAST_WEEK -> currentWeekStart.minusWeeks(1)
      }
    val weekStartMillis = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val weekEndMillis = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()

    return combine(
      dao.observeTotalChapterCount().distinctUntilChanged(),
      dao.observeCompletedCountBefore(weekStartMillis).distinctUntilChanged(),
      dao.observeCompletedWithoutTimestampCount().distinctUntilChanged(),
      dao.observeCompletedTimestampsBetween(weekStartMillis, weekEndMillis).distinctUntilChanged(),
    ) { total, beforeWeekCount, withoutDateCount, weekTimestamps ->
      if (total == 0) {
        List(7) { 0f }
      } else {
        val inWeekDates = weekTimestamps.map { ts -> Instant.ofEpochMilli(ts).atZone(zone).toLocalDate() }
        (0 until 7).map { offset ->
          val dayEnd = weekStart.plusDays(offset.toLong())
          val effectiveDate = if (dayEnd.isAfter(today)) today else dayEnd
          val completedInWeekUpToDay = inWeekDates.count { !it.isAfter(effectiveDate) }
          val completedByDay = beforeWeekCount + completedInWeekUpToDay + withoutDateCount
          (completedByDay.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
      }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)
  }

  private fun mapBookProgressRow(row: BookProgressRow): BookProgressSummary {
    val nextChapter =
      if (row.nextChapterId != null && row.nextChapterName != null && row.nextChapterNumber != null) {
        ChapterEntity(
          id = row.nextChapterId,
          bookId = row.book.id,
          name = row.nextChapterName,
          number = row.nextChapterNumber,
          isCompleted = false,
          completedAt = null,
        )
      } else {
        null
      }
    return BookProgressSummary(
      book = row.book,
      totalChapters = row.totalChapters,
      completedChapters = row.completedChapters,
      nextIncompleteChapter = nextChapter,
    )
  }

  fun observeBookById(bookId: Long): Flow<BookEntity?> =
    dao.observeBookById(bookId).distinctUntilChanged()

  fun observeChaptersForBook(bookId: Long): Flow<List<ChapterEntity>> =
    dao.observeChaptersForBook(bookId).distinctUntilChanged()

  suspend fun getChaptersForBookOnce(bookId: Long): List<ChapterEntity> =
    dao.getChaptersForBookOnce(bookId)

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

  suspend fun getExistingBookAndChapterIds(): Pair<Set<Long>, Set<Long>> =
    withContext(Dispatchers.IO) {
      var bookIds: Set<Long> = emptySet()
      var chapterIds: Set<Long> = emptySet()
      runInTransaction {
        bookIds = dao.getAllBooksOnce().map { it.id }.toHashSet()
        chapterIds = dao.getAllChaptersOnce().map { it.id }.toHashSet()
      }
      bookIds to chapterIds
    }

  suspend fun createBackupSnapshot(
    appVersion: String,
    exportedAt: Long = System.currentTimeMillis(),
  ): SyllabusBackup =
    withContext(Dispatchers.IO) {
      var books = emptyList<BookEntity>()
      var chapters = emptyList<ChapterEntity>()
      var sessions = emptyList<ReadingSessionEntity>()
      var sessionEvents = emptyList<SessionEventEntity>()
      var goals = emptyList<ReadingGoalEntity>()

      runInTransaction {
        books = dao.getAllBooksOnce()
        chapters = dao.getAllChaptersOnce()
        if (timerDao != null) {
          val activeSessionId = timerDao.getActiveTimerStateOnce()?.sessionId
          sessions =
            timerDao
              .getAllSessionsOnce()
              .filter { session ->
                val baseId = session.sessionId.substringBefore("#")
                (activeSessionId == null || (session.sessionId != activeSessionId && baseId != activeSessionId)) &&
                  (session.status == "SAVED" || session.status == "COMPLETED")
              }
          val savedSessionKeys = HashSet<String>(sessions.size * 2)
          for (session in sessions) {
            savedSessionKeys.add(session.sessionId)
            savedSessionKeys.add(session.sessionId.substringBefore("#"))
          }
          sessionEvents =
            timerDao.getAllEventsOnce().filter { ev ->
              (activeSessionId == null || ev.sessionId != activeSessionId) &&
                (ev.sessionId in savedSessionKeys || ev.sessionId.substringBefore("#") in savedSessionKeys)
            }
          goals = timerDao.getAllGoalsOnce()
        }
      }

      val prefs = prefsRepo.getCurrentPreferences()
      BackupManager.buildBackup(
        appVersion = appVersion,
        settings = prefs,
        books = books,
        chapters = chapters,
        sessions = sessions,
        sessionEvents = sessionEvents,
        goals = goals,
        exportedAt = exportedAt,
      )
    }

  suspend fun exportBackupToUri(
    context: Context,
    uri: Uri,
    appVersion: String,
  ): BackupExportResult =
    withContext(Dispatchers.IO) {
      val snapshot = createBackupSnapshot(appVersion = appVersion)
      BackupManager.writeBackupToUri(context, uri, snapshot)
      BackupExportResult(
        booksCount = snapshot.books.size,
        chaptersCount = snapshot.chapters.size,
        sessionsCount = snapshot.sessions.size,
        sessionEventsCount = snapshot.sessionEvents.size,
        goalsCount = snapshot.goals.size,
      )
    }

  suspend fun exportBackupJson(appVersion: String): String {
    val snapshot = createBackupSnapshot(appVersion = appVersion)
    val out = java.io.ByteArrayOutputStream(8192)
    BackupManager.writeBackupStream(out, snapshot)
    return out.toString(Charsets.UTF_8.name())
  }

  suspend fun importBackup(
    backup: SyllabusBackup,
    mode: BackupImportMode,
  ): UserPreferences = importBackupWithResult(backup, mode).preferences

  suspend fun importBackupWithResult(
    backup: SyllabusBackup,
    mode: BackupImportMode,
  ): BackupImportResult =
    withContext(Dispatchers.IO) {
      var firstInsertedBookId = -1L
      var booksImported = 0
      var chaptersImported = 0
      var sessionsImported = 0
      var sessionEventsImported = 0
      var goalsImported = 0
      var skippedDuplicates = 0
      var resolvedLastOpenedBookId = -1L

      val importedBookIds = backup.books.map { it.id }.toHashSet()
      val importedChapterIds = backup.chapters.map { it.id }.toHashSet()

      runInTransaction {
        when (mode) {
          BackupImportMode.REPLACE -> {
            for (session in backup.sessions) {
              if (session.bookId !in importedBookIds || session.chapterId !in importedChapterIds) {
                throw IllegalArgumentException(
                  "Session '${session.sessionId}' references a book or chapter not included in this backup for Replace mode."
                )
              }
            }
            for (ev in backup.sessionEvents) {
              if (ev.bookId !in importedBookIds || ev.chapterId !in importedChapterIds) {
                throw IllegalArgumentException(
                  "Session event '${ev.sessionId}' references a book or chapter not included in this backup for Replace mode."
                )
              }
            }

            timerDao?.clearActiveTimerState()
            timerDao?.deleteAllSessionEvents()
            timerDao?.deleteAllReadingSessions()
            timerDao?.deleteAllReadingGoals()
            dao.deleteAllChapters()
            dao.deleteAllBooks()

            if (backup.books.isNotEmpty()) {
              val bookEntities =
                backup.books.map { book ->
                  BookEntity(
                    id = book.id,
                    name = book.name.trim(),
                    createdAt = book.createdAt,
                  )
                }
              dao.insertBooks(bookEntities)
              firstInsertedBookId = bookEntities.first().id
              booksImported = bookEntities.size
            }

            if (backup.chapters.isNotEmpty()) {
              val chaptersToInsert =
                backup.chapters.map { chapter ->
                  ChapterEntity(
                    id = chapter.id,
                    bookId = chapter.bookId,
                    name = chapter.name.trim(),
                    number = chapter.number,
                    isCompleted = chapter.isCompleted,
                    completedAt =
                      if (chapter.isCompleted) (chapter.completedAt ?: chapter.createdAt) else null,
                  )
                }
              dao.insertChapters(chaptersToInsert)
              chaptersImported = chaptersToInsert.size
            }

            if (timerDao != null) {
              val sessionsToInsert =
                backup.sessions
                  .filter { it.status == "SAVED" || it.status == "COMPLETED" }
                  .map { s ->
                    ReadingSessionEntity(
                      id = if (s.id > 0L) s.id else 0L,
                      sessionId = s.sessionId,
                      bookId = s.bookId,
                      chapterId = s.chapterId,
                      mode = s.mode,
                      status = "SAVED",
                      startWallTimeMs = s.startWallTimeMs,
                      endWallTimeMs = s.endWallTimeMs,
                      activeFocusMs = s.activeFocusMs,
                      pauseMs = s.pauseMs,
                      breakMs = s.breakMs,
                      goalMs = s.goalMs,
                      notes = s.notes.trim(),
                      sessionDate = s.sessionDate,
                      createdAt = s.createdAt,
                    )
                  }
              if (sessionsToInsert.isNotEmpty()) {
                val rowIds = timerDao.insertSessions(sessionsToInsert)
                sessionsImported = rowIds.count { it > 0L }
              }

              if (backup.sessionEvents.isNotEmpty()) {
                val eventsToInsert =
                  backup.sessionEvents.map { ev ->
                    SessionEventEntity(
                      id = if (ev.id > 0L) ev.id else 0L,
                      sessionId = ev.sessionId,
                      bookId = ev.bookId,
                      chapterId = ev.chapterId,
                      eventType = ev.eventType,
                      elapsedAtEventMs = ev.elapsedAtEventMs,
                      wallTimeMs = ev.wallTimeMs,
                      label = ev.label,
                    )
                  }
                val eventRowIds = timerDao.insertEvents(eventsToInsert)
                sessionEventsImported = eventRowIds.count { it > 0L }
              }

              if (backup.goals.isNotEmpty()) {
                val goalsToInsert =
                  backup.goals.map { g ->
                    ReadingGoalEntity(
                      id = if (g.id > 0L) g.id else 0L,
                      periodType = g.periodType,
                      targetMinutes = g.targetMinutes,
                      isActive = g.isActive,
                      createdAt = g.createdAt,
                    )
                  }
                timerDao.insertGoals(goalsToInsert)
                goalsImported = goalsToInsert.size
              }
            }

            resolvedLastOpenedBookId =
              if (backup.settings.lastOpenedBookId > 0L && backup.settings.lastOpenedBookId in importedBookIds) {
                backup.settings.lastOpenedBookId
              } else {
                firstInsertedBookId
              }
          }
          BackupImportMode.MERGE -> {
            val existingBooks = dao.getAllBooksOnce()
            val existingChapters = dao.getAllChaptersOnce()
            val existingSessions = timerDao?.getAllSessionsOnce().orEmpty()
            val existingEvents = timerDao?.getAllEventsOnce().orEmpty()
            val existingGoals = timerDao?.getAllGoalsOnce().orEmpty()

            val existingBookIds = existingBooks.mapTo(HashSet(existingBooks.size)) { it.id }
            val existingChapterIds = existingChapters.mapTo(HashSet(existingChapters.size)) { it.id }

            val bookIdByNormalizedName = HashMap<String, Long>(existingBooks.size + backup.books.size)
            for (existing in existingBooks) {
              bookIdByNormalizedName.putIfAbsent(existing.name.trim().lowercase(), existing.id)
            }

            val existingChapterByBookAndNumber =
              HashMap<Pair<Long, Int>, Long>(existingChapters.size + backup.chapters.size)
            for (ch in existingChapters) {
              existingChapterByBookAndNumber.putIfAbsent(ch.bookId to ch.number, ch.id)
            }

            val newBooksToInsert = ArrayList<BookEntity>()
            val newBookNormalizedKeys = ArrayList<String>()
            val seenNewBookKeys = HashSet<String>()
            for (book in backup.books) {
              val key = book.name.trim().lowercase()
              if (!bookIdByNormalizedName.containsKey(key)) {
                if (seenNewBookKeys.add(key)) {
                  newBooksToInsert.add(
                    BookEntity(
                      id = 0L,
                      name = book.name.trim(),
                      createdAt = book.createdAt,
                    )
                  )
                  newBookNormalizedKeys.add(key)
                } else {
                  skippedDuplicates++
                }
              } else {
                skippedDuplicates++
              }
            }
            if (newBooksToInsert.isNotEmpty()) {
              val insertedIds = dao.insertBooks(newBooksToInsert)
              for (i in newBookNormalizedKeys.indices) {
                bookIdByNormalizedName[newBookNormalizedKeys[i]] = insertedIds[i]
              }
              booksImported = insertedIds.size
            }

            val backupBookIdToTargetId = HashMap<Long, Long>(backup.books.size)
            for (book in backup.books) {
              val key = book.name.trim().lowercase()
              val targetBookId = bookIdByNormalizedName[key] ?: continue
              if (firstInsertedBookId <= 0L) {
                firstInsertedBookId = targetBookId
              }
              backupBookIdToTargetId[book.id] = targetBookId
            }

            fun resolveBookId(rawBookId: Long): Long? =
              backupBookIdToTargetId[rawBookId] ?: rawBookId.takeIf { it in existingBookIds }

            val newChaptersToInsert = ArrayList<ChapterEntity>()
            val newChapterBackupIds = ArrayList<Long>()
            val newChapterKeys = ArrayList<Pair<Long, Int>>()
            val pendingChapterKeyToBackupIds =
              HashMap<Pair<Long, Int>, MutableList<Long>>()
            val backupChapterIdToTargetId = HashMap<Long, Long>(backup.chapters.size)

            for (chapter in backup.chapters) {
              val targetBookId = resolveBookId(chapter.bookId) ?: continue
              val key = targetBookId to chapter.number
              val existingChId = existingChapterByBookAndNumber[key]
              if (existingChId != null) {
                backupChapterIdToTargetId[chapter.id] = existingChId
                skippedDuplicates++
              } else if (pendingChapterKeyToBackupIds.containsKey(key)) {
                pendingChapterKeyToBackupIds[key]?.add(chapter.id)
                skippedDuplicates++
              } else {
                pendingChapterKeyToBackupIds[key] = mutableListOf(chapter.id)
                newChaptersToInsert.add(
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
                newChapterBackupIds.add(chapter.id)
                newChapterKeys.add(key)
              }
            }
            if (newChaptersToInsert.isNotEmpty()) {
              val insertedIds = dao.insertChapters(newChaptersToInsert)
              for (i in newChapterKeys.indices) {
                val insertedId = insertedIds[i]
                val key = newChapterKeys[i]
                existingChapterByBookAndNumber[key] = insertedId
                for (bChId in pendingChapterKeyToBackupIds[key].orEmpty()) {
                  backupChapterIdToTargetId[bChId] = insertedId
                }
              }
              chaptersImported = insertedIds.size
            }

            fun resolveChapterId(rawChapterId: Long): Long? =
              backupChapterIdToTargetId[rawChapterId] ?: rawChapterId.takeIf { it in existingChapterIds }

            if (timerDao != null) {
              val seenSessionIds = existingSessions.mapTo(HashSet(existingSessions.size)) { it.sessionId }
              val seenCompositeKeys =
                existingSessions.mapTo(HashSet(existingSessions.size)) {
                  Triple(it.startWallTimeMs, it.chapterId, it.activeFocusMs)
                }

              val sessionsToInsert = ArrayList<ReadingSessionEntity>()
              val importedBaseSessionIds = HashSet<String>()

              for (s in backup.sessions) {
                if (s.status != "SAVED" && s.status != "COMPLETED") continue
                val targetBookId = resolveBookId(s.bookId) ?: continue
                val targetChapterId = resolveChapterId(s.chapterId) ?: continue
                val compositeKey = Triple(s.startWallTimeMs, targetChapterId, s.activeFocusMs)

                if (s.sessionId in seenSessionIds || compositeKey in seenCompositeKeys) {
                  skippedDuplicates++
                  continue
                }
                seenSessionIds.add(s.sessionId)
                seenCompositeKeys.add(compositeKey)
                importedBaseSessionIds.add(s.sessionId)
                importedBaseSessionIds.add(s.sessionId.substringBefore("#"))

                sessionsToInsert.add(
                  ReadingSessionEntity(
                    id = 0L,
                    sessionId = s.sessionId,
                    bookId = targetBookId,
                    chapterId = targetChapterId,
                    mode = s.mode,
                    status = "SAVED",
                    startWallTimeMs = s.startWallTimeMs,
                    endWallTimeMs = s.endWallTimeMs,
                    activeFocusMs = s.activeFocusMs,
                    pauseMs = s.pauseMs,
                    breakMs = s.breakMs,
                    goalMs = s.goalMs,
                    notes = s.notes.trim(),
                    sessionDate = s.sessionDate,
                    createdAt = s.createdAt,
                  )
                )
              }
              if (sessionsToInsert.isNotEmpty()) {
                val rowIds = timerDao.insertSessions(sessionsToInsert)
                sessionsImported = rowIds.count { it > 0L }
              }

              data class EventDedupKey(
                val sessionId: String,
                val eventType: String,
                val wallTimeMs: Long,
                val elapsedAtEventMs: Long,
              )
              val seenEventKeys =
                existingEvents.mapTo(HashSet(existingEvents.size)) {
                  EventDedupKey(it.sessionId, it.eventType, it.wallTimeMs, it.elapsedAtEventMs)
                }
              val eventsToInsert = ArrayList<SessionEventEntity>()
              for (ev in backup.sessionEvents) {
                val targetBookId = resolveBookId(ev.bookId) ?: continue
                val targetChapterId = resolveChapterId(ev.chapterId) ?: continue
                val baseEvSession = ev.sessionId.substringBefore("#")
                if (
                  ev.sessionId !in seenSessionIds &&
                    baseEvSession !in importedBaseSessionIds &&
                    ev.sessionId !in importedBaseSessionIds
                ) {
                  continue
                }
                val key =
                  EventDedupKey(ev.sessionId, ev.eventType, ev.wallTimeMs, ev.elapsedAtEventMs)
                if (!seenEventKeys.add(key)) {
                  continue
                }
                eventsToInsert.add(
                  SessionEventEntity(
                    id = 0L,
                    sessionId = ev.sessionId,
                    bookId = targetBookId,
                    chapterId = targetChapterId,
                    eventType = ev.eventType,
                    elapsedAtEventMs = ev.elapsedAtEventMs,
                    wallTimeMs = ev.wallTimeMs,
                    label = ev.label,
                  )
                )
              }
              if (eventsToInsert.isNotEmpty()) {
                val rowIds = timerDao.insertEvents(eventsToInsert)
                sessionEventsImported = rowIds.count { it > 0L }
              }

              data class GoalDedupKey(
                val periodType: String,
                val targetMinutes: Int,
                val isActive: Boolean,
                val createdAt: Long,
              )
              val seenGoalKeys =
                existingGoals.mapTo(HashSet(existingGoals.size)) {
                  GoalDedupKey(it.periodType, it.targetMinutes, it.isActive, it.createdAt)
                }
              val goalsToInsert = ArrayList<ReadingGoalEntity>()
              for (g in backup.goals) {
                val key = GoalDedupKey(g.periodType, g.targetMinutes, g.isActive, g.createdAt)
                if (!seenGoalKeys.add(key)) {
                  skippedDuplicates++
                  continue
                }
                goalsToInsert.add(
                  ReadingGoalEntity(
                    id = 0L,
                    periodType = g.periodType,
                    targetMinutes = g.targetMinutes,
                    isActive = g.isActive,
                    createdAt = g.createdAt,
                  )
                )
              }
              if (goalsToInsert.isNotEmpty()) {
                timerDao.insertGoals(goalsToInsert)
                goalsImported = goalsToInsert.size
              }
            }

            val currentPrefs = prefsRepo.getCurrentPreferences()
            resolvedLastOpenedBookId =
              backupBookIdToTargetId[backup.settings.lastOpenedBookId]
                ?: currentPrefs.lastOpenedBookId.takeIf { it > 0L }
                ?: firstInsertedBookId
          }
        }
      }

      val targetPrefs =
        UserPreferences(
          dailyGoal = backup.settings.dailyGoal,
          reminderEnabled = backup.settings.reminderEnabled,
          reminderHour = backup.settings.reminderHour,
          reminderMinute = backup.settings.reminderMinute,
          lastOpenedBookId = resolvedLastOpenedBookId,
          timerAlarmEnabled = backup.settings.timerAlarmEnabled,
          timerVibrationEnabled = backup.settings.timerVibrationEnabled,
          timerPermissionChecklistShown = backup.settings.timerPermissionChecklistShown,
          pomodoroFocusMinutes = backup.settings.pomodoroFocusMinutes,
          pomodoroBreakMinutes = backup.settings.pomodoroBreakMinutes,
        )
      val restoredPrefs = prefsRepo.restorePreferences(targetPrefs)
      onDataChanged()

      BackupImportResult(
        mode = mode,
        booksImported = booksImported,
        chaptersImported = chaptersImported,
        sessionsImported = sessionsImported,
        sessionEventsImported = sessionEventsImported,
        goalsImported = goalsImported,
        skippedDuplicates = skippedDuplicates,
        preferences = restoredPrefs,
      )
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

  suspend fun setPomodoroDurations(focusMinutes: Int, breakMinutes: Int) {
    prefsRepo.setPomodoroDurations(focusMinutes, breakMinutes)
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
