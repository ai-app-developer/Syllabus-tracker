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
import kotlinx.coroutines.launch

/**
 * Keeps the ongoing timer chronometer notification and recovery heartbeat alive.
 * Alarm delivery is handled strictly by the system AlarmManager + TimerAlarmReceiver.
 */
class TimerForegroundService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var stateObserverJob: Job? = null
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
              stopForeground(STOP_FOREGROUND_REMOVE)
              NotificationHelper.cancelOngoingTimerNotification(this@TimerForegroundService)
              stopSelf()
              return@collectLatest
            }
            val meta = app.loadActiveSessionMetadataOnce() ?: return@collectLatest
            updateOngoingNotification(meta)
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

  override fun onDestroy() {
    stateObserverJob?.cancel()
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
