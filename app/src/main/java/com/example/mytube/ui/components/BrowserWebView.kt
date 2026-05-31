package com.example.mytube.ui.components

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mytube.browser.WebViewManager

@Composable
fun BrowserWebView(
    webViewManager: WebViewManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webView = remember {
        webViewManager.webView ?: run {
            val view = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            webViewManager.attachWebView(view)
            webViewManager.loadUrl(webViewManager.currentUrl)
            view
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose { }
    }
}
