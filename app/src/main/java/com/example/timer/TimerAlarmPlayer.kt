package com.example.timer

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.example.SyllabusApplication
import com.example.data.repository.AlarmOutcome
import com.example.notifications.NotificationHelper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

enum class AlarmFireSource(val logLabel: String) {
  PRIMARY("primary"),
  BACKUP("backup"),
}

interface TimerAlarmDeduplicator {
  fun tryAcquire(eventKey: String, sessionId: String): Boolean

  fun markSuppressed(eventKey: String, sessionId: String)

  fun clearEvent(eventKey: String, sessionId: String)

  fun clearSession(sessionId: String)

  fun clearAll()
}

class InMemoryAlarmDeduplicator : TimerAlarmDeduplicator {
  private val lock = Any()
  private var activeSessionId: String? = null
  private val keys = HashSet<String>()

  override fun tryAcquire(eventKey: String, sessionId: String): Boolean =
    synchronized(lock) {
      if (activeSessionId != sessionId) {
        activeSessionId = sessionId
        keys.clear()
      }
      keys.add(eventKey)
    }

  override fun markSuppressed(eventKey: String, sessionId: String) {
    synchronized(lock) {
      if (activeSessionId != sessionId) {
        activeSessionId = sessionId
        keys.clear()
      }
      keys.add(eventKey)
    }
  }

  override fun clearEvent(eventKey: String, sessionId: String) {
    synchronized(lock) {
      if (activeSessionId == sessionId) {
        keys.remove(eventKey)
      }
    }
  }

  override fun clearSession(sessionId: String) {
    synchronized(lock) {
      if (activeSessionId == sessionId) {
        keys.clear()
        activeSessionId = null
      }
    }
  }

  override fun clearAll() {
    synchronized(lock) {
      keys.clear()
      activeSessionId = null
    }
  }
}

class PrefsAlarmDeduplicator(context: Context) : TimerAlarmDeduplicator {
  private val prefs =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val lock = Any()

  override fun tryAcquire(eventKey: String, sessionId: String): Boolean =
    synchronized(lock) {
      val currentSet = loadKeysForSessionLocked(sessionId)
      if (currentSet.contains(eventKey)) {
        return@synchronized false
      }
      currentSet.add(eventKey)
      saveKeysLocked(sessionId, currentSet)
      true
    }

  override fun markSuppressed(eventKey: String, sessionId: String) {
    synchronized(lock) {
      val currentSet = loadKeysForSessionLocked(sessionId)
      if (currentSet.add(eventKey)) {
        saveKeysLocked(sessionId, currentSet)
      }
    }
  }

  override fun clearEvent(eventKey: String, sessionId: String) {
    synchronized(lock) {
      val storedSession = prefs.getString(KEY_SESSION_ID, null)
      if (storedSession == sessionId) {
        val currentSet = loadKeysForSessionLocked(sessionId)
        if (currentSet.remove(eventKey)) {
          saveKeysLocked(sessionId, currentSet)
        }
      }
    }
  }

  override fun clearSession(sessionId: String) {
    synchronized(lock) {
      val storedSession = prefs.getString(KEY_SESSION_ID, null)
      if (storedSession == sessionId) {
        prefs.edit().remove(KEY_SESSION_ID).remove(KEY_EVENT_KEYS).apply()
      }
    }
  }

  override fun clearAll() {
    synchronized(lock) {
      prefs.edit().clear().apply()
    }
  }

  private fun loadKeysForSessionLocked(sessionId: String): MutableSet<String> {
    val storedSession = prefs.getString(KEY_SESSION_ID, null)
    if (storedSession != sessionId) {
      return LinkedHashSet()
    }
    return prefs.getStringSet(KEY_EVENT_KEYS, emptySet())?.toMutableSet() ?: LinkedHashSet()
  }

  private fun saveKeysLocked(sessionId: String, keys: MutableSet<String>) {
    val bounded =
      if (keys.size > MAX_STORED_KEYS) {
        keys.toList().takeLast(MAX_STORED_KEYS).toSet()
      } else {
        keys.toSet()
      }
    prefs.edit().putString(KEY_SESSION_ID, sessionId).putStringSet(KEY_EVENT_KEYS, bounded).apply()
  }

  companion object {
    private const val PREFS_NAME = "timer_alarm_dedup"
    private const val KEY_SESSION_ID = "active_session_id"
    private const val KEY_EVENT_KEYS = "fired_event_keys"
    private const val MAX_STORED_KEYS = 48
  }
}

object TimerAlarmPlayer {
  private const val TAG = "TimerAlarm"
  const val MINI_ALARM_DURATION_MS = 3000L
  const val MINI_ALARM_VIBRATION_MS = 400L

  private val stopExecutor = Executors.newSingleThreadScheduledExecutor()
  private val lock = Any()

  private var activeRingtone: Ringtone? = null
  private var activeAudioManager: AudioManager? = null
  private var activeFocusRequest: AudioFocusRequest? = null
  private var pendingStopFuture: ScheduledFuture<*>? = null

  private val alarmAudioAttributes: AudioAttributes by lazy {
    AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_ALARM)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()
  }

  fun isBlockedByDoNotDisturb(notificationManager: NotificationManager?): Boolean {
    if (notificationManager == null) return false
    return isBlockedByDoNotDisturb(
      interruptionFilter = notificationManager.currentInterruptionFilter,
      priorityCategories = runCatching { notificationManager.notificationPolicy?.priorityCategories }.getOrNull(),
    )
  }

  fun isBlockedByDoNotDisturb(
    interruptionFilter: Int,
    priorityCategories: Int?,
  ): Boolean {
    return when (interruptionFilter) {
      NotificationManager.INTERRUPTION_FILTER_ALL,
      NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> false
      NotificationManager.INTERRUPTION_FILTER_NONE -> true
      NotificationManager.INTERRUPTION_FILTER_ALARMS -> false
      NotificationManager.INTERRUPTION_FILTER_PRIORITY -> {
        val categories = priorityCategories ?: return true
        (categories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) == 0
      }
      else -> true
    }
  }

  /**
   * Single shared alarm firing entry point used by both the primary in-service timer
   * and the backup TimerAlarmReceiver.
   */
  suspend fun fireEvent(
    context: Context,
    source: AlarmFireSource,
    eventType: TimerAlarmEventType,
    sessionId: String,
    phaseIndex: Int,
    expectedEventWallMs: Long,
  ): AlarmOutcome? {
    val app = context.applicationContext as? SyllabusApplication ?: return null
    val outcome =
      app.timerRepository.fireEvent(
        source = source,
        eventType = eventType,
        sessionId = sessionId,
        phaseIndex = phaseIndex,
        expectedEventWallMs = expectedEventWallMs,
      ) ?: return null

    if (BuildConfig.DEBUG) {
      Log.d(TAG, "fired by ${source.logLabel} ${eventType.name}, delay ${outcome.delayMs} ms")
    }

    val prefs = app.preferencesRepository.getCurrentPreferences()
    val isTooLate = outcome.delayMs > TimerAlarmScheduler.LATE_THRESHOLD_MS

    val soundStarted =
      if (!isTooLate && (prefs.timerAlarmEnabled || prefs.timerVibrationEnabled)) {
        playMiniAlarm(
          context = app,
          playSound = prefs.timerAlarmEnabled,
          playVibration = prefs.timerVibrationEnabled,
        )
      } else {
        false
      }

    val shouldUseChannelFallbackSound =
      !isTooLate && prefs.timerAlarmEnabled && !soundStarted

    NotificationHelper.showTimerAlert(
      context = app,
      title = outcome.title,
      message = outcome.message,
      notificationId = outcome.notificationId,
      silent = !shouldUseChannelFallbackSound,
    )
    return outcome
  }

  /**
   * Plays a short 3s Ringtone (USAGE_ALARM) and/or 400ms one-shot vibration.
   * Returns true if Ringtone audio started successfully.
   */
  fun playMiniAlarm(
    context: Context,
    playSound: Boolean,
    playVibration: Boolean,
  ): Boolean {
    if (!playSound && !playVibration) return false

    val appContext = context.applicationContext
    val notificationManager =
      appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    if (isBlockedByDoNotDisturb(notificationManager)) {
      if (BuildConfig.DEBUG) {
        Log.d(TAG, "Mini alarm suppressed by Do Not Disturb")
      }
      return false
    }

    if (playVibration) {
      triggerShortVibration(appContext)
    }

    return if (playSound) {
      triggerShortRingtone(appContext)
    } else {
      false
    }
  }

  fun stopAll() {
    synchronized(lock) {
      cleanupLocked()
    }
  }

  private fun triggerShortVibration(context: Context) {
    runCatching {
      val vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
          manager?.defaultVibrator
        } else {
          @Suppress("DEPRECATION")
          context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

      if (vibrator == null || !vibrator.hasVibrator()) return

      val effect =
        VibrationEffect.createOneShot(
          MINI_ALARM_VIBRATION_MS,
          VibrationEffect.DEFAULT_AMPLITUDE,
        )

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val attributes =
          VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_ALARM)
            .build()
        vibrator.vibrate(effect, attributes)
      } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(effect, alarmAudioAttributes)
      }
    }.onFailure { err ->
      Log.w(TAG, "Short vibration failed", err)
    }
  }

  private fun triggerShortRingtone(context: Context): Boolean {
    synchronized(lock) {
      cleanupLocked()

      val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      val focusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
          .setAudioAttributes(alarmAudioAttributes)
          .setAcceptsDelayedFocusGain(false)
          .setOnAudioFocusChangeListener {}
          .build()

      if (audioManager != null) {
        runCatching { audioManager.requestAudioFocus(focusRequest) }
        activeAudioManager = audioManager
        activeFocusRequest = focusRequest
      }

      val candidates = resolveCandidateUris(context)
      var startedRingtone: Ringtone? = null
      for (uri in candidates) {
        val played =
          runCatching {
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return@runCatching null
            ringtone.audioAttributes = alarmAudioAttributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
              ringtone.isLooping = false
            }
            ringtone.play()
            ringtone
          }.getOrNull()
        if (played != null) {
          startedRingtone = played
          break
        }
      }

      if (startedRingtone == null) {
        cleanupLocked()
        return false
      }

      activeRingtone = startedRingtone
      pendingStopFuture =
        stopExecutor.schedule(
          {
            synchronized(lock) {
              cleanupLocked()
            }
          },
          MINI_ALARM_DURATION_MS,
          TimeUnit.MILLISECONDS,
        )
      return true
    }
  }

  private fun resolveCandidateUris(context: Context): List<Uri> {
    val list = ArrayList<Uri>(3)
    runCatching { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) }.getOrNull()?.let {
      list.add(it)
    }
    runCatching { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) }.getOrNull()?.let {
      list.add(it)
    }
    list.add(Uri.parse("android.resource://${context.packageName}/${R.raw.timer_alarm_beep}"))
    return list
  }

  private fun cleanupLocked() {
    pendingStopFuture?.cancel(false)
    pendingStopFuture = null

    activeRingtone?.let { ringtone ->
      runCatching { ringtone.stop() }
    }
    activeRingtone = null

    val am = activeAudioManager
    val req = activeFocusRequest
    if (am != null && req != null) {
      runCatching { am.abandonAudioFocusRequest(req) }
    }
    activeAudioManager = null
    activeFocusRequest = null
  }
}

typealias AlarmPlayer = TimerAlarmPlayer
