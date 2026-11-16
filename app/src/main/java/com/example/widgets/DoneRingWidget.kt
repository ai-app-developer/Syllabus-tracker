package com.example.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.R

class DoneRingWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = DoneRingWidget()

  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    WidgetUpdater.schedulePeriodicRefresh(context)
  }
}

class DoneRingWidget : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val snapshot = WidgetBitmapHelper.loadWidgetData(context)

    val ringBitmap =
      WidgetBitmapHelper.getOrDrawProgressRingBitmap(
        percentage = snapshot.overallPercentage,
        sizePx = 300,
        strokePx = 28f,
        trackColor = 0xFFD8DCFE.toInt(),
        arcColor = 0xFF4F46E5.toInt(),
      )

    val homeIntent = WidgetIntents.createActivityIntent(context, WidgetIntents.ACTION_OPEN_PROGRESS)

    provideContent {
      Box(
        modifier =
          GlanceModifier.fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_indigo_tint))
            .clickable(actionStartActivity(homeIntent))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (snapshot.totalBooks == 0) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "0%",
              style =
                TextStyle(
                  color = ColorProvider(Color(0xFF1A1A2E)),
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Bold,
                ),
            )
            Text(
              text = "Add a book to start",
              style =
                TextStyle(
                  color = ColorProvider(Color(0xFF6B7280)),
                  fontSize = 11.sp,
                  textAlign = TextAlign.Center,
                ),
            )
          }
        } else {
          Box(
            modifier = GlanceModifier.size(96.dp),
            contentAlignment = Alignment.Center,
          ) {
            Image(
              provider = ImageProvider(ringBitmap),
              contentDescription = "${snapshot.overallPercentage}% Done",
              modifier = GlanceModifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "${snapshot.overallPercentage}%",
                style =
                  TextStyle(
                    color = ColorProvider(Color(0xFF1A1A2E)),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                  ),
              )
              Text(
                text = "Done",
                style =
                  TextStyle(
                    color = ColorProvider(Color(0xFF6B7280)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
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
        setTextViewText(android.R.id.text1, "Done Ring")
      }
    AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
  }
}
