package com.example.notifications

import android.Manifest
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

object NotificationHelper {
  const val CHANNEL_ID = "syllabus_study_reminders"
  private const val NOTIFICATION_ID = 1001

  fun ensureNotificationChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val existing = manager.getNotificationChannel(CHANNEL_ID)
    if (existing == null) {
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
}
