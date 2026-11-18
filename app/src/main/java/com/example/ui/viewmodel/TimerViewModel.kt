package com.example.ui.viewmodel

import android.app.Application
import android.os.SystemClock
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
import com.example.timer.TimerAnalyticsCalculator
import com.example.timer.TimerAnalyticsUiState
import com.example.timer.TimerMath
import com.example.timer.TimerMode
import com.example.timer.TimerStatus
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

data class TimerSetupState(
  val selectedBookId: Long? = null,
  val selectedChapterId: Long? = null,
  val mode: String = TimerMode.STOPWATCH,
  val pomodoroFocusMinutes: Int = 25,
  val pomodoroBreakMinutes: Int = 5,
  val goalMinutes: Int? = 90,
  val notes: String = "",
)

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
  val events: List<SessionEventEntity> = emptyList(),
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

data class HistoryFilterState(
  val searchQuery: String = "",
  val isSearchActive: Boolean = false,
  val selectedBookId: Long? = null,
  val selectedChapterId: Long? = null,
  val dateFilter: HistoryDateFilter = HistoryDateFilter.ALL,
  val sortOrder: HistorySortOrder = HistorySortOrder.NEWEST,
)

data class TimerUiState(
  val books: List<BookEntity> = emptyList(),
  val chaptersForSelectedBook: List<ChapterEntity> = emptyList(),
  val allChapters: List<ChapterEntity> = emptyList(),
  val setup: TimerSetupState = TimerSetupState(),
  val liveStudying: LiveStudyingUiState = LiveStudyingUiState(),
  val recoveryPrompt: RecoveryPromptData? = null,
  val allSessions: List<ReadingSessionWithMeta> = emptyList(),
  val historyFilter: HistoryFilterState = HistoryFilterState(),
  val analytics: TimerAnalyticsUiState = TimerAnalyticsUiState(),
) {
  val historyChaptersForFilter: List<ChapterEntity>
    get() {
      val bookId = historyFilter.selectedBookId ?: return emptyList()
      return allChapters
        .filter { it.bookId == bookId }
        .sortedWith(compareBy({ it.number }, { it.id }))
    }

  val filteredHistorySessions: List<ReadingSessionWithMeta>
    get() {
      val zone = ZoneId.systemDefault()
      val today = LocalDate.now(zone)
      val q = historyFilter.searchQuery.trim()
      val filtered =
        allSessions.filter { item ->
          val matchesBook =
            historyFilter.selectedBookId == null || item.session.bookId == historyFilter.selectedBookId
          val matchesChapter =
            historyFilter.selectedChapterId == null ||
              item.session.chapterId == historyFilter.selectedChapterId
          val matchesQuery =
            q.isEmpty() ||
              item.bookName.contains(q, ignoreCase = true) ||
              item.chapterName.contains(q, ignoreCase = true) ||
              item.session.notes.contains(q, ignoreCase = true)
          val matchesDate =
            when (historyFilter.dateFilter) {
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
      return when (historyFilter.sortOrder) {
        HistorySortOrder.NEWEST -> filtered.sortedByDescending { it.session.endWallTimeMs }
        HistorySortOrder.OLDEST -> filtered.sortedBy { it.session.endWallTimeMs }
        HistorySortOrder.LONGEST -> filtered.sortedByDescending { it.session.activeFocusMs }
      }
    }
}

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
    timerRepo.activeTimerState.flatMapLatest { state ->
      if (state == null) flowOf(emptyList()) else timerRepo.observeSessionEvents(state.sessionId)
    }

  private val liveStudyingFlow: Flow<LiveStudyingUiState> =
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
        if (meta.state.mode == TimerMode.POMODORO && metrics.currentPhaseRemainingMs == 0L) {
          app.applicationScope.launch(Dispatchers.IO) {
            timerRepo.advancePomodoroPhaseIfDue()
          }
        }
        if (
          !meta.state.goalNotified &&
            meta.state.status == TimerStatus.RUNNING &&
            meta.state.goalMs != null &&
            metrics.activeFocusMs >= meta.state.goalMs
        ) {
          app.applicationScope.launch(Dispatchers.IO) {
            timerRepo.checkAndMarkGoalAlertIfReached()
          }
        }
        LiveStudyingUiState(
          activeMeta = meta,
          metrics = metrics,
          events = sessionEvents,
        )
      }
    }

  val uiState: StateFlow<TimerUiState> =
    combine(
      syllabusRepo.snapshot,
      setupState,
      liveStudyingFlow,
      timerRepo.recoveryPrompt,
      combine(timerRepo.allSessionsWithMeta, historyFilterState, analyticsPeriodState) {
        sessions,
        filter,
        period ->
        Triple(sessions, filter, period)
      },
    ) { snapshot, setup, live, recovery, (sessions, filter, period) ->
      val rawBooks = snapshot.books.map { it.book }
      val preferredBookId =
        setup.selectedBookId?.takeIf { id -> rawBooks.any { it.id == id } }
          ?: snapshot.continueStudyingBook?.book?.id
          ?: rawBooks.firstOrNull()?.id

      val chaptersForBook =
        if (preferredBookId == null) {
          emptyList()
        } else {
          snapshot.allChapters
            .filter { it.bookId == preferredBookId }
            .sortedWith(compareBy({ it.number }, { it.id }))
        }

      val preferredChapterId =
        setup.selectedChapterId?.takeIf { cid -> chaptersForBook.any { it.id == cid } }
          ?: chaptersForBook.firstOrNull { !it.isCompleted }?.id
          ?: chaptersForBook.firstOrNull()?.id

      val resolvedSetup =
        setup.copy(
          selectedBookId = preferredBookId,
          selectedChapterId = preferredChapterId,
        )

      val computedAnalytics =
        TimerAnalyticsCalculator.compute(
          sessions = sessions,
          period = period,
          today = LocalDate.now(),
        )

      TimerUiState(
        books = rawBooks,
        chaptersForSelectedBook = chaptersForBook,
        allChapters = snapshot.allChapters,
        setup = resolvedSetup,
        liveStudying = live,
        recoveryPrompt = recovery,
        allSessions = sessions,
        historyFilter = filter,
        analytics = computedAnalytics,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = TimerUiState(),
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
    setupState.update {
      it.copy(
        mode = TimerMode.POMODORO,
        pomodoroFocusMinutes = focusMinutes.coerceIn(1, 180),
        pomodoroBreakMinutes = breakMinutes.coerceIn(1, 60),
      )
    }
  }

  fun setGoalMinutes(goalMinutes: Int?) {
    setupState.update { it.copy(goalMinutes = goalMinutes) }
  }

  fun setSetupNotes(notes: String) {
    setupState.update { it.copy(notes = notes) }
  }

  fun startTimerSession(onStarted: () -> Unit) {
    val currentSetup = uiState.value.setup
    val bookId = currentSetup.selectedBookId
    val chapterId = currentSetup.selectedChapterId
    if (bookId == null || chapterId == null) {
      _events.tryEmit(TimerUiEvent.ShowMessage("Please select both a book and a chapter."))
      return
    }
    if (uiState.value.liveStudying.isRunningOrPaused) {
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
