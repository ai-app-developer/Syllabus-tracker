package com.example.ui.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SyllabusApplication
import com.example.data.local.dao.ReadingSessionWithMeta
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.SessionEventEntity
import com.example.data.repository.ActiveSessionMetadata
import com.example.data.repository.RecoveryPromptData
import com.example.data.repository.TimerRepository
import com.example.timer.AnalyticsPeriod
import com.example.timer.ComputedTimerMetrics
import com.example.timer.TimerAnalyticsUiState
import com.example.timer.TimerMath
import com.example.timer.TimerMode
import com.example.timer.TimerStatus
import com.example.ui.model.StableList
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HistoryDateFilter(val label: String) {
  ALL("All Time"),
  TODAY("Today"),
  LAST_7_DAYS("Last 7 Days"),
  THIS_MONTH("This Month"),
}

enum class HistorySortOrder(val label: String) {
  NEWEST("Newest First"),
  OLDEST("Oldest First"),
  LONGEST("Longest Duration"),
}

@Immutable
data class TimerSetupState(
  val selectedBookId: Long? = null,
  val selectedChapterId: Long? = null,
  val mode: String = TimerMode.STOPWATCH,
  val pomodoroFocusMinutes: Int = 25,
  val pomodoroBreakMinutes: Int = 5,
  val goalMinutes: Int? = 90,
  val notes: String = "",
)

@Immutable
data class LiveStudyingUiState(
  val activeMeta: ActiveSessionMetadata? = null,
  val metrics: ComputedTimerMetrics =
    ComputedTimerMetrics(
      activeFocusMs = 0L,
      pauseMs = 0L,
      breakMs = 0L,
      currentPhaseElapsedMs = 0L,
      currentPhaseRemainingMs = 0L,
      focusPercent = 100,
      progressFraction = 0f,
    ),
  val events: StableList<SessionEventEntity> = StableList(),
) {
  val isRunningOrPaused: Boolean
    get() = activeMeta != null

  val isPaused: Boolean
    get() = activeMeta?.state?.status == TimerStatus.PAUSED

  val isBreak: Boolean
    get() = activeMeta?.state?.status == TimerStatus.BREAK

  val isStopSummaryVisible: Boolean
    get() = activeMeta?.state?.status == TimerStatus.STOP_SUMMARY

  val primaryClockDisplay: String
    get() {
      val state = activeMeta?.state ?: return "00:00:00"
      return if (state.mode == TimerMode.POMODORO) {
        TimerMath.formatHms(metrics.currentPhaseRemainingMs)
      } else {
        TimerMath.formatHms(metrics.activeFocusMs)
      }
    }
}

@Immutable
data class HistoryFilterState(
  val searchQuery: String = "",
  val isSearchActive: Boolean = false,
  val selectedBookId: Long? = null,
  val selectedChapterId: Long? = null,
  val dateFilter: HistoryDateFilter = HistoryDateFilter.ALL,
  val sortOrder: HistorySortOrder = HistorySortOrder.NEWEST,
)

@Immutable
data class TimerSetupUiState(
  val books: StableList<BookEntity> = StableList(),
  val chaptersForSelectedBook: StableList<ChapterEntity> = StableList(),
  val setup: TimerSetupState = TimerSetupState(),
)

@Immutable
data class ReadingHistoryUiState(
  val books: StableList<BookEntity> = StableList(),
  val chaptersForFilter: StableList<ChapterEntity> = StableList(),
  val filteredSessions: StableList<ReadingSessionWithMeta> = StableList(),
  val hasAnySessions: Boolean = false,
  val historyFilter: HistoryFilterState = HistoryFilterState(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModel(application: Application) : AndroidViewModel(application) {
  private val app = application as SyllabusApplication
  private val timerRepo: TimerRepository = app.timerRepository
  private val syllabusRepo = app.syllabusRepository

  private val setupState = MutableStateFlow(TimerSetupState())
  private val historyFilterState = MutableStateFlow(HistoryFilterState())
  private val analyticsPeriodState = MutableStateFlow(AnalyticsPeriod.THIS_WEEK)

  private val _events = MutableSharedFlow<TimerUiEvent>(extraBufferCapacity = 8)
  val events: SharedFlow<TimerUiEvent> = _events.asSharedFlow()

  val recoveryPrompt: StateFlow<RecoveryPromptData?> = timerRepo.recoveryPrompt

  init {
    viewModelScope.launch {
      syllabusRepo
        .observePreferences()
        .map { it.pomodoroFocusMinutes to it.pomodoroBreakMinutes }
        .distinctUntilChanged()
        .collect { (focus, brk) ->
          setupState.update { current ->
            current.copy(
              pomodoroFocusMinutes = focus,
              pomodoroBreakMinutes = brk,
            )
          }
        }
    }
  }

  val isTimerActive: StateFlow<Boolean> =
    timerRepo.activeTimerState
      .map { it != null }
      .distinctUntilChanged()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false,
      )

  val todayStudyMs: StateFlow<Long> =
    timerRepo.todayFocusMs
      .distinctUntilChanged()
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L,
      )

  private val tickerFlow: Flow<Long> =
    timerRepo.activeTimerState.flatMapLatest { state ->
      if (state == null || state.status == TimerStatus.PAUSED || state.status == TimerStatus.STOP_SUMMARY) {
        flowOf(SystemClock.elapsedRealtime())
      } else {
        flow {
          while (true) {
            emit(SystemClock.elapsedRealtime())
            delay(500L)
          }
        }
      }
    }

  private val activeEventsFlow: Flow<List<SessionEventEntity>> =
    timerRepo.activeTimerState
      .map { it?.sessionId }
      .distinctUntilChanged()
      .flatMapLatest { sessionId ->
        if (sessionId == null) flowOf(emptyList()) else timerRepo.observeSessionEvents(sessionId)
      }

  val liveStudyingState: StateFlow<LiveStudyingUiState> =
    combine(
      timerRepo.activeSessionWithMeta,
      tickerFlow,
      activeEventsFlow,
    ) { meta, nowElapsed, sessionEvents ->
      if (meta == null) {
        LiveStudyingUiState()
      } else {
        val nowWall = System.currentTimeMillis()
        val metrics = TimerMath.computeMetrics(meta.state, nowElapsed, nowWall)
        LiveStudyingUiState(
          activeMeta = meta,
          metrics = metrics,
          events = StableList(sessionEvents),
        )
      }
    }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000L, replayExpirationMillis = Long.MAX_VALUE),
        initialValue = LiveStudyingUiState(),
      )

  val analyticsUiState: StateFlow<TimerAnalyticsUiState> =
    timerRepo.observeAnalytics(analyticsPeriodState)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TimerAnalyticsUiState(),
      )

  private val resolvedBookIdFlow: Flow<Pair<List<BookEntity>, Long?>> =
    combine(
      syllabusRepo.observeBookSummaries(),
      syllabusRepo.observePreferences(),
      setupState.map { it.selectedBookId }.distinctUntilChanged(),
    ) { summaries, prefs, explicitBookId ->
      val rawBooks = summaries.map { it.book }
      val preferred =
        explicitBookId?.takeIf { id -> rawBooks.any { it.id == id } }
          ?: summaries.firstOrNull { it.book.id == prefs.lastOpenedBookId && it.nextIncompleteChapter != null }?.book?.id
          ?: summaries.firstOrNull { it.nextIncompleteChapter != null }?.book?.id
          ?: summaries.firstOrNull { it.book.id == prefs.lastOpenedBookId }?.book?.id
          ?: rawBooks.firstOrNull()?.id
      rawBooks to preferred
    }.distinctUntilChanged()

  private val selectedBookChaptersFlow: Flow<List<ChapterEntity>> =
    resolvedBookIdFlow
      .map { it.second }
      .distinctUntilChanged()
      .flatMapLatest { bookId ->
        if (bookId == null) flowOf(emptyList()) else syllabusRepo.observeChaptersForBook(bookId)
      }

  val setupUiState: StateFlow<TimerSetupUiState> =
    combine(resolvedBookIdFlow, selectedBookChaptersFlow, setupState) {
      (rawBooks, preferredBookId),
      chaptersForBook,
      setup ->
      val preferredChapterId =
        setup.selectedChapterId?.takeIf { cid -> chaptersForBook.any { it.id == cid } }
          ?: chaptersForBook.firstOrNull { !it.isCompleted }?.id
          ?: chaptersForBook.firstOrNull()?.id
      TimerSetupUiState(
        books = StableList(rawBooks),
        chaptersForSelectedBook = StableList(chaptersForBook),
        setup =
          setup.copy(
            selectedBookId = preferredBookId,
            selectedChapterId = preferredChapterId,
          ),
      )
    }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TimerSetupUiState(),
      )

  private val historyFilterChaptersFlow: Flow<List<ChapterEntity>> =
    historyFilterState
      .map { it.selectedBookId }
      .distinctUntilChanged()
      .flatMapLatest { bookId ->
        if (bookId == null) flowOf(emptyList()) else syllabusRepo.observeChaptersForBook(bookId)
      }

  val historyUiState: StateFlow<ReadingHistoryUiState> =
    combine(
      syllabusRepo.observeBooks(),
      historyFilterChaptersFlow,
      timerRepo.allSessionsWithMeta,
      historyFilterState,
    ) { books, chaptersForFilter, sessions, filter ->
      val zone = ZoneId.systemDefault()
      val today = LocalDate.now(zone)
      val q = filter.searchQuery.trim()
      val filtered =
        sessions.filter { item ->
          val matchesBook = filter.selectedBookId == null || item.session.bookId == filter.selectedBookId
          val matchesChapter =
            filter.selectedChapterId == null || item.session.chapterId == filter.selectedChapterId
          val matchesQuery =
            q.isEmpty() ||
              item.bookName.contains(q, ignoreCase = true) ||
              item.chapterName.contains(q, ignoreCase = true) ||
              item.session.notes.contains(q, ignoreCase = true)
          val matchesDate =
            when (filter.dateFilter) {
              HistoryDateFilter.ALL -> true
              HistoryDateFilter.TODAY -> item.session.sessionDate == today.toString()
              HistoryDateFilter.LAST_7_DAYS -> {
                val itemDate =
                  runCatching { LocalDate.parse(item.session.sessionDate) }
                    .getOrElse {
                      Instant.ofEpochMilli(item.session.startWallTimeMs).atZone(zone).toLocalDate()
                    }
                !itemDate.isBefore(today.minusDays(6))
              }
              HistoryDateFilter.THIS_MONTH -> {
                val itemDate =
                  runCatching { LocalDate.parse(item.session.sessionDate) }
                    .getOrElse {
                      Instant.ofEpochMilli(item.session.startWallTimeMs).atZone(zone).toLocalDate()
                    }
                itemDate.year == today.year && itemDate.month == today.month
              }
            }
          matchesBook && matchesChapter && matchesQuery && matchesDate
        }
      val sorted =
        when (filter.sortOrder) {
          HistorySortOrder.NEWEST -> filtered.sortedByDescending { it.session.endWallTimeMs }
          HistorySortOrder.OLDEST -> filtered.sortedBy { it.session.endWallTimeMs }
          HistorySortOrder.LONGEST -> filtered.sortedByDescending { it.session.activeFocusMs }
        }
      ReadingHistoryUiState(
        books = StableList(books),
        chaptersForFilter = StableList(chaptersForFilter),
        filteredSessions = StableList(sorted),
        hasAnySessions = sessions.isNotEmpty(),
        historyFilter = filter,
      )
    }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReadingHistoryUiState(),
      )

  fun setAnalyticsPeriod(period: AnalyticsPeriod) {
    analyticsPeriodState.value = period
  }

  fun observeSessionEvents(sessionId: String): Flow<List<SessionEventEntity>> =
    timerRepo.observeSessionEvents(sessionId)

  // ---- Setup Screen Actions ----

  fun selectBookForTimer(bookId: Long) {
    setupState.update { it.copy(selectedBookId = bookId, selectedChapterId = null) }
  }

  fun selectChapterForTimer(chapterId: Long) {
    setupState.update { it.copy(selectedChapterId = chapterId) }
  }

  fun selectTimerMode(mode: String) {
    setupState.update { it.copy(mode = mode) }
  }

  fun setPomodoroPreset(focusMinutes: Int, breakMinutes: Int) {
    val clampedFocus = focusMinutes.coerceIn(1, 180)
    val clampedBreak = breakMinutes.coerceIn(1, 60)
    setupState.update {
      it.copy(
        mode = TimerMode.POMODORO,
        pomodoroFocusMinutes = clampedFocus,
        pomodoroBreakMinutes = clampedBreak,
      )
    }
    app.applicationScope.launch(Dispatchers.IO) {
      syllabusRepo.setPomodoroDurations(clampedFocus, clampedBreak)
    }
  }

  fun setGoalMinutes(goalMinutes: Int?) {
    setupState.update { it.copy(goalMinutes = goalMinutes) }
  }

  fun setSetupNotes(notes: String) {
    setupState.update { it.copy(notes = notes) }
  }

  fun startTimerSession(onStarted: () -> Unit) {
    val currentSetup = setupUiState.value.setup
    val bookId = currentSetup.selectedBookId
    val chapterId = currentSetup.selectedChapterId
    if (bookId == null || chapterId == null) {
      _events.tryEmit(TimerUiEvent.ShowMessage("Please select both a book and a chapter."))
      return
    }
    if (isTimerActive.value) {
      onStarted()
      return
    }
    app.applicationScope.launch {
      val started =
        withContext(Dispatchers.IO) {
          timerRepo.startSession(
            bookId = bookId,
            chapterId = chapterId,
            mode = currentSetup.mode,
            focusMinutes = currentSetup.pomodoroFocusMinutes,
            breakMinutes = currentSetup.pomodoroBreakMinutes,
            goalMinutes = currentSetup.goalMinutes,
            notes = currentSetup.notes,
          )
        }
      withContext(Dispatchers.Main) {
        if (started) {
          setupState.update { it.copy(notes = "") }
          onStarted()
        } else {
          _events.tryEmit(TimerUiEvent.ShowMessage("A study session is already active."))
        }
      }
    }
  }

  // ---- Live Studying Actions ----

  fun togglePauseResume() {
    app.applicationScope.launch(Dispatchers.IO) {
      timerRepo.togglePauseResume()
    }
  }

  fun recordLap() {
    app.applicationScope.launch(Dispatchers.IO) {
      val lapNum = timerRepo.recordLap()
      if (lapNum != null) {
        _events.tryEmit(TimerUiEvent.ShowMessage("Lap $lapNum recorded"))
      }
    }
  }

  fun skipBreak() {
    app.applicationScope.launch(Dispatchers.IO) {
      timerRepo.skipBreak()
    }
  }

  fun updateActiveNotes(notes: String) {
    app.applicationScope.launch(Dispatchers.IO) {
      timerRepo.updateActiveNotes(notes)
    }
  }

  fun requestStopSummary() {
    app.applicationScope.launch(Dispatchers.IO) {
      timerRepo.requestStopSummary()
    }
  }

  fun resumeFromStopSummary() {
    app.applicationScope.launch(Dispatchers.IO) {
      timerRepo.resumeFromStopSummary()
    }
  }

  fun saveSession(finalNotes: String, onSaved: () -> Unit) {
    app.applicationScope.launch {
      val saved =
        withContext(Dispatchers.IO) {
          timerRepo.saveCurrentSession(finalNotes)
        }
      withContext(Dispatchers.Main) {
        if (saved) {
          _events.tryEmit(TimerUiEvent.ShowMessage("Study session saved"))
        }
        onSaved()
      }
    }
  }

  fun discardSession(onDiscarded: () -> Unit) {
    app.applicationScope.launch {
      withContext(Dispatchers.IO) {
        timerRepo.discardCurrentSession()
      }
      withContext(Dispatchers.Main) {
        _events.tryEmit(TimerUiEvent.ShowMessage("Session discarded"))
        onDiscarded()
      }
    }
  }

  fun touchLastKnownWallTime() {
    app.applicationScope.launch(Dispatchers.IO) {
      timerRepo.touchLastKnownWallTime()
    }
  }

  // ---- Crash / Kill / Reboot Recovery Actions ----

  fun resumeRecoveredSession(onOpenStudying: () -> Unit) {
    app.applicationScope.launch {
      withContext(Dispatchers.IO) { timerRepo.resumeRecoveredSession() }
      withContext(Dispatchers.Main) { onOpenStudying() }
    }
  }

  fun saveRecoveredSessionUpToLastKnownTime() {
    app.applicationScope.launch {
      val saved = withContext(Dispatchers.IO) { timerRepo.saveRecoveredSessionUpToLastKnownTime() }
      if (saved) {
        _events.tryEmit(TimerUiEvent.ShowMessage("Recovered session saved"))
      }
    }
  }

  fun discardRecoveredSession() {
    app.applicationScope.launch(Dispatchers.IO) {
      timerRepo.discardRecoveredSession()
      _events.tryEmit(TimerUiEvent.ShowMessage("Unfinished session discarded"))
    }
  }

  // ---- Reading History Actions ----

  fun toggleHistorySearch() {
    historyFilterState.update {
      val next = !it.isSearchActive
      it.copy(isSearchActive = next, searchQuery = if (next) it.searchQuery else "")
    }
  }

  fun setHistorySearchQuery(query: String) {
    historyFilterState.update { it.copy(searchQuery = query) }
  }

  fun setHistoryBookFilter(bookId: Long?) {
    historyFilterState.update { it.copy(selectedBookId = bookId, selectedChapterId = null) }
  }

  fun setHistoryChapterFilter(chapterId: Long?) {
    historyFilterState.update { it.copy(selectedChapterId = chapterId) }
  }

  fun setHistoryDateFilter(dateFilter: HistoryDateFilter) {
    historyFilterState.update { it.copy(dateFilter = dateFilter) }
  }

  fun setHistorySortOrder(sortOrder: HistorySortOrder) {
    historyFilterState.update { it.copy(sortOrder = sortOrder) }
  }

  fun updateHistorySessionNotes(sessionRowId: Long, notes: String) {
    app.applicationScope.launch(Dispatchers.IO) {
      timerRepo.updateSessionNotes(sessionRowId, notes)
      _events.tryEmit(TimerUiEvent.ShowMessage("Session notes updated"))
    }
  }

  fun deleteHistorySession(sessionRowId: Long) {
    app.applicationScope.launch(Dispatchers.IO) {
      timerRepo.deleteReadingSession(sessionRowId)
      _events.tryEmit(TimerUiEvent.ShowMessage("Session deleted"))
    }
  }
}

sealed interface TimerUiEvent {
  data class ShowMessage(val message: String) : TimerUiEvent
}
