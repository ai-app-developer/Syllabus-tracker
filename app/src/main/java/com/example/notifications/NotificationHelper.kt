package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.entity.ActiveTimerStateEntity
import com.example.timer.ComputedTimerMetrics
import com.example.timer.TimerForegroundService
import com.example.timer.TimerMath
import com.example.timer.TimerMode
import com.example.timer.TimerStatus

object NotificationHelper {
  const val CHANNEL_ID = "syllabus_study_reminders"
  const val TIMER_ONGOING_CHANNEL_ID = "syllabus_timer_ongoing"
  const val TIMER_ALERTS_CHANNEL_ID = "syllabus_timer_alerts"

  private const val NOTIFICATION_ID = 1001
  const val TIMER_ONGOING_NOTIFICATION_ID = 2001
  private const val TIMER_ALERT_NOTIFICATION_ID = 2002

  const val EXTRA_TIMER_ACTION = "extra_timer_action"
  const val TIMER_ACTION_OPEN_STUDYING = "com.example.action.OPEN_STUDYING"
  const val TIMER_ACTION_STOP_SUMMARY = "com.example.action.STOP_SUMMARY"

  fun ensureNotificationChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
      val channel =
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
          )
          .apply {
            description = context.getString(R.string.notification_channel_desc)
          }
      manager.createNotificationChannel(channel)
    }

    if (manager.getNotificationChannel(TIMER_ONGOING_CHANNEL_ID) == null) {
      val timerChannel =
        NotificationChannel(
            TIMER_ONGOING_CHANNEL_ID,
            context.getString(R.string.timer_ongoing_channel_name),
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply {
            description = context.getString(R.string.timer_ongoing_channel_desc)
            setShowBadge(false)
          }
      manager.createNotificationChannel(timerChannel)
    }

    if (manager.getNotificationChannel(TIMER_ALERTS_CHANNEL_ID) == null) {
      val alertsChannel =
        NotificationChannel(
            TIMER_ALERTS_CHANNEL_ID,
            context.getString(R.string.timer_alerts_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
          )
          .apply {
            description = context.getString(R.string.timer_alerts_channel_desc)
          }
      manager.createNotificationChannel(alertsChannel)
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun showDailyStudyReminder(
    context: Context,
    dailyGoal: Int,
    completedToday: Int,
    totalChapters: Int,
  ) {
    if (!hasNotificationPermission(context)) return
    ensureNotificationChannel(context)

    val remainingToday = (dailyGoal - completedToday).coerceAtLeast(0)
    val message =
      when {
        totalChapters == 0 -> "Add a book and chapters to start tracking your syllabus."
        remainingToday == 0 ->
          "You finished $completedToday ${if (completedToday == 1) "chapter" else "chapters"} today. Great consistency!"
        remainingToday == 1 -> "1 chapter left today. Keep going!"
        else -> "$remainingToday chapters left today. Keep going!"
      }

    val openAppIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val contentPendingIntent =
      PendingIntent.getActivity(
        context,
        0,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_book)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(contentPendingIntent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
  }

  fun buildOngoingTimerNotification(
    context: Context,
    state: ActiveTimerStateEntity,
    bookName: String,
    chapterLabel: String,
    metrics: ComputedTimerMetrics,
    nowWallTimeMs: Long = System.currentTimeMillis(),
  ): Notification {
    ensureNotificationChannel(context)

    val effectiveStatus =
      if (state.status == TimerStatus.STOP_SUMMARY) {
        state.statusBeforeStopSummary ?: TimerStatus.PAUSED
      } else {
        state.status
      }

    val isPaused = effectiveStatus == TimerStatus.PAUSED || state.status == TimerStatus.STOP_SUMMARY
    val isBreak = effectiveStatus == TimerStatus.BREAK

    val modeLabel = if (state.mode == TimerMode.POMODORO) "Pomodoro" else "Stopwatch"
    val statusText =
      when {
        isPaused -> "Paused • ${TimerMath.formatHms(metrics.activeFocusMs)}"
        isBreak -> "Break • Cycle ${state.pomodoroCycle}"
        else -> "Studying • $modeLabel"
      }

    val openStudyingIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_TIMER_ACTION, TIMER_ACTION_OPEN_STUDYING)
      }
    val openPendingIntent =
      PendingIntent.getActivity(
        context,
        201,
        openStudyingIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val toggleServiceIntent =
      Intent(context, TimerForegroundService::class.java).apply {
        action = TimerForegroundService.ACTION_TOGGLE_PAUSE_RESUME
      }
    val togglePendingIntent =
      PendingIntent.getService(
        context,
        202,
        toggleServiceIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_TIMER_ACTION, TIMER_ACTION_STOP_SUMMARY)
      }
    val stopPendingIntent =
      PendingIntent.getActivity(
        context,
        203,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val builder =
      NotificationCompat.Builder(context, TIMER_ONGOING_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_book)
        .setContentTitle("$bookName — $chapterLabel")
        .setContentText(statusText)
        .setSubText(modeLabel)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setContentIntent(openPendingIntent)
        .addAction(
          0,
          if (isPaused) "Resume" else "Pause",
          togglePendingIntent,
        )
        .addAction(
          0,
          "Stop",
          stopPendingIntent,
        )

    if (!isPaused) {
      if (state.mode == TimerMode.POMODORO) {
        builder.setUsesChronometer(true)
        builder.setChronometerCountDown(true)
        builder.setWhen(nowWallTimeMs + metrics.currentPhaseRemainingMs)
      } else {
        builder.setUsesChronometer(true)
        builder.setChronometerCountDown(false)
        builder.setWhen(nowWallTimeMs - metrics.activeFocusMs)
      }
    } else {
      builder.setUsesChronometer(false)
      builder.setShowWhen(false)
    }

    return builder.build()
  }

  fun showTimerAlert(
    context: Context,
    title: String,
    message: String,
    silent: Boolean = false,
  ) {
    if (!hasNotificationPermission(context)) return
    ensureNotificationChannel(context)

    val openStudyingIntent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_TIMER_ACTION, TIMER_ACTION_OPEN_STUDYING)
      }
    val contentPendingIntent =
      PendingIntent.getActivity(
        context,
        204,
        openStudyingIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, TIMER_ALERTS_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_book)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(contentPendingIntent)
        .setSilent(silent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(TIMER_ALERT_NOTIFICATION_ID, notification)
  }

  fun cancelOngoingTimerNotification(context: Context) {
    NotificationManagerCompat.from(context).cancel(TIMER_ONGOING_NOTIFICATION_ID)
  }
}
