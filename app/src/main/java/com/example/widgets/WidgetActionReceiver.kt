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
    if (intent.action != WidgetIntents.ACTION_COMPLETE_NEXT_CHAPTER) return

    val pendingResult: PendingResult? = goAsync()
    val app = context.applicationContext as SyllabusApplication
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
}
