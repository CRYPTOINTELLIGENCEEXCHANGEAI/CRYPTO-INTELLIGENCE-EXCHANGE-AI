package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "myfund_session")

class SessionManager(private val context: Context) {

    companion object {
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_APPS_SCRIPT_URL = stringPreferencesKey("apps_script_url")
    }

    val loggedInEmailFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_EMAIL]?.takeIf { it.isNotBlank() }
    }

    val appsScriptUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val saved = prefs[KEY_APPS_SCRIPT_URL]?.trim().orEmpty()
        saved.ifBlank { BuildConfig.APPS_SCRIPT_URL.trim() }
    }

    suspend fun getAppsScriptUrl(): String {
        return appsScriptUrlFlow.first()
    }

    suspend fun saveUserEmail(email: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_EMAIL] = email.trim().lowercase()
        }
    }

    suspend fun saveAppsScriptUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_APPS_SCRIPT_URL] = url.trim()
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_USER_EMAIL)
        }
    }
}
