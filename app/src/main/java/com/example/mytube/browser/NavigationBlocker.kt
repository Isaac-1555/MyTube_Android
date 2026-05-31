package com.example.mytube.browser

import android.net.Uri
import com.example.mytube.util.Constants

object NavigationBlocker {
    private val allowedHosts = setOf(
        Constants.YOUTUBE_HOST,
        Constants.YOUTUBE_WWW_HOST,
        Constants.YOUTUBE_M_HOST,
        Constants.YOUTUBE_SHORT,
        "accounts.google.com",
        "accounts.youtube.com",
    )

    fun shouldAllowNavigation(url: String): Boolean {
        val uri = Uri.parse(url)
        return allowedHosts.contains(uri.host)
    }
}
