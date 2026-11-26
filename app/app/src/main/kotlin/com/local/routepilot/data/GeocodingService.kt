package com.local.routepilot.data

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GeocodingService(private val repository: RouteRepository, private val context: Context) {
  val settings: AiSettings = AiSettings(context)
  val tomtom: TomTomClient = TomTomClient(settings)
  private val local = LocalAddressIndex(context)
  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()
  private var lastCall = 0L

  fun restrictArea(): Boolean = settings.restrictArea()

  suspend fun resolve(
    address: String,
    origin: Pair<Double, Double>?,
    restrict: Boolean,
  ): Pair<Double, Double>? {
    val key = AddressNormalizer.key(address)
    if (key.isNotEmpty()) {
      repository.cachedPoint(key)?.let { return it }
    }

    val localPoint = local.lookup(address)
    if (localPoint != null) {
      if (key.isNotEmpty()) repository.saveCache(key, localPoint.first, localPoint.second, "Local index")
      return localPoint
    }

    val tomTomPoint = tomtom.geocode(address, restrict, origin)
    if (tomTomPoint != null && plausible(tomTomPoint, origin, restrict)) {
      if (key.isNotEmpty()) repository.saveCache(key, tomTomPoint.first, tomTomPoint.second, "TomTom")
      return tomTomPoint
    }

    val devicePoint = deviceGeocode(address)
    if (devicePoint != null && plausible(devicePoint, origin, restrict)) {
      if (key.isNotEmpty()) repository.saveCache(key, devicePoint.first, devicePoint.second, "Android Geocoder")
      return devicePoint
    }

    val nominatimPoint = nominatim(address)
    if (nominatimPoint != null && plausible(nominatimPoint, origin, restrict)) {
      if (key.isNotEmpty()) repository.saveCache(key, nominatimPoint.first, nominatimPoint.second, "Nominatim")
      return nominatimPoint
    }
    return null
  }

  private fun plausible(point: Pair<Double, Double>, origin: Pair<Double, Double>?, restrict: Boolean): Boolean {
    if (!restrict || origin == null) return true
    val distance = FloatArray(1)
    Location.distanceBetween(origin.first, origin.second, point.first, point.second, distance)
    return distance[0] <= MAX_AREA_DISTANCE_M
  }

  private suspend fun deviceGeocode(address: String): Pair<Double, Double>? {
    val geocoder = Geocoder(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return suspendCoroutine { continuation ->
        runCatching {
          geocoder.getFromLocationName(
            address,
            1,
            object : Geocoder.GeocodeListener {
              override fun onGeocode(addresses: MutableList<android.location.Address>) {
                val first = addresses.firstOrNull()
                continuation.resume(first?.let { it.latitude to it.longitude })
              }

              override fun onError(errorMessage: String?) {
                continuation.resume(null)
              }
            },
          )
        }.onFailure { continuation.resume(null) }
      }
    }
    @Suppress("DEPRECATION")
    return runCatching {
      geocoder.getFromLocationName(address, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
    }.getOrNull()
  }

  private suspend fun nominatim(address: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    val wait = synchronized(this@GeocodingService) {
      val remaining = 1100L - (System.currentTimeMillis() - lastCall)
      lastCall = System.currentTimeMillis() + if (remaining > 0) remaining else 0
      remaining
    }
    if (wait > 0) delay(wait)

    val query = URLEncoder.encode(address, "UTF-8")
    val request = Request.Builder()
      .url("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=$query")
      .addHeader("User-Agent", "RoutePilot/1.0 (courier route helper)")
      .build()
    runCatching {
      val body = client.newCall(request).execute().use { it.body?.string().orEmpty() }
      val first = JSON.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject ?: return@runCatching null
      val lat = first["lat"]?.toString()?.toDoubleOrNull() ?: return@runCatching null
      val lon = first["lon"]?.toString()?.toDoubleOrNull() ?: return@runCatching null
      lat to lon
    }.getOrNull()
  }

  companion object {
    private const val MAX_AREA_DISTANCE_M = 50_000f
    private val JSON = Json { ignoreUnknownKeys = true }
  }
}
