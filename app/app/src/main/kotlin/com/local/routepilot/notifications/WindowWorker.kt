package com.local.routepilot.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.local.routepilot.R
import kotlinx.coroutines.flow.first
import com.local.routepilot.data.RouteRepository
import java.util.Calendar

class WindowWorker(
  context: Context,
  params: WorkerParameters,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val repository = RouteRepository(applicationContext)
    val pending = repository.allPending.firstOrNullSafely()
    val now = Calendar.getInstance()
    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    val upcoming = pending.filter { stop ->
      val parts = stop.windowStart.split(":")
      if (parts.size != 2) return@filter false
      val target = parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: return@filter false
      val diff = target - nowMinutes
      diff in 0..30
    }

    if (upcoming.isNotEmpty()) {
      val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channelId = "delivery_windows"
      val channel = NotificationChannel(channelId, "Delivery windows", NotificationManager.IMPORTANCE_DEFAULT)
      manager.createNotificationChannel(channel)
      val text = upcoming.joinToString(", ") { it.label.ifBlank { it.address } }
      val notification = NotificationCompat.Builder(applicationContext, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Upcoming delivery window")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .build()
      manager.notify(1001, notification)
    }
    return Result.success()
  }
}

private suspend fun kotlinx.coroutines.flow.Flow<List<com.local.routepilot.domain.Stop>>.firstOrNullSafely(): List<com.local.routepilot.domain.Stop> =
  try {
    first()
  } catch (_: Exception) {
    emptyList()
  }
