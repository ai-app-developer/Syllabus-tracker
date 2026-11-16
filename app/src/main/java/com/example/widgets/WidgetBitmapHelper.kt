package com.example.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.example.SyllabusApplication
import com.example.data.repository.SyllabusSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object WidgetBitmapHelper {
  suspend fun loadSnapshot(context: Context): SyllabusSnapshot =
    withContext(Dispatchers.IO) {
      val app = context.applicationContext as SyllabusApplication
      app.syllabusRepository.snapshot.first()
    }

  fun drawProgressRingBitmap(
    sizePx: Int = 180,
    strokePx: Float = 16f,
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
