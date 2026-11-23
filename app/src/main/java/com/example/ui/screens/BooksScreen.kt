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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Timer
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.repository.BookProgressSummary
import com.example.timer.TimerMath
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.BookFilter
import com.example.ui.viewmodel.BookSortOrder
import com.example.ui.viewmodel.BooksUiState
import com.example.ui.viewmodel.LiveStudyingUiState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun BooksScreen(
  state: BooksUiState,
  todayStudyMs: Long,
  isTimerActive: Boolean,
  liveStudyingState: StateFlow<LiveStudyingUiState>,
  innerPadding: PaddingValues,
  onToggleSearch: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSelectFilter: (BookFilter) -> Unit,
  onSelectSortOrder: (BookSortOrder) -> Unit,
  onBookClick: (Long) -> Unit,
  onAddBookClick: () -> Unit,
  onStudyTimerClick: () -> Unit,
) {
  val colors = AppTheme.colors
  var overflowExpanded by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier.fillMaxSize().background(colors.screenBackground),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 600.dp)
          .fillMaxSize()
          .padding(
            top = innerPadding.calculateTopPadding(),
            start = 20.dp,
            end = 20.dp,
          ),
    ) {
      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.books_label),
          fontSize = 30.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = (-0.5).sp,
          color = colors.textPrimary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onToggleSearch,
            modifier = Modifier.size(48.dp).testTag("books_search_button"),
          ) {
            Icon(
              imageVector = if (state.isSearchActive) Icons.Default.Close else Icons.Default.Search,
              contentDescription = "Search books",
              tint = colors.textPrimary,
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
                tint = colors.textPrimary,
              )
            }
            DropdownMenu(
              expanded = overflowExpanded,
              onDismissRequest = { overflowExpanded = false },
              containerColor = colors.dialogSurface,
            ) {
              DropdownMenuItem(
                text = { Text("Add Book", color = colors.textPrimary) },
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
                      if (state.bookSortOrder == BookSortOrder.RECENT) FontWeight.SemiBold
                      else FontWeight.Normal,
                    color =
                      if (state.bookSortOrder == BookSortOrder.RECENT) colors.primary
                      else colors.textPrimary,
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
                      if (state.bookSortOrder == BookSortOrder.NAME_ASC) FontWeight.SemiBold
                      else FontWeight.Normal,
                    color =
                      if (state.bookSortOrder == BookSortOrder.NAME_ASC) colors.primary
                      else colors.textPrimary,
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
        visible = state.isSearchActive,
        enter = expandVertically(tween(150)) + fadeIn(tween(150)),
        exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
      ) {
        OutlinedTextField(
          value = state.searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text("Search books...", color = colors.textSecondary) },
          leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = colors.textSecondary)
          },
          trailingIcon = {
            if (state.searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchQueryChange("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = colors.textSecondary)
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = colors.primary,
              unfocusedBorderColor = colors.border,
              focusedContainerColor = colors.surface,
              unfocusedContainerColor = colors.surface,
              focusedTextColor = colors.textPrimary,
              unfocusedTextColor = colors.textPrimary,
              cursorColor = colors.primary,
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
          selected = state.bookFilter == BookFilter.ALL,
          testTag = "filter_all",
          onClick = { onSelectFilter(BookFilter.ALL) },
        )
        FilterChipPill(
          label = stringResource(R.string.filter_in_progress),
          selected = state.bookFilter == BookFilter.IN_PROGRESS,
          testTag = "filter_in_progress",
          onClick = { onSelectFilter(BookFilter.IN_PROGRESS) },
        )
        FilterChipPill(
          label = stringResource(R.string.filter_completed),
          selected = state.bookFilter == BookFilter.COMPLETED,
          testTag = "filter_completed",
          onClick = { onSelectFilter(BookFilter.COMPLETED) },
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      val books = state.filteredBooks
      val bottomPad = innerPadding.calculateBottomPadding() + 14.dp

      if (!state.hasAnyBooks) {
        Column(
          modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = bottomPad),
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
          Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            val emptyShape = RoundedCornerShape(20.dp)
            Surface(
              shape = emptyShape,
              color = colors.surface,
              border = BorderStroke(1.dp, colors.border),
              modifier =
                Modifier.fillMaxWidth()
                  .shadow(
                    elevation = if (colors.isAmoled || colors.isGlass) 0.dp else 8.dp,
                    shape = emptyShape,
                    clip = false,
                    ambientColor = colors.ambientShadow,
                    spotColor = colors.ambientShadow,
                  ),
            ) {
              Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Box(
                  modifier =
                    Modifier.size(56.dp)
                      .clip(RoundedCornerShape(14.dp))
                      .background(colors.tint),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(28.dp),
                  )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                  text = "No books yet",
                  style = MaterialTheme.typography.titleLarge,
                  color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Add your first subject or textbook to track chapters.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                  onClick = onAddBookClick,
                  shape = RoundedCornerShape(26.dp),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = colors.primarySolid,
                      contentColor = colors.onPrimary,
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

          BooksStudyTimerCard(
            todayStudyMs = todayStudyMs,
            isTimerActive = isTimerActive,
            liveStudyingState = liveStudyingState,
            onClick = onStudyTimerClick,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(bottom = bottomPad),
        ) {
          if (books.isEmpty()) {
            item(contentType = "empty") {
              Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(
                  text = "No matching books",
                  style = MaterialTheme.typography.titleMedium,
                  color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Try a different filter or search term.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
              }
            }
          } else {
            itemsIndexed(
              items = books,
              key = { _, it -> it.book.id },
              contentType = { _, _ -> "book" },
            ) { index, summary ->
              BookProgressCard(
                summary = summary,
                colorSlot = index,
                onClick = { onBookClick(summary.book.id) },
              )
            }
          }

          item {
            Spacer(modifier = Modifier.height(2.dp))
            Button(
              onClick = onAddBookClick,
              shape = RoundedCornerShape(26.dp),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = colors.tint,
                  contentColor = colors.primary,
                ),
              elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
              modifier = Modifier.fillMaxWidth().height(52.dp).testTag("add_book_button"),
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

          item(contentType = "timer_card") {
            BooksStudyTimerCard(
              todayStudyMs = todayStudyMs,
              isTimerActive = isTimerActive,
              liveStudyingState = liveStudyingState,
              onClick = onStudyTimerClick,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BooksStudyTimerCard(
  todayStudyMs: Long,
  isTimerActive: Boolean,
  liveStudyingState: StateFlow<LiveStudyingUiState>,
  onClick: () -> Unit,
) {
  val colors = AppTheme.colors
  val shape = RoundedCornerShape(20.dp)
  val subtitle =
    if (isTimerActive) {
      val liveStudying by liveStudyingState.collectAsStateWithLifecycle()
      if (liveStudying.isRunningOrPaused) {
        val totalMs = todayStudyMs + liveStudying.metrics.activeFocusMs
        "Active: ${liveStudying.primaryClockDisplay} • Today: ${TimerMath.formatHoursMinutes(totalMs)}"
      } else {
        "Today: ${TimerMath.formatHoursMinutes(todayStudyMs)}"
      }
    } else {
      "Today: ${TimerMath.formatHoursMinutes(todayStudyMs)}"
    }
  val cardGradient =
    Brush.horizontalGradient(colors = listOf(colors.softAccentCardStart, colors.softAccentCardEnd))

  Surface(
    onClick = onClick,
    shape = shape,
    color = Color.Transparent,
    border = BorderStroke(1.dp, colors.softAccentBorder),
    modifier =
      Modifier.fillMaxWidth()
        .shadow(
          elevation = if (colors.isAmoled || colors.isGlass) 0.dp else 4.dp,
          shape = shape,
          clip = false,
          ambientColor = colors.ambientShadow,
          spotColor = colors.ambientShadow,
        )
        .testTag("books_study_timer_card"),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(cardGradient)
          .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(44.dp)
            .clip(CircleShape)
            .background(colors.timerIconBadge),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Outlined.Timer,
          contentDescription = null,
          tint = colors.primary,
          modifier = Modifier.size(23.dp),
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.study_timer_title),
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          fontSize = 13.sp,
          fontWeight = FontWeight.Medium,
          color = colors.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      Icon(
        imageVector = Icons.Filled.BarChart,
        contentDescription = "Open study timer",
        tint = colors.primary,
        modifier = Modifier.size(26.dp),
      )
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
  val colors = AppTheme.colors
  Surface(
    shape = RoundedCornerShape(50),
    color = if (selected) colors.primarySolid else colors.surface,
    border = if (selected) null else BorderStroke(1.dp, colors.border),
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
        color = if (selected) colors.onPrimary else colors.textSecondary,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
      )
    }
  }
}

private enum class BookBadgeStyle(val slotIndex: Int) {
  BOOK_VIOLET(0),
  SIGMA_BLUE(1),
  AA_GREEN(2),
  SCIENCE_AMBER(3),
}

private fun resolveBookBadgeStyle(bookName: String, fallbackSlot: Int): BookBadgeStyle {
  val lower = bookName.lowercase()
  return when {
    lower.contains("math") || lower.contains("algebra") || lower.contains("calc") ->
      BookBadgeStyle.SIGMA_BLUE
    lower.contains("english") || lower.contains("grammar") || lower.contains("writing") ->
      BookBadgeStyle.AA_GREEN
    lower.contains("science") ||
      lower.contains("phys") ||
      lower.contains("chem") ||
      lower.contains("bio") -> BookBadgeStyle.SCIENCE_AMBER
    lower.contains("bangla") || lower.contains("hist") || lower.contains("lit") ->
      BookBadgeStyle.BOOK_VIOLET
    else ->
      when (fallbackSlot.mod(4)) {
        0 -> BookBadgeStyle.BOOK_VIOLET
        1 -> BookBadgeStyle.SIGMA_BLUE
        2 -> BookBadgeStyle.AA_GREEN
        else -> BookBadgeStyle.SCIENCE_AMBER
      }
  }
}

@Composable
fun BookProgressCard(
  summary: BookProgressSummary,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  colorSlot: Int = (summary.book.id % 4L).toInt(),
) {
  val colors = AppTheme.colors
  val badgeStyle = remember(summary.book.name, colorSlot) {
    resolveBookBadgeStyle(summary.book.name, colorSlot)
  }
  val badgeColors = colors.bookBadges[badgeStyle.slotIndex.mod(colors.bookBadges.size)]
  val iconBg = badgeColors.background
  val iconTint = badgeColors.tint
  val barFill = if (summary.isCompleted) colors.vibrantSuccess else colors.primarySolid
  val cardShape = RoundedCornerShape(20.dp)

  Surface(
    onClick = onClick,
    shape = cardShape,
    color = colors.surface,
    border = BorderStroke(1.dp, colors.border),
    modifier =
      modifier
        .fillMaxWidth()
        .shadow(
          elevation = if (colors.isAmoled || colors.isGlass) 0.dp else 2.dp,
          shape = cardShape,
          clip = false,
          ambientColor = colors.ambientShadow,
          spotColor = colors.ambientShadow,
        )
        .testTag("book_card_${summary.book.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(iconBg),
        contentAlignment = Alignment.Center,
      ) {
        when (badgeStyle) {
          BookBadgeStyle.SIGMA_BLUE ->
            Text(
              text = "Σ",
              fontSize = 22.sp,
              fontWeight = FontWeight.SemiBold,
              color = iconTint,
            )
          BookBadgeStyle.AA_GREEN ->
            Text(
              text = "Aa",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = iconTint,
            )
          BookBadgeStyle.SCIENCE_AMBER ->
            Icon(
              imageVector = Icons.Outlined.Science,
              contentDescription = null,
              tint = iconTint,
              modifier = Modifier.size(24.dp),
            )
          BookBadgeStyle.BOOK_VIOLET ->
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.MenuBook,
              contentDescription = null,
              tint = iconTint,
              modifier = Modifier.size(24.dp),
            )
        }
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
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "${summary.percentage}%",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = "Open chapters",
              tint = colors.textSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "${summary.completedChapters} / ${summary.totalChapters} chapters",
          fontSize = 13.sp,
          color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(colors.progressTrack),
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
