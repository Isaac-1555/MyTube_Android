package com.example.mytube.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves the current home URL for the movies / anime tabs.
 *
 * Precedence: user override (Settings) -> remotely advertised -> hardcoded default.
 * The remote config lets the rotating movie/anime mirrors be updated without a
 * store release; every other value is a safe fallback.
 */
class SourceConfigRepository(
    context: Context,
    private val prefs: PreferencesManager
) {
    private val appContext = context.applicationContext

    val moviesHome: Flow<String> = combine(prefs.moviesSourceUrl, prefs.remoteMoviesHome) { user, remote ->
        firstNonBlank(user, remote, Constants.MOVIES_HOME)
    }

    val animeHome: Flow<String> = combine(prefs.animeSourceUrl, prefs.remoteAnimeHome) { user, remote ->
        firstNonBlank(user, remote, Constants.ANIME_HOME)
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(Constants.SOURCES_CONFIG_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            connection.inputStream.bufferedReader().use { it.readText() }.let { body ->
                val json = JSONObject(body)
                prefs.setRemoteHomes(
                    movies = json.optString("movies").ifBlank { null },
                    anime = json.optString("anime").ifBlank { null }
                )
            }
            connection.disconnect()
        } catch (_: Exception) {
            // Offline / bad config: keep the cached or default home.
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8000
        const val READ_TIMEOUT_MS = 8000
    }
}

private fun firstNonBlank(vararg values: String?): String {
    return values.firstOrNull { !it.isNullOrBlank() }?.trim() ?: Constants.MOVIES_HOME
}
