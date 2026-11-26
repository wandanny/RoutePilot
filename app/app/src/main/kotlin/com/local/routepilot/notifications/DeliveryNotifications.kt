package com.local.routepilot.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.local.routepilot.R
import java.util.Calendar

object DeliveryNotifications {
  const val ACTION_DELIVERY_WINDOW = "com.local.routepilot.DELIVERY_WINDOW"
  internal const val EXTRA_LABEL = "label"
  internal const val EXTRA_WINDOW = "window_start"
  internal const val CHANNEL_ID = "delivery_windows"
  private const val PREFS_NAME = "routepilot.delivery_alarms"
  private const val KEY_FIRE_AT = "fire_at_"
  private const val KEY_FIRED_AT = "fired_at_"
  private const val FALLBACK_WINDOW_MS = 60 * 60 * 1000L

  /** Schedule a notification exactly 30 minutes before the window opens. */
  fun schedule(context: Context, stopId: Long, label: String, windowStart: String) {
    val (hour, minute) = parseTime(windowStart) ?: return
    val now = Calendar.getInstance()
    val fire = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      add(Calendar.MINUTE, -30)
    }
    if (!fire.after(now)) fire.add(Calendar.DAY_OF_YEAR, 1)

    ensureChannel(context)
    val intent = windowIntent(context, stopId)
      .putExtra(EXTRA_LABEL, label)
      .putExtra(EXTRA_WINDOW, windowStart)
    val pending = PendingIntent.getBroadcast(
      context,
      stopId.toInt(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire.timeInMillis, pending)
    prefs(context).edit()
      .putLong(KEY_FIRE_AT + stopId, fire.timeInMillis)
      .remove(KEY_FIRED_AT + stopId)
      .apply()
  }

  fun cancel(context: Context, stopId: Long) {
    val pending = pendingIntent(context, stopId, noCreate = true)
    if (pending != null) {
      (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
      pending.cancel()
    }
    prefs(context).edit()
      .remove(KEY_FIRE_AT + stopId)
      .remove(KEY_FIRED_AT + stopId)
      .apply()
  }

  /** True only while a scheduled alarm is still pending in the future. */
  fun isScheduled(context: Context, stopId: Long): Boolean {
    val fireAt = prefs(context).getLong(KEY_FIRE_AT + stopId, 0L)
    return fireAt > System.currentTimeMillis() && pendingIntent(context, stopId, noCreate = true) != null
  }

  /** True for an hour after a window alert was delivered, so the worker cannot repeat it. */
  fun wasDeliveredRecently(context: Context, stopId: Long): Boolean =
    prefs(context).getLong(KEY_FIRED_AT + stopId, 0L) > System.currentTimeMillis() - FALLBACK_WINDOW_MS

  internal fun markFired(context: Context, stopId: Long) {
    prefs(context).edit()
      .putLong(KEY_FIRED_AT + stopId, System.currentTimeMillis())
      .remove(KEY_FIRE_AT + stopId)
      .apply()
  }

  internal fun ensureChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Delivery windows",
      NotificationManager.IMPORTANCE_DEFAULT,
    )
    manager.createNotificationChannel(channel)
  }

  private fun windowIntent(context: Context, stopId: Long): Intent =
    Intent(ACTION_DELIVERY_WINDOW)
      .setPackage(context.packageName)
      .apply { data = Uri.parse("routepilot://window/$stopId") }

  private fun pendingIntent(context: Context, stopId: Long, noCreate: Boolean): PendingIntent? {
    val flags = PendingIntent.FLAG_IMMUTABLE or if (noCreate) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT
    return PendingIntent.getBroadcast(context, stopId.toInt(), windowIntent(context, stopId), flags)
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private fun parseTime(value: String): Pair<Int, Int>? {
    val parts = value.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour to minute else null
  }
}

class DeliveryAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val stopId = intent.dataString?.substringAfterLast('/')?.toLongOrNull() ?: return
    val label = intent.getStringExtra(DeliveryNotifications.EXTRA_LABEL).orEmpty()
    val windowStart = intent.getStringExtra(DeliveryNotifications.EXTRA_WINDOW).orEmpty()
    DeliveryNotifications.ensureChannel(context)
    val notification = NotificationCompat.Builder(context.applicationContext, DeliveryNotifications.CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle("Delivery window")
      .setContentText("$label opens at $windowStart.")
      .setStyle(NotificationCompat.BigTextStyle().bigText("$label opens at $windowStart."))
      .build()
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(stopId.toInt(), notification)
    DeliveryNotifications.markFired(context.applicationContext, stopId)
  }
}
