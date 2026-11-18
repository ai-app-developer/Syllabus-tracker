package com.example.timer

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log

interface TimerAlarmDeduplicator {
  fun tryAcquire(eventKey: String, sessionId: String): Boolean

  fun markSuppressed(eventKey: String, sessionId: String)

  fun clearSession(sessionId: String)
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

  override fun clearSession(sessionId: String) {
    synchronized(lock) {
      if (activeSessionId == sessionId) {
        keys.clear()
        activeSessionId = null
      }
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

  override fun clearSession(sessionId: String) {
    synchronized(lock) {
      val storedSession = prefs.getString(KEY_SESSION_ID, null)
      if (storedSession == sessionId) {
        prefs.edit().remove(KEY_SESSION_ID).remove(KEY_EVENT_KEYS).apply()
      }
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
  private const val TAG = "TimerAlarmPlayer"
  const val MINI_ALARM_DURATION_MS = 2800L
  const val MINI_ALARM_VIBRATION_MS = 400L

  private val mainHandler = Handler(Looper.getMainLooper())
  private val lock = Any()

  private var activePlayer: MediaPlayer? = null
  private var activeToneGenerator: ToneGenerator? = null
  private var activeAudioManager: AudioManager? = null
  private var activeFocusRequest: AudioFocusRequest? = null
  private var pendingStopRunnable: Runnable? = null

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

  fun playMiniAlarm(
    context: Context,
    playSound: Boolean,
    playVibration: Boolean,
  ) {
    if (!playSound && !playVibration) return

    val appContext = context.applicationContext
    val notificationManager =
      appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    if (isBlockedByDoNotDisturb(notificationManager)) {
      Log.d(TAG, "Mini alarm suppressed by Do Not Disturb")
      return
    }

    if (playVibration) {
      triggerShortVibration(appContext)
    }
    if (playSound) {
      triggerShortSound(appContext)
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

  private fun triggerShortSound(context: Context) {
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
        runCatching {
          audioManager.requestAudioFocus(focusRequest)
        }
        activeAudioManager = audioManager
        activeFocusRequest = focusRequest
      }

      val soundUri = resolveGentleAlarmUri()
      val startedMedia =
        if (soundUri != null) {
          startMediaPlayerLocked(context, soundUri)
        } else {
          false
        }

      if (!startedMedia) {
        startFallbackToneLocked()
      }

      val stopTask = Runnable {
        synchronized(lock) {
          cleanupLocked()
        }
      }
      pendingStopRunnable = stopTask
      mainHandler.postDelayed(stopTask, MINI_ALARM_DURATION_MS)
    }
  }

  private fun startMediaPlayerLocked(context: Context, uri: Uri): Boolean {
    return runCatching {
      val player = MediaPlayer()
      player.setAudioAttributes(alarmAudioAttributes)
      player.isLooping = false
      player.setDataSource(context, uri)
      player.setOnCompletionListener {
        synchronized(lock) {
          cleanupLocked()
        }
      }
      player.setOnErrorListener { _, _, _ ->
        synchronized(lock) {
          cleanupLocked()
        }
        true
      }
      player.prepare()
      player.start()
      activePlayer = player
      true
    }.getOrElse { err ->
      Log.w(TAG, "MediaPlayer failed for $uri, using short fallback tone", err)
      false
    }
  }

  private fun startFallbackToneLocked() {
    runCatching {
      val generator = ToneGenerator(AudioManager.STREAM_ALARM, 75)
      generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
      activeToneGenerator = generator
    }.onFailure { err ->
      Log.w(TAG, "Fallback ToneGenerator failed", err)
    }
  }

  private fun resolveGentleAlarmUri(): Uri? {
    return runCatching {
      RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: Settings.System.DEFAULT_NOTIFICATION_URI
    }.getOrNull()
  }

  private fun cleanupLocked() {
    pendingStopRunnable?.let { mainHandler.removeCallbacks(it) }
    pendingStopRunnable = null

    activePlayer?.let { player ->
      runCatching {
        if (player.isPlaying) {
          player.stop()
        }
      }
      runCatching { player.reset() }
      runCatching { player.release() }
    }
    activePlayer = null

    activeToneGenerator?.let { generator ->
      runCatching { generator.stopTone() }
      runCatching { generator.release() }
    }
    activeToneGenerator = null

    val am = activeAudioManager
    val req = activeFocusRequest
    if (am != null && req != null) {
      runCatching { am.abandonAudioFocusRequest(req) }
    }
    activeAudioManager = null
    activeFocusRequest = null
  }
}
