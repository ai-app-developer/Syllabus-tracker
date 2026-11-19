package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.AmbientShadow
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LightIndigoTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.ProgressTrack
import com.example.ui.theme.RingGradientBottom
import com.example.ui.theme.RingGradientMid
import com.example.ui.theme.RingGradientTop
import com.example.ui.theme.SoftLavenderBorder
import com.example.ui.theme.SoftLavenderCardEnd
import com.example.ui.theme.SoftLavenderCardStart
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VibrantGreen
import com.example.ui.theme.VioletGradientBottom
import com.example.ui.theme.VioletGradientTop
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.LiveStudyingUiState
import kotlinx.coroutines.flow.StateFlow

private val HomeTimerCardGradient =
  Brush.horizontalGradient(colors = listOf(SoftLavenderCardStart, SoftLavenderCardEnd))
private val HomeTimerPlayGradient =
  Brush.verticalGradient(colors = listOf(VioletGradientTop, VioletGradientBottom))

@Composable
fun HomeScreen(
  state: HomeUiState,
  liveStudyingState: StateFlow<LiveStudyingUiState>,
  innerPadding: PaddingValues,
  onBellClick: () -> Unit,
  onStudyTimerClick: () -> Unit,
  onTodaysPlanClick: () -> Unit,
  onContinueBookClick: (Long) -> Unit,
  onAddBookClick: () -> Unit,
) {
  val targetFraction =
    if (state.totalChapters == 0) 0f
    else state.completedChapters.toFloat() / state.totalChapters.toFloat()
  val animatedFraction by
    animateFloatAsState(
      targetValue = targetFraction.coerceIn(0f, 1f),
      animationSpec = tween(durationMillis = 180),
      label = "home_progress_ring",
    )

  val remainingToday = (state.preferences.dailyGoal - state.completedToday).coerceAtLeast(0)
  val showBellDot = state.preferences.reminderEnabled && remainingToday > 0

  Box(
    modifier = Modifier.fillMaxSize().background(AppBackground),
    contentAlignment = Alignment.TopCenter,
  ) {
    Image(
      painter = painterResource(id = R.drawable.bg_home_waves),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
    Column(
      modifier =
        Modifier.widthIn(max = 600.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(
            top = innerPadding.calculateTopPadding() + 14.dp,
            bottom = innerPadding.calculateBottomPadding() + 12.dp,
            start = 20.dp,
            end = 20.dp,
          ),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Syllabus\nTracker",
            fontSize = 31.sp,
            lineHeight = 35.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
            color = TextPrimary,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = stringResource(R.string.home_subtitle),
            fontSize = 14.sp,
            color = TextSecondary,
          )
        }

        IconButton(
          onClick = onBellClick,
          modifier = Modifier.size(48.dp).testTag("home_bell_button"),
        ) {
          Box(contentAlignment = Alignment.TopEnd) {
            Icon(
              imageVector = Icons.Outlined.Notifications,
              contentDescription = "Reminder settings",
              tint = TextPrimary,
              modifier = Modifier.size(26.dp),
            )
            if (showBellDot) {
              Box(
                modifier =
                  Modifier.offset(x = 1.dp, y = (-1).dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ErrorRed)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier = Modifier.size(184.dp),
          contentAlignment = Alignment.Center,
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 15.dp.toPx()
            drawArc(
              color = ProgressTrack,
              startAngle = 0f,
              sweepAngle = 360f,
              useCenter = false,
              style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (animatedFraction > 0f) {
              drawArc(
                brush =
                  Brush.linearGradient(
                    colors = listOf(RingGradientTop, RingGradientMid, RingGradientBottom),
                    start = Offset(size.width * 0.5f, 0f),
                    end = Offset(size.width * 0.5f, size.height),
                  ),
                startAngle = -90f,
                sweepAngle = 360f * animatedFraction,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
              )
            }
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "${state.overallPercentage}%",
              fontSize = 42.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = (-1).sp,
              color = TextPrimary,
            )
            Text(
              text = stringResource(R.string.completed_label),
              fontSize = 14.sp,
              color = TextSecondary,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      val cardShape = RoundedCornerShape(20.dp)
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
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp, horizontal = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          HomeStatColumn(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            iconTint = PrimaryIndigo,
            value = state.totalBooks.toString(),
            label = stringResource(R.string.books_label),
            modifier = Modifier.weight(1f),
          )
          VerticalDivider(
            modifier = Modifier.height(44.dp),
            thickness = 1.dp,
            color = HairlineBorder,
          )
          HomeStatColumn(
            icon = Icons.Outlined.Layers,
            iconTint = PrimaryIndigo,
            value = state.totalChapters.toString(),
            label = stringResource(R.string.chapters_label),
            modifier = Modifier.weight(1f),
          )
          VerticalDivider(
            modifier = Modifier.height(44.dp),
            thickness = 1.dp,
            color = HairlineBorder,
          )
          HomeStatColumn(
            icon = Icons.Outlined.CheckCircle,
            iconTint = VibrantGreen,
            value = state.completedChapters.toString(),
            label = stringResource(R.string.completed_label),
            modifier = Modifier.weight(1f),
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      HomeStudyTimerCard(
        liveStudyingState = liveStudyingState,
        cardShape = cardShape,
        onClick = onStudyTimerClick,
      )

      Spacer(modifier = Modifier.height(12.dp))

      val goal = state.preferences.dailyGoal
      val planSubtitle =
        when {
          state.completedToday == 0 ->
            "Complete $goal ${if (goal == 1) "chapter" else "chapters"}"
          state.completedToday < goal ->
            "${state.completedToday} / $goal chapters done today"
          else -> "Goal reached (${state.completedToday} / $goal chapters)"
        }

      Surface(
        onClick = onTodaysPlanClick,
        shape = cardShape,
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier =
          Modifier.fillMaxWidth()
            .shadow(
              elevation = 8.dp,
              shape = cardShape,
              clip = false,
              ambientColor = AmbientShadow,
              spotColor = AmbientShadow,
            )
            .testTag("todays_plan_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LightIndigoTint),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.CalendarToday,
              contentDescription = null,
              tint = PrimaryIndigo,
              modifier = Modifier.size(22.dp),
            )
          }
          Spacer(modifier = Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.todays_plan),
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = planSubtitle,
              fontSize = 13.sp,
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

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = stringResource(R.string.continue_studying),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
      )

      Spacer(modifier = Modifier.height(10.dp))

      val continueBook = state.continueStudyingBook
      if (continueBook != null) {
        val nextChapter = continueBook.nextIncompleteChapter
        val subtitleText =
          when {
            nextChapter != null -> "Chapter ${nextChapter.number}: ${nextChapter.name}"
            continueBook.totalChapters > 0 ->
              "All ${continueBook.totalChapters} chapters completed"
            else -> "No chapters yet — tap to add"
          }

        Surface(
          onClick = { onContinueBookClick(continueBook.book.id) },
          shape = cardShape,
          color = CardSurface,
          border = BorderStroke(1.dp, HairlineBorder),
          modifier =
            Modifier.fillMaxWidth()
              .shadow(
                elevation = 8.dp,
                shape = cardShape,
                clip = false,
                ambientColor = AmbientShadow,
                spotColor = AmbientShadow,
              )
              .testTag("continue_studying_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.size(44.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(LightIndigoTint),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = PrimaryIndigo,
                modifier = Modifier.size(22.dp),
              )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = continueBook.book.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = subtitleText,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
              modifier = Modifier.size(36.dp).clip(CircleShape).background(LightIndigoTint),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Open book",
                tint = PrimaryIndigo,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      } else {
        Surface(
          shape = cardShape,
          color = CardSurface,
          border = BorderStroke(1.dp, HairlineBorder),
          modifier =
            Modifier.fillMaxWidth()
              .shadow(
                elevation = 8.dp,
                shape = cardShape,
                clip = false,
                ambientColor = AmbientShadow,
                spotColor = AmbientShadow,
              ),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Start your syllabus",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Add your first book to begin tracking.",
                fontSize = 13.sp,
                color = TextSecondary,
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
              onClick = onAddBookClick,
              shape = RoundedCornerShape(14.dp),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = LightIndigoTint,
                  contentColor = PrimaryIndigo,
                ),
              elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
              modifier = Modifier.testTag("home_empty_add_book"),
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add Book", fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HomeStudyTimerCard(
  liveStudyingState: StateFlow<LiveStudyingUiState>,
  cardShape: Shape,
  onClick: () -> Unit,
) {
  val liveStudying by liveStudyingState.collectAsStateWithLifecycle()
  val activeMeta = liveStudying.activeMeta
  val timerCardTitle =
    if (liveStudying.isRunningOrPaused && activeMeta != null) {
      "Study Timer • ${liveStudying.primaryClockDisplay}"
    } else {
      stringResource(R.string.study_timer_title)
    }
  val timerCardSubtitle =
    if (liveStudying.isRunningOrPaused && activeMeta != null) {
      val prefix = if (liveStudying.isPaused) "Paused • " else ""
      "$prefix${activeMeta.bookName} • ${activeMeta.chapterLabel}"
    } else {
      "Track your reading time"
    }

  Surface(
    onClick = onClick,
    shape = cardShape,
    color = Color.Transparent,
    border = BorderStroke(1.dp, SoftLavenderBorder),
    modifier =
      Modifier.fillMaxWidth()
        .shadow(
          elevation = 8.dp,
          shape = cardShape,
          clip = false,
          ambientColor = AmbientShadow,
          spotColor = AmbientShadow,
        )
        .testTag("home_study_timer_card"),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(HomeTimerCardGradient)
          .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Outlined.Timer,
          contentDescription = null,
          tint = PrimaryIndigo,
          modifier = Modifier.size(24.dp),
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = timerCardTitle,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = timerCardSubtitle,
          fontSize = 13.sp,
          color = TextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .background(HomeTimerPlayGradient),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription =
            if (liveStudying.isRunningOrPaused) "Open running timer" else "Open study timer",
          tint = Color.White,
          modifier = Modifier.size(22.dp),
        )
      }
    }
  }
}

@Composable
private fun HomeStatColumn(
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
      modifier = Modifier.size(22.dp),
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = value,
      fontSize = 20.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimary,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = label,
      fontSize = 12.sp,
      color = TextSecondary,
    )
  }
}
