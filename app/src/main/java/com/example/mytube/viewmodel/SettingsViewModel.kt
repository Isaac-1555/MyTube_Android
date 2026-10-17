package com.example.mytube.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mytube.MyTubeApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MyTubeApplication
    private val container = app.container

    val backgroundPlayback: StateFlow<Boolean> = container.prefsManager.backgroundPlayback
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val autoPip: StateFlow<Boolean> = container.prefsManager.autoPip
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val adblockEnabled: StateFlow<Boolean> = container.prefsManager.adblockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val autoHideBar: StateFlow<Boolean> = container.prefsManager.autoHideBar
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val showYoutubeLink: StateFlow<Boolean> = container.prefsManager.showYoutubeLink
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showMusicLink: StateFlow<Boolean> = container.prefsManager.showMusicLink
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showMoviesLink: StateFlow<Boolean> = container.prefsManager.showMoviesLink
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showAnimeLink: StateFlow<Boolean> = container.prefsManager.showAnimeLink
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val moviesSourceUrl: Flow<String?> = container.prefsManager.moviesSourceUrl
    val animeSourceUrl: Flow<String?> = container.prefsManager.animeSourceUrl

    fun setMoviesSourceUrl(url: String?) {
        viewModelScope.launch { container.prefsManager.setMoviesSourceUrl(url) }
    }

    fun setAnimeSourceUrl(url: String?) {
        viewModelScope.launch { container.prefsManager.setAnimeSourceUrl(url) }
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch { container.prefsManager.setBackgroundPlayback(enabled) }
    }

    fun setAutoPip(enabled: Boolean) {
        viewModelScope.launch { container.prefsManager.setAutoPip(enabled) }
    }

    fun setAdblockEnabled(enabled: Boolean) {
        viewModelScope.launch { container.prefsManager.setAdblockEnabled(enabled) }
    }

    fun setAutoHideBar(enabled: Boolean) {
        viewModelScope.launch { container.prefsManager.setAutoHideBar(enabled) }
    }

    fun setShowYoutubeLink(enabled: Boolean) {
        viewModelScope.launch { container.prefsManager.setShowYoutubeLink(enabled) }
    }

    fun setShowMusicLink(enabled: Boolean) {
        viewModelScope.launch { container.prefsManager.setShowMusicLink(enabled) }
    }

    fun setShowMoviesLink(enabled: Boolean) {
        viewModelScope.launch { container.prefsManager.setShowMoviesLink(enabled) }
    }

    fun setShowAnimeLink(enabled: Boolean) {
        viewModelScope.launch { container.prefsManager.setShowAnimeLink(enabled) }
    }
}
