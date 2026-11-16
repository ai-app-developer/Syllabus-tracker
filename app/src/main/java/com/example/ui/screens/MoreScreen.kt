package com.example.ui.screens

import android.Manifest
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.UserPreferences
import com.example.notifications.NotificationHelper
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LightIndigoTint
import com.example.ui.theme.LightRedTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.ProgressTrack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MoreScreen(
  preferences: UserPreferences,
  innerPadding: PaddingValues,
  onEditDailyGoal: () -> Unit,
  onToggleReminder: (Boolean) -> Unit,
  onEditReminderTime: () -> Unit,
  onExportBackupUri: (Uri) -> Unit,
  onImportBackupUri: (Uri) -> Unit,
) {
  val context = LocalContext.current
  var hasPermission by remember {
    mutableStateOf(NotificationHelper.hasNotificationPermission(context))
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted
      if (granted) {
        onToggleReminder(true)
      }
    }

  val exportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      if (uri != null) {
        onExportBackupUri(uri)
      }
    }

  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        onImportBackupUri(uri)
      }
    }

  val formattedTime =
    remember(preferences.reminderHour, preferences.reminderMinute) {
      LocalTime.of(preferences.reminderHour, preferences.reminderMinute)
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }

  Box(
    modifier = Modifier.fillMaxSize().background(AppBackground).padding(innerPadding),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 600.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
      Text(
        text = stringResource(R.string.more_title),
        style = MaterialTheme.typography.headlineLarge,
        color = TextPrimary,
      )

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = "Study Goal",
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
      )
      Spacer(modifier = Modifier.height(8.dp))

      Surface(
        onClick = onEditDailyGoal,
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().testTag("more_daily_goal_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LightIndigoTint),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.CalendarToday,
              contentDescription = null,
              tint = PrimaryIndigo,
              modifier = Modifier.size(20.dp),
            )
          }
          Spacer(modifier = Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Daily Chapter Goal",
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary,
            )
            Text(
              text =
                "${preferences.dailyGoal} ${if (preferences.dailyGoal == 1) "chapter" else "chapters"} per day",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Edit daily goal",
            tint = TextSecondary,
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "Notifications",
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
      )
      Spacer(modifier = Modifier.height(8.dp))

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.size(42.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(LightIndigoTint),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = PrimaryIndigo,
                modifier = Modifier.size(20.dp),
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Daily Study Reminder",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
              )
              Text(
                text = "Get reminded of remaining chapters",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
            }
            Switch(
              checked = preferences.reminderEnabled,
              onCheckedChange = { enable ->
                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
                  permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                  onToggleReminder(enable)
                }
              },
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = Color.White,
                  checkedTrackColor = PrimaryIndigo,
                  uncheckedThumbColor = Color.White,
                  uncheckedTrackColor = ProgressTrack,
                  uncheckedBorderColor = Color.Transparent,
                ),
              modifier = Modifier.testTag("reminder_enabled_switch"),
            )
          }

          HorizontalDivider(thickness = 1.dp, color = HairlineBorder)

          Surface(
            onClick = onEditReminderTime,
            color = CardSurface,
            modifier = Modifier.fillMaxWidth().testTag("reminder_time_row"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightIndigoTint),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Outlined.AccessTime,
                  contentDescription = null,
                  tint = PrimaryIndigo,
                  modifier = Modifier.size(20.dp),
                )
              }
              Spacer(modifier = Modifier.width(14.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Reminder Time",
                  style = MaterialTheme.typography.titleMedium,
                  color = TextPrimary,
                )
                Text(
                  text = formattedTime,
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                )
              }
              Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Change reminder time",
                tint = TextSecondary,
              )
            }
          }
        }
      }

      if (
        preferences.reminderEnabled &&
          !hasPermission &&
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
      ) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = LightRedTint,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Notification permission is required for daily reminders.",
              style = MaterialTheme.typography.bodyMedium,
              color = ErrorRed,
              modifier = Modifier.weight(1f),
            )
            TextButton(
              onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            ) {
              Text("Allow", color = PrimaryIndigo, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "Backup",
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
      )
      Spacer(modifier = Modifier.height(8.dp))

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column {
          Surface(
            onClick = {
              val dateStr = LocalDate.now().toString()
              exportLauncher.launch("syllabus_tracker_backup_$dateStr.json")
            },
            color = CardSurface,
            modifier = Modifier.fillMaxWidth().testTag("backup_export_row"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightIndigoTint),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Outlined.Upload,
                  contentDescription = null,
                  tint = PrimaryIndigo,
                  modifier = Modifier.size(20.dp),
                )
              }
              Spacer(modifier = Modifier.width(14.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Export Data",
                  style = MaterialTheme.typography.titleMedium,
                  color = TextPrimary,
                )
                Text(
                  text = "Save books, chapters, and settings as JSON",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                )
              }
              Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Export data",
                tint = TextSecondary,
              )
            }
          }

          HorizontalDivider(thickness = 1.dp, color = HairlineBorder)

          Surface(
            onClick = { importLauncher.launch(arrayOf("application/json")) },
            color = CardSurface,
            modifier = Modifier.fillMaxWidth().testTag("backup_import_row"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LightIndigoTint),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Outlined.Download,
                  contentDescription = null,
                  tint = PrimaryIndigo,
                  modifier = Modifier.size(20.dp),
                )
              }
              Spacer(modifier = Modifier.width(14.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Import Data",
                  style = MaterialTheme.typography.titleMedium,
                  color = TextPrimary,
                )
                Text(
                  text = "Restore or merge from a JSON backup",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                )
              }
              Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Import data",
                tint = TextSecondary,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "About",
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
      )
      Spacer(modifier = Modifier.height(8.dp))

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier.size(42.dp)
                  .clip(RoundedCornerShape(12.dp))
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
            Spacer(modifier = Modifier.width(14.dp))
            Column {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
              )
              Text(
                text = "Version 1.0",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
          Row(verticalAlignment = Alignment.Top) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = null,
              tint = TextSecondary,
              modifier = Modifier.size(16.dp).padding(top = 2.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text =
                "Offline-first personal syllabus tracker. Your books, chapters, and study progress are stored locally on your device.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}
