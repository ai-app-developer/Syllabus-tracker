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

class ContinueStudyingWidgetReceiver : AppWidgetProvider() {
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
      val views = RemoteViews(context.packageName, R.layout.widget_continue_studying)

      val clickPending =
        if (summary.activeBookId != null) {
          WidgetIntents.createActivityPendingIntent(
            context = context,
            requestCode = 500,
            action = WidgetIntents.ACTION_OPEN_BOOK,
            bookId = summary.activeBookId,
          )
        } else {
          WidgetIntents.createActivityPendingIntent(
            context = context,
            requestCode = 500,
            action = WidgetIntents.ACTION_OPEN_HOME,
          )
        }
      views.setOnClickPendingIntent(R.id.widget_root, clickPending)

      if (summary.activeBookId == null) {
        views.setViewVisibility(R.id.empty_container, View.VISIBLE)
        views.setViewVisibility(R.id.content_container, View.GONE)
      } else {
        val bookPercent = summary.activeBookPercent.coerceIn(0, 100)
        val subtitle =
          when {
            summary.nextChapterNumber != null && summary.nextChapterName != null ->
              "Chapter ${summary.nextChapterNumber}: ${summary.nextChapterName}"
            summary.activeBookTotalChapters > 0 ->
              "All ${summary.activeBookTotalChapters} chapters completed"
            else -> "No chapters yet"
          }
        views.setViewVisibility(R.id.empty_container, View.GONE)
        views.setViewVisibility(R.id.content_container, View.VISIBLE)
        views.setTextViewText(R.id.tv_book_name, summary.activeBookName.orEmpty())
        views.setTextViewText(R.id.tv_chapter_subtitle, subtitle)
        views.setProgressBar(R.id.progress_bar, 100, bookPercent, false)
        views.setTextViewText(R.id.tv_book_percent, "$bookPercent%")
      }

      return views
    }
  }
}
