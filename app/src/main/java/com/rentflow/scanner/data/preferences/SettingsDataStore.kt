package com.rentflow.scanner.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val serverUrl: Flow<String> = context.dataStore.data.map { it[KEY_SERVER_URL] ?: "" }
    val language: Flow<String> = context.dataStore.data.map { it[KEY_LANGUAGE] ?: "de" }
    val scanMode: Flow<String> = context.dataStore.data.map { it[KEY_SCAN_MODE] ?: SCAN_MODE_BARCODE }
    val lockTimeoutMinutes: Flow<Int> = context.dataStore.data.map { it[KEY_LOCK_TIMEOUT] ?: DEFAULT_LOCK_TIMEOUT_MINUTES }
    val fullReloginHours: Flow<Int> = context.dataStore.data.map { it[KEY_FULL_RELOGIN] ?: DEFAULT_FULL_RELOGIN_HOURS }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun setScanMode(mode: String) {
        context.dataStore.edit { it[KEY_SCAN_MODE] = mode }
    }

    suspend fun setLockTimeout(minutes: Int) {
        context.dataStore.edit { it[KEY_LOCK_TIMEOUT] = minutes }
    }

    suspend fun setFullReloginTimeout(hours: Int) {
        context.dataStore.edit { it[KEY_FULL_RELOGIN] = hours }
    }

    companion object {
        const val SCAN_MODE_BARCODE = "barcode"
        const val SCAN_MODE_RFID = "rfid"
        const val DEFAULT_LOCK_TIMEOUT_MINUTES = 30
        const val DEFAULT_FULL_RELOGIN_HOURS = 4
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_SCAN_MODE = stringPreferencesKey("scan_mode")
        private val KEY_LOCK_TIMEOUT = intPreferencesKey("lock_timeout_minutes")
        private val KEY_FULL_RELOGIN = intPreferencesKey("full_relogin_hours")
    }
}
