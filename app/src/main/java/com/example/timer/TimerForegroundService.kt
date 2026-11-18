package com.example.timer

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.example.SyllabusApplication
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimerForegroundService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var boundaryWatcherJob: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    NotificationHelper.ensureNotificationChannel(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val app = applicationContext as? SyllabusApplication
    if (app == null) {
      stopSelf()
      return START_NOT_STICKY
    }

    val action = intent?.action ?: ACTION_SYNC
    serviceScope.launch {
      val repo = app.timerRepository
      if (action == ACTION_TOGGLE_PAUSE_RESUME) {
        repo.togglePauseResume()
      }

      val meta = app.loadActiveSessionMetadataOnce()
      if (meta == null) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        NotificationHelper.cancelOngoingTimerNotification(this@TimerForegroundService)
        stopSelf()
        return@launch
      }

      val nowElapsed = SystemClock.elapsedRealtime()
      val nowWall = System.currentTimeMillis()
      val metrics = TimerMath.computeMetrics(meta.state, nowElapsed, nowWall)
      val notification =
        NotificationHelper.buildOngoingTimerNotification(
          context = this@TimerForegroundService,
          state = meta.state,
          bookName = meta.bookName,
          chapterLabel = meta.chapterLabel,
          metrics = metrics,
          nowWallTimeMs = nowWall,
        )

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(
          NotificationHelper.TIMER_ONGOING_NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
      } else {
        startForeground(NotificationHelper.TIMER_ONGOING_NOTIFICATION_ID, notification)
      }

      scheduleNextBoundaryAndHeartbeat(app)
    }

    return START_STICKY
  }

  private fun scheduleNextBoundaryAndHeartbeat(app: SyllabusApplication) {
    boundaryWatcherJob?.cancel()
    boundaryWatcherJob =
      serviceScope.launch {
        while (true) {
          val meta = app.loadActiveSessionMetadataOnce() ?: break
          val state = meta.state
          val nowElapsed = SystemClock.elapsedRealtime()
          val nowWall = System.currentTimeMillis()
          val metrics = TimerMath.computeMetrics(state, nowElapsed, nowWall)

          val effectiveRunning =
            state.status == TimerStatus.RUNNING || state.status == TimerStatus.BREAK
          if (!effectiveRunning) {
            break
          }

          val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
          manager.notify(
            NotificationHelper.TIMER_ONGOING_NOTIFICATION_ID,
            NotificationHelper.buildOngoingTimerNotification(
              context = this@TimerForegroundService,
              state = state,
              bookName = meta.bookName,
              chapterLabel = meta.chapterLabel,
              metrics = metrics,
              nowWallTimeMs = nowWall,
            ),
          )

          var nextWaitMs = 30_000L
          if (state.mode == TimerMode.POMODORO && metrics.currentPhaseRemainingMs > 0L) {
            nextWaitMs = minOf(nextWaitMs, metrics.currentPhaseRemainingMs)
          }
          if (!state.goalNotified && state.status == TimerStatus.RUNNING && state.goalMs != null) {
            val remainingToGoal = (state.goalMs - metrics.activeFocusMs).coerceAtLeast(1L)
            nextWaitMs = minOf(nextWaitMs, remainingToGoal)
          }

          delay(nextWaitMs.coerceAtLeast(250L))

          app.timerRepository.touchLastKnownWallTime()
          app.timerRepository.checkAndMarkGoalAlertIfReached()
          app.timerRepository.advancePomodoroPhaseIfDue()
        }
      }
  }

  override fun onDestroy() {
    boundaryWatcherJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    const val ACTION_SYNC = "com.example.timer.ACTION_SYNC"
    const val ACTION_TOGGLE_PAUSE_RESUME = "com.example.timer.ACTION_TOGGLE_PAUSE_RESUME"
    const val ACTION_STOP_SERVICE = "com.example.timer.ACTION_STOP_SERVICE"

    fun sync(context: Context) {
      val intent =
        Intent(context, TimerForegroundService::class.java).apply {
          action = ACTION_SYNC
        }
      runCatching {
        context.startForegroundService(intent)
      }
    }

    fun stop(context: Context) {
      val intent = Intent(context, TimerForegroundService::class.java)
      runCatching {
        context.stopService(intent)
      }
      NotificationHelper.cancelOngoingTimerNotification(context)
    }
  }
}
