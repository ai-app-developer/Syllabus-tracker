package com.example.data.preferences

import android.content.Context
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.onEach

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "syllabus_prefs")

@Immutable
data class UserPreferences(
  val dailyGoal: Int = DEFAULT_DAILY_GOAL,
  val reminderEnabled: Boolean = true,
  val reminderHour: Int = DEFAULT_REMINDER_HOUR,
  val reminderMinute: Int = DEFAULT_REMINDER_MINUTE,
  val lastOpenedBookId: Long = -1L,
  val timerAlarmEnabled: Boolean = true,
  val timerVibrationEnabled: Boolean = true,
  val timerPermissionChecklistShown: Boolean = false,
  val pomodoroFocusMinutes: Int = DEFAULT_POMODORO_FOCUS_MINUTES,
  val pomodoroBreakMinutes: Int = DEFAULT_POMODORO_BREAK_MINUTES,
) {
  companion object {
    const val DEFAULT_DAILY_GOAL = 2
    const val DEFAULT_REMINDER_HOUR = 20 // 8:00 PM
    const val DEFAULT_REMINDER_MINUTE = 0
    const val DEFAULT_POMODORO_FOCUS_MINUTES = 25
    const val DEFAULT_POMODORO_BREAK_MINUTES = 5
  }
}

class UserPreferencesRepository(private val context: Context) {
  private object Keys {
    val DAILY_GOAL = intPreferencesKey("daily_goal")
    val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
    val REMINDER_HOUR = intPreferencesKey("reminder_hour")
    val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    val LAST_OPENED_BOOK_ID = longPreferencesKey("last_opened_book_id")
    val TIMER_ALARM_ENABLED = booleanPreferencesKey("timer_alarm_enabled")
    val TIMER_VIBRATION_ENABLED = booleanPreferencesKey("timer_vibration_enabled")
    val TIMER_PERMISSION_CHECKLIST_SHOWN = booleanPreferencesKey("timer_permission_checklist_shown")
    val POMODORO_FOCUS_MINUTES = intPreferencesKey("pomodoro_focus_minutes")
    val POMODORO_BREAK_MINUTES = intPreferencesKey("pomodoro_break_minutes")
  }

  @Volatile
  private var cachedPrefs: UserPreferences? = null

  val userPreferences: Flow<UserPreferences> =
    context.dataStore.data
      .map { prefs ->
        UserPreferences(
          dailyGoal = (prefs[Keys.DAILY_GOAL] ?: UserPreferences.DEFAULT_DAILY_GOAL).coerceIn(1, 50),
          reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: true,
          reminderHour =
            (prefs[Keys.REMINDER_HOUR] ?: UserPreferences.DEFAULT_REMINDER_HOUR).coerceIn(0, 23),
          reminderMinute =
            (prefs[Keys.REMINDER_MINUTE] ?: UserPreferences.DEFAULT_REMINDER_MINUTE).coerceIn(0, 59),
          lastOpenedBookId = prefs[Keys.LAST_OPENED_BOOK_ID] ?: -1L,
          timerAlarmEnabled = prefs[Keys.TIMER_ALARM_ENABLED] ?: true,
          timerVibrationEnabled = prefs[Keys.TIMER_VIBRATION_ENABLED] ?: true,
          timerPermissionChecklistShown = prefs[Keys.TIMER_PERMISSION_CHECKLIST_SHOWN] ?: false,
          pomodoroFocusMinutes =
            (prefs[Keys.POMODORO_FOCUS_MINUTES] ?: UserPreferences.DEFAULT_POMODORO_FOCUS_MINUTES)
              .coerceIn(1, 180),
          pomodoroBreakMinutes =
            (prefs[Keys.POMODORO_BREAK_MINUTES] ?: UserPreferences.DEFAULT_POMODORO_BREAK_MINUTES)
              .coerceIn(1, 60),
        )
      }
      .onEach { cachedPrefs = it }

  fun getCachedPreferencesSnapshot(): UserPreferences = cachedPrefs ?: UserPreferences()

  suspend fun getCurrentPreferences(): UserPreferences =
    cachedPrefs ?: userPreferences.first().also { cachedPrefs = it }

  suspend fun setDailyGoal(goal: Int) {
    val clamped = goal.coerceIn(1, 50)
    cachedPrefs = cachedPrefs?.copy(dailyGoal = clamped)
    context.dataStore.edit { prefs -> prefs[Keys.DAILY_GOAL] = clamped }
  }

  suspend fun setReminderEnabled(enabled: Boolean) {
    cachedPrefs = cachedPrefs?.copy(reminderEnabled = enabled)
    context.dataStore.edit { prefs -> prefs[Keys.REMINDER_ENABLED] = enabled }
  }

  suspend fun setReminderTime(hour: Int, minute: Int) {
    val h = hour.coerceIn(0, 23)
    val m = minute.coerceIn(0, 59)
    cachedPrefs = cachedPrefs?.copy(reminderHour = h, reminderMinute = m)
    context.dataStore.edit { prefs ->
      prefs[Keys.REMINDER_HOUR] = h
      prefs[Keys.REMINDER_MINUTE] = m
    }
  }

  suspend fun setLastOpenedBookId(bookId: Long) {
    cachedPrefs = cachedPrefs?.copy(lastOpenedBookId = bookId)
    context.dataStore.edit { prefs -> prefs[Keys.LAST_OPENED_BOOK_ID] = bookId }
  }

  suspend fun setTimerAlarmEnabled(enabled: Boolean) {
    cachedPrefs = cachedPrefs?.copy(timerAlarmEnabled = enabled)
    context.dataStore.edit { prefs -> prefs[Keys.TIMER_ALARM_ENABLED] = enabled }
  }

  suspend fun setTimerVibrationEnabled(enabled: Boolean) {
    cachedPrefs = cachedPrefs?.copy(timerVibrationEnabled = enabled)
    context.dataStore.edit { prefs -> prefs[Keys.TIMER_VIBRATION_ENABLED] = enabled }
  }

  suspend fun setTimerPermissionChecklistShown(shown: Boolean) {
    cachedPrefs = cachedPrefs?.copy(timerPermissionChecklistShown = shown)
    context.dataStore.edit { prefs -> prefs[Keys.TIMER_PERMISSION_CHECKLIST_SHOWN] = shown }
  }

  suspend fun setPomodoroDurations(focusMinutes: Int, breakMinutes: Int) {
    val focus = focusMinutes.coerceIn(1, 180)
    val brk = breakMinutes.coerceIn(1, 60)
    cachedPrefs =
      cachedPrefs?.copy(pomodoroFocusMinutes = focus, pomodoroBreakMinutes = brk)
    context.dataStore.edit { prefs ->
      prefs[Keys.POMODORO_FOCUS_MINUTES] = focus
      prefs[Keys.POMODORO_BREAK_MINUTES] = brk
    }
  }

  suspend fun restorePreferences(target: UserPreferences): UserPreferences {
    val sanitized =
      target.copy(
        dailyGoal = target.dailyGoal.coerceIn(1, 50),
        reminderHour = target.reminderHour.coerceIn(0, 23),
        reminderMinute = target.reminderMinute.coerceIn(0, 59),
        pomodoroFocusMinutes = target.pomodoroFocusMinutes.coerceIn(1, 180),
        pomodoroBreakMinutes = target.pomodoroBreakMinutes.coerceIn(1, 60),
      )
    cachedPrefs = sanitized
    context.dataStore.edit { prefs ->
      prefs[Keys.DAILY_GOAL] = sanitized.dailyGoal
      prefs[Keys.REMINDER_ENABLED] = sanitized.reminderEnabled
      prefs[Keys.REMINDER_HOUR] = sanitized.reminderHour
      prefs[Keys.REMINDER_MINUTE] = sanitized.reminderMinute
      prefs[Keys.LAST_OPENED_BOOK_ID] = sanitized.lastOpenedBookId
      prefs[Keys.TIMER_ALARM_ENABLED] = sanitized.timerAlarmEnabled
      prefs[Keys.TIMER_VIBRATION_ENABLED] = sanitized.timerVibrationEnabled
      prefs[Keys.TIMER_PERMISSION_CHECKLIST_SHOWN] = sanitized.timerPermissionChecklistShown
      prefs[Keys.POMODORO_FOCUS_MINUTES] = sanitized.pomodoroFocusMinutes
      prefs[Keys.POMODORO_BREAK_MINUTES] = sanitized.pomodoroBreakMinutes
    }
    return sanitized
  }
}
