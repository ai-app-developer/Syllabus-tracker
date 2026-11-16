package com.example

import android.app.Application
import com.example.data.local.SyllabusDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.SyllabusRepository
import com.example.notifications.NotificationHelper
import com.example.notifications.StudyReminderScheduler
import com.example.widgets.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SyllabusApplication : Application() {
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val database: SyllabusDatabase by lazy { SyllabusDatabase.getInstance(this) }
  val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
  val syllabusRepository: SyllabusRepository by lazy {
    SyllabusRepository(
      dao = database.syllabusDao(),
      prefsRepo = preferencesRepository,
      onDataChanged = { WidgetUpdater.updateAllWidgets(this@SyllabusApplication) },
    )
  }

  override fun onCreate() {
    super.onCreate()
    NotificationHelper.ensureNotificationChannel(this)
    WidgetUpdater.schedulePeriodicRefresh(this)
    appScope.launch {
      val prefs = preferencesRepository.getCurrentPreferences()
      if (prefs.reminderEnabled) {
        StudyReminderScheduler.scheduleDailyReminder(
          context = this@SyllabusApplication,
          hour = prefs.reminderHour,
          minute = prefs.reminderMinute,
        )
      }
    }
  }
}
