package com.example.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.SyllabusApplication
import com.example.data.local.dao.WidgetSummaryRow
import kotlinx.coroutines.launch

class DoneRingWidgetReceiver : AppWidgetProvider() {
  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    WidgetUpdater.schedulePeriodicRefresh(context)
  }

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    val pendingResult: PendingResult? = goAsync()
    val app = context.applicationContext as SyllabusApplication
    app.applicationScope.launch {
      try {
        WidgetUpdater.updateNow(app, force = true)
      } finally {
        pendingResult?.finish()
      }
    }
  }

  companion object {
    fun buildRemoteViews(context: Context, summary: WidgetSummaryRow): RemoteViews {
      val views = RemoteViews(context.packageName, R.layout.widget_done_ring)

      val progressPending =
        WidgetIntents.createActivityPendingIntent(
          context = context,
          requestCode = 400,
          action = WidgetIntents.ACTION_OPEN_PROGRESS,
        )
      views.setOnClickPendingIntent(R.id.widget_root, progressPending)

      if (summary.totalBooks == 0) {
        views.setViewVisibility(R.id.empty_container, View.VISIBLE)
        views.setViewVisibility(R.id.content_container, View.GONE)
      } else {
        val percent = summary.overallPercentage.coerceIn(0, 100)
        views.setViewVisibility(R.id.empty_container, View.GONE)
        views.setViewVisibility(R.id.content_container, View.VISIBLE)
        views.setProgressBar(R.id.progress_ring, 100, percent, false)
        views.setTextViewText(R.id.tv_percent, "$percent%")
      }

      return views
    }
  }
}
