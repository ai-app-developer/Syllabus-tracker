package com.example.timer

import android.os.SystemClock
import com.example.data.local.entity.ActiveTimerStateEntity
import com.example.data.local.entity.ReadingSessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object TimerMode {
  const val STOPWATCH = "STOPWATCH"
  const val POMODORO = "POMODORO"
}

object TimerStatus {
  const val IDLE = "IDLE"
  const val RUNNING = "RUNNING"
  const val PAUSED = "PAUSED"
  const val BREAK = "BREAK"
  const val STOP_SUMMARY = "STOP_SUMMARY"
}

object SessionEventType {
  const val START = "START"
  const val PAUSE = "PAUSE"
  const val RESUME = "RESUME"
  const val LAP = "LAP"
  const val BREAK_START = "BREAK_START"
  const val BREAK_SKIP = "BREAK_SKIP"
  const val STOP = "STOP"
}

object GoalPeriodType {
  const val DAILY = "DAILY"
  const val WEEKLY = "WEEKLY"
  const val MONTHLY = "MONTHLY"
}

interface ClockProvider {
  fun elapsedRealtime(): Long

  fun currentTimeMillis(): Long
}

object SystemClockProvider : ClockProvider {
  override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

  override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

data class ComputedTimerMetrics(
  val activeFocusMs: Long,
  val pauseMs: Long,
  val breakMs: Long,
  val currentPhaseElapsedMs: Long,
  val currentPhaseRemainingMs: Long,
  val focusPercent: Int,
  val progressFraction: Float,
)

object TimerMath {
  private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  fun formatLocalDate(wallTimeMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
    return Instant.ofEpochMilli(wallTimeMs).atZone(zoneId).toLocalDate().format(dateFormatter)
  }

  fun computeMetrics(
    state: ActiveTimerStateEntity,
    nowElapsedMs: Long,
    nowWallTimeMs: Long = System.currentTimeMillis(),
  ): ComputedTimerMetrics {
    val liveDelta =
      if (state.status == TimerStatus.RUNNING || state.status == TimerStatus.BREAK) {
        if (nowElapsedMs >= state.phaseStartElapsedMs) {
          (nowElapsedMs - state.phaseStartElapsedMs).coerceAtLeast(0L)
        } else {
          (nowWallTimeMs - state.phaseStartWallTimeMs).coerceAtLeast(0L)
        }
      } else {
        0L
      }

    val phaseElapsed = (state.phaseCarryMs + liveDelta).coerceAtLeast(0L)

    val effectivePhaseStatus =
      when (state.status) {
        TimerStatus.RUNNING, TimerStatus.BREAK -> state.status
        TimerStatus.PAUSED -> state.pausedFromStatus ?: TimerStatus.RUNNING
        TimerStatus.STOP_SUMMARY ->
          when (state.statusBeforeStopSummary) {
            TimerStatus.BREAK -> TimerStatus.BREAK
            TimerStatus.PAUSED -> state.pausedFromStatus ?: TimerStatus.RUNNING
            else -> TimerStatus.RUNNING
          }
        else -> TimerStatus.RUNNING
      }

    val focusMs: Long
    val breakMs: Long
    when (effectivePhaseStatus) {
      TimerStatus.BREAK -> {
        focusMs = state.accumulatedFocusMs
        breakMs = state.accumulatedBreakMs + phaseElapsed
      }
      else -> {
        focusMs = state.accumulatedFocusMs + phaseElapsed
        breakMs = state.accumulatedBreakMs
      }
    }

    val pauseStartElapsed = state.pauseStartElapsedMs
    val openPause =
      if (state.status == TimerStatus.PAUSED && pauseStartElapsed != null) {
        if (nowElapsedMs >= pauseStartElapsed) {
          (nowElapsedMs - pauseStartElapsed).coerceAtLeast(0L)
        } else {
          val pauseStartWall = state.pauseStartWallTimeMs ?: state.lastKnownWallTimeMs
          (nowWallTimeMs - pauseStartWall).coerceAtLeast(0L)
        }
      } else {
        0L
      }
    val pauseMs = state.accumulatedPauseMs + openPause

    val phaseDurationMs =
      when {
        state.mode == TimerMode.POMODORO && effectivePhaseStatus == TimerStatus.BREAK ->
          state.breakDurationMs
        state.mode == TimerMode.POMODORO -> state.focusDurationMs
        else -> 0L
      }

    val remainingMs =
      if (phaseDurationMs > 0L) (phaseDurationMs - phaseElapsed).coerceAtLeast(0L) else 0L

    val totalEngagedAndPaused = focusMs + pauseMs
    val focusPercent =
      if (totalEngagedAndPaused <= 0L) {
        100
      } else {
        ((focusMs * 100.0) / totalEngagedAndPaused).roundToInt().coerceIn(0, 100)
      }

    val progressFraction =
      when {
        state.mode == TimerMode.POMODORO && phaseDurationMs > 0L -> {
          (phaseElapsed.toFloat() / phaseDurationMs.toFloat()).coerceIn(0f, 1f)
        }
        state.goalMs != null && state.goalMs > 0L -> {
          (focusMs.toFloat() / state.goalMs.toFloat()).coerceIn(0f, 1f)
        }
        else -> {
          // Smooth 60-minute cycle visual progress when no goal is set in Stopwatch mode.
          ((focusMs % 3_600_000L).toFloat() / 3_600_000f).coerceIn(0f, 1f)
        }
      }

    return ComputedTimerMetrics(
      activeFocusMs = focusMs,
      pauseMs = pauseMs,
      breakMs = breakMs,
      currentPhaseElapsedMs = phaseElapsed,
      currentPhaseRemainingMs = remainingMs,
      focusPercent = focusPercent,
      progressFraction = progressFraction,
    )
  }

  /**
   * Computes conservative recovery focus time up to `lastKnownWallTimeMs` in case of crash/kill/reboot.
   */
  fun computeLastKnownRecoveryMetrics(state: ActiveTimerStateEntity): ComputedTimerMetrics {
    val effectivePhaseStatus =
      when (state.status) {
        TimerStatus.RUNNING, TimerStatus.BREAK -> state.status
        TimerStatus.PAUSED -> state.pausedFromStatus ?: TimerStatus.RUNNING
        TimerStatus.STOP_SUMMARY ->
          when (state.statusBeforeStopSummary) {
            TimerStatus.BREAK -> TimerStatus.BREAK
            TimerStatus.PAUSED -> state.pausedFromStatus ?: TimerStatus.RUNNING
            else -> TimerStatus.RUNNING
          }
        else -> TimerStatus.RUNNING
      }

    val wallDelta =
      if (state.status == TimerStatus.RUNNING || state.status == TimerStatus.BREAK) {
        (state.lastKnownWallTimeMs - state.phaseStartWallTimeMs).coerceAtLeast(0L)
      } else {
        0L
      }
    val rawPhaseElapsed = (state.phaseCarryMs + wallDelta).coerceAtLeast(0L)
    val phaseDurationMs =
      when {
        state.mode == TimerMode.POMODORO && effectivePhaseStatus == TimerStatus.BREAK ->
          state.breakDurationMs
        state.mode == TimerMode.POMODORO -> state.focusDurationMs
        else -> 0L
      }
    val cappedPhaseElapsed =
      if (state.mode == TimerMode.POMODORO && phaseDurationMs > 0L) {
        rawPhaseElapsed.coerceAtMost(phaseDurationMs)
      } else {
        rawPhaseElapsed
      }

    val focusMs =
      if (effectivePhaseStatus == TimerStatus.RUNNING) {
        state.accumulatedFocusMs + cappedPhaseElapsed
      } else {
        state.accumulatedFocusMs
      }
    val breakMs =
      if (effectivePhaseStatus == TimerStatus.BREAK) {
        state.accumulatedBreakMs + cappedPhaseElapsed
      } else {
        state.accumulatedBreakMs
      }
    val extraPause =
      if (state.status == TimerStatus.PAUSED && state.pauseStartWallTimeMs != null) {
        (state.lastKnownWallTimeMs - state.pauseStartWallTimeMs).coerceAtLeast(0L)
      } else {
        0L
      }
    val pauseMs = state.accumulatedPauseMs + extraPause
    val total = focusMs + pauseMs
    val focusPct =
      if (total <= 0L) 100 else ((focusMs * 100.0) / total).roundToInt().coerceIn(0, 100)
    val remainingMs =
      if (phaseDurationMs > 0L) (phaseDurationMs - cappedPhaseElapsed).coerceAtLeast(0L) else 0L
    val progressFraction =
      when {
        state.mode == TimerMode.POMODORO && phaseDurationMs > 0L ->
          (cappedPhaseElapsed.toFloat() / phaseDurationMs.toFloat()).coerceIn(0f, 1f)
        state.goalMs != null && state.goalMs > 0L ->
          (focusMs.toFloat() / state.goalMs.toFloat()).coerceIn(0f, 1f)
        else ->
          ((focusMs % 3_600_000L).toFloat() / 3_600_000f).coerceIn(0f, 1f)
      }

    return ComputedTimerMetrics(
      activeFocusMs = focusMs,
      pauseMs = pauseMs,
      breakMs = breakMs,
      currentPhaseElapsedMs = cappedPhaseElapsed,
      currentPhaseRemainingMs = remainingMs,
      focusPercent = focusPct,
      progressFraction = progressFraction,
    )
  }

  /**
   * Splits a completed session across local calendar dates if it spans midnight.
   * Ensures exact conservation of total activeFocusMs, pauseMs, and breakMs, and generates
   * deterministic per-day sessionId keys for duplicate-save protection.
   */
  fun splitSessionAcrossDays(
    baseSessionId: String,
    bookId: Long,
    chapterId: Long,
    mode: String,
    startWallTimeMs: Long,
    endWallTimeMs: Long,
    activeFocusMs: Long,
    pauseMs: Long,
    breakMs: Long,
    goalMs: Long?,
    notes: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): List<ReadingSessionEntity> {
    val safeEndWall = maxOf(endWallTimeMs, startWallTimeMs)
    val startDate = Instant.ofEpochMilli(startWallTimeMs).atZone(zoneId).toLocalDate()
    val endDate = Instant.ofEpochMilli(safeEndWall).atZone(zoneId).toLocalDate()

    if (!endDate.isAfter(startDate) || safeEndWall == startWallTimeMs) {
      val dateStr = startDate.format(dateFormatter)
      return listOf(
        ReadingSessionEntity(
          sessionId = "$baseSessionId#$dateStr",
          bookId = bookId,
          chapterId = chapterId,
          mode = mode,
          status = "SAVED",
          startWallTimeMs = startWallTimeMs,
          endWallTimeMs = safeEndWall,
          activeFocusMs = activeFocusMs.coerceAtLeast(0L),
          pauseMs = pauseMs.coerceAtLeast(0L),
          breakMs = breakMs.coerceAtLeast(0L),
          goalMs = goalMs,
          notes = notes.trim(),
          sessionDate = dateStr,
          createdAt = safeEndWall,
        )
      )
    }

    val segments = mutableListOf<Triple<LocalDate, Long, Long>>()
    var cursorDate = startDate
    while (!cursorDate.isAfter(endDate)) {
      val dayStart = cursorDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
      val dayEnd = cursorDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
      val segStart = maxOf(startWallTimeMs, dayStart)
      val segEnd = minOf(safeEndWall, dayEnd)
      if (segEnd > segStart) {
        segments.add(Triple(cursorDate, segStart, segEnd))
      }
      cursorDate = cursorDate.plusDays(1)
    }

    if (segments.isEmpty()) {
      val dateStr = startDate.format(dateFormatter)
      return listOf(
        ReadingSessionEntity(
          sessionId = "$baseSessionId#$dateStr",
          bookId = bookId,
          chapterId = chapterId,
          mode = mode,
          status = "SAVED",
          startWallTimeMs = startWallTimeMs,
          endWallTimeMs = safeEndWall,
          activeFocusMs = activeFocusMs.coerceAtLeast(0L),
          pauseMs = pauseMs.coerceAtLeast(0L),
          breakMs = breakMs.coerceAtLeast(0L),
          goalMs = goalMs,
          notes = notes.trim(),
          sessionDate = dateStr,
          createdAt = safeEndWall,
        )
      )
    }

    val totalWallSpan = segments.sumOf { (it.third - it.second).coerceAtLeast(1L) }.toDouble()
    var remainingFocus = activeFocusMs.coerceAtLeast(0L)
    var remainingPause = pauseMs.coerceAtLeast(0L)
    var remainingBreak = breakMs.coerceAtLeast(0L)

    return segments.mapIndexed { index, (date, segStart, segEnd) ->
      val isLast = index == segments.lastIndex
      val segFocus: Long
      val segPause: Long
      val segBreak: Long
      if (isLast) {
        segFocus = remainingFocus
        segPause = remainingPause
        segBreak = remainingBreak
      } else {
        val ratio = (segEnd - segStart).toDouble() / totalWallSpan
        segFocus = (activeFocusMs * ratio).roundToInt().toLong().coerceIn(0L, remainingFocus)
        segPause = (pauseMs * ratio).roundToInt().toLong().coerceIn(0L, remainingPause)
        segBreak = (breakMs * ratio).roundToInt().toLong().coerceIn(0L, remainingBreak)
        remainingFocus -= segFocus
        remainingPause -= segPause
        remainingBreak -= segBreak
      }

      val dateStr = date.format(dateFormatter)
      ReadingSessionEntity(
        sessionId = "$baseSessionId#$dateStr",
        bookId = bookId,
        chapterId = chapterId,
        mode = mode,
        status = "SAVED",
        startWallTimeMs = segStart,
        endWallTimeMs = segEnd,
        activeFocusMs = segFocus,
        pauseMs = segPause,
        breakMs = segBreak,
        goalMs = goalMs,
        notes = notes.trim(),
        sessionDate = dateStr,
        createdAt = segEnd,
      )
    }
  }

  fun formatHms(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
  }

  fun formatShortElapsed(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
      hours > 0 -> "${hours}h ${minutes}m"
      minutes > 0 -> "${minutes}m ${seconds}s"
      else -> "${seconds}s"
    }
  }

  fun formatGoalWords(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
      hours > 0 && minutes > 0 ->
        "$hours ${if (hours == 1) "hour" else "hours"} $minutes ${if (minutes == 1) "minute" else "minutes"}"
      hours > 0 -> "$hours ${if (hours == 1) "hour" else "hours"}"
      else -> "$minutes ${if (minutes == 1) "minute" else "minutes"}"
    }
  }

  fun formatGoalTile(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
      hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
      hours > 0 -> "${hours}h"
      else -> "${minutes}m"
    }
  }

  fun formatHoursMinutes(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L).toInt()
    if (totalMinutes <= 0 && durationMs > 0L) return "1m"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
      hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
      hours > 0 -> "${hours}h"
      else -> "${minutes}m"
    }
  }

  fun formatStackedHoursMinutes(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
      hours > 0 && minutes > 0 -> "${hours}h\n${minutes}m"
      hours > 0 -> "${hours}h"
      else -> "${minutes}m"
    }
  }
}
