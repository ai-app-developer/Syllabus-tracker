package com.example.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.MainActivity
import com.example.SyllabusApplication
import com.example.data.local.entity.ActiveTimerStateEntity
import com.example.notifications.NotificationHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TimerAlarmEventType(val requestCode: Int, val action: String) {
  FOCUS_END(3101, "com.example.timer.ACTION_ALARM_FOCUS_END"),
  BREAK_END(3102, "com.example.timer.ACTION_ALARM_BREAK_END"),
  GOAL_REACHED(3103, "com.example.timer.ACTION_ALARM_GOAL_REACHED"),
}

data class ScheduledAlarmTarget(
  val eventType: TimerAlarmEventType,
  val triggerWallMs: Long,
)

object TimerAlarmScheduler {
  private const val TAG = "TimerAlarm"
  const val BACKUP_OFFSET_MS = 2_000L
  const val LATE_THRESHOLD_MS = 15_000L
  const val TEST_ALARM_DELAY_MS = 10_000L
  const val TEST_ALARM_REQUEST_CODE = 3104
  const val ACTION_TEST_ALARM = "com.example.timer.ACTION_TEST_ALARM"

  const val EXTRA_EVENT_TYPE = "extra_alarm_event_type"
  const val EXTRA_SESSION_ID = "extra_alarm_session_id"
  const val EXTRA_POMODORO_CYCLE = "extra_alarm_pomodoro_cycle"
  const val EXTRA_TRIGGER_WALL_MS = "extra_alarm_trigger_wall_ms"
  const val EXTRA_TEST_ID = "extra_test_alarm_id"

  private val logTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

  @Volatile
  private var pendingTestPrimaryJob: Job? = null

  fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
  }

  /**
   * Computes exact wall-clock trigger targets from the stored timer state (elapsedRealtime-based
   * during the same boot session, or stored wall timestamps when recovering across reboot).
   */
  fun computeScheduledTargets(
    state: ActiveTimerStateEntity?,
    nowElapsedMs: Long,
    nowWallMs: Long,
    fromStoredWallTimesOnly: Boolean = false,
  ): List<ScheduledAlarmTarget> {
    if (state == null) return emptyList()
    if (state.status != TimerStatus.RUNNING && state.status != TimerStatus.BREAK) {
      return emptyList()
    }

    val targets = ArrayList<ScheduledAlarmTarget>(2)

    if (fromStoredWallTimesOnly) {
      when {
        state.mode == TimerMode.POMODORO &&
          state.status == TimerStatus.RUNNING &&
          state.focusDurationMs > 0L -> {
          val remainingFromPhaseStart = (state.focusDurationMs - state.phaseCarryMs).coerceAtLeast(0L)
          targets.add(
            ScheduledAlarmTarget(
              eventType = TimerAlarmEventType.FOCUS_END,
              triggerWallMs = state.phaseStartWallTimeMs + remainingFromPhaseStart,
            )
          )
        }
        state.mode == TimerMode.POMODORO &&
          state.status == TimerStatus.BREAK &&
          state.breakDurationMs > 0L -> {
          val remainingFromPhaseStart = (state.breakDurationMs - state.phaseCarryMs).coerceAtLeast(0L)
          targets.add(
            ScheduledAlarmTarget(
              eventType = TimerAlarmEventType.BREAK_END,
              triggerWallMs = state.phaseStartWallTimeMs + remainingFromPhaseStart,
            )
          )
        }
      }

      if (!state.goalNotified && state.status == TimerStatus.RUNNING && state.goalMs != null && state.goalMs > 0L) {
        val remainingFromPhaseStart =
          (state.goalMs - state.accumulatedFocusMs - state.phaseCarryMs).coerceAtLeast(0L)
        targets.add(
          ScheduledAlarmTarget(
            eventType = TimerAlarmEventType.GOAL_REACHED,
            triggerWallMs = state.phaseStartWallTimeMs + remainingFromPhaseStart,
          )
        )
      }
      return targets
    }

    val metrics = TimerMath.computeMetrics(state, nowElapsedMs, nowWallMs)

    when {
      state.mode == TimerMode.POMODORO &&
        state.status == TimerStatus.RUNNING &&
        state.focusDurationMs > 0L -> {
        val remainingMs = (state.focusDurationMs - metrics.currentPhaseElapsedMs).coerceAtLeast(0L)
        targets.add(
          ScheduledAlarmTarget(
            eventType = TimerAlarmEventType.FOCUS_END,
            triggerWallMs = nowWallMs + remainingMs,
          )
        )
      }
      state.mode == TimerMode.POMODORO &&
        state.status == TimerStatus.BREAK &&
        state.breakDurationMs > 0L -> {
        val remainingMs = (state.breakDurationMs - metrics.currentPhaseElapsedMs).coerceAtLeast(0L)
        targets.add(
          ScheduledAlarmTarget(
            eventType = TimerAlarmEventType.BREAK_END,
            triggerWallMs = nowWallMs + remainingMs,
          )
        )
      }
    }

    if (!state.goalNotified && state.status == TimerStatus.RUNNING && state.goalMs != null && state.goalMs > 0L) {
      val remainingGoalMs = (state.goalMs - metrics.activeFocusMs).coerceAtLeast(0L)
      targets.add(
        ScheduledAlarmTarget(
          eventType = TimerAlarmEventType.GOAL_REACHED,
          triggerWallMs = nowWallMs + remainingGoalMs,
        )
      )
    }

    return targets
  }

  fun syncAlarms(
    context: Context,
    state: ActiveTimerStateEntity?,
    clock: ClockProvider = SystemClockProvider,
    fromStoredWallTimesOnly: Boolean = false,
  ) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val nowElapsed = clock.elapsedRealtime()
    val nowWall = clock.currentTimeMillis()
    val targets =
      computeScheduledTargets(
        state = state,
        nowElapsedMs = nowElapsed,
        nowWallMs = nowWall,
        fromStoredWallTimesOnly = fromStoredWallTimesOnly,
      )

    if (targets.isEmpty() || state == null) {
      cancelAllAlarms(context)
      return
    }

    val activeEventTypes = targets.map { it.eventType }.toSet()
    for (eventType in TimerAlarmEventType.entries) {
      if (eventType !in activeEventTypes) {
        cancelSingleAlarm(context, alarmManager, eventType)
      }
    }

    val showIntent = buildShowStudyingIntent(context)
    val exactAllowed = canScheduleExactAlarms(context)

    for (target in targets) {
      val backupTriggerWallMs = target.triggerWallMs + BACKUP_OFFSET_MS
      val pi =
        buildAlarmPendingIntent(
          context = context,
          eventType = target.eventType,
          sessionId = state.sessionId,
          pomodoroCycle = state.pomodoroCycle,
          triggerWallMs = target.triggerWallMs,
        )
      runCatching { alarmManager.cancel(pi) }

      runCatching {
        if (exactAllowed) {
          alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(backupTriggerWallMs, showIntent),
            pi,
          )
        } else {
          alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            backupTriggerWallMs,
            pi,
          )
        }
        val formattedTime =
          Instant.ofEpochMilli(target.triggerWallMs)
            .atZone(ZoneId.systemDefault())
            .format(logTimeFormatter)
        Log.i(
          TAG,
          "scheduled primary+backup ${target.eventType.name} at $formattedTime (backup +2000ms at $backupTriggerWallMs, exact=$exactAllowed)",
        )
      }.onFailure { err ->
        Log.w(TAG, "Failed scheduling ${target.eventType.name}", err)
      }
    }
  }

  /**
   * Schedules a synthetic, session-less 10-second test alarm using BOTH:
   *  - Primary path: coroutine wait on applicationScope (fires at +10s)
   *  - Backup path: AlarmManager.setAlarmClock / setAndAllowWhileIdle (fires at +10s +2s backup)
   * so screen-locked end-to-end delivery can be verified cleanly without altering active_timer_state.
   */
  fun scheduleTestAlarmIn10Seconds(context: Context): String {
    val appContext = context.applicationContext
    val triggerWallMs = System.currentTimeMillis() + TEST_ALARM_DELAY_MS
    val backupTriggerWallMs = triggerWallMs + BACKUP_OFFSET_MS
    val testId = "test_${triggerWallMs}"

    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    val exactAllowed = canScheduleExactAlarms(appContext)
    val pi = buildTestAlarmPendingIntent(appContext, testId, triggerWallMs)

    if (alarmManager != null) {
      runCatching { alarmManager.cancel(pi) }
      runCatching {
        if (exactAllowed) {
          alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(backupTriggerWallMs, buildShowStudyingIntent(appContext)),
            pi,
          )
        } else {
          alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            backupTriggerWallMs,
            pi,
          )
        }
        Log.i(
          TAG,
          "scheduled test-in-10s primary+backup testId=$testId exact=$exactAllowed trigger=$triggerWallMs backup=$backupTriggerWallMs",
        )
      }.onFailure { err ->
        Log.w(TAG, "Failed scheduling 10s test backup alarm", err)
      }
    }

    val app = appContext as? SyllabusApplication
    pendingTestPrimaryJob?.cancel()
    pendingTestPrimaryJob =
      app?.applicationScope?.launch {
        delay(TEST_ALARM_DELAY_MS)
        TimerAlarmPlayer.fireTestAlarm(
          context = app,
          source = AlarmFireSource.PRIMARY,
          testId = testId,
          expectedEventWallMs = triggerWallMs,
        )
      }

    return testId
  }

  fun cancelAllAlarms(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    for (eventType in TimerAlarmEventType.entries) {
      cancelSingleAlarm(context, alarmManager, eventType)
    }
  }

  private fun cancelSingleAlarm(
    context: Context,
    alarmManager: AlarmManager,
    eventType: TimerAlarmEventType,
  ) {
    val intent =
      Intent(context, TimerAlarmReceiver::class.java).apply {
        action = eventType.action
      }
    val pi =
      PendingIntent.getBroadcast(
        context,
        eventType.requestCode,
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
      ) ?: return
    runCatching {
      alarmManager.cancel(pi)
      pi.cancel()
    }
  }

  private fun buildAlarmPendingIntent(
    context: Context,
    eventType: TimerAlarmEventType,
    sessionId: String,
    pomodoroCycle: Int,
    triggerWallMs: Long,
  ): PendingIntent {
    val intent =
      Intent(context, TimerAlarmReceiver::class.java).apply {
        action = eventType.action
        putExtra(EXTRA_EVENT_TYPE, eventType.name)
        putExtra(EXTRA_SESSION_ID, sessionId)
        putExtra(EXTRA_POMODORO_CYCLE, pomodoroCycle)
        putExtra(EXTRA_TRIGGER_WALL_MS, triggerWallMs)
      }
    return PendingIntent.getBroadcast(
      context,
      eventType.requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun buildTestAlarmPendingIntent(
    context: Context,
    testId: String,
    triggerWallMs: Long,
  ): PendingIntent {
    val intent =
      Intent(context, TimerAlarmReceiver::class.java).apply {
        action = ACTION_TEST_ALARM
        putExtra(EXTRA_TEST_ID, testId)
        putExtra(EXTRA_TRIGGER_WALL_MS, triggerWallMs)
      }
    return PendingIntent.getBroadcast(
      context,
      TEST_ALARM_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun buildShowStudyingIntent(context: Context): PendingIntent {
    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(NotificationHelper.EXTRA_TIMER_ACTION, NotificationHelper.TIMER_ACTION_OPEN_STUDYING)
      }
    return PendingIntent.getActivity(
      context,
      3100,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}

class TimerAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val app = context.applicationContext as? SyllabusApplication ?: return

    if (intent.action == TimerAlarmScheduler.ACTION_TEST_ALARM) {
      val testId = intent.getStringExtra(TimerAlarmScheduler.EXTRA_TEST_ID).orEmpty()
      if (testId.isBlank()) return
      val expectedTriggerWallMs =
        intent.getLongExtra(TimerAlarmScheduler.EXTRA_TRIGGER_WALL_MS, System.currentTimeMillis())
      val pendingResult: PendingResult? = goAsync()
      app.applicationScope.launch {
        try {
          TimerAlarmPlayer.fireTestAlarm(
            context = app,
            source = AlarmFireSource.BACKUP,
            testId = testId,
            expectedEventWallMs = expectedTriggerWallMs,
          )
        } finally {
          pendingResult?.finish()
        }
      }
      return
    }

    val eventTypeName = intent.getStringExtra(TimerAlarmScheduler.EXTRA_EVENT_TYPE) ?: return
    val eventType = runCatching { TimerAlarmEventType.valueOf(eventTypeName) }.getOrNull() ?: return
    val sessionId = intent.getStringExtra(TimerAlarmScheduler.EXTRA_SESSION_ID).orEmpty()
    if (sessionId.isBlank()) return
    val expectedCycle = intent.getIntExtra(TimerAlarmScheduler.EXTRA_POMODORO_CYCLE, 1)
    val expectedTriggerWallMs =
      intent.getLongExtra(TimerAlarmScheduler.EXTRA_TRIGGER_WALL_MS, System.currentTimeMillis())

    val pendingResult: PendingResult? = goAsync()
    app.applicationScope.launch {
      try {
        TimerAlarmPlayer.fireEvent(
          context = app,
          source = AlarmFireSource.BACKUP,
          eventType = eventType,
          sessionId = sessionId,
          phaseIndex = expectedCycle,
          expectedEventWallMs = expectedTriggerWallMs,
        )
      } finally {
        pendingResult?.finish()
      }
    }
  }
}
