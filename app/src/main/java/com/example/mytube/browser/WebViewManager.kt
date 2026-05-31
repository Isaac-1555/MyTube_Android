package com.example.mytube.browser

import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import com.example.mytube.adblock.UblockScriptlets
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    isAlgorithmicDarkeningAllowed = true
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
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
                    if (url.contains("/youtubei/v1/")) {
                        return interceptYoutubeApi(request)
                    }
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

    private fun interceptYoutubeApi(request: WebResourceRequest): WebResourceResponse? {
        return try {
            val conn = URL(request.url.toString()).openConnection() as HttpURLConnection
            conn.requestMethod = request.method
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            for ((k, v) in request.requestHeaders) {
                if (k.equals("Host", ignoreCase = true)) continue
                conn.setRequestProperty(k, v)
            }
            conn.setRequestProperty("User-Agent", webView?.settings?.userAgentString ?: "Mozilla/5.0")
            val cookies = android.webkit.CookieManager.getInstance().getCookie(request.url.toString())
            if (cookies != null) conn.setRequestProperty("Cookie", cookies)
            if (request.method.equals("POST", ignoreCase = true)) {
                val body = try {
                    val m = android.webkit.WebResourceRequest::class.java.getMethod("getRequestBody")
                    m.invoke(request) as? java.io.InputStream
                } catch (_: Exception) { null }
                if (body != null) {
                    conn.doOutput = true
                    body.copyTo(conn.outputStream)
                }
            }
            val statusCode = conn.responseCode
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val ct = conn.contentType ?: "application/json"
            val mime = ct.split(";").firstOrNull()?.trim() ?: "application/json"
            val encoding = "utf-8"
            val stripped = stripAdFields(body)
            val respHeaders = conn.headerFields
                ?.filterKeys { it != null }
                ?.mapKeys { it.key!! }
                ?.mapValues { it.value.joinToString(", ") }
                ?: emptyMap()
            WebResourceResponse(mime, encoding, statusCode, "OK", respHeaders, stripped.byteInputStream(Charsets.UTF_8))
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Youtube API intercept failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "WebViewManager"
        private val AD_KEYS = listOf("playerAds", "adPlacements", "adSlots", "adBreak", "adBreaks")
    }

    private fun stripAdFields(json: String): String {
        return try {
            val obj = JSONObject(json)
            var changed = false
            for (key in AD_KEYS) {
                if (obj.has(key)) {
                    obj.remove(key)
                    changed = true
                }
            }
            if (obj.has("playerResponse")) {
                val pr = obj.opt("playerResponse")
                if (pr is JSONObject) {
                    for (key in AD_KEYS) {
                        if (pr.has(key)) {
                            pr.remove(key)
                            changed = true
                        }
                    }
                    if (changed) obj.put("playerResponse", pr)
                }
            }
            if (changed) obj.toString() else json
        } catch (e: Exception) {
            android.util.Log.w(TAG, "stripAdFields failed: ${e.message}")
            json
        }
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

    fun evaluateJsFromMainThread(script: String) {
        webView?.post { webView?.evaluateJavascript(script, null) }
    }

    fun destroy() {
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
    }
}
