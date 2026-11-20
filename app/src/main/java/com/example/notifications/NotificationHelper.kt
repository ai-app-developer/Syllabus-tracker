package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
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
  const val TIMER_ALARM_CHANNEL_ID = "timer_alarm"

  private const val NOTIFICATION_ID = 1001
  const val TIMER_ONGOING_NOTIFICATION_ID = 2001
  const val TIMER_ALERT_PHASE_NOTIFICATION_ID = 2010
  const val TIMER_ALERT_GOAL_NOTIFICATION_ID = 2011

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

    if (manager.getNotificationChannel(TIMER_ALARM_CHANNEL_ID) == null) {
      val alarmSoundUri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
          ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val alarmAudioAttrs =
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      val alarmChannel =
        NotificationChannel(
            TIMER_ALARM_CHANNEL_ID,
            context.getString(R.string.timer_alerts_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
          )
          .apply {
            description = context.getString(R.string.timer_alerts_channel_desc)
            setSound(alarmSoundUri, alarmAudioAttrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 400L)
          }
      manager.createNotificationChannel(alarmChannel)
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
  }

  fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return false
    return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
  }

  fun isTimerAlarmChannelSoundAllowed(context: Context): Boolean {
    ensureNotificationChannel(context)
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
    val channel = manager.getNotificationChannel(TIMER_ALARM_CHANNEL_ID) ?: return false
    return channel.importance >= NotificationManager.IMPORTANCE_DEFAULT && channel.sound != null
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

  fun buildSyncingTimerNotification(context: Context): Notification {
    ensureNotificationChannel(context)
    return NotificationCompat.Builder(context, TIMER_ONGOING_CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification_book)
      .setContentTitle(context.getString(R.string.app_name))
      .setContentText("Syncing study timer...")
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setUsesChronometer(false)
      .setShowWhen(false)
      .build()
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

    val isPaused =
      state.status == TimerStatus.PAUSED || state.status == TimerStatus.STOP_SUMMARY
    val isBreak =
      when (state.status) {
        TimerStatus.BREAK -> true
        TimerStatus.PAUSED -> state.pausedFromStatus == TimerStatus.BREAK
        TimerStatus.STOP_SUMMARY ->
          state.statusBeforeStopSummary == TimerStatus.BREAK ||
            (state.statusBeforeStopSummary == TimerStatus.PAUSED &&
              state.pausedFromStatus == TimerStatus.BREAK)
        else -> false
      }

    val modeLabel = if (state.mode == TimerMode.POMODORO) "Pomodoro" else "Stopwatch"
    val frozenClockText =
      if (state.mode == TimerMode.POMODORO) {
        TimerMath.formatHms(metrics.currentPhaseRemainingMs)
      } else {
        TimerMath.formatHms(metrics.activeFocusMs)
      }
    val statusText =
      when {
        isPaused -> "Paused • $frozenClockText"
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

    val actionIntent =
      Intent(context, TimerForegroundService::class.java).apply {
        action = TimerForegroundService.ACTION_TOGGLE_PAUSE_RESUME
      }
    val actionPendingIntent =
      PendingIntent.getService(
        context,
        if (isPaused) 204 else 202,
        actionIntent,
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
          actionPendingIntent,
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
    notificationId: Int = TIMER_ALERT_PHASE_NOTIFICATION_ID,
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
        notificationId,
        openStudyingIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, TIMER_ALARM_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_book)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(contentPendingIntent)
        .setSilent(silent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
  }

  fun cancelOngoingTimerNotification(context: Context) {
    NotificationManagerCompat.from(context).cancel(TIMER_ONGOING_NOTIFICATION_ID)
  }
}
