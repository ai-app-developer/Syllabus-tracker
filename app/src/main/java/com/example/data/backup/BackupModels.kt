package com.example.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class SyllabusBackup(
  val appVersion: String,
  val schemaVersion: Int,
  val exportedAt: Long,
  val settings: BackupSettings,
  val books: List<BackupBook>,
  val chapters: List<BackupChapter>,
)

@Serializable
data class BackupSettings(
  val dailyGoal: Int,
  val reminderEnabled: Boolean = true,
  val reminderHour: Int,
  val reminderMinute: Int,
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

enum class BackupImportMode {
  REPLACE,
  MERGE,
}

sealed interface BackupValidationResult {
  data class Valid(val backup: SyllabusBackup) : BackupValidationResult

  data class Invalid(val message: String) : BackupValidationResult
}
