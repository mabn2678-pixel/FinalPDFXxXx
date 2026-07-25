package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.BuildConfig

object SecureKeyManager {
    private const val PREFS_NAME = "secure_api_keys"
    private const val KEY_GEMINI_API = "user_gemini_api_key"

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            e.printStackTrace()
            context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveGeminiApiKey(context: Context, key: String) {
        val trimmed = key.trim()
        getEncryptedPrefs(context).edit().putString(KEY_GEMINI_API, trimmed).apply()
        try {
            context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
                .edit().putString(KEY_GEMINI_API, trimmed).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getGeminiApiKey(context: Context): String {
        val userKey = getEncryptedPrefs(context).getString(KEY_GEMINI_API, "")?.trim() ?: ""
        if (userKey.isNotBlank()) return userKey

        val legacyKey = try {
            context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
                .getString(KEY_GEMINI_API, "")?.trim() ?: ""
        } catch (e: Exception) { "" }

        if (legacyKey.isNotBlank()) {
            saveGeminiApiKey(context, legacyKey)
            return legacyKey
        }

        return try {
            val buildConfigKey = BuildConfig.GEMINI_API_KEY
            if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") buildConfigKey else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun clearGeminiApiKey(context: Context) {
        getEncryptedPrefs(context).edit().remove(KEY_GEMINI_API).apply()
        try {
            context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
                .edit().remove(KEY_GEMINI_API).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hasSavedKey(context: Context): Boolean {
        val userKey = getEncryptedPrefs(context).getString(KEY_GEMINI_API, "")?.trim() ?: ""
        if (userKey.isNotBlank()) return true
        val legacyKey = try {
            context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
                .getString(KEY_GEMINI_API, "")?.trim() ?: ""
        } catch (e: Exception) { "" }
        return legacyKey.isNotBlank()
    }
}
