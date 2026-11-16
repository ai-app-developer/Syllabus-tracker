package com.example.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WidgetUpdater {
  private const val UNIQUE_WORK_NAME = "syllabus_widget_periodic_refresh"

  suspend fun updateAllWidgets(context: Context) {
    val appContext = context.applicationContext
    runCatching { OverviewWidget().updateAll(appContext) }
    runCatching { DarkProgressWidget().updateAll(appContext) }
    runCatching { TodaysPlanWidget().updateAll(appContext) }
    runCatching { DoneRingWidget().updateAll(appContext) }
    runCatching { ContinueStudyingWidget().updateAll(appContext) }
  }

  fun schedulePeriodicRefresh(context: Context) {
    val appContext = context.applicationContext
    synchronized(this) {
      if (!WorkManager.isInitialized()) {
        runCatching {
          WorkManager.initialize(appContext, Configuration.Builder().build())
        }
      }
    }
    runCatching {
      val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
      WorkManager.getInstance(appContext)
        .enqueueUniquePeriodicWork(
          UNIQUE_WORK_NAME,
          ExistingPeriodicWorkPolicy.KEEP,
          request,
        )
    }
  }
}
