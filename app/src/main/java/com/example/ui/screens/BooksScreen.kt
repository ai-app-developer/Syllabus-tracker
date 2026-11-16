package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.repository.BookProgressSummary
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LightGreenTint
import com.example.ui.theme.LightIndigoTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.ProgressTrack
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.BookFilter
import com.example.ui.viewmodel.BookSortOrder
import com.example.ui.viewmodel.SyllabusUiState

@Composable
fun BooksScreen(
  uiState: SyllabusUiState,
  innerPadding: PaddingValues,
  onToggleSearch: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSelectFilter: (BookFilter) -> Unit,
  onSelectSortOrder: (BookSortOrder) -> Unit,
  onBookClick: (Long) -> Unit,
  onAddBookClick: () -> Unit,
) {
  var overflowExpanded by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier.fillMaxSize().background(AppBackground).padding(innerPadding),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 20.dp),
    ) {
      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.books_label),
          style = MaterialTheme.typography.headlineLarge,
          color = TextPrimary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onToggleSearch,
            modifier = Modifier.size(48.dp).testTag("books_search_button"),
          ) {
            Icon(
              imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
              contentDescription = "Search books",
              tint = TextPrimary,
            )
          }
          Box {
            IconButton(
              onClick = { overflowExpanded = true },
              modifier = Modifier.size(48.dp).testTag("books_more_menu_button"),
            ) {
              Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Book options",
                tint = TextPrimary,
              )
            }
            DropdownMenu(
              expanded = overflowExpanded,
              onDismissRequest = { overflowExpanded = false },
              containerColor = CardSurface,
            ) {
              DropdownMenuItem(
                text = { Text("Add Book", color = TextPrimary) },
                onClick = {
                  overflowExpanded = false
                  onAddBookClick()
                },
              )
              DropdownMenuItem(
                text = {
                  Text(
                    text = "Sort: Recently Added",
                    fontWeight =
                      if (uiState.bookSortOrder == BookSortOrder.RECENT) FontWeight.SemiBold
                      else FontWeight.Normal,
                    color =
                      if (uiState.bookSortOrder == BookSortOrder.RECENT) PrimaryIndigo
                      else TextPrimary,
                  )
                },
                onClick = {
                  overflowExpanded = false
                  onSelectSortOrder(BookSortOrder.RECENT)
                },
              )
              DropdownMenuItem(
                text = {
                  Text(
                    text = "Sort: Name (A–Z)",
                    fontWeight =
                      if (uiState.bookSortOrder == BookSortOrder.NAME_ASC) FontWeight.SemiBold
                      else FontWeight.Normal,
                    color =
                      if (uiState.bookSortOrder == BookSortOrder.NAME_ASC) PrimaryIndigo
                      else TextPrimary,
                  )
                },
                onClick = {
                  overflowExpanded = false
                  onSelectSortOrder(BookSortOrder.NAME_ASC)
                },
              )
            }
          }
        }
      }

      AnimatedVisibility(
        visible = uiState.isSearchActive,
        enter = expandVertically(tween(150)) + fadeIn(tween(150)),
        exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
      ) {
        OutlinedTextField(
          value = uiState.searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text("Search books...", color = TextSecondary) },
          leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
          },
          trailingIcon = {
            if (uiState.searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchQueryChange("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = TextSecondary)
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PrimaryIndigo,
              unfocusedBorderColor = HairlineBorder,
              focusedContainerColor = CardSurface,
              unfocusedContainerColor = CardSurface,
            ),
          modifier =
            Modifier.fillMaxWidth().padding(top = 10.dp).testTag("books_search_input"),
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChipPill(
          label = stringResource(R.string.filter_all),
          selected = uiState.bookFilter == BookFilter.ALL,
          testTag = "filter_all",
          onClick = { onSelectFilter(BookFilter.ALL) },
        )
        FilterChipPill(
          label = stringResource(R.string.filter_in_progress),
          selected = uiState.bookFilter == BookFilter.IN_PROGRESS,
          testTag = "filter_in_progress",
          onClick = { onSelectFilter(BookFilter.IN_PROGRESS) },
        )
        FilterChipPill(
          label = stringResource(R.string.filter_completed),
          selected = uiState.bookFilter == BookFilter.COMPLETED,
          testTag = "filter_completed",
          onClick = { onSelectFilter(BookFilter.COMPLETED) },
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      val books = uiState.filteredBooks
      if (uiState.snapshot.books.isEmpty()) {
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
                  imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                  contentDescription = null,
                  tint = PrimaryIndigo,
                  modifier = Modifier.size(28.dp),
                )
              }
              Spacer(modifier = Modifier.height(14.dp))
              Text(
                text = "No books yet",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Add your first subject or textbook to track chapters.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
              Spacer(modifier = Modifier.height(18.dp))
              Button(
                onClick = onAddBookClick,
                shape = RoundedCornerShape(24.dp),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = PrimaryIndigo,
                    contentColor = Color.White,
                  ),
                modifier = Modifier.height(48.dp).testTag("empty_add_book_button"),
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.add_book))
              }
            }
          }
        }
      } else if (books.isEmpty()) {
        Box(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "No matching books",
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Try a different filter or search term.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(bottom = 12.dp),
        ) {
          items(books, key = { it.book.id }) { summary ->
            BookProgressCard(
              summary = summary,
              onClick = { onBookClick(summary.book.id) },
            )
          }
        }
      }

      if (uiState.snapshot.books.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = onAddBookClick,
          shape = RoundedCornerShape(26.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = LightIndigoTint,
              contentColor = PrimaryIndigo,
            ),
          elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
          modifier =
            Modifier.fillMaxWidth()
              .padding(bottom = 16.dp)
              .height(52.dp)
              .testTag("add_book_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.add_book),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

@Composable
private fun FilterChipPill(
  label: String,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(50),
    color = if (selected) PrimaryIndigo else CardSurface,
    border = if (selected) null else BorderStroke(1.dp, HairlineBorder),
    modifier =
      Modifier.height(38.dp)
        .clip(RoundedCornerShape(50))
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Box(
      modifier = Modifier.padding(horizontal = 18.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        color = if (selected) Color.White else TextSecondary,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
      )
    }
  }
}

@Composable
fun BookProgressCard(
  summary: BookProgressSummary,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val iconBg = if (summary.isCompleted) LightGreenTint else LightIndigoTint
  val iconTint = if (summary.isCompleted) SuccessGreen else PrimaryIndigo
  val barFill = if (summary.isCompleted) SuccessGreen else PrimaryIndigo

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = CardSurface,
    border = BorderStroke(1.dp, HairlineBorder),
    shadowElevation = 1.dp,
    modifier = modifier.fillMaxWidth().testTag("book_card_${summary.book.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(iconBg),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.MenuBook,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(24.dp),
        )
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = summary.book.name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "${summary.percentage}%",
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = "Open chapters",
              tint = TextSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "${summary.completedChapters} / ${summary.totalChapters} chapters",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(ProgressTrack),
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth(summary.progressFraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(barFill),
          )
        }
      }
    }
  }
}
