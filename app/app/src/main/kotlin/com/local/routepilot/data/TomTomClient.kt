package com.local.routepilot.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class TomTomClient(private val settings: AiSettings) {
  constructor(context: Context) : this(AiSettings(context))

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  fun hasKey(): Boolean = settings.tomtomApiKey().isNotBlank()

  suspend fun geocode(
    address: String,
    restrict: Boolean,
    origin: Pair<Double, Double>?,
  ): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    val key = settings.tomtomApiKey()
    if (key.isBlank()) return@withContext null

    val url = HttpUrl.Builder()
      .scheme("https")
      .host("api.tomtom.com")
      .addPathSegment("search")
      .addPathSegment("2")
      .addPathSegment("geocode")
      .addPathSegment("$address.json")
      .addQueryParameter("key", key)
      .addQueryParameter("limit", "1")
      .addQueryParameter("countrySet", "MY")
      .apply {
        if (restrict && origin != null) {
          addQueryParameter("lat", origin.first.toString())
          addQueryParameter("lon", origin.second.toString())
          addQueryParameter("radius", "50000")
        }
      }
      .build()

    runCatching {
      val body = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string().orEmpty() }
      val first = JSON.parseToJsonElement(body).jsonObject["results"]?.jsonArray?.firstOrNull()?.jsonObject
        ?: return@runCatching null
      val position = first["position"]?.jsonObject ?: return@runCatching null
      val lat = position["lat"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
      val lon = position["lon"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
      lat to lon
    }.getOrNull()
  }

  suspend fun matrix(
    origin: Pair<Double, Double>,
    stops: List<Pair<Double, Double>>,
  ): List<List<Double>>? = withContext(Dispatchers.IO) {
    val key = settings.tomtomApiKey()
    if (key.isBlank()) return@withContext null

    val points = (listOf(origin) + stops).map { point ->
      buildJsonObject {
        put(
          "point",
          buildJsonObject {
            put("latitude", point.first)
            put("longitude", point.second)
          },
        )
      }
    }
    val copies = buildJsonArray { points.forEach { add(it) } }
    val body = buildJsonObject {
      put("origins", copies)
      put("destinations", copies)
    }.toString()

    val url = HttpUrl.Builder()
      .scheme("https")
      .host("api.tomtom.com")
      .addPathSegment("routing")
      .addPathSegment("1")
      .addPathSegment("matrix")
      .addPathSegment("json")
      .addQueryParameter("key", key)
      .build()
    val request = Request.Builder()
      .url(url)
      .addHeader("Content-Type", "application/json")
      .post(body.toRequestBody(JSON_MEDIA))
      .build()

    runCatching {
      val text = client.newCall(request).execute().use { it.body?.string().orEmpty() }
      val matrix = JSON.parseToJsonElement(text).jsonObject["matrix"]?.jsonArray ?: return@runCatching null
      val durations = matrix.map { row ->
        row.jsonArray.map { cell ->
          cell.jsonObject["response"]?.jsonObject?.get("routeSummary")?.jsonObject
            ?.get("travelTimeInSeconds")?.jsonPrimitive?.doubleOrNull ?: 0.0
        }
      }
      if (durations.isEmpty()) null else durations
    }.getOrNull()
  }

  companion object {
    private val JSON_MEDIA = "application/json".toMediaType()
    private val JSON = Json { ignoreUnknownKeys = true }
  }
}
