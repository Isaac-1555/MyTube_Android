package com.example.mytube.browser

import android.content.Context
import android.view.View
import android.webkit.WebView

class MediaWebView(context: Context) : WebView(context) {
    /**
     * Whether the page has to be reported to Chromium as visible.
     *
     * Chromium reads the page's visibility from [onVisibilityAggregated], and the
     * HTML standard makes the user agent pause the media when the page goes
     * hidden. The window goes away when the app is put in the background, and
     * the media was paused behind the app's back: the app resumed it, the
     * browser paused it again, and the two fought over it - the notification
     * flipped between pause and play, the audio was restarted on every change,
     * and the picture kept re-buffering.
     *
     * The app plays in the background on purpose, so the page is reported as
     * visible while the playback is meant to run. The override of
     * [onWindowVisibilityChanged] below was trying to do the same thing, but
     * Chromium has read the aggregated visibility instead since Android 7.
     */
    var keepPageVisible: Boolean = false

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(keepPageVisible || isVisible)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != View.GONE) {
            super.onWindowVisibilityChanged(View.VISIBLE)
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(true)
    }

    override fun onPause() {
    }

    override fun onResume() {
    }

    override fun onDetachedFromWindow() {
        try {
            super.onDetachedFromWindow()
        } catch (_: Exception) {
        }
    }
}
