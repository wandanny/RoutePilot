package com.local.routepilot.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

@Serializable
data class CloudFields(
  val name: String? = null,
  val address: String? = null,
  val postcode: String? = null,
)

class LucifClient(private val settings: AiSettings) {
  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

  suspend fun extract(jpeg: ByteArray): CloudFields = withContext(Dispatchers.IO) {
    val key = settings.apiKey()
    require(key.isNotBlank()) { "Set an API key in AI settings first" }

    val base64 = Base64.getEncoder().encodeToString(jpeg)
    val body = buildJsonObject {
      put("model", "lucif")
      put("max_tokens", 250)
      put(
        "messages",
        buildJsonArray {
          add(
            buildJsonObject {
              put("role", "user")
              put(
                "content",
                buildJsonArray {
                  add(buildJsonObject { put("type", "text"); put("text", PROMPT) })
                  add(
                    buildJsonObject {
                      put("type", "image_url")
                      put(
                        "image_url",
                        buildJsonObject { put("url", "data:image/jpeg;base64,$base64") },
                      )
                    },
                  )
                },
              )
            },
          )
        },
      )
    }.toString()

    val request = Request.Builder()
      .url(ENDPOINT)
      .addHeader("Authorization", "Bearer $key")
      .addHeader("Content-Type", "application/json")
      .post(body.toRequestBody(JSON_MEDIA))
      .build()

    val raw = client.newCall(request).execute().use { response ->
      val text = response.body?.string().orEmpty()
      if (!response.isSuccessful) error("Service returned ${response.code}: ${text.take(120)}")
      text
    }
    val root = JSON.parseToJsonElement(raw).jsonObject
    val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
      ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
      ?: error("No content in response")
    parseFields(content)
  }

  suspend fun estimateCoordinates(address: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    runCatching {
      val body = buildJsonObject {
        put("model", "lucif")
        put("max_tokens", 120)
        put(
          "messages",
          buildJsonArray {
            add(
              buildJsonObject {
                put("role", "user")
                put(
                  "content",
                  "Estimate latitude and longitude for this Malaysian delivery address: $address. " +
                    "Return ONLY compact JSON: {\"lat\":number,\"lon\":number}",
                )
              },
            )
          },
        )
      }.toString()
      val request = Request.Builder()
        .url(ENDPOINT)
        .addHeader("Authorization", "Bearer ${settings.apiKey()}")
        .addHeader("Content-Type", "application/json")
        .post(body.toRequestBody(JSON_MEDIA))
        .build()
      val raw = client.newCall(request).execute().use { it.body?.string().orEmpty() }
      val root = JSON.parseToJsonElement(raw).jsonObject
      val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
        ?: error("No content in response")
      val start = content.indexOf('{')
      val end = content.lastIndexOf('}')
      require(start >= 0 && end > start) { "No JSON in geocoding response" }
      val obj = JSON.parseToJsonElement(content.substring(start, end + 1)).jsonObject
      val lat = obj["lat"]?.toString()?.toDoubleOrNull() ?: return@runCatching null
      val lon = obj["lon"]?.toString()?.toDoubleOrNull() ?: return@runCatching null
      lat to lon
    }.getOrNull()
  }

  companion object {
    private const val ENDPOINT = "https://lucif.ai/v1/chat/completions"
    private val JSON_MEDIA = "application/json".toMediaType()
    private val JSON = Json { ignoreUnknownKeys = true }
    private const val PROMPT =
      "Read this parcel label. Return ONLY compact JSON: {\"name\":...,\"address\":...,\"postcode\":...} " +
        "for the RECIPIENT, not the sender. Use null if unknown."

    fun parseFields(raw: String): CloudFields {
      val start = raw.indexOf('{')
      val end = raw.lastIndexOf('}')
      require(start >= 0 && end > start) { "No JSON object in response" }
      return JSON.decodeFromString<CloudFields>(raw.substring(start, end + 1))
    }
  }
}
