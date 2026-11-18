package com.example.data.backup

import android.content.Context
import android.net.Uri
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.preferences.UserPreferences
import java.io.ByteArrayOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object BackupManager {
  const val CURRENT_SCHEMA_VERSION = 1
  const val MAX_BACKUP_BYTES = 20 * 1024 * 1024 // 20 MB guard for malformed/oversized files

  private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  fun createBackupJson(
    appVersion: String,
    settings: UserPreferences,
    books: List<BookEntity>,
    chapters: List<ChapterEntity>,
    exportedAt: Long = System.currentTimeMillis(),
  ): String {
    val bookCreatedAtById = books.associate { it.id to it.createdAt }
    val backup =
      SyllabusBackup(
        appVersion = appVersion.ifBlank { "1.0" },
        schemaVersion = CURRENT_SCHEMA_VERSION,
        exportedAt = exportedAt,
        settings =
          BackupSettings(
            dailyGoal = settings.dailyGoal.coerceIn(1, 50),
            reminderEnabled = settings.reminderEnabled,
            reminderHour = settings.reminderHour.coerceIn(0, 23),
            reminderMinute = settings.reminderMinute.coerceIn(0, 59),
            timerAlarmEnabled = settings.timerAlarmEnabled,
            timerVibrationEnabled = settings.timerVibrationEnabled,
          ),
        books =
          books.map { book ->
            BackupBook(
              id = book.id,
              name = book.name,
              createdAt = book.createdAt,
            )
          },
        chapters =
          chapters.map { chapter ->
            val fallbackCreatedAt =
              chapter.completedAt ?: bookCreatedAtById[chapter.bookId] ?: exportedAt
            BackupChapter(
              id = chapter.id,
              bookId = chapter.bookId,
              name = chapter.name,
              number = chapter.number,
              isCompleted = chapter.isCompleted,
              completedAt = chapter.completedAt,
              createdAt = fallbackCreatedAt,
            )
          },
      )
    return json.encodeToString(backup)
  }

  fun parseAndValidate(rawJson: String): BackupValidationResult {
    if (rawJson.isBlank()) {
      return BackupValidationResult.Invalid("Backup file is empty.")
    }

    val rootElement =
      try {
        json.parseToJsonElement(rawJson)
      } catch (_: Exception) {
        return BackupValidationResult.Invalid("File is not valid JSON.")
      }

    val rootObj =
      rootElement as? JsonObject
        ?: return BackupValidationResult.Invalid("Backup root must be a JSON object.")

    val requiredTopKeys =
      listOf("appVersion", "schemaVersion", "exportedAt", "settings", "books", "chapters")
    for (key in requiredTopKeys) {
      if (!rootObj.containsKey(key)) {
        return BackupValidationResult.Invalid("Missing required field: $key.")
      }
    }

    val rawSchemaVersion = rootObj["schemaVersion"]?.jsonPrimitive?.intOrNull
      ?: return BackupValidationResult.Invalid("Invalid or missing schemaVersion.")
    if (rawSchemaVersion != CURRENT_SCHEMA_VERSION) {
      return BackupValidationResult.Invalid(
        "Unsupported backup schemaVersion: $rawSchemaVersion (expected $CURRENT_SCHEMA_VERSION)."
      )
    }

    val settingsObj = rootObj["settings"] as? JsonObject
      ?: return BackupValidationResult.Invalid("Field 'settings' must be an object.")
    val requiredSettingsKeys = listOf("dailyGoal", "reminderHour", "reminderMinute")
    for (key in requiredSettingsKeys) {
      if (!settingsObj.containsKey(key)) {
        return BackupValidationResult.Invalid("Missing settings field: $key.")
      }
    }

    val booksArr = rootObj["books"] as? JsonArray
      ?: return BackupValidationResult.Invalid("Field 'books' must be an array.")
    for ((index, element) in booksArr.withIndex()) {
      val bookObj = element as? JsonObject
        ?: return BackupValidationResult.Invalid("Book entry #$index must be an object.")
      for (key in listOf("id", "name", "createdAt")) {
        if (!bookObj.containsKey(key)) {
          return BackupValidationResult.Invalid("Book entry #$index is missing '$key'.")
        }
      }
    }

    val chaptersArr = rootObj["chapters"] as? JsonArray
      ?: return BackupValidationResult.Invalid("Field 'chapters' must be an array.")
    for ((index, element) in chaptersArr.withIndex()) {
      val chObj = element as? JsonObject
        ?: return BackupValidationResult.Invalid("Chapter entry #$index must be an object.")
      for (key in listOf("id", "bookId", "name", "number", "isCompleted", "createdAt")) {
        if (!chObj.containsKey(key)) {
          return BackupValidationResult.Invalid("Chapter entry #$index is missing '$key'.")
        }
      }
    }

    val backup =
      try {
        json.decodeFromString<SyllabusBackup>(rawJson)
      } catch (_: Exception) {
        return BackupValidationResult.Invalid("Backup fields have invalid types or formatting.")
      }

    if (backup.appVersion.isBlank()) {
      return BackupValidationResult.Invalid("Backup appVersion must not be blank.")
    }
    if (backup.exportedAt <= 0L) {
      return BackupValidationResult.Invalid("Backup exportedAt timestamp is invalid.")
    }
    if (backup.settings.dailyGoal !in 1..50) {
      return BackupValidationResult.Invalid("Backup dailyGoal must be between 1 and 50.")
    }
    if (backup.settings.reminderHour !in 0..23 || backup.settings.reminderMinute !in 0..59) {
      return BackupValidationResult.Invalid("Backup notification time is out of range.")
    }

    val seenBookIds = HashSet<Long>(backup.books.size)
    for (book in backup.books) {
      if (book.id <= 0L) {
        return BackupValidationResult.Invalid("Book '${book.name}' has an invalid id (${book.id}).")
      }
      if (book.name.isBlank()) {
        return BackupValidationResult.Invalid("Book id ${book.id} has an empty name.")
      }
      if (book.createdAt < 0L) {
        return BackupValidationResult.Invalid("Book '${book.name}' has a negative createdAt.")
      }
      if (!seenBookIds.add(book.id)) {
        return BackupValidationResult.Invalid("Duplicate book id ${book.id} found in backup.")
      }
    }

    val seenChapterIds = HashSet<Long>(backup.chapters.size)
    for (chapter in backup.chapters) {
      if (chapter.id <= 0L) {
        return BackupValidationResult.Invalid("Chapter '${chapter.name}' has an invalid id.")
      }
      if (!seenChapterIds.add(chapter.id)) {
        return BackupValidationResult.Invalid("Duplicate chapter id ${chapter.id} found in backup.")
      }
      if (chapter.bookId !in seenBookIds) {
        return BackupValidationResult.Invalid(
          "Chapter '${chapter.name}' references missing bookId ${chapter.bookId}."
        )
      }
      if (chapter.name.isBlank()) {
        return BackupValidationResult.Invalid("Chapter id ${chapter.id} has an empty name.")
      }
      if (chapter.number <= 0) {
        return BackupValidationResult.Invalid(
          "Chapter '${chapter.name}' has an invalid chapter number (${chapter.number})."
        )
      }
      if (chapter.createdAt < 0L) {
        return BackupValidationResult.Invalid("Chapter '${chapter.name}' has a negative createdAt.")
      }
    }

    return BackupValidationResult.Valid(backup)
  }

  fun readTextFromUri(context: Context, uri: Uri): String {
    val inputStream =
      context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Unable to open selected file.")
    return inputStream.use { stream ->
      val buffer = ByteArray(16 * 1024)
      val out = ByteArrayOutputStream()
      var totalBytes = 0
      while (true) {
        val read = stream.read(buffer)
        if (read == -1) break
        totalBytes += read
        if (totalBytes > MAX_BACKUP_BYTES) {
          throw IllegalArgumentException("Backup file is too large (max 20 MB).")
        }
        out.write(buffer, 0, read)
      }
      out.toString(Charsets.UTF_8.name())
    }
  }

  fun writeTextToUri(context: Context, uri: Uri, content: String) {
    val outputStream =
      context.contentResolver.openOutputStream(uri, "wt")
        ?: context.contentResolver.openOutputStream(uri)
        ?: throw IllegalStateException("Unable to write to selected destination.")
    outputStream.use { stream ->
      stream.write(content.toByteArray(Charsets.UTF_8))
      stream.flush()
    }
  }
}
