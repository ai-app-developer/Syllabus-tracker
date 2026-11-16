package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.SyllabusApplication
import com.example.widgets.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BootCompletedReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    if (
      action == Intent.ACTION_BOOT_COMPLETED ||
        action == Intent.ACTION_MY_PACKAGE_REPLACED ||
        action == Intent.ACTION_DATE_CHANGED ||
        action == Intent.ACTION_TIME_CHANGED ||
        action == Intent.ACTION_TIMEZONE_CHANGED
    ) {
      val pendingResult = goAsync()
      val app = context.applicationContext as SyllabusApplication
      app.applicationScope.launch {
        try {
          withContext(Dispatchers.IO) {
            val prefs = app.preferencesRepository.getCurrentPreferences()
            if (prefs.reminderEnabled) {
              StudyReminderScheduler.scheduleDailyReminder(
                context = app,
                hour = prefs.reminderHour,
                minute = prefs.reminderMinute,
              )
            }
          }
          WidgetUpdater.refreshAll(app)
        } finally {
          pendingResult.finish()
        }
      }
    }
  }
}
