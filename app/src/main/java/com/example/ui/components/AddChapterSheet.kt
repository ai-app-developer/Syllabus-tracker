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
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LightIndigoTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.ProgressTrack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChapterBottomSheet(
  books: List<BookProgressSummary>,
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

  val selectedBook = books.firstOrNull { it.book.id == selectedBookId }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = CardSurface,
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
          tint = TextSecondary,
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
        color = TextPrimary,
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
        color = TextSecondary,
      )

      Spacer(modifier = Modifier.height(20.dp))

      if (books.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = LightIndigoTint,
          border = BorderStroke(1.dp, HairlineBorder),
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
              color = TextPrimary,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
              onClick = onCreateBookClick,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, PrimaryIndigo),
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = PrimaryIndigo,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("New Book", color = PrimaryIndigo)
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
            color = CardSurface,
            border = BorderStroke(1.dp, if (bookError) ErrorRed else HairlineBorder),
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
                    .background(LightIndigoTint),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                  contentDescription = null,
                  tint = PrimaryIndigo,
                  modifier = Modifier.size(20.dp),
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = selectedBook?.book?.name ?: "Select a Book",
                style = MaterialTheme.typography.titleMedium,
                color = if (selectedBook != null) TextPrimary else TextSecondary,
                modifier = Modifier.weight(1f),
              )
              Icon(
                imageVector =
                  if (dropdownExpanded) Icons.Default.KeyboardArrowUp
                  else Icons.Default.KeyboardArrowDown,
                contentDescription = "Select book",
                tint = TextSecondary,
              )
            }
          }

          ExposedDropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { dropdownExpanded = false },
            containerColor = CardSurface,
          ) {
            books.forEach { summary ->
              DropdownMenuItem(
                text = { Text(summary.book.name, color = TextPrimary) },
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
        color = TextPrimary,
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = chapterName,
        onValueChange = {
          chapterName = it
          if (nameError && it.isNotBlank()) nameError = false
        },
        placeholder = { Text("e.g. Algebra", color = TextSecondary) },
        singleLine = true,
        isError = nameError,
        supportingText =
          if (nameError) {
            { Text("Chapter name is required", color = ErrorRed) }
          } else {
            null
          },
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryIndigo,
            unfocusedBorderColor = HairlineBorder,
            focusedContainerColor = CardSurface,
            unfocusedContainerColor = CardSurface,
          ),
        modifier = Modifier.fillMaxWidth().testTag("chapter_name_input"),
      )

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = stringResource(R.string.chapter_number_label),
        style = MaterialTheme.typography.labelLarge,
        color = TextPrimary,
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
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
          )
        },
        placeholder = { Text("3", color = TextSecondary) },
        singleLine = true,
        isError = numberError,
        supportingText =
          if (numberError) {
            { Text("Enter a valid chapter number (1 or higher)", color = ErrorRed) }
          } else {
            null
          },
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryIndigo,
            unfocusedBorderColor = HairlineBorder,
            focusedContainerColor = CardSurface,
            unfocusedContainerColor = CardSurface,
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
          color = TextPrimary,
        )
        Switch(
          checked = isCompleted,
          onCheckedChange = { isCompleted = it },
          colors =
            SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = PrimaryIndigo,
              uncheckedThumbColor = Color.White,
              uncheckedTrackColor = ProgressTrack,
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
            containerColor = PrimaryIndigo,
            contentColor = Color.White,
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
