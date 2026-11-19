package com.example.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
  tableName = "reading_sessions",
  foreignKeys =
    [
      ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = ChapterEntity::class,
        parentColumns = ["id"],
        childColumns = ["chapterId"],
        onDelete = ForeignKey.CASCADE,
      ),
    ],
  indices =
    [
      Index(value = ["sessionId"], unique = true),
      Index(value = ["bookId"]),
      Index(value = ["chapterId"]),
      Index(value = ["sessionDate"]),
      Index(value = ["sessionDate", "startWallTimeMs"]),
      Index(value = ["startWallTimeMs"]),
    ],
)
data class ReadingSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val sessionId: String,
  val bookId: Long,
  val chapterId: Long,
  val mode: String,
  val status: String,
  val startWallTimeMs: Long,
  val endWallTimeMs: Long,
  val activeFocusMs: Long,
  val pauseMs: Long,
  val breakMs: Long,
  val goalMs: Long? = null,
  val notes: String = "",
  val sessionDate: String,
  val createdAt: Long = System.currentTimeMillis(),
)

@Immutable
@Entity(
  tableName = "session_events",
  foreignKeys =
    [
      ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = ChapterEntity::class,
        parentColumns = ["id"],
        childColumns = ["chapterId"],
        onDelete = ForeignKey.CASCADE,
      ),
    ],
  indices =
    [
      Index(value = ["sessionId"]),
      Index(value = ["bookId"]),
      Index(value = ["chapterId"]),
    ],
)
data class SessionEventEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val sessionId: String,
  val bookId: Long,
  val chapterId: Long,
  val eventType: String,
  val elapsedAtEventMs: Long,
  val wallTimeMs: Long,
  val label: String? = null,
)

@Immutable
@Entity(
  tableName = "reading_goals",
  indices = [Index(value = ["periodType"])],
)
data class ReadingGoalEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val periodType: String,
  val targetMinutes: Int,
  val isActive: Boolean = true,
  val createdAt: Long = System.currentTimeMillis(),
)

@Immutable
@Entity(
  tableName = "active_timer_state",
  foreignKeys =
    [
      ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
      ),
      ForeignKey(
        entity = ChapterEntity::class,
        parentColumns = ["id"],
        childColumns = ["chapterId"],
        onDelete = ForeignKey.CASCADE,
      ),
    ],
  indices =
    [
      Index(value = ["bookId"]),
      Index(value = ["chapterId"]),
    ],
)
data class ActiveTimerStateEntity(
  @PrimaryKey val id: Int = 1,
  val sessionId: String,
  val bookId: Long,
  val chapterId: Long,
  val mode: String,
  val status: String,
  val focusDurationMs: Long,
  val breakDurationMs: Long,
  val goalMs: Long? = null,
  val notes: String = "",
  val sessionStartWallTimeMs: Long,
  val phaseStartElapsedMs: Long,
  val phaseStartWallTimeMs: Long,
  val accumulatedFocusMs: Long = 0L,
  val accumulatedBreakMs: Long = 0L,
  val accumulatedPauseMs: Long = 0L,
  val pauseStartElapsedMs: Long? = null,
  val pauseStartWallTimeMs: Long? = null,
  val lastKnownWallTimeMs: Long,
  val lapCount: Int = 0,
  val goalNotified: Boolean = false,
  val pomodoroCycle: Int = 1,
  val statusBeforeStopSummary: String? = null,
)
