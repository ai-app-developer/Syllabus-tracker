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

class ContinueStudyingWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = ContinueStudyingWidget()

  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    WidgetUpdater.schedulePeriodicRefresh(context)
  }
}

class ContinueStudyingWidget : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val snapshot = WidgetBitmapHelper.loadWidgetData(context)
    val activeBook = snapshot.activeBook
    val fraction = activeBook?.progressFraction ?: 0f
    val bookPercent = activeBook?.percentage ?: 0

    val barBitmap =
      WidgetBitmapHelper.drawHorizontalBarBitmap(
        widthPx = 220,
        heightPx = 12,
        fraction = fraction,
        trackColor = 0xFFEEF0FF.toInt(),
        fillColor = 0xFF4F46E5.toInt(),
      )

    val clickIntent =
      if (activeBook != null) {
        WidgetIntents.createActivityIntent(
          context,
          WidgetIntents.ACTION_OPEN_BOOK,
          activeBook.id,
        )
      } else {
        WidgetIntents.createActivityIntent(context, WidgetIntents.ACTION_OPEN_HOME)
      }

    provideContent {
      Box(
        modifier =
          GlanceModifier.fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_light))
            .clickable(actionStartActivity(clickIntent))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (activeBook == null) {
          Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                GlanceModifier.size(44.dp)
                  .background(ImageProvider(R.drawable.widget_icon_tile_bg)),
              contentAlignment = Alignment.Center,
            ) {
              Image(
                provider = ImageProvider(R.drawable.ic_widget_book),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF4F46E5))),
                modifier = GlanceModifier.size(22.dp),
              )
            }
            Spacer(modifier = GlanceModifier.width(12.dp))
            Text(
              text = "Add a book to start",
              style =
                TextStyle(
                  color = ColorProvider(Color(0xFF6B7280)),
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Medium,
                ),
              modifier = GlanceModifier.defaultWeight(),
            )
            Box(
              modifier =
                GlanceModifier.size(36.dp)
                  .background(ImageProvider(R.drawable.widget_circle_btn_bg)),
              contentAlignment = Alignment.Center,
            ) {
              Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = "Add book",
                colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF4F46E5))),
                modifier = GlanceModifier.size(18.dp),
              )
            }
          }
        } else {
          val chapterSubtitle =
            when {
              activeBook.nextChapterNumber != null && activeBook.nextChapterName != null ->
                "Chapter ${activeBook.nextChapterNumber}: ${activeBook.nextChapterName}"
              activeBook.totalChapters > 0 -> "All ${activeBook.totalChapters} chapters completed"
              else -> "No chapters yet"
            }

          Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                GlanceModifier.size(46.dp)
                  .background(ImageProvider(R.drawable.widget_icon_tile_bg)),
              contentAlignment = Alignment.Center,
            ) {
              Image(
                provider = ImageProvider(R.drawable.ic_widget_book),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF4F46E5))),
                modifier = GlanceModifier.size(24.dp),
              )
            }

            Spacer(modifier = GlanceModifier.width(12.dp))

            Column(modifier = GlanceModifier.defaultWeight()) {
              Text(
                text = activeBook.name,
                style =
                  TextStyle(
                    color = ColorProvider(Color(0xFF1A1A2E)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                  ),
                maxLines = 1,
              )
              Spacer(modifier = GlanceModifier.height(2.dp))
              Text(
                text = chapterSubtitle,
                style =
                  TextStyle(
                    color = ColorProvider(Color(0xFF6B7280)),
                    fontSize = 12.sp,
                  ),
                maxLines = 1,
              )
              Spacer(modifier = GlanceModifier.height(6.dp))
              Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Image(
                  provider = ImageProvider(barBitmap),
                  contentDescription = "$bookPercent% complete",
                  contentScale = ContentScale.FillBounds,
                  modifier = GlanceModifier.defaultWeight().height(6.dp),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                  text = "$bookPercent%",
                  style =
                    TextStyle(
                      color = ColorProvider(Color(0xFF1A1A2E)),
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                    ),
                )
              }
            }

            Spacer(modifier = GlanceModifier.width(12.dp))

            Box(
              modifier =
                GlanceModifier.size(38.dp)
                  .background(ImageProvider(R.drawable.widget_circle_btn_bg)),
              contentAlignment = Alignment.Center,
            ) {
              Image(
                provider = ImageProvider(R.drawable.ic_widget_play),
                contentDescription = "Continue studying",
                colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF4F46E5))),
                modifier = GlanceModifier.size(18.dp),
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
        setTextViewText(android.R.id.text1, "Continue Studying")
      }
    AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
  }
}
