package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.ChapterEntity
import com.example.data.repository.BookProgressSummary
import com.example.ui.model.StableList
import com.example.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChapterBottomSheet(
  books: StableList<BookProgressSummary>,
  preselectedBookId: Long?,
  editingChapter: ChapterEntity?,
  onDismiss: () -> Unit,
  onCreateBookClick: () -> Unit,
  onSaveChapter: (bookId: Long, name: String, number: Int, isCompleted: Boolean) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val initialBookId =
    editingChapter?.bookId
      ?: preselectedBookId?.takeIf { id -> books.any { it.book.id == id } }
      ?: books.firstOrNull()?.book?.id

  var selectedBookId by remember(books, preselectedBookId, editingChapter) {
    mutableStateOf(initialBookId)
  }
  var dropdownExpanded by remember { mutableStateOf(false) }
  var chapterName by remember(editingChapter) { mutableStateOf(editingChapter?.name ?: "") }
  var chapterNumberText by
    remember(editingChapter) {
      mutableStateOf(editingChapter?.number?.toString() ?: "")
    }
  var isCompleted by remember(editingChapter) { mutableStateOf(editingChapter?.isCompleted ?: false) }

  var bookError by remember { mutableStateOf(false) }
  var nameError by remember { mutableStateOf(false) }
  var numberError by remember { mutableStateOf(false) }

  val colors = AppTheme.colors
  val selectedBook = books.firstOrNull { it.book.id == selectedBookId }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = colors.dialogSurface,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp)
          .navigationBarsPadding()
          .padding(bottom = 24.dp)
    ) {
      IconButton(
        onClick = onDismiss,
        modifier = Modifier.size(48.dp).testTag("close_add_chapter_sheet"),
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close",
          tint = colors.textSecondary,
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text =
          if (editingChapter == null) {
            stringResource(R.string.add_chapter)
          } else {
            stringResource(R.string.edit_chapter)
          },
        style = MaterialTheme.typography.headlineMedium,
        color = colors.textPrimary,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text =
          if (editingChapter == null) {
            stringResource(R.string.add_chapter_subtitle)
          } else {
            stringResource(R.string.edit_chapter_subtitle)
          },
        style = MaterialTheme.typography.bodyLarge,
        color = colors.textSecondary,
      )

      Spacer(modifier = Modifier.height(20.dp))

      if (books.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = colors.tint,
          border = BorderStroke(1.dp, colors.border),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Create a book first to add chapters.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.textPrimary,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
              onClick = onCreateBookClick,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, colors.primary),
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("New Book", color = colors.primary)
            }
          }
        }
      } else {
        ExposedDropdownMenuBox(
          expanded = dropdownExpanded,
          onExpandedChange = { dropdownExpanded = !dropdownExpanded },
        ) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.inputSurface,
            border = BorderStroke(1.dp, if (bookError) colors.error else colors.border),
            modifier =
              Modifier.fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .testTag("chapter_book_dropdown"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.tint),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                  contentDescription = null,
                  tint = colors.primary,
                  modifier = Modifier.size(20.dp),
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = selectedBook?.book?.name ?: "Select a Book",
                style = MaterialTheme.typography.titleMedium,
                color = if (selectedBook != null) colors.textPrimary else colors.textSecondary,
                modifier = Modifier.weight(1f),
              )
              Icon(
                imageVector =
                  if (dropdownExpanded) Icons.Default.KeyboardArrowUp
                  else Icons.Default.KeyboardArrowDown,
                contentDescription = "Select book",
                tint = colors.textSecondary,
              )
            }
          }

          ExposedDropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { dropdownExpanded = false },
            containerColor = colors.dialogSurface,
          ) {
            books.forEach { summary ->
              DropdownMenuItem(
                text = { Text(summary.book.name, color = colors.textPrimary) },
                onClick = {
                  selectedBookId = summary.book.id
                  bookError = false
                  dropdownExpanded = false
                },
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = stringResource(R.string.chapter_name_label),
        style = MaterialTheme.typography.labelLarge,
        color = colors.textPrimary,
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = chapterName,
        onValueChange = {
          chapterName = it
          if (nameError && it.isNotBlank()) nameError = false
        },
        placeholder = { Text("e.g. Algebra", color = colors.textSecondary) },
        singleLine = true,
        isError = nameError,
        supportingText =
          if (nameError) {
            { Text("Chapter name is required", color = colors.error) }
          } else {
            null
          },
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.border,
            focusedContainerColor = colors.inputSurface,
            unfocusedContainerColor = colors.inputSurface,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            cursorColor = colors.primary,
          ),
        modifier = Modifier.fillMaxWidth().testTag("chapter_name_input"),
      )

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = stringResource(R.string.chapter_number_label),
        style = MaterialTheme.typography.labelLarge,
        color = colors.textPrimary,
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = chapterNumberText,
        onValueChange = { input ->
          val filtered = input.filter { it.isDigit() }
          chapterNumberText = filtered
          if (numberError && (filtered.toIntOrNull() ?: 0) > 0) numberError = false
        },
        prefix = {
          Text(
            text = "#  ",
            color = colors.textSecondary,
            fontWeight = FontWeight.Medium,
          )
        },
        placeholder = { Text("3", color = colors.textSecondary) },
        singleLine = true,
        isError = numberError,
        supportingText =
          if (numberError) {
            { Text("Enter a valid chapter number (1 or higher)", color = colors.error) }
          } else {
            null
          },
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.border,
            focusedContainerColor = colors.inputSurface,
            unfocusedContainerColor = colors.inputSurface,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            cursorColor = colors.primary,
          ),
        modifier = Modifier.fillMaxWidth().testTag("chapter_number_input"),
      )

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.mark_as_completed),
          style = MaterialTheme.typography.titleMedium,
          color = colors.textPrimary,
        )
        Switch(
          checked = isCompleted,
          onCheckedChange = { isCompleted = it },
          colors =
            SwitchDefaults.colors(
              checkedThumbColor = colors.switchThumb,
              checkedTrackColor = colors.primarySolid,
              uncheckedThumbColor = colors.switchThumb,
              uncheckedTrackColor = colors.progressTrack,
              uncheckedBorderColor = Color.Transparent,
            ),
          modifier = Modifier.testTag("chapter_completed_switch"),
        )
      }

      Spacer(modifier = Modifier.height(22.dp))

      Button(
        onClick = {
          val bookId = selectedBookId
          val parsedNum = chapterNumberText.toIntOrNull()
          val hasBookErr = bookId == null
          val hasNameErr = chapterName.isBlank()
          val hasNumErr = parsedNum == null || parsedNum <= 0

          bookError = hasBookErr
          nameError = hasNameErr
          numberError = hasNumErr

          if (!hasBookErr && !hasNameErr && !hasNumErr) {
            onSaveChapter(bookId!!, chapterName.trim(), parsedNum!!, isCompleted)
          }
        },
        enabled = books.isNotEmpty(),
        shape = RoundedCornerShape(28.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = colors.primarySolid,
            contentColor = colors.onPrimary,
          ),
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("submit_chapter_button"),
      ) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text =
            if (editingChapter == null) {
              stringResource(R.string.add_chapter)
            } else {
              "Save Changes"
            },
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}
