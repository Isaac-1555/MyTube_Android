package com.example.mytube.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFS_NAME)

class PreferencesManager(private val context: Context) {
    companion object {
        val BG_PLAYBACK = booleanPreferencesKey("bg_playback")
        val AUTO_PIP = booleanPreferencesKey("auto_pip")
        val ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
        val AUTO_HIDE_BAR = booleanPreferencesKey("auto_hide_bar")
        val NOTIF_PERM_REQUESTED = booleanPreferencesKey("notif_perm_requested")
        val YOUTUBE_LAST_URL = stringPreferencesKey("youtube_last_url")
        val MOVIES_LAST_URL = stringPreferencesKey("movies_last_url")
        val ANIME_LAST_URL = stringPreferencesKey("anime_last_url")
    }

    val backgroundPlayback: Flow<Boolean> = context.dataStore.data.map { it[BG_PLAYBACK] ?: true }
    val autoPip: Flow<Boolean> = context.dataStore.data.map { it[AUTO_PIP] ?: true }
    val adblockEnabled: Flow<Boolean> = context.dataStore.data.map { it[ADBLOCK_ENABLED] ?: true }
    val autoHideBar: Flow<Boolean> = context.dataStore.data.map { it[AUTO_HIDE_BAR] ?: false }
    val notifPermissionRequested: Flow<Boolean> = context.dataStore.data.map { it[NOTIF_PERM_REQUESTED] ?: false }
    val youtubeLastUrl: Flow<String?> = context.dataStore.data.map { it[YOUTUBE_LAST_URL] }
    val moviesLastUrl: Flow<String?> = context.dataStore.data.map { it[MOVIES_LAST_URL] }
    val animeLastUrl: Flow<String?> = context.dataStore.data.map { it[ANIME_LAST_URL] }

    suspend fun setBackgroundPlayback(enabled: Boolean) {
        context.dataStore.edit { it[BG_PLAYBACK] = enabled }
    }

    suspend fun setAutoPip(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_PIP] = enabled }
    }

    suspend fun setAdblockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ADBLOCK_ENABLED] = enabled }
    }

    suspend fun setAutoHideBar(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_HIDE_BAR] = enabled }
    }

    suspend fun markNotifPermissionRequested() {
        context.dataStore.edit { it[NOTIF_PERM_REQUESTED] = true }
    }

    suspend fun setYoutubeLastUrl(url: String) {
        context.dataStore.edit { it[YOUTUBE_LAST_URL] = url }
    }

    suspend fun setMoviesLastUrl(url: String) {
        context.dataStore.edit { it[MOVIES_LAST_URL] = url }
    }

    suspend fun setAnimeLastUrl(url: String) {
        context.dataStore.edit { it[ANIME_LAST_URL] = url }
    }
}
