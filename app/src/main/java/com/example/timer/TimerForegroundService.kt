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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs the PRIMARY exact-time in-service timer alarm and keeps the ongoing timer chronometer
 * notification & recovery heartbeat alive. System AlarmManager (TimerAlarmReceiver) serves
 * as the +2000ms BACKUP trigger via the same shared TimerAlarmPlayer.fireEvent() function.
 */
class TimerForegroundService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var stateObserverJob: Job? = null
  private var boundaryJob: Job? = null
  private var heartbeatJob: Job? = null

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

      startOngoingObservationAndHeartbeat(app)
    }

    return START_STICKY
  }

  private fun startOngoingObservationAndHeartbeat(app: SyllabusApplication) {
    if (stateObserverJob?.isActive != true) {
      stateObserverJob =
        serviceScope.launch {
          app.database.timerDao().observeActiveTimerState().collectLatest { state ->
            if (state == null) {
              boundaryJob?.cancel()
              stopForeground(STOP_FOREGROUND_REMOVE)
              NotificationHelper.cancelOngoingTimerNotification(this@TimerForegroundService)
              stopSelf()
              return@collectLatest
            }
            val meta = app.loadActiveSessionMetadataOnce() ?: return@collectLatest
            updateOngoingNotification(meta)
            schedulePrimaryBoundaryAlarm(meta.state)
          }
        }
    }

    if (heartbeatJob?.isActive != true) {
      heartbeatJob =
        serviceScope.launch {
          while (true) {
            delay(30_000L)
            val meta = app.loadActiveSessionMetadataOnce() ?: break
            if (meta.state.status == TimerStatus.RUNNING || meta.state.status == TimerStatus.BREAK) {
              app.timerRepository.touchLastKnownWallTime()
              updateOngoingNotification(meta)
            }
          }
        }
    }
  }

  private fun updateOngoingNotification(meta: com.example.data.repository.ActiveSessionMetadata) {
    val nowElapsed = SystemClock.elapsedRealtime()
    val nowWall = System.currentTimeMillis()
    val metrics = TimerMath.computeMetrics(meta.state, nowElapsed, nowWall)
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(
      NotificationHelper.TIMER_ONGOING_NOTIFICATION_ID,
      NotificationHelper.buildOngoingTimerNotification(
        context = this@TimerForegroundService,
        state = meta.state,
        bookName = meta.bookName,
        chapterLabel = meta.chapterLabel,
        metrics = metrics,
        nowWallTimeMs = nowWall,
      ),
    )
  }

  private fun schedulePrimaryBoundaryAlarm(state: com.example.data.local.entity.ActiveTimerStateEntity) {
    boundaryJob?.cancel()
    if (state.status != TimerStatus.RUNNING && state.status != TimerStatus.BREAK) {
      return
    }

    val nowElapsed = SystemClock.elapsedRealtime()
    val nowWall = System.currentTimeMillis()
    val targets =
      TimerAlarmScheduler.computeScheduledTargets(
        state = state,
        nowElapsedMs = nowElapsed,
        nowWallMs = nowWall,
        fromStoredWallTimesOnly = false,
      )
    if (targets.isEmpty()) return

    // Pick the earliest target; on exact ties, fire GOAL_REACHED before FOCUS_END
    // so both execute cleanly without BREAK blocking GOAL_REACHED.
    val nextTarget =
      targets.minWith(
        compareBy<ScheduledAlarmTarget> { it.triggerWallMs }
          .thenBy { if (it.eventType == TimerAlarmEventType.GOAL_REACHED) 0 else 1 }
      )
    val initialWaitMs = (nextTarget.triggerWallMs - nowWall).coerceAtLeast(0L)
    val targetElapsedMs = nowElapsed + initialWaitMs

    boundaryJob =
      serviceScope.launch {
        while (isActive) {
          val remainingMs = targetElapsedMs - SystemClock.elapsedRealtime()
          if (remainingMs <= 0L) break
          delay(remainingMs)
        }
        if (!isActive) return@launch
        TimerAlarmPlayer.fireEvent(
          context = this@TimerForegroundService,
          source = AlarmFireSource.PRIMARY,
          eventType = nextTarget.eventType,
          sessionId = state.sessionId,
          phaseIndex = state.pomodoroCycle,
          expectedEventWallMs = nextTarget.triggerWallMs,
        )
      }
  }

  override fun onDestroy() {
    stateObserverJob?.cancel()
    boundaryJob?.cancel()
    heartbeatJob?.cancel()
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
