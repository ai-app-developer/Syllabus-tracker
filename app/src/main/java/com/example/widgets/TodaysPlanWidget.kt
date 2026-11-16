package com.example.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.R

class TodaysPlanWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = TodaysPlanWidget()

  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    WidgetUpdater.schedulePeriodicRefresh(context)
  }
}

class TodaysPlanWidget : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val snapshot = WidgetBitmapHelper.loadWidgetData(context)
    val dailyGoal = snapshot.dailyGoal.coerceAtLeast(1)
    val completedToday = snapshot.completedToday
    val fraction = (completedToday.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
    val goalReached = completedToday >= dailyGoal

    val barBitmap =
      WidgetBitmapHelper.drawHorizontalBarBitmap(
        widthPx = 220,
        heightPx = 12,
        fraction = fraction,
        trackColor = 0xFFEEF0FF.toInt(),
        fillColor = if (goalReached) 0xFF22A06B.toInt() else 0xFF4F46E5.toInt(),
      )

    val planIntent =
      WidgetIntents.createActivityIntent(context, WidgetIntents.ACTION_OPEN_TODAYS_PLAN)

    provideContent {
      Box(
        modifier =
          GlanceModifier.fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_light))
            .clickable(actionStartActivity(planIntent))
            .padding(horizontal = 16.dp, vertical = 14.dp),
      ) {
        Column(
          modifier = GlanceModifier.fillMaxSize(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Image(
              provider = ImageProvider(R.drawable.ic_widget_calendar),
              contentDescription = null,
              colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF4F46E5))),
              modifier = GlanceModifier.size(18.dp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
              text = "Today's Plan",
              style =
                TextStyle(
                  color = ColorProvider(Color(0xFF1A1A2E)),
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                ),
              maxLines = 1,
              modifier = GlanceModifier.defaultWeight(),
            )
            Image(
              provider = ImageProvider(R.drawable.ic_widget_chevron),
              contentDescription = "Open plan",
              colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF6B7280))),
              modifier = GlanceModifier.size(18.dp),
            )
          }

          Spacer(modifier = GlanceModifier.height(14.dp))

          if (snapshot.totalBooks == 0) {
            Text(
              text = "Add a book to start",
              style =
                TextStyle(
                  color = ColorProvider(Color(0xFF6B7280)),
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium,
                ),
            )
          } else {
            Row(
              modifier = GlanceModifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Image(
                provider =
                  ImageProvider(
                    if (goalReached) R.drawable.ic_widget_check_circle
                    else R.drawable.ic_widget_circle_outline
                  ),
                contentDescription = null,
                colorFilter =
                  ColorFilter.tint(
                    ColorProvider(if (goalReached) Color(0xFF22A06B) else Color(0xFFC7CCFB))
                  ),
                modifier = GlanceModifier.size(18.dp),
              )
              Spacer(modifier = GlanceModifier.width(8.dp))
              Text(
                text = "Complete $dailyGoal ${if (dailyGoal == 1) "chapter" else "chapters"}",
                style =
                  TextStyle(
                    color = ColorProvider(Color(0xFF1A1A2E)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                  ),
                maxLines = 1,
              )
            }

            Spacer(modifier = GlanceModifier.height(14.dp))

            Row(
              modifier = GlanceModifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Image(
                provider = ImageProvider(barBitmap),
                contentDescription = "$completedToday of $dailyGoal chapters",
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.defaultWeight().height(7.dp),
              )
              Spacer(modifier = GlanceModifier.width(10.dp))
              Text(
                text = "$completedToday/$dailyGoal",
                style =
                  TextStyle(
                    color = ColorProvider(Color(0xFF1A1A2E)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                  ),
              )
            }
          }
        }
      }
    }
  }

  override fun onCompositionError(
    context: Context,
    glanceId: GlanceId,
    appWidgetId: Int,
    throwable: Throwable,
  ) {
    val fallback =
      RemoteViews(context.packageName, android.R.layout.simple_list_item_1).apply {
        setTextViewText(android.R.id.text1, "Today's Plan")
      }
    AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
  }
}
