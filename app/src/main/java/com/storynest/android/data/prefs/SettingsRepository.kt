package com.storynest.android.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Stores the Gemini API key in EncryptedSharedPreferences.
 * Never log the key value.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "storynest_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Fallback if Keystore unavailable (rare emulator edge cases)
        context.getSharedPreferences("storynest_prefs_fallback", Context.MODE_PRIVATE)
    }

    private val _hasApiKey = MutableStateFlow(readKey().isNotBlank())
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    fun getApiKey(): String = readKey()

    suspend fun setApiKey(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_API, key.trim()).apply()
        _hasApiKey.value = key.trim().isNotBlank()
    }

    suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_API).apply()
        _hasApiKey.value = false
    }

    /** Masked preview for Settings UI — never the full key. */
    fun maskedPreview(): String {
        val k = readKey()
        if (k.isBlank()) return ""
        if (k.length <= 8) return "••••••••"
        return k.take(4) + "••••" + k.takeLast(4)
    }

    private fun readKey(): String = prefs.getString(KEY_API, "") ?: ""

    companion object {
        private const val KEY_API = "gemini_api_key"
    }
}
