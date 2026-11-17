package com.example.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.R
import com.example.SyllabusApplication
import com.example.data.local.dao.WidgetSummaryRow
import kotlinx.coroutines.launch

class DarkProgressWidgetReceiver : AppWidgetProvider() {
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
      val views = RemoteViews(context.packageName, R.layout.widget_dark_progress)

      val progressPending =
        WidgetIntents.createActivityPendingIntent(
          context = context,
          requestCode = 200,
          action = WidgetIntents.ACTION_OPEN_PROGRESS,
        )
      val continuePending =
        if (summary.activeBookId != null) {
          WidgetIntents.createActivityPendingIntent(
            context = context,
            requestCode = 201,
            action = WidgetIntents.ACTION_OPEN_BOOK,
            bookId = summary.activeBookId,
          )
        } else {
          WidgetIntents.createActivityPendingIntent(
            context = context,
            requestCode = 201,
            action = WidgetIntents.ACTION_OPEN_HOME,
          )
        }
      views.setOnClickPendingIntent(R.id.widget_root, progressPending)
      views.setOnClickPendingIntent(R.id.btn_continue_tile, continuePending)

      if (summary.totalBooks == 0) {
        views.setTextViewText(R.id.tv_percent, "0%")
        views.setTextViewText(R.id.tv_subtitle, "Add a book to start")
        views.setProgressBar(R.id.progress_bar, 100, 0, false)
      } else {
        val percent = summary.overallPercentage.coerceIn(0, 100)
        views.setTextViewText(R.id.tv_percent, "$percent%")
        views.setTextViewText(R.id.tv_subtitle, "Syllabus Completed")
        views.setProgressBar(R.id.progress_bar, 100, percent, false)
      }

      return views
    }
  }
}
