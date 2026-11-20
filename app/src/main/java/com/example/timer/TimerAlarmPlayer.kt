package com.example.timer

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Immutable
import com.example.R
import com.example.SyllabusApplication
import com.example.data.repository.AlarmOutcome
import com.example.notifications.NotificationHelper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

enum class AlarmFireSource(val logLabel: String) {
  PRIMARY("primary"),
  BACKUP("backup"),
  TEST("test"),
}

interface TimerAlarmDeduplicator {
  fun isMarked(eventKey: String, sessionId: String): Boolean

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

  override fun isMarked(eventKey: String, sessionId: String): Boolean =
    synchronized(lock) { activeSessionId == sessionId && keys.contains(eventKey) }

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

  override fun isMarked(eventKey: String, sessionId: String): Boolean =
    synchronized(lock) { loadKeysForSessionLocked(sessionId).contains(eventKey) }

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

@Immutable
data class AlarmDiagnosticEntry(
  val timestampMs: Long,
  val eventType: String,
  val source: String,
  val outcome: String,
  val detail: String = "",
)

/**
 * Stores the last 20 alarm events in SharedPreferences (works in both debug & release builds)
 * so alarm delivery can be inspected directly from Settings without a Room schema change.
 */
object AlarmDiagnosticLog {
  private const val PREFS_NAME = "timer_alarm_diag_log"
  private const val KEY_ENTRIES = "entries_json"
  const val MAX_ENTRIES = 20

  const val OUTCOME_PLAYED = "played"
  const val OUTCOME_SKIPPED_DUPLICATE = "skipped duplicate"
  const val OUTCOME_SKIPPED_LATE = "skipped late"
  const val OUTCOME_NO_PERMISSION = "no permission"
  const val OUTCOME_SOUND_FAILED = "sound failed"
  const val OUTCOME_SUPPRESSED_DND = "suppressed dnd"

  private val lock = Any()

  fun record(
    context: Context,
    eventType: String,
    source: String,
    outcome: String,
    detail: String = "",
    timestampMs: Long = System.currentTimeMillis(),
  ) {
    val appContext = context.applicationContext
    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    synchronized(lock) {
      val list = readLocked(prefs).toMutableList()
      list.add(
        0,
        AlarmDiagnosticEntry(
          timestampMs = timestampMs,
          eventType = eventType,
          source = source,
          outcome = outcome,
          detail = detail,
        ),
      )
      val trimmed = if (list.size > MAX_ENTRIES) list.take(MAX_ENTRIES) else list
      writeLocked(prefs, trimmed)
    }
    Log.i(
      "TimerAlarm",
      "diag source=$source event=$eventType outcome=$outcome${if (detail.isNotBlank()) " detail=$detail" else ""}",
    )
  }

  fun getEntries(context: Context): List<AlarmDiagnosticEntry> {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    synchronized(lock) {
      return readLocked(prefs)
    }
  }

  fun clear(context: Context) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    synchronized(lock) {
      prefs.edit().remove(KEY_ENTRIES).apply()
    }
  }

  private fun readLocked(prefs: android.content.SharedPreferences): List<AlarmDiagnosticEntry> {
    val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
    return runCatching {
      val arr = JSONArray(raw)
      val out = ArrayList<AlarmDiagnosticEntry>(arr.length())
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        out.add(
          AlarmDiagnosticEntry(
            timestampMs = obj.optLong("t", 0L),
            eventType = obj.optString("e", ""),
            source = obj.optString("s", ""),
            outcome = obj.optString("o", ""),
            detail = obj.optString("d", ""),
          )
        )
      }
      out
    }.getOrDefault(emptyList())
  }

  private fun writeLocked(
    prefs: android.content.SharedPreferences,
    entries: List<AlarmDiagnosticEntry>,
  ) {
    val arr = JSONArray()
    for (entry in entries) {
      arr.put(
        JSONObject()
          .put("t", entry.timestampMs)
          .put("e", entry.eventType)
          .put("s", entry.source)
          .put("o", entry.outcome)
          .put("d", entry.detail)
      )
    }
    prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
  }
}

@Immutable
data class SoundAttemptResult(
  val started: Boolean,
  val method: String,
)

@Immutable
data class AlarmTestReport(
  val soundStarted: Boolean,
  val soundMethod: String,
  val vibrationTriggered: Boolean,
  val notificationPosted: Boolean,
  val notificationPermissionGranted: Boolean,
  val alarmVolumePercent: Int,
  val dndBlocked: Boolean,
  val dndStatusLabel: String,
  val exactAlarmAllowed: Boolean,
  val channelEnabled: Boolean,
)

internal interface AlarmAudioBackend {
  suspend fun playSound(context: Context): SoundAttemptResult

  fun playVibration(context: Context): Boolean

  fun stopAll()
}

object TimerAlarmPlayer {
  private const val TAG = "TimerAlarm"
  const val MINI_ALARM_DURATION_MS = 3000L
  const val MINI_ALARM_VIBRATION_MS = 400L

  const val METHOD_MEDIA_PLAYER_RAW = "MediaPlayer (raw)"
  const val METHOD_RINGTONE_ALARM = "Ringtone (TYPE_ALARM)"
  const val METHOD_RINGTONE_NOTIFICATION = "Ringtone (TYPE_NOTIFICATION)"
  const val METHOD_CHANNEL_FALLBACK = "Channel fallback"
  const val METHOD_NONE = "None"

  private const val TEST_DEDUP_PREFS = "timer_alarm_test_dedup"
  private const val KEY_TEST_IDS = "fired_test_ids"

  private val testMutex = Mutex()
  private val stopExecutor = Executors.newSingleThreadScheduledExecutor()
  private val audioLock = Any()

  private var activeMediaPlayer: MediaPlayer? = null
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

  private val defaultBackend: AlarmAudioBackend =
    object : AlarmAudioBackend {
      override suspend fun playSound(context: Context): SoundAttemptResult {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        synchronized(audioLock) {
          cleanupLocked()
          if (audioManager != null) {
            val focusRequest =
              AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(alarmAudioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener {}
                .build()
            runCatching { audioManager.requestAudioFocus(focusRequest) }
            activeAudioManager = audioManager
            activeFocusRequest = focusRequest
          }
        }

        // 1. Primary: Bundled res/raw/timer_alarm_beep via MediaPlayer
        if (tryPlayRawMediaPlayer(appContext)) {
          return SoundAttemptResult(started = true, method = METHOD_MEDIA_PLAYER_RAW)
        }

        // 2. Fallback 1: RingtoneManager.TYPE_ALARM (verified playing)
        val alarmUri = runCatching { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) }.getOrNull()
        if (alarmUri != null && tryPlayVerifiedRingtone(appContext, alarmUri)) {
          return SoundAttemptResult(started = true, method = METHOD_RINGTONE_ALARM)
        }

        // 3. Fallback 2: RingtoneManager.TYPE_NOTIFICATION (verified playing)
        val notifUri =
          runCatching { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) }.getOrNull()
        if (notifUri != null && tryPlayVerifiedRingtone(appContext, notifUri)) {
          return SoundAttemptResult(started = true, method = METHOD_RINGTONE_NOTIFICATION)
        }

        synchronized(audioLock) {
          cleanupLocked()
        }
        return SoundAttemptResult(started = false, method = METHOD_NONE)
      }

      override fun playVibration(context: Context): Boolean = triggerShortVibration(context)

      override fun stopAll() {
        synchronized(audioLock) {
          cleanupLocked()
        }
      }
    }

  @Volatile
  private var audioBackend: AlarmAudioBackend = defaultBackend

  @VisibleForTesting
  internal fun setAudioBackendForTest(backend: AlarmAudioBackend?) {
    audioBackend = backend ?: defaultBackend
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

  fun getDndStatusLabel(notificationManager: NotificationManager?): String {
    if (notificationManager == null) return "Allowed"
    return when (notificationManager.currentInterruptionFilter) {
      NotificationManager.INTERRUPTION_FILTER_ALL,
      NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> "Off (Allowed)"
      NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms only (Allowed)"
      NotificationManager.INTERRUPTION_FILTER_NONE -> "Total silence (Blocked)"
      NotificationManager.INTERRUPTION_FILTER_PRIORITY ->
        if (isBlockedByDoNotDisturb(notificationManager)) {
          "Priority DND (Alarms blocked)"
        } else {
          "Priority DND (Alarms allowed)"
        }
      else -> "Blocked by DND"
    }
  }

  fun getAlarmVolumePercent(context: Context): Int {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 100
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    if (max <= 0) return 0
    val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM).coerceIn(0, max)
    return (current * 100) / max
  }

  /**
   * Single shared alarm firing entry point used by both the primary in-service timer
   * and the backup TimerAlarmReceiver.
   *
   * Reads preferences synchronously before touching repo state, executes audio/notification
   * before committing the fired mark, and wraps execution in NonCancellable on applicationScope
   * so Room state updates cancelling boundaryJob can never abort an in-flight alarm.
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
    val prefs = app.preferencesRepository.getCachedPreferencesSnapshot()

    return withContext(NonCancellable) {
      val fireJob =
        app.applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {} // ensure scope valid
      fireJob.cancel()

      val outcome =
        app.timerRepository.fireEvent(
          source = source,
          eventType = eventType,
          sessionId = sessionId,
          phaseIndex = phaseIndex,
          expectedEventWallMs = expectedEventWallMs,
          onBeforeCommitAttempt = { delayMs, title, message, notificationId ->
            deliverAlarmAlert(
              context = app,
              eventLabel = eventType.name,
              sourceLabel = source.logLabel,
              delayMs = delayMs,
              title = title,
              message = message,
              notificationId = notificationId,
              playSound = prefs.timerAlarmEnabled,
              playVibration = prefs.timerVibrationEnabled,
            )
          },
        )

      if (outcome == null) {
        AlarmDiagnosticLog.record(
          context = app,
          eventType = eventType.name,
          source = source.logLabel,
          outcome = AlarmDiagnosticLog.OUTCOME_SKIPPED_DUPLICATE,
          detail = "session=$sessionId cycle=$phaseIndex",
        )
      } else {
        Log.i(
          TAG,
          "fired by ${source.logLabel} ${eventType.name}, delay ${outcome.delayMs} ms",
        )
      }
      outcome
    }
  }

  /**
   * Shared delivery path for real timer events and Test Alarm / Test-in-10s alarms.
   */
  internal suspend fun deliverAlarmAlert(
    context: Context,
    eventLabel: String,
    sourceLabel: String,
    delayMs: Long,
    title: String,
    message: String,
    notificationId: Int,
    playSound: Boolean,
    playVibration: Boolean,
  ): AlarmTestReport {
    val appContext = context.applicationContext
    val notificationManager =
      appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    val dndBlocked = isBlockedByDoNotDisturb(notificationManager)
    val dndLabel = getDndStatusLabel(notificationManager)
    val alarmVolumePercent = getAlarmVolumePercent(appContext)
    val notifPermissionGranted = NotificationHelper.hasNotificationPermission(appContext)
    val exactAlarmAllowed = TimerAlarmScheduler.canScheduleExactAlarms(appContext)
    val channelEnabled = NotificationHelper.isTimerAlarmChannelEnabled(appContext)
    val isTooLate = delayMs > TimerAlarmScheduler.LATE_THRESHOLD_MS

    var vibrationTriggered = false
    var soundResult = SoundAttemptResult(started = false, method = METHOD_NONE)
    var usedChannelFallback = false

    when {
      isTooLate -> {
        soundResult = SoundAttemptResult(false, "Skipped (>${TimerAlarmScheduler.LATE_THRESHOLD_MS / 1000}s late)")
      }
      dndBlocked -> {
        soundResult = SoundAttemptResult(false, "Suppressed (Do Not Disturb)")
      }
      else -> {
        if (playVibration) {
          vibrationTriggered = audioBackend.playVibration(appContext)
        }
        if (playSound) {
          soundResult = audioBackend.playSound(appContext)
          if (!soundResult.started) {
            usedChannelFallback = true
          }
        } else {
          soundResult = SoundAttemptResult(false, "Disabled in settings")
        }
      }
    }

    val shouldUseChannelSound = !isTooLate && !dndBlocked && playSound && !soundResult.started
    if (notifPermissionGranted) {
      NotificationHelper.showTimerAlert(
        context = appContext,
        title = title,
        message = message,
        notificationId = notificationId,
        silent = !shouldUseChannelSound,
      )
    }

    val effectiveMethod =
      when {
        soundResult.started -> soundResult.method
        usedChannelFallback && channelEnabled && notifPermissionGranted -> METHOD_CHANNEL_FALLBACK
        usedChannelFallback -> "Failed (channel/permission disabled)"
        else -> soundResult.method
      }

    val diagOutcome =
      when {
        isTooLate -> AlarmDiagnosticLog.OUTCOME_SKIPPED_LATE
        dndBlocked -> AlarmDiagnosticLog.OUTCOME_SUPPRESSED_DND
        !notifPermissionGranted && !soundResult.started -> AlarmDiagnosticLog.OUTCOME_NO_PERMISSION
        playSound && !soundResult.started && (!channelEnabled || !notifPermissionGranted) ->
          AlarmDiagnosticLog.OUTCOME_SOUND_FAILED
        !playSound && !playVibration -> AlarmDiagnosticLog.OUTCOME_PLAYED
        soundResult.started || shouldUseChannelSound || vibrationTriggered ->
          AlarmDiagnosticLog.OUTCOME_PLAYED
        else -> AlarmDiagnosticLog.OUTCOME_SOUND_FAILED
      }

    val diagDetail =
      "method=$effectiveMethod vol=${alarmVolumePercent}% vib=$vibrationTriggered perm=$notifPermissionGranted ch=$channelEnabled delay=${delayMs}ms"
    AlarmDiagnosticLog.record(
      context = appContext,
      eventType = eventLabel,
      source = sourceLabel,
      outcome = diagOutcome,
      detail = diagDetail,
    )

    return AlarmTestReport(
      soundStarted = soundResult.started || (shouldUseChannelSound && channelEnabled && notifPermissionGranted),
      soundMethod = effectiveMethod,
      vibrationTriggered = vibrationTriggered,
      notificationPosted = notifPermissionGranted,
      notificationPermissionGranted = notifPermissionGranted,
      alarmVolumePercent = alarmVolumePercent,
      dndBlocked = dndBlocked,
      dndStatusLabel = dndLabel,
      exactAlarmAllowed = exactAlarmAllowed,
      channelEnabled = channelEnabled,
    )
  }

  /**
   * Immediate Test Alarm invoked from More > Timer Alarm ("Test alarm").
   * Uses the exact same shared firing code path.
   */
  suspend fun runTestAlarmNow(context: Context): AlarmTestReport {
    val app = context.applicationContext as? SyllabusApplication
    val prefs =
      app?.preferencesRepository?.getCachedPreferencesSnapshot()
        ?: com.example.data.preferences.UserPreferencesRepository(context).getCachedPreferencesSnapshot()
    return withContext(NonCancellable) {
      deliverAlarmAlert(
        context = context.applicationContext,
        eventLabel = "TEST_NOW",
        sourceLabel = AlarmFireSource.TEST.logLabel,
        delayMs = 0L,
        title = "Test timer alarm",
        message = "Syllabus Tracker alarm & notification test.",
        notificationId = NotificationHelper.TIMER_ALERT_TEST_NOTIFICATION_ID,
        playSound = prefs.timerAlarmEnabled,
        playVibration = prefs.timerVibrationEnabled,
      )
    }
  }

  /**
   * Duplicate-protected Test-in-10s delivery invoked by both the primary delayed coroutine
   * and the backup setAlarmClock TimerAlarmReceiver.
   */
  suspend fun fireTestAlarm(
    context: Context,
    source: AlarmFireSource,
    testId: String,
    expectedEventWallMs: Long,
  ): AlarmTestReport? {
    val appContext = context.applicationContext
    val app = appContext as? SyllabusApplication
    val prefs =
      app?.preferencesRepository?.getCachedPreferencesSnapshot()
        ?: com.example.data.preferences.UserPreferencesRepository(appContext).getCachedPreferencesSnapshot()

    return withContext(NonCancellable) {
      testMutex.withLock {
        if (isTestIdFired(appContext, testId)) {
          AlarmDiagnosticLog.record(
            context = appContext,
            eventType = "TEST_10S",
            source = source.logLabel,
            outcome = AlarmDiagnosticLog.OUTCOME_SKIPPED_DUPLICATE,
            detail = "testId=$testId",
          )
          return@withLock null
        }

        val delayMs = (System.currentTimeMillis() - expectedEventWallMs).coerceAtLeast(0L)
        val report =
          deliverAlarmAlert(
            context = appContext,
            eventLabel = "TEST_10S",
            sourceLabel = source.logLabel,
            delayMs = delayMs,
            title = "Test timer alarm (10s)",
            message = "10-second scheduled alarm test fired via ${source.logLabel}.",
            notificationId = NotificationHelper.TIMER_ALERT_TEST_NOTIFICATION_ID,
            playSound = prefs.timerAlarmEnabled,
            playVibration = prefs.timerVibrationEnabled,
          )
        markTestIdFired(appContext, testId)
        report
      }
    }
  }

  /**
   * Backward-compatible helper returning whether sound started cleanly.
   */
  suspend fun playMiniAlarm(
    context: Context,
    playSound: Boolean,
    playVibration: Boolean,
  ): Boolean {
    if (!playSound && !playVibration) return false
    val appContext = context.applicationContext
    val notificationManager =
      appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    if (isBlockedByDoNotDisturb(notificationManager)) {
      Log.i(TAG, "Mini alarm suppressed by Do Not Disturb")
      return false
    }
    if (playVibration) {
      audioBackend.playVibration(appContext)
    }
    return if (playSound) {
      audioBackend.playSound(appContext).started
    } else {
      false
    }
  }

  fun stopAll() {
    audioBackend.stopAll()
  }

  private suspend fun tryPlayRawMediaPlayer(context: Context): Boolean {
    var mp: MediaPlayer? = null
    return try {
      mp = MediaPlayer()
      mp.setAudioAttributes(alarmAudioAttributes)
      val afd = runCatching { context.resources.openRawResourceFd(R.raw.timer_alarm_beep) }.getOrNull()
      if (afd != null) {
        afd.use { descriptor ->
          mp.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
        }
      } else {
        mp.setDataSource(
          context,
          Uri.parse("android.resource://${context.packageName}/${R.raw.timer_alarm_beep}"),
        )
      }
      mp.isLooping = false
      val prepared = CompletableDeferred<Boolean>()
      mp.setOnPreparedListener { readyMp ->
        runCatching {
          readyMp.start()
          prepared.complete(readyMp.isPlaying)
        }.onFailure { err ->
          Log.w(TAG, "MediaPlayer raw start failed", err)
          prepared.complete(false)
        }
      }
      mp.setOnErrorListener { _, what, extra ->
        Log.w(TAG, "MediaPlayer raw error what=$what extra=$extra")
        prepared.complete(false)
        true
      }
      mp.setOnCompletionListener { completedMp ->
        synchronized(audioLock) {
          if (activeMediaPlayer === completedMp) {
            cleanupLocked()
          } else {
            runCatching { completedMp.release() }
          }
        }
      }
      mp.prepareAsync()
      val started = withTimeoutOrNull(1500L) { prepared.await() } == true
      if (started) {
        synchronized(audioLock) {
          activeMediaPlayer = mp
          scheduleStopLocked()
        }
        true
      } else {
        runCatching { mp.release() }
        false
      }
    } catch (err: Throwable) {
      Log.w(TAG, "MediaPlayer raw setup failed", err)
      runCatching { mp?.release() }
      false
    }
  }

  private suspend fun tryPlayVerifiedRingtone(context: Context, uri: Uri): Boolean {
    val ringtone =
      runCatching {
        RingtoneManager.getRingtone(context, uri)?.apply {
          audioAttributes = alarmAudioAttributes
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            isLooping = false
          }
          play()
        }
      }.onFailure { err ->
        Log.w(TAG, "Ringtone play threw for $uri", err)
      }.getOrNull() ?: return false

    if (!ringtone.isPlaying) {
      delay(60L)
    }

    val actuallyPlaying = runCatching { ringtone.isPlaying }.getOrDefault(false)
    if (!actuallyPlaying) {
      Log.w(TAG, "Ringtone did not start playing for $uri; trying next fallback")
      runCatching { ringtone.stop() }
      return false
    }

    synchronized(audioLock) {
      activeRingtone = ringtone
      scheduleStopLocked()
    }
    return true
  }

  private fun triggerShortVibration(context: Context): Boolean {
    return runCatching {
      val vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
          manager?.defaultVibrator
        } else {
          @Suppress("DEPRECATION")
          context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

      if (vibrator == null || !vibrator.hasVibrator()) return false

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
      true
    }.onFailure { err ->
      Log.w(TAG, "Short vibration failed", err)
    }.getOrDefault(false)
  }

  private fun scheduleStopLocked() {
    pendingStopFuture?.cancel(false)
    pendingStopFuture =
      stopExecutor.schedule(
        {
          synchronized(audioLock) {
            cleanupLocked()
          }
        },
        MINI_ALARM_DURATION_MS,
        TimeUnit.MILLISECONDS,
      )
  }

  private fun cleanupLocked() {
    pendingStopFuture?.cancel(false)
    pendingStopFuture = null

    activeMediaPlayer?.let { mp ->
      runCatching {
        if (mp.isPlaying) mp.stop()
      }
      runCatching { mp.release() }
    }
    activeMediaPlayer = null

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

  private fun isTestIdFired(context: Context, testId: String): Boolean {
    val prefs = context.getSharedPreferences(TEST_DEDUP_PREFS, Context.MODE_PRIVATE)
    return (prefs.getStringSet(KEY_TEST_IDS, emptySet()) ?: emptySet()).contains(testId)
  }

  private fun markTestIdFired(context: Context, testId: String) {
    val prefs = context.getSharedPreferences(TEST_DEDUP_PREFS, Context.MODE_PRIVATE)
    val set = (prefs.getStringSet(KEY_TEST_IDS, emptySet()) ?: emptySet()).toMutableSet()
    set.add(testId)
    val bounded = if (set.size > 16) set.toList().takeLast(16).toSet() else set
    prefs.edit().putStringSet(KEY_TEST_IDS, bounded).apply()
  }
}

typealias AlarmPlayer = TimerAlarmPlayer
