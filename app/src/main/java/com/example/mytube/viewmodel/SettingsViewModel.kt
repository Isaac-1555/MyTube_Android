package com.example.mytube.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mytube.MyTubeApplication
import com.example.mytube.data.entity.BookmarkEntity
import com.example.mytube.data.entity.ScriptEntity
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

    fun getBookmarks(): List<BookmarkEntity> = container.bookmarkRepository.getAll()

    fun getScripts(): List<ScriptEntity> = container.scriptRepository.getAll()

    fun setBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch { container.prefsManager.setBackgroundPlayback(enabled) }
    }

    fun setAutoPip(enabled: Boolean) {
        viewModelScope.launch { container.prefsManager.setAutoPip(enabled) }
    }

    fun setAdblockEnabled(enabled: Boolean) {
        viewModelScope.launch { container.prefsManager.setAdblockEnabled(enabled) }
    }

    fun toggleScript(id: String, enabled: Boolean) {
        container.scriptRepository.setEnabled(id, enabled)
    }

    fun removeBookmark(url: String) {
        container.bookmarkRepository.remove(url)
    }
}
