package com.example.data.local.dao

import androidx.compose.runtime.Immutable
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ActiveTimerStateEntity
import com.example.data.local.entity.ReadingGoalEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.SessionEventEntity
import kotlinx.coroutines.flow.Flow

@Immutable
data class ReadingSessionWithMeta(
  @Embedded val session: ReadingSessionEntity,
  val bookName: String,
  val chapterName: String,
  val chapterNumber: Int,
)

@Immutable
data class ActiveTimerWithMetaRow(
  @Embedded val state: ActiveTimerStateEntity,
  val bookName: String?,
  val chapterName: String?,
  val chapterNumber: Int?,
)

@Immutable
data class BookFocusTotalRow(
  val bookId: Long,
  val bookName: String,
  val totalMs: Long,
)

@Immutable
data class DailyFocusTotalRow(
  val sessionDate: String,
  val totalMs: Long,
)

@Dao
interface TimerDao {
  @Query("SELECT * FROM active_timer_state WHERE id = 1 LIMIT 1")
  fun observeActiveTimerState(): Flow<ActiveTimerStateEntity?>

  @Query(
    """
    SELECT
      ats.*,
      b.name AS bookName,
      c.name AS chapterName,
      c.number AS chapterNumber
    FROM active_timer_state ats
    LEFT JOIN books b ON b.id = ats.bookId
    LEFT JOIN chapters c ON c.id = ats.chapterId
    WHERE ats.id = 1
    LIMIT 1
    """
  )
  fun observeActiveSessionWithMeta(): Flow<ActiveTimerWithMetaRow?>

  @Query("SELECT * FROM active_timer_state WHERE id = 1 LIMIT 1")
  suspend fun getActiveTimerStateOnce(): ActiveTimerStateEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertActiveTimerState(state: ActiveTimerStateEntity)

  @Query(
    """
    UPDATE active_timer_state
    SET status = :status,
        pausedFromStatus = :pausedFromStatus,
        phaseCarryMs = :phaseCarryMs,
        pauseStartElapsedMs = :pauseStartElapsedMs,
        pauseStartWallTimeMs = :pauseStartWallTimeMs,
        lastKnownWallTimeMs = :lastKnownWallTimeMs
    WHERE id = 1
    """
  )
  suspend fun updatePauseState(
    status: String,
    pausedFromStatus: String,
    phaseCarryMs: Long,
    pauseStartElapsedMs: Long,
    pauseStartWallTimeMs: Long,
    lastKnownWallTimeMs: Long,
  )

  @Query(
    """
    UPDATE active_timer_state
    SET status = :status,
        pausedFromStatus = NULL,
        statusBeforeStopSummary = NULL,
        phaseStartElapsedMs = :phaseStartElapsedMs,
        phaseStartWallTimeMs = :phaseStartWallTimeMs,
        accumulatedPauseMs = :accumulatedPauseMs,
        pauseStartElapsedMs = NULL,
        pauseStartWallTimeMs = NULL,
        lastKnownWallTimeMs = :lastKnownWallTimeMs
    WHERE id = 1
    """
  )
  suspend fun updateResumeState(
    status: String,
    phaseStartElapsedMs: Long,
    phaseStartWallTimeMs: Long,
    accumulatedPauseMs: Long,
    lastKnownWallTimeMs: Long,
  )

  @Query(
    """
    UPDATE active_timer_state
    SET status = :status,
        pausedFromStatus = :pausedFromStatus,
        statusBeforeStopSummary = :statusBeforeStopSummary,
        phaseCarryMs = :phaseCarryMs,
        accumulatedPauseMs = :accumulatedPauseMs,
        pauseStartElapsedMs = :pauseStartElapsedMs,
        pauseStartWallTimeMs = :pauseStartWallTimeMs,
        lastKnownWallTimeMs = :lastKnownWallTimeMs
    WHERE id = 1
    """
  )
  suspend fun updateStopSummaryState(
    status: String,
    pausedFromStatus: String?,
    statusBeforeStopSummary: String?,
    phaseCarryMs: Long,
    accumulatedPauseMs: Long,
    pauseStartElapsedMs: Long,
    pauseStartWallTimeMs: Long,
    lastKnownWallTimeMs: Long,
  )

  @Query(
    """
    UPDATE active_timer_state
    SET accumulatedPauseMs = :accumulatedPauseMs,
        pauseStartElapsedMs = :pauseStartElapsedMs,
        pauseStartWallTimeMs = :pauseStartWallTimeMs,
        lastKnownWallTimeMs = :lastKnownWallTimeMs
    WHERE id = 1 AND status = 'PAUSED'
    """
  )
  suspend fun updateRebootRebasedPausedState(
    accumulatedPauseMs: Long,
    pauseStartElapsedMs: Long,
    pauseStartWallTimeMs: Long,
    lastKnownWallTimeMs: Long,
  )

  @Query("DELETE FROM active_timer_state")
  suspend fun clearActiveTimerState()

  @Query("UPDATE active_timer_state SET lastKnownWallTimeMs = :wallTimeMs WHERE id = 1")
  suspend fun updateLastKnownWallTime(wallTimeMs: Long)

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertEvent(event: SessionEventEntity): Long

  @Query("SELECT * FROM session_events WHERE sessionId = :sessionId ORDER BY wallTimeMs ASC, id ASC")
  fun observeEventsForSession(sessionId: String): Flow<List<SessionEventEntity>>

  @Query("SELECT * FROM session_events WHERE sessionId = :sessionId ORDER BY wallTimeMs ASC, id ASC")
  suspend fun getEventsForSessionOnce(sessionId: String): List<SessionEventEntity>

  @Query("DELETE FROM session_events WHERE sessionId = :sessionId")
  suspend fun deleteEventsBySessionId(sessionId: String)

  @Query("SELECT COUNT(*) FROM reading_sessions WHERE sessionId = :sessionId")
  suspend fun countSessionsBySessionId(sessionId: String): Int

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertSession(session: ReadingSessionEntity): Long

  @Update
  suspend fun updateSession(session: ReadingSessionEntity)

  @Query("DELETE FROM reading_sessions WHERE id = :id")
  suspend fun deleteSessionById(id: Long)

  @Query("SELECT * FROM reading_sessions WHERE id = :id LIMIT 1")
  suspend fun getSessionByIdOnce(id: Long): ReadingSessionEntity?

  @Query(
    """
    SELECT rs.*, b.name AS bookName, c.name AS chapterName, c.number AS chapterNumber
    FROM reading_sessions rs
    INNER JOIN books b ON b.id = rs.bookId
    INNER JOIN chapters c ON c.id = rs.chapterId
    ORDER BY rs.endWallTimeMs DESC, rs.id DESC
    """
  )
  fun observeAllSessionsWithMeta(): Flow<List<ReadingSessionWithMeta>>

  @Query("SELECT COALESCE(SUM(activeFocusMs), 0) FROM reading_sessions WHERE sessionDate = :todayDate")
  fun observeTodayFocusMs(todayDate: String): Flow<Long>

  @Query(
    "SELECT COALESCE(SUM(activeFocusMs), 0) FROM reading_sessions WHERE sessionDate >= :startDate AND sessionDate <= :endDate"
  )
  fun observeTotalFocusMsBetween(startDate: String, endDate: String): Flow<Long>

  @Query(
    """
    SELECT rs.bookId AS bookId, b.name AS bookName, COALESCE(SUM(rs.activeFocusMs), 0) AS totalMs
    FROM reading_sessions rs
    INNER JOIN books b ON b.id = rs.bookId
    WHERE rs.sessionDate >= :startDate AND rs.sessionDate <= :endDate AND rs.activeFocusMs > 0
    GROUP BY rs.bookId, b.name
    ORDER BY totalMs DESC, rs.bookId ASC
    """
  )
  fun observeBookFocusTotalsBetween(startDate: String, endDate: String): Flow<List<BookFocusTotalRow>>

  @Query(
    """
    SELECT sessionDate, COALESCE(SUM(activeFocusMs), 0) AS totalMs
    FROM reading_sessions
    WHERE sessionDate >= :startDate AND sessionDate <= :endDate
    GROUP BY sessionDate
    ORDER BY sessionDate ASC
    """
  )
  fun observeDailyFocusTotalsBetween(startDate: String, endDate: String): Flow<List<DailyFocusTotalRow>>

  @Query("SELECT DISTINCT sessionDate FROM reading_sessions WHERE activeFocusMs > 0 ORDER BY sessionDate DESC")
  fun observeDistinctActiveDatesDescending(): Flow<List<String>>

  @Query("SELECT COUNT(*) FROM reading_sessions")
  fun observeSessionCount(): Flow<Int>

  @Query("SELECT * FROM reading_sessions ORDER BY endWallTimeMs DESC, id DESC")
  suspend fun getAllSessionsOnce(): List<ReadingSessionEntity>

  @Query("SELECT * FROM session_events ORDER BY wallTimeMs ASC, id ASC")
  suspend fun getAllEventsOnce(): List<SessionEventEntity>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertSessions(sessions: List<ReadingSessionEntity>): List<Long>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertEvents(events: List<SessionEventEntity>): List<Long>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGoals(goals: List<ReadingGoalEntity>): List<Long>

  @Query("DELETE FROM session_events")
  suspend fun deleteAllSessionEvents()

  @Query("DELETE FROM reading_sessions")
  suspend fun deleteAllReadingSessions()

  @Query("DELETE FROM reading_goals")
  suspend fun deleteAllReadingGoals()

  @Query("SELECT * FROM reading_goals WHERE isActive = 1 ORDER BY id ASC")
  fun observeActiveGoals(): Flow<List<ReadingGoalEntity>>

  @Query("SELECT * FROM reading_goals ORDER BY id ASC")
  suspend fun getAllGoalsOnce(): List<ReadingGoalEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGoal(goal: ReadingGoalEntity): Long

  @Query("DELETE FROM reading_goals WHERE periodType = :periodType")
  suspend fun deleteGoalsByPeriod(periodType: String)
}
