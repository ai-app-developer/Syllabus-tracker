package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LightIndigoTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.ProgressTrack
import com.example.ui.theme.SoftLavenderCardStart
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPromptBottomSheet(
  totalBooks: Int,
  totalChapters: Int,
  completedChapters: Int,
  overallPercentage: Int,
  isFallbackManualSteps: Boolean,
  isPinBusy: Boolean,
  onBecameVisible: () -> Unit,
  onAddWidgetClick: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  LaunchedEffect(Unit) {
    onBecameVisible()
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor = CardSurface,
    dragHandle = {
      BottomSheetDefaults.DragHandle(color = HairlineBorder)
    },
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp)
          .padding(top = 4.dp, bottom = 20.dp)
          .testTag("widget_prompt_sheet"),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (!isFallbackManualSteps) {
        Surface(
          shape = RoundedCornerShape(24.dp),
          color = SoftLavenderCardStart,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
            contentAlignment = Alignment.Center,
          ) {
            OverviewWidgetLivePreview(
              totalBooks = totalBooks,
              totalChapters = totalChapters,
              completedChapters = completedChapters,
              overallPercentage = overallPercentage,
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
          text = stringResource(R.string.widget_prompt_headline),
          fontSize = 24.sp,
          lineHeight = 30.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          textAlign = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = stringResource(R.string.widget_prompt_body),
          fontSize = 15.sp,
          lineHeight = 22.sp,
          color = TextSecondary,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
          onClick = onAddWidgetClick,
          enabled = !isPinBusy,
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = PrimaryIndigo,
              contentColor = Color.White,
            ),
          modifier =
            Modifier.fillMaxWidth().height(56.dp).testTag("widget_prompt_add_button"),
        ) {
          Text(
            text = stringResource(R.string.widget_prompt_add_action),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("widget_prompt_not_now_button"),
        ) {
          Text(
            text = stringResource(R.string.widget_prompt_not_now),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
          )
        }
      } else {
        ManualWidgetInstallStepsContent(
          onGotIt = onDismiss,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualWidgetStepsBottomSheet(
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor = CardSurface,
    dragHandle = {
      BottomSheetDefaults.DragHandle(color = HairlineBorder)
    },
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp)
          .padding(top = 4.dp, bottom = 24.dp)
          .testTag("manual_widget_fallback_sheet"),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      ManualWidgetInstallStepsContent(onGotIt = onDismiss)
    }
  }
}

@Composable
fun ManualWidgetInstallStepsContent(
  onGotIt: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().testTag("widget_manual_steps_content"),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(R.string.widget_fallback_title),
      fontSize = 22.sp,
      lineHeight = 28.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimary,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(20.dp))

    Surface(
      shape = RoundedCornerShape(20.dp),
      color = SoftLavenderCardStart,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        ManualStepRow(
          icon = Icons.Outlined.TouchApp,
          text = stringResource(R.string.widget_fallback_step_1),
        )
        HorizontalDivider(
          modifier = Modifier.padding(vertical = 12.dp),
          thickness = 1.dp,
          color = HairlineBorder,
        )
        ManualStepRow(
          icon = Icons.Outlined.Widgets,
          text = stringResource(R.string.widget_fallback_step_2),
        )
        HorizontalDivider(
          modifier = Modifier.padding(vertical = 12.dp),
          thickness = 1.dp,
          color = HairlineBorder,
        )
        ManualStepRow(
          icon = Icons.Outlined.OpenWith,
          text = stringResource(R.string.widget_fallback_step_3),
        )
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
      onClick = onGotIt,
      shape = CircleShape,
      colors =
        ButtonDefaults.buttonColors(
          containerColor = PrimaryIndigo,
          contentColor = Color.White,
        ),
      modifier = Modifier.fillMaxWidth().height(56.dp).testTag("widget_fallback_got_it_button"),
    ) {
      Text(
        text = stringResource(R.string.widget_fallback_got_it),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
private fun ManualStepRow(
  icon: ImageVector,
  text: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.size(38.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(LightIndigoTint),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = PrimaryIndigo,
        modifier = Modifier.size(20.dp),
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = TextPrimary,
      fontWeight = FontWeight.Medium,
    )
  }
}

@Composable
fun OverviewWidgetLivePreview(
  totalBooks: Int,
  totalChapters: Int,
  completedChapters: Int,
  overallPercentage: Int,
  modifier: Modifier = Modifier,
) {
  val hasData = totalBooks > 0 || totalChapters > 0
  val displayPercent = if (hasData) overallPercentage.coerceIn(0, 100) else 0
  val displayBooks = if (hasData) totalBooks.coerceAtLeast(0) else 0
  val displayChapters = if (hasData) totalChapters.coerceAtLeast(0) else 0
  val displayCompleted = if (hasData) completedChapters.coerceAtLeast(0) else 0
  val sweepAngle = (displayPercent / 100f) * 360f

  Surface(
    shape = RoundedCornerShape(24.dp),
    color = CardSurface,
    shadowElevation = 2.dp,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = modifier.fillMaxWidth().testTag("overview_widget_live_preview"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.MenuBook,
          contentDescription = null,
          tint = PrimaryIndigo,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.app_name),
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Box(
          modifier =
            Modifier.size(28.dp)
              .clip(CircleShape)
              .background(LightIndigoTint),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = PrimaryIndigo,
            modifier = Modifier.size(16.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier.size(92.dp),
          contentAlignment = Alignment.Center,
        ) {
          Canvas(modifier = Modifier.size(92.dp)) {
            val strokePx = 8.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)
            drawArc(
              color = ProgressTrack,
              startAngle = 0f,
              sweepAngle = 360f,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            if (sweepAngle > 0f) {
              drawArc(
                color = PrimaryIndigo,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
              )
            }
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "$displayPercent%",
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
            )
            Text(
              text = stringResource(R.string.completed_label),
              fontSize = 10.sp,
              fontWeight = FontWeight.Medium,
              color = TextSecondary,
            )
          }
        }

        VerticalDivider(
          modifier = Modifier.height(74.dp).padding(horizontal = 12.dp),
          thickness = 1.dp,
          color = HairlineBorder,
        )

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OverviewMetricPreviewRow(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            count = displayBooks,
            label = stringResource(R.string.books_label),
          )
          OverviewMetricPreviewRow(
            icon = Icons.Outlined.Layers,
            count = displayChapters,
            label = stringResource(R.string.chapters_label),
          )
          OverviewMetricPreviewRow(
            icon = Icons.Outlined.CheckCircle,
            count = displayCompleted,
            label = stringResource(R.string.completed_label),
          )
        }
      }
    }
  }
}

@Composable
private fun OverviewMetricPreviewRow(
  icon: ImageVector,
  count: Int,
  label: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = PrimaryIndigo,
      modifier = Modifier.size(16.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = count.toString(),
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimary,
    )
    Spacer(modifier = Modifier.width(5.dp))
    Text(
      text = label,
      fontSize = 12.sp,
      color = TextSecondary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
