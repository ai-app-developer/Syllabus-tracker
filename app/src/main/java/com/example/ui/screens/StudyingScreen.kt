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
import com.example.R
import com.example.timer.SessionEventType
import com.example.timer.TimerMath
import com.example.timer.TimerMode
import com.example.ui.components.StopSessionSummaryDialog
import com.example.ui.theme.AmbientShadow
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LightGreenBorder
import com.example.ui.theme.LightGreenTint
import com.example.ui.theme.LightIndigoTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.ProgressTrack
import com.example.ui.theme.RingGradientBottom
import com.example.ui.theme.RingGradientMid
import com.example.ui.theme.RingGradientTop
import com.example.ui.theme.SoftOrangeTint
import com.example.ui.theme.StreakOrange
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VibrantGreen
import com.example.ui.theme.VioletGradientBottom
import com.example.ui.theme.VioletGradientTop
import com.example.ui.viewmodel.LiveStudyingUiState

private val WarmAmberText = Color(0xFFD97706)

@Composable
fun StudyingScreen(
  liveState: LiveStudyingUiState,
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

  var menuExpanded by remember { mutableStateOf(false) }
  val activeMeta = liveState.activeMeta

  if (activeMeta == null) {
    Box(
      modifier = Modifier.fillMaxSize().background(AppBackground).padding(innerPadding),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(
          text = "No Active Session",
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Start a study session from Study Timer setup.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
          onClick = onMinimizeBack,
          shape = RoundedCornerShape(24.dp),
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
        ) {
          Text("Go Back", color = Color.White)
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
      isPaused -> SoftOrangeTint
      isBreak -> LightIndigoTint
      else -> LightGreenTint
    }
  val statusDotColor =
    when {
      isPaused -> WarmAmberText
      isBreak -> PrimaryIndigo
      else -> VibrantGreen
    }
  val statusTextColor =
    when {
      isPaused -> WarmAmberText
      isBreak -> PrimaryIndigo
      else -> SuccessGreen
    }
  val statusLabel =
    when {
      isPaused -> "Paused"
      isBreak -> "Break • Cycle ${activeMeta.state.pomodoroCycle}"
      activeMeta.state.mode == TimerMode.POMODORO -> "Focus • Cycle ${activeMeta.state.pomodoroCycle}"
      else -> "Studying..."
    }

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
            tint = TextPrimary,
            modifier = Modifier.size(28.dp),
          )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
          Text(
            text = stringResource(R.string.studying_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
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
              tint = TextPrimary,
            )
          }
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            containerColor = CardSurface,
          ) {
            DropdownMenuItem(
              text = { Text("Reading History", color = TextPrimary) },
              onClick = {
                menuExpanded = false
                onOpenHistory()
              },
            )
            if (isBreak) {
              DropdownMenuItem(
                text = { Text("Skip Break", color = TextPrimary) },
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
                elevation = 10.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = AmbientShadow,
                spotColor = AmbientShadow,
              )
              .clip(CircleShape)
              .background(CardSurface),
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
            drawPath(path = backWave, color = Color(0xFFF1EEFE))

            val frontWave =
              Path().apply {
                moveTo(0f, h * 0.80f)
                cubicTo(w * 0.35f, h * 0.89f, w * 0.70f, h * 0.70f, w, h * 0.80f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
              }
            drawPath(path = frontWave, color = Color(0xFFE3DCFE))
          }
        }

        // Outer circular progress ring with gradient arc
        Canvas(modifier = Modifier.fillMaxSize()) {
          val strokeWidth = 11.dp.toPx()
          drawArc(
            color = ProgressTrack,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
          )
          if (animatedProgress > 0f) {
            drawArc(
              brush =
                Brush.linearGradient(
                  colors = listOf(RingGradientTop, RingGradientMid, RingGradientBottom),
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
            tint = PrimaryIndigo,
            modifier = Modifier.size(22.dp),
          )
          Spacer(modifier = Modifier.height(5.dp))
          Text(
            text = activeMeta.bookName,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = activeMeta.chapterLabel,
            fontSize = 12.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = liveState.primaryClockDisplay,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
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
          iconTint = PrimaryIndigo,
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
          iconTint = PrimaryIndigo,
          label = "Elapsed",
          value = TimerMath.formatShortElapsed(liveState.metrics.activeFocusMs),
          modifier = Modifier.weight(1f),
        )
        StudyingMetricTile(
          icon = Icons.Filled.LocalFireDepartment,
          iconTint = StreakOrange,
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
            color = CardSurface,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier =
              Modifier.size(58.dp)
                .shadow(
                  elevation = 8.dp,
                  shape = CircleShape,
                  clip = false,
                  ambientColor = AmbientShadow,
                  spotColor = AmbientShadow,
                )
                .testTag("studying_pause_resume_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Resume timer" else "Pause timer",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = if (isPaused) "Resume" else "Pause",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
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
                  elevation = 12.dp,
                  shape = CircleShape,
                  clip = false,
                  ambientColor = AmbientShadow,
                  spotColor = AmbientShadow,
                )
                .testTag("studying_stop_button"),
          ) {
            Box(
              modifier =
                Modifier.fillMaxSize()
                  .background(
                    Brush.verticalGradient(listOf(VioletGradientTop, VioletGradientBottom))
                  ),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop session",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Stop",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
          )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Surface(
            onClick = onRecordLap,
            shape = CircleShape,
            color = CardSurface,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier =
              Modifier.size(58.dp)
                .shadow(
                  elevation = 8.dp,
                  shape = CircleShape,
                  clip = false,
                  ambientColor = AmbientShadow,
                  spotColor = AmbientShadow,
                )
                .testTag("studying_lap_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Outlined.Flag,
                contentDescription = "Record lap",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Lap",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
          )
        }
      }

      if (isBreak) {
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(
          onClick = onSkipBreak,
          shape = RoundedCornerShape(24.dp),
          border = BorderStroke(1.dp, PrimaryIndigo),
          modifier = Modifier.testTag("studying_skip_break_button"),
        ) {
          Icon(
            imageVector = Icons.Default.SkipNext,
            contentDescription = null,
            tint = PrimaryIndigo,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Skip Break & Start Focus", color = PrimaryIndigo, fontWeight = FontWeight.SemiBold)
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      Surface(
        shape = RoundedCornerShape(18.dp),
        color = LightGreenTint,
        border = BorderStroke(1.dp, LightGreenBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = VibrantGreen,
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
              color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text =
                if (isPaused) "Tap Resume whenever you're ready to continue."
                else "You can minimize the app, the timer will keep running.",
              fontSize = 12.sp,
              color = TextSecondary,
            )
          }
        }
      }

      val laps = liveState.events.filter { it.eventType == SessionEventType.LAP }
      if (laps.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        val lapsShape = RoundedCornerShape(20.dp)
        Surface(
          shape = lapsShape,
          color = CardSurface,
          border = BorderStroke(1.dp, HairlineBorder),
          modifier =
            Modifier.fillMaxWidth()
              .shadow(
                elevation = 6.dp,
                shape = lapsShape,
                clip = false,
                ambientColor = AmbientShadow,
                spotColor = AmbientShadow,
              ),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
              text = "Laps (${laps.size})",
              style = MaterialTheme.typography.labelLarge,
              color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            laps.reversed().take(5).forEachIndexed { idx, lap ->
              if (idx > 0) {
                HorizontalDivider(
                  thickness = 1.dp,
                  color = HairlineBorder,
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
                  color = TextPrimary,
                )
                Text(
                  text = TimerMath.formatHms(lap.elapsedAtEventMs),
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = PrimaryIndigo,
                )
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
  val tileShape = RoundedCornerShape(20.dp)
  Surface(
    shape = tileShape,
    color = CardSurface,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier =
      modifier.shadow(
        elevation = 8.dp,
        shape = tileShape,
        clip = false,
        ambientColor = AmbientShadow,
        spotColor = AmbientShadow,
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
        color = TextSecondary,
        maxLines = 1,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = value,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        maxLines = 1,
      )
    }
  }
}
