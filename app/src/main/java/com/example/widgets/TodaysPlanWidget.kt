package com.example.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.SyllabusApplication
import com.example.data.local.dao.WidgetSummaryRow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class TodaysPlanWidgetReceiver : AppWidgetProvider() {
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
      val views = RemoteViews(context.packageName, R.layout.widget_todays_plan)

      val planPending =
        WidgetIntents.createActivityPendingIntent(
          context = context,
          requestCode = 300,
          action = WidgetIntents.ACTION_OPEN_TODAYS_PLAN,
        )
      views.setOnClickPendingIntent(R.id.widget_root, planPending)

      if (summary.totalBooks == 0) {
        views.setViewVisibility(R.id.empty_view, View.VISIBLE)
        views.setViewVisibility(R.id.content_container, View.GONE)
      } else {
        val dailyGoal = summary.dailyGoal.coerceAtLeast(1)
        val completedToday = summary.completedToday
        val goalReached = completedToday >= dailyGoal
        val progressPercent =
          ((completedToday.toFloat() / dailyGoal.toFloat()) * 100f).roundToInt().coerceIn(0, 100)

        views.setViewVisibility(R.id.empty_view, View.GONE)
        views.setViewVisibility(R.id.content_container, View.VISIBLE)
        views.setImageViewResource(
          R.id.iv_goal_check,
          if (goalReached) R.drawable.ic_widget_check_circle else R.drawable.ic_widget_circle_outline,
        )
        views.setTextViewText(
          R.id.tv_goal_text,
          "Complete $dailyGoal ${if (dailyGoal == 1) "chapter" else "chapters"}",
        )
        views.setTextViewText(R.id.tv_ratio, "$completedToday/$dailyGoal")

        if (goalReached) {
          views.setViewVisibility(R.id.progress_bar_indigo, View.GONE)
          views.setViewVisibility(R.id.progress_bar_green, View.VISIBLE)
          views.setProgressBar(R.id.progress_bar_green, 100, progressPercent, false)
        } else {
          views.setViewVisibility(R.id.progress_bar_indigo, View.VISIBLE)
          views.setViewVisibility(R.id.progress_bar_green, View.GONE)
          views.setProgressBar(R.id.progress_bar_indigo, 100, progressPercent, false)
        }

        val togglePending =
          WidgetIntents.createCompleteNextChapterPendingIntent(
            context = context,
            requestCode = 301,
          )
        views.setOnClickPendingIntent(R.id.row_goal_toggle, togglePending)
      }

      return views
    }
  }
}
