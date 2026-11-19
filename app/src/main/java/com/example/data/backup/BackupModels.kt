package com.example.data.backup

import com.example.data.preferences.UserPreferences
import kotlinx.serialization.Serializable

@Serializable
data class BackupCounts(
  val books: Int = 0,
  val chapters: Int = 0,
  val sessions: Int = 0,
  val sessionEvents: Int = 0,
  val goals: Int = 0,
)

@Serializable
data class SyllabusBackup(
  val appVersion: String,
  val schemaVersion: Int,
  val exportedAt: Long,
  val counts: BackupCounts? = null,
  val settings: BackupSettings,
  val books: List<BackupBook>,
  val chapters: List<BackupChapter>,
  val sessions: List<BackupReadingSession> = emptyList(),
  val sessionEvents: List<BackupSessionEvent> = emptyList(),
  val goals: List<BackupReadingGoal> = emptyList(),
)

@Serializable
data class BackupSettings(
  val dailyGoal: Int,
  val reminderEnabled: Boolean = true,
  val reminderHour: Int,
  val reminderMinute: Int,
  val timerAlarmEnabled: Boolean = true,
  val timerVibrationEnabled: Boolean = true,
  val timerPermissionChecklistShown: Boolean = false,
  val lastOpenedBookId: Long = -1L,
  val pomodoroFocusMinutes: Int = 25,
  val pomodoroBreakMinutes: Int = 5,
)

@Serializable
data class BackupBook(
  val id: Long,
  val name: String,
  val createdAt: Long,
)

@Serializable
data class BackupChapter(
  val id: Long,
  val bookId: Long,
  val name: String,
  val number: Int,
  val isCompleted: Boolean,
  val completedAt: Long? = null,
  val createdAt: Long,
)

@Serializable
data class BackupReadingSession(
  val id: Long = 0L,
  val sessionId: String,
  val bookId: Long,
  val bookName: String = "",
  val chapterId: Long,
  val chapterName: String = "",
  val mode: String,
  val status: String = "SAVED",
  val startWallTimeMs: Long,
  val endWallTimeMs: Long,
  val activeFocusMs: Long,
  val pauseMs: Long = 0L,
  val breakMs: Long = 0L,
  val activeDurationSeconds: Long = (activeFocusMs / 1000L).coerceAtLeast(0L),
  val pauseDurationSeconds: Long = (pauseMs / 1000L).coerceAtLeast(0L),
  val goalMs: Long? = null,
  val notes: String = "",
  val sessionDate: String,
  val createdAt: Long,
)

@Serializable
data class BackupSessionEvent(
  val id: Long = 0L,
  val sessionId: String,
  val bookId: Long,
  val chapterId: Long,
  val eventType: String,
  val elapsedAtEventMs: Long,
  val wallTimeMs: Long,
  val label: String? = null,
)

@Serializable
data class BackupReadingGoal(
  val id: Long = 0L,
  val periodType: String,
  val targetMinutes: Int,
  val isActive: Boolean = true,
  val createdAt: Long,
)

data class BackupExportResult(
  val booksCount: Int,
  val chaptersCount: Int,
  val sessionsCount: Int,
  val sessionEventsCount: Int,
  val goalsCount: Int,
)

data class BackupImportResult(
  val mode: BackupImportMode,
  val booksImported: Int,
  val chaptersImported: Int,
  val sessionsImported: Int,
  val sessionEventsImported: Int,
  val goalsImported: Int,
  val skippedDuplicates: Int,
  val preferences: UserPreferences,
)

enum class BackupImportMode {
  REPLACE,
  MERGE,
}

sealed interface BackupValidationResult {
  data class Valid(val backup: SyllabusBackup) : BackupValidationResult

  data class Invalid(val message: String) : BackupValidationResult
}
