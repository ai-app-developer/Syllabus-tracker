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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.example.data.repository.DailyStudyCount
import com.example.data.repository.StudyTrendPeriod
import com.example.timer.AnalyticsPeriod
import com.example.timer.BookStudySlice
import com.example.timer.TimerAnalyticsUiState
import com.example.timer.TimerMath
import com.example.ui.theme.AmbientShadow
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LightGreenTint
import com.example.ui.theme.LightIndigoTint
import com.example.ui.theme.LightRedTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.ProgressTrack
import com.example.ui.theme.SecondaryIndigoBar
import com.example.ui.theme.SoftOrangeTint
import com.example.ui.theme.StreakOrange
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VibrantGreen
import com.example.ui.theme.VioletGradientBottom
import com.example.ui.theme.VioletGradientTop
import com.example.ui.viewmodel.ProgressTab
import com.example.ui.viewmodel.SyllabusUiState

@Composable
fun ProgressScreen(
  uiState: SyllabusUiState,
  timerAnalytics: TimerAnalyticsUiState,
  innerPadding: PaddingValues,
  onSelectTab: (ProgressTab) -> Unit,
  onSelectTrendPeriod: (StudyTrendPeriod) -> Unit,
  onSelectAnalyticsPeriod: (AnalyticsPeriod) -> Unit,
  onBookClick: (Long) -> Unit,
  onOpenReadingHistory: () -> Unit,
) {
  val bottomPad = innerPadding.calculateBottomPadding() + 14.dp

  Box(
    modifier = Modifier.fillMaxSize().background(AppBackground),
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

      Text(
        text = stringResource(R.string.progress_title),
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        color = TextPrimary,
      )

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        ProgressTabPill(
          label = stringResource(R.string.tab_overall),
          selected = uiState.progressTab == ProgressTab.OVERALL,
          testTag = "tab_overall",
          onClick = { onSelectTab(ProgressTab.OVERALL) },
          modifier = Modifier.weight(1f),
        )
        ProgressTabPill(
          label = stringResource(R.string.tab_by_book),
          selected = uiState.progressTab == ProgressTab.BY_BOOK,
          testTag = "tab_by_book",
          onClick = { onSelectTab(ProgressTab.BY_BOOK) },
          modifier = Modifier.weight(1f),
        )
        ProgressTabPill(
          label = stringResource(R.string.tab_timer),
          selected = uiState.progressTab == ProgressTab.TIMER,
          testTag = "tab_timer",
          onClick = { onSelectTab(ProgressTab.TIMER) },
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      when (uiState.progressTab) {
        ProgressTab.OVERALL -> {
          Column(
            modifier =
              Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomPad),
          ) {
            val cardShape = RoundedCornerShape(22.dp)
            Surface(
              shape = cardShape,
              color = CardSurface,
              border = BorderStroke(1.dp, HairlineBorder),
              modifier =
                Modifier.fillMaxWidth()
                  .shadow(
                    elevation = 10.dp,
                    shape = cardShape,
                    clip = false,
                    ambientColor = AmbientShadow,
                    spotColor = AmbientShadow,
                  ),
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
                    iconTint = VibrantGreen,
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
          }
        }

        ProgressTab.BY_BOOK -> {
          if (uiState.snapshot.books.isEmpty()) {
            Box(
              modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = bottomPad),
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
              contentPadding = PaddingValues(bottom = bottomPad),
            ) {
              itemsIndexed(uiState.snapshot.books, key = { _, it -> it.book.id }) { index, summary ->
                BookProgressCard(
                  summary = summary,
                  colorSlot = index,
                  onClick = { onBookClick(summary.book.id) },
                )
              }
            }
          }
        }

        ProgressTab.TIMER -> {
          Column(
            modifier =
              Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            TimerStudyTimeCard(
              analytics = timerAnalytics,
              onSelectPeriod = onSelectAnalyticsPeriod,
            )

            BookWiseStudyTimeCard(
              totalPeriodMs = timerAnalytics.periodTotalMs,
              slices = timerAnalytics.bookSlices,
            )

            StudyStreakCard(
              streakDays = timerAnalytics.currentStreakDays,
              onClick = onOpenReadingHistory,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ProgressTabPill(
  label: String,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val pillShape = RoundedCornerShape(50)
  Surface(
    shape = pillShape,
    color = if (selected) PrimaryIndigo else CardSurface,
    border = if (selected) null else BorderStroke(1.dp, HairlineBorder),
    modifier =
      modifier
        .height(40.dp)
        .shadow(
          elevation = if (selected) 6.dp else 3.dp,
          shape = pillShape,
          clip = false,
          ambientColor = AmbientShadow,
          spotColor = AmbientShadow,
        )
        .clip(pillShape)
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        color = if (selected) Color.White else TextPrimary,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun TimerStudyTimeCard(
  analytics: TimerAnalyticsUiState,
  onSelectPeriod: (AnalyticsPeriod) -> Unit,
) {
  var periodMenuExpanded by remember { mutableStateOf(false) }
  val cardShape = RoundedCornerShape(22.dp)
  val maxMs = (analytics.bars.maxOfOrNull { it.durationMs } ?: 0L).coerceAtLeast(1L)

  Surface(
    shape = cardShape,
    color = CardSurface,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier =
      Modifier.fillMaxWidth()
        .shadow(
          elevation = 10.dp,
          shape = cardShape,
          clip = false,
          ambientColor = AmbientShadow,
          spotColor = AmbientShadow,
        ),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Study Time",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
        )

        Box {
          Surface(
            onClick = { periodMenuExpanded = true },
            shape = RoundedCornerShape(50),
            color = AppBackground,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.testTag("timer_period_dropdown"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = analytics.period.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
              )
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Select period",
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
            AnalyticsPeriod.entries.forEach { period ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = period.label,
                    color = if (analytics.period == period) PrimaryIndigo else TextPrimary,
                    fontWeight =
                      if (analytics.period == period) FontWeight.SemiBold else FontWeight.Normal,
                  )
                },
                onClick = {
                  periodMenuExpanded = false
                  onSelectPeriod(period)
                },
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = TimerMath.formatHoursMinutes(analytics.periodTotalMs),
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
        color = TextPrimary,
        modifier = Modifier.testTag("timer_period_total_text"),
      )

      Spacer(modifier = Modifier.height(6.dp))

      val delta = analytics.deltaPercent
      if (delta != null) {
        val isPositive = delta >= 0
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isPositive) LightGreenTint else LightRedTint,
          ) {
            Text(
              text = if (isPositive) "↑ +$delta%" else "↓ $delta%",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (isPositive) SuccessGreen else ErrorRed,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = analytics.period.comparisonLabel,
            fontSize = 13.sp,
            color = TextSecondary,
          )
        }
      } else {
        Text(
          text =
            if (analytics.periodTotalMs == 0L) {
              "No study time yet — start a session to track your progress"
            } else {
              "First tracked ${analytics.period.label.lowercase()}"
            },
          fontSize = 13.sp,
          color = TextSecondary,
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      Row(
        modifier = Modifier.fillMaxWidth().height(124.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
      ) {
        analytics.bars.forEach { bucket ->
          val hasData = bucket.durationMs > 0L
          val barHeight =
            if (!hasData) {
              10.dp
            } else {
              ((bucket.durationMs.toFloat() / maxMs.toFloat()) * 76f).dp.coerceAtLeast(18.dp)
            }
          val barShape = RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 5.dp, bottomEnd = 5.dp)

          Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
          ) {
            Box(
              modifier =
                Modifier.width(28.dp)
                  .height(barHeight)
                  .clip(barShape)
                  .background(
                    when {
                      !hasData -> Brush.verticalGradient(listOf(ProgressTrack, ProgressTrack))
                      bucket.isHighest ->
                        Brush.verticalGradient(listOf(VioletGradientTop, VioletGradientBottom))
                      else ->
                        Brush.verticalGradient(listOf(Color(0xFFCBC3FE), Color(0xFFD9D3FE)))
                    }
                  ),
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
              text = bucket.label,
              fontSize = 11.sp,
              color = TextSecondary,
              maxLines = 1,
            )
            Text(
              text = bucket.valueLabel,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BookWiseStudyTimeCard(
  totalPeriodMs: Long,
  slices: List<BookStudySlice>,
) {
  val cardShape = RoundedCornerShape(22.dp)
  Surface(
    shape = cardShape,
    color = CardSurface,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier =
      Modifier.fillMaxWidth()
        .shadow(
          elevation = 10.dp,
          shape = cardShape,
          clip = false,
          ambientColor = AmbientShadow,
          spotColor = AmbientShadow,
        ),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
      Text(
        text = "Book-wise Study Time",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
      )

      Spacer(modifier = Modifier.height(16.dp))

      if (slices.isEmpty() || totalPeriodMs <= 0L) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier.size(116.dp),
            contentAlignment = Alignment.Center,
          ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
              drawArc(
                color = ProgressTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Butt),
              )
            }
            Text(
              text = "No study\ntime yet",
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = TextSecondary,
              textAlign = TextAlign.Center,
              lineHeight = 15.sp,
            )
          }
          Spacer(modifier = Modifier.width(16.dp))
          Text(
            text = "Complete a study timer session to see your reading time split across books.",
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
          )
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier.size(122.dp),
            contentAlignment = Alignment.Center,
          ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
              val strokeWidth = 19.dp.toPx()
              val gapDeg = if (slices.size > 1) 4f else 0f
              val totalSweepAvail = 360f - (gapDeg * slices.size)
              var startAngle = -90f
              val sumMs = slices.sumOf { it.totalMs }.coerceAtLeast(1L).toFloat()

              slices.forEach { slice ->
                val sweep = (slice.totalMs.toFloat() / sumMs) * totalSweepAvail
                drawArc(
                  color = Color(slice.colorHex),
                  startAngle = startAngle,
                  sweepAngle = sweep.coerceAtLeast(1f),
                  useCenter = false,
                  style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += sweep + gapDeg
              }
            }
            Text(
              text = TimerMath.formatStackedHoursMinutes(totalPeriodMs),
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              textAlign = TextAlign.Center,
              lineHeight = 19.sp,
            )
          }

          Spacer(modifier = Modifier.width(16.dp))

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            slices.forEach { slice ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier.size(9.dp)
                      .clip(CircleShape)
                      .background(Color(slice.colorHex)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = slice.bookName,
                  fontSize = 13.sp,
                  color = TextSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = TimerMath.formatHoursMinutes(slice.totalMs),
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary,
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
private fun StudyStreakCard(
  streakDays: Int,
  onClick: () -> Unit,
) {
  val cardShape = RoundedCornerShape(22.dp)
  Surface(
    onClick = onClick,
    shape = cardShape,
    color = CardSurface,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier =
      Modifier.fillMaxWidth()
        .shadow(
          elevation = 10.dp,
          shape = cardShape,
          clip = false,
          ambientColor = AmbientShadow,
          spotColor = AmbientShadow,
        )
        .testTag("progress_streak_card"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SoftOrangeTint),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.LocalFireDepartment,
          contentDescription = null,
          tint = StreakOrange,
          modifier = Modifier.size(30.dp),
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Study Streak",
          fontSize = 13.sp,
          color = TextSecondary,
        )
        Text(
          text = "$streakDays ${if (streakDays == 1) "Day" else "Days"}",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
        )
        Text(
          text = if (streakDays > 0) "Keep it up!" else "No study time yet — start today!",
          fontSize = 13.sp,
          color = TextSecondary,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = "Open reading history",
        tint = TextSecondary,
      )
    }
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
  val cardShape = RoundedCornerShape(22.dp)

  Surface(
    shape = cardShape,
    color = CardSurface,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier =
      Modifier.fillMaxWidth()
        .shadow(
          elevation = 10.dp,
          shape = cardShape,
          clip = false,
          ambientColor = AmbientShadow,
          spotColor = AmbientShadow,
        ),
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
            shape = RoundedCornerShape(50),
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
              day.count == 0 -> ProgressTrack
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
                Modifier.width(26.dp)
                  .height(barHeight)
                  .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
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
