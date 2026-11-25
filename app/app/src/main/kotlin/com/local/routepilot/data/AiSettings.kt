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

  private companion object {
    const val KEY_API = "lucif_api_key"
    const val KEY_CONSENT = "cloud_consent"
  }
}
