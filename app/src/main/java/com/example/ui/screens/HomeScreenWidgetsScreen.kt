package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.components.ManualWidgetStepsBottomSheet
import com.example.ui.theme.AppBackground
import com.example.ui.theme.CardSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LightGreenBorder
import com.example.ui.theme.LightGreenTint
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.SoftLavenderCardStart
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.widgets.HomeWidgetType

@Composable
fun HomeScreenWidgetsScreen(
  widgetCounts: Map<HomeWidgetType, Int>,
  isPinBusy: Boolean,
  showFallbackSheet: Boolean,
  innerPadding: PaddingValues,
  onBackClick: () -> Unit,
  onRefreshCounts: () -> Unit,
  onAddWidgetClick: (HomeWidgetType) -> Unit,
  onDismissFallbackSheet: () -> Unit,
) {
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        onRefreshCounts()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Box(
    modifier = Modifier.fillMaxSize().background(AppBackground).padding(innerPadding),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 20.dp),
    ) {
      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier.size(48.dp).testTag("home_widgets_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextPrimary,
          )
        }
        Text(
          text = stringResource(R.string.home_widgets_screen_title),
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary,
          modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home_widgets_list"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
      ) {
        items(items = HomeWidgetType.entries, key = { it.key }) { widgetType ->
          val count = widgetCounts[widgetType] ?: 0
          HomeWidgetCatalogCard(
            widgetType = widgetType,
            addedCount = count,
            isPinBusy = isPinBusy,
            onAddClick = { onAddWidgetClick(widgetType) },
          )
        }
      }
    }
  }

  if (showFallbackSheet) {
    ManualWidgetStepsBottomSheet(onDismiss = onDismissFallbackSheet)
  }
}

@Composable
private fun HomeWidgetCatalogCard(
  widgetType: HomeWidgetType,
  addedCount: Int,
  isPinBusy: Boolean,
  onAddClick: () -> Unit,
) {
  val name = stringResource(widgetType.nameRes)
  val desc = stringResource(widgetType.descRes)

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = CardSurface,
    border = BorderStroke(1.dp, HairlineBorder),
    shadowElevation = 1.dp,
    modifier = Modifier.fillMaxWidth().testTag("widget_card_${widgetType.key}"),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SoftLavenderCardStart)
            .padding(vertical = 14.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          painter = painterResource(widgetType.previewRes),
          contentDescription = name,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxWidth().height(112.dp),
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (addedCount > 0) {
              Surface(
                shape = CircleShape,
                color = LightGreenTint,
                border = BorderStroke(1.dp, LightGreenBorder),
                modifier = Modifier.testTag("widget_added_badge_${widgetType.key}"),
              ) {
                Text(
                  text = stringResource(R.string.widget_added_count_badge, addedCount),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = SuccessGreen,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Button(
          onClick = onAddClick,
          enabled = !isPinBusy,
          shape = CircleShape,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = PrimaryIndigo,
              contentColor = Color.White,
            ),
          contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
          modifier =
            Modifier.height(48.dp).testTag("widget_add_button_${widgetType.key}"),
        ) {
          Text(
            text = stringResource(R.string.widget_add_button),
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}
