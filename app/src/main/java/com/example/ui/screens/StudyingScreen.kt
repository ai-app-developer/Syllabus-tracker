package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.timer.SessionEventType
import com.example.timer.TimerMath
import com.example.timer.TimerMode
import com.example.ui.components.StopSessionSummaryDialog
import com.example.ui.theme.AppTheme
import com.example.ui.viewmodel.LiveStudyingUiState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun StudyingScreen(
  liveStudyingState: StateFlow<LiveStudyingUiState>,
  innerPadding: PaddingValues,
  onMinimizeBack: () -> Unit,
  onTogglePauseResume: () -> Unit,
  onRequestStop: () -> Unit,
  onRecordLap: () -> Unit,
  onSkipBreak: () -> Unit,
  onSaveSession: (String) -> Unit,
  onResumeFromSummary: () -> Unit,
  onDiscardSession: () -> Unit,
  onOpenHistory: () -> Unit,
) {
  BackHandler { onMinimizeBack() }

  val colors = AppTheme.colors
  val liveState by liveStudyingState.collectAsStateWithLifecycle()
  var menuExpanded by remember { mutableStateOf(false) }
  val activeMeta = liveState.activeMeta

  if (activeMeta == null) {
    Box(
      modifier = Modifier.fillMaxSize().background(colors.screenBackground).padding(innerPadding),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(
          text = "No Active Session",
          style = MaterialTheme.typography.titleLarge,
          color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Start a study session from Study Timer setup.",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textSecondary,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
          onClick = onMinimizeBack,
          shape = RoundedCornerShape(24.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = colors.primarySolid,
              contentColor = colors.onPrimary,
            ),
        ) {
          Text("Go Back")
        }
      }
    }
    return
  }

  val animatedProgress by
    animateFloatAsState(
      targetValue = liveState.metrics.progressFraction.coerceIn(0f, 1f),
      animationSpec = tween(durationMillis = 150),
      label = "studying_ring_progress",
    )

  val isPaused = liveState.isPaused
  val isBreak = liveState.isBreak
  val statusBgColor =
    when {
      isPaused -> colors.warningTint
      isBreak -> colors.tint
      else -> colors.successTint
    }
  val statusDotColor =
    when {
      isPaused -> colors.warningText
      isBreak -> colors.primary
      else -> colors.vibrantSuccess
    }
  val statusTextColor =
    when {
      isPaused -> colors.warningText
      isBreak -> colors.primary
      else -> colors.success
    }
  val statusLabel =
    when {
      isPaused -> "Paused"
      isBreak -> "Break • Cycle ${activeMeta.state.pomodoroCycle}"
      activeMeta.state.mode == TimerMode.POMODORO -> "Focus • Cycle ${activeMeta.state.pomodoroCycle}"
      else -> "Studying..."
    }
  val stopGradient =
    Brush.verticalGradient(listOf(colors.primaryGradientTop, colors.primaryGradientBottom))

  Box(
    modifier = Modifier.fillMaxSize().background(colors.screenBackground),
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
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onMinimizeBack,
          modifier = Modifier.size(48.dp).testTag("studying_minimize_button"),
        ) {
          Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Minimize timer",
            tint = colors.textPrimary,
            modifier = Modifier.size(28.dp),
          )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
          Text(
            text = stringResource(R.string.studying_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
          )
        }
        Box {
          IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(48.dp).testTag("studying_menu_button"),
          ) {
            Icon(
              imageVector = Icons.Default.MoreVert,
              contentDescription = "More options",
              tint = colors.textPrimary,
            )
          }
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            containerColor = colors.dialogSurface,
          ) {
            DropdownMenuItem(
              text = { Text("Reading History", color = colors.textPrimary) },
              onClick = {
                menuExpanded = false
                onOpenHistory()
              },
            )
            if (isBreak) {
              DropdownMenuItem(
                text = { Text("Skip Break", color = colors.textPrimary) },
                onClick = {
                  menuExpanded = false
                  onSkipBreak()
                },
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Box(
        modifier = Modifier.size(254.dp),
        contentAlignment = Alignment.Center,
      ) {
        // Static inner wave background clipped inside the circular dial
        Box(
          modifier =
            Modifier.size(230.dp)
              .shadow(
                elevation = if (colors.isAmoled || colors.isGlass) 0.dp else 10.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = colors.ambientShadow,
                spotColor = colors.ambientShadow,
              )
              .clip(CircleShape)
              .background(colors.surface),
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val backWave =
              Path().apply {
                moveTo(0f, h * 0.72f)
                cubicTo(w * 0.25f, h * 0.62f, w * 0.55f, h * 0.84f, w, h * 0.66f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
              }
            drawPath(path = backWave, color = colors.dialWaveBack)

            val frontWave =
              Path().apply {
                moveTo(0f, h * 0.80f)
                cubicTo(w * 0.35f, h * 0.89f, w * 0.70f, h * 0.70f, w, h * 0.80f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
              }
            drawPath(path = frontWave, color = colors.dialWaveFront)
          }
        }

        // Outer circular progress ring with gradient arc
        Canvas(modifier = Modifier.fillMaxSize()) {
          val strokeWidth = 11.dp.toPx()
          drawArc(
            color = colors.progressTrack,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
          )
          if (animatedProgress > 0f) {
            drawArc(
              brush =
                Brush.linearGradient(
                  colors = listOf(colors.ringGradientTop, colors.ringGradientMid, colors.ringGradientBottom),
                  start = Offset(size.width * 0.5f, 0f),
                  end = Offset(size.width * 0.5f, size.height),
                ),
              startAngle = -90f,
              sweepAngle = 360f * animatedProgress,
              useCenter = false,
              style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
          }
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(horizontal = 26.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(22.dp),
          )
          Spacer(modifier = Modifier.height(5.dp))
          Text(
            text = activeMeta.bookName,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = activeMeta.chapterLabel,
            fontSize = 12.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = liveState.primaryClockDisplay,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            letterSpacing = (-0.8).sp,
            style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.testTag("studying_clock_text"),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Surface(
            shape = RoundedCornerShape(50),
            color = statusBgColor,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 13.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier.size(7.dp).clip(CircleShape).background(statusDotColor)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = statusLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = statusTextColor,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      val goalText =
        when {
          activeMeta.state.goalMs != null -> TimerMath.formatGoalTile(activeMeta.state.goalMs)
          activeMeta.state.mode == TimerMode.POMODORO ->
            "${activeMeta.state.focusDurationMs / 60_000L}m"
          else -> "None"
        }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        StudyingMetricTile(
          icon = Icons.Outlined.TrackChanges,
          iconTint = colors.primary,
          label =
            if (activeMeta.state.goalMs == null && activeMeta.state.mode == TimerMode.POMODORO) {
              "Focus Block"
            } else {
              "Goal"
            },
          value = goalText,
          modifier = Modifier.weight(1f),
        )
        StudyingMetricTile(
          icon = Icons.AutoMirrored.Outlined.MenuBook,
          iconTint = colors.primary,
          label = "Elapsed",
          value = TimerMath.formatShortElapsed(liveState.metrics.activeFocusMs),
          modifier = Modifier.weight(1f),
        )
        StudyingMetricTile(
          icon = Icons.Filled.LocalFireDepartment,
          iconTint = colors.warning,
          label = "Focus",
          value = "${liveState.metrics.focusPercent}%",
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Surface(
            onClick = onTogglePauseResume,
            shape = CircleShape,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            modifier =
              Modifier.size(58.dp)
                .shadow(
                  elevation = if (colors.isAmoled || colors.isGlass) 0.dp else 8.dp,
                  shape = CircleShape,
                  clip = false,
                  ambientColor = colors.ambientShadow,
                  spotColor = colors.ambientShadow,
                )
                .testTag("studying_pause_resume_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Resume timer" else "Pause timer",
                tint = colors.textPrimary,
                modifier = Modifier.size(24.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = if (isPaused) "Resume" else "Pause",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
          )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Surface(
            onClick = onRequestStop,
            shape = CircleShape,
            color = Color.Transparent,
            modifier =
              Modifier.size(74.dp)
                .shadow(
                  elevation = if (colors.isAmoled || colors.isGlass) 0.dp else 12.dp,
                  shape = CircleShape,
                  clip = false,
                  ambientColor = colors.ambientShadow,
                  spotColor = colors.ambientShadow,
                )
                .testTag("studying_stop_button"),
          ) {
            Box(
              modifier =
                Modifier.fillMaxSize().background(stopGradient),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop session",
                tint = colors.onPrimary,
                modifier = Modifier.size(30.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Stop",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
          )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Surface(
            onClick = onRecordLap,
            shape = CircleShape,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            modifier =
              Modifier.size(58.dp)
                .shadow(
                  elevation = if (colors.isAmoled || colors.isGlass) 0.dp else 8.dp,
                  shape = CircleShape,
                  clip = false,
                  ambientColor = colors.ambientShadow,
                  spotColor = colors.ambientShadow,
                )
                .testTag("studying_lap_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.Flag,
                contentDescription = "Record lap",
                tint = colors.textPrimary,
                modifier = Modifier.size(22.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Lap",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
          )
        }
      }

      if (isBreak) {
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(
          onClick = onSkipBreak,
          shape = RoundedCornerShape(24.dp),
          border = BorderStroke(1.dp, colors.primary),
          modifier = Modifier.testTag("studying_skip_break_button"),
        ) {
          Icon(
            imageVector = Icons.Default.SkipNext,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Skip Break & Start Focus", color = colors.primary, fontWeight = FontWeight.SemiBold)
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.successTint,
        border = BorderStroke(1.dp, colors.successBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = colors.vibrantSuccess,
            modifier = Modifier.size(22.dp),
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text =
                if (isPaused) "Timer is paused"
                else "Timer is running in background",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text =
                if (isPaused) "Tap Resume whenever you're ready to continue."
                else "You can minimize the app, the timer will keep running.",
              fontSize = 12.sp,
              color = colors.textSecondary,
            )
          }
        }
      }

      val recentLaps =
        remember(liveState.events) {
          val filtered = liveState.events.filter { it.eventType == SessionEventType.LAP }
          filtered.size to filtered.reversed().take(5)
        }
      val totalLapCount = recentLaps.first
      val displayedLaps = recentLaps.second
      if (totalLapCount > 0) {
        Spacer(modifier = Modifier.height(14.dp))
        val lapsShape = RoundedCornerShape(20.dp)
        Surface(
          shape = lapsShape,
          color = colors.surface,
          border = BorderStroke(1.dp, colors.border),
          modifier =
            Modifier.fillMaxWidth()
              .shadow(
                elevation = if (colors.isAmoled || colors.isGlass) 0.dp else 6.dp,
                shape = lapsShape,
                clip = false,
                ambientColor = colors.ambientShadow,
                spotColor = colors.ambientShadow,
              ),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
              text = "Laps ($totalLapCount)",
              style = MaterialTheme.typography.labelLarge,
              color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            displayedLaps.forEachIndexed { idx, lap ->
              key(lap.id) {
                if (idx > 0) {
                  HorizontalDivider(
                    thickness = 1.dp,
                    color = colors.border,
                    modifier = Modifier.padding(vertical = 6.dp),
                  )
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = lap.label ?: "Lap",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                  )
                  Text(
                    text = TimerMath.formatHms(lap.elapsedAtEventMs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (liveState.isStopSummaryVisible) {
    StopSessionSummaryDialog(
      meta = activeMeta,
      metrics = liveState.metrics,
      onResume = onResumeFromSummary,
      onDiscard = onDiscardSession,
      onSave = onSaveSession,
    )
  }
}

@Composable
private fun StudyingMetricTile(
  icon: ImageVector,
  iconTint: Color,
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  val colors = AppTheme.colors
  val tileShape = RoundedCornerShape(20.dp)
  Surface(
    shape = tileShape,
    color = colors.surface,
    border = BorderStroke(1.dp, colors.border),
    modifier =
      modifier.shadow(
        elevation = if (colors.isAmoled || colors.isGlass) 0.dp else 8.dp,
        shape = tileShape,
        clip = false,
        ambientColor = colors.ambientShadow,
        spotColor = colors.ambientShadow,
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = iconTint,
        modifier = Modifier.size(21.dp),
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = label,
        fontSize = 12.sp,
        color = colors.textSecondary,
        maxLines = 1,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = value,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textPrimary,
        maxLines = 1,
      )
    }
  }
}
