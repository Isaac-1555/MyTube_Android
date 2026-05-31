package com.example.mytube.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mytube.MyTubeApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MyTubeApplication
    private val container = app.container

    val webViewManager = container.webViewManager
    val playbackManager = container.playbackManager
    val bookmarkRepository = container.bookmarkRepository
    val prefsManager = container.prefsManager

    val bgPlaybackEnabled = prefsManager.backgroundPlayback.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )
    val autoPipEnabled = prefsManager.autoPip.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )

    init {
        val abm = container.adBlockManager
        if (abm.isReady) {
            abm.loadCached()
        } else {
            viewModelScope.launch {
                abm.downloadAndLoad()
            }
        }

        playbackManager.jsEvaluator = { webViewManager.evaluateJsFromMainThread(it) }
        webViewManager.onPlaybackUpdate = { playing, title, duration, position ->
            playbackManager.updateMetadata(playing, title, duration, position)
            if (playing && bgPlaybackEnabled.value) {
                playbackManager.startService()
            } else if (!playing) {
                playbackManager.stopService()
            }
        }

        webViewManager.onPageLoaded = { url, _ ->
            injectScripts()
            injectCosmeticCss(url)
        }
        // Ad domain blocking in WebViewManager.shouldInterceptRequest.
        // Scriptlet handles ad stripping + YouTube internal config disabling.
        webViewManager.shouldIntercept = { null }
    }

    private fun injectCosmeticCss(url: String) {
        val domain = kotlin.runCatching { java.net.URI(url).host }.getOrNull() ?: return
        val css = container.adBlockManager.getCosmeticCss(domain)
        if (css.isNotBlank()) {
            val escaped = css
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            webViewManager.evaluateJs(
                "(function(){var s=document.createElement('style');" +
                "s.textContent='$escaped';" +
                "document.documentElement.appendChild(s);})()"
            )
        }
    }

    fun loadUrl(url: String) {
        val fixed = if (!url.startsWith("http")) "https://$url" else url
        webViewManager.loadUrl(fixed)
    }

    fun goBack() {
        if (!webViewManager.goBack()) {
            playbackManager.stopService()
        }
    }

    fun goForward() {
        webViewManager.goForward()
    }

    fun reload() {
        webViewManager.reload()
    }

    fun toggleBookmark(url: String, title: String) {
        if (bookmarkRepository.exists(url)) {
            bookmarkRepository.remove(url)
        } else {
            bookmarkRepository.add(url, title)
        }
    }

    fun isBookmarked(url: String): Boolean = bookmarkRepository.exists(url)

    private fun injectScripts() {
        container.scriptManager.seedDefaults()
        container.scriptManager.loadAndInject { source, _ ->
            webViewManager.evaluateJs(source)
        }
    }

    override fun onCleared() {
        super.onCleared()
        webViewManager.destroy()
        playbackManager.destroy()
    }
}
