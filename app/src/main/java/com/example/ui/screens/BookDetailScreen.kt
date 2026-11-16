package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.BookEntity
import com.example.data.local.entity.ChapterEntity
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LightIndigoTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.ProgressTrack
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
  book: BookEntity?,
  chapters: List<ChapterEntity>,
  onMarkOpened: () -> Unit,
  onBackClick: () -> Unit,
  onEditBookClick: (BookEntity) -> Unit,
  onDeleteBookClick: (BookEntity) -> Unit,
  onAddChapterClick: () -> Unit,
  onToggleChapter: (ChapterEntity) -> Unit,
  onEditChapterClick: (ChapterEntity) -> Unit,
  onDeleteChapterClick: (ChapterEntity) -> Unit,
) {
  LaunchedEffect(book?.id) {
    if (book != null) {
      onMarkOpened()
    }
  }

  val completedCount = chapters.count { it.isCompleted }
  val totalCount = chapters.size
  val percentage =
    if (totalCount == 0) 0 else ((completedCount * 100f) / totalCount).roundToInt()
  val fraction = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount.toFloat()

  Scaffold(
    containerColor = AppBackground,
    topBar = {
      TopAppBar(
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = AppBackground,
            titleContentColor = TextPrimary,
          ),
        navigationIcon = {
          IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(48.dp).testTag("book_detail_back"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = TextPrimary,
            )
          }
        },
        title = {
          Text(
            text = book?.name ?: "Book",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        actions = {
          if (book != null) {
            IconButton(
              onClick = { onEditBookClick(book) },
              modifier = Modifier.size(48.dp).testTag("edit_book_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Rename book",
                tint = TextPrimary,
              )
            }
            IconButton(
              onClick = { onDeleteBookClick(book) },
              modifier = Modifier.size(48.dp).testTag("delete_book_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete book",
                tint = ErrorRed,
              )
            }
          }
        },
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 20.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = CardSurface,
          border = BorderStroke(1.dp, HairlineBorder),
          shadowElevation = 1.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "$completedCount / $totalCount chapters",
                  style = MaterialTheme.typography.titleMedium,
                  color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "${(totalCount - completedCount).coerceAtLeast(0)} remaining",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                )
              }
              Text(
                text = "$percentage%",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (totalCount > 0 && completedCount == totalCount) SuccessGreen else PrimaryIndigo,
              )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(7.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(ProgressTrack),
            ) {
              Box(
                modifier =
                  Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                      if (totalCount > 0 && completedCount == totalCount) SuccessGreen
                      else PrimaryIndigo
                    ),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (chapters.isEmpty()) {
          Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = CardSurface,
              border = BorderStroke(1.dp, HairlineBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Box(
                  modifier =
                    Modifier.size(56.dp)
                      .clip(RoundedCornerShape(14.dp))
                      .background(LightIndigoTint),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Outlined.MenuBook,
                    contentDescription = null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(28.dp),
                  )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                  text = "No chapters yet",
                  style = MaterialTheme.typography.titleLarge,
                  color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Add chapters to this book to track your progress.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                  onClick = onAddChapterClick,
                  shape = RoundedCornerShape(24.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                  modifier = Modifier.height(48.dp).testTag("empty_add_chapter_button"),
                ) {
                  Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.add_chapter))
                }
              }
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
          ) {
            items(chapters, key = { it.id }) { chapter ->
              ChapterItemCard(
                chapter = chapter,
                onToggle = { onToggleChapter(chapter) },
                onEdit = { onEditChapterClick(chapter) },
                onDelete = { onDeleteChapterClick(chapter) },
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          Button(
            onClick = onAddChapterClick,
            shape = RoundedCornerShape(26.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = PrimaryIndigo,
                contentColor = Color.White,
              ),
            modifier =
              Modifier.fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(52.dp)
                .testTag("detail_add_chapter_button"),
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.add_chapter),
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ChapterItemCard(
  chapter: ChapterEntity,
  onToggle: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = CardSurface,
    border = BorderStroke(1.dp, HairlineBorder),
    shadowElevation = 1.dp,
    modifier = Modifier.fillMaxWidth().testTag("chapter_card_${chapter.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .testTag("chapter_check_${chapter.id}"),
        contentAlignment = Alignment.Center,
      ) {
        if (chapter.isCompleted) {
          Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(SuccessGreen),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Completed",
              tint = Color.White,
              modifier = Modifier.size(16.dp),
            )
          }
        } else {
          Box(
            modifier =
              Modifier.size(24.dp)
                .clip(CircleShape)
                .border(2.dp, ProgressTrack, CircleShape),
          )
        }
      }

      Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
              .background(LightIndigoTint)
              .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
          Text(
            text = "Ch. ${chapter.number}",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryIndigo,
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = chapter.name,
          style = MaterialTheme.typography.titleMedium,
          color = if (chapter.isCompleted) TextSecondary else TextPrimary,
          textDecoration = if (chapter.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onEdit,
        modifier = Modifier.size(48.dp).testTag("edit_chapter_${chapter.id}"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Edit,
          contentDescription = "Edit chapter",
          tint = TextSecondary,
          modifier = Modifier.size(20.dp),
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(48.dp).testTag("delete_chapter_${chapter.id}"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Delete,
          contentDescription = "Delete chapter",
          tint = ErrorRed,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}
