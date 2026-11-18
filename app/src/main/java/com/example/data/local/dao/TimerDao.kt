package com.example.data.local.dao

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

data class ReadingSessionWithMeta(
  @Embedded val session: ReadingSessionEntity,
  val bookName: String,
  val chapterName: String,
  val chapterNumber: Int,
)

@Dao
interface TimerDao {
  @Query("SELECT * FROM active_timer_state WHERE id = 1 LIMIT 1")
  fun observeActiveTimerState(): Flow<ActiveTimerStateEntity?>

  @Query("SELECT * FROM active_timer_state WHERE id = 1 LIMIT 1")
  suspend fun getActiveTimerStateOnce(): ActiveTimerStateEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertActiveTimerState(state: ActiveTimerStateEntity)

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

  @Query("SELECT * FROM reading_sessions ORDER BY endWallTimeMs DESC, id DESC")
  suspend fun getAllSessionsOnce(): List<ReadingSessionEntity>

  @Query("SELECT * FROM reading_goals WHERE isActive = 1 ORDER BY id ASC")
  fun observeActiveGoals(): Flow<List<ReadingGoalEntity>>

  @Query("SELECT * FROM reading_goals ORDER BY id ASC")
  suspend fun getAllGoalsOnce(): List<ReadingGoalEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGoal(goal: ReadingGoalEntity): Long

  @Query("DELETE FROM reading_goals WHERE periodType = :periodType")
  suspend fun deleteGoalsByPeriod(periodType: String)
}
