package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.data.local.entity.ReadingGoalEntity
import com.example.data.local.entity.ReadingSessionEntity
import com.example.data.local.entity.SessionEventEntity
import com.example.data.preferences.UserPreferences
import com.example.timer.GoalPeriodType
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate

object BackupManager {
  const val CURRENT_SCHEMA_VERSION = 2
  const val MAX_BACKUP_BYTES = 20 * 1024 * 1024 // 20 MB guard for malformed/oversized files

  private val supportedGoalPeriods =
    setOf(GoalPeriodType.DAILY, GoalPeriodType.WEEKLY, GoalPeriodType.MONTHLY)

  fun buildBackup(
    appVersion: String,
    settings: UserPreferences,
    books: List<BookEntity>,
    chapters: List<ChapterEntity>,
    sessions: List<ReadingSessionEntity> = emptyList(),
    sessionEvents: List<SessionEventEntity> = emptyList(),
    goals: List<ReadingGoalEntity> = emptyList(),
    exportedAt: Long = System.currentTimeMillis(),
  ): SyllabusBackup {
    val bookById = books.associateBy { it.id }
    val chapterById = chapters.associateBy { it.id }

    val backupBooks =
      books.map { book ->
        BackupBook(
          id = book.id,
          name = book.name,
          createdAt = book.createdAt,
        )
      }
    val backupChapters =
      chapters.map { chapter ->
        val fallbackCreatedAt =
          chapter.completedAt ?: bookById[chapter.bookId]?.createdAt ?: exportedAt
        BackupChapter(
          id = chapter.id,
          bookId = chapter.bookId,
          name = chapter.name,
          number = chapter.number,
          isCompleted = chapter.isCompleted,
          completedAt = chapter.completedAt,
          createdAt = fallbackCreatedAt,
        )
      }
    val backupSessions =
      sessions.map { session ->
        BackupReadingSession(
          id = session.id,
          sessionId = session.sessionId,
          bookId = session.bookId,
          bookName = bookById[session.bookId]?.name.orEmpty(),
          chapterId = session.chapterId,
          chapterName = chapterById[session.chapterId]?.name.orEmpty(),
          mode = session.mode,
          status = session.status,
          startWallTimeMs = session.startWallTimeMs,
          endWallTimeMs = session.endWallTimeMs,
          activeFocusMs = session.activeFocusMs,
          pauseMs = session.pauseMs,
          breakMs = session.breakMs,
          activeDurationSeconds = (session.activeFocusMs / 1000L).coerceAtLeast(0L),
          pauseDurationSeconds = (session.pauseMs / 1000L).coerceAtLeast(0L),
          goalMs = session.goalMs,
          notes = session.notes,
          sessionDate = session.sessionDate,
          createdAt = session.createdAt,
        )
      }
    val backupEvents =
      sessionEvents.map { ev ->
        BackupSessionEvent(
          id = ev.id,
          sessionId = ev.sessionId,
          bookId = ev.bookId,
          chapterId = ev.chapterId,
          eventType = ev.eventType,
          elapsedAtEventMs = ev.elapsedAtEventMs,
          wallTimeMs = ev.wallTimeMs,
          label = ev.label,
        )
      }
    val backupGoals =
      goals.map { goal ->
        BackupReadingGoal(
          id = goal.id,
          periodType = goal.periodType,
          targetMinutes = goal.targetMinutes,
          isActive = goal.isActive,
          createdAt = goal.createdAt,
        )
      }

    return SyllabusBackup(
      appVersion = appVersion.ifBlank { "1.0" },
      schemaVersion = CURRENT_SCHEMA_VERSION,
      exportedAt = exportedAt,
      counts =
        BackupCounts(
          books = backupBooks.size,
          chapters = backupChapters.size,
          sessions = backupSessions.size,
          sessionEvents = backupEvents.size,
          goals = backupGoals.size,
        ),
      settings =
        BackupSettings(
          dailyGoal = settings.dailyGoal.coerceIn(1, 50),
          reminderEnabled = settings.reminderEnabled,
          reminderHour = settings.reminderHour.coerceIn(0, 23),
          reminderMinute = settings.reminderMinute.coerceIn(0, 59),
          timerAlarmEnabled = settings.timerAlarmEnabled,
          timerVibrationEnabled = settings.timerVibrationEnabled,
          timerPermissionChecklistShown = settings.timerPermissionChecklistShown,
          lastOpenedBookId = settings.lastOpenedBookId,
          pomodoroFocusMinutes = settings.pomodoroFocusMinutes.coerceIn(1, 180),
          pomodoroBreakMinutes = settings.pomodoroBreakMinutes.coerceIn(1, 60),
        ),
      books = backupBooks,
      chapters = backupChapters,
      sessions = backupSessions,
      sessionEvents = backupEvents,
      goals = backupGoals,
    )
  }

  fun createBackupJson(
    appVersion: String,
    settings: UserPreferences,
    books: List<BookEntity>,
    chapters: List<ChapterEntity>,
    sessions: List<ReadingSessionEntity> = emptyList(),
    sessionEvents: List<SessionEventEntity> = emptyList(),
    goals: List<ReadingGoalEntity> = emptyList(),
    exportedAt: Long = System.currentTimeMillis(),
  ): String {
    val backup =
      buildBackup(
        appVersion = appVersion,
        settings = settings,
        books = books,
        chapters = chapters,
        sessions = sessions,
        sessionEvents = sessionEvents,
        goals = goals,
        exportedAt = exportedAt,
      )
    val out = ByteArrayOutputStream(8192)
    writeBackupStream(out, backup)
    return out.toString(Charsets.UTF_8.name())
  }

  fun writeBackupStream(outputStream: OutputStream, backup: SyllabusBackup) {
    val writer = JsonWriter(BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8), 16 * 1024))
    writer.setIndent("  ")
    val counts =
      backup.counts
        ?: BackupCounts(
          books = backup.books.size,
          chapters = backup.chapters.size,
          sessions = backup.sessions.size,
          sessionEvents = backup.sessionEvents.size,
          goals = backup.goals.size,
        )

    writer.beginObject()
    writer.name("appVersion").value(backup.appVersion)
    writer.name("schemaVersion").value(backup.schemaVersion)
    writer.name("exportedAt").value(backup.exportedAt)

    writer.name("counts")
    writer.beginObject()
    writer.name("books").value(counts.books)
    writer.name("chapters").value(counts.chapters)
    writer.name("sessions").value(counts.sessions)
    writer.name("sessionEvents").value(counts.sessionEvents)
    writer.name("goals").value(counts.goals)
    writer.endObject()

    writer.name("settings")
    writer.beginObject()
    writer.name("dailyGoal").value(backup.settings.dailyGoal)
    writer.name("reminderEnabled").value(backup.settings.reminderEnabled)
    writer.name("reminderHour").value(backup.settings.reminderHour)
    writer.name("reminderMinute").value(backup.settings.reminderMinute)
    writer.name("timerAlarmEnabled").value(backup.settings.timerAlarmEnabled)
    writer.name("timerVibrationEnabled").value(backup.settings.timerVibrationEnabled)
    writer.name("timerPermissionChecklistShown").value(backup.settings.timerPermissionChecklistShown)
    writer.name("lastOpenedBookId").value(backup.settings.lastOpenedBookId)
    writer.name("pomodoroFocusMinutes").value(backup.settings.pomodoroFocusMinutes)
    writer.name("pomodoroBreakMinutes").value(backup.settings.pomodoroBreakMinutes)
    writer.endObject()

    writer.name("books")
    writer.beginArray()
    for (book in backup.books) {
      writer.beginObject()
      writer.name("id").value(book.id)
      writer.name("name").value(book.name)
      writer.name("createdAt").value(book.createdAt)
      writer.endObject()
    }
    writer.endArray()

    writer.name("chapters")
    writer.beginArray()
    for (chapter in backup.chapters) {
      writer.beginObject()
      writer.name("id").value(chapter.id)
      writer.name("bookId").value(chapter.bookId)
      writer.name("name").value(chapter.name)
      writer.name("number").value(chapter.number)
      writer.name("isCompleted").value(chapter.isCompleted)
      writer.name("completedAt")
      if (chapter.completedAt != null) {
        writer.value(chapter.completedAt)
      } else {
        writer.nullValue()
      }
      writer.name("createdAt").value(chapter.createdAt)
      writer.endObject()
    }
    writer.endArray()

    writer.name("sessions")
    writer.beginArray()
    for (session in backup.sessions) {
      writer.beginObject()
      writer.name("id").value(session.id)
      writer.name("sessionId").value(session.sessionId)
      writer.name("bookId").value(session.bookId)
      writer.name("bookName").value(session.bookName)
      writer.name("chapterId").value(session.chapterId)
      writer.name("chapterName").value(session.chapterName)
      writer.name("mode").value(session.mode)
      writer.name("status").value(session.status)
      writer.name("startWallTimeMs").value(session.startWallTimeMs)
      writer.name("endWallTimeMs").value(session.endWallTimeMs)
      writer.name("activeFocusMs").value(session.activeFocusMs)
      writer.name("pauseMs").value(session.pauseMs)
      writer.name("breakMs").value(session.breakMs)
      writer.name("activeDurationSeconds").value(session.activeDurationSeconds)
      writer.name("pauseDurationSeconds").value(session.pauseDurationSeconds)
      writer.name("goalMs")
      if (session.goalMs != null) {
        writer.value(session.goalMs)
      } else {
        writer.nullValue()
      }
      writer.name("notes").value(session.notes)
      writer.name("sessionDate").value(session.sessionDate)
      writer.name("createdAt").value(session.createdAt)
      writer.endObject()
    }
    writer.endArray()

    writer.name("sessionEvents")
    writer.beginArray()
    for (event in backup.sessionEvents) {
      writer.beginObject()
      writer.name("id").value(event.id)
      writer.name("sessionId").value(event.sessionId)
      writer.name("bookId").value(event.bookId)
      writer.name("chapterId").value(event.chapterId)
      writer.name("eventType").value(event.eventType)
      writer.name("elapsedAtEventMs").value(event.elapsedAtEventMs)
      writer.name("wallTimeMs").value(event.wallTimeMs)
      writer.name("label")
      if (event.label != null) {
        writer.value(event.label)
      } else {
        writer.nullValue()
      }
      writer.endObject()
    }
    writer.endArray()

    writer.name("goals")
    writer.beginArray()
    for (goal in backup.goals) {
      writer.beginObject()
      writer.name("id").value(goal.id)
      writer.name("periodType").value(goal.periodType)
      writer.name("targetMinutes").value(goal.targetMinutes)
      writer.name("isActive").value(goal.isActive)
      writer.name("createdAt").value(goal.createdAt)
      writer.endObject()
    }
    writer.endArray()

    writer.endObject()
    writer.flush()
  }

  fun writeBackupToUri(context: Context, uri: Uri, backup: SyllabusBackup) {
    val outputStream =
      context.contentResolver.openOutputStream(uri, "wt")
        ?: context.contentResolver.openOutputStream(uri)
        ?: throw IllegalStateException("Unable to write to selected destination.")
    outputStream.use { stream ->
      writeBackupStream(stream, backup)
      stream.flush()
    }
  }

  fun readAndValidateUri(
    context: Context,
    uri: Uri,
    existingBookIds: Set<Long> = emptySet(),
    existingChapterIds: Set<Long> = emptySet(),
  ): BackupValidationResult {
    val inputStream =
      context.contentResolver.openInputStream(uri)
        ?: return BackupValidationResult.Invalid("Unable to open selected file.")
    return inputStream.use { stream ->
      readAndValidateStream(
        inputStream = stream,
        existingBookIds = existingBookIds,
        existingChapterIds = existingChapterIds,
      )
    }
  }

  fun parseAndValidate(
    rawJson: String,
    existingBookIds: Set<Long> = emptySet(),
    existingChapterIds: Set<Long> = emptySet(),
  ): BackupValidationResult {
    if (rawJson.isBlank()) {
      return BackupValidationResult.Invalid("Backup file is empty.")
    }
    return readAndValidateStream(
      inputStream = ByteArrayInputStream(rawJson.toByteArray(Charsets.UTF_8)),
      existingBookIds = existingBookIds,
      existingChapterIds = existingChapterIds,
    )
  }

  fun readAndValidateStream(
    inputStream: InputStream,
    existingBookIds: Set<Long> = emptySet(),
    existingChapterIds: Set<Long> = emptySet(),
  ): BackupValidationResult {
    val limitedStream = ByteLimitInputStream(inputStream, MAX_BACKUP_BYTES)
    val reader = JsonReader(BufferedReader(InputStreamReader(limitedStream, Charsets.UTF_8), 16 * 1024))

    var appVersion: String? = null
    var schemaVersion: Int? = null
    var exportedAt: Long? = null
    var counts: BackupCounts? = null
    var settings: BackupSettings? = null
    var books: List<BackupBook>? = null
    var chapters: List<BackupChapter>? = null
    var sessions: List<BackupReadingSession>? = null
    var sessionEvents: List<BackupSessionEvent>? = null
    var goals: List<BackupReadingGoal>? = null

    try {
      if (reader.peek() == JsonToken.END_DOCUMENT) {
        return BackupValidationResult.Invalid("Backup file is empty.")
      }
      if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        return BackupValidationResult.Invalid("Backup root must be a JSON object.")
      }
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "appVersion" -> appVersion = reader.nextString()
          "schemaVersion" -> schemaVersion = reader.nextInt()
          "exportedAt" -> exportedAt = reader.nextLong()
          "counts" -> {
            if (reader.peek() == JsonToken.NULL) {
              reader.nextNull()
            } else {
              counts = readCounts(reader)
            }
          }
          "settings" -> settings = readSettings(reader)
          "books" -> books = readBooks(reader)
          "chapters" -> chapters = readChapters(reader)
          "sessions" -> {
            if (reader.peek() == JsonToken.NULL) {
              reader.nextNull()
            } else {
              sessions = readSessions(reader)
            }
          }
          "sessionEvents" -> {
            if (reader.peek() == JsonToken.NULL) {
              reader.nextNull()
            } else {
              sessionEvents = readSessionEvents(reader)
            }
          }
          "goals" -> {
            if (reader.peek() == JsonToken.NULL) {
              reader.nextNull()
            } else {
              goals = readGoals(reader)
            }
          }
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      if (reader.peek() != JsonToken.END_DOCUMENT) {
        return BackupValidationResult.Invalid("File is not valid JSON.")
      }
    } catch (e: BackupLimitExceededException) {
      return BackupValidationResult.Invalid(e.message ?: "Backup file is too large (max 20 MB).")
    } catch (e: BackupParseException) {
      return BackupValidationResult.Invalid(e.message ?: "Backup fields have invalid formatting.")
    } catch (_: Exception) {
      return BackupValidationResult.Invalid("File is not valid JSON or has invalid field types.")
    }

    val validAppVersion =
      appVersion ?: return BackupValidationResult.Invalid("Missing required field: appVersion.")
    val validSchemaVersion =
      schemaVersion ?: return BackupValidationResult.Invalid("Missing required field: schemaVersion.")
    val validExportedAt =
      exportedAt ?: return BackupValidationResult.Invalid("Missing required field: exportedAt.")
    val validSettings =
      settings ?: return BackupValidationResult.Invalid("Missing required field: settings.")
    val validBooks =
      books ?: return BackupValidationResult.Invalid("Missing required field: books.")
    val validChapters =
      chapters ?: return BackupValidationResult.Invalid("Missing required field: chapters.")
    val validSessions = sessions ?: emptyList()
    val validEvents = sessionEvents ?: emptyList()
    val validGoals = goals ?: emptyList()

    if (validSchemaVersion !in 1..CURRENT_SCHEMA_VERSION) {
      return BackupValidationResult.Invalid(
        "Unsupported backup schemaVersion: $validSchemaVersion (supported: 1..$CURRENT_SCHEMA_VERSION)."
      )
    }
    if (validAppVersion.isBlank()) {
      return BackupValidationResult.Invalid("Backup appVersion must not be blank.")
    }
    if (validExportedAt <= 0L) {
      return BackupValidationResult.Invalid("Backup exportedAt timestamp is invalid.")
    }
    if (validSettings.dailyGoal !in 1..50) {
      return BackupValidationResult.Invalid("Backup dailyGoal must be between 1 and 50.")
    }
    if (validSettings.reminderHour !in 0..23 || validSettings.reminderMinute !in 0..59) {
      return BackupValidationResult.Invalid("Backup notification time is out of range.")
    }
    if (validSettings.pomodoroFocusMinutes !in 1..180 || validSettings.pomodoroBreakMinutes !in 1..60) {
      return BackupValidationResult.Invalid("Backup Pomodoro durations are out of range.")
    }

    val seenBookIds = HashSet<Long>(validBooks.size)
    for (book in validBooks) {
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

    val seenChapterIds = HashSet<Long>(validChapters.size)
    for (chapter in validChapters) {
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

    val allowedBookIds = seenBookIds + existingBookIds
    val allowedChapterIds = seenChapterIds + existingChapterIds

    val seenSessionIds = HashSet<String>(validSessions.size)
    val seenSessionRowIds = HashSet<Long>(validSessions.size)
    for ((idx, session) in validSessions.withIndex()) {
      if (session.id < 0L) {
        return BackupValidationResult.Invalid("Session #$idx has a negative id.")
      }
      if (session.id > 0L && !seenSessionRowIds.add(session.id)) {
        return BackupValidationResult.Invalid("Duplicate session row id ${session.id} found in backup.")
      }
      if (session.sessionId.isBlank()) {
        return BackupValidationResult.Invalid("Session #$idx has an empty sessionId.")
      }
      if (!seenSessionIds.add(session.sessionId)) {
        return BackupValidationResult.Invalid("Duplicate sessionId '${session.sessionId}' found in backup.")
      }
      if (session.bookId <= 0L || session.bookId !in allowedBookIds) {
        return BackupValidationResult.Invalid(
          "Session '${session.sessionId}' references missing bookId ${session.bookId}."
        )
      }
      if (session.chapterId <= 0L || session.chapterId !in allowedChapterIds) {
        return BackupValidationResult.Invalid(
          "Session '${session.sessionId}' references missing chapterId ${session.chapterId}."
        )
      }
      if (session.mode.isBlank()) {
        return BackupValidationResult.Invalid("Session '${session.sessionId}' has an empty mode.")
      }
      if (session.startWallTimeMs < 0L || session.endWallTimeMs < 0L || session.createdAt < 0L) {
        return BackupValidationResult.Invalid("Session '${session.sessionId}' has negative timestamps.")
      }
      if (session.activeFocusMs < 0L || session.pauseMs < 0L || session.breakMs < 0L) {
        return BackupValidationResult.Invalid("Session '${session.sessionId}' has negative durations.")
      }
      if (session.sessionDate.isBlank() || runCatching { LocalDate.parse(session.sessionDate) }.isFailure) {
        return BackupValidationResult.Invalid(
          "Session '${session.sessionId}' has invalid sessionDate '${session.sessionDate}'."
        )
      }
    }

    val seenEventRowIds = HashSet<Long>(validEvents.size)
    for ((idx, event) in validEvents.withIndex()) {
      if (event.id < 0L) {
        return BackupValidationResult.Invalid("Session event #$idx has a negative id.")
      }
      if (event.id > 0L && !seenEventRowIds.add(event.id)) {
        return BackupValidationResult.Invalid("Duplicate session event id ${event.id} found in backup.")
      }
      if (event.sessionId.isBlank()) {
        return BackupValidationResult.Invalid("Session event #$idx has an empty sessionId.")
      }
      if (event.bookId <= 0L || event.bookId !in allowedBookIds) {
        return BackupValidationResult.Invalid(
          "Session event #$idx references missing bookId ${event.bookId}."
        )
      }
      if (event.chapterId <= 0L || event.chapterId !in allowedChapterIds) {
        return BackupValidationResult.Invalid(
          "Session event #$idx references missing chapterId ${event.chapterId}."
        )
      }
      if (event.eventType.isBlank()) {
        return BackupValidationResult.Invalid("Session event #$idx has an empty eventType.")
      }
      if (event.elapsedAtEventMs < 0L || event.wallTimeMs < 0L) {
        return BackupValidationResult.Invalid("Session event #$idx has negative timestamps.")
      }
    }

    val seenGoalIds = HashSet<Long>(validGoals.size)
    for ((idx, goal) in validGoals.withIndex()) {
      if (goal.id < 0L) {
        return BackupValidationResult.Invalid("Reading goal #$idx has a negative id.")
      }
      if (goal.id > 0L && !seenGoalIds.add(goal.id)) {
        return BackupValidationResult.Invalid("Duplicate reading goal id ${goal.id} found in backup.")
      }
      if (goal.periodType !in supportedGoalPeriods) {
        return BackupValidationResult.Invalid(
          "Reading goal #$idx has unsupported periodType '${goal.periodType}'."
        )
      }
      if (goal.targetMinutes <= 0) {
        return BackupValidationResult.Invalid(
          "Reading goal #$idx has invalid targetMinutes (${goal.targetMinutes})."
        )
      }
      if (goal.createdAt < 0L) {
        return BackupValidationResult.Invalid("Reading goal #$idx has negative createdAt.")
      }
    }

    return BackupValidationResult.Valid(
      SyllabusBackup(
        appVersion = validAppVersion,
        schemaVersion = validSchemaVersion,
        exportedAt = validExportedAt,
        counts =
          counts
            ?: BackupCounts(
              books = validBooks.size,
              chapters = validChapters.size,
              sessions = validSessions.size,
              sessionEvents = validEvents.size,
              goals = validGoals.size,
            ),
        settings = validSettings,
        books = validBooks,
        chapters = validChapters,
        sessions = validSessions,
        sessionEvents = validEvents,
        goals = validGoals,
      )
    )
  }

  private fun readCounts(reader: JsonReader): BackupCounts {
    var books = 0
    var chapters = 0
    var sessions = 0
    var sessionEvents = 0
    var goals = 0
    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "books" -> books = reader.nextInt()
        "chapters" -> chapters = reader.nextInt()
        "sessions" -> sessions = reader.nextInt()
        "sessionEvents" -> sessionEvents = reader.nextInt()
        "goals" -> goals = reader.nextInt()
        else -> reader.skipValue()
      }
    }
    reader.endObject()
    return BackupCounts(books, chapters, sessions, sessionEvents, goals)
  }

  private fun readSettings(reader: JsonReader): BackupSettings {
    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
      throw BackupParseException("Field 'settings' must be an object.")
    }
    var dailyGoal: Int? = null
    var reminderEnabled = true
    var reminderHour: Int? = null
    var reminderMinute: Int? = null
    var timerAlarmEnabled = true
    var timerVibrationEnabled = true
    var timerPermissionChecklistShown = false
    var lastOpenedBookId = -1L
    var pomodoroFocusMinutes = UserPreferences.DEFAULT_POMODORO_FOCUS_MINUTES
    var pomodoroBreakMinutes = UserPreferences.DEFAULT_POMODORO_BREAK_MINUTES

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "dailyGoal" -> dailyGoal = reader.nextInt()
        "reminderEnabled" -> reminderEnabled = reader.nextBoolean()
        "reminderHour" -> reminderHour = reader.nextInt()
        "reminderMinute" -> reminderMinute = reader.nextInt()
        "timerAlarmEnabled" -> timerAlarmEnabled = reader.nextBoolean()
        "timerVibrationEnabled" -> timerVibrationEnabled = reader.nextBoolean()
        "timerPermissionChecklistShown" -> timerPermissionChecklistShown = reader.nextBoolean()
        "lastOpenedBookId" -> lastOpenedBookId = reader.nextLong()
        "pomodoroFocusMinutes" -> pomodoroFocusMinutes = reader.nextInt()
        "pomodoroBreakMinutes" -> pomodoroBreakMinutes = reader.nextInt()
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return BackupSettings(
      dailyGoal = dailyGoal ?: throw BackupParseException("Missing settings field: dailyGoal."),
      reminderEnabled = reminderEnabled,
      reminderHour = reminderHour ?: throw BackupParseException("Missing settings field: reminderHour."),
      reminderMinute =
        reminderMinute ?: throw BackupParseException("Missing settings field: reminderMinute."),
      timerAlarmEnabled = timerAlarmEnabled,
      timerVibrationEnabled = timerVibrationEnabled,
      timerPermissionChecklistShown = timerPermissionChecklistShown,
      lastOpenedBookId = lastOpenedBookId,
      pomodoroFocusMinutes = pomodoroFocusMinutes,
      pomodoroBreakMinutes = pomodoroBreakMinutes,
    )
  }

  private fun readBooks(reader: JsonReader): List<BackupBook> {
    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
      throw BackupParseException("Field 'books' must be an array.")
    }
    val list = ArrayList<BackupBook>()
    reader.beginArray()
    var index = 0
    while (reader.hasNext()) {
      if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        throw BackupParseException("Book entry #$index must be an object.")
      }
      var id: Long? = null
      var name: String? = null
      var createdAt: Long? = null
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "id" -> id = reader.nextLong()
          "name" -> name = reader.nextString()
          "createdAt" -> createdAt = reader.nextLong()
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      list.add(
        BackupBook(
          id = id ?: throw BackupParseException("Book entry #$index is missing 'id'."),
          name = name ?: throw BackupParseException("Book entry #$index is missing 'name'."),
          createdAt =
            createdAt ?: throw BackupParseException("Book entry #$index is missing 'createdAt'."),
        )
      )
      index++
    }
    reader.endArray()
    return list
  }

  private fun readChapters(reader: JsonReader): List<BackupChapter> {
    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
      throw BackupParseException("Field 'chapters' must be an array.")
    }
    val list = ArrayList<BackupChapter>()
    reader.beginArray()
    var index = 0
    while (reader.hasNext()) {
      if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        throw BackupParseException("Chapter entry #$index must be an object.")
      }
      var id: Long? = null
      var bookId: Long? = null
      var name: String? = null
      var number: Int? = null
      var isCompleted: Boolean? = null
      var completedAt: Long? = null
      var createdAt: Long? = null
      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "id" -> id = reader.nextLong()
          "bookId" -> bookId = reader.nextLong()
          "name" -> name = reader.nextString()
          "number" -> number = reader.nextInt()
          "isCompleted" -> isCompleted = reader.nextBoolean()
          "completedAt" -> {
            completedAt =
              if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                null
              } else {
                reader.nextLong()
              }
          }
          "createdAt" -> createdAt = reader.nextLong()
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      list.add(
        BackupChapter(
          id = id ?: throw BackupParseException("Chapter entry #$index is missing 'id'."),
          bookId = bookId ?: throw BackupParseException("Chapter entry #$index is missing 'bookId'."),
          name = name ?: throw BackupParseException("Chapter entry #$index is missing 'name'."),
          number = number ?: throw BackupParseException("Chapter entry #$index is missing 'number'."),
          isCompleted =
            isCompleted ?: throw BackupParseException("Chapter entry #$index is missing 'isCompleted'."),
          completedAt = completedAt,
          createdAt =
            createdAt ?: throw BackupParseException("Chapter entry #$index is missing 'createdAt'."),
        )
      )
      index++
    }
    reader.endArray()
    return list
  }

  private fun readSessions(reader: JsonReader): List<BackupReadingSession> {
    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
      throw BackupParseException("Field 'sessions' must be an array.")
    }
    val list = ArrayList<BackupReadingSession>()
    reader.beginArray()
    var index = 0
    while (reader.hasNext()) {
      if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        throw BackupParseException("Session entry #$index must be an object.")
      }
      var id = 0L
      var sessionId: String? = null
      var bookId: Long? = null
      var bookName = ""
      var chapterId: Long? = null
      var chapterName = ""
      var mode: String? = null
      var status = "SAVED"
      var startWallTimeMs: Long? = null
      var endWallTimeMs: Long? = null
      var activeFocusMs: Long? = null
      var pauseMs: Long? = null
      var breakMs = 0L
      var activeSecondsFallback: Long? = null
      var pauseSecondsFallback: Long? = null
      var goalMs: Long? = null
      var notes = ""
      var sessionDate: String? = null
      var createdAt: Long? = null

      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "id" -> id = reader.nextLong()
          "sessionId" -> sessionId = reader.nextString()
          "bookId" -> bookId = reader.nextLong()
          "bookName" -> bookName = reader.nextNullableString().orEmpty()
          "chapterId" -> chapterId = reader.nextLong()
          "chapterName" -> chapterName = reader.nextNullableString().orEmpty()
          "mode" -> mode = reader.nextString()
          "status", "sessionStatus" -> status = reader.nextString()
          "startWallTimeMs", "startTime" -> startWallTimeMs = reader.nextLong()
          "endWallTimeMs", "endTime" -> endWallTimeMs = reader.nextLong()
          "activeFocusMs" -> activeFocusMs = reader.nextLong()
          "pauseMs" -> pauseMs = reader.nextLong()
          "breakMs" -> breakMs = reader.nextLong()
          "activeDurationSeconds" -> activeSecondsFallback = reader.nextLong()
          "pauseDurationSeconds" -> pauseSecondsFallback = reader.nextLong()
          "goalMs", "goal" -> {
            goalMs =
              if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                null
              } else {
                reader.nextLong()
              }
          }
          "notes" -> notes = reader.nextNullableString().orEmpty()
          "sessionDate" -> sessionDate = reader.nextString()
          "createdAt" -> createdAt = reader.nextLong()
          else -> reader.skipValue()
        }
      }
      reader.endObject()

      val resolvedStart =
        startWallTimeMs ?: throw BackupParseException("Session entry #$index is missing 'startWallTimeMs'.")
      val resolvedEnd =
        endWallTimeMs ?: throw BackupParseException("Session entry #$index is missing 'endWallTimeMs'.")
      val resolvedActiveMs =
        activeFocusMs
          ?: activeSecondsFallback?.let { it * 1000L }
          ?: throw BackupParseException("Session entry #$index is missing 'activeFocusMs'.")
      val resolvedPauseMs = pauseMs ?: pauseSecondsFallback?.let { it * 1000L } ?: 0L

      list.add(
        BackupReadingSession(
          id = id,
          sessionId = sessionId ?: throw BackupParseException("Session entry #$index is missing 'sessionId'."),
          bookId = bookId ?: throw BackupParseException("Session entry #$index is missing 'bookId'."),
          bookName = bookName,
          chapterId = chapterId ?: throw BackupParseException("Session entry #$index is missing 'chapterId'."),
          chapterName = chapterName,
          mode = mode ?: throw BackupParseException("Session entry #$index is missing 'mode'."),
          status = status,
          startWallTimeMs = resolvedStart,
          endWallTimeMs = resolvedEnd,
          activeFocusMs = resolvedActiveMs,
          pauseMs = resolvedPauseMs,
          breakMs = breakMs,
          activeDurationSeconds = activeSecondsFallback ?: (resolvedActiveMs / 1000L).coerceAtLeast(0L),
          pauseDurationSeconds = pauseSecondsFallback ?: (resolvedPauseMs / 1000L).coerceAtLeast(0L),
          goalMs = goalMs,
          notes = notes,
          sessionDate =
            sessionDate ?: throw BackupParseException("Session entry #$index is missing 'sessionDate'."),
          createdAt = createdAt ?: resolvedEnd,
        )
      )
      index++
    }
    reader.endArray()
    return list
  }

  private fun readSessionEvents(reader: JsonReader): List<BackupSessionEvent> {
    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
      throw BackupParseException("Field 'sessionEvents' must be an array.")
    }
    val list = ArrayList<BackupSessionEvent>()
    reader.beginArray()
    var index = 0
    while (reader.hasNext()) {
      if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        throw BackupParseException("Session event entry #$index must be an object.")
      }
      var id = 0L
      var sessionId: String? = null
      var bookId: Long? = null
      var chapterId: Long? = null
      var eventType: String? = null
      var elapsedAtEventMs: Long? = null
      var wallTimeMs: Long? = null
      var label: String? = null

      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "id" -> id = reader.nextLong()
          "sessionId" -> sessionId = reader.nextString()
          "bookId" -> bookId = reader.nextLong()
          "chapterId" -> chapterId = reader.nextLong()
          "eventType" -> eventType = reader.nextString()
          "elapsedAtEventMs" -> elapsedAtEventMs = reader.nextLong()
          "wallTimeMs" -> wallTimeMs = reader.nextLong()
          "label" -> label = reader.nextNullableString()
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      list.add(
        BackupSessionEvent(
          id = id,
          sessionId =
            sessionId ?: throw BackupParseException("Session event #$index is missing 'sessionId'."),
          bookId = bookId ?: throw BackupParseException("Session event #$index is missing 'bookId'."),
          chapterId =
            chapterId ?: throw BackupParseException("Session event #$index is missing 'chapterId'."),
          eventType =
            eventType ?: throw BackupParseException("Session event #$index is missing 'eventType'."),
          elapsedAtEventMs =
            elapsedAtEventMs
              ?: throw BackupParseException("Session event #$index is missing 'elapsedAtEventMs'."),
          wallTimeMs =
            wallTimeMs ?: throw BackupParseException("Session event #$index is missing 'wallTimeMs'."),
          label = label,
        )
      )
      index++
    }
    reader.endArray()
    return list
  }

  private fun readGoals(reader: JsonReader): List<BackupReadingGoal> {
    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
      throw BackupParseException("Field 'goals' must be an array.")
    }
    val list = ArrayList<BackupReadingGoal>()
    reader.beginArray()
    var index = 0
    while (reader.hasNext()) {
      if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        throw BackupParseException("Reading goal #$index must be an object.")
      }
      var id = 0L
      var periodType: String? = null
      var targetMinutes: Int? = null
      var isActive = true
      var createdAt = System.currentTimeMillis()

      reader.beginObject()
      while (reader.hasNext()) {
        when (reader.nextName()) {
          "id" -> id = reader.nextLong()
          "periodType" -> periodType = reader.nextString()
          "targetMinutes" -> targetMinutes = reader.nextInt()
          "isActive" -> isActive = reader.nextBoolean()
          "createdAt" -> createdAt = reader.nextLong()
          else -> reader.skipValue()
        }
      }
      reader.endObject()
      list.add(
        BackupReadingGoal(
          id = id,
          periodType =
            periodType ?: throw BackupParseException("Reading goal #$index is missing 'periodType'."),
          targetMinutes =
            targetMinutes
              ?: throw BackupParseException("Reading goal #$index is missing 'targetMinutes'."),
          isActive = isActive,
          createdAt = createdAt,
        )
      )
      index++
    }
    reader.endArray()
    return list
  }

  private fun JsonReader.nextNullableString(): String? {
    return if (peek() == JsonToken.NULL) {
      nextNull()
      null
    } else {
      nextString()
    }
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

  private class BackupParseException(message: String) : IllegalArgumentException(message)

  private class BackupLimitExceededException(message: String) : IllegalArgumentException(message)

  private class ByteLimitInputStream(
    delegate: InputStream,
    private val maxBytes: Int,
  ) : FilterInputStream(delegate) {
    private var totalRead = 0L

    override fun read(): Int {
      val b = super.read()
      if (b != -1) {
        totalRead++
        if (totalRead > maxBytes) {
          throw BackupLimitExceededException("Backup file is too large (max 20 MB).")
        }
      }
      return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      val count = super.read(b, off, len)
      if (count > 0) {
        totalRead += count
        if (totalRead > maxBytes) {
          throw BackupLimitExceededException("Backup file is too large (max 20 MB).")
        }
      }
      return count
    }
  }
}
