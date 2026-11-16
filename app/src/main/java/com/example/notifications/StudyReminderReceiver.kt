package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.SyllabusApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StudyReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pendingResult = goAsync()
    val app = context.applicationContext as SyllabusApplication
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        val prefs = app.preferencesRepository.getCurrentPreferences()
        if (prefs.reminderEnabled) {
          val completedToday = app.syllabusRepository.getCompletedTodayCount()
          val totalChapters = app.syllabusRepository.getTotalChapterCount()
          NotificationHelper.showDailyStudyReminder(
            context = context,
            dailyGoal = prefs.dailyGoal,
            completedToday = completedToday,
            totalChapters = totalChapters,
          )
          StudyReminderScheduler.scheduleDailyReminder(
            context = context,
            hour = prefs.reminderHour,
            minute = prefs.reminderMinute,
            forceNextDay = true,
          )
        }
      } finally {
        pendingResult.finish()
      }
    }
  }
}
