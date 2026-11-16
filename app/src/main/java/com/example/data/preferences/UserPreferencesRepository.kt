package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "syllabus_prefs")

data class UserPreferences(
  val dailyGoal: Int = DEFAULT_DAILY_GOAL,
  val reminderEnabled: Boolean = true,
  val reminderHour: Int = DEFAULT_REMINDER_HOUR,
  val reminderMinute: Int = DEFAULT_REMINDER_MINUTE,
  val lastOpenedBookId: Long = -1L,
) {
  companion object {
    const val DEFAULT_DAILY_GOAL = 2
    const val DEFAULT_REMINDER_HOUR = 20 // 8:00 PM
    const val DEFAULT_REMINDER_MINUTE = 0
  }
}

class UserPreferencesRepository(private val context: Context) {
  private object Keys {
    val DAILY_GOAL = intPreferencesKey("daily_goal")
    val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
    val REMINDER_HOUR = intPreferencesKey("reminder_hour")
    val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    val LAST_OPENED_BOOK_ID = longPreferencesKey("last_opened_book_id")
  }

  val userPreferences: Flow<UserPreferences> =
    context.dataStore.data.map { prefs ->
      UserPreferences(
        dailyGoal = (prefs[Keys.DAILY_GOAL] ?: UserPreferences.DEFAULT_DAILY_GOAL).coerceIn(1, 50),
        reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: true,
        reminderHour = (prefs[Keys.REMINDER_HOUR] ?: UserPreferences.DEFAULT_REMINDER_HOUR).coerceIn(0, 23),
        reminderMinute =
          (prefs[Keys.REMINDER_MINUTE] ?: UserPreferences.DEFAULT_REMINDER_MINUTE).coerceIn(0, 59),
        lastOpenedBookId = prefs[Keys.LAST_OPENED_BOOK_ID] ?: -1L,
      )
    }

  suspend fun getCurrentPreferences(): UserPreferences = userPreferences.first()

  suspend fun setDailyGoal(goal: Int) {
    context.dataStore.edit { prefs -> prefs[Keys.DAILY_GOAL] = goal.coerceIn(1, 50) }
  }

  suspend fun setReminderEnabled(enabled: Boolean) {
    context.dataStore.edit { prefs -> prefs[Keys.REMINDER_ENABLED] = enabled }
  }

  suspend fun setReminderTime(hour: Int, minute: Int) {
    context.dataStore.edit { prefs ->
      prefs[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
      prefs[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
    }
  }

  suspend fun setLastOpenedBookId(bookId: Long) {
    context.dataStore.edit { prefs -> prefs[Keys.LAST_OPENED_BOOK_ID] = bookId }
  }
}
