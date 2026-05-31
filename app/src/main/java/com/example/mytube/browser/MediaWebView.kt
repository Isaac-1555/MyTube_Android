package com.example.mytube.browser

import android.content.Context
import android.view.View
import android.webkit.WebView

class MediaWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != View.GONE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        }
    }
}
