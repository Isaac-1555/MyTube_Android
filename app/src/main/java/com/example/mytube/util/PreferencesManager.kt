package com.example.mytube.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFS_NAME)

class PreferencesManager(private val context: Context) {
    companion object {
        val BG_PLAYBACK = booleanPreferencesKey("bg_playback")
        val AUTO_PIP = booleanPreferencesKey("auto_pip")
        val ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
    }

    val backgroundPlayback: Flow<Boolean> = context.dataStore.data.map { it[BG_PLAYBACK] ?: true }
    val autoPip: Flow<Boolean> = context.dataStore.data.map { it[AUTO_PIP] ?: true }
    val adblockEnabled: Flow<Boolean> = context.dataStore.data.map { it[ADBLOCK_ENABLED] ?: true }

    suspend fun setBackgroundPlayback(enabled: Boolean) {
        context.dataStore.edit { it[BG_PLAYBACK] = enabled }
    }

    suspend fun setAutoPip(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_PIP] = enabled }
    }

    suspend fun setAdblockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ADBLOCK_ENABLED] = enabled }
    }
}
