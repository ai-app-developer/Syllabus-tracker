package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.dao.ReadingSessionWithMeta
import com.example.data.local.entity.SessionEventEntity
import com.example.data.repository.ActiveSessionMetadata
import com.example.data.repository.RecoveryPromptData
import com.example.notifications.NotificationHelper
import com.example.timer.AlarmDiagnosticEntry
import com.example.timer.AlarmDiagnosticLog
import com.example.timer.AlarmTestReport
import com.example.timer.ComputedTimerMetrics
import com.example.timer.TimerAlarmScheduler
import com.example.timer.TimerMath
import com.example.timer.TimerMode
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LightGreenTint
import com.example.ui.theme.LightIndigoTint
import com.example.ui.theme.LightRedTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RECOVERY_TIME_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
private val DIALOG_TIME_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val DIALOG_DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionGoalDialog(
  currentGoalMinutes: Int?,
  onDismiss: () -> Unit,
  onConfirm: (Int?) -> Unit,
) {
  val initial = currentGoalMinutes ?: 90
  var hours by remember(currentGoalMinutes) { mutableIntStateOf((initial / 60).coerceIn(0, 8)) }
  var minutes by remember(currentGoalMinutes) { mutableIntStateOf((initial % 60).coerceIn(0, 55)) }
  var noGoalSelected by remember(currentGoalMinutes) { mutableStateOf(currentGoalMinutes == null) }

  val presets = listOf(30 to "30m", 45 to "45m", 60 to "1h", 90 to "1h 30m", 120 to "2h")

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CardSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Text(
        text = "Session Goal",
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
      )
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Set a target reading duration or leave unset.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          FilterChip(
            selected = noGoalSelected,
            onClick = { noGoalSelected = true },
            label = { Text("No goal") },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = PrimaryIndigo,
                selectedLabelColor = Color.White,
                containerColor = AppBackground,
                labelColor = TextPrimary,
              ),
          )
          presets.forEach { (presetMin, label) ->
            key(presetMin) {
              val isSelected = !noGoalSelected && (hours * 60 + minutes) == presetMin
              FilterChip(
                selected = isSelected,
                onClick = {
                  noGoalSelected = false
                  hours = presetMin / 60
                  minutes = presetMin % 60
                },
                label = { Text(label) },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryIndigo,
                    selectedLabelColor = Color.White,
                    containerColor = AppBackground,
                    labelColor = TextPrimary,
                  ),
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DurationStepper(
            label = "Hours",
            value = if (noGoalSelected) 0 else hours,
            onDecrement = {
              noGoalSelected = false
              if (hours > 0) hours -= 1
            },
            onIncrement = {
              noGoalSelected = false
              if (hours < 8) hours += 1
            },
          )
          DurationStepper(
            label = "Minutes",
            value = if (noGoalSelected) 0 else minutes,
            onDecrement = {
              noGoalSelected = false
              minutes = if (minutes >= 5) minutes - 5 else 0
            },
            onIncrement = {
              noGoalSelected = false
              minutes = if (minutes <= 50) minutes + 5 else 55
            },
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val total = hours * 60 + minutes
          if (noGoalSelected || total <= 0) onConfirm(null) else onConfirm(total)
        },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
        modifier = Modifier.testTag("session_goal_save_button"),
      ) {
        Text("Apply", color = Color.White)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = TextSecondary)
      }
    },
  )
}

@Composable
fun PomodoroCustomDialog(
  initialFocusMinutes: Int,
  initialBreakMinutes: Int,
  onDismiss: () -> Unit,
  onConfirm: (Int, Int) -> Unit,
) {
  var focusMin by remember(initialFocusMinutes) { mutableIntStateOf(initialFocusMinutes.coerceIn(5, 120)) }
  var breakMin by remember(initialBreakMinutes) { mutableIntStateOf(initialBreakMinutes.coerceIn(1, 30)) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CardSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Text(
        text = "Custom Pomodoro",
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
      )
    },
    text = {
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        DurationStepper(
          label = "Focus (min)",
          value = focusMin,
          onDecrement = { if (focusMin > 5) focusMin -= 5 },
          onIncrement = { if (focusMin < 120) focusMin += 5 },
        )
        DurationStepper(
          label = "Break (min)",
          value = breakMin,
          onDecrement = { if (breakMin > 1) breakMin -= 1 },
          onIncrement = { if (breakMin < 30) breakMin += 1 },
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(focusMin, breakMin) },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
      ) {
        Text("Save", color = Color.White)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = TextSecondary)
      }
    },
  )
}

@Composable
private fun DurationStepper(
  label: String,
  value: Int,
  onDecrement: () -> Unit,
  onIncrement: () -> Unit,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    Spacer(modifier = Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      FilledTonalIconButton(
        onClick = onDecrement,
        colors =
          IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = LightIndigoTint,
            contentColor = PrimaryIndigo,
          ),
        modifier = Modifier.size(40.dp),
      ) {
        Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
      }
      Text(
        text = value.toString().padStart(2, '0'),
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
        modifier = Modifier.padding(horizontal = 12.dp),
      )
      FilledTonalIconButton(
        onClick = onIncrement,
        colors =
          IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = LightIndigoTint,
            contentColor = PrimaryIndigo,
          ),
        modifier = Modifier.size(40.dp),
      ) {
        Icon(Icons.Default.Add, contentDescription = "Increase $label")
      }
    }
  }
}

@Composable
fun StopSessionSummaryDialog(
  meta: ActiveSessionMetadata,
  metrics: ComputedTimerMetrics,
  onResume: () -> Unit,
  onDiscard: () -> Unit,
  onSave: (String) -> Unit,
) {
  var notesText by remember(meta.state.sessionId) { mutableStateOf(meta.state.notes) }

  AlertDialog(
    onDismissRequest = onResume,
    containerColor = CardSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier.size(38.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(LightIndigoTint),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Timer,
            contentDescription = null,
            tint = PrimaryIndigo,
            modifier = Modifier.size(20.dp),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = "Finish Study Session?",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
          )
          Text(
            text = "${meta.bookName} • ${meta.chapterLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
        }
      }
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = AppBackground,
          border = BorderStroke(1.dp, HairlineBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            SummaryMetricRow("Active Focus", TimerMath.formatHms(metrics.activeFocusMs))
            Spacer(modifier = Modifier.height(6.dp))
            SummaryMetricRow("Paused Time", TimerMath.formatHms(metrics.pauseMs))
            if (meta.state.mode == TimerMode.POMODORO) {
              Spacer(modifier = Modifier.height(6.dp))
              SummaryMetricRow("Break Time", TimerMath.formatHms(metrics.breakMs))
            }
            Spacer(modifier = Modifier.height(6.dp))
            SummaryMetricRow("Focus Score", "${metrics.focusPercent}%")
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
          value = notesText,
          onValueChange = { notesText = it },
          label = { Text("Session Notes") },
          placeholder = { Text("What did you cover?") },
          maxLines = 3,
          shape = RoundedCornerShape(12.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PrimaryIndigo,
              unfocusedBorderColor = HairlineBorder,
              focusedContainerColor = CardSurface,
              unfocusedContainerColor = CardSurface,
            ),
          modifier = Modifier.fillMaxWidth().testTag("stop_summary_notes_input"),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
          onClick = { onSave(notesText) },
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("stop_summary_save_button"),
        ) {
          Text("Save Session", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = onResume,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, HairlineBorder),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("stop_summary_resume_button"),
        ) {
          Text("Resume Studying", color = TextPrimary, fontWeight = FontWeight.Medium)
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDiscard,
        modifier = Modifier.testTag("stop_summary_discard_button"),
      ) {
        Text("Discard", color = ErrorRed, fontWeight = FontWeight.SemiBold)
      }
    },
  )
}

@Composable
fun TimerRecoveryDialog(
  data: RecoveryPromptData,
  onResume: () -> Unit,
  onSaveUpToLastKnown: () -> Unit,
  onDiscard: () -> Unit,
) {
  val lastSeenTime =
    remember(data.state.lastKnownWallTimeMs) {
      Instant.ofEpochMilli(data.state.lastKnownWallTimeMs)
        .atZone(ZoneId.systemDefault())
        .format(RECOVERY_TIME_FORMATTER)
    }

  AlertDialog(
    onDismissRequest = {},
    containerColor = CardSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier.size(38.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(LightIndigoTint),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Restore,
            contentDescription = null,
            tint = PrimaryIndigo,
            modifier = Modifier.size(20.dp),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = "Recover Unfinished Session",
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary,
        )
      }
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text =
            "A study session for ${data.bookName} (${data.chapterLabel}) was interrupted before saving.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = AppBackground,
          border = BorderStroke(1.dp, HairlineBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            SummaryMetricRow(
              "Recorded Focus",
              TimerMath.formatHms(data.recoveredMetrics.activeFocusMs),
            )
            Spacer(modifier = Modifier.height(6.dp))
            SummaryMetricRow("Last Active", lastSeenTime)
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
          onClick = onResume,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("recovery_resume_button"),
        ) {
          Text("Recover session", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = onSaveUpToLastKnown,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, HairlineBorder),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("recovery_save_button"),
        ) {
          Text("Save up to last known time", color = TextPrimary, fontWeight = FontWeight.Medium)
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDiscard,
        modifier = Modifier.testTag("recovery_discard_button"),
      ) {
        Text("Discard", color = ErrorRed, fontWeight = FontWeight.SemiBold)
      }
    },
  )
}

@Composable
fun SessionDetailDialog(
  item: ReadingSessionWithMeta,
  events: List<SessionEventEntity>,
  onDismiss: () -> Unit,
  onSaveNotes: (String) -> Unit,
  onRequestDelete: () -> Unit,
) {
  var notesText by remember(item.session.id) { mutableStateOf(item.session.notes) }
  val (dateText, startStr, endStr) =
    remember(item.session.id, item.session.startWallTimeMs, item.session.endWallTimeMs) {
      val zone = ZoneId.systemDefault()
      Triple(
        Instant.ofEpochMilli(item.session.startWallTimeMs).atZone(zone).format(DIALOG_DATE_FORMATTER),
        Instant.ofEpochMilli(item.session.startWallTimeMs).atZone(zone).format(DIALOG_TIME_FORMATTER),
        Instant.ofEpochMilli(item.session.endWallTimeMs).atZone(zone).format(DIALOG_TIME_FORMATTER),
      )
    }

  val focusPct =
    remember(item.session.activeFocusMs, item.session.pauseMs) {
      val total = item.session.activeFocusMs + item.session.pauseMs
      if (total <= 0L) 100 else ((item.session.activeFocusMs * 100.0) / total).toInt().coerceIn(0, 100)
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CardSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Column {
        Text(
          text = item.bookName,
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary,
        )
        Text(
          text = "Chapter ${item.chapterNumber}: ${item.chapterName}",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = AppBackground,
          border = BorderStroke(1.dp, HairlineBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            SummaryMetricRow("Date", dateText)
            Spacer(modifier = Modifier.height(4.dp))
            SummaryMetricRow("Window", "$startStr – $endStr")
            Spacer(modifier = Modifier.height(4.dp))
            SummaryMetricRow("Mode", if (item.session.mode == TimerMode.POMODORO) "Pomodoro" else "Stopwatch")
            Spacer(modifier = Modifier.height(4.dp))
            SummaryMetricRow("Active Focus", TimerMath.formatHms(item.session.activeFocusMs))
            Spacer(modifier = Modifier.height(4.dp))
            SummaryMetricRow("Paused", TimerMath.formatHms(item.session.pauseMs))
            if (item.session.breakMs > 0L) {
              Spacer(modifier = Modifier.height(4.dp))
              SummaryMetricRow("Break", TimerMath.formatHms(item.session.breakMs))
            }
            Spacer(modifier = Modifier.height(4.dp))
            SummaryMetricRow("Focus Score", "$focusPct%")
          }
        }

        val laps = events.filter { it.eventType == "LAP" }
        if (laps.isNotEmpty()) {
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "Recorded Laps (${laps.size})",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = AppBackground,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              laps.forEachIndexed { idx, lap ->
                key(lap.id) {
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
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = lap.label ?: "Lap ${idx + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                      )
                    }
                    Text(
                      text = TimerMath.formatHms(lap.elapsedAtEventMs),
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = TextPrimary,
                    )
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
          value = notesText,
          onValueChange = { notesText = it },
          label = { Text("Notes") },
          placeholder = { Text("Add session notes") },
          maxLines = 3,
          shape = RoundedCornerShape(12.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PrimaryIndigo,
              unfocusedBorderColor = HairlineBorder,
              focusedContainerColor = CardSurface,
              unfocusedContainerColor = CardSurface,
            ),
          modifier = Modifier.fillMaxWidth().testTag("session_detail_notes_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSaveNotes(notesText) },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
        modifier = Modifier.testTag("session_detail_save_button"),
      ) {
        Text("Save", color = Color.White)
      }
    },
    dismissButton = {
      Row {
        TextButton(
          onClick = onRequestDelete,
          modifier = Modifier.testTag("session_detail_delete_button"),
        ) {
          Text("Delete", color = ErrorRed)
        }
        TextButton(onClick = onDismiss) {
          Text("Close", color = TextSecondary)
        }
      }
    },
  )
}

@Composable
private fun SummaryMetricRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = TextPrimary,
    )
  }
}

private data class TimerPermissionStatusSnapshot(
  val notificationsAllowed: Boolean,
  val exactAlarmAllowed: Boolean,
  val batteryUnrestricted: Boolean,
  val alarmChannelSoundAllowed: Boolean,
)

private fun readTimerPermissionStatus(context: Context): TimerPermissionStatusSnapshot {
  return TimerPermissionStatusSnapshot(
    notificationsAllowed = NotificationHelper.hasNotificationPermission(context),
    exactAlarmAllowed = TimerAlarmScheduler.canScheduleExactAlarms(context),
    batteryUnrestricted = NotificationHelper.isBatteryOptimizationIgnored(context),
    alarmChannelSoundAllowed = NotificationHelper.isTimerAlarmChannelSoundAllowed(context),
  )
}

@Composable
fun TimerPermissionsChecklistDialog(
  isStartFlow: Boolean,
  onDismiss: () -> Unit,
  onContinue: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var status by remember(context) { mutableStateOf(readTimerPermissionStatus(context)) }
  var requestedRuntimeNotifBefore by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        status = readTimerPermissionStatus(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val notificationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      status = readTimerPermissionStatus(context)
      if (!granted && requestedRuntimeNotifBefore) {
        val activity = context as? Activity
        val canAskAgain =
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(
              activity,
              Manifest.permission.POST_NOTIFICATIONS,
            )
        if (!canAskAgain) {
          openAppNotificationSettings(context)
        }
      }
    }

  val settingsLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      status = readTimerPermissionStatus(context)
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CardSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Text(
        text = "Permissions needed for timer alarm",
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
      )
    },
    text = {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        PermissionChecklistRow(
          title = "Notifications",
          description = "Needed to show the running timer and alarm messages.",
          allowed = status.notificationsAllowed,
          allowButtonTag = "perm_allow_notifications",
          onAllowClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              if (!requestedRuntimeNotifBefore) {
                requestedRuntimeNotifBefore = true
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              } else {
                openAppNotificationSettings(context)
              }
            } else {
              openAppNotificationSettings(context)
            }
          },
        )

        PermissionChecklistRow(
          title = "Alarms & reminders",
          description = "Needed so the alarm rings exactly on time.",
          allowed = status.exactAlarmAllowed,
          allowButtonTag = "perm_allow_exact_alarm",
          onAllowClick = {
            val pkgUri = Uri.parse("package:${context.packageName}")
            val intent =
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri)
              } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
              }
            runCatching { settingsLauncher.launch(intent) }.onFailure {
              runCatching {
                settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
              }
            }
          },
        )

        PermissionChecklistRow(
          title = "Battery: Unrestricted",
          description = "Needed so the phone does not stop the timer in the background.",
          allowed = status.batteryUnrestricted,
          allowButtonTag = "perm_allow_battery",
          onAllowClick = {
            val pkgUri = Uri.parse("package:${context.packageName}")
            val primaryIntent =
              Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
            runCatching { settingsLauncher.launch(primaryIntent) }.onFailure {
              runCatching {
                settingsLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
              }.onFailure {
                runCatching {
                  settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
                }
              }
            }
          },
        )

        PermissionChecklistRow(
          title = "Alarm notification channel sound",
          description = "Make sure sound is on for this channel.",
          allowed = status.alarmChannelSoundAllowed,
          allowButtonTag = "perm_allow_channel_sound",
          onAllowClick = {
            val channelIntent =
              Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.TIMER_ALARM_CHANNEL_ID)
              }
            runCatching { settingsLauncher.launch(channelIntent) }.onFailure {
              openAppNotificationSettings(context)
            }
          },
        )

        PermissionChecklistNoteRow(
          title = "Do Not Disturb note",
          description = "If your phone is in Silent/Do Not Disturb mode, the alarm may not ring.",
        )

        PermissionChecklistNoteRow(
          title = "Flyme / Meizu note",
          description = "Enable Autostart for this app, and lock the app in Recent apps.",
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onContinue,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
        modifier = Modifier.testTag("permission_checklist_continue_button"),
      ) {
        Text(
          text = if (isStartFlow) "Continue anyway" else "Done",
          color = Color.White,
        )
      }
    },
    dismissButton =
      if (isStartFlow) {
        {
          TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("permission_checklist_cancel_button"),
          ) {
            Text("Cancel", color = TextSecondary)
          }
        }
      } else {
        null
      },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionChecklistRow(
  title: String,
  description: String,
  allowed: Boolean,
  allowButtonTag: String,
  onAllowClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = AppBackground,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.CenterVertically),
          )
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (allowed) LightGreenTint else LightRedTint,
            modifier = Modifier.align(Alignment.CenterVertically),
          ) {
            Text(
              text = if (allowed) "Allowed" else "Not allowed",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (allowed) SuccessGreen else ErrorRed,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
        )
      }
      OutlinedButton(
        onClick = onAllowClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, PrimaryIndigo),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.heightIn(min = 48.dp).testTag(allowButtonTag),
      ) {
        Text(
          text = "Allow",
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = PrimaryIndigo,
        )
      }
    }
  }
}

@Composable
private fun PermissionChecklistNoteRow(
  title: String,
  description: String,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = LightIndigoTint.copy(alpha = 0.55f),
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Icon(
        imageVector = Icons.Outlined.Info,
        contentDescription = null,
        tint = PrimaryIndigo,
        modifier = Modifier.size(18.dp).padding(top = 2.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
        )
      }
    }
  }
}

private fun openAppNotificationSettings(context: Context) {
  val pkgUri = Uri.parse("package:${context.packageName}")
  val notifIntent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
      putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
  runCatching { context.startActivity(notifIntent) }.onFailure {
    runCatching {
      context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
    }
  }
}

@Composable
fun TimerAlarmTestDialog(
  report: AlarmTestReport,
  isRunningTest: Boolean,
  scheduled10sStatus: String?,
  onRunTestAgain: () -> Unit,
  onSchedule10sTest: () -> Unit,
  onOpenAlarmLog: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CardSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Text(
        text = "Timer alarm test",
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
      )
    },
    text = {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(max = 430.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TestResultRow(
          label = "Sound started",
          value = if (report.soundStarted) "Yes (${report.soundMethod})" else "No (${report.soundMethod})",
          healthy = report.soundStarted,
        )
        TestResultRow(
          label = "Vibration",
          value = if (report.vibrationTriggered) "Yes" else "No",
          healthy = report.vibrationTriggered,
        )
        TestResultRow(
          label = "Notification permission",
          value = if (report.notificationPermissionGranted) "Granted" else "Denied",
          healthy = report.notificationPermissionGranted,
        )
        TestResultRow(
          label = "Alarm volume",
          value = "${report.alarmVolumePercent}%",
          healthy = report.alarmVolumePercent > 0,
        )
        TestResultRow(
          label = "Do Not Disturb",
          value = report.dndStatusLabel,
          healthy = !report.dndBlocked,
        )
        TestResultRow(
          label = "Exact alarm permission",
          value = if (report.exactAlarmAllowed) "Allowed" else "Denied",
          healthy = report.exactAlarmAllowed,
        )
        TestResultRow(
          label = "Channel enabled",
          value = if (report.channelEnabled) "Yes (timer_alarm_v2)" else "No",
          healthy = report.channelEnabled,
        )

        if (!scheduled10sStatus.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = LightIndigoTint.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth().testTag("test_alarm_10s_status"),
          ) {
            Text(
              text = scheduled10sStatus,
              style = MaterialTheme.typography.bodySmall,
              color = TextPrimary,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedButton(
          onClick = onSchedule10sTest,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, PrimaryIndigo),
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("test_alarm_10s_button"),
        ) {
          Text(
            text = "Test in 10 seconds",
            color = PrimaryIndigo,
            fontWeight = FontWeight.SemiBold,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onOpenAlarmLog,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("test_alarm_log_button"),
          ) {
            Text(text = "Alarm log", color = TextPrimary)
          }
          OutlinedButton(
            onClick = onRunTestAgain,
            enabled = !isRunningTest,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("test_alarm_run_again_button"),
          ) {
            Text(text = if (isRunningTest) "Testing..." else "Run again", color = PrimaryIndigo)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
        modifier = Modifier.testTag("test_alarm_done_button"),
      ) {
        Text(text = "Done", color = Color.White)
      }
    },
  )
}

@Composable
private fun TestResultRow(
  label: String,
  value: String,
  healthy: Boolean,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = AppBackground,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = Modifier.weight(1f).padding(end = 8.dp),
      )
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (healthy) LightGreenTint else LightRedTint,
      ) {
        Text(
          text = value,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = if (healthy) SuccessGreen else ErrorRed,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }
    }
  }
}

@Composable
fun AlarmDiagnosticLogDialog(
  entries: List<AlarmDiagnosticEntry>,
  onClear: () -> Unit,
  onDismiss: () -> Unit,
) {
  val timeFormatter = remember {
    DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.US)
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = CardSurface,
    shape = RoundedCornerShape(16.dp),
    title = {
      Text(
        text = "Alarm log (last 20)",
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
      )
    },
    text = {
      if (entries.isEmpty()) {
        Text(
          text = "No alarm events recorded yet.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
      } else {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(max = 400.dp)
              .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          entries.forEach { entry ->
            val timeText =
              remember(entry.timestampMs) {
                Instant.ofEpochMilli(entry.timestampMs)
                  .atZone(ZoneId.systemDefault())
                  .format(timeFormatter)
              }
            val isSuccess = entry.outcome == AlarmDiagnosticLog.OUTCOME_PLAYED
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = AppBackground,
              border = BorderStroke(1.dp, HairlineBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "${entry.eventType} • ${entry.source}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                  )
                  Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSuccess) LightGreenTint else LightIndigoTint,
                  ) {
                    Text(
                      text = entry.outcome,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = if (isSuccess) SuccessGreen else PrimaryIndigo,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                  }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = timeText,
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                )
                if (entry.detail.isNotBlank()) {
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
        modifier = Modifier.testTag("alarm_log_close_button"),
      ) {
        Text(text = "Close", color = Color.White)
      }
    },
    dismissButton =
      if (entries.isNotEmpty()) {
        {
          TextButton(
            onClick = onClear,
            modifier = Modifier.testTag("alarm_log_clear_button"),
          ) {
            Text(text = "Clear", color = ErrorRed)
          }
        }
      } else {
        null
      },
  )
}


