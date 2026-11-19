package com.example.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.BuildConfig
import com.example.SyllabusApplication
import com.example.data.local.dao.WidgetSummaryRow
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object WidgetUpdater {
  private const val TAG = "WidgetUpdater"
  private const val UNIQUE_WORK_NAME = "syllabus_widget_periodic_refresh"
  private const val COALESCE_WINDOW_MS = 100L

  private data class WidgetUpdateRequest(
    val app: Context,
    val force: Boolean,
    val precomputedSummary: WidgetSummaryRow?,
  )

  private val updateMutex = Mutex()
  private val updaterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val requestChannel = Channel<WidgetUpdateRequest>(Channel.CONFLATED)

  init {
    updaterScope.launch {
      for (first in requestChannel) {
        delay(COALESCE_WINDOW_MS)
        var latest = first
        while (true) {
          val next = requestChannel.tryReceive().getOrNull() ?: break
          latest =
            WidgetUpdateRequest(
              app = next.app,
              force = latest.force || next.force,
              precomputedSummary = next.precomputedSummary,
            )
        }
        runCatching {
          updateNow(
            context = latest.app,
            force = latest.force,
            precomputedSummary = latest.precomputedSummary,
          )
        }
      }
    }
  }

  @Volatile
  private var lastSummary: WidgetSummaryRow? = null

  fun clearLastSummary() {
    lastSummary = null
  }

  fun requestUpdate(
    context: Context,
    force: Boolean = false,
    precomputedSummary: WidgetSummaryRow? = null,
  ) {
    requestChannel.trySend(
      WidgetUpdateRequest(
        app = context.applicationContext,
        force = force,
        precomputedSummary = precomputedSummary,
      )
    )
  }

  suspend fun updateNow(
    context: Context,
    force: Boolean = false,
    precomputedSummary: WidgetSummaryRow? = null,
  ) {
    val startNs = System.nanoTime()
    updateMutex.withLock {
      performUpdateLocked(context, force, precomputedSummary, startNs)
    }
  }

  suspend fun updateNowIfIdle(context: Context): Boolean {
    if (!updateMutex.tryLock()) {
      return false
    }
    return try {
      val startNs = System.nanoTime()
      performUpdateLocked(context, force = false, precomputedSummary = null, startNs = startNs)
      true
    } finally {
      updateMutex.unlock()
    }
  }

  private suspend fun performUpdateLocked(
    context: Context,
    force: Boolean,
    precomputedSummary: WidgetSummaryRow?,
    startNs: Long,
  ) {
    val app = context.applicationContext as SyllabusApplication
    val appWidgetManager = AppWidgetManager.getInstance(app)

    val overviewIds =
      appWidgetManager.getAppWidgetIds(ComponentName(app, OverviewWidgetReceiver::class.java))
    val darkIds =
      appWidgetManager.getAppWidgetIds(ComponentName(app, DarkProgressWidgetReceiver::class.java))
    val planIds =
      appWidgetManager.getAppWidgetIds(ComponentName(app, TodaysPlanWidgetReceiver::class.java))
    val doneIds =
      appWidgetManager.getAppWidgetIds(ComponentName(app, DoneRingWidgetReceiver::class.java))
    val continueIds =
      appWidgetManager.getAppWidgetIds(
        ComponentName(app, ContinueStudyingWidgetReceiver::class.java)
      )

    if (
      overviewIds.isEmpty() &&
        darkIds.isEmpty() &&
        planIds.isEmpty() &&
        doneIds.isEmpty() &&
        continueIds.isEmpty()
    ) {
      if (BuildConfig.DEBUG) {
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        Log.d(TAG, "updateNow took $elapsedMs ms")
      }
      return
    }

    val summary =
      precomputedSummary
        ?: withContext(Dispatchers.IO) {
          val prefs = app.preferencesRepository.getCurrentPreferences()
          val zone = ZoneId.systemDefault()
          val today = LocalDate.now(zone)
          val startOfTodayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
          val endOfTodayMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
          app.database.syllabusDao().getWidgetSummaryRow(
            startOfTodayMillis = startOfTodayMillis,
            endOfTodayMillis = endOfTodayMillis,
            lastOpenedBookId = prefs.lastOpenedBookId,
            dailyGoal = prefs.dailyGoal.coerceAtLeast(1),
          )
        }

    if (!force && summary == lastSummary) {
      if (BuildConfig.DEBUG) {
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        Log.d(TAG, "updateNow took $elapsedMs ms")
      }
      return
    }

    if (overviewIds.isNotEmpty()) {
      appWidgetManager.updateAppWidget(
        overviewIds,
        OverviewWidgetReceiver.buildRemoteViews(app, summary),
      )
    }
    if (darkIds.isNotEmpty()) {
      appWidgetManager.updateAppWidget(
        darkIds,
        DarkProgressWidgetReceiver.buildRemoteViews(app, summary),
      )
    }
    if (planIds.isNotEmpty()) {
      appWidgetManager.updateAppWidget(
        planIds,
        TodaysPlanWidgetReceiver.buildRemoteViews(app, summary),
      )
    }
    if (doneIds.isNotEmpty()) {
      appWidgetManager.updateAppWidget(
        doneIds,
        DoneRingWidgetReceiver.buildRemoteViews(app, summary),
      )
    }
    if (continueIds.isNotEmpty()) {
      appWidgetManager.updateAppWidget(
        continueIds,
        ContinueStudyingWidgetReceiver.buildRemoteViews(app, summary),
      )
    }

    lastSummary = summary
    if (BuildConfig.DEBUG) {
      val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
      Log.d(TAG, "updateNow took $elapsedMs ms")
    }
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
