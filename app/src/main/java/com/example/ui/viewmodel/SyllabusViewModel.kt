package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SyllabusApplication
import com.example.data.backup.BackupImportMode
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupValidationResult
import com.example.data.backup.SyllabusBackup
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.preferences.UserPreferences
import com.example.data.repository.BookProgressSummary
import com.example.data.repository.DailyStudyCount
import com.example.data.repository.StudyTrendPeriod
import com.example.data.repository.SyllabusRepository
import com.example.data.repository.SyllabusSnapshot
import com.example.notifications.StudyReminderScheduler
import com.example.widgets.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
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
  val pendingBackupImport: SyllabusBackup? = null,
  val backupErrorMessage: String? = null,
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
  val pendingBackupImport: SyllabusBackup? = null,
  val backupErrorMessage: String? = null,
)

class SyllabusViewModel(application: Application) : AndroidViewModel(application) {
  private val app = application as SyllabusApplication
  private val repository: SyllabusRepository = app.syllabusRepository

  private val controls = MutableStateFlow(ScreenControlsState())
  private val optimisticChapterToggles =
    MutableStateFlow<Map<Long, Pair<Boolean, Long?>>>(emptyMap())

  private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

  val uiState: StateFlow<SyllabusUiState> =
    combine(repository.snapshot, controls, optimisticChapterToggles) {
        baseSnapshot,
        ctrl,
        optimisticMap ->
        val snapshot = applyOptimisticToggles(baseSnapshot, optimisticMap)
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
          pendingBackupImport = ctrl.pendingBackupImport,
          backupErrorMessage = ctrl.backupErrorMessage,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyllabusUiState(),
      )

  private fun applyOptimisticToggles(
    base: SyllabusSnapshot,
    overrides: Map<Long, Pair<Boolean, Long?>>,
  ): SyllabusSnapshot {
    if (overrides.isEmpty()) return base
    val updatedChapters =
      base.allChapters.map { ch ->
        val override = overrides[ch.id]
        if (override != null) {
          ch.copy(isCompleted = override.first, completedAt = override.second)
        } else {
          ch
        }
      }
    val chaptersByBook = updatedChapters.groupBy { it.bookId }
    val updatedBooks =
      base.books.map { summary ->
        val bookChapters =
          chaptersByBook[summary.book.id].orEmpty().sortedWith(compareBy({ it.number }, { it.id }))
        summary.copy(
          totalChapters = bookChapters.size,
          completedChapters = bookChapters.count { it.isCompleted },
          nextIncompleteChapter = bookChapters.firstOrNull { !it.isCompleted },
        )
      }
    val totalChapters = updatedChapters.size
    val completedChapters = updatedChapters.count { it.isCompleted }
    val remainingChapters = (totalChapters - completedChapters).coerceAtLeast(0)
    val overallPercentage =
      if (totalChapters == 0) 0 else ((completedChapters * 100f) / totalChapters).roundToInt()

    return base.copy(
      books = updatedBooks,
      allChapters = updatedChapters,
      totalChapters = totalChapters,
      completedChapters = completedChapters,
      remainingChapters = remainingChapters,
      overallPercentage = overallPercentage,
      continueStudyingBook =
        updatedBooks.firstOrNull { it.book.id == base.continueStudyingBook?.book?.id }
          ?: base.continueStudyingBook,
    )
  }

  fun observeBookById(bookId: Long): Flow<BookEntity?> = repository.observeBookById(bookId)

  fun observeChaptersForBook(bookId: Long): Flow<List<ChapterEntity>> =
    combine(repository.observeChaptersForBook(bookId), optimisticChapterToggles) {
      chapters,
      overrides ->
      if (overrides.isEmpty()) {
        chapters
      } else {
        chapters.map { ch ->
          val override = overrides[ch.id]
          if (override != null) {
            ch.copy(isCompleted = override.first, completedAt = override.second)
          } else {
            ch
          }
        }
      }
    }

  fun markBookOpened(bookId: Long) {
    app.applicationScope.launch {
      withContext(Dispatchers.IO) { repository.markBookOpened(bookId) }
    }
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
    if (current is DeleteConfirmState.DeleteBook) {
      onBookDeleted?.invoke()
    }
    app.applicationScope.launch {
      withContext(Dispatchers.IO) {
        when (current) {
          is DeleteConfirmState.DeleteBook -> repository.deleteBook(current.book.id)
          is DeleteConfirmState.DeleteChapter -> repository.deleteChapter(current.chapter.id)
          DeleteConfirmState.None -> Unit
        }
      }
    }
  }

  fun saveBook(name: String, editingBook: BookEntity? = null) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    controls.update { it.copy(bookFormDialog = BookFormDialogState()) }
    app.applicationScope.launch {
      withContext(Dispatchers.IO) {
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
    app.applicationScope.launch {
      withContext(Dispatchers.IO) {
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
  }

  fun toggleChapterCompleted(chapter: ChapterEntity) {
    val nextState = !chapter.isCompleted
    val nextCompletedAt = if (nextState) System.currentTimeMillis() else null
    optimisticChapterToggles.update { it + (chapter.id to (nextState to nextCompletedAt)) }
    app.applicationScope.launch {
      try {
        withContext(Dispatchers.IO) {
          repository.toggleChapterCompleted(
            chapter = chapter,
            targetCompleted = nextState,
            targetCompletedAt = nextCompletedAt,
          )
        }
      } finally {
        optimisticChapterToggles.update { it - chapter.id }
      }
    }
  }

  fun openDailyGoalDialog() {
    controls.update { it.copy(isDailyGoalDialogOpen = true) }
  }

  fun closeDailyGoalDialog() {
    controls.update { it.copy(isDailyGoalDialogOpen = false) }
  }

  fun saveDailyGoal(goal: Int) {
    controls.update { it.copy(isDailyGoalDialogOpen = false) }
    app.applicationScope.launch {
      withContext(Dispatchers.IO) { repository.setDailyGoal(goal) }
    }
  }

  fun openTimePicker() {
    controls.update { it.copy(isTimePickerOpen = true) }
  }

  fun closeTimePicker() {
    controls.update { it.copy(isTimePickerOpen = false) }
  }

  fun setReminderEnabled(enabled: Boolean) {
    app.applicationScope.launch {
      withContext(Dispatchers.IO) { repository.setReminderEnabled(enabled) }
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
    app.applicationScope.launch {
      withContext(Dispatchers.IO) { repository.setReminderTime(hour, minute) }
      if (uiState.value.snapshot.preferences.reminderEnabled) {
        StudyReminderScheduler.scheduleDailyReminder(app, hour, minute)
      }
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
        val json = repository.exportBackupJson(versionName)
        BackupManager.writeTextToUri(app, uri, json)
      }.onSuccess {
        _snackbarMessages.tryEmit("Backup saved")
      }.onFailure { err ->
        controls.update {
          it.copy(backupErrorMessage = err.message ?: "Failed to export backup file.")
        }
      }
    }
  }

  fun inspectBackupUri(uri: Uri) {
    app.applicationScope.launch(Dispatchers.IO) {
      val readResult = runCatching { BackupManager.readTextFromUri(app, uri) }
      val raw =
        readResult.getOrElse { err ->
          controls.update {
            it.copy(backupErrorMessage = err.message ?: "Unable to read selected backup file.")
          }
          return@launch
        }

      when (val validation = BackupManager.parseAndValidate(raw)) {
        is BackupValidationResult.Valid -> {
          controls.update { it.copy(pendingBackupImport = validation.backup) }
        }
        is BackupValidationResult.Invalid -> {
          controls.update { it.copy(backupErrorMessage = validation.message) }
        }
      }
    }
  }

  fun dismissImportConfirm() {
    controls.update { it.copy(pendingBackupImport = null) }
  }

  fun dismissBackupError() {
    controls.update { it.copy(backupErrorMessage = null) }
  }

  fun confirmImport(mode: BackupImportMode) {
    val backup = controls.value.pendingBackupImport ?: return
    controls.update { it.copy(pendingBackupImport = null) }
    app.applicationScope.launch {
      runCatching {
        val restoredPrefs =
          withContext(Dispatchers.IO) { repository.importBackup(backup, mode) }
        if (restoredPrefs.reminderEnabled) {
          StudyReminderScheduler.scheduleDailyReminder(
            context = app,
            hour = restoredPrefs.reminderHour,
            minute = restoredPrefs.reminderMinute,
          )
        } else {
          StudyReminderScheduler.cancelReminder(app)
        }
        WidgetUpdater.refreshAll(app)
      }.onSuccess {
        _snackbarMessages.tryEmit("Data imported")
      }.onFailure { err ->
        controls.update {
          it.copy(backupErrorMessage = err.message ?: "Failed to import backup.")
        }
      }
    }
  }
}
