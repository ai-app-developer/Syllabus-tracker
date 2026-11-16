package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object StudyReminderScheduler {
  private const val REQUEST_CODE = 2001

  fun scheduleDailyReminder(
    context: Context,
    hour: Int,
    minute: Int,
    forceNextDay: Boolean = false,
  ) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = buildPendingIntent(context)
    val triggerAtMillis = computeNextTriggerMillis(hour, minute, forceNextDay)

    alarmManager.cancel(pendingIntent)
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
  }

  fun cancelReminder(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(buildPendingIntent(context))
  }

  internal fun computeNextTriggerMillis(
    hour: Int,
    minute: Int,
    forceNextDay: Boolean = false,
    now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
  ): Long {
    val targetTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    var scheduled = ZonedDateTime.of(LocalDate.from(now), targetTime, now.zone)
    if (forceNextDay || !scheduled.isAfter(now)) {
      scheduled = scheduled.plusDays(1)
    }
    return scheduled.toInstant().toEpochMilli()
  }

  private fun buildPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, StudyReminderReceiver::class.java)
    return PendingIntent.getBroadcast(
      context,
      REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}
