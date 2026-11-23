package com.example.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.MainActivity

object WidgetIntents {
  const val EXTRA_WIDGET_ACTION = "widget_action"
  const val EXTRA_BOOK_ID = "book_id"

  const val ACTION_OPEN_HOME = "com.example.WIDGET_OPEN_HOME"
  const val ACTION_OPEN_ADD_CHAPTER = "com.example.WIDGET_OPEN_ADD_CHAPTER"
  const val ACTION_OPEN_BOOK = "com.example.WIDGET_OPEN_BOOK"
  const val ACTION_OPEN_TODAYS_PLAN = "com.example.WIDGET_OPEN_TODAYS_PLAN"
  const val ACTION_OPEN_PROGRESS = "com.example.WIDGET_OPEN_PROGRESS"

  const val ACTION_COMPLETE_NEXT_CHAPTER = "com.example.WIDGET_COMPLETE_NEXT_CHAPTER"
  const val ACTION_WIDGET_PIN_SUCCESS = "com.example.WIDGET_PIN_SUCCESS"
  const val EXTRA_WIDGET_KEY = "widget_key"

  private const val PENDING_FLAGS =
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

  fun createActivityIntent(
    context: Context,
    action: String,
    bookId: Long? = null,
  ): Intent {
    return Intent(context, MainActivity::class.java).apply {
      this.action = if (bookId != null) "${action}_$bookId" else action
      putExtra(EXTRA_WIDGET_ACTION, action)
      if (bookId != null) {
        putExtra(EXTRA_BOOK_ID, bookId)
      }
      flags =
        Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TOP or
          Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
  }

  fun createActivityPendingIntent(
    context: Context,
    requestCode: Int,
    action: String,
    bookId: Long? = null,
  ): PendingIntent {
    val intent = createActivityIntent(context, action, bookId)
    return PendingIntent.getActivity(context, requestCode, intent, PENDING_FLAGS)
  }

  fun createCompleteNextChapterPendingIntent(
    context: Context,
    requestCode: Int,
  ): PendingIntent {
    val intent =
      Intent(context, WidgetActionReceiver::class.java).apply {
        action = ACTION_COMPLETE_NEXT_CHAPTER
      }
    return PendingIntent.getBroadcast(context, requestCode, intent, PENDING_FLAGS)
  }
}
