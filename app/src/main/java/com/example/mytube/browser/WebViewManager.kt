package com.example.mytube.browser

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewBuilder
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.mytube.adblock.UblockScriptlets
import com.example.mytube.util.Constants
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

enum class BrowserMode { YOUTUBE, MOVIES, ANIME }

class WebViewManager {
    var webView: WebView? = null
        private set

    var moviesWebView: WebView? = null
        private set

    var animeWebView: WebView? = null
        private set

    // First URL to load when each WebView is created (resume support).
    // Consumed once the WebView is created.
    var pendingYoutubeUrl: String? = null
    var pendingMoviesUrl: String? = null
    var pendingAnimeUrl: String? = null

    private val profiles = mutableMapOf<BrowserMode, Profile>()

    private val _activeMode = mutableStateOf(BrowserMode.YOUTUBE)
    var activeMode: BrowserMode
        get() = _activeMode.value
        private set(value) { _activeMode.value = value }

    fun activate(mode: BrowserMode) {
        _activeMode.value = mode
    }

    private fun activeWebView(): WebView? = when (activeMode) {
        BrowserMode.YOUTUBE -> webView
        BrowserMode.MOVIES -> moviesWebView
        BrowserMode.ANIME -> animeWebView
    }

    private val _currentUrl = mutableStateOf(Constants.YOUTUBE_HOME)
    var currentUrl: String
        get() = _currentUrl.value
        private set(value) { _currentUrl.value = value }

    private val _pageTitle = mutableStateOf("MyTube")
    var pageTitle: String
        get() = _pageTitle.value
        private set(value) { _pageTitle.value = value }

    private val _isLoading = mutableStateOf(false)
    var isLoading: Boolean
        get() = _isLoading.value
        private set(value) { _isLoading.value = value }

    private val _canGoBack = mutableStateOf(false)
    var canGoBack: Boolean
        get() = _canGoBack.value
        private set(value) { _canGoBack.value = value }

    private val _canGoForward = mutableStateOf(false)
    var canGoForward: Boolean
        get() = _canGoForward.value
        private set(value) { _canGoForward.value = value }

    private val _progress = mutableStateOf(0)
    var progress: Int
        get() = _progress.value
        private set(value) { _progress.value = value }

    var onPageLoaded: ((String, String) -> Unit)? = null
    var onPersistablePage: ((BrowserMode, String) -> Unit)? = null
    var homeResolver: ((BrowserMode) -> String?)? = null
    var onNavigationBlocked: ((String) -> Unit)? = null
    var shouldIntercept: ((String) -> WebResourceResponse?)? = null
    var onPlaybackUpdate: ((Boolean, String, Double, Double, String?, Boolean, Boolean) -> Unit)? = null
    var networkBlocker: ((String) -> Boolean)? = null

    /**
     * Cancels an ad/tracker top-level navigation on non-YouTube tabs. Receives
     * (targetUrl, currentPageHost); returns true to block.
     */
    var shouldBlockNavigation: ((String, String?) -> Boolean)? = null

    /**
     * Supplies the document-start ad-block script for a given mode. Set by the
     * ViewModel so the script can include the current cosmetic filter set.
     */
    var documentStartScriptProvider: ((BrowserMode) -> String)? = null

    @Volatile
    private var documentStartSupported = false

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val _fullscreenView = mutableStateOf<View?>(null)
    var fullscreenView: View?
        get() = _fullscreenView.value
        private set(value) { _fullscreenView.value = value }

    val isFullscreen: Boolean
        get() = customView != null

    private inner class PlaybackBridge {
        @JavascriptInterface
        fun onPlaybackStateChanged(
            playing: Boolean,
            title: String,
            duration: Double,
            currentTime: Double,
            artUrl: String?,
            canNext: Boolean,
            canPrev: Boolean
        ) {
            onPlaybackUpdate?.invoke(playing, title, duration, currentTime, artUrl, canNext, canPrev)
        }
    }

    fun getOrCreateWebView(mode: BrowserMode, context: Context): WebView {
        return when (mode) {
            BrowserMode.YOUTUBE -> webView ?: createYoutubeWebView(context)
            BrowserMode.MOVIES -> moviesWebView ?: createMoviesWebView(context)
            BrowserMode.ANIME -> animeWebView ?: createAnimeWebView(context)
        }
    }

    private fun createYoutubeWebView(context: Context): WebView {
        val wv = MediaWebView(context)
        configureWebView(wv, BrowserMode.YOUTUBE)
        webView = wv
        val initial = pendingYoutubeUrl ?: Constants.YOUTUBE_HOME
        pendingYoutubeUrl = null
        wv.loadUrl(initial)
        return wv
    }

    private fun createMoviesWebView(context: Context): WebView {
        val wv = createProfiledWebView(BrowserMode.MOVIES, Constants.MOVIE_PROFILE_NAME, context)
        moviesWebView = wv
        val initial = pendingMoviesUrl
            ?: homeResolver?.invoke(BrowserMode.MOVIES)
            ?: Constants.MOVIES_HOME
        pendingMoviesUrl = null
        wv.loadUrl(initial)
        return wv
    }

    private fun createAnimeWebView(context: Context): WebView {
        val wv = createProfiledWebView(BrowserMode.ANIME, Constants.ANIME_PROFILE_NAME, context)
        animeWebView = wv
        val initial = pendingAnimeUrl
            ?: homeResolver?.invoke(BrowserMode.ANIME)
            ?: Constants.ANIME_HOME
        pendingAnimeUrl = null
        wv.loadUrl(initial)
        return wv
    }

    @OptIn(WebViewBuilder.Experimental::class)
    private fun createProfiledWebView(mode: BrowserMode, profileName: String, context: Context): WebView {
        val profile = ensureProfile(profileName, mode)
        val wv: WebView = if (profile != null &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEBVIEW_BUILDER_EXPERIMENTAL_V1)
        ) {
            try {
                WebViewBuilder(WebViewBuilder.PRESET_LEGACY)
                    .setProfile(profileName)
                    .build(context)
            } catch (_: Exception) {
                profiles.remove(mode)
                MediaWebView(context)
            }
        } else {
            MediaWebView(context)
        }
        configureWebView(wv, mode)
        return wv
    }

    private fun ensureProfile(profileName: String, mode: BrowserMode): Profile? {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return null
        return try {
            ProfileStore.getInstance().getOrCreateProfile(profileName).also { profiles[mode] = it }
        } catch (_: Exception) {
            null
        }
    }

    private fun configureWebView(wv: WebView, mode: BrowserMode) {
        val docStartScript = runCatching {
            documentStartScriptProvider?.invoke(mode) ?: UblockScriptlets.getDocumentStartJs()
        }.getOrElse { UblockScriptlets.getDocumentStartJs() }
        wv.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = false
                setSupportZoom(false)
                mediaPlaybackRequiresUserGesture = false
                userAgentString = WebSettings.getDefaultUserAgent(wv.context)
                    .replace("; wv", "")
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                if (mode != BrowserMode.YOUTUBE) {
                    // Kill popups/popunders: no automatic windows, and new-window
                    // requests are rejected in onCreateWindow below.
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    isAlgorithmicDarkeningAllowed = false
                }
            }
            if (mode == BrowserMode.YOUTUBE) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            } else {
                profiles[mode]?.cookieManager?.setAcceptThirdPartyCookies(this, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
            }
            addJavascriptInterface(PlaybackBridge(), "Android")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (mode == BrowserMode.YOUTUBE) {
                        if (NavigationBlocker.shouldAllowNavigation(url, allowAll = false)) {
                            return false
                        }
                        onNavigationBlocked?.invoke(url)
                        return true
                    }
                    // Movies/anime mirrors rotate across hosts, so allow in-site
                    // and known provider navigation but cancel ad/tracker targets.
                    val pageHost = runCatching { java.net.URI(view.url ?: "") }.getOrNull()?.host
                    if (shouldBlockNavigation?.invoke(url, pageHost) == true) {
                        onNavigationBlocked?.invoke(url)
                        return true
                    }
                    return false
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    if (networkBlocker?.invoke(url) == true) {
                        return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                    blockAdDomain(url)?.let { return it }
                    if (isYoutubeApi(url)) {
                        interceptYoutubeApi(request)?.let { return it }
                    }
                    return shouldIntercept?.invoke(url)
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url != null) {
                        _currentUrl.value = url
                    }
                    _isLoading.value = true
                    // Fallback when the WebView can't run true document-start
                    // scripts: inject as early as the page lifecycle allows.
                    if (!documentStartSupported) {
                        runCatching { view.evaluateJavascript(docStartScript, null) }
                    }
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null) _currentUrl.value = url
                    if (url != null && redirectIfDeadMirror(view, mode, url)) return
                    _isLoading.value = false
                    _canGoBack.value = view.canGoBack()
                    _canGoForward.value = view.canGoForward()
                    onPageLoaded?.invoke(_currentUrl.value, _pageTitle.value)
                    if (url != null && url.startsWith("http")) {
                        onPersistablePage?.invoke(mode, url)
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    if (url != null) _currentUrl.value = url
                    _canGoBack.value = view.canGoBack()
                    _canGoForward.value = view.canGoForward()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    _progress.value = newProgress
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    _pageTitle.value = title ?: "MyTube"
                }

                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    customViewCallback?.onCustomViewHidden()
                    customView = view
                    customViewCallback = callback
                    _fullscreenView.value = view
                }

                override fun onHideCustomView() {
                    customViewCallback?.onCustomViewHidden()
                    customView = null
                    customViewCallback = null
                    _fullscreenView.value = null
                }

                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message
                ): Boolean {
                    // Reject every popup/new-window request on non-YouTube tabs.
                    return false
                }
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(wv, docStartScript, setOf("*"))
                documentStartSupported = true
            } catch (e: Exception) {
                documentStartSupported = false
                Log.w(TAG, "addDocumentStartJavaScript failed: ${e.message}")
            }
        } else {
            documentStartSupported = false
            Log.w(TAG, "DOCUMENT_START_SCRIPT unsupported; using onPageStarted fallback")
        }
    }

    fun hideCustomView() {
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        _fullscreenView.value = null
    }

    companion object {
        private const val TAG = "WebViewManager"
        private val YOUTUBE_API_PATHS = listOf(
            "/youtubei/v1/player",
            "/youtubei/v1/browse",
            "/youtubei/v1/search",
            "/youtubei/v1/next",
        )
    }

    private fun isYoutubeApi(url: String): Boolean =
        YOUTUBE_API_PATHS.any { url.contains(it) }

    /**
     * Reads a request body via the (hidden) `getRequestBody()` accessor. Returns
     * null when unavailable, so callers can fall back to normal loading.
     */
    private fun readRequestBody(request: WebResourceRequest): InputStream? {
        val candidates = listOf(request.javaClass, WebResourceRequest::class.java)
        for (cls in candidates) {
            try {
                val m = cls.getMethod("getRequestBody")
                return m.invoke(request) as? InputStream
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * The movie/anime mirrors rotate and get seized constantly. When a page loads
     * on a known-dead host, bounce to the currently resolved home instead of
     * showing the mirror's own "Page not found" route.
     */
    private fun redirectIfDeadMirror(view: WebView, mode: BrowserMode, url: String): Boolean {
        if (mode == BrowserMode.YOUTUBE) return false
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
        if (host !in Constants.DEAD_MOVIE_HOSTS) return false
        val home = homeResolver?.invoke(mode)?.takeIf { it.isNotBlank() } ?: return false
        if (url.startsWith(home)) return false
        _isLoading.value = false
        view.loadUrl(home)
        return true
    }

    /**
     * Fetch the `/youtubei/` response on the native side and strip ad keys from
     * the JSON before the page sees it. Never returns an empty body (that breaks
     * playback) — on any anomaly we return null and let the WebView load it.
     */
    private fun interceptYoutubeApi(request: WebResourceRequest): WebResourceResponse? {
        val isPost = request.method.equals("POST", ignoreCase = true)
        // The request body isn't part of the public WebResourceRequest API. If we
        // can't read it, skip interception rather than replaying an empty POST
        // (which would fail and force a duplicate request).
        val requestBody = if (isPost) readRequestBody(request) else null
        if (isPost && requestBody == null) return null
        return try {
            val url = request.url.toString()
            val conn = (URL(url).openConnection() as? HttpURLConnection) ?: return null
            conn.requestMethod = request.method
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            for ((k, v) in request.requestHeaders) {
                // Let HttpURLConnection manage encoding/length so the body we
                // read is already decoded and safe to re-serve.
                if (k.equals("Host", ignoreCase = true)) continue
                if (k.equals("Accept-Encoding", ignoreCase = true)) continue
                if (k.equals("Content-Length", ignoreCase = true)) continue
                if (k.equals("Connection", ignoreCase = true)) continue
                conn.setRequestProperty(k, v)
            }
            conn.setRequestProperty("User-Agent", webView?.settings?.userAgentString ?: "Mozilla/5.0")
            CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
            if (requestBody != null) {
                conn.doOutput = true
                requestBody.copyTo(conn.outputStream)
            }
            val code = conn.responseCode
            if (code !in 200..299) return null
            val contentType = conn.contentType ?: return null
            if (!contentType.contains("json", ignoreCase = true)) return null
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            val stripped = stripAdKeys(raw)
            if (stripped == null) return null
            val mime = contentType.substringBefore(';').trim()
            val headers = conn.headerFields
                ?.filterKeys { it != null }
                ?.mapKeys { it.key!! }
                ?.filterKeys {
                    !it.equals("Content-Encoding", ignoreCase = true) &&
                        !it.equals("Content-Length", ignoreCase = true) &&
                        !it.equals("Transfer-Encoding", ignoreCase = true)
                }
                ?.mapValues { it.value.joinToString(", ") }
                ?: emptyMap()
            WebResourceResponse(mime, "utf-8", code, "OK", headers, stripped.byteInputStream(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Youtube API intercept failed: ${e.message}")
            null
        }
    }

    /** Returns null when nothing changed so callers can pass the response through. */
    private fun stripAdKeys(json: String): String? {
        return try {
            val obj = JSONObject(json)
            if (pruneJson(obj)) obj.toString() else null
        } catch (e: Exception) {
            Log.w(TAG, "stripAdKeys failed: ${e.message}")
            null
        }
    }

    private fun pruneJson(node: Any?): Boolean {
        var changed = false
        when (node) {
            is JSONObject -> {
                val keys = node.keys().asSequence().toList()
                for (k in keys) {
                    if (k in UblockScriptlets.AD_KEYS) {
                        node.remove(k)
                        changed = true
                    } else if (pruneJson(node.opt(k))) {
                        changed = true
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    if (pruneJson(node.opt(i))) changed = true
                }
            }
        }
        return changed
    }

    private fun blockAdDomain(url: String): WebResourceResponse? {
        val host = kotlin.runCatching { java.net.URI(url).host }.getOrNull() ?: return null
        val adDomains = listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "pagead2.googlesyndication.com"
        )
        if (adDomains.any { host.contains(it, ignoreCase = true) }) {
            android.util.Log.d(TAG, "Blocked ad domain: $url")
            return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
        return null
    }

    fun loadUrl(url: String) {
        activeWebView()?.loadUrl(url)
    }

    fun goBack(): Boolean {
        return activeWebView()?.let {
            if (it.canGoBack()) {
                it.goBack()
                true
            } else false
        } ?: false
    }

    fun goForward(): Boolean {
        return activeWebView()?.let {
            if (it.canGoForward()) {
                it.goForward()
                true
            } else false
        } ?: false
    }

    fun reload() {
        activeWebView()?.reload()
    }

    fun scrollVideoIntoView() {
        evaluateJs(
            "(function(){var v=document.querySelector('video');" +
                "if(v){v.scrollIntoView({block:'center',inline:'center'});}})()"
        )
    }

    private var videoOnlyMode = false

    /**
     * While in PiP, strip the page down to just the video player so the
     * PiP window doesn't show the page's responsive sidebar/header layout.
     */
    fun setVideoOnlyMode(enabled: Boolean) {
        if (videoOnlyMode == enabled) return
        videoOnlyMode = enabled
        if (enabled) {
            evaluateJs(
                "(function(){if(!document.querySelector('video'))return;" +
                    "var s=document.getElementById('mytube-pip-video-only');" +
                    "if(!s){s=document.createElement('style');" +
                    "s.id='mytube-pip-video-only';" +
                    "document.documentElement.appendChild(s);}" +
                    "s.textContent='html,body{background:#000!important;overflow:hidden!important}" +
                    "body *{visibility:hidden!important}" +
                    "video,.html5-video-player,.html5-video-player *{visibility:visible!important}" +
                    ".html5-video-player{position:fixed!important;top:0!important;left:0!important;" +
                    "width:100vw!important;height:100vh!important;z-index:2147483647!important;" +
                    "background:#000!important}';})()"
            )
        } else {
            evaluateJs(
                "(function(){var s=document.getElementById('mytube-pip-video-only');" +
                    "if(s&&s.parentNode){s.parentNode.removeChild(s);}})()"
            )
        }
    }

    fun evaluateJs(script: String) {
        activeWebView()?.evaluateJavascript(script, null)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun evaluateJsFromMainThread(script: String) {
        mainHandler.post { activeWebView()?.evaluateJavascript(script, null) }
    }

    fun destroy() {
        listOf(webView, moviesWebView, animeWebView).forEach { wv ->
            wv?.apply {
                stopLoading()
                destroy()
            }
        }
        webView = null
        moviesWebView = null
        animeWebView = null
    }
}
