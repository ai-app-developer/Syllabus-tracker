package com.example

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.example.data.local.SyllabusDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ActiveSessionMetadata
import com.example.data.repository.SyllabusRepository
import com.example.data.repository.TimerRepository
import com.example.notifications.NotificationHelper
import com.example.notifications.StudyReminderScheduler
import com.example.timer.TimerForegroundService
import com.example.widgets.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

class SyllabusApplication : Application() {
  val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val database: SyllabusDatabase by lazy { SyllabusDatabase.getInstance(this) }
  val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
  val syllabusRepository: SyllabusRepository by lazy {
    SyllabusRepository(
      dao = database.syllabusDao(),
      prefsRepo = preferencesRepository,
      runInTransaction = { block -> database.withTransaction { block() } },
      onDataChanged = { WidgetUpdater.updateNow(this@SyllabusApplication) },
    )
  }

  val timerRepository: TimerRepository by lazy {
    TimerRepository(
      timerDao = database.timerDao(),
      syllabusDao = database.syllabusDao(),
      runInTransaction = { block -> database.withTransaction { block() } },
      onSyncService = { TimerForegroundService.sync(this@SyllabusApplication) },
      onStopService = { TimerForegroundService.stop(this@SyllabusApplication) },
      onTimerAlert = { _, title, message ->
        NotificationHelper.showTimerAlert(this@SyllabusApplication, title, message)
      },
    )
  }

  suspend fun loadActiveSessionMetadataOnce(): ActiveSessionMetadata? {
    val active = database.timerDao().getActiveTimerStateOnce() ?: return null
    val book = database.syllabusDao().getBookByIdOnce(active.bookId)
    val chapter =
      database.syllabusDao().getAllChaptersOnce().firstOrNull { it.id == active.chapterId }
    return ActiveSessionMetadata(
      state = active,
      bookName = book?.name ?: "Study Session",
      chapterName = chapter?.name ?: "Chapter",
      chapterNumber = chapter?.number ?: 1,
    )
  }

  private val systemClockReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        val pendingResult: PendingResult? = goAsync()
        applicationScope.launch {
          try {
            WidgetUpdater.updateNow(this@SyllabusApplication, force = true)
          } finally {
            pendingResult?.finish()
          }
        }
      }
    }

  override fun onCreate() {
    super.onCreate()
    NotificationHelper.ensureNotificationChannel(this)
    WidgetUpdater.schedulePeriodicRefresh(this)

    applicationScope.launch(Dispatchers.IO) {
      timerRepository.checkUnfinishedSessionOnLaunch()
    }

    applicationScope.launch {
      WidgetUpdater.updateNow(this@SyllabusApplication)
    }

    applicationScope.launch {
      syllabusRepository.observeWidgetSummaryRow()
        .conflate()
        .collect { summaryRow ->
          WidgetUpdater.updateNow(
            context = this@SyllabusApplication,
            precomputedSummary = summaryRow,
          )
        }
    }

    val clockFilter =
      IntentFilter().apply {
        addAction(Intent.ACTION_DATE_CHANGED)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
      }
    runCatching {
      ContextCompat.registerReceiver(
        this,
        systemClockReceiver,
        clockFilter,
        ContextCompat.RECEIVER_NOT_EXPORTED,
      )
    }

    applicationScope.launch(Dispatchers.IO) {
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
