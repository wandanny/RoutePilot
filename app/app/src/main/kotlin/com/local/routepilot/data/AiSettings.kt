package com.local.routepilot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AiSettings(context: Context) {
  private val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

  private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
    context,
    "routepilot_ai_settings",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
  )

  fun apiKey(): String = prefs.getString(KEY_API, "").orEmpty()

  fun saveApiKey(value: String) {
    prefs.edit().putString(KEY_API, value.trim()).apply()
  }

  fun hasConsent(): Boolean = prefs.getBoolean(KEY_CONSENT, false)

  fun markConsent() {
    prefs.edit().putBoolean(KEY_CONSENT, true).apply()
  }

  fun tomtomApiKey(): String = prefs.getString(KEY_TOMTOM_API, "").orEmpty()

  fun saveTomtomApiKey(value: String) {
    prefs.edit().putString(KEY_TOMTOM_API, value.trim()).apply()
  }

  fun restrictArea(): Boolean = prefs.getBoolean(KEY_RESTRICT_AREA, false)

  fun saveRestrictArea(value: Boolean) {
    prefs.edit().putBoolean(KEY_RESTRICT_AREA, value).apply()
  }

  fun indexVersion(): String = prefs.getString(KEY_INDEX_VERSION, "").orEmpty()

  fun saveIndexVersion(value: String) {
    prefs.edit().putString(KEY_INDEX_VERSION, value.trim()).apply()
  }

  fun indexCheckedAt(): Long = prefs.getLong(KEY_INDEX_CHECK, 0L)

  fun markIndexChecked() {
    prefs.edit().putLong(KEY_INDEX_CHECK, System.currentTimeMillis()).apply()
  }

  fun aiCoordsCleared(): Boolean = prefs.getBoolean(KEY_AI_COORDS_CLEARED, false)

  fun markAiCoordsCleared() {
    prefs.edit().putBoolean(KEY_AI_COORDS_CLEARED, true).apply()
  }

  private companion object {
    const val KEY_API = "lucif_api_key"
    const val KEY_CONSENT = "cloud_consent"
    const val KEY_TOMTOM_API = "tomtom_api_key"
    const val KEY_RESTRICT_AREA = "restrict_area"
    const val KEY_INDEX_VERSION = "index_version"
    const val KEY_INDEX_CHECK = "index_last_check"
    const val KEY_AI_COORDS_CLEARED = "ai_coords_cleared"
  }
}
