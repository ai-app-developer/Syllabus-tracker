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

  private val syncPrefs =
    context.applicationContext.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)

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
      .onEach { loaded ->
        cachedPrefs = loaded
        persistSyncMirror(loaded)
      }

  fun getCachedPreferencesSnapshot(): UserPreferences {
    cachedPrefs?.let { return it }
    val loaded = readFromSyncMirror()
    cachedPrefs = loaded
    return loaded
  }

  suspend fun getCurrentPreferences(): UserPreferences =
    cachedPrefs ?: userPreferences.first().also {
      cachedPrefs = it
      persistSyncMirror(it)
    }

  suspend fun setDailyGoal(goal: Int) {
    val clamped = goal.coerceIn(1, 50)
    val base = getCachedPreferencesSnapshot()
    val updated = base.copy(dailyGoal = clamped)
    cachedPrefs = updated
    persistSyncMirror(updated)
    context.dataStore.edit { prefs -> prefs[Keys.DAILY_GOAL] = clamped }
  }

  suspend fun setReminderEnabled(enabled: Boolean) {
    val updated = getCachedPreferencesSnapshot().copy(reminderEnabled = enabled)
    cachedPrefs = updated
    persistSyncMirror(updated)
    context.dataStore.edit { prefs -> prefs[Keys.REMINDER_ENABLED] = enabled }
  }

  suspend fun setReminderTime(hour: Int, minute: Int) {
    val h = hour.coerceIn(0, 23)
    val m = minute.coerceIn(0, 59)
    val updated = getCachedPreferencesSnapshot().copy(reminderHour = h, reminderMinute = m)
    cachedPrefs = updated
    persistSyncMirror(updated)
    context.dataStore.edit { prefs ->
      prefs[Keys.REMINDER_HOUR] = h
      prefs[Keys.REMINDER_MINUTE] = m
    }
  }

  suspend fun setLastOpenedBookId(bookId: Long) {
    val updated = getCachedPreferencesSnapshot().copy(lastOpenedBookId = bookId)
    cachedPrefs = updated
    persistSyncMirror(updated)
    context.dataStore.edit { prefs -> prefs[Keys.LAST_OPENED_BOOK_ID] = bookId }
  }

  suspend fun setTimerAlarmEnabled(enabled: Boolean) {
    val updated = getCachedPreferencesSnapshot().copy(timerAlarmEnabled = enabled)
    cachedPrefs = updated
    persistSyncMirror(updated)
    context.dataStore.edit { prefs -> prefs[Keys.TIMER_ALARM_ENABLED] = enabled }
  }

  suspend fun setTimerVibrationEnabled(enabled: Boolean) {
    val updated = getCachedPreferencesSnapshot().copy(timerVibrationEnabled = enabled)
    cachedPrefs = updated
    persistSyncMirror(updated)
    context.dataStore.edit { prefs -> prefs[Keys.TIMER_VIBRATION_ENABLED] = enabled }
  }

  suspend fun setTimerPermissionChecklistShown(shown: Boolean) {
    val updated = getCachedPreferencesSnapshot().copy(timerPermissionChecklistShown = shown)
    cachedPrefs = updated
    persistSyncMirror(updated)
    context.dataStore.edit { prefs -> prefs[Keys.TIMER_PERMISSION_CHECKLIST_SHOWN] = shown }
  }

  suspend fun setPomodoroDurations(focusMinutes: Int, breakMinutes: Int) {
    val focus = focusMinutes.coerceIn(1, 180)
    val brk = breakMinutes.coerceIn(1, 60)
    val updated =
      getCachedPreferencesSnapshot().copy(pomodoroFocusMinutes = focus, pomodoroBreakMinutes = brk)
    cachedPrefs = updated
    persistSyncMirror(updated)
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
    persistSyncMirror(sanitized)
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

  private fun readFromSyncMirror(): UserPreferences {
    return UserPreferences(
      dailyGoal = syncPrefs.getInt(SYNC_DAILY_GOAL, UserPreferences.DEFAULT_DAILY_GOAL).coerceIn(1, 50),
      reminderEnabled = syncPrefs.getBoolean(SYNC_REMINDER_ENABLED, true),
      reminderHour = syncPrefs.getInt(SYNC_REMINDER_HOUR, UserPreferences.DEFAULT_REMINDER_HOUR).coerceIn(0, 23),
      reminderMinute = syncPrefs.getInt(SYNC_REMINDER_MINUTE, UserPreferences.DEFAULT_REMINDER_MINUTE).coerceIn(0, 59),
      lastOpenedBookId = syncPrefs.getLong(SYNC_LAST_OPENED_BOOK_ID, -1L),
      timerAlarmEnabled = syncPrefs.getBoolean(SYNC_TIMER_ALARM_ENABLED, true),
      timerVibrationEnabled = syncPrefs.getBoolean(SYNC_TIMER_VIBRATION_ENABLED, true),
      timerPermissionChecklistShown = syncPrefs.getBoolean(SYNC_CHECKLIST_SHOWN, false),
      pomodoroFocusMinutes = syncPrefs.getInt(SYNC_POMO_FOCUS, UserPreferences.DEFAULT_POMODORO_FOCUS_MINUTES).coerceIn(1, 180),
      pomodoroBreakMinutes = syncPrefs.getInt(SYNC_POMO_BREAK, UserPreferences.DEFAULT_POMODORO_BREAK_MINUTES).coerceIn(1, 60),
    )
  }

  private fun persistSyncMirror(prefs: UserPreferences) {
    syncPrefs.edit()
      .putInt(SYNC_DAILY_GOAL, prefs.dailyGoal)
      .putBoolean(SYNC_REMINDER_ENABLED, prefs.reminderEnabled)
      .putInt(SYNC_REMINDER_HOUR, prefs.reminderHour)
      .putInt(SYNC_REMINDER_MINUTE, prefs.reminderMinute)
      .putLong(SYNC_LAST_OPENED_BOOK_ID, prefs.lastOpenedBookId)
      .putBoolean(SYNC_TIMER_ALARM_ENABLED, prefs.timerAlarmEnabled)
      .putBoolean(SYNC_TIMER_VIBRATION_ENABLED, prefs.timerVibrationEnabled)
      .putBoolean(SYNC_CHECKLIST_SHOWN, prefs.timerPermissionChecklistShown)
      .putInt(SYNC_POMO_FOCUS, prefs.pomodoroFocusMinutes)
      .putInt(SYNC_POMO_BREAK, prefs.pomodoroBreakMinutes)
      .apply()
  }

  companion object {
    private const val SYNC_PREFS_NAME = "timer_prefs_sync"
    private const val SYNC_DAILY_GOAL = "daily_goal"
    private const val SYNC_REMINDER_ENABLED = "reminder_enabled"
    private const val SYNC_REMINDER_HOUR = "reminder_hour"
    private const val SYNC_REMINDER_MINUTE = "reminder_minute"
    private const val SYNC_LAST_OPENED_BOOK_ID = "last_opened_book_id"
    private const val SYNC_TIMER_ALARM_ENABLED = "timer_alarm_enabled"
    private const val SYNC_TIMER_VIBRATION_ENABLED = "timer_vibration_enabled"
    private const val SYNC_CHECKLIST_SHOWN = "timer_permission_checklist_shown"
    private const val SYNC_POMO_FOCUS = "pomodoro_focus_minutes"
    private const val SYNC_POMO_BREAK = "pomodoro_break_minutes"
  }
}
