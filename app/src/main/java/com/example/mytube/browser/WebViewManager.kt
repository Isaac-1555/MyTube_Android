package com.example.mytube.browser

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import com.example.mytube.adblock.UblockScriptlets
import java.io.ByteArrayInputStream


class WebViewManager {
    var webView: WebView? = null
        private set

    private val _currentUrl = mutableStateOf("https://www.youtube.com")
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
    var onNavigationBlocked: ((String) -> Unit)? = null
    var shouldIntercept: ((String) -> WebResourceResponse?)? = null
    var onPlaybackUpdate: ((Boolean, String, Double, Double) -> Unit)? = null
    var networkBlocker: ((String) -> Boolean)? = null

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
        fun onPlaybackStateChanged(playing: Boolean, title: String, duration: Double, currentTime: Double) {
            onPlaybackUpdate?.invoke(playing, title, duration, currentTime)
        }
    }

    fun attachWebView(wv: WebView) {
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    isAlgorithmicDarkeningAllowed = false
                }
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
            }
            addJavascriptInterface(PlaybackBridge(), "Android")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (NavigationBlocker.shouldAllowNavigation(url)) {
                        return false
                    }
                    onNavigationBlocked?.invoke(url)
                    return true
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    if (networkBlocker?.invoke(url) == true) {
                        return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                    blockAdDomain(url)?.let { return it }
                    return shouldIntercept?.invoke(url)
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url != null) {
                        _currentUrl.value = url
                    }
                    _isLoading.value = true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null) _currentUrl.value = url
                    _isLoading.value = false
                    _canGoBack.value = view.canGoBack()
                    _canGoForward.value = view.canGoForward()
                    onPageLoaded?.invoke(_currentUrl.value, _pageTitle.value)
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
            }
        }
        try {
            WebViewCompat.addDocumentStartJavaScript(
                wv,
                UblockScriptlets.getDocumentStartJs(),
                setOf("*")
            )
        } catch (_: Exception) { }
        webView = wv
    }

    fun hideCustomView() {
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        _fullscreenView.value = null
    }

    companion object {
        private const val TAG = "WebViewManager"
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
        webView?.loadUrl(url)
    }

    fun goBack(): Boolean {
        return webView?.let {
            if (it.canGoBack()) {
                it.goBack()
                true
            } else false
        } ?: false
    }

    fun goForward(): Boolean {
        return webView?.let {
            if (it.canGoForward()) {
                it.goForward()
                true
            } else false
        } ?: false
    }

    fun reload() {
        webView?.reload()
    }

    fun evaluateJs(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    fun evaluateJsFromMainThread(script: String) {
        mainHandler.post { webView?.evaluateJavascript(script, null) }
    }

    fun destroy() {
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
    }
}
