package com.example.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WidgetUpdater {
  private const val UNIQUE_WORK_NAME = "syllabus_widget_periodic_refresh"

  suspend fun refreshAll(context: Context) {
    val appContext = context.applicationContext
    withContext(Dispatchers.Default) {
      coroutineScope {
        launch { runCatching { OverviewWidget().updateAll(appContext) } }
        launch { runCatching { DarkProgressWidget().updateAll(appContext) } }
        launch { runCatching { TodaysPlanWidget().updateAll(appContext) } }
        launch { runCatching { DoneRingWidget().updateAll(appContext) } }
        launch { runCatching { ContinueStudyingWidget().updateAll(appContext) } }
      }
    }
  }

  suspend fun updateAllWidgets(context: Context) {
    refreshAll(context)
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
