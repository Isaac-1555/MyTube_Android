package com.example.mytube.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
    key(webViewManager.activeMode) {
        AndroidView(
            factory = {
                webViewManager.getOrCreateWebView(webViewManager.activeMode, context)
            },
            modifier = modifier
        )
    }
}
