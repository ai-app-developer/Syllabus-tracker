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
import androidx.glance.layout.fillMaxHeight
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

class DarkProgressWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = DarkProgressWidget()

  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    WidgetUpdater.schedulePeriodicRefresh(context)
  }
}

class DarkProgressWidget : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val snapshot = WidgetBitmapHelper.loadWidgetData(context)
    val fraction =
      if (snapshot.totalChapters == 0) 0f
      else snapshot.completedChapters.toFloat() / snapshot.totalChapters.toFloat()

    val barBitmap =
      WidgetBitmapHelper.drawHorizontalBarBitmap(
        widthPx = 240,
        heightPx = 14,
        fraction = fraction,
        trackColor = 0xFF31334D.toInt(),
        fillColor = 0xFF4F46E5.toInt(),
      )

    val homeIntent = WidgetIntents.createActivityIntent(context, WidgetIntents.ACTION_OPEN_PROGRESS)
    val continueBookId = snapshot.activeBook?.id
    val continueIntent =
      if (continueBookId != null) {
        WidgetIntents.createActivityIntent(context, WidgetIntents.ACTION_OPEN_BOOK, continueBookId)
      } else {
        WidgetIntents.createActivityIntent(context, WidgetIntents.ACTION_OPEN_HOME)
      }

    provideContent {
      Box(
        modifier =
          GlanceModifier.fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_dark))
            .clickable(actionStartActivity(homeIntent))
            .padding(horizontal = 16.dp, vertical = 14.dp),
      ) {
        Row(
          modifier = GlanceModifier.fillMaxSize(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Image(
                provider = ImageProvider(R.drawable.ic_widget_book),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                modifier = GlanceModifier.size(16.dp),
              )
              Spacer(modifier = GlanceModifier.width(7.dp))
              Text(
                text = "Syllabus Tracker",
                style =
                  TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                  ),
                maxLines = 1,
              )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            if (snapshot.totalBooks == 0) {
              Text(
                text = "0%",
                style =
                  TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                  ),
              )
              Text(
                text = "Add a book to start",
                style =
                  TextStyle(
                    color = ColorProvider(Color(0xFF9CA3AF)),
                    fontSize = 11.sp,
                  ),
              )
            } else {
              Text(
                text = "${snapshot.overallPercentage}%",
                style =
                  TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                  ),
              )
              Text(
                text = "Syllabus Completed",
                style =
                  TextStyle(
                    color = ColorProvider(Color(0xFFB4B9CE)),
                    fontSize = 11.sp,
                  ),
                maxLines = 1,
              )
              Spacer(modifier = GlanceModifier.height(10.dp))
              Image(
                provider = ImageProvider(barBitmap),
                contentDescription = "${snapshot.overallPercentage}% progress bar",
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp),
              )
            }
          }

          Spacer(modifier = GlanceModifier.width(14.dp))

          Box(
            modifier =
              GlanceModifier.width(102.dp)
                .fillMaxHeight()
                .background(ImageProvider(R.drawable.widget_dark_tile_bg))
                .clickable(actionStartActivity(continueIntent))
                .padding(12.dp),
          ) {
            Column(
              modifier = GlanceModifier.fillMaxSize(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Image(
                provider = ImageProvider(R.drawable.ic_widget_book),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                modifier = GlanceModifier.size(22.dp),
              )
              Spacer(modifier = GlanceModifier.height(12.dp))
              Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
              ) {
                Text(
                  text = "Continue\nStudying",
                  style =
                    TextStyle(
                      color = ColorProvider(Color.White),
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Medium,
                    ),
                  modifier = GlanceModifier.defaultWeight(),
                )
                Image(
                  provider = ImageProvider(R.drawable.ic_widget_chevron),
                  contentDescription = "Open book",
                  colorFilter = ColorFilter.tint(ColorProvider(Color(0xFFCBD5E1))),
                  modifier = GlanceModifier.size(16.dp),
                )
              }
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
        setTextViewText(android.R.id.text1, "Dark Progress")
      }
    AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
  }
}
