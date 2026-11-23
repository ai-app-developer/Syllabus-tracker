package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.dao.ReadingSessionWithMeta
import com.example.data.local.entity.SessionEventEntity
import com.example.timer.TimerMath
import com.example.timer.TimerMode
import com.example.ui.components.SessionDetailDialog
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.HistoryDateFilter
import com.example.ui.viewmodel.HistorySortOrder
import com.example.ui.viewmodel.ReadingHistoryUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private val HISTORY_DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val HISTORY_TIME_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

@Composable
fun ReadingHistoryScreen(
  state: ReadingHistoryUiState,
  innerPadding: PaddingValues,
  observeEvents: (String) -> Flow<List<SessionEventEntity>>,
  onBackClick: () -> Unit,
  onToggleSearch: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSelectBookFilter: (Long?) -> Unit,
  onSelectChapterFilter: (Long?) -> Unit,
  onSelectDateFilter: (HistoryDateFilter) -> Unit,
  onSelectSortOrder: (HistorySortOrder) -> Unit,
  onUpdateNotes: (Long, String) -> Unit,
  onDeleteSession: (Long) -> Unit,
  onStartNewSession: () -> Unit,
) {
  val colors = AppTheme.colors
  var showSortMenu by remember { mutableStateOf(false) }
  var selectedSessionForDetail by remember { mutableStateOf<ReadingSessionWithMeta?>(null) }
  var pendingDeleteSession by remember { mutableStateOf<ReadingSessionWithMeta?>(null) }

  val filteredSessions = state.filteredSessions
  val detailEvents by
    remember(selectedSessionForDetail?.session?.sessionId) {
      val sid = selectedSessionForDetail?.session?.sessionId
      if (sid != null) observeEvents(sid) else flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

  Box(
    modifier = Modifier.fillMaxSize().background(colors.screenBackground).padding(innerPadding),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 20.dp),
    ) {
      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier.size(48.dp).testTag("history_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = colors.textPrimary,
          )
        }
        Text(
          text = stringResource(R.string.reading_history_title),
          style = MaterialTheme.typography.titleLarge,
          color = colors.textPrimary,
          modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        IconButton(
          onClick = onToggleSearch,
          modifier = Modifier.size(48.dp).testTag("history_search_button"),
        ) {
          Icon(
            imageVector = if (state.historyFilter.isSearchActive) Icons.Default.Close else Icons.Default.Search,
            contentDescription = "Search sessions",
            tint = colors.textPrimary,
          )
        }
        Box {
          IconButton(
            onClick = { showSortMenu = true },
            modifier = Modifier.size(48.dp).testTag("history_sort_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Sort,
              contentDescription = "Sort sessions",
              tint = colors.textPrimary,
            )
          }
          DropdownMenu(
            expanded = showSortMenu,
            onDismissRequest = { showSortMenu = false },
            containerColor = colors.dialogSurface,
          ) {
            HistorySortOrder.entries.forEach { order ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = order.label,
                    color = if (state.historyFilter.sortOrder == order) colors.primary else colors.textPrimary,
                    fontWeight = if (state.historyFilter.sortOrder == order) FontWeight.SemiBold else FontWeight.Normal,
                  )
                },
                onClick = {
                  onSelectSortOrder(order)
                  showSortMenu = false
                },
              )
            }
          }
        }
      }

      if (state.historyFilter.isSearchActive) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = state.historyFilter.searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text("Search book, chapter, or notes", color = colors.textSecondary) },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
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
          modifier = Modifier.fillMaxWidth().testTag("history_search_input"),
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        HistoryDateFilter.entries.forEach { df ->
          key(df) {
            FilterChip(
              selected = state.historyFilter.dateFilter == df,
              onClick = { onSelectDateFilter(df) },
              label = { Text(df.label) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = colors.primarySolid,
                  selectedLabelColor = colors.onPrimary,
                  containerColor = colors.surface,
                  labelColor = colors.textPrimary,
                ),
            )
          }
        }
      }

      if (state.books.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = state.historyFilter.selectedBookId == null,
            onClick = { onSelectBookFilter(null) },
            label = { Text("All Books") },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = colors.tint,
                selectedLabelColor = colors.primary,
                containerColor = colors.surface,
                labelColor = colors.textSecondary,
              ),
          )
          state.books.forEach { book ->
            key(book.id) {
              FilterChip(
                selected = state.historyFilter.selectedBookId == book.id,
                onClick = { onSelectBookFilter(book.id) },
                label = { Text(book.name, maxLines = 1) },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.tint,
                    selectedLabelColor = colors.primary,
                    containerColor = colors.surface,
                    labelColor = colors.textSecondary,
                  ),
              )
            }
          }
        }

        val chaptersForFilter = state.chaptersForFilter
        if (chaptersForFilter.isNotEmpty()) {
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = state.historyFilter.selectedChapterId == null,
              onClick = { onSelectChapterFilter(null) },
              label = { Text("All Chapters") },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = colors.tint,
                  selectedLabelColor = colors.primary,
                  containerColor = colors.surface,
                  labelColor = colors.textSecondary,
                ),
            )
            chaptersForFilter.forEach { ch ->
              key(ch.id) {
                FilterChip(
                  selected = state.historyFilter.selectedChapterId == ch.id,
                  onClick = { onSelectChapterFilter(ch.id) },
                  label = { Text("Ch ${ch.number}: ${ch.name}", maxLines = 1) },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = colors.tint,
                      selectedLabelColor = colors.primary,
                      containerColor = colors.surface,
                      labelColor = colors.textSecondary,
                    ),
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      if (filteredSessions.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxWidth().weight(1f),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
          ) {
            Box(
              modifier =
                Modifier.size(56.dp)
                  .clip(RoundedCornerShape(16.dp))
                  .background(colors.tint),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(28.dp),
              )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
              text =
                if (!state.hasAnySessions) "No Reading Sessions Yet"
                else "No Matching Sessions",
              style = MaterialTheme.typography.titleLarge,
              color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text =
                if (!state.hasAnySessions) {
                  "Start the Study Timer to record focus time and build your reading history."
                } else {
                  "Try clearing your search or selecting a wider date filter."
                },
              style = MaterialTheme.typography.bodyMedium,
              color = colors.textSecondary,
              textAlign = TextAlign.Center,
            )
            if (!state.hasAnySessions) {
              Spacer(modifier = Modifier.height(18.dp))
              Button(
                onClick = onStartNewSession,
                shape = RoundedCornerShape(24.dp),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = colors.primarySolid,
                    contentColor = colors.onPrimary,
                  ),
                modifier = Modifier.testTag("history_empty_start_button"),
              ) {
                Text("Start Study Timer", fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().weight(1f),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(bottom = 20.dp),
        ) {
          items(
            items = filteredSessions,
            key = { it.session.id },
            contentType = { "session" },
          ) { item ->
            ReadingSessionCard(
              item = item,
              onClick = { selectedSessionForDetail = item },
            )
          }
        }
      }
    }
  }

  selectedSessionForDetail?.let { selected ->
    SessionDetailDialog(
      item = selected,
      events = detailEvents,
      onDismiss = { selectedSessionForDetail = null },
      onSaveNotes = { updatedNotes ->
        onUpdateNotes(selected.session.id, updatedNotes)
        selectedSessionForDetail = null
      },
      onRequestDelete = {
        pendingDeleteSession = selected
        selectedSessionForDetail = null
      },
    )
  }

  pendingDeleteSession?.let { target ->
    AlertDialog(
      onDismissRequest = { pendingDeleteSession = null },
      containerColor = colors.surface,
      shape = RoundedCornerShape(16.dp),
      title = {
        Text(
          text = "Delete Session?",
          style = MaterialTheme.typography.titleLarge,
          color = colors.textPrimary,
        )
      },
      text = {
        Text(
          text =
            "Delete the ${TimerMath.formatShortElapsed(target.session.activeFocusMs)} session for ${target.bookName} — Chapter ${target.chapterNumber}: ${target.chapterName}?",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textSecondary,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteSession(target.session.id)
            pendingDeleteSession = null
          },
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = colors.error),
          modifier = Modifier.testTag("confirm_delete_session_button"),
        ) {
          Text("Delete", color = colors.onPrimary)
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteSession = null }) {
          Text("Cancel", color = colors.textSecondary)
        }
      },
    )
  }
}

@Composable
private fun ReadingSessionCard(
  item: ReadingSessionWithMeta,
  onClick: () -> Unit,
) {
  val colors = AppTheme.colors
  val (dateLabel, startLabel) =
    remember(item.session.startWallTimeMs) {
      val zoned = Instant.ofEpochMilli(item.session.startWallTimeMs).atZone(ZoneId.systemDefault())
      zoned.format(HISTORY_DATE_FORMATTER) to zoned.format(HISTORY_TIME_FORMATTER)
    }

  val focusPct =
    remember(item.session.activeFocusMs, item.session.pauseMs) {
      val total = item.session.activeFocusMs + item.session.pauseMs
      if (total <= 0L) 100 else ((item.session.activeFocusMs * 100.0) / total).toInt().coerceIn(0, 100)
    }

  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.border),
    shadowElevation = if (colors.isAmoled || colors.isGlass) 0.dp else 1.dp,
    modifier = Modifier.fillMaxWidth().testTag("history_session_card_${item.session.id}"),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier.size(40.dp)
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
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = item.bookName,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "Chapter ${item.chapterNumber}: ${item.chapterName}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          shape = RoundedCornerShape(50),
          color = colors.tint,
        ) {
          Text(
            text = TimerMath.formatShortElapsed(item.session.activeFocusMs),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "$dateLabel • $startLabel",
          style = MaterialTheme.typography.labelMedium,
          color = colors.textSecondary,
        )
        Text(
          text =
            "${if (item.session.mode == TimerMode.POMODORO) "Pomodoro" else "Stopwatch"} • Focus $focusPct%",
          style = MaterialTheme.typography.labelMedium,
          color = colors.textSecondary,
        )
      }

      if (item.session.notes.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = item.session.notes,
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textPrimary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
