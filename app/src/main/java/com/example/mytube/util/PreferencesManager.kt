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
        val YOUTUBE_MUSIC_LAST_URL = stringPreferencesKey("youtube_music_last_url")
        val MOVIES_LAST_URL = stringPreferencesKey("movies_last_url")
        val ANIME_LAST_URL = stringPreferencesKey("anime_last_url")
        val MOVIES_SOURCE_URL = stringPreferencesKey("movies_source_url")
        val ANIME_SOURCE_URL = stringPreferencesKey("anime_source_url")
        val REMOTE_MOVIES_HOME = stringPreferencesKey("remote_movies_home")
        val REMOTE_ANIME_HOME = stringPreferencesKey("remote_anime_home")
    }

    val backgroundPlayback: Flow<Boolean> = context.dataStore.data.map { it[BG_PLAYBACK] ?: true }
    val autoPip: Flow<Boolean> = context.dataStore.data.map { it[AUTO_PIP] ?: true }
    val adblockEnabled: Flow<Boolean> = context.dataStore.data.map { it[ADBLOCK_ENABLED] ?: true }
    val autoHideBar: Flow<Boolean> = context.dataStore.data.map { it[AUTO_HIDE_BAR] ?: false }
    val notifPermissionRequested: Flow<Boolean> = context.dataStore.data.map { it[NOTIF_PERM_REQUESTED] ?: false }
    val youtubeLastUrl: Flow<String?> = context.dataStore.data.map { it[YOUTUBE_LAST_URL] }
    val youtubeMusicLastUrl: Flow<String?> = context.dataStore.data.map { it[YOUTUBE_MUSIC_LAST_URL] }
    val moviesLastUrl: Flow<String?> = context.dataStore.data.map { it[MOVIES_LAST_URL] }
    val animeLastUrl: Flow<String?> = context.dataStore.data.map { it[ANIME_LAST_URL] }

    val moviesSourceUrl: Flow<String?> = context.dataStore.data.map { it[MOVIES_SOURCE_URL] }
    val animeSourceUrl: Flow<String?> = context.dataStore.data.map { it[ANIME_SOURCE_URL] }
    val remoteMoviesHome: Flow<String?> = context.dataStore.data.map { it[REMOTE_MOVIES_HOME] }
    val remoteAnimeHome: Flow<String?> = context.dataStore.data.map { it[REMOTE_ANIME_HOME] }

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

    suspend fun setYoutubeMusicLastUrl(url: String) {
        context.dataStore.edit { it[YOUTUBE_MUSIC_LAST_URL] = url }
    }

    suspend fun setMoviesLastUrl(url: String) {
        context.dataStore.edit { it[MOVIES_LAST_URL] = url }
    }

    suspend fun setAnimeLastUrl(url: String) {
        context.dataStore.edit { it[ANIME_LAST_URL] = url }
    }

    suspend fun setMoviesSourceUrl(url: String?) {
        context.dataStore.edit {
            if (url.isNullOrBlank()) it.remove(MOVIES_SOURCE_URL) else it[MOVIES_SOURCE_URL] = url.trim()
        }
    }

    suspend fun setAnimeSourceUrl(url: String?) {
        context.dataStore.edit {
            if (url.isNullOrBlank()) it.remove(ANIME_SOURCE_URL) else it[ANIME_SOURCE_URL] = url.trim()
        }
    }

    suspend fun setRemoteHomes(movies: String?, anime: String?) {
        context.dataStore.edit { prefs ->
            if (!movies.isNullOrBlank()) prefs[REMOTE_MOVIES_HOME] = movies.trim()
            if (!anime.isNullOrBlank()) prefs[REMOTE_ANIME_HOME] = anime.trim()
        }
    }
}
