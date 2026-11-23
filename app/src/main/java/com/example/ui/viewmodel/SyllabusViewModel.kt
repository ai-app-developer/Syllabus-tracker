package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.SyllabusApplication
import com.example.data.backup.BackupImportMode
import com.example.data.backup.BackupImportResult
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupValidationResult
import com.example.data.backup.SyllabusBackup
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.preferences.UserPreferences
import com.example.data.repository.BookProgressSummary
import com.example.data.repository.DailyStudyCount
import com.example.data.repository.OverallStatsSummary
import com.example.data.repository.StudyTrendPeriod
import com.example.data.repository.SyllabusRepository
import com.example.notifications.StudyReminderScheduler
import com.example.ui.model.StableList
import com.example.widgets.HomeWidgetType
import com.example.widgets.WidgetPinManager
import com.example.widgets.WidgetPinRequestResult
import com.example.widgets.WidgetPromptScheduler
import com.example.widgets.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class BookFilter {
  ALL,
  IN_PROGRESS,
  COMPLETED,
}

enum class BookSortOrder {
  RECENT,
  NAME_ASC,
}

enum class ProgressTab {
  OVERALL,
  BY_BOOK,
  TIMER,
}

@Immutable
data class AddChapterSheetState(
  val isOpen: Boolean = false,
  val preselectedBookId: Long? = null,
  val editingChapter: ChapterEntity? = null,
)

@Immutable
data class BookFormDialogState(
  val isOpen: Boolean = false,
  val editingBook: BookEntity? = null,
)

@Immutable
sealed interface DeleteConfirmState {
  data object None : DeleteConfirmState

  @Immutable
  data class DeleteBook(val book: BookEntity) : DeleteConfirmState

  @Immutable
  data class DeleteChapter(val chapter: ChapterEntity) : DeleteConfirmState
}

@Immutable
data class SyllabusDialogsUiState(
  val addChapterSheet: AddChapterSheetState = AddChapterSheetState(),
  val bookFormDialog: BookFormDialogState = BookFormDialogState(),
  val deleteConfirm: DeleteConfirmState = DeleteConfirmState.None,
  val isDailyGoalDialogOpen: Boolean = false,
  val isTimePickerOpen: Boolean = false,
  val pendingBackupImport: SyllabusBackup? = null,
  val importResult: BackupImportResult? = null,
  val backupErrorMessage: String? = null,
)

@Immutable
sealed interface WidgetPromptSheetState {
  data object Hidden : WidgetPromptSheetState

  @Immutable
  data class Visible(val isFallbackManualSteps: Boolean = false) : WidgetPromptSheetState
}

@Immutable
data class HomeUiState(
  val isLoading: Boolean = true,
  val totalBooks: Int = 0,
  val totalChapters: Int = 0,
  val completedChapters: Int = 0,
  val overallPercentage: Int = 0,
  val completedToday: Int = 0,
  val continueStudyingBook: BookProgressSummary? = null,
  val preferences: UserPreferences = UserPreferences(),
)

@Immutable
data class BooksUiState(
  val hasAnyBooks: Boolean = false,
  val filteredBooks: StableList<BookProgressSummary> = StableList(),
  val searchQuery: String = "",
  val isSearchActive: Boolean = false,
  val bookFilter: BookFilter = BookFilter.ALL,
  val bookSortOrder: BookSortOrder = BookSortOrder.RECENT,
)

@Immutable
data class ProgressUiState(
  val totalBooks: Int = 0,
  val totalChapters: Int = 0,
  val completedChapters: Int = 0,
  val remainingChapters: Int = 0,
  val overallPercentage: Int = 0,
  val books: StableList<BookProgressSummary> = StableList(),
  val progressTab: ProgressTab = ProgressTab.OVERALL,
  val trendPeriod: StudyTrendPeriod = StudyTrendPeriod.THIS_WEEK,
  val weeklyTrend: StableList<DailyStudyCount> = StableList(),
  val cumulativeSeries: StableList<Float> = StableList(List(7) { 0f }),
)

@Immutable
data class BookDetailUiState(
  val book: BookEntity? = null,
  val chapters: StableList<ChapterEntity> = StableList(),
  val completedCount: Int = 0,
  val totalCount: Int = 0,
  val percentage: Int = 0,
  val fraction: Float = 0f,
)

private data class BooksFilterControls(
  val searchQuery: String = "",
  val isSearchActive: Boolean = false,
  val bookFilter: BookFilter = BookFilter.ALL,
  val bookSortOrder: BookSortOrder = BookSortOrder.RECENT,
)

private data class QueuedChapterOp(
  val opId: Long,
  val op: PendingChapterOp,
)

private sealed interface PendingChapterOp {
  data class Toggle(
    val chapter: ChapterEntity,
    val targetCompleted: Boolean,
    val targetCompletedAt: Long?,
  ) : PendingChapterOp

  data class Add(
    val chapter: ChapterEntity,
  ) : PendingChapterOp

  data class Edit(
    val previous: ChapterEntity,
    val updated: ChapterEntity,
  ) : PendingChapterOp

  data class Delete(
    val chapter: ChapterEntity,
  ) : PendingChapterOp
}

@OptIn(ExperimentalCoroutinesApi::class)
class SyllabusViewModel(application: Application) : AndroidViewModel(application) {
  private val app = application as SyllabusApplication
  private val repository: SyllabusRepository = app.syllabusRepository

  private val nextOpId = AtomicLong(1L)
  private val pendingChapterOps = MutableStateFlow<List<QueuedChapterOp>>(emptyList())

  private val booksControls = MutableStateFlow(BooksFilterControls())
  private val progressTabState = MutableStateFlow(ProgressTab.OVERALL)
  private val trendPeriodState = MutableStateFlow(StudyTrendPeriod.THIS_WEEK)
  private val dialogsControls = MutableStateFlow(SyllabusDialogsUiState())

  private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

  private val _widgetPromptSheet = MutableStateFlow<WidgetPromptSheetState>(WidgetPromptSheetState.Hidden)
  val widgetPromptSheet: StateFlow<WidgetPromptSheetState> = _widgetPromptSheet.asStateFlow()

  private val _widgetCounts = MutableStateFlow<Map<HomeWidgetType, Int>>(emptyMap())
  val widgetCounts: StateFlow<Map<HomeWidgetType, Int>> = _widgetCounts.asStateFlow()

  private val _isPinRequestActive = MutableStateFlow(false)
  val isPinRequestActive: StateFlow<Boolean> = _isPinRequestActive.asStateFlow()

  private val _showMoreWidgetFallbackSheet = MutableStateFlow(false)
  val showMoreWidgetFallbackSheet: StateFlow<Boolean> = _showMoreWidgetFallbackSheet.asStateFlow()

  private var launchWidgetPromptCheckStarted = false
  private var promptVisibilityRecordedForOpen = false

  val dialogsState: StateFlow<SyllabusDialogsUiState> = dialogsControls

  init {
    viewModelScope.launch {
      WidgetPinManager.pinSuccessEvents.collect {
        _isPinRequestActive.value = false
        _widgetPromptSheet.value = WidgetPromptSheetState.Hidden
        _showMoreWidgetFallbackSheet.value = false
        refreshWidgetCounts()
        _snackbarMessages.tryEmit(app.getString(R.string.widget_added_snackbar))
      }
    }
  }

  val preferencesState: StateFlow<UserPreferences> =
    repository.observePreferences()
      .distinctUntilChanged()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferences(),
      )

  val bookSummariesState: StateFlow<StableList<BookProgressSummary>> =
    combine(repository.observeBookSummaries(), pendingChapterOps) { baseBooks, ops ->
      StableList(applyOptimisticOpsToBookSummaries(baseBooks, ops))
    }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StableList(),
      )

  private val overallStatsFlow: Flow<OverallStatsSummary> =
    combine(repository.observeOverallStats(), pendingChapterOps) { stats, ops ->
      applyOptimisticOpsToOverallStats(stats, ops)
    }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)

  val homeUiState: StateFlow<HomeUiState> =
    combine(overallStatsFlow, bookSummariesState) { stats, books ->
      val prefs = stats.preferences
      val lastOpenedSummary = books.firstOrNull { it.book.id == prefs.lastOpenedBookId }
      val continueBook =
        when {
          lastOpenedSummary != null && lastOpenedSummary.nextIncompleteChapter != null ->
            lastOpenedSummary
          else ->
            books.firstOrNull { it.nextIncompleteChapter != null }
              ?: lastOpenedSummary
              ?: books.firstOrNull()
        }
      HomeUiState(
        isLoading = false,
        totalBooks = stats.totalBooks,
        totalChapters = stats.totalChapters,
        completedChapters = stats.completedChapters,
        overallPercentage = stats.overallPercentage,
        completedToday = stats.completedToday,
        continueStudyingBook = continueBook,
        preferences = prefs,
      )
    }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(),
      )

  val booksUiState: StateFlow<BooksUiState> =
    combine(bookSummariesState, booksControls) { books, ctrl ->
      val query = ctrl.searchQuery.trim()
      val filtered =
        books.filter { summary ->
          val matchesQuery =
            query.isEmpty() || summary.book.name.contains(query, ignoreCase = true)
          val matchesFilter =
            when (ctrl.bookFilter) {
              BookFilter.ALL -> true
              BookFilter.IN_PROGRESS -> !summary.isCompleted
              BookFilter.COMPLETED -> summary.isCompleted
            }
          matchesQuery && matchesFilter
        }
      val sorted =
        when (ctrl.bookSortOrder) {
          BookSortOrder.RECENT -> filtered
          BookSortOrder.NAME_ASC -> filtered.sortedBy { it.book.name.lowercase() }
        }
      BooksUiState(
        hasAnyBooks = books.isNotEmpty(),
        filteredBooks = StableList(sorted),
        searchQuery = ctrl.searchQuery,
        isSearchActive = ctrl.isSearchActive,
        bookFilter = ctrl.bookFilter,
        bookSortOrder = ctrl.bookSortOrder,
      )
    }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BooksUiState(),
      )

  private val overallTrendBundleFlow: Flow<Pair<StableList<DailyStudyCount>, StableList<Float>>> =
    progressTabState
      .flatMapLatest { tab ->
        if (tab != ProgressTab.OVERALL) {
          flowOf(StableList<DailyStudyCount>() to StableList(List(7) { 0f }))
        } else {
          trendPeriodState
            .flatMapLatest { period ->
              combine(
                repository.observeWeeklyTrend(period),
                repository.observeCumulativeSeries(period),
              ) { trend, cumulative ->
                StableList(trend) to StableList(cumulative)
              }
            }
        }
      }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)

  val progressUiState: StateFlow<ProgressUiState> =
    combine(
      overallStatsFlow,
      bookSummariesState,
      progressTabState,
      trendPeriodState,
      overallTrendBundleFlow,
    ) { stats, books, tab, trendPeriod, (weeklyTrend, cumulativeSeries) ->
      ProgressUiState(
        totalBooks = stats.totalBooks,
        totalChapters = stats.totalChapters,
        completedChapters = stats.completedChapters,
        remainingChapters = stats.remainingChapters,
        overallPercentage = stats.overallPercentage,
        books = books,
        progressTab = tab,
        trendPeriod = trendPeriod,
        weeklyTrend = weeklyTrend,
        cumulativeSeries = cumulativeSeries,
      )
    }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProgressUiState(),
      )

  fun observeBookDetailUiState(bookId: Long): Flow<BookDetailUiState> {
    return combine(
      repository.observeBookById(bookId),
      repository.observeChaptersForBook(bookId),
      pendingChapterOps,
    ) { book, dbChapters, ops ->
      val resolved = applyOptimisticOpsToChapterList(bookId, dbChapters, ops)
      val completedCount = resolved.count { it.isCompleted }
      val totalCount = resolved.size
      val percentage =
        if (totalCount == 0) 0 else ((completedCount * 100f) / totalCount).roundToInt()
      val fraction =
        if (totalCount == 0) 0f else (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
      BookDetailUiState(
        book = book,
        chapters = StableList(resolved),
        completedCount = completedCount,
        totalCount = totalCount,
        percentage = percentage,
        fraction = fraction,
      )
    }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
  }

  private fun applyOptimisticOpsToChapterList(
    bookId: Long,
    dbChapters: List<ChapterEntity>,
    ops: List<QueuedChapterOp>,
  ): List<ChapterEntity> {
    if (ops.isEmpty()) return dbChapters
    val mutable = dbChapters.toMutableList()
    for (entry in ops) {
      when (val op = entry.op) {
        is PendingChapterOp.Toggle -> {
          if (op.chapter.bookId == bookId) {
            val idx = mutable.indexOfFirst { it.id == op.chapter.id }
            if (idx >= 0) {
              mutable[idx] =
                mutable[idx].copy(
                  isCompleted = op.targetCompleted,
                  completedAt = op.targetCompletedAt,
                )
            }
          }
        }
        is PendingChapterOp.Add -> {
          if (op.chapter.bookId == bookId) {
            val alreadyPersisted =
              mutable.any {
                it.bookId == op.chapter.bookId &&
                  it.number == op.chapter.number &&
                  it.name == op.chapter.name
              }
            if (!alreadyPersisted) {
              mutable.add(op.chapter)
            }
          }
        }
        is PendingChapterOp.Edit -> {
          val idx = mutable.indexOfFirst { it.id == op.updated.id }
          if (op.updated.bookId == bookId) {
            if (idx >= 0) {
              mutable[idx] = op.updated
            } else {
              mutable.add(op.updated)
            }
          } else if (idx >= 0) {
            mutable.removeAt(idx)
          }
        }
        is PendingChapterOp.Delete -> {
          if (op.chapter.bookId == bookId) {
            mutable.removeAll { it.id == op.chapter.id }
          }
        }
      }
    }
    mutable.sortWith(compareBy({ it.number }, { it.id }))
    return mutable
  }

  private fun applyOptimisticOpsToBookSummaries(
    baseBooks: List<BookProgressSummary>,
    ops: List<QueuedChapterOp>,
  ): List<BookProgressSummary> {
    if (ops.isEmpty() || baseBooks.isEmpty()) return baseBooks
    val result = baseBooks.toMutableList()
    for (entry in ops) {
      when (val op = entry.op) {
        is PendingChapterOp.Toggle -> {
          val idx = result.indexOfFirst { it.book.id == op.chapter.bookId }
          if (idx >= 0) {
            val item = result[idx]
            val delta =
              when {
                !op.chapter.isCompleted && op.targetCompleted -> 1
                op.chapter.isCompleted && !op.targetCompleted -> -1
                else -> 0
              }
            val newCompleted = (item.completedChapters + delta).coerceIn(0, item.totalChapters)
            val updatedNext =
              when {
                op.targetCompleted && item.nextIncompleteChapter?.id == op.chapter.id -> null
                !op.targetCompleted && item.nextIncompleteChapter == null ->
                  op.chapter.copy(isCompleted = false, completedAt = null)
                else -> item.nextIncompleteChapter
              }
            result[idx] =
              item.copy(
                completedChapters = newCompleted,
                nextIncompleteChapter = updatedNext,
              )
          }
        }
        is PendingChapterOp.Add -> {
          val idx = result.indexOfFirst { it.book.id == op.chapter.bookId }
          if (idx >= 0) {
            val item = result[idx]
            val nextTotal = item.totalChapters + 1
            val nextCompleted =
              if (op.chapter.isCompleted) item.completedChapters + 1 else item.completedChapters
            val nextIncomplete =
              if (!op.chapter.isCompleted && item.nextIncompleteChapter == null) op.chapter
              else item.nextIncompleteChapter
            result[idx] =
              item.copy(
                totalChapters = nextTotal,
                completedChapters = nextCompleted.coerceIn(0, nextTotal),
                nextIncompleteChapter = nextIncomplete,
              )
          }
        }
        is PendingChapterOp.Edit -> {
          val prevBookId = op.previous.bookId
          val nextBookId = op.updated.bookId
          if (prevBookId == nextBookId) {
            val idx = result.indexOfFirst { it.book.id == nextBookId }
            if (idx >= 0) {
              val item = result[idx]
              val delta =
                when {
                  !op.previous.isCompleted && op.updated.isCompleted -> 1
                  op.previous.isCompleted && !op.updated.isCompleted -> -1
                  else -> 0
                }
              val nextCompleted = (item.completedChapters + delta).coerceIn(0, item.totalChapters)
              val nextIncomplete =
                if (item.nextIncompleteChapter?.id == op.updated.id) {
                  if (op.updated.isCompleted) null else op.updated
                } else {
                  item.nextIncompleteChapter
                }
              result[idx] =
                item.copy(
                  completedChapters = nextCompleted,
                  nextIncompleteChapter = nextIncomplete,
                )
            }
          }
        }
        is PendingChapterOp.Delete -> {
          val idx = result.indexOfFirst { it.book.id == op.chapter.bookId }
          if (idx >= 0) {
            val item = result[idx]
            val nextTotal = (item.totalChapters - 1).coerceAtLeast(0)
            val nextCompleted =
              (if (op.chapter.isCompleted) item.completedChapters - 1 else item.completedChapters)
                .coerceIn(0, nextTotal)
            val nextIncomplete =
              if (item.nextIncompleteChapter?.id == op.chapter.id) null
              else item.nextIncompleteChapter
            result[idx] =
              item.copy(
                totalChapters = nextTotal,
                completedChapters = nextCompleted,
                nextIncompleteChapter = nextIncomplete,
              )
          }
        }
      }
    }
    return result
  }

  private fun applyOptimisticOpsToOverallStats(
    stats: OverallStatsSummary,
    ops: List<QueuedChapterOp>,
  ): OverallStatsSummary {
    if (ops.isEmpty()) return stats
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun isCompletedToday(completed: Boolean, completedAt: Long?): Boolean =
      completed && completedAt != null && completedAt >= startOfToday && completedAt < endOfToday

    var totalDelta = 0
    var completedDelta = 0
    var todayDelta = 0

    for (entry in ops) {
      when (val op = entry.op) {
        is PendingChapterOp.Toggle -> {
          if (!op.chapter.isCompleted && op.targetCompleted) {
            completedDelta += 1
          } else if (op.chapter.isCompleted && !op.targetCompleted) {
            completedDelta -= 1
          }
          val wasToday = isCompletedToday(op.chapter.isCompleted, op.chapter.completedAt)
          val nowToday = isCompletedToday(op.targetCompleted, op.targetCompletedAt)
          if (!wasToday && nowToday) todayDelta += 1 else if (wasToday && !nowToday) todayDelta -= 1
        }
        is PendingChapterOp.Add -> {
          totalDelta += 1
          if (op.chapter.isCompleted) completedDelta += 1
          if (isCompletedToday(op.chapter.isCompleted, op.chapter.completedAt)) todayDelta += 1
        }
        is PendingChapterOp.Edit -> {
          if (!op.previous.isCompleted && op.updated.isCompleted) {
            completedDelta += 1
          } else if (op.previous.isCompleted && !op.updated.isCompleted) {
            completedDelta -= 1
          }
          val wasToday = isCompletedToday(op.previous.isCompleted, op.previous.completedAt)
          val nowToday = isCompletedToday(op.updated.isCompleted, op.updated.completedAt)
          if (!wasToday && nowToday) todayDelta += 1 else if (wasToday && !nowToday) todayDelta -= 1
        }
        is PendingChapterOp.Delete -> {
          totalDelta -= 1
          if (op.chapter.isCompleted) completedDelta -= 1
          if (isCompletedToday(op.chapter.isCompleted, op.chapter.completedAt)) todayDelta -= 1
        }
      }
    }

    val nextTotal = (stats.totalChapters + totalDelta).coerceAtLeast(0)
    val nextCompleted = (stats.completedChapters + completedDelta).coerceIn(0, nextTotal)
    val nextRemaining = (nextTotal - nextCompleted).coerceAtLeast(0)
    val nextPct = if (nextTotal == 0) 0 else ((nextCompleted * 100f) / nextTotal).roundToInt()
    val nextToday = (stats.completedToday + todayDelta).coerceAtLeast(0)

    return stats.copy(
      totalChapters = nextTotal,
      completedChapters = nextCompleted,
      remainingChapters = nextRemaining,
      overallPercentage = nextPct,
      completedToday = nextToday,
    )
  }

  fun markBookOpened(bookId: Long) {
    app.applicationScope.launch(Dispatchers.IO) {
      repository.markBookOpened(bookId)
    }
  }

  fun toggleSearchActive() {
    booksControls.update {
      val next = !it.isSearchActive
      it.copy(isSearchActive = next, searchQuery = if (next) it.searchQuery else "")
    }
  }

  fun setSearchQuery(query: String) {
    booksControls.update { it.copy(searchQuery = query) }
  }

  fun setBookFilter(filter: BookFilter) {
    booksControls.update { it.copy(bookFilter = filter) }
  }

  fun setBookSortOrder(order: BookSortOrder) {
    booksControls.update { it.copy(bookSortOrder = order) }
  }

  fun setProgressTab(tab: ProgressTab) {
    progressTabState.value = tab
  }

  fun setTrendPeriod(period: StudyTrendPeriod) {
    trendPeriodState.value = period
  }

  fun openAddChapterSheet(preselectedBookId: Long? = null) {
    dialogsControls.update {
      it.copy(
        addChapterSheet =
          AddChapterSheetState(
            isOpen = true,
            preselectedBookId = preselectedBookId,
            editingChapter = null,
          )
      )
    }
  }

  fun openEditChapterSheet(chapter: ChapterEntity) {
    dialogsControls.update {
      it.copy(
        addChapterSheet =
          AddChapterSheetState(
            isOpen = true,
            preselectedBookId = chapter.bookId,
            editingChapter = chapter,
          )
      )
    }
  }

  fun closeAddChapterSheet() {
    dialogsControls.update { it.copy(addChapterSheet = AddChapterSheetState()) }
  }

  fun openAddBookDialog() {
    dialogsControls.update { it.copy(bookFormDialog = BookFormDialogState(isOpen = true)) }
  }

  fun openEditBookDialog(book: BookEntity) {
    dialogsControls.update {
      it.copy(bookFormDialog = BookFormDialogState(isOpen = true, editingBook = book))
    }
  }

  fun closeBookFormDialog() {
    dialogsControls.update { it.copy(bookFormDialog = BookFormDialogState()) }
  }

  fun requestDeleteBook(book: BookEntity) {
    dialogsControls.update { it.copy(deleteConfirm = DeleteConfirmState.DeleteBook(book)) }
  }

  fun requestDeleteChapter(chapter: ChapterEntity) {
    dialogsControls.update { it.copy(deleteConfirm = DeleteConfirmState.DeleteChapter(chapter)) }
  }

  fun dismissDeleteConfirm() {
    dialogsControls.update { it.copy(deleteConfirm = DeleteConfirmState.None) }
  }

  fun confirmDelete(onBookDeleted: (() -> Unit)? = null) {
    val current = dialogsControls.value.deleteConfirm
    dialogsControls.update { it.copy(deleteConfirm = DeleteConfirmState.None) }
    if (current is DeleteConfirmState.DeleteBook) {
      onBookDeleted?.invoke()
    }
    val opId =
      if (current is DeleteConfirmState.DeleteChapter) {
        val id = nextOpId.incrementAndGet()
        pendingChapterOps.update { it + QueuedChapterOp(id, PendingChapterOp.Delete(current.chapter)) }
        id
      } else {
        null
      }

    app.applicationScope.launch(Dispatchers.IO) {
      try {
        when (current) {
          is DeleteConfirmState.DeleteBook -> repository.deleteBook(current.book.id)
          is DeleteConfirmState.DeleteChapter -> repository.deleteChapter(current.chapter.id)
          DeleteConfirmState.None -> Unit
        }
      } finally {
        if (opId != null) {
          pendingChapterOps.update { list -> list.filterNot { it.opId == opId } }
        }
      }
    }
  }

  fun saveBook(name: String, editingBook: BookEntity? = null) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    dialogsControls.update { it.copy(bookFormDialog = BookFormDialogState()) }
    app.applicationScope.launch(Dispatchers.IO) {
      if (editingBook == null) {
        val newId = repository.addBook(trimmed)
        if (dialogsControls.value.addChapterSheet.isOpen) {
          dialogsControls.update {
            it.copy(addChapterSheet = it.addChapterSheet.copy(preselectedBookId = newId))
          }
        }
      } else {
        repository.updateBook(editingBook.copy(name = trimmed))
      }
    }
  }

  fun saveChapter(
    bookId: Long,
    name: String,
    number: Int,
    isCompleted: Boolean,
    editingChapter: ChapterEntity? = null,
  ) {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || number <= 0) return
    dialogsControls.update { it.copy(addChapterSheet = AddChapterSheetState()) }

    val now = System.currentTimeMillis()
    val opId = nextOpId.incrementAndGet()
    val op: PendingChapterOp =
      if (editingChapter == null) {
        PendingChapterOp.Add(
          ChapterEntity(
            id = -opId,
            bookId = bookId,
            name = trimmed,
            number = number,
            isCompleted = isCompleted,
            completedAt = if (isCompleted) now else null,
          )
        )
      } else {
        val newCompletedAt =
          when {
            isCompleted && editingChapter.completedAt != null -> editingChapter.completedAt
            isCompleted -> now
            else -> null
          }
        PendingChapterOp.Edit(
          previous = editingChapter,
          updated =
            editingChapter.copy(
              bookId = bookId,
              name = trimmed,
              number = number,
              isCompleted = isCompleted,
              completedAt = newCompletedAt,
            ),
        )
      }
    pendingChapterOps.update { it + QueuedChapterOp(opId, op) }

    app.applicationScope.launch(Dispatchers.IO) {
      try {
        if (editingChapter == null) {
          repository.addChapter(bookId, trimmed, number, isCompleted)
        } else {
          repository.updateChapter(
            editingChapter.copy(bookId = bookId, name = trimmed, number = number),
            isCompleted = isCompleted,
          )
        }
      } finally {
        pendingChapterOps.update { list -> list.filterNot { it.opId == opId } }
      }
    }
  }

  fun toggleChapterCompleted(chapter: ChapterEntity) {
    val nextState = !chapter.isCompleted
    val nextCompletedAt = if (nextState) System.currentTimeMillis() else null
    val opId = nextOpId.incrementAndGet()
    pendingChapterOps.update {
      it +
        QueuedChapterOp(
          opId = opId,
          op =
            PendingChapterOp.Toggle(
              chapter = chapter,
              targetCompleted = nextState,
              targetCompletedAt = nextCompletedAt,
            ),
        )
    }
    app.applicationScope.launch(Dispatchers.IO) {
      try {
        repository.toggleChapterCompleted(
          chapter = chapter,
          targetCompleted = nextState,
          targetCompletedAt = nextCompletedAt,
        )
      } finally {
        pendingChapterOps.update { list -> list.filterNot { it.opId == opId } }
      }
    }
  }

  fun openDailyGoalDialog() {
    dialogsControls.update { it.copy(isDailyGoalDialogOpen = true) }
  }

  fun closeDailyGoalDialog() {
    dialogsControls.update { it.copy(isDailyGoalDialogOpen = false) }
  }

  fun saveDailyGoal(goal: Int) {
    dialogsControls.update { it.copy(isDailyGoalDialogOpen = false) }
    app.applicationScope.launch(Dispatchers.IO) {
      repository.setDailyGoal(goal)
    }
  }

  fun openTimePicker() {
    dialogsControls.update { it.copy(isTimePickerOpen = true) }
  }

  fun closeTimePicker() {
    dialogsControls.update { it.copy(isTimePickerOpen = false) }
  }

  fun setReminderEnabled(enabled: Boolean) {
    app.applicationScope.launch {
      val prefs =
        withContext(Dispatchers.IO) {
          repository.setReminderEnabled(enabled)
          repository.getCurrentPreferences()
        }
      if (enabled) {
        StudyReminderScheduler.scheduleDailyReminder(
          context = app,
          hour = prefs.reminderHour,
          minute = prefs.reminderMinute,
        )
      } else {
        StudyReminderScheduler.cancelReminder(app)
      }
    }
  }

  fun saveReminderTime(hour: Int, minute: Int) {
    dialogsControls.update { it.copy(isTimePickerOpen = false) }
    app.applicationScope.launch {
      val prefs =
        withContext(Dispatchers.IO) {
          repository.setReminderTime(hour, minute)
          repository.getCurrentPreferences()
        }
      if (prefs.reminderEnabled) {
        StudyReminderScheduler.scheduleDailyReminder(app, hour, minute)
      }
    }
  }

  fun setTimerAlarmEnabled(enabled: Boolean) {
    app.applicationScope.launch(Dispatchers.IO) {
      repository.setTimerAlarmEnabled(enabled)
    }
  }

  fun setTimerVibrationEnabled(enabled: Boolean) {
    app.applicationScope.launch(Dispatchers.IO) {
      repository.setTimerVibrationEnabled(enabled)
    }
  }

  fun setTimerPermissionChecklistShown(shown: Boolean = true) {
    app.applicationScope.launch(Dispatchers.IO) {
      repository.setTimerPermissionChecklistShown(shown)
    }
  }

  fun exportBackupToUri(uri: Uri) {
    app.applicationScope.launch(Dispatchers.IO) {
      runCatching {
        val versionName =
          runCatching {
              app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "1.0"
            }
            .getOrDefault("1.0")
        repository.exportBackupToUri(app, uri, versionName)
      }.onSuccess { result ->
        _snackbarMessages.tryEmit(
          "Backup saved: ${result.booksCount} ${if (result.booksCount == 1) "book" else "books"}, " +
            "${result.chaptersCount} ${if (result.chaptersCount == 1) "chapter" else "chapters"}, " +
            "${result.sessionsCount} ${if (result.sessionsCount == 1) "session" else "sessions"}"
        )
      }.onFailure { err ->
        dialogsControls.update {
          it.copy(backupErrorMessage = err.message ?: "Failed to export backup file.")
        }
      }
    }
  }

  fun inspectBackupUri(uri: Uri) {
    app.applicationScope.launch(Dispatchers.IO) {
      val (existingBookIds, existingChapterIds) =
        runCatching { repository.getExistingBookAndChapterIds() }
          .getOrElse { emptySet<Long>() to emptySet() }

      val validation =
        runCatching {
          BackupManager.readAndValidateUri(
            context = app,
            uri = uri,
            existingBookIds = existingBookIds,
            existingChapterIds = existingChapterIds,
          )
        }.getOrElse { err ->
          BackupValidationResult.Invalid(err.message ?: "Unable to read selected backup file.")
        }

      when (validation) {
        is BackupValidationResult.Valid -> {
          dialogsControls.update { it.copy(pendingBackupImport = validation.backup) }
        }
        is BackupValidationResult.Invalid -> {
          dialogsControls.update { it.copy(backupErrorMessage = validation.message) }
        }
      }
    }
  }

  fun dismissImportConfirm() {
    dialogsControls.update { it.copy(pendingBackupImport = null) }
  }

  fun dismissImportResult() {
    dialogsControls.update { it.copy(importResult = null) }
  }

  fun dismissBackupError() {
    dialogsControls.update { it.copy(backupErrorMessage = null) }
  }

  fun confirmImport(mode: BackupImportMode) {
    val backup = dialogsControls.value.pendingBackupImport ?: return
    dialogsControls.update { it.copy(pendingBackupImport = null) }
    app.applicationScope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          val result = repository.importBackupWithResult(backup, mode)
          app.timerRepository.resetAfterBackupImport(wasReplaceMode = (mode == BackupImportMode.REPLACE))
          val restoredPrefs = result.preferences
          if (restoredPrefs.reminderEnabled) {
            StudyReminderScheduler.scheduleDailyReminder(
              context = app,
              hour = restoredPrefs.reminderHour,
              minute = restoredPrefs.reminderMinute,
            )
          } else {
            StudyReminderScheduler.cancelReminder(app)
          }
          WidgetUpdater.updateNow(app, force = true)
          result
        }
      }.onSuccess { result ->
        dialogsControls.update { it.copy(importResult = result) }
      }.onFailure { err ->
        dialogsControls.update {
          it.copy(backupErrorMessage = err.message ?: "Failed to import backup.")
        }
      }
    }
  }

  fun checkWidgetPromptOnAppStart(
    isTimerActive: Boolean,
    hasRecoveryPrompt: Boolean,
    isStartupPermissionPending: Boolean,
    isCurrentlyOnHome: Boolean,
    clockNowMs: Long = System.currentTimeMillis(),
  ) {
    if (launchWidgetPromptCheckStarted) return
    launchWidgetPromptCheckStarted = true
    viewModelScope.launch {
      val counts = withContext(Dispatchers.IO) { WidgetPinManager.getAllWidgetCounts(app) }
      _widgetCounts.value = counts
      val anyPlaced = counts.values.any { it > 0 }
      if (anyPlaced) {
        withContext(Dispatchers.IO) { app.preferencesRepository.markWidgetEverAdded() }
      }
      val prefs = withContext(Dispatchers.IO) { app.preferencesRepository.getCurrentPreferences() }
      val dialogs = dialogsControls.value
      val anyDialogOpen =
        dialogs.addChapterSheet.isOpen ||
          dialogs.bookFormDialog.isOpen ||
          dialogs.deleteConfirm != DeleteConfirmState.None ||
          dialogs.isDailyGoalDialogOpen ||
          dialogs.isTimePickerOpen ||
          dialogs.pendingBackupImport != null ||
          dialogs.importResult != null ||
          dialogs.backupErrorMessage != null
      val isBlocked =
        isTimerActive ||
          hasRecoveryPrompt ||
          anyDialogOpen ||
          isStartupPermissionPending ||
          !isCurrentlyOnHome
      if (
        WidgetPromptScheduler.shouldShowWidgetPrompt(
          prefs = prefs,
          anyWidgetPlaced = anyPlaced,
          nowMs = clockNowMs,
          isBlocked = isBlocked,
        )
      ) {
        promptVisibilityRecordedForOpen = false
        _widgetPromptSheet.value = WidgetPromptSheetState.Visible(isFallbackManualSteps = false)
      }
    }
  }

  fun onWidgetPromptSheetVisible(clockNowMs: Long = System.currentTimeMillis()) {
    if (!promptVisibilityRecordedForOpen) {
      promptVisibilityRecordedForOpen = true
      app.applicationScope.launch(Dispatchers.IO) {
        app.preferencesRepository.recordWidgetPromptShown(clockNowMs)
      }
    }
  }

  fun dismissWidgetPrompt() {
    _widgetPromptSheet.value = WidgetPromptSheetState.Hidden
    _isPinRequestActive.value = false
  }

  fun requestPinOverviewFromPrompt() {
    if (_isPinRequestActive.value) return
    _isPinRequestActive.value = true
    when (WidgetPinManager.requestPinWidget(app, HomeWidgetType.OVERVIEW)) {
      WidgetPinRequestResult.UNSUPPORTED_OR_FAILED -> {
        _isPinRequestActive.value = false
        _widgetPromptSheet.value = WidgetPromptSheetState.Visible(isFallbackManualSteps = true)
      }
      WidgetPinRequestResult.REQUEST_SENT -> Unit
    }
  }

  fun refreshWidgetCounts() {
    _isPinRequestActive.value = false
    viewModelScope.launch {
      val counts = withContext(Dispatchers.IO) { WidgetPinManager.getAllWidgetCounts(app) }
      _widgetCounts.value = counts
      if (counts.values.any { it > 0 }) {
        withContext(Dispatchers.IO) { app.preferencesRepository.markWidgetEverAdded() }
      }
    }
  }

  fun requestPinWidgetFromSettings(type: HomeWidgetType) {
    if (_isPinRequestActive.value) return
    _isPinRequestActive.value = true
    when (WidgetPinManager.requestPinWidget(app, type)) {
      WidgetPinRequestResult.UNSUPPORTED_OR_FAILED -> {
        _isPinRequestActive.value = false
        _showMoreWidgetFallbackSheet.value = true
      }
      WidgetPinRequestResult.REQUEST_SENT -> Unit
    }
  }

  fun dismissMoreWidgetFallbackSheet() {
    _showMoreWidgetFallbackSheet.value = false
  }
}
