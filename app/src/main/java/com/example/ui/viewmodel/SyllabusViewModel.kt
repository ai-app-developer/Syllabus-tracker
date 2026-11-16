package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SyllabusApplication
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.preferences.UserPreferences
import com.example.data.repository.BookProgressSummary
import com.example.data.repository.DailyStudyCount
import com.example.data.repository.StudyTrendPeriod
import com.example.data.repository.SyllabusRepository
import com.example.data.repository.SyllabusSnapshot
import com.example.notifications.StudyReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
}

data class AddChapterSheetState(
  val isOpen: Boolean = false,
  val preselectedBookId: Long? = null,
  val editingChapter: ChapterEntity? = null,
)

data class BookFormDialogState(
  val isOpen: Boolean = false,
  val editingBook: BookEntity? = null,
)

sealed interface DeleteConfirmState {
  data object None : DeleteConfirmState

  data class DeleteBook(val book: BookEntity) : DeleteConfirmState

  data class DeleteChapter(val chapter: ChapterEntity) : DeleteConfirmState
}

data class SyllabusUiState(
  val isLoading: Boolean = true,
  val snapshot: SyllabusSnapshot =
    SyllabusSnapshot(
      books = emptyList(),
      allChapters = emptyList(),
      totalBooks = 0,
      totalChapters = 0,
      completedChapters = 0,
      remainingChapters = 0,
      overallPercentage = 0,
      completedToday = 0,
      continueStudyingBook = null,
      preferences = UserPreferences(),
    ),
  val searchQuery: String = "",
  val isSearchActive: Boolean = false,
  val bookFilter: BookFilter = BookFilter.ALL,
  val bookSortOrder: BookSortOrder = BookSortOrder.RECENT,
  val progressTab: ProgressTab = ProgressTab.OVERALL,
  val trendPeriod: StudyTrendPeriod = StudyTrendPeriod.THIS_WEEK,
  val weeklyTrend: List<DailyStudyCount> = emptyList(),
  val cumulativeSeries: List<Float> = List(7) { 0f },
  val addChapterSheet: AddChapterSheetState = AddChapterSheetState(),
  val bookFormDialog: BookFormDialogState = BookFormDialogState(),
  val deleteConfirm: DeleteConfirmState = DeleteConfirmState.None,
  val isDailyGoalDialogOpen: Boolean = false,
  val isTimePickerOpen: Boolean = false,
) {
  val filteredBooks: List<BookProgressSummary>
    get() {
      val query = searchQuery.trim()
      val filtered =
        snapshot.books.filter { summary ->
          val matchesQuery =
            query.isEmpty() || summary.book.name.contains(query, ignoreCase = true)
          val matchesFilter =
            when (bookFilter) {
              BookFilter.ALL -> true
              BookFilter.IN_PROGRESS -> !summary.isCompleted
              BookFilter.COMPLETED -> summary.isCompleted
            }
          matchesQuery && matchesFilter
        }
      return when (bookSortOrder) {
        BookSortOrder.RECENT -> filtered
        BookSortOrder.NAME_ASC -> filtered.sortedBy { it.book.name.lowercase() }
      }
    }
}

private data class ScreenControlsState(
  val searchQuery: String = "",
  val isSearchActive: Boolean = false,
  val bookFilter: BookFilter = BookFilter.ALL,
  val bookSortOrder: BookSortOrder = BookSortOrder.RECENT,
  val progressTab: ProgressTab = ProgressTab.OVERALL,
  val trendPeriod: StudyTrendPeriod = StudyTrendPeriod.THIS_WEEK,
  val addChapterSheet: AddChapterSheetState = AddChapterSheetState(),
  val bookFormDialog: BookFormDialogState = BookFormDialogState(),
  val deleteConfirm: DeleteConfirmState = DeleteConfirmState.None,
  val isDailyGoalDialogOpen: Boolean = false,
  val isTimePickerOpen: Boolean = false,
)

class SyllabusViewModel(application: Application) : AndroidViewModel(application) {
  private val app = application as SyllabusApplication
  private val repository: SyllabusRepository = app.syllabusRepository

  private val controls = MutableStateFlow(ScreenControlsState())

  val uiState: StateFlow<SyllabusUiState> =
    combine(repository.snapshot, controls) { snapshot, ctrl ->
        val weeklyTrend =
          SyllabusRepository.computeWeeklyTrend(snapshot.allChapters, ctrl.trendPeriod)
        val cumulativeSeries =
          SyllabusRepository.computeCumulativeSeries(snapshot.allChapters, ctrl.trendPeriod)

        SyllabusUiState(
          isLoading = false,
          snapshot = snapshot,
          searchQuery = ctrl.searchQuery,
          isSearchActive = ctrl.isSearchActive,
          bookFilter = ctrl.bookFilter,
          bookSortOrder = ctrl.bookSortOrder,
          progressTab = ctrl.progressTab,
          trendPeriod = ctrl.trendPeriod,
          weeklyTrend = weeklyTrend,
          cumulativeSeries = cumulativeSeries,
          addChapterSheet = ctrl.addChapterSheet,
          bookFormDialog = ctrl.bookFormDialog,
          deleteConfirm = ctrl.deleteConfirm,
          isDailyGoalDialogOpen = ctrl.isDailyGoalDialogOpen,
          isTimePickerOpen = ctrl.isTimePickerOpen,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyllabusUiState(),
      )

  fun observeBookById(bookId: Long): Flow<BookEntity?> = repository.observeBookById(bookId)

  fun observeChaptersForBook(bookId: Long): Flow<List<ChapterEntity>> =
    repository.observeChaptersForBook(bookId)

  fun markBookOpened(bookId: Long) {
    viewModelScope.launch { repository.markBookOpened(bookId) }
  }

  fun toggleSearchActive() {
    controls.update {
      val next = !it.isSearchActive
      it.copy(isSearchActive = next, searchQuery = if (next) it.searchQuery else "")
    }
  }

  fun setSearchQuery(query: String) {
    controls.update { it.copy(searchQuery = query) }
  }

  fun setBookFilter(filter: BookFilter) {
    controls.update { it.copy(bookFilter = filter) }
  }

  fun setBookSortOrder(order: BookSortOrder) {
    controls.update { it.copy(bookSortOrder = order) }
  }

  fun setProgressTab(tab: ProgressTab) {
    controls.update { it.copy(progressTab = tab) }
  }

  fun setTrendPeriod(period: StudyTrendPeriod) {
    controls.update { it.copy(trendPeriod = period) }
  }

  fun openAddChapterSheet(preselectedBookId: Long? = null) {
    controls.update {
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
    controls.update {
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
    controls.update { it.copy(addChapterSheet = AddChapterSheetState()) }
  }

  fun openAddBookDialog() {
    controls.update { it.copy(bookFormDialog = BookFormDialogState(isOpen = true)) }
  }

  fun openEditBookDialog(book: BookEntity) {
    controls.update {
      it.copy(bookFormDialog = BookFormDialogState(isOpen = true, editingBook = book))
    }
  }

  fun closeBookFormDialog() {
    controls.update { it.copy(bookFormDialog = BookFormDialogState()) }
  }

  fun requestDeleteBook(book: BookEntity) {
    controls.update { it.copy(deleteConfirm = DeleteConfirmState.DeleteBook(book)) }
  }

  fun requestDeleteChapter(chapter: ChapterEntity) {
    controls.update { it.copy(deleteConfirm = DeleteConfirmState.DeleteChapter(chapter)) }
  }

  fun dismissDeleteConfirm() {
    controls.update { it.copy(deleteConfirm = DeleteConfirmState.None) }
  }

  fun confirmDelete(onBookDeleted: (() -> Unit)? = null) {
    val current = controls.value.deleteConfirm
    controls.update { it.copy(deleteConfirm = DeleteConfirmState.None) }
    viewModelScope.launch {
      when (current) {
        is DeleteConfirmState.DeleteBook -> {
          repository.deleteBook(current.book.id)
          onBookDeleted?.invoke()
        }
        is DeleteConfirmState.DeleteChapter -> repository.deleteChapter(current.chapter.id)
        DeleteConfirmState.None -> Unit
      }
    }
  }

  fun saveBook(name: String, editingBook: BookEntity? = null) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    controls.update { it.copy(bookFormDialog = BookFormDialogState()) }
    viewModelScope.launch {
      if (editingBook == null) {
        val newId = repository.addBook(trimmed)
        if (controls.value.addChapterSheet.isOpen) {
          controls.update {
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
    controls.update { it.copy(addChapterSheet = AddChapterSheetState()) }
    viewModelScope.launch {
      if (editingChapter == null) {
        repository.addChapter(bookId, trimmed, number, isCompleted)
      } else {
        repository.updateChapter(
          editingChapter.copy(bookId = bookId, name = trimmed, number = number),
          isCompleted = isCompleted,
        )
      }
    }
  }

  fun toggleChapterCompleted(chapter: ChapterEntity) {
    viewModelScope.launch { repository.toggleChapterCompleted(chapter) }
  }

  fun openDailyGoalDialog() {
    controls.update { it.copy(isDailyGoalDialogOpen = true) }
  }

  fun closeDailyGoalDialog() {
    controls.update { it.copy(isDailyGoalDialogOpen = false) }
  }

  fun saveDailyGoal(goal: Int) {
    controls.update { it.copy(isDailyGoalDialogOpen = false) }
    viewModelScope.launch { repository.setDailyGoal(goal) }
  }

  fun openTimePicker() {
    controls.update { it.copy(isTimePickerOpen = true) }
  }

  fun closeTimePicker() {
    controls.update { it.copy(isTimePickerOpen = false) }
  }

  fun setReminderEnabled(enabled: Boolean) {
    viewModelScope.launch {
      repository.setReminderEnabled(enabled)
      val prefs = uiState.value.snapshot.preferences
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
    controls.update { it.copy(isTimePickerOpen = false) }
    viewModelScope.launch {
      repository.setReminderTime(hour, minute)
      if (uiState.value.snapshot.preferences.reminderEnabled) {
        StudyReminderScheduler.scheduleDailyReminder(app, hour, minute)
      }
    }
  }
}
