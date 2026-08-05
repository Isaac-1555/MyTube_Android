package com.example.mytube.browser

import android.content.Context
import android.webkit.WebView

class MediaWebView(context: Context) : WebView(context) {
    override fun onDetachedFromWindow() {
        try {
            super.onDetachedFromWindow()
        } catch (_: Exception) {
        }
    }
}
