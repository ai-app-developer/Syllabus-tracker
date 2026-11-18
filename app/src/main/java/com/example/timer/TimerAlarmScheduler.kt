package com.example.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.SyllabusApplication
import com.example.data.local.entity.ActiveTimerStateEntity
import kotlinx.coroutines.launch

object TimerAlarmScheduler {
  private const val REQUEST_CODE = 3001

  fun computeNextTriggerElapsedMs(state: ActiveTimerStateEntity?): Long? {
    if (state == null) return null
    if (state.status != TimerStatus.RUNNING && state.status != TimerStatus.BREAK) {
      return null
    }

    val phaseEndElapsedMs: Long? =
      when {
        state.mode == TimerMode.POMODORO &&
          state.status == TimerStatus.RUNNING &&
          state.focusDurationMs > 0L -> state.phaseStartElapsedMs + state.focusDurationMs
        state.mode == TimerMode.POMODORO &&
          state.status == TimerStatus.BREAK &&
          state.breakDurationMs > 0L -> state.phaseStartElapsedMs + state.breakDurationMs
        else -> null
      }

    val goalReachElapsedMs: Long? =
      if (!state.goalNotified && state.status == TimerStatus.RUNNING && state.goalMs != null && state.goalMs > 0L) {
        val remainingFocusToGoal = (state.goalMs - state.accumulatedFocusMs).coerceAtLeast(0L)
        state.phaseStartElapsedMs + remainingFocusToGoal
      } else {
        null
      }

    return listOfNotNull(phaseEndElapsedMs, goalReachElapsedMs).minOrNull()
  }

  fun syncAlarm(context: Context, state: ActiveTimerStateEntity?) {
    val nextTriggerElapsedMs = computeNextTriggerElapsedMs(state)
    if (nextTriggerElapsedMs == null) {
      cancelAlarm(context)
      return
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pendingIntent = buildPendingIntent(context)
    runCatching { alarmManager.cancel(pendingIntent) }

    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
          alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            nextTriggerElapsedMs,
            pendingIntent,
          )
        } else {
          alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            nextTriggerElapsedMs,
            pendingIntent,
          )
        }
      } else {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.ELAPSED_REALTIME_WAKEUP,
          nextTriggerElapsedMs,
          pendingIntent,
        )
      }
    }
  }

  fun cancelAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    runCatching {
      alarmManager.cancel(buildPendingIntent(context))
    }
  }

  private fun buildPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, TimerAlarmReceiver::class.java)
    return PendingIntent.getBroadcast(
      context,
      REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}

class TimerAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val app = context.applicationContext as? SyllabusApplication ?: return
    val pendingResult: PendingResult? = goAsync()
    app.applicationScope.launch {
      try {
        app.timerRepository.checkAndMarkGoalAlertIfReached()
        app.timerRepository.advancePomodoroPhaseIfDue()
        val active = app.database.timerDao().getActiveTimerStateOnce()
        TimerAlarmScheduler.syncAlarm(app, active)
        if (active != null && (active.status == TimerStatus.RUNNING || active.status == TimerStatus.BREAK)) {
          TimerForegroundService.sync(app)
        }
      } finally {
        pendingResult?.finish()
      }
    }
  }
}
