package com.example.mytube.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mytube.MyTubeApplication
import com.example.mytube.browser.BrowserMode
import com.example.mytube.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MyTubeApplication
    private val container = app.container

    val webViewManager = container.webViewManager
    val playbackManager = container.playbackManager
    val prefsManager = container.prefsManager

    private var ytLastUrlCache: String? = null
    private var moviesLastUrlCache: String? = null
    private var animeLastUrlCache: String? = null
    private var moviesHomeCache: String = Constants.MOVIES_HOME
    private var animeHomeCache: String = Constants.ANIME_HOME

    fun switchToYoutube() {
        webViewManager.activate(BrowserMode.YOUTUBE)
        if (webViewManager.webView == null) {
            webViewManager.pendingYoutubeUrl = ytLastUrlCache ?: Constants.YOUTUBE_HOME
        } else {
            webViewManager.loadUrl(Constants.YOUTUBE_HOME)
        }
    }

    fun switchToYoutubeMusic() {
        webViewManager.activate(BrowserMode.YOUTUBE)
        if (webViewManager.webView == null) {
            webViewManager.pendingYoutubeUrl = Constants.YOUTUBE_MUSIC_HOME
        } else {
            webViewManager.loadUrl(Constants.YOUTUBE_MUSIC_HOME)
        }
    }

    fun switchToMovies() {
        webViewManager.activate(BrowserMode.MOVIES)
        if (webViewManager.moviesWebView == null) {
            webViewManager.pendingMoviesUrl = moviesLastUrlCache ?: moviesHomeCache
        }
    }

    fun switchToAnime() {
        webViewManager.activate(BrowserMode.ANIME)
        if (webViewManager.animeWebView == null) {
            webViewManager.pendingAnimeUrl = animeLastUrlCache ?: animeHomeCache
        }
    }

    val bgPlaybackEnabled = prefsManager.backgroundPlayback.stateIn(
        viewModelScope, SharingStarted.Eagerly, true
    )

    val autoHideBar = prefsManager.autoHideBar.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    val notifPermissionRequested = prefsManager.notifPermissionRequested.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    fun markNotifPermissionRequested() {
        viewModelScope.launch { prefsManager.markNotifPermissionRequested() }
    }

    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining.asStateFlow()

    private var sleepTimerJob: Job? = null
    var onSleepTimerFired: (() -> Unit)? = null

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemaining.value = null
            return
        }
        _sleepTimerRemaining.value = minutes * 60_000L
        sleepTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val remaining = _sleepTimerRemaining.value ?: break
                if (remaining <= 1000) {
                    _sleepTimerRemaining.value = null
                    fireSleepTimer()
                    break
                } else {
                    _sleepTimerRemaining.value = remaining - 1000
                }
            }
        }
    }

    private fun fireSleepTimer() {
        sleepTimerJob = null
        playbackManager.destroy()
        onSleepTimerFired?.invoke()
    }

    var onExitApp: (() -> Unit)? = null

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun toggleLock() {
        _isLocked.value = !_isLocked.value
    }

    private val _showExitDialog = MutableStateFlow(false)
    val showExitDialog: StateFlow<Boolean> = _showExitDialog.asStateFlow()

    fun requestExit() {
        _showExitDialog.value = true
    }

    fun dismissExitDialog() {
        _showExitDialog.value = false
    }

    fun confirmExit() {
        _showExitDialog.value = false
        playbackManager.stopService()
        onExitApp?.invoke()
    }

    init {
        val abm = container.adBlockManager
        // Seed bundled rules synchronously so blocking + cosmetic CSS are active
        // on the very first frame, before the async filter-list work finishes.
        abm.loadBundled()
        webViewManager.documentStartScriptProvider = { mode ->
            abm.documentStartScript(forYoutube = mode == BrowserMode.YOUTUBE)
        }
        if (container.filterListUpdater.hasCached()) {
            viewModelScope.launch(Dispatchers.Default) { abm.loadCached() }
        } else {
            viewModelScope.launch { abm.downloadAndLoad() }
        }

        playbackManager.jsEvaluator = { webViewManager.evaluateJsFromMainThread(it) }
        webViewManager.networkBlocker = { container.adBlockManager.shouldBlock(it) }
        webViewManager.shouldBlockNavigation = { url, pageHost ->
            container.adBlockManager.shouldBlockNavigation(url, pageHost)
        }
        webViewManager.onPlaybackUpdate = { playing, title, duration, position, artUrl, hasNext, hasPrev ->
            if (bgPlaybackEnabled.value) {
                playbackManager.updateMetadata(playing, title, duration, position, artUrl, hasNext, hasPrev)
            } else if (!playing) {
                playbackManager.stopService()
            }
        }

        viewModelScope.launch {
            bgPlaybackEnabled.collect { enabled ->
                if (enabled) {
                    container.scriptManager.loadAsset("scripts/background_playback.js")
                        ?.let { webViewManager.evaluateJs(it) }
                } else {
                    playbackManager.stopService()
                    webViewManager.evaluateJs("window.setBackgroundMode && window.setBackgroundMode(false)")
                }
            }
        }

        webViewManager.onPageLoaded = { url, _ ->
            injectScripts()
            injectScriptlets(url)
            injectCosmeticCss(url)
        }
        webViewManager.onPersistablePage = { mode, url ->
            when (mode) {
                BrowserMode.YOUTUBE -> {
                    ytLastUrlCache = url
                    viewModelScope.launch { prefsManager.setYoutubeLastUrl(url) }
                }
                BrowserMode.MOVIES -> {
                    moviesLastUrlCache = url
                    viewModelScope.launch { prefsManager.setMoviesLastUrl(url) }
                }
                BrowserMode.ANIME -> {
                    animeLastUrlCache = url
                    viewModelScope.launch { prefsManager.setAnimeLastUrl(url) }
                }
            }
        }
        viewModelScope.launch {
            prefsManager.youtubeLastUrl.collect { ytLastUrlCache = it }
        }
        viewModelScope.launch {
            prefsManager.moviesLastUrl.collect { moviesLastUrlCache = it }
        }
        viewModelScope.launch {
            prefsManager.animeLastUrl.collect { animeLastUrlCache = it }
        }
        viewModelScope.launch {
            container.sourceConfig.moviesHome.collect { moviesHomeCache = it }
        }
        viewModelScope.launch {
            container.sourceConfig.animeHome.collect { animeHomeCache = it }
        }
        viewModelScope.launch { container.sourceConfig.refresh() }
        webViewManager.homeResolver = { mode ->
            when (mode) {
                BrowserMode.MOVIES -> moviesHomeCache
                BrowserMode.ANIME -> animeHomeCache
                BrowserMode.YOUTUBE -> null
            }
        }
        // Ad domain blocking in WebViewManager.shouldInterceptRequest.
        // Scriptlet handles ad stripping + YouTube internal config disabling.
        webViewManager.shouldIntercept = { null }
    }

    private fun injectScriptlets(url: String) {
        val domain = kotlin.runCatching { java.net.URI(url).host }.getOrNull() ?: return
        val js = container.adBlockManager.getScriptletJs(domain)
        if (js.isNotBlank()) webViewManager.evaluateJs(js)
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

    fun goBack(): Boolean {
        return webViewManager.goBack()
    }

    fun goForward(): Boolean {
        return webViewManager.goForward()
    }

    fun reload() {
        webViewManager.reload()
    }

    private fun injectScripts() {
        container.scriptManager.seedDefaults()
        container.scriptManager.loadAndInject({ source, _ ->
            webViewManager.evaluateJs(source)
        }, includeBgPlayback = bgPlaybackEnabled.value)
    }

    override fun onCleared() {
        super.onCleared()
        sleepTimerJob?.cancel()
        webViewManager.destroy()
        playbackManager.destroy()
    }
}
