package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.SyllabusApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    if (
      action == Intent.ACTION_BOOT_COMPLETED ||
        action == Intent.ACTION_MY_PACKAGE_REPLACED ||
        action == Intent.ACTION_TIME_CHANGED ||
        action == Intent.ACTION_TIMEZONE_CHANGED
    ) {
      val pendingResult = goAsync()
      val app = context.applicationContext as SyllabusApplication
      CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
          val prefs = app.preferencesRepository.getCurrentPreferences()
          if (prefs.reminderEnabled) {
            StudyReminderScheduler.scheduleDailyReminder(
              context = context,
              hour = prefs.reminderHour,
              minute = prefs.reminderMinute,
            )
          }
        } finally {
          pendingResult.finish()
        }
      }
    }
  }
}
