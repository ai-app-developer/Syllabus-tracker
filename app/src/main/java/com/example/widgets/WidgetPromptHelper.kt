package com.example.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.R
import com.example.data.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class HomeWidgetType(
  val key: String,
  val providerClass: Class<out AppWidgetProvider>,
  @StringRes val nameRes: Int,
  @StringRes val descRes: Int,
  @DrawableRes val previewRes: Int,
) {
  OVERVIEW(
    key = "overview",
    providerClass = OverviewWidgetReceiver::class.java,
    nameRes = R.string.widget_overview_name,
    descRes = R.string.widget_overview_desc,
    previewRes = R.drawable.widget_preview_overview,
  ),
  DARK_PROGRESS(
    key = "dark_progress",
    providerClass = DarkProgressWidgetReceiver::class.java,
    nameRes = R.string.widget_dark_progress_name,
    descRes = R.string.widget_dark_progress_desc,
    previewRes = R.drawable.widget_preview_dark_progress,
  ),
  TODAYS_PLAN(
    key = "todays_plan",
    providerClass = TodaysPlanWidgetReceiver::class.java,
    nameRes = R.string.widget_todays_plan_name,
    descRes = R.string.widget_todays_plan_desc,
    previewRes = R.drawable.widget_preview_todays_plan,
  ),
  DONE_RING(
    key = "done_ring",
    providerClass = DoneRingWidgetReceiver::class.java,
    nameRes = R.string.widget_done_ring_name,
    descRes = R.string.widget_done_ring_desc,
    previewRes = R.drawable.widget_preview_done_ring,
  ),
  CONTINUE_STUDYING(
    key = "continue_studying",
    providerClass = ContinueStudyingWidgetReceiver::class.java,
    nameRes = R.string.widget_continue_studying_name,
    descRes = R.string.widget_continue_studying_desc,
    previewRes = R.drawable.widget_preview_continue_studying,
  ),
}

enum class WidgetPinRequestResult {
  REQUEST_SENT,
  UNSUPPORTED_OR_FAILED,
}

object WidgetPromptScheduler {
  const val MAX_AUTO_APPEARANCES = 4
  const val DAY_MILLIS = 86_400_000L

  fun requiredCooldownMillis(shownCount: Int): Long {
    return when {
      shownCount <= 0 -> 0L
      shownCount == 1 -> 2L * DAY_MILLIS
      shownCount == 2 -> 7L * DAY_MILLIS
      shownCount == 3 -> 14L * DAY_MILLIS
      else -> Long.MAX_VALUE
    }
  }

  fun shouldShowWidgetPrompt(
    prefs: UserPreferences,
    anyWidgetPlaced: Boolean,
    nowMs: Long,
    isBlocked: Boolean,
  ): Boolean {
    if (isBlocked || anyWidgetPlaced || prefs.widgetEverAdded) return false
    val count = prefs.widgetPromptShownCount
    if (count >= MAX_AUTO_APPEARANCES) return false
    if (count <= 0) return true
    val elapsed = nowMs - prefs.widgetLastPromptTime
    return elapsed >= requiredCooldownMillis(count)
  }
}

object WidgetPinManager {
  private val _pinSuccessEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val pinSuccessEvents: SharedFlow<String> = _pinSuccessEvents.asSharedFlow()

  fun notifyPinSuccess(widgetKey: String) {
    _pinSuccessEvents.tryEmit(widgetKey)
  }

  fun getAllWidgetCounts(context: Context): Map<HomeWidgetType, Int> {
    val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return emptyMap()
    return HomeWidgetType.entries.associateWith { type ->
      runCatching {
        manager.getAppWidgetIds(ComponentName(context, type.providerClass)).size
      }.getOrDefault(0)
    }
  }

  fun requestPinWidget(context: Context, widgetType: HomeWidgetType): WidgetPinRequestResult {
    return runCatching {
      val manager = AppWidgetManager.getInstance(context) ?: return WidgetPinRequestResult.UNSUPPORTED_OR_FAILED
      if (!manager.isRequestPinAppWidgetSupported) {
        return WidgetPinRequestResult.UNSUPPORTED_OR_FAILED
      }
      val callbackIntent =
        Intent(context, WidgetActionReceiver::class.java).apply {
          action = WidgetIntents.ACTION_WIDGET_PIN_SUCCESS
          putExtra(WidgetIntents.EXTRA_WIDGET_KEY, widgetType.key)
        }
      val successCallback =
        PendingIntent.getBroadcast(
          context,
          500 + widgetType.ordinal,
          callbackIntent,
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
      val launched =
        manager.requestPinAppWidget(
          ComponentName(context, widgetType.providerClass),
          null,
          successCallback,
        )
      if (launched) {
        WidgetPinRequestResult.REQUEST_SENT
      } else {
        WidgetPinRequestResult.UNSUPPORTED_OR_FAILED
      }
    }.getOrDefault(WidgetPinRequestResult.UNSUPPORTED_OR_FAILED)
  }
}
