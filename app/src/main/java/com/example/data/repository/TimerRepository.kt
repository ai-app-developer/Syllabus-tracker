package com.example.data.repository

import android.util.Log
import androidx.compose.runtime.Immutable
import com.example.BuildConfig
import com.example.data.local.dao.ReadingSessionWithMeta
import com.example.data.local.dao.SyllabusDao
import com.example.data.local.dao.TimerDao
import com.example.data.local.entity.ActiveTimerStateEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.SessionEventEntity
import com.example.notifications.NotificationHelper
import com.example.timer.AlarmFireSource
import com.example.timer.AnalyticsPeriod
import com.example.timer.ClockProvider
import com.example.timer.ComputedTimerMetrics
import com.example.timer.InMemoryAlarmDeduplicator
import com.example.timer.SessionEventType
import com.example.timer.SystemClockProvider
import com.example.timer.TimerAlarmDeduplicator
import com.example.timer.TimerAlarmEventType
import com.example.timer.TimerAlarmScheduler
import com.example.timer.TimerAnalyticsCalculator
import com.example.timer.TimerAnalyticsUiState
import com.example.timer.TimerMath
import com.example.timer.TimerMode
import com.example.timer.TimerStatus
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Immutable
data class AlarmOutcome(
  val eventType: TimerAlarmEventType,
  val delayMs: Long,
  val title: String,
  val message: String,
  val notificationId: Int,
  val updatedState: ActiveTimerStateEntity,
)

@Immutable
data class BootLateNotification(
  val title: String,
  val message: String,
  val notificationId: Int,
)

@Immutable
data class ActiveSessionMetadata(
  val state: ActiveTimerStateEntity,
  val bookName: String,
  val chapterName: String,
  val chapterNumber: Int,
) {
  val chapterLabel: String
    get() = "Chapter $chapterNumber: $chapterName"
}

@Immutable
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
  private val onSessionsChanged: suspend () -> Unit = {},
  private val alarmDeduplicator: TimerAlarmDeduplicator = InMemoryAlarmDeduplicator(),
  private val onSyncAlarm: (ActiveTimerStateEntity?) -> Unit = {},
  private val onSyncWallAlarms: (ActiveTimerStateEntity?) -> Unit = onSyncAlarm,
) {
  private val mutex = Mutex()

  @Volatile
  private var sessionStartedInCurrentProcess = false

  private val _recoveryPrompt = MutableStateFlow<RecoveryPromptData?>(null)
  val recoveryPrompt: StateFlow<RecoveryPromptData?> = _recoveryPrompt.asStateFlow()

  val activeTimerState: Flow<ActiveTimerStateEntity?> =
    timerDao.observeActiveTimerState().distinctUntilChanged()

  val activeSessionWithMeta: Flow<ActiveSessionMetadata?> =
    timerDao.observeActiveSessionWithMeta()
      .distinctUntilChanged()
      .map { row ->
        row?.let {
          ActiveSessionMetadata(
            state = it.state,
            bookName = it.bookName ?: "Unknown Book",
            chapterName = it.chapterName ?: "Chapter",
            chapterNumber = it.chapterNumber ?: 1,
          )
        }
      }
      .flowOn(Dispatchers.Default)

  val allSessionsWithMeta: Flow<List<ReadingSessionWithMeta>> =
    timerDao.observeAllSessionsWithMeta().distinctUntilChanged()

  val todayFocusMs: Flow<Long> =
    timerDao.observeTodayFocusMs(LocalDate.now(zoneId).toString()).distinctUntilChanged()

  fun observeSessionEvents(sessionId: String): Flow<List<SessionEventEntity>> =
    timerDao.observeEventsForSession(sessionId).distinctUntilChanged()

  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeAnalytics(periodFlow: Flow<AnalyticsPeriod>): Flow<TimerAnalyticsUiState> {
    return periodFlow
      .distinctUntilChanged()
      .flatMapLatest { period ->
        val today = LocalDate.now(zoneId)
        val todayStr = today.toString()
        val bounds = TimerAnalyticsCalculator.periodBounds(period, today)
        val startStr = bounds.start.toString()
        val endStr = bounds.end.toString()
        val prevStartStr = bounds.prevStart.toString()
        val prevEndStr = bounds.prevEnd.toString()

        val totalsFlow =
          combine(
            timerDao.observeTodayFocusMs(todayStr).distinctUntilChanged(),
            timerDao.observeTotalFocusMsBetween(startStr, endStr).distinctUntilChanged(),
            timerDao.observeTotalFocusMsBetween(prevStartStr, prevEndStr).distinctUntilChanged(),
          ) { todayMs, periodMs, prevPeriodMs ->
            Triple(todayMs, periodMs, prevPeriodMs)
          }

        val breakdownFlow =
          combine(
            timerDao.observeDailyFocusTotalsBetween(startStr, endStr).distinctUntilChanged(),
            timerDao.observeBookFocusTotalsBetween(startStr, endStr).distinctUntilChanged(),
            timerDao.observeDistinctActiveDatesDescending().distinctUntilChanged(),
          ) { dailyRows, bookRows, activeDatesDesc ->
            Triple(dailyRows, bookRows, activeDatesDesc)
          }

        combine(totalsFlow, breakdownFlow) {
          (todayMs, periodMs, prevPeriodMs),
          (dailyRows, bookRows, activeDatesDesc) ->
          TimerAnalyticsCalculator.computeFromAggregates(
            period = period,
            todayTotalMs = todayMs,
            periodTotalMs = periodMs,
            previousPeriodTotalMs = prevPeriodMs,
            dailyRows = dailyRows,
            bookRows = bookRows,
            activeDatesDesc = activeDatesDesc,
            today = today,
          )
        }
      }
      .distinctUntilChanged()
      .flowOn(Dispatchers.Default)
  }

  suspend fun checkUnfinishedSessionOnLaunch() {
    mutex.withLock {
      if (sessionStartedInCurrentProcess) return
      val existing = timerDao.getActiveTimerStateOnce() ?: return
      val book = syllabusDao.getBookByIdOnce(existing.bookId)
      val chapter = syllabusDao.getChapterByIdOnce(existing.chapterId)
      if (book == null || chapter == null) {
        onSyncAlarm(null)
        timerDao.clearActiveTimerState()
        alarmDeduplicator.clearSession(existing.sessionId)
        return
      }
      val nowWall = clock.currentTimeMillis()
      val nowElapsed = clock.elapsedRealtime()

      if (existing.status == TimerStatus.PAUSED) {
        _recoveryPrompt.value = null
        onSyncAlarm(null)
        val pauseStartElapsed = existing.pauseStartElapsedMs
        val needsRebootRebase = pauseStartElapsed == null || nowElapsed < pauseStartElapsed
        if (needsRebootRebase) {
          val pausedWall = existing.pauseStartWallTimeMs ?: existing.lastKnownWallTimeMs
          val offlineWallPause = (nowWall - pausedWall).coerceAtLeast(0L)
          timerDao.updateRebootRebasedPausedState(
            accumulatedPauseMs = existing.accumulatedPauseMs + offlineWallPause,
            pauseStartElapsedMs = nowElapsed,
            pauseStartWallTimeMs = nowWall,
            lastKnownWallTimeMs = nowWall,
          )
        }
        sessionStartedInCurrentProcess = true
        onSyncService()
        return
      }

      if (existing.status == TimerStatus.RUNNING || existing.status == TimerStatus.BREAK) {
        val wallTargets =
          TimerAlarmScheduler.computeScheduledTargets(
            state = existing,
            nowElapsedMs = nowElapsed,
            nowWallMs = nowWall,
            fromStoredWallTimesOnly = true,
          )
        for (target in wallTargets) {
          if (target.triggerWallMs <= nowWall) {
            alarmDeduplicator.markSuppressed(
              buildEventKey(existing.sessionId, existing.pomodoroCycle, target.eventType),
              existing.sessionId,
            )
          }
        }
        val hasFutureTargets = wallTargets.any { it.triggerWallMs > nowWall }
        if (hasFutureTargets) {
          onSyncWallAlarms(existing)
        } else {
          onSyncAlarm(null)
        }
      } else {
        onSyncAlarm(null)
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
          phaseCarryMs = 0L,
          pausedFromStatus = null,
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
      alarmDeduplicator.clearSession(sessionId)
      onSyncAlarm(newState)
      onSyncService()
      true
    }
  }

  suspend fun pause() {
    mutex.withLock {
      pauseLocked()
    }
  }

  suspend fun resume() {
    mutex.withLock {
      resumeLocked()
    }
  }

  suspend fun pauseSession() {
    pause()
  }

  suspend fun resumeSession() {
    resume()
  }

  suspend fun togglePauseResume() {
    mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return
      when (current.status) {
        TimerStatus.RUNNING, TimerStatus.BREAK -> pauseLocked(current)
        TimerStatus.PAUSED, TimerStatus.STOP_SUMMARY -> resumeLocked(current)
      }
    }
  }

  private suspend fun pauseLocked(preloaded: ActiveTimerStateEntity? = null) {
    val current = preloaded ?: timerDao.getActiveTimerStateOnce() ?: return
    if (current.status != TimerStatus.RUNNING && current.status != TimerStatus.BREAK) return

    val nowElapsed = clock.elapsedRealtime()
    val nowWall = clock.currentTimeMillis()
    val runningMetrics = TimerMath.computeMetrics(current, nowElapsed, nowWall)
    val carryMs = runningMetrics.currentPhaseElapsedMs
    val pausedPhase = current.status
    val updated =
      current.copy(
        status = TimerStatus.PAUSED,
        pausedFromStatus = pausedPhase,
        phaseCarryMs = carryMs,
        pauseStartElapsedMs = nowElapsed,
        pauseStartWallTimeMs = nowWall,
        lastKnownWallTimeMs = nowWall,
      )
    val metrics = TimerMath.computeMetrics(updated, nowElapsed, nowWall)

    runInTransaction {
      timerDao.updatePauseState(
        status = TimerStatus.PAUSED,
        pausedFromStatus = pausedPhase,
        phaseCarryMs = carryMs,
        pauseStartElapsedMs = nowElapsed,
        pauseStartWallTimeMs = nowWall,
        lastKnownWallTimeMs = nowWall,
      )
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
    onSyncAlarm(null)
    onSyncService()
    if (BuildConfig.DEBUG) {
      Log.d(
        "TimerRepo",
        "pause: phaseCarryMs=$carryMs phaseElapsed=${metrics.currentPhaseElapsedMs} accumulatedFocusMs=${current.accumulatedFocusMs} totalPause=${metrics.pauseMs}",
      )
    }
  }

  private suspend fun resumeLocked(preloaded: ActiveTimerStateEntity? = null) {
    val current = preloaded ?: timerDao.getActiveTimerStateOnce() ?: return
    if (current.status != TimerStatus.PAUSED && current.status != TimerStatus.STOP_SUMMARY) return

    val nowElapsed = clock.elapsedRealtime()
    val nowWall = clock.currentTimeMillis()
    val targetStatus =
      when (current.status) {
        TimerStatus.PAUSED -> current.pausedFromStatus ?: TimerStatus.RUNNING
        TimerStatus.STOP_SUMMARY ->
          when (current.statusBeforeStopSummary) {
            TimerStatus.BREAK -> TimerStatus.BREAK
            TimerStatus.PAUSED -> current.pausedFromStatus ?: TimerStatus.RUNNING
            else -> TimerStatus.RUNNING
          }
        else -> TimerStatus.RUNNING
      }

    val pauseStartElapsed = current.pauseStartElapsedMs
    val extraPause =
      when {
        pauseStartElapsed != null && nowElapsed >= pauseStartElapsed ->
          (nowElapsed - pauseStartElapsed).coerceAtLeast(0L)
        current.pauseStartWallTimeMs != null ->
          (nowWall - current.pauseStartWallTimeMs).coerceAtLeast(0L)
        else -> 0L
      }

    val newPhaseStartElapsed = nowElapsed
    val newPhaseStartWall = nowWall
    val newAccumulatedPause = current.accumulatedPauseMs + extraPause

    val updated =
      current.copy(
        status = targetStatus,
        phaseStartElapsedMs = newPhaseStartElapsed,
        phaseStartWallTimeMs = newPhaseStartWall,
        accumulatedPauseMs = newAccumulatedPause,
        pauseStartElapsedMs = null,
        pauseStartWallTimeMs = null,
        lastKnownWallTimeMs = nowWall,
        pausedFromStatus = null,
        statusBeforeStopSummary = null,
      )
    val metrics = TimerMath.computeMetrics(updated, nowElapsed, nowWall)

    runInTransaction {
      timerDao.updateResumeState(
        status = targetStatus,
        phaseStartElapsedMs = newPhaseStartElapsed,
        phaseStartWallTimeMs = newPhaseStartWall,
        accumulatedPauseMs = newAccumulatedPause,
        lastKnownWallTimeMs = nowWall,
      )
      timerDao.insertEvent(
        SessionEventEntity(
          sessionId = current.sessionId,
          bookId = current.bookId,
          chapterId = current.chapterId,
          eventType = SessionEventType.RESUME,
          elapsedAtEventMs = metrics.activeFocusMs,
          wallTimeMs = nowWall,
        )
      )
    }
    sessionStartedInCurrentProcess = true
    onSyncAlarm(updated)
    onSyncService()
    if (BuildConfig.DEBUG) {
      Log.d(
        "TimerRepo",
        "resume: phaseCarryMs=${updated.phaseCarryMs} phaseElapsed=${metrics.currentPhaseElapsedMs} accumulatedFocusMs=${updated.accumulatedFocusMs} totalPause=${metrics.pauseMs}",
      )
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

  suspend fun updateActiveGoal(goalMinutes: Int?) {
    mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return
      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val metrics = TimerMath.computeMetrics(current, nowElapsed, nowWall)
      val newGoalMs = goalMinutes?.takeIf { it > 0 }?.let { it * 60_000L }
      val alreadyReached = newGoalMs != null && metrics.activeFocusMs >= newGoalMs
      if (!alreadyReached) {
        alarmDeduplicator.clearEvent(
          buildEventKey(current.sessionId, current.pomodoroCycle, TimerAlarmEventType.GOAL_REACHED),
          current.sessionId,
        )
      }
      val updated =
        current.copy(
          goalMs = newGoalMs,
          goalNotified = alreadyReached,
          lastKnownWallTimeMs = nowWall,
        )
      timerDao.upsertActiveTimerState(updated)
      onSyncAlarm(updated)
    }
  }

  suspend fun requestStopSummary() {
    mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return
      if (current.status == TimerStatus.STOP_SUMMARY) return

      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val metrics = TimerMath.computeMetrics(current, nowElapsed, nowWall)

      timerDao.updateStopSummaryState(
        status = TimerStatus.STOP_SUMMARY,
        pausedFromStatus = current.pausedFromStatus,
        statusBeforeStopSummary = current.status,
        phaseCarryMs = metrics.currentPhaseElapsedMs,
        accumulatedPauseMs = metrics.pauseMs,
        pauseStartElapsedMs = nowElapsed,
        pauseStartWallTimeMs = nowWall,
        lastKnownWallTimeMs = nowWall,
      )
      sessionStartedInCurrentProcess = true
      onSyncAlarm(null)
      onSyncService()
    }
  }

  suspend fun resumeFromStopSummary() {
    resume()
  }

  suspend fun saveCurrentSession(finalNotes: String? = null): Boolean {
    return mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce() ?: return@withLock false
      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()
      val metrics = TimerMath.computeMetrics(current, nowElapsed, nowWall)
      val notesToSave = (finalNotes ?: current.notes).trim()

      persistSessionAndCleanupLocked(
        state = current,
        endWallTimeMs = nowWall,
        activeFocusMs = metrics.activeFocusMs,
        pauseMs = metrics.pauseMs,
        breakMs = metrics.breakMs,
        notes = notesToSave,
        nowElapsedMs = metrics.activeFocusMs,
      )
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
      alarmDeduplicator.clearSession(current.sessionId)
      onSyncAlarm(null)
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
          phaseCarryMs = 0L,
          pausedFromStatus = null,
          statusBeforeStopSummary = null,
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
      alarmDeduplicator.markSuppressed(
        buildEventKey(current.sessionId, current.pomodoroCycle, TimerAlarmEventType.BREAK_END),
        current.sessionId,
      )
      sessionStartedInCurrentProcess = true
      onSyncAlarm(updated)
      onSyncService()
    }
  }

  /**
   * Shared duplicate-proof firing gate called by both the PRIMARY in-service timer
   * and the BACKUP TimerAlarmReceiver via TimerAlarmPlayer.fireEvent().
   */
  suspend fun fireEvent(
    source: AlarmFireSource = AlarmFireSource.BACKUP,
    eventType: TimerAlarmEventType,
    sessionId: String,
    phaseIndex: Int,
    expectedEventWallMs: Long,
  ): AlarmOutcome? {
    val eventKey = buildEventKey(sessionId, phaseIndex, eventType)
    return mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce()
      if (current == null || current.sessionId != sessionId) {
        if (BuildConfig.DEBUG) Log.d(ALARM_TAG, "skipped duplicate $eventKey")
        return@withLock null
      }
      if (current.status != TimerStatus.RUNNING && current.status != TimerStatus.BREAK) {
        if (BuildConfig.DEBUG) Log.d(ALARM_TAG, "skipped duplicate $eventKey")
        onSyncAlarm(null)
        return@withLock null
      }

      val nowElapsed = clock.elapsedRealtime()
      val nowWall = clock.currentTimeMillis()

      if (source == AlarmFireSource.BACKUP && nowWall < expectedEventWallMs - EARLY_TOLERANCE_MS) {
        onSyncAlarm(current)
        return@withLock null
      }

      val delayMs = (nowWall - expectedEventWallMs).coerceAtLeast(0L)

      when (eventType) {
        TimerAlarmEventType.FOCUS_END -> {
          if (
            current.mode != TimerMode.POMODORO ||
              current.status != TimerStatus.RUNNING ||
              current.pomodoroCycle != phaseIndex ||
              current.focusDurationMs <= 0L
          ) {
            if (BuildConfig.DEBUG) Log.d(ALARM_TAG, "skipped duplicate $eventKey")
            return@withLock null
          }
          if (!alarmDeduplicator.tryAcquire(eventKey, current.sessionId)) {
            if (BuildConfig.DEBUG) Log.d(ALARM_TAG, "skipped duplicate $eventKey")
            return@withLock null
          }

          val newAccumulatedFocus = current.accumulatedFocusMs + current.focusDurationMs
          val updated =
            current.copy(
              status = TimerStatus.BREAK,
              phaseStartElapsedMs = nowElapsed,
              phaseStartWallTimeMs = nowWall,
              accumulatedFocusMs = newAccumulatedFocus,
              lastKnownWallTimeMs = nowWall,
              phaseCarryMs = 0L,
              pausedFromStatus = null,
              statusBeforeStopSummary = null,
            )

          runInTransaction {
            timerDao.upsertActiveTimerState(updated)
            timerDao.insertEvent(
              SessionEventEntity(
                sessionId = current.sessionId,
                bookId = current.bookId,
                chapterId = current.chapterId,
                eventType = SessionEventType.BREAK_START,
                elapsedAtEventMs = newAccumulatedFocus,
                wallTimeMs = nowWall,
                label = "Break",
              )
            )
          }

          onSyncAlarm(updated)
          val bookName = syllabusDao.getBookByIdOnce(current.bookId)?.name ?: "Study Session"
          val breakMins = (current.breakDurationMs / 60_000L).coerceAtLeast(1L)
          AlarmOutcome(
            eventType = eventType,
            delayMs = delayMs,
            title = "Break started",
            message = "$bookName • Time for a $breakMins min break.",
            notificationId = NotificationHelper.TIMER_ALERT_PHASE_NOTIFICATION_ID,
            updatedState = updated,
          )
        }

        TimerAlarmEventType.BREAK_END -> {
          if (
            current.mode != TimerMode.POMODORO ||
              current.status != TimerStatus.BREAK ||
              current.pomodoroCycle != phaseIndex ||
              current.breakDurationMs <= 0L
          ) {
            if (BuildConfig.DEBUG) Log.d(ALARM_TAG, "skipped duplicate $eventKey")
            return@withLock null
          }
          if (!alarmDeduplicator.tryAcquire(eventKey, current.sessionId)) {
            if (BuildConfig.DEBUG) Log.d(ALARM_TAG, "skipped duplicate $eventKey")
            return@withLock null
          }

          val newAccumulatedBreak = current.accumulatedBreakMs + current.breakDurationMs
          val nextCycle = current.pomodoroCycle + 1
          val updated =
            current.copy(
              status = TimerStatus.RUNNING,
              phaseStartElapsedMs = nowElapsed,
              phaseStartWallTimeMs = nowWall,
              accumulatedBreakMs = newAccumulatedBreak,
              lastKnownWallTimeMs = nowWall,
              pomodoroCycle = nextCycle,
              phaseCarryMs = 0L,
              pausedFromStatus = null,
              statusBeforeStopSummary = null,
            )

          timerDao.upsertActiveTimerState(updated)
          onSyncAlarm(updated)
          val bookName = syllabusDao.getBookByIdOnce(current.bookId)?.name ?: "Study Session"
          AlarmOutcome(
            eventType = eventType,
            delayMs = delayMs,
            title = "Break over, back to focus",
            message = "$bookName • Cycle $nextCycle focus started.",
            notificationId = NotificationHelper.TIMER_ALERT_PHASE_NOTIFICATION_ID,
            updatedState = updated,
          )
        }

        TimerAlarmEventType.GOAL_REACHED -> {
          val goalMs = current.goalMs
          if (goalMs == null || current.goalNotified || current.status != TimerStatus.RUNNING) {
            if (BuildConfig.DEBUG) Log.d(ALARM_TAG, "skipped duplicate $eventKey")
            return@withLock null
          }
          if (!alarmDeduplicator.tryAcquire(eventKey, current.sessionId)) {
            if (BuildConfig.DEBUG) Log.d(ALARM_TAG, "skipped duplicate $eventKey")
            return@withLock null
          }

          val updated = current.copy(goalNotified = true, lastKnownWallTimeMs = nowWall)
          timerDao.upsertActiveTimerState(updated)
          onSyncAlarm(updated)
          val bookName = syllabusDao.getBookByIdOnce(current.bookId)?.name ?: "Study Session"
          AlarmOutcome(
            eventType = eventType,
            delayMs = delayMs,
            title = "Goal completed",
            message = "$bookName • You reached your ${TimerMath.formatGoalWords(goalMs)} study goal.",
            notificationId = NotificationHelper.TIMER_ALERT_GOAL_NOTIFICATION_ID,
            updatedState = updated,
          )
        }
      }
    }
  }

  /**
   * Reschedules alarms on BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME_SET / TIMEZONE_CHANGED.
   * Returns optional silent notifications for events that passed within the last 15 seconds.
   */
  suspend fun handleSystemTimeOrBootReschedule(isReboot: Boolean): List<BootLateNotification> {
    return mutex.withLock {
      val current = timerDao.getActiveTimerStateOnce()
      if (current == null) {
        onSyncAlarm(null)
        return@withLock emptyList()
      }
      if (current.status != TimerStatus.RUNNING && current.status != TimerStatus.BREAK) {
        onSyncAlarm(null)
        return@withLock emptyList()
      }

      if (!isReboot) {
        onSyncAlarm(current)
        return@withLock emptyList()
      }

      val nowWall = clock.currentTimeMillis()
      val nowElapsed = clock.elapsedRealtime()
      val bookName = syllabusDao.getBookByIdOnce(current.bookId)?.name ?: "Study Session"
      val lateNotifications = ArrayList<BootLateNotification>()
      var workingState: ActiveTimerStateEntity = current

      val targets =
        TimerAlarmScheduler.computeScheduledTargets(
          state = workingState,
          nowElapsedMs = nowElapsed,
          nowWallMs = nowWall,
          fromStoredWallTimesOnly = true,
        )

      for (target in targets) {
        if (target.triggerWallMs <= nowWall) {
          val lateMs = nowWall - target.triggerWallMs
          when (target.eventType) {
            TimerAlarmEventType.GOAL_REACHED -> {
              alarmDeduplicator.markSuppressed(
                buildEventKey(workingState.sessionId, workingState.pomodoroCycle, TimerAlarmEventType.GOAL_REACHED),
                workingState.sessionId,
              )
              workingState = workingState.copy(goalNotified = true, lastKnownWallTimeMs = nowWall)
              timerDao.upsertActiveTimerState(workingState)
              if (lateMs <= TimerAlarmScheduler.LATE_THRESHOLD_MS) {
                val goalMs = workingState.goalMs ?: 0L
                lateNotifications.add(
                  BootLateNotification(
                    title = "Goal completed",
                    message = "$bookName • You reached your ${TimerMath.formatGoalWords(goalMs)} study goal.",
                    notificationId = NotificationHelper.TIMER_ALERT_GOAL_NOTIFICATION_ID,
                  )
                )
              }
            }
            TimerAlarmEventType.FOCUS_END -> {
              alarmDeduplicator.markSuppressed(
                buildEventKey(workingState.sessionId, workingState.pomodoroCycle, TimerAlarmEventType.FOCUS_END),
                workingState.sessionId,
              )
              if (lateMs <= TimerAlarmScheduler.LATE_THRESHOLD_MS) {
                val breakMins = (workingState.breakDurationMs / 60_000L).coerceAtLeast(1L)
                workingState =
                  workingState.copy(
                    status = TimerStatus.BREAK,
                    phaseStartElapsedMs = (nowElapsed - lateMs).coerceAtLeast(0L),
                    phaseStartWallTimeMs = target.triggerWallMs,
                    accumulatedFocusMs =
                      workingState.accumulatedFocusMs + workingState.focusDurationMs,
                    lastKnownWallTimeMs = nowWall,
                    phaseCarryMs = 0L,
                    pausedFromStatus = null,
                    statusBeforeStopSummary = null,
                  )
                timerDao.upsertActiveTimerState(workingState)
                lateNotifications.add(
                  BootLateNotification(
                    title = "Break started",
                    message = "$bookName • Time for a $breakMins min break.",
                    notificationId = NotificationHelper.TIMER_ALERT_PHASE_NOTIFICATION_ID,
                  )
                )
              }
            }
            TimerAlarmEventType.BREAK_END -> {
              alarmDeduplicator.markSuppressed(
                buildEventKey(workingState.sessionId, workingState.pomodoroCycle, TimerAlarmEventType.BREAK_END),
                workingState.sessionId,
              )
              if (lateMs <= TimerAlarmScheduler.LATE_THRESHOLD_MS) {
                val nextCycle = workingState.pomodoroCycle + 1
                workingState =
                  workingState.copy(
                    status = TimerStatus.RUNNING,
                    phaseStartElapsedMs = (nowElapsed - lateMs).coerceAtLeast(0L),
                    phaseStartWallTimeMs = target.triggerWallMs,
                    accumulatedBreakMs =
                      workingState.accumulatedBreakMs + workingState.breakDurationMs,
                    lastKnownWallTimeMs = nowWall,
                    pomodoroCycle = nextCycle,
                    phaseCarryMs = 0L,
                    pausedFromStatus = null,
                    statusBeforeStopSummary = null,
                  )
                timerDao.upsertActiveTimerState(workingState)
                lateNotifications.add(
                  BootLateNotification(
                    title = "Break over, back to focus",
                    message = "$bookName • Cycle $nextCycle focus started.",
                    notificationId = NotificationHelper.TIMER_ALERT_PHASE_NOTIFICATION_ID,
                  )
                )
              }
            }
          }
        }
      }

      val futureTargets =
        TimerAlarmScheduler.computeScheduledTargets(
          state = workingState,
          nowElapsedMs = nowElapsed,
          nowWallMs = nowWall,
          fromStoredWallTimesOnly = true,
        ).filter { it.triggerWallMs > nowWall }

      if (futureTargets.isNotEmpty()) {
        onSyncWallAlarms(workingState)
      } else {
        onSyncAlarm(null)
      }

      lateNotifications
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

      if (saved.status == TimerStatus.PAUSED) {
        val pauseStartElapsed = saved.pauseStartElapsedMs
        val needsRebootRebase = pauseStartElapsed == null || nowElapsed < pauseStartElapsed
        val offlinePause =
          if (needsRebootRebase) {
            val pausedWall = saved.pauseStartWallTimeMs ?: saved.lastKnownWallTimeMs
            (nowWall - pausedWall).coerceAtLeast(0L)
          } else {
            0L
          }
        val restored =
          saved.copy(
            status = TimerStatus.PAUSED,
            pausedFromStatus = saved.pausedFromStatus ?: TimerStatus.RUNNING,
            accumulatedPauseMs = saved.accumulatedPauseMs + offlinePause,
            pauseStartElapsedMs = if (needsRebootRebase) nowElapsed else pauseStartElapsed,
            pauseStartWallTimeMs = if (needsRebootRebase) nowWall else saved.pauseStartWallTimeMs,
            lastKnownWallTimeMs = nowWall,
          )
        timerDao.upsertActiveTimerState(restored)
        sessionStartedInCurrentProcess = true
        _recoveryPrompt.value = null
        onSyncAlarm(null)
        onSyncService()
        return
      }

      val goalAlreadyPassedBeforeDeath =
        saved.goalMs != null && metrics.activeFocusMs >= saved.goalMs
      val resolvedGoalNotified = saved.goalNotified || goalAlreadyPassedBeforeDeath
      if (resolvedGoalNotified) {
        alarmDeduplicator.markSuppressed(
          buildEventKey(saved.sessionId, saved.pomodoroCycle, TimerAlarmEventType.GOAL_REACHED),
          saved.sessionId,
        )
      }

      if (saved.mode == TimerMode.POMODORO) {
        val phaseAtLastKnown = metrics.currentPhaseElapsedMs
        if (
          saved.status == TimerStatus.RUNNING &&
            saved.focusDurationMs > 0L &&
            phaseAtLastKnown >= saved.focusDurationMs
        ) {
          alarmDeduplicator.markSuppressed(
            buildEventKey(saved.sessionId, saved.pomodoroCycle, TimerAlarmEventType.FOCUS_END),
            saved.sessionId,
          )
        } else if (
          saved.status == TimerStatus.BREAK &&
            saved.breakDurationMs > 0L &&
            phaseAtLastKnown >= saved.breakDurationMs
        ) {
          alarmDeduplicator.markSuppressed(
            buildEventKey(saved.sessionId, saved.pomodoroCycle, TimerAlarmEventType.BREAK_END),
            saved.sessionId,
          )
        }
      }

      val targetStatus =
        when {
          saved.status == TimerStatus.BREAK -> TimerStatus.BREAK
          saved.status == TimerStatus.STOP_SUMMARY &&
            (saved.statusBeforeStopSummary == TimerStatus.BREAK ||
              (saved.statusBeforeStopSummary == TimerStatus.PAUSED &&
                saved.pausedFromStatus == TimerStatus.BREAK)) ->
            TimerStatus.BREAK
          else -> TimerStatus.RUNNING
        }

      val rebased =
        saved.copy(
          status = targetStatus,
          phaseStartElapsedMs = nowElapsed,
          phaseStartWallTimeMs = nowWall,
          phaseCarryMs = metrics.currentPhaseElapsedMs,
          accumulatedFocusMs = saved.accumulatedFocusMs,
          accumulatedBreakMs = saved.accumulatedBreakMs,
          accumulatedPauseMs = metrics.pauseMs,
          pauseStartElapsedMs = null,
          pauseStartWallTimeMs = null,
          lastKnownWallTimeMs = nowWall,
          goalNotified = resolvedGoalNotified,
          pausedFromStatus = null,
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
      onSyncAlarm(rebased)
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
      alarmDeduplicator.clearSession(candidate.state.sessionId)
      onSyncAlarm(null)
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

  suspend fun resetAfterBackupImport(wasReplaceMode: Boolean) {
    mutex.withLock {
      if (wasReplaceMode) {
        sessionStartedInCurrentProcess = false
        _recoveryPrompt.value = null
        alarmDeduplicator.clearAll()
        onSyncAlarm(null)
        onStopService()
      } else {
        val active = timerDao.getActiveTimerStateOnce()
        if (active == null) {
          alarmDeduplicator.clearAll()
          onSyncAlarm(null)
          onStopService()
        } else {
          onSyncAlarm(active)
          onSyncService()
        }
      }
    }
  }

  fun observeSessionCount(): Flow<Int> =
    timerDao.observeSessionCount().distinctUntilChanged()

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
    alarmDeduplicator.clearSession(state.sessionId)
    onSyncAlarm(null)
    onStopService()
    if (insertedAny) {
      onSessionsChanged()
    }
    return insertedAny
  }

  companion object {
    private const val TAG = "TimerRepository"
    private const val ALARM_TAG = "TimerAlarm"
    private const val EARLY_TOLERANCE_MS = 1500L

    fun buildEventKey(
      sessionId: String,
      phaseIndex: Int,
      eventType: TimerAlarmEventType,
    ): String {
      return if (eventType == TimerAlarmEventType.GOAL_REACHED) {
        "$sessionId:session:${eventType.name}"
      } else {
        "$sessionId:$phaseIndex:${eventType.name}"
      }
    }
  }
}
