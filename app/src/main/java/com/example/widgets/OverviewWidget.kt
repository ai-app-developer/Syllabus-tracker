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

class OverviewWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = OverviewWidget()

  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    WidgetUpdater.schedulePeriodicRefresh(context)
  }
}

class OverviewWidget : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val snapshot = WidgetBitmapHelper.loadWidgetData(context)

    val ringBitmap =
      WidgetBitmapHelper.getOrDrawProgressRingBitmap(
        percentage = snapshot.overallPercentage,
        sizePx = 300,
        strokePx = 28f,
        trackColor = 0xFFEEF0FF.toInt(),
        arcColor = 0xFF4F46E5.toInt(),
      )

    val homeIntent = WidgetIntents.createActivityIntent(context, WidgetIntents.ACTION_OPEN_HOME)
    val addChapterIntent =
      WidgetIntents.createActivityIntent(context, WidgetIntents.ACTION_OPEN_ADD_CHAPTER)

    provideContent {
      Box(
        modifier =
          GlanceModifier.fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_light))
            .clickable(actionStartActivity(homeIntent))
            .padding(horizontal = 16.dp, vertical = 14.dp),
      ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
          Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Image(
              provider = ImageProvider(R.drawable.ic_widget_book),
              contentDescription = null,
              colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF4F46E5))),
              modifier = GlanceModifier.size(18.dp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
              text = "Syllabus Tracker",
              style =
                TextStyle(
                  color = ColorProvider(Color(0xFF1A1A2E)),
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                ),
              maxLines = 1,
              modifier = GlanceModifier.defaultWeight(),
            )
            Box(
              modifier =
                GlanceModifier.size(28.dp)
                  .background(ImageProvider(R.drawable.widget_circle_btn_bg))
                  .clickable(actionStartActivity(addChapterIntent)),
              contentAlignment = Alignment.Center,
            ) {
              Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = "Add chapter",
                colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF4F46E5))),
                modifier = GlanceModifier.size(16.dp),
              )
            }
          }

          Spacer(modifier = GlanceModifier.height(10.dp))

          if (snapshot.totalBooks == 0) {
            Box(
              modifier = GlanceModifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = "Add a book to start",
                style =
                  TextStyle(
                    color = ColorProvider(Color(0xFF6B7280)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                  ),
              )
            }
          } else {
            Row(
              modifier = GlanceModifier.fillMaxSize(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = GlanceModifier.size(92.dp),
                contentAlignment = Alignment.Center,
              ) {
                Image(
                  provider = ImageProvider(ringBitmap),
                  contentDescription = "${snapshot.overallPercentage}% Completed",
                  modifier = GlanceModifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(
                    text = "${snapshot.overallPercentage}%",
                    style =
                      TextStyle(
                        color = ColorProvider(Color(0xFF1A1A2E)),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                      ),
                  )
                  Text(
                    text = "Completed",
                    style =
                      TextStyle(
                        color = ColorProvider(Color(0xFF6B7280)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                      ),
                  )
                }
              }

              Spacer(modifier = GlanceModifier.width(12.dp))
              Box(
                modifier =
                  GlanceModifier.width(1.dp)
                    .height(74.dp)
                    .background(ColorProvider(Color(0xFFE8EAF0))),
              ) {}
              Spacer(modifier = GlanceModifier.width(12.dp))

              Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                OverviewStatRow(
                  iconRes = R.drawable.ic_widget_book,
                  iconTint = Color(0xFF4F46E5),
                  count = snapshot.totalBooks,
                  label = "Books",
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                OverviewStatRow(
                  iconRes = R.drawable.ic_widget_layers,
                  iconTint = Color(0xFF4F46E5),
                  count = snapshot.totalChapters,
                  label = "Chapters",
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                OverviewStatRow(
                  iconRes = R.drawable.ic_widget_check_circle,
                  iconTint = Color(0xFF22A06B),
                  count = snapshot.completedChapters,
                  label = "Completed",
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
        setTextViewText(android.R.id.text1, "Syllabus Tracker Overview")
      }
    AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
  }
}

@androidx.compose.runtime.Composable
private fun OverviewStatRow(
  iconRes: Int,
  iconTint: Color,
  count: Int,
  label: String,
) {
  Row(
    modifier = GlanceModifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Image(
      provider = ImageProvider(iconRes),
      contentDescription = label,
      colorFilter = ColorFilter.tint(ColorProvider(iconTint)),
      modifier = GlanceModifier.size(16.dp),
    )
    Spacer(modifier = GlanceModifier.width(8.dp))
    Text(
      text = count.toString(),
      style =
        TextStyle(
          color = ColorProvider(Color(0xFF1A1A2E)),
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
        ),
    )
    Spacer(modifier = GlanceModifier.width(5.dp))
    Text(
      text = label,
      style =
        TextStyle(
          color = ColorProvider(Color(0xFF6B7280)),
          fontSize = 12.sp,
          fontWeight = FontWeight.Normal,
        ),
      maxLines = 1,
    )
  }
}
