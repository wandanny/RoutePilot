package com.local.routepilot.data

import android.graphics.Bitmap
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object ImagePreparer {
  private const val MAX_DIMENSION = 1280
  private const val JPEG_QUALITY = 82

  fun prepare(bitmap: Bitmap, bounds: Rect?): ByteArray {
    val crop = bounds?.let { expandAndClamp(it, bitmap.width, bitmap.height) }
      ?: Rect(0, 0, bitmap.width, bitmap.height)
    val cropped = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
    val scale = min(1f, MAX_DIMENSION.toFloat() / max(cropped.width, cropped.height))
    val scaled = if (scale < 1f) {
      Bitmap.createScaledBitmap(
        cropped,
        max(1, (cropped.width * scale).toInt()),
        max(1, (cropped.height * scale).toInt()),
        true,
      )
    } else {
      cropped
    }
    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
    if (scaled !== cropped) scaled.recycle()
    if (cropped !== bitmap) cropped.recycle()
    return stream.toByteArray()
  }

  private fun expandAndClamp(source: Rect, width: Int, height: Int): Rect {
    val padX = (source.width() * 0.1f).toInt().coerceAtLeast(16)
    val padY = (source.height() * 0.1f).toInt().coerceAtLeast(16)
    return Rect(
      max(0, source.left - padX),
      max(0, source.top - padY),
      min(width, source.right + padX),
      min(height, source.bottom + padY),
    )
  }
}
