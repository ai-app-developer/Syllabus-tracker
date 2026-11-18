package com.example.timer

import com.example.data.local.dao.ReadingSessionWithMeta
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

enum class AnalyticsPeriod(
  val label: String,
  val comparisonLabel: String,
) {
  THIS_WEEK("This Week", "than last week"),
  THIS_MONTH("This Month", "than last month"),
  THIS_YEAR("This Year", "than last year"),
}

data class StudyBarBucket(
  val label: String,
  val durationMs: Long,
  val valueLabel: String,
  val isHighest: Boolean,
)

data class BookStudySlice(
  val bookId: Long?,
  val bookName: String,
  val totalMs: Long,
  val colorHex: Long,
)

data class TimerAnalyticsUiState(
  val period: AnalyticsPeriod = AnalyticsPeriod.THIS_WEEK,
  val todayTotalMs: Long = 0L,
  val periodTotalMs: Long = 0L,
  val previousPeriodTotalMs: Long = 0L,
  val deltaPercent: Int? = null,
  val bars: List<StudyBarBucket> = emptyList(),
  val bookSlices: List<BookStudySlice> = emptyList(),
  val currentStreakDays: Int = 0,
)

object TimerAnalyticsCalculator {
  private val slicePalette =
    listOf(
      0xFF8B5CF6L, // Violet
      0xFF3B82F6L, // Blue
      0xFF22C55EL, // Green
      0xFFF59E0BL, // Orange
      0xFFCBD5E1L, // Others grey
    )

  fun compute(
    sessions: List<ReadingSessionWithMeta>,
    period: AnalyticsPeriod,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
  ): TimerAnalyticsUiState {
    val parsed =
      sessions.map { item ->
        val date =
          runCatching { LocalDate.parse(item.session.sessionDate) }
            .getOrElse {
              Instant.ofEpochMilli(item.session.startWallTimeMs).atZone(zoneId).toLocalDate()
            }
        date to item
      }

    val todayStr = today.toString()
    val todayTotalMs =
      parsed
        .filter { (date, _) -> date.toString() == todayStr }
        .sumOf { (_, item) -> item.session.activeFocusMs.coerceAtLeast(0L) }

    val (currentStart, currentEnd, prevStart, prevEnd) = periodBounds(period, today)

    val currentSessions =
      parsed.filter { (date, _) -> !date.isBefore(currentStart) && !date.isAfter(currentEnd) }
    val prevSessions =
      parsed.filter { (date, _) -> !date.isBefore(prevStart) && !date.isAfter(prevEnd) }

    val periodTotalMs =
      currentSessions.sumOf { (_, item) -> item.session.activeFocusMs.coerceAtLeast(0L) }
    val prevTotalMs =
      prevSessions.sumOf { (_, item) -> item.session.activeFocusMs.coerceAtLeast(0L) }

    val deltaPercent =
      if (prevTotalMs > 0L) {
        (((periodTotalMs - prevTotalMs).toDouble() / prevTotalMs.toDouble()) * 100.0).roundToInt()
      } else {
        null
      }

    val bars = buildBars(period, today, currentStart, currentSessions)
    val bookSlices = buildBookSlices(currentSessions.map { it.second })
    val streak = computeCurrentStreak(parsed.filter { it.second.session.activeFocusMs > 0L }.map { it.first }, today)

    return TimerAnalyticsUiState(
      period = period,
      todayTotalMs = todayTotalMs,
      periodTotalMs = periodTotalMs,
      previousPeriodTotalMs = prevTotalMs,
      deltaPercent = deltaPercent,
      bars = bars,
      bookSlices = bookSlices,
      currentStreakDays = streak,
    )
  }

  private data class Bounds(
    val start: LocalDate,
    val end: LocalDate,
    val prevStart: LocalDate,
    val prevEnd: LocalDate,
  )

  private fun periodBounds(period: AnalyticsPeriod, today: LocalDate): Bounds {
    return when (period) {
      AnalyticsPeriod.THIS_WEEK -> {
        val daysSinceSat = (today.dayOfWeek.value - DayOfWeek.SATURDAY.value + 7) % 7
        val sat = today.minusDays(daysSinceSat.toLong())
        val fri = sat.plusDays(6)
        Bounds(
          start = sat,
          end = fri,
          prevStart = sat.minusDays(7),
          prevEnd = sat.minusDays(1),
        )
      }
      AnalyticsPeriod.THIS_MONTH -> {
        val firstOfMonth = today.withDayOfMonth(1)
        val lastOfMonth = today.withDayOfMonth(today.lengthOfMonth())
        val prevMonth = firstOfMonth.minusMonths(1)
        Bounds(
          start = firstOfMonth,
          end = lastOfMonth,
          prevStart = prevMonth,
          prevEnd = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth()),
        )
      }
      AnalyticsPeriod.THIS_YEAR -> {
        val firstOfYear = today.withDayOfYear(1)
        val lastOfYear = today.withDayOfYear(today.lengthOfYear())
        val prevYearStart = firstOfYear.minusYears(1)
        Bounds(
          start = firstOfYear,
          end = lastOfYear,
          prevStart = prevYearStart,
          prevEnd = prevYearStart.withDayOfYear(prevYearStart.lengthOfYear()),
        )
      }
    }
  }

  private fun buildBars(
    period: AnalyticsPeriod,
    today: LocalDate,
    currentStart: LocalDate,
    currentSessions: List<Pair<LocalDate, ReadingSessionWithMeta>>,
  ): List<StudyBarBucket> {
    val rawBuckets: List<Pair<String, Long>> =
      when (period) {
        AnalyticsPeriod.THIS_WEEK -> {
          val byDate = currentSessions.groupBy { it.first }
          (0..6).map { offset ->
            val date = currentStart.plusDays(offset.toLong())
            val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).take(3)
            val totalMs =
              byDate[date].orEmpty().sumOf { it.second.session.activeFocusMs.coerceAtLeast(0L) }
            label to totalMs
          }
        }
        AnalyticsPeriod.THIS_MONTH -> {
          val monthLen = today.lengthOfMonth()
          val ranges =
            listOf(1..7, 8..14, 15..21, 22..28, 29..monthLen).filter { it.first <= it.last }
          ranges.mapIndexed { idx, range ->
            val totalMs =
              currentSessions
                .filter { (date, _) -> date.dayOfMonth in range }
                .sumOf { it.second.session.activeFocusMs.coerceAtLeast(0L) }
            "W${idx + 1}" to totalMs
          }
        }
        AnalyticsPeriod.THIS_YEAR -> {
          val monthRange = if (today.monthValue <= 6) 1..6 else 7..12
          monthRange.map { monthVal ->
            val totalMs =
              currentSessions
                .filter { (date, _) -> date.monthValue == monthVal }
                .sumOf { it.second.session.activeFocusMs.coerceAtLeast(0L) }
            val monthName =
              today.withMonth(monthVal).month.getDisplayName(TextStyle.SHORT, Locale.US).take(3)
            monthName to totalMs
          }
        }
      }

    val maxMs = rawBuckets.maxOfOrNull { it.second } ?: 0L
    return rawBuckets.map { (label, ms) ->
      StudyBarBucket(
        label = label,
        durationMs = ms,
        valueLabel = formatBarDuration(ms),
        isHighest = maxMs > 0L && ms == maxMs,
      )
    }
  }

  private fun buildBookSlices(sessions: List<ReadingSessionWithMeta>): List<BookStudySlice> {
    if (sessions.isEmpty()) return emptyList()
    val grouped =
      sessions
        .filter { it.session.activeFocusMs > 0L }
        .groupBy { it.session.bookId }
        .map { (bookId, items) ->
          Triple(
            bookId,
            items.first().bookName,
            items.sumOf { it.session.activeFocusMs.coerceAtLeast(0L) },
          )
        }
        .sortedByDescending { it.third }

    if (grouped.isEmpty()) return emptyList()

    return if (grouped.size <= 4) {
      grouped.mapIndexed { idx, (bookId, name, totalMs) ->
        BookStudySlice(
          bookId = bookId,
          bookName = name,
          totalMs = totalMs,
          colorHex = slicePalette[idx],
        )
      }
    } else {
      val top4 =
        grouped.take(4).mapIndexed { idx, (bookId, name, totalMs) ->
          BookStudySlice(
            bookId = bookId,
            bookName = name,
            totalMs = totalMs,
            colorHex = slicePalette[idx],
          )
        }
      val othersMs = grouped.drop(4).sumOf { it.third }
      top4 +
        BookStudySlice(
          bookId = null,
          bookName = "Others",
          totalMs = othersMs,
          colorHex = slicePalette[4],
        )
    }
  }

  private fun computeCurrentStreak(activeDates: List<LocalDate>, today: LocalDate): Int {
    if (activeDates.isEmpty()) return 0
    val dateSet = activeDates.toHashSet()
    var cursor =
      when {
        today in dateSet -> today
        today.minusDays(1) in dateSet -> today.minusDays(1)
        else -> return 0
      }
    var count = 0
    while (cursor in dateSet) {
      count++
      cursor = cursor.minusDays(1)
    }
    return count
  }

  fun formatBarDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0h"
    val totalMinutes = (durationMs / 60_000L).toInt()
    if (totalMinutes <= 0) return "<1m"
    if (totalMinutes < 60) return "${totalMinutes}m"
    val tenths = ((totalMinutes / 60.0) * 10.0).roundToInt()
    return if (tenths % 10 == 0) {
      "${tenths / 10}h"
    } else {
      "${tenths / 10}.${tenths % 10}h"
    }
  }
}
