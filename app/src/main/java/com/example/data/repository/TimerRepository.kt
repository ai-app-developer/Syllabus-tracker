package com.example.data.repository

import android.util.Log
import com.example.data.local.dao.ReadingSessionWithMeta
import com.example.data.local.dao.SyllabusDao
import com.example.data.local.dao.TimerDao
import com.example.data.local.entity.ActiveTimerStateEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.SessionEventEntity
import com.example.timer.ClockProvider
import com.example.timer.ComputedTimerMetrics
import com.example.timer.SessionEventType
import com.example.timer.SystemClockProvider
import com.example.timer.TimerMath
import com.example.timer.TimerMode
import com.example.timer.TimerStatus
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class TimerAlertType {
  POMODORO_FOCUS_COMPLETE,
  POMODORO_BREAK_COMPLETE,
  GOAL_REACHED,
}

data class ActiveSessionMetadata(
  val state: ActiveTimerStateEntity,
  val bookName: String,
  val chapterName: String,
  val chapterNumber: Int,
) {
  val chapterLabel: String
    get() = "Chapter $chapterNumber: $chapterName"
}

data class RecoveryPromptData(
  val state: ActiveTimerStateEntity,
  val bookName: String,
  val chapterName: String,
  val chapterNumber: Int,
  val recoveredMetrics: ComputedTimerMetrics,
) {
  val chapterLabel: String
    get() = "Chapter $chapterNumber: $chapterName"
}

class TimerRepository(
  private val timerDao: TimerDao,
  private val syllabusDao: SyllabusDao,
  private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
  private val clock: ClockProvider = SystemClockProvider,
  private val zoneId: ZoneId = ZoneId.systemDefault(),
  private val onSyncService: () -> Unit = {},
  private val onStopService: () -> Unit = {},
  private val onTimerAlert: (TimerAlertType, String, String) -> Unit = { _, _, _ -> },
  private val onSessionsChanged: suspend () -> Unit = {},
) {
  private val mutex = Mutex()

  @Volatile
  private var sessionStartedInCurrentProcess = false

  private val _recoveryPrompt = MutableStateFlow<RecoveryPromptData?>(null)
  val recoveryPrompt: StateFlow<RecoveryPromptData?> = _recoveryPrompt.asStateFlow()

  val activeTimerState: Flow<ActiveTimerStateEntity?> = timerDao.observeActiveTimerState()

  val activeSessionWithMeta: Flow<ActiveSessionMetadata?> =
    combine(
      timerDao.observeActiveTimerState(),
      syllabusDao.observeBooks(),
      syllabusDao.observeAllChapters(),
    ) { active, books, chapters ->
      if (active == null) {
        null
      } else {
        val bookName = books.firstOrNull { it.id == active.bookId }?.name ?: "Unknown Book"
        val chapter = chapters.firstOrNull { it.id == active.chapterId }
        ActiveSessionMetadata(
          state = active,
          bookName = bookName,
          chapterName = chapter?.name ?: "Chapter",
          chapterNumber = chapter?.number ?: 1,
        )
      }
    }

  val allSessionsWithMeta: Flow<List<ReadingSessionWithMeta>> =
    timerDao.observeAllSessionsWithMeta()

  val todayFocusMs: Flow<Long> =
    timerDao.observeTodayFocusMs(LocalDate.now(zoneId).toString())

  fun observeSessionEvents(sessionId: String): Flow<List<SessionEventEntity>> =
    timerDao.observeEventsForSession(sessionId)

  suspend fun checkUnfinishedSessionOnLaunch() {
    mutex.withLock {
      if (sessionStartedInCurrentProcess) return
      val existing = timerDao.getActiveTimerStateOnce() ?: return
      val book = syllabusDao.getBookByIdOnce(existing.bookId)
      val chapters = syllabusDao.getAllChaptersOnce()
      val chapter = chapters.firstOrNull { it.id == existing.chapterId }
      if (book == null || chapter == null) {
        timerDao.clearActiveTimerState()
        return
      }
      val recoveryMetrics = TimerMath.computeLastKnownRecoveryMetrics(existing)
      _recoveryPrompt.value =
        RecoveryPromptData(
          state = existing,
          bookName = book.name,
          chapterName = chapter.name,
          chapterNumber = chapter.number,
          recoveredMetrics = recoveryMetrics,
        )
    }
  }

  suspend fun startSession(
    bookId: Long,
    chapterId: Long,
    mode: String,
    focusMinutes: Int = 25,
    breakMinutes: Int = 5,
    goalMinutes: Int? = null,
    notes: String = "",
  ): Boolean {
    return mutex.withLock {
      if (timerDao.getActiveTimerStateOnce() != null) {
        Log.w(TAG, "Blocked startSession: active session already exists")
        return@withLock false
      }

      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val sessionId = UUID.randomUUID().toString()
      val focusMs =
        if (mode == TimerMode.POMODORO) focusMinutes.coerceIn(1, 180) * 60_000L else 0L
      val breakMs =
        if (mode == TimerMode.POMODORO) breakMinutes.coerceIn(1, 60) * 60_000L else 0L
      val goalMs = goalMinutes?.takeIf { it > 0 }?.let { it * 60_000L }

      val newState =
        ActiveTimerStateEntity(
          id = 1,
          sessionId = sessionId,
          bookId = bookId,
          chapterId = chapterId,
          mode = mode,
          status = TimerStatus.RUNNING,
          focusDurationMs = focusMs,
          breakDurationMs = breakMs,
          goalMs = goalMs,
          notes = notes.trim(),
          sessionStartWallTimeMs = nowWall,
          phaseStartElapsedMs = nowElapsed,
          phaseStartWallTimeMs = nowWall,
          accumulatedFocusMs = 0L,
          accumulatedBreakMs = 0L,
          accumulatedPauseMs = 0L,
          pauseStartElapsedMs = null,
          pauseStartWallTimeMs = null,
          lastKnownWallTimeMs = nowWall,
          lapCount = 0,
          goalNotified = false,
          pomodoroCycle = 1,
          statusBeforeStopSummary = null,
        )

      runInTransaction {
        timerDao.upsertActiveTimerState(newState)
        timerDao.insertEvent(
          SessionEventEntity(
            sessionId = sessionId,
            bookId = bookId,
            chapterId = chapterId,
            eventType = SessionEventType.START,
            elapsedAtEventMs = 0L,
            wallTimeMs = nowWall,
            label = mode,
          )
        )
      }

      sessionStartedInCurrentProcess = true
      _recoveryPrompt.value = null
      onSyncService()
      true
    }
  }

  suspend fun pauseSession() {
    mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return
      if (current.status != TimerStatus.RUNNING && current.status != TimerStatus.BREAK) return

      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val metrics = TimerMath.computeMetrics(current, nowElapsed, nowWall)

      val updated =
        current.copy(
          status = TimerStatus.PAUSED,
          accumulatedFocusMs = metrics.activeFocusMs,
          accumulatedBreakMs = metrics.breakMs,
          pauseStartElapsedMs = nowElapsed,
          pauseStartWallTimeMs = nowWall,
          lastKnownWallTimeMs = nowWall,
          statusBeforeStopSummary = null,
        )

      runInTransaction {
        timerDao.upsertActiveTimerState(updated)
        timerDao.insertEvent(
          SessionEventEntity(
            sessionId = current.sessionId,
            bookId = current.bookId,
            chapterId = current.chapterId,
            eventType = SessionEventType.PAUSE,
            elapsedAtEventMs = metrics.activeFocusMs,
            wallTimeMs = nowWall,
          )
        )
      }
      sessionStartedInCurrentProcess = true
      onSyncService()
    }
  }

  suspend fun resumeSession() {
    mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return
      if (current.status != TimerStatus.PAUSED && current.status != TimerStatus.STOP_SUMMARY) return

      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val targetStatus =
        if (current.status == TimerStatus.STOP_SUMMARY) {
          when (current.statusBeforeStopSummary) {
            TimerStatus.BREAK -> TimerStatus.BREAK
            else -> TimerStatus.RUNNING
          }
        } else {
          TimerStatus.RUNNING
        }

      val extraPause =
        current.pauseStartElapsedMs?.let { (nowElapsed - it).coerceAtLeast(0L) } ?: 0L

      val updated =
        current.copy(
          status = targetStatus,
          phaseStartElapsedMs = nowElapsed,
          phaseStartWallTimeMs = nowWall,
          accumulatedPauseMs = current.accumulatedPauseMs + extraPause,
          pauseStartElapsedMs = null,
          pauseStartWallTimeMs = null,
          lastKnownWallTimeMs = nowWall,
          statusBeforeStopSummary = null,
        )

      runInTransaction {
        timerDao.upsertActiveTimerState(updated)
        timerDao.insertEvent(
          SessionEventEntity(
            sessionId = current.sessionId,
            bookId = current.bookId,
            chapterId = current.chapterId,
            eventType = SessionEventType.RESUME,
            elapsedAtEventMs = current.accumulatedFocusMs,
            wallTimeMs = nowWall,
          )
        )
      }
      sessionStartedInCurrentProcess = true
      onSyncService()
    }
  }

  suspend fun togglePauseResume() {
    val current = timerDao.getActiveTimerStateOnce() ?: return
    when (current.status) {
      TimerStatus.RUNNING, TimerStatus.BREAK -> pauseSession()
      TimerStatus.PAUSED, TimerStatus.STOP_SUMMARY -> resumeSession()
    }
  }

  suspend fun recordLap(): Int? {
    return mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return@withLock null
      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val metrics = TimerMath.computeMetrics(current, nowElapsed, nowWall)
      val nextLap = current.lapCount + 1

      runInTransaction {
        timerDao.upsertActiveTimerState(
          current.copy(lapCount = nextLap, lastKnownWallTimeMs = nowWall)
        )
        timerDao.insertEvent(
          SessionEventEntity(
            sessionId = current.sessionId,
            bookId = current.bookId,
            chapterId = current.chapterId,
            eventType = SessionEventType.LAP,
            elapsedAtEventMs = metrics.activeFocusMs,
            wallTimeMs = nowWall,
            label = "Lap $nextLap",
          )
        )
      }
      nextLap
    }
  }

  suspend fun updateActiveNotes(notes: String) {
    mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return
      timerDao.upsertActiveTimerState(current.copy(notes = notes))
    }
  }

  suspend fun requestStopSummary() {
    mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return
      if (current.status == TimerStatus.STOP_SUMMARY) return

      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val metrics = TimerMath.computeMetrics(current, nowElapsed, nowWall)

      val updated =
        current.copy(
          status = TimerStatus.STOP_SUMMARY,
          statusBeforeStopSummary = current.status,
          accumulatedFocusMs = metrics.activeFocusMs,
          accumulatedBreakMs = metrics.breakMs,
          accumulatedPauseMs = metrics.pauseMs,
          pauseStartElapsedMs = nowElapsed,
          pauseStartWallTimeMs = nowWall,
          lastKnownWallTimeMs = nowWall,
        )

      timerDao.upsertActiveTimerState(updated)
      sessionStartedInCurrentProcess = true
      onSyncService()
    }
  }

  suspend fun resumeFromStopSummary() {
    resumeSession()
  }

  suspend fun saveCurrentSession(finalNotes: String? = null): Boolean {
    return mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return@withLock false
      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val metrics = TimerMath.computeMetrics(current, nowElapsed, nowWall)
      val notesToSave = (finalNotes ?: current.notes).trim()

      val inserted =
        persistSessionAndCleanupLocked(
          state = current,
          endWallTimeMs = nowWall,
          activeFocusMs = metrics.activeFocusMs,
          pauseMs = metrics.pauseMs,
          breakMs = metrics.breakMs,
          notes = notesToSave,
          nowElapsedMs = metrics.activeFocusMs,
        )
      inserted
    }
  }

  suspend fun discardCurrentSession() {
    mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return
      runInTransaction {
        timerDao.deleteEventsBySessionId(current.sessionId)
        timerDao.clearActiveTimerState()
      }
      sessionStartedInCurrentProcess = false
      _recoveryPrompt.value = null
      onStopService()
    }
  }

  suspend fun skipBreak() {
    mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return
      if (current.mode != TimerMode.POMODORO || current.status != TimerStatus.BREAK) return

      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val metrics = TimerMath.computeMetrics(current, nowElapsed, nowWall)

      val updated =
        current.copy(
          status = TimerStatus.RUNNING,
          phaseStartElapsedMs = nowElapsed,
          phaseStartWallTimeMs = nowWall,
          accumulatedBreakMs = metrics.breakMs,
          lastKnownWallTimeMs = nowWall,
          pomodoroCycle = current.pomodoroCycle + 1,
        )

      runInTransaction {
        timerDao.upsertActiveTimerState(updated)
        timerDao.insertEvent(
          SessionEventEntity(
            sessionId = current.sessionId,
            bookId = current.bookId,
            chapterId = current.chapterId,
            eventType = SessionEventType.BREAK_SKIP,
            elapsedAtEventMs = metrics.activeFocusMs,
            wallTimeMs = nowWall,
          )
        )
      }
      sessionStartedInCurrentProcess = true
      onSyncService()
    }
  }

  suspend fun advancePomodoroPhaseIfDue() {
    mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return
      if (current.mode != TimerMode.POMODORO) return
      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val metrics = TimerMath.computeMetrics(current, nowElapsed, nowWall)

      if (current.status == TimerStatus.RUNNING && current.focusDurationMs > 0L && metrics.currentPhaseRemainingMs == 0L) {
        val updated =
          current.copy(
            status = TimerStatus.BREAK,
            phaseStartElapsedMs = nowElapsed,
            phaseStartWallTimeMs = nowWall,
            accumulatedFocusMs = metrics.activeFocusMs,
            lastKnownWallTimeMs = nowWall,
          )
        runInTransaction {
          timerDao.upsertActiveTimerState(updated)
          timerDao.insertEvent(
            SessionEventEntity(
              sessionId = current.sessionId,
              bookId = current.bookId,
              chapterId = current.chapterId,
              eventType = SessionEventType.BREAK_START,
              elapsedAtEventMs = metrics.activeFocusMs,
              wallTimeMs = nowWall,
              label = "Break",
            )
          )
        }
        val bookName = syllabusDao.getBookByIdOnce(current.bookId)?.name ?: "Study Session"
        val breakMins = (current.breakDurationMs / 60_000L).coerceAtLeast(1L)
        onTimerAlert(
          TimerAlertType.POMODORO_FOCUS_COMPLETE,
          "Focus complete — $bookName",
          "Time for a $breakMins min break.",
        )
        onSyncService()
      } else if (current.status == TimerStatus.BREAK && current.breakDurationMs > 0L && metrics.currentPhaseRemainingMs == 0L) {
        val updated =
          current.copy(
            status = TimerStatus.RUNNING,
            phaseStartElapsedMs = nowElapsed,
            phaseStartWallTimeMs = nowWall,
            accumulatedBreakMs = metrics.breakMs,
            lastKnownWallTimeMs = nowWall,
            pomodoroCycle = current.pomodoroCycle + 1,
          )
        timerDao.upsertActiveTimerState(updated)
        val bookName = syllabusDao.getBookByIdOnce(current.bookId)?.name ?: "Study Session"
        onTimerAlert(
          TimerAlertType.POMODORO_BREAK_COMPLETE,
          "Break complete — $bookName",
          "Cycle ${current.pomodoroCycle + 1} focus started.",
        )
        onSyncService()
      }
    }
  }

  suspend fun checkAndMarkGoalAlertIfReached() {
    mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return
      val goalMs = current.goalMs ?: return
      if (current.goalNotified || current.status != TimerStatus.RUNNING) return

      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val metrics = TimerMath.computeMetrics(current, nowElapsed, nowWall)
      if (metrics.activeFocusMs >= goalMs) {
        timerDao.upsertActiveTimerState(
          current.copy(goalNotified = true, lastKnownWallTimeMs = nowWall)
        )
        val bookName = syllabusDao.getBookByIdOnce(current.bookId)?.name ?: "Study Session"
        onTimerAlert(
          TimerAlertType.GOAL_REACHED,
          "Session goal reached!",
          "You completed your ${TimerMath.formatGoalWords(goalMs)} goal for $bookName.",
        )
      }
    }
  }

  suspend fun touchLastKnownWallTime() {
    mutex.withLock {
      if (timerDao.getActiveTimerStateOnce() != null) {
        timerDao.updateLastKnownWallTime(clock.currentTimeMillis())
      }
    }
  }

  // ---- Crash / Kill / Reboot Recovery actions ----

  suspend fun resumeRecoveredSession() {
    mutex.withLock {
      val candidate = _recoveryPrompt.value ?: return
      val saved = candidate.state
      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val metrics = TimerMath.computeLastKnownRecoveryMetrics(saved)

      val targetStatus =
        when (saved.status) {
          TimerStatus.BREAK -> TimerStatus.BREAK
          else -> TimerStatus.RUNNING
        }

      val rebased =
        saved.copy(
          status = targetStatus,
          phaseStartElapsedMs = nowElapsed,
          phaseStartWallTimeMs = nowWall,
          accumulatedFocusMs = metrics.activeFocusMs,
          accumulatedBreakMs = metrics.breakMs,
          accumulatedPauseMs = metrics.pauseMs,
          pauseStartElapsedMs = null,
          pauseStartWallTimeMs = null,
          lastKnownWallTimeMs = nowWall,
          statusBeforeStopSummary = null,
        )

      runInTransaction {
        timerDao.upsertActiveTimerState(rebased)
        timerDao.insertEvent(
          SessionEventEntity(
            sessionId = saved.sessionId,
            bookId = saved.bookId,
            chapterId = saved.chapterId,
            eventType = SessionEventType.RESUME,
            elapsedAtEventMs = metrics.activeFocusMs,
            wallTimeMs = nowWall,
            label = "Recovered",
          )
        )
      }

      sessionStartedInCurrentProcess = true
      _recoveryPrompt.value = null
      onSyncService()
    }
  }

  suspend fun saveRecoveredSessionUpToLastKnownTime(): Boolean {
    return mutex.withLock {
      val candidate = _recoveryPrompt.value ?: return@withLock false
      val saved = candidate.state
      val metrics = TimerMath.computeLastKnownRecoveryMetrics(saved)
      val savedEndWall = maxOf(saved.lastKnownWallTimeMs, saved.sessionStartWallTimeMs)

      val ok =
        persistSessionAndCleanupLocked(
          state = saved,
          endWallTimeMs = savedEndWall,
          activeFocusMs = metrics.activeFocusMs,
          pauseMs = metrics.pauseMs,
          breakMs = metrics.breakMs,
          notes = saved.notes,
          nowElapsedMs = metrics.activeFocusMs,
        )
      _recoveryPrompt.value = null
      ok
    }
  }

  suspend fun discardRecoveredSession() {
    mutex.withLock {
      val candidate = _recoveryPrompt.value ?: return
      runInTransaction {
        timerDao.deleteEventsBySessionId(candidate.state.sessionId)
        timerDao.clearActiveTimerState()
      }
      sessionStartedInCurrentProcess = false
      _recoveryPrompt.value = null
      onStopService()
    }
  }

  // ---- Reading History CRUD ----

  suspend fun updateSessionNotes(sessionIdRow: Long, notes: String) {
    mutex.withLock {
      val existing = timerDao.getSessionByIdOnce(sessionIdRow) ?: return
      timerDao.updateSession(existing.copy(notes = notes.trim()))
    }
    onSessionsChanged()
  }

  suspend fun deleteReadingSession(sessionIdRow: Long) {
    mutex.withLock {
      timerDao.deleteSessionById(sessionIdRow)
    }
    onSessionsChanged()
  }

  fun observeSessionCount(): Flow<Int> =
    timerDao.observeAllSessionsWithMeta().map { it.size }

  private suspend fun persistSessionAndCleanupLocked(
    state: ActiveTimerStateEntity,
    endWallTimeMs: Long,
    activeFocusMs: Long,
    pauseMs: Long,
    breakMs: Long,
    notes: String,
    nowElapsedMs: Long,
  ): Boolean {
    val slices =
      TimerMath.splitSessionAcrossDays(
        baseSessionId = state.sessionId,
        bookId = state.bookId,
        chapterId = state.chapterId,
        mode = state.mode,
        startWallTimeMs = state.sessionStartWallTimeMs,
        endWallTimeMs = endWallTimeMs,
        activeFocusMs = activeFocusMs,
        pauseMs = pauseMs,
        breakMs = breakMs,
        goalMs = state.goalMs,
        notes = notes,
        zoneId = zoneId,
      )

    var insertedAny = false
    runInTransaction {
      for (slice in slices) {
        if (timerDao.countSessionsBySessionId(slice.sessionId) == 0) {
          val rowId = timerDao.insertSession(slice)
          if (rowId > 0L) insertedAny = true
        }
      }
      timerDao.insertEvent(
        SessionEventEntity(
          sessionId = state.sessionId,
          bookId = state.bookId,
          chapterId = state.chapterId,
          eventType = SessionEventType.STOP,
          elapsedAtEventMs = nowElapsedMs,
          wallTimeMs = endWallTimeMs,
        )
      )
      timerDao.clearActiveTimerState()
    }

    sessionStartedInCurrentProcess = false
    onStopService()
    if (insertedAny) {
      onSessionsChanged()
    }
    return insertedAny
  }

  companion object {
    private const val TAG = "TimerRepository"
  }
}
