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
import androidx.compose.ui.graphics.Color
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
import com.example.R
import com.example.data.repository.SyllabusSnapshot
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LightIndigoTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.ProgressTrack
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HomeScreen(
  snapshot: SyllabusSnapshot,
  innerPadding: PaddingValues,
  onBellClick: () -> Unit,
  onTodaysPlanClick: () -> Unit,
  onContinueBookClick: (Long) -> Unit,
  onAddBookClick: () -> Unit,
) {
  val targetFraction =
    if (snapshot.totalChapters == 0) 0f
    else snapshot.completedChapters.toFloat() / snapshot.totalChapters.toFloat()
  val animatedFraction by
    animateFloatAsState(
      targetValue = targetFraction.coerceIn(0f, 1f),
      animationSpec = tween(durationMillis = 180),
      label = "home_progress_ring",
    )

  val remainingToday = (snapshot.preferences.dailyGoal - snapshot.completedToday).coerceAtLeast(0)
  val showBellDot = snapshot.preferences.reminderEnabled && remainingToday > 0

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
          .padding(innerPadding)
          .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Syllabus\nTracker",
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = TextPrimary,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
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

      Spacer(modifier = Modifier.height(22.dp))

      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier = Modifier.size(180.dp),
          contentAlignment = Alignment.Center,
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            drawArc(
              color = ProgressTrack,
              startAngle = 0f,
              sweepAngle = 360f,
              useCenter = false,
              style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (animatedFraction > 0f) {
              drawArc(
                color = PrimaryIndigo,
                startAngle = -90f,
                sweepAngle = 360f * animatedFraction,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
              )
            }
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "${snapshot.overallPercentage}%",
              fontSize = 36.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
            )
            Text(
              text = stringResource(R.string.completed_label),
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          HomeStatColumn(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            iconTint = PrimaryIndigo,
            value = snapshot.totalBooks.toString(),
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
            value = snapshot.totalChapters.toString(),
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
            iconTint = SuccessGreen,
            value = snapshot.completedChapters.toString(),
            label = stringResource(R.string.completed_label),
            modifier = Modifier.weight(1f),
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      val goal = snapshot.preferences.dailyGoal
      val planSubtitle =
        when {
          snapshot.completedToday == 0 ->
            "Complete $goal ${if (goal == 1) "chapter" else "chapters"}"
          snapshot.completedToday < goal ->
            "${snapshot.completedToday} / $goal chapters done today"
          else -> "Goal reached (${snapshot.completedToday} / $goal chapters)"
        }

      Surface(
        onClick = onTodaysPlanClick,
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().testTag("todays_plan_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(44.dp)
                .clip(RoundedCornerShape(12.dp))
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
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = planSubtitle,
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
        text = stringResource(R.string.continue_studying),
        style = MaterialTheme.typography.titleLarge,
        color = TextPrimary,
      )

      Spacer(modifier = Modifier.height(10.dp))

      val continueBook = snapshot.continueStudyingBook
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
          shape = RoundedCornerShape(16.dp),
          color = CardSurface,
          border = BorderStroke(1.dp, HairlineBorder),
          shadowElevation = 1.dp,
          modifier = Modifier.fillMaxWidth().testTag("continue_studying_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.size(44.dp)
                  .clip(RoundedCornerShape(12.dp))
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
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
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
          shape = RoundedCornerShape(16.dp),
          color = CardSurface,
          border = BorderStroke(1.dp, HairlineBorder),
          shadowElevation = 1.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Start your syllabus",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Add your first book to begin tracking.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
              onClick = onAddBookClick,
              shape = RoundedCornerShape(12.dp),
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

      Spacer(modifier = Modifier.height(16.dp))
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
      fontSize = 19.sp,
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
