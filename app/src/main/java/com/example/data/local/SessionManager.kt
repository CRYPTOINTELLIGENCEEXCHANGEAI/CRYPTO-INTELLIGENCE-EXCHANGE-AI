package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

data class SavedCredentials(
    val username: String,
    val password: String
)

data class ProfileOverride(
    val name: String,
    val phone: String,
    val photoPath: String
)

class SessionManager(context: Context) {

    companion object {
        private const val PREF_NAME = "myfund_encrypted_session"
        private const val KEY_USERNAME = "saved_username"
        private const val KEY_PASSWORD = "saved_password"
        private const val PREFIX_OVERRIDE_NAME = "profile_name_"
        private const val PREFIX_OVERRIDE_PHONE = "profile_phone_"
        private const val PREFIX_OVERRIDE_PHOTO = "profile_photo_"
    }

    private val prefs: SharedPreferences = createEncryptedPrefs(context.applicationContext)

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREF_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w("SessionManager", "EncryptedSharedPreferences fallback: ${e.message}")
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getSavedCredentials(): SavedCredentials? {
        val username = prefs.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val password = prefs.getString(KEY_PASSWORD, null).orEmpty()
        if (username.isEmpty() || password.isEmpty()) return null
        return SavedCredentials(username = username, password = password)
    }

    fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }

    private fun safeKey(rawKey: String): String {
        return rawKey.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
    }

    fun saveProfileOverride(userKey: String, name: String, phone: String, photoPath: String) {
        val key = safeKey(userKey)
        if (key.isBlank()) return
        prefs.edit()
            .putString(PREFIX_OVERRIDE_NAME + key, name.trim())
            .putString(PREFIX_OVERRIDE_PHONE + key, phone.trim())
            .putString(PREFIX_OVERRIDE_PHOTO + key, photoPath.trim())
            .apply()
    }

    fun getProfileOverride(userKey: String): ProfileOverride? {
        val key = safeKey(userKey)
        if (key.isBlank()) return null
        val name = prefs.getString(PREFIX_OVERRIDE_NAME + key, null).orEmpty()
        val phone = prefs.getString(PREFIX_OVERRIDE_PHONE + key, null).orEmpty()
        val photo = prefs.getString(PREFIX_OVERRIDE_PHOTO + key, null).orEmpty()
        if (name.isBlank() && phone.isBlank() && photo.isBlank()) return null
        return ProfileOverride(name = name, phone = phone, photoPath = photo)
    }
}
