package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.notifications.NotificationHelper
import com.example.timer.TimerMath
import com.example.timer.TimerMode
import com.example.ui.components.PomodoroCustomDialog
import com.example.ui.components.SessionGoalDialog
import com.example.ui.theme.AmbientShadow
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InputSurface
import com.example.ui.theme.LightGreenBorder
import com.example.ui.theme.LightGreenTint
import com.example.ui.theme.LightIndigoTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletGradientBottom
import com.example.ui.theme.VioletGradientTop
import com.example.ui.viewmodel.TimerUiState

@Composable
fun StudyTimerSetupScreen(
  uiState: TimerUiState,
  innerPadding: PaddingValues,
  onBackClick: () -> Unit,
  onSelectBook: (Long) -> Unit,
  onSelectChapter: (Long) -> Unit,
  onSelectMode: (String) -> Unit,
  onSelectPomodoroPreset: (Int, Int) -> Unit,
  onSetGoalMinutes: (Int?) -> Unit,
  onNotesChange: (String) -> Unit,
  onStartTimerClick: () -> Unit,
  onOpenActiveTimer: () -> Unit,
  onAddBookClick: () -> Unit,
  onAddChapterClick: (Long) -> Unit,
) {
  val context = LocalContext.current
  var showBookMenu by remember { mutableStateOf(false) }
  var showChapterMenu by remember { mutableStateOf(false) }
  var showGoalDialog by remember { mutableStateOf(false) }
  var showCustomPomodoroDialog by remember { mutableStateOf(false) }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      onStartTimerClick()
    }

  val setup = uiState.setup
  val selectedBook = uiState.books.firstOrNull { it.id == setup.selectedBookId }
  val selectedChapter =
    uiState.chaptersForSelectedBook.firstOrNull { it.id == setup.selectedChapterId }
  val canStart = selectedBook != null && selectedChapter != null

  Box(
    modifier = Modifier.fillMaxSize().background(AppBackground),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 600.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(
            top = innerPadding.calculateTopPadding() + 10.dp,
            bottom = innerPadding.calculateBottomPadding() + 20.dp,
            start = 20.dp,
            end = 20.dp,
          ),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier.size(48.dp).testTag("timer_setup_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Back",
            tint = TextPrimary,
            modifier = Modifier.size(28.dp),
          )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
          Text(
            text = stringResource(R.string.study_timer_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
        }
        Spacer(modifier = Modifier.size(48.dp))
      }

      Spacer(modifier = Modifier.height(10.dp))

      if (uiState.liveStudying.isRunningOrPaused) {
        val activeMeta = uiState.liveStudying.activeMeta
        val bannerShape = RoundedCornerShape(18.dp)
        Surface(
          onClick = onOpenActiveTimer,
          shape = bannerShape,
          color = LightGreenTint,
          border = BorderStroke(1.dp, LightGreenBorder),
          modifier = Modifier.fillMaxWidth().testTag("timer_setup_active_banner"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Active session running",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
              )
              if (activeMeta != null) {
                Text(
                  text =
                    "${activeMeta.bookName} • ${uiState.liveStudying.primaryClockDisplay}",
                  fontSize = 13.sp,
                  color = SuccessGreen,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
            Text(
              text = "Open",
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              color = PrimaryIndigo,
            )
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
      }

      // Main grouped setup card matching Screen 3 of the reference image
      val containerShape = RoundedCornerShape(22.dp)
      Surface(
        shape = containerShape,
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier =
          Modifier.fillMaxWidth()
            .shadow(
              elevation = 10.dp,
              shape = containerShape,
              clip = false,
              ambientColor = AmbientShadow,
              spotColor = AmbientShadow,
            ),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
          Text(
            text = "Select Book",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
          Spacer(modifier = Modifier.height(8.dp))

          Box {
            Surface(
              onClick = {
                if (uiState.books.isEmpty()) onAddBookClick() else showBookMenu = true
              },
              shape = RoundedCornerShape(14.dp),
              color = InputSurface,
              border = BorderStroke(1.dp, HairlineBorder),
              modifier = Modifier.fillMaxWidth().testTag("timer_select_book_dropdown"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier.size(30.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(LightIndigoTint),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = selectedBook?.name ?: "No books yet — Tap to add a book",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Medium,
                  color = if (selectedBook != null) TextPrimary else TextSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Icon(
                  imageVector = Icons.Default.KeyboardArrowDown,
                  contentDescription = "Choose book",
                  tint = TextSecondary,
                )
              }
            }

            DropdownMenu(
              expanded = showBookMenu,
              onDismissRequest = { showBookMenu = false },
              containerColor = CardSurface,
            ) {
              uiState.books.forEach { book ->
                DropdownMenuItem(
                  text = { Text(book.name, color = TextPrimary) },
                  onClick = {
                    onSelectBook(book.id)
                    showBookMenu = false
                  },
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = "Select Chapter",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
          Spacer(modifier = Modifier.height(8.dp))

          Box {
            Surface(
              onClick = {
                when {
                  selectedBook == null -> onAddBookClick()
                  uiState.chaptersForSelectedBook.isEmpty() -> onAddChapterClick(selectedBook.id)
                  else -> showChapterMenu = true
                }
              },
              shape = RoundedCornerShape(14.dp),
              color = InputSurface,
              border = BorderStroke(1.dp, HairlineBorder),
              modifier = Modifier.fillMaxWidth().testTag("timer_select_chapter_dropdown"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier.size(30.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(LightIndigoTint),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                val chapterText =
                  when {
                    selectedBook == null -> "Select a book first"
                    selectedChapter == null -> "No chapters yet — Tap to add"
                    else -> "Chapter ${selectedChapter.number}: ${selectedChapter.name}"
                  }
                Text(
                  text = chapterText,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Medium,
                  color = if (selectedChapter != null) TextPrimary else TextSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Icon(
                  imageVector = Icons.Default.KeyboardArrowDown,
                  contentDescription = "Choose chapter",
                  tint = TextSecondary,
                )
              }
            }

            DropdownMenu(
              expanded = showChapterMenu,
              onDismissRequest = { showChapterMenu = false },
              containerColor = CardSurface,
            ) {
              uiState.chaptersForSelectedBook.forEach { chapter ->
                DropdownMenuItem(
                  text = {
                    Text(
                      text = "Chapter ${chapter.number}: ${chapter.name}",
                      color = TextPrimary,
                    )
                  },
                  onClick = {
                    onSelectChapter(chapter.id)
                    showChapterMenu = false
                  },
                )
              }
            }
          }

          HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            thickness = 1.dp,
            color = HairlineBorder,
          )

          Text(
            text = "Timer Mode",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
          )
          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            TimerModeOptionCard(
              title = "Stopwatch",
              subtitle = "Count up",
              icon = Icons.Outlined.Timer,
              selected = setup.mode == TimerMode.STOPWATCH,
              onClick = { onSelectMode(TimerMode.STOPWATCH) },
              testTag = "mode_stopwatch_card",
              modifier = Modifier.weight(1f),
            )
            TimerModeOptionCard(
              title = "Pomodoro",
              subtitle = "Focus + Break",
              icon = Icons.Outlined.HourglassEmpty,
              selected = setup.mode == TimerMode.POMODORO,
              onClick = { onSelectMode(TimerMode.POMODORO) },
              testTag = "mode_pomodoro_card",
              modifier = Modifier.weight(1f),
            )
          }

          if (setup.mode == TimerMode.POMODORO) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              val is25 = setup.pomodoroFocusMinutes == 25 && setup.pomodoroBreakMinutes == 5
              val is50 = setup.pomodoroFocusMinutes == 50 && setup.pomodoroBreakMinutes == 10
              val isCustom = !is25 && !is50

              FilterChip(
                selected = is25,
                onClick = { onSelectPomodoroPreset(25, 5) },
                label = { Text("25 / 5 min") },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryIndigo,
                    selectedLabelColor = Color.White,
                    containerColor = InputSurface,
                    labelColor = TextPrimary,
                  ),
              )
              FilterChip(
                selected = is50,
                onClick = { onSelectPomodoroPreset(50, 10) },
                label = { Text("50 / 10 min") },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryIndigo,
                    selectedLabelColor = Color.White,
                    containerColor = InputSurface,
                    labelColor = TextPrimary,
                  ),
              )
              FilterChip(
                selected = isCustom,
                onClick = { showCustomPomodoroDialog = true },
                label = {
                  Text(
                    if (isCustom) "${setup.pomodoroFocusMinutes}/${setup.pomodoroBreakMinutes}m"
                    else "Custom"
                  )
                },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryIndigo,
                    selectedLabelColor = Color.White,
                    containerColor = InputSurface,
                    labelColor = TextPrimary,
                  ),
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      val secondaryCardShape = RoundedCornerShape(20.dp)
      Surface(
        onClick = { showGoalDialog = true },
        shape = secondaryCardShape,
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier =
          Modifier.fillMaxWidth()
            .shadow(
              elevation = 8.dp,
              shape = secondaryCardShape,
              clip = false,
              ambientColor = AmbientShadow,
              spotColor = AmbientShadow,
            )
            .testTag("timer_set_goal_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(42.dp)
                .clip(CircleShape)
                .background(LightIndigoTint),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.TrackChanges,
              contentDescription = null,
              tint = PrimaryIndigo,
              modifier = Modifier.size(22.dp),
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Set Goal ",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
              )
              Text(
                text = "(Optional)",
                fontSize = 13.sp,
                color = TextSecondary,
              )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text =
                setup.goalMinutes?.let { TimerMath.formatGoalWords(it * 60_000L) }
                  ?: "No goal set",
              fontSize = 14.sp,
              color = TextPrimary,
              fontWeight = FontWeight.Medium,
            )
          }
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Edit session goal",
            tint = TextSecondary,
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Surface(
        shape = secondaryCardShape,
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier =
          Modifier.fillMaxWidth()
            .shadow(
              elevation = 8.dp,
              shape = secondaryCardShape,
              clip = false,
              ambientColor = AmbientShadow,
              spotColor = AmbientShadow,
            ),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Outlined.Edit,
              contentDescription = null,
              tint = PrimaryIndigo,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Notes ",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
            )
            Text(
              text = "(Optional)",
              fontSize = 13.sp,
              color = TextSecondary,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = setup.notes,
            onValueChange = onNotesChange,
            placeholder = {
              Text("e.g., Practice exercises, revision", color = TextSecondary, fontSize = 14.sp)
            },
            maxLines = 3,
            shape = RoundedCornerShape(14.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryIndigo,
                unfocusedBorderColor = HairlineBorder,
                focusedContainerColor = InputSurface,
                unfocusedContainerColor = InputSurface,
              ),
            modifier = Modifier.fillMaxWidth().testTag("timer_setup_notes_input"),
          )
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      val ctaEnabled = canStart && !uiState.liveStudying.isRunningOrPaused
      val ctaShape = RoundedCornerShape(28.dp)
      Surface(
        onClick = {
          if (ctaEnabled) {
            if (
              Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationHelper.hasNotificationPermission(context)
            ) {
              notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
              onStartTimerClick()
            }
          }
        },
        enabled = ctaEnabled,
        shape = ctaShape,
        color = Color.Transparent,
        modifier =
          Modifier.fillMaxWidth()
            .height(54.dp)
            .shadow(
              elevation = if (ctaEnabled) 10.dp else 0.dp,
              shape = ctaShape,
              clip = false,
              ambientColor = AmbientShadow,
              spotColor = AmbientShadow,
            )
            .testTag("start_timer_button"),
      ) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(
                if (ctaEnabled) {
                  Brush.verticalGradient(listOf(VioletGradientTop, VioletGradientBottom))
                } else {
                  Brush.verticalGradient(
                    listOf(
                      PrimaryIndigo.copy(alpha = 0.42f),
                      PrimaryIndigo.copy(alpha = 0.42f),
                    )
                  )
                }
              ),
          contentAlignment = Alignment.Center,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Start Timer",
              fontSize = 16.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color.White,
            )
          }
        }
      }

      if (!canStart) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
        ) {
          TextButton(
            onClick = {
              if (selectedBook == null) onAddBookClick() else onAddChapterClick(selectedBook.id)
            }
          ) {
            Text(
              text =
                if (selectedBook == null) "Add a book to start a timer"
                else "Add a chapter to ${selectedBook.name}",
              color = PrimaryIndigo,
              fontWeight = FontWeight.Medium,
            )
          }
        }
      }
    }
  }

  if (showGoalDialog) {
    SessionGoalDialog(
      currentGoalMinutes = setup.goalMinutes,
      onDismiss = { showGoalDialog = false },
      onConfirm = { minutes ->
        onSetGoalMinutes(minutes)
        showGoalDialog = false
      },
    )
  }

  if (showCustomPomodoroDialog) {
    PomodoroCustomDialog(
      initialFocusMinutes = setup.pomodoroFocusMinutes,
      initialBreakMinutes = setup.pomodoroBreakMinutes,
      onDismiss = { showCustomPomodoroDialog = false },
      onConfirm = { focusMin, breakMin ->
        onSelectPomodoroPreset(focusMin, breakMin)
        showCustomPomodoroDialog = false
      },
    )
  }
}

@Composable
private fun TimerModeOptionCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  val tileShape = RoundedCornerShape(16.dp)
  Surface(
    onClick = onClick,
    shape = tileShape,
    color = Color.Transparent,
    border = if (selected) null else BorderStroke(1.dp, Color(0xFFE3E6F4)),
    modifier = modifier.height(98.dp).testTag(testTag),
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .background(
            if (selected) {
              Brush.verticalGradient(listOf(VioletGradientTop, VioletGradientBottom))
            } else {
              Brush.verticalGradient(listOf(Color(0xFFF0F1FA), Color(0xFFEAECFA)))
            }
          )
          .padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) Color.White else TextPrimary,
        modifier = Modifier.size(24.dp),
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = if (selected) Color.White else TextPrimary,
      )
      Spacer(modifier = Modifier.height(1.dp))
      Text(
        text = subtitle,
        fontSize = 12.sp,
        color = if (selected) Color.White.copy(alpha = 0.85f) else TextSecondary,
      )
    }
  }
}
