package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.backup.BackupImportMode
import com.example.data.backup.BackupImportResult
import com.example.data.backup.SyllabusBackup
import com.example.data.local.entity.BookEntity
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.DeleteConfirmState

@Composable
fun ImportBackupConfirmDialog(
  backup: SyllabusBackup,
  onDismiss: () -> Unit,
  onSelectMode: (BackupImportMode) -> Unit,
) {
  val colors = AppTheme.colors
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.dialogSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Text(
        text = "Import Backup",
        style = MaterialTheme.typography.titleLarge,
        color = colors.textPrimary,
      )
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text =
            "Found ${backup.books.size} ${if (backup.books.size == 1) "book" else "books"}, " +
              "${backup.chapters.size} ${if (backup.chapters.size == 1) "chapter" else "chapters"}, " +
              "${backup.sessions.size} ${if (backup.sessions.size == 1) "session" else "sessions"}, and " +
              "${backup.goals.size} ${if (backup.goals.size == 1) "goal" else "goals"}. Choose how to import:",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
          onClick = { onSelectMode(BackupImportMode.REPLACE) },
          shape = RoundedCornerShape(12.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = colors.primarySolid,
              contentColor = colors.onPrimary,
            ),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("import_replace_button"),
        ) {
          Text("Replace existing data", color = colors.onPrimary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = { onSelectMode(BackupImportMode.MERGE) },
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, colors.border),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("import_merge_button"),
        ) {
          Text("Merge with existing data", color = colors.textPrimary)
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("import_cancel_button"),
      ) {
        Text("Cancel", color = colors.textSecondary)
      }
    },
  )
}

@Composable
fun ImportBackupResultDialog(
  result: BackupImportResult,
  onDismiss: () -> Unit,
) {
  val colors = AppTheme.colors
  val summaryText =
    buildString {
      append(
        "Imported: ${result.booksImported} books, ${result.chaptersImported} chapters, " +
          "${result.sessionsImported} sessions, ${result.goalsImported} goals"
      )
      if (result.mode == BackupImportMode.MERGE && result.skippedDuplicates > 0) {
        append("\nSkipped duplicates: ${result.skippedDuplicates}")
      }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.dialogSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Text(
        text = "Import Complete",
        style = MaterialTheme.typography.titleLarge,
        color = colors.textPrimary,
      )
    },
    text = {
      Text(
        text = summaryText,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
      )
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        shape = RoundedCornerShape(12.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = colors.primarySolid,
            contentColor = colors.onPrimary,
          ),
        modifier = Modifier.testTag("import_result_ok_button"),
      ) {
        Text("OK", color = colors.onPrimary)
      }
    },
  )
}

@Composable
fun BackupErrorDialog(
  message: String,
  onDismiss: () -> Unit,
) {
  val colors = AppTheme.colors
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.dialogSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Text(
        text = "Backup Error",
        style = MaterialTheme.typography.titleLarge,
        color = colors.textPrimary,
      )
    },
    text = {
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
      )
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        shape = RoundedCornerShape(12.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = colors.primarySolid,
            contentColor = colors.onPrimary,
          ),
        modifier = Modifier.testTag("backup_error_ok_button"),
      ) {
        Text("OK", color = colors.onPrimary)
      }
    },
  )
}

@Composable
fun BookFormDialog(
  editingBook: BookEntity?,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  val colors = AppTheme.colors
  var name by remember(editingBook) { mutableStateOf(editingBook?.name ?: "") }
  var showError by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.dialogSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Text(
        text = if (editingBook == null) "Add Book" else "Rename Book",
        style = MaterialTheme.typography.titleLarge,
        color = colors.textPrimary,
      )
    },
    text = {
      Column {
        Text(
          text = "Enter the subject or textbook title.",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            if (showError && it.isNotBlank()) showError = false
          },
          label = { Text("Book Name") },
          placeholder = { Text("e.g. Mathematics", color = colors.textSecondary) },
          singleLine = true,
          isError = showError,
          supportingText =
            if (showError) {
              { Text("Book name is required", color = colors.error) }
            } else {
              null
            },
          shape = RoundedCornerShape(12.dp),
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = colors.primary,
              unfocusedBorderColor = colors.border,
              focusedContainerColor = colors.inputSurface,
              unfocusedContainerColor = colors.inputSurface,
              focusedTextColor = colors.textPrimary,
              unfocusedTextColor = colors.textPrimary,
              focusedLabelColor = colors.primary,
              unfocusedLabelColor = colors.textSecondary,
              cursorColor = colors.primary,
            ),
          modifier = Modifier.fillMaxWidth().testTag("book_name_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isBlank()) {
            showError = true
          } else {
            onConfirm(name.trim())
          }
        },
        shape = RoundedCornerShape(12.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = colors.primarySolid,
            contentColor = colors.onPrimary,
          ),
        modifier = Modifier.testTag("save_book_button"),
      ) {
        Text(if (editingBook == null) "Add" else "Save", color = colors.onPrimary)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = colors.textSecondary)
      }
    },
  )
}

@Composable
fun ConfirmDeleteDialog(
  state: DeleteConfirmState,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  if (state is DeleteConfirmState.None) return
  val colors = AppTheme.colors

  val (title, body) =
    when (state) {
      is DeleteConfirmState.DeleteBook ->
        "Delete \"${state.book.name}\"?" to
          "This will also remove all chapters inside this book. This action cannot be undone."
      is DeleteConfirmState.DeleteChapter ->
        "Delete \"${state.chapter.name}\"?" to
          "Chapter ${state.chapter.number} will be permanently deleted."
      DeleteConfirmState.None -> "" to ""
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.dialogSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = colors.textPrimary,
      )
    },
    text = {
      Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.textSecondary,
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        shape = RoundedCornerShape(12.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = colors.error,
            contentColor = colors.onPrimary,
          ),
        modifier = Modifier.testTag("confirm_delete_button"),
      ) {
        Text("Delete", color = colors.onPrimary)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = colors.textSecondary)
      }
    },
  )
}

@Composable
fun DailyGoalDialog(
  currentGoal: Int,
  onDismiss: () -> Unit,
  onSave: (Int) -> Unit,
) {
  val colors = AppTheme.colors
  var goal by remember(currentGoal) { mutableIntStateOf(currentGoal.coerceIn(1, 50)) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.dialogSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Text(
        text = "Daily Chapter Goal",
        style = MaterialTheme.typography.titleLarge,
        color = colors.textPrimary,
      )
    },
    text = {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "How many chapters do you aim to finish each day?",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          FilledTonalIconButton(
            onClick = { if (goal > 1) goal -= 1 },
            colors =
              IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.tint,
                contentColor = colors.primary,
              ),
            modifier = Modifier.size(48.dp).testTag("goal_decrement_button"),
          ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease goal")
          }
          Spacer(modifier = Modifier.width(24.dp))
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = goal.toString(),
              fontSize = 32.sp,
              fontWeight = FontWeight.Bold,
              color = colors.textPrimary,
            )
            Text(
              text = if (goal == 1) "chapter" else "chapters",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.textSecondary,
            )
          }
          Spacer(modifier = Modifier.width(24.dp))
          FilledTonalIconButton(
            onClick = { if (goal < 50) goal += 1 },
            colors =
              IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.tint,
                contentColor = colors.primary,
              ),
            modifier = Modifier.size(48.dp).testTag("goal_increment_button"),
          ) {
            Icon(Icons.Default.Add, contentDescription = "Increase goal")
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(goal) },
        shape = RoundedCornerShape(12.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = colors.primarySolid,
            contentColor = colors.onPrimary,
          ),
        modifier = Modifier.testTag("save_goal_button"),
      ) {
        Text("Save", color = colors.onPrimary)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = colors.textSecondary)
      }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimePickerDialog(
  initialHour: Int,
  initialMinute: Int,
  onDismiss: () -> Unit,
  onConfirm: (Int, Int) -> Unit,
) {
  val colors = AppTheme.colors
  val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = colors.dialogSurface,
      border = BorderStroke(1.dp, colors.border),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "Reminder Time",
          style = MaterialTheme.typography.titleLarge,
          color = colors.textPrimary,
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        TimePicker(
          state = timeState,
          colors =
            TimePickerDefaults.colors(
              clockDialColor = colors.inputSurface,
              clockDialSelectedContentColor = colors.onPrimary,
              clockDialUnselectedContentColor = colors.textPrimary,
              selectorColor = colors.primarySolid,
              containerColor = colors.dialogSurface,
              periodSelectorBorderColor = colors.border,
              periodSelectorSelectedContainerColor = colors.tint,
              periodSelectorUnselectedContainerColor = colors.inputSurface,
              periodSelectorSelectedContentColor = colors.primary,
              periodSelectorUnselectedContentColor = colors.textSecondary,
              timeSelectorSelectedContainerColor = colors.tint,
              timeSelectorUnselectedContainerColor = colors.inputSurface,
              timeSelectorSelectedContentColor = colors.primary,
              timeSelectorUnselectedContentColor = colors.textPrimary,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colors.border),
          ) {
            Text("Cancel", color = colors.textSecondary)
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = { onConfirm(timeState.hour, timeState.minute) },
            shape = RoundedCornerShape(12.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = colors.primarySolid,
                contentColor = colors.onPrimary,
              ),
          ) {
            Text("Set Time", color = colors.onPrimary)
          }
        }
      }
    }
  }
}
