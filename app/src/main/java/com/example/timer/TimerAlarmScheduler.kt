package com.example.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.MainActivity
import com.example.SyllabusApplication
import com.example.data.local.entity.ActiveTimerStateEntity
import com.example.notifications.NotificationHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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

  const val EXTRA_EVENT_TYPE = "extra_alarm_event_type"
  const val EXTRA_SESSION_ID = "extra_alarm_session_id"
  const val EXTRA_POMODORO_CYCLE = "extra_alarm_pomodoro_cycle"
  const val EXTRA_TRIGGER_WALL_MS = "extra_alarm_trigger_wall_ms"

  private val logTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

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
          targets.add(
            ScheduledAlarmTarget(
              eventType = TimerAlarmEventType.FOCUS_END,
              triggerWallMs = state.phaseStartWallTimeMs + state.focusDurationMs,
            )
          )
        }
        state.mode == TimerMode.POMODORO &&
          state.status == TimerStatus.BREAK &&
          state.breakDurationMs > 0L -> {
          targets.add(
            ScheduledAlarmTarget(
              eventType = TimerAlarmEventType.BREAK_END,
              triggerWallMs = state.phaseStartWallTimeMs + state.breakDurationMs,
            )
          )
        }
      }

      if (!state.goalNotified && state.status == TimerStatus.RUNNING && state.goalMs != null && state.goalMs > 0L) {
        val remainingFromPhaseStart = (state.goalMs - state.accumulatedFocusMs).coerceAtLeast(0L)
        targets.add(
          ScheduledAlarmTarget(
            eventType = TimerAlarmEventType.GOAL_REACHED,
            triggerWallMs = state.phaseStartWallTimeMs + remainingFromPhaseStart,
          )
        )
      }
      return targets
    }

    val elapsedDelta =
      if (nowElapsedMs >= state.phaseStartElapsedMs) {
        (nowElapsedMs - state.phaseStartElapsedMs).coerceAtLeast(0L)
      } else {
        (nowWallMs - state.phaseStartWallTimeMs).coerceAtLeast(0L)
      }

    when {
      state.mode == TimerMode.POMODORO &&
        state.status == TimerStatus.RUNNING &&
        state.focusDurationMs > 0L -> {
        val remainingMs = (state.focusDurationMs - elapsedDelta).coerceAtLeast(0L)
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
        val remainingMs = (state.breakDurationMs - elapsedDelta).coerceAtLeast(0L)
        targets.add(
          ScheduledAlarmTarget(
            eventType = TimerAlarmEventType.BREAK_END,
            triggerWallMs = nowWallMs + remainingMs,
          )
        )
      }
    }

    if (!state.goalNotified && state.status == TimerStatus.RUNNING && state.goalMs != null && state.goalMs > 0L) {
      val currentFocusMs = state.accumulatedFocusMs + elapsedDelta
      val remainingGoalMs = (state.goalMs - currentFocusMs).coerceAtLeast(0L)
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
        if (BuildConfig.DEBUG) {
          val formattedTime =
            Instant.ofEpochMilli(target.triggerWallMs)
              .atZone(ZoneId.systemDefault())
              .format(logTimeFormatter)
          Log.d(
            TAG,
            "scheduled primary+backup ${target.eventType.name} at $formattedTime (backup +2000ms at $backupTriggerWallMs)",
          )
        }
      }.onFailure { err ->
        Log.w(TAG, "Failed scheduling ${target.eventType.name}", err)
      }
    }
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
    val eventTypeName = intent.getStringExtra(TimerAlarmScheduler.EXTRA_EVENT_TYPE) ?: return
    val eventType = runCatching { TimerAlarmEventType.valueOf(eventTypeName) }.getOrNull() ?: return
    val sessionId = intent.getStringExtra(TimerAlarmScheduler.EXTRA_SESSION_ID).orEmpty()
    if (sessionId.isBlank()) return
    val expectedCycle = intent.getIntExtra(TimerAlarmScheduler.EXTRA_POMODORO_CYCLE, 1)
    val expectedTriggerWallMs =
      intent.getLongExtra(TimerAlarmScheduler.EXTRA_TRIGGER_WALL_MS, System.currentTimeMillis())

    val app = context.applicationContext as? SyllabusApplication ?: return
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
