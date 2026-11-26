package com.local.routepilot.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object AddressNormalizer {
  private val IGNORED = setOf("no", "taiping", "kamunting", "simpang", "perak", "malaysia")
  private val NON_ALNUM = Regex("[^a-z0-9]+")

  fun normalize(text: String): List<String> =
    NON_ALNUM.replace(text.lowercase(Locale.ROOT), " ").trim().split(' ').filter { it.isNotEmpty() }

  fun key(text: String): String =
    normalize(text).filter { token -> token !in IGNORED && !isPostcode(token) }.joinToString(" ")

  fun postcode(text: String): String? = normalize(text).firstOrNull { isPostcode(it) }

  private fun isPostcode(token: String): Boolean = token.length == 5 && token.all { it.isDigit() }
}

@Serializable
private data class AddressEntry(
  val a: String = "",
  val n: String = "",
  val p: String = "",
  val y: Double = 0.0,
  val x: Double = 0.0,
  val s: String = "",
)

class LocalAddressIndex(private val context: Context) {
  private val settings = AiSettings(context)
  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

  @Volatile private var entries: List<AddressEntry> = emptyList()

  val count: Int
    get() {
      loadIfNeeded()
      return entries.size
    }

  init {
    loadIfNeeded()
  }

  @Synchronized
  fun lookup(address: String): Pair<Double, Double>? {
    loadIfNeeded()
    val key = AddressNormalizer.key(address)
    if (key.isEmpty()) return null
    val tokens = key.split(' ').filter { it.isNotEmpty() }
    val hasHouseNumber = tokens.any { it.first().isDigit() }
    val expectedPostcode = AddressNormalizer.postcode(address)

    var match: AddressEntry? = null
    var matches = 0
    for (entry in entries) {
      if (expectedPostcode != null && entry.p.isNotBlank() && entry.p != expectedPostcode) continue
      val hit = if (hasHouseNumber) {
        val entryTokens = entry.a.split(' ').filter { it.isNotEmpty() }.toSet()
        entryTokens.isNotEmpty() && tokens.all { it in entryTokens }
      } else {
        entry.n.isNotBlank() && entry.n == key
      }
      if (hit) {
        matches++
        if (matches > 1) return null
        match = entry
      }
    }
    return match?.let { it.y to it.x }
  }

  suspend fun refreshIfNeeded(force: Boolean): Boolean = withContext(Dispatchers.IO) {
    val lastCheck = settings.indexCheckedAt()
    settings.markIndexChecked()
    if (!force && System.currentTimeMillis() - lastCheck < WEEK_MS) return@withContext false

    val remoteVersion = fetchText(VERSION_URL)?.trim().orEmpty()
    if (remoteVersion.isEmpty()) return@withContext false
    if (!force && remoteVersion == settings.indexVersion()) return@withContext false

    val text = fetchText(INDEX_URL) ?: return@withContext false
    val decoded = parse(text)
    if (decoded.isEmpty()) return@withContext false
    val destination = File(context.filesDir, FILE_NAME)
    if (runCatching { destination.writeText(text) }.isFailure) return@withContext false

    synchronized(this) { entries = decoded }
    settings.saveIndexVersion(remoteVersion)
    true
  }

  @Synchronized
  private fun loadIfNeeded() {
    if (entries.isNotEmpty()) return
    val downloaded = File(context.filesDir, FILE_NAME)
    val text = if (downloaded.exists()) {
      runCatching { downloaded.readText() }.getOrNull()
    } else {
      runCatching { context.assets.open(FILE_NAME).bufferedReader().use { it.readText() } }.getOrNull()
    }
    if (!text.isNullOrEmpty()) entries = parse(text)
  }

  private fun fetchText(url: String): String? = runCatching {
    val request = Request.Builder().url(url).build()
    client.newCall(request).execute().use { response ->
      if (response.isSuccessful) response.body?.string() else null
    }
  }.getOrNull()

  private fun parse(text: String): List<AddressEntry> =
    text.lineSequence().mapNotNull { line ->
      if (line.isBlank()) null else runCatching { JSON.decodeFromString<AddressEntry>(line) }.getOrNull()
    }.toList()

  companion object {
    private const val FILE_NAME = "taiping-addresses.jsonl"
    private const val VERSION_URL =
      "https://raw.githubusercontent.com/wandanny/RoutePilot/main/ios/data/taiping-addresses.version"
    private const val INDEX_URL =
      "https://raw.githubusercontent.com/wandanny/RoutePilot/main/ios/data/taiping-addresses.jsonl"
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private val JSON = Json { ignoreUnknownKeys = true }
  }
}
