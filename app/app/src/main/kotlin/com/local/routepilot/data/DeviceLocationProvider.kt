package com.local.routepilot.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

class DeviceLocationProvider(private val context: Context) {
  fun current(): Pair<Double, Double>? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
      PackageManager.PERMISSION_GRANTED
    ) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
      .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
      .maxByOrNull { it.time }
      ?.let { it.latitude to it.longitude }
  }
}
