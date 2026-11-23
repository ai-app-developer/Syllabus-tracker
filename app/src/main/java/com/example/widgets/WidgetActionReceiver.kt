package com.example.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.SyllabusApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    val app = context.applicationContext as SyllabusApplication

    when (action) {
      WidgetIntents.ACTION_COMPLETE_NEXT_CHAPTER -> {
        val pendingResult: PendingResult? = goAsync()
        app.applicationScope.launch {
          try {
            val completed =
              withContext(Dispatchers.IO) {
                app.syllabusRepository.completeNextIncompleteChapterFromWidget()
              }
            if (completed) {
              WidgetUpdater.updateNow(app, force = true)
            } else {
              val openPlan =
                WidgetIntents.createActivityIntent(app, WidgetIntents.ACTION_OPEN_TODAYS_PLAN)
              app.startActivity(openPlan)
            }
          } finally {
            pendingResult?.finish()
          }
        }
      }

      WidgetIntents.ACTION_WIDGET_PIN_SUCCESS -> {
        val widgetKey = intent.getStringExtra(WidgetIntents.EXTRA_WIDGET_KEY).orEmpty()
        val pendingResult: PendingResult? = goAsync()
        app.applicationScope.launch {
          try {
            withContext(Dispatchers.IO) {
              app.preferencesRepository.markWidgetEverAdded()
            }
            WidgetUpdater.updateNow(app, force = true)
            WidgetPinManager.notifyPinSuccess(widgetKey)
          } finally {
            pendingResult?.finish()
          }
        }
      }
    }
  }
}
