package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.repository.DailyStudyCount
import com.example.data.repository.StudyTrendPeriod
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LightIndigoTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.SecondaryIndigoBar
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ProgressTab
import com.example.ui.viewmodel.SyllabusUiState

@Composable
fun ProgressScreen(
  uiState: SyllabusUiState,
  innerPadding: PaddingValues,
  onSelectTab: (ProgressTab) -> Unit,
  onSelectTrendPeriod: (StudyTrendPeriod) -> Unit,
  onBookClick: (Long) -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().background(AppBackground).padding(innerPadding),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 20.dp),
    ) {
      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = stringResource(R.string.progress_title),
        style = MaterialTheme.typography.headlineLarge,
        color = TextPrimary,
      )

      Spacer(modifier = Modifier.height(14.dp))

      Surface(
        shape = RoundedCornerShape(50),
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth().height(46.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SegmentedTabButton(
            label = stringResource(R.string.tab_overall),
            selected = uiState.progressTab == ProgressTab.OVERALL,
            testTag = "tab_overall",
            onClick = { onSelectTab(ProgressTab.OVERALL) },
            modifier = Modifier.weight(1f),
          )
          SegmentedTabButton(
            label = stringResource(R.string.tab_by_book),
            selected = uiState.progressTab == ProgressTab.BY_BOOK,
            testTag = "tab_by_book",
            onClick = { onSelectTab(ProgressTab.BY_BOOK) },
            modifier = Modifier.weight(1f),
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      when (uiState.progressTab) {
        ProgressTab.OVERALL -> {
          Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
          ) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = CardSurface,
              border = BorderStroke(1.dp, HairlineBorder),
              shadowElevation = 1.dp,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column {
                Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp)) {
                  Text(
                    text = "${uiState.snapshot.overallPercentage}%",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = stringResource(R.string.syllabus_completed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                  )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlatCumulativeChart(
                  series = uiState.cumulativeSeries,
                  modifier = Modifier.fillMaxWidth().height(92.dp),
                )

                HorizontalDivider(thickness = 1.dp, color = HairlineBorder)

                Row(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  ProgressStatColumn(
                    icon = Icons.Outlined.CheckCircle,
                    iconTint = SuccessGreen,
                    value = uiState.snapshot.completedChapters.toString(),
                    label = stringResource(R.string.completed_label),
                    modifier = Modifier.weight(1f),
                  )
                  VerticalDivider(
                    modifier = Modifier.height(44.dp),
                    thickness = 1.dp,
                    color = HairlineBorder,
                  )
                  ProgressStatColumn(
                    icon = Icons.Outlined.AccessTime,
                    iconTint = TextSecondary,
                    value = uiState.snapshot.remainingChapters.toString(),
                    label = stringResource(R.string.remaining_label),
                    modifier = Modifier.weight(1f),
                  )
                  VerticalDivider(
                    modifier = Modifier.height(44.dp),
                    thickness = 1.dp,
                    color = HairlineBorder,
                  )
                  ProgressStatColumn(
                    icon = Icons.Outlined.Layers,
                    iconTint = PrimaryIndigo,
                    value = uiState.snapshot.totalChapters.toString(),
                    label = stringResource(R.string.total_label),
                    modifier = Modifier.weight(1f),
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            StudyTrendCard(
              trend = uiState.weeklyTrend,
              period = uiState.trendPeriod,
              onSelectPeriod = onSelectTrendPeriod,
            )

            Spacer(modifier = Modifier.height(16.dp))
          }
        }

        ProgressTab.BY_BOOK -> {
          if (uiState.snapshot.books.isEmpty()) {
            Box(
              modifier = Modifier.weight(1f).fillMaxWidth(),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "No books added yet.",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier.weight(1f).fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              contentPadding = PaddingValues(bottom = 16.dp),
            ) {
              items(uiState.snapshot.books, key = { it.book.id }) { summary ->
                BookProgressCard(
                  summary = summary,
                  onClick = { onBookClick(summary.book.id) },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SegmentedTabButton(
  label: String,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .height(38.dp)
        .clip(RoundedCornerShape(50))
        .background(if (selected) PrimaryIndigo else Color.Transparent)
        .clickable(onClick = onClick)
        .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      color = if (selected) Color.White else TextSecondary,
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun FlatCumulativeChart(
  series: List<Float>,
  modifier: Modifier = Modifier,
) {
  val points = if (series.size >= 2) series else List(7) { 0f }

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val topPad = 12.dp.toPx()
    val usableHeight = (h - topPad).coerceAtLeast(1f)
    val stepX = w / (points.size - 1).coerceAtLeast(1)

    val coords =
      points.mapIndexed { index, value ->
        val x = index * stepX
        val y = topPad + usableHeight * (1f - value.coerceIn(0f, 1f))
        Offset(x, y)
      }

    val linePath =
      Path().apply {
        moveTo(coords.first().x, coords.first().y)
        for (i in 0 until coords.lastIndex) {
          val p0 = coords[i]
          val p1 = coords[i + 1]
          val midX = (p0.x + p1.x) / 2f
          cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
        }
      }

    val fillPath =
      Path().apply {
        addPath(linePath)
        lineTo(w, h)
        lineTo(0f, h)
        close()
      }

    drawPath(path = fillPath, color = LightIndigoTint)
    drawPath(
      path = linePath,
      color = PrimaryIndigo,
      style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}

@Composable
private fun ProgressStatColumn(
  icon: ImageVector,
  iconTint: Color,
  value: String,
  label: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = iconTint,
      modifier = Modifier.size(20.dp),
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = value,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimary,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = TextSecondary,
    )
  }
}

@Composable
private fun StudyTrendCard(
  trend: List<DailyStudyCount>,
  period: StudyTrendPeriod,
  onSelectPeriod: (StudyTrendPeriod) -> Unit,
) {
  var periodMenuExpanded by remember { mutableStateOf(false) }
  val maxCount = (trend.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = CardSurface,
    border = BorderStroke(1.dp, HairlineBorder),
    shadowElevation = 1.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.study_trend),
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary,
        )

        Box {
          Surface(
            onClick = { periodMenuExpanded = true },
            shape = RoundedCornerShape(10.dp),
            color = AppBackground,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.testTag("trend_period_dropdown"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text =
                  if (period == StudyTrendPeriod.THIS_WEEK) {
                    stringResource(R.string.this_week)
                  } else {
                    stringResource(R.string.last_week)
                  },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
              )
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select week",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp),
              )
            }
          }

          DropdownMenu(
            expanded = periodMenuExpanded,
            onDismissRequest = { periodMenuExpanded = false },
            containerColor = CardSurface,
          ) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.this_week), color = TextPrimary) },
              onClick = {
                periodMenuExpanded = false
                onSelectPeriod(StudyTrendPeriod.THIS_WEEK)
              },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.last_week), color = TextPrimary) },
              onClick = {
                periodMenuExpanded = false
                onSelectPeriod(StudyTrendPeriod.LAST_WEEK)
              },
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Row(
        modifier = Modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
      ) {
        trend.forEach { day ->
          val barColor =
            when {
              day.count == 0 -> LightIndigoTint
              day.isToday -> PrimaryIndigo
              else -> SecondaryIndigoBar
            }
          val barHeight =
            if (day.count == 0) 8.dp
            else ((day.count.toFloat() / maxCount.toFloat()) * 86f).dp.coerceAtLeast(16.dp)

          Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
          ) {
            if (day.count > 0) {
              Text(
                text = day.count.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (day.isToday) PrimaryIndigo else TextSecondary,
              )
              Spacer(modifier = Modifier.height(4.dp))
            }
            Box(
              modifier =
                Modifier.width(24.dp)
                  .height(barHeight)
                  .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                  .background(barColor),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = day.dayLabel.take(3),
              fontSize = 12.sp,
              fontWeight = if (day.isToday) FontWeight.SemiBold else FontWeight.Normal,
              color = if (day.isToday) TextPrimary else TextSecondary,
            )
          }
        }
      }
    }
  }
}
