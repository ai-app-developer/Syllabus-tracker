package com.example.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import com.example.SyllabusApplication
import com.example.data.repository.WidgetSummaryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetBitmapHelper {
  private val ringBitmapCache = LruCache<String, Bitmap>(128)

  suspend fun loadWidgetData(context: Context): WidgetSummaryData =
    withContext(Dispatchers.IO) {
      val app = context.applicationContext as SyllabusApplication
      app.syllabusRepository.loadWidgetSummary()
    }

  fun getOrDrawProgressRingBitmap(
    percentage: Int,
    sizePx: Int = 300,
    strokePx: Float = 28f,
    trackColor: Int,
    arcColor: Int,
  ): Bitmap {
    val clampedPct = percentage.coerceIn(0, 100)
    val key = "$clampedPct:$sizePx:${strokePx.toInt()}:$trackColor:$arcColor"
    ringBitmapCache.get(key)?.let { cached ->
      if (!cached.isRecycled) return cached
    }

    val drawn =
      drawProgressRingBitmap(
        sizePx = sizePx,
        strokePx = strokePx,
        fraction = clampedPct / 100f,
        trackColor = trackColor,
        arcColor = arcColor,
      )
    ringBitmapCache.put(key, drawn)
    return drawn
  }

  fun drawProgressRingBitmap(
    sizePx: Int = 300,
    strokePx: Float = 28f,
    fraction: Float,
    trackColor: Int,
    arcColor: Int,
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val inset = strokePx / 2f + 2f
    val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)

    val trackPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        color = trackColor
      }
    canvas.drawArc(rect, 0f, 360f, false, trackPaint)

    val clamped = fraction.coerceIn(0f, 1f)
    if (clamped > 0f) {
      val arcPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.STROKE
          strokeWidth = strokePx
          strokeCap = Paint.Cap.ROUND
          color = arcColor
        }
      canvas.drawArc(rect, -90f, 360f * clamped, false, arcPaint)
    }
    return bitmap
  }

  fun drawHorizontalBarBitmap(
    widthPx: Int = 240,
    heightPx: Int = 14,
    fraction: Float,
    trackColor: Int,
    fillColor: Int,
  ): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = heightPx / 2f

    val trackPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = trackColor
      }
    canvas.drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), radius, radius, trackPaint)

    val clamped = fraction.coerceIn(0f, 1f)
    if (clamped > 0f) {
      val fillWidth = (widthPx * clamped).coerceAtLeast(heightPx.toFloat())
      val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
          style = Paint.Style.FILL
          color = fillColor
        }
      canvas.drawRoundRect(RectF(0f, 0f, fillWidth, heightPx.toFloat()), radius, radius, fillPaint)
    }
    return bitmap
  }
}
