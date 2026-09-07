package com.example.mytube.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mytube.MyTubeApplication
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
}
