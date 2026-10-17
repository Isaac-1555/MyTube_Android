package com.example.mytube.browser

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private const val TAG = "MyTubeFullscreen"

/** Re-hide the bars after the orientation relayout has had time to revert them. */
private const val SETTLE_DELAY_MS = 150L

/** Grace window in which a revealed bar is left alone so a swipe can be peeked at. */
private const val PEEK_GRACE_MS = 1_200L

/** How often the guard checks whether a system bar has come back. */
private const val GUARD_INTERVAL_MS = 250L

/** How long after entry to make the first auto-promote attempt. */
private const val AUTO_PROMOTE_DELAY_MS = 500L

/**
 * How often to retry, and for how long. YouTube rebuilds its player around a rotation
 * and the control overlay holding the fullscreen button does not always exist by the
 * time we look for it, so a single attempt timed out roughly one time in three on
 * device. One attempt is not enough; a fixed delay long enough to always work would
 * mean a visible stall on every entry.
 */
private const val AUTO_PROMOTE_RETRY_MS = 400L
private const val AUTO_PROMOTE_DEADLINE_MS = 2_500L

/**
 * Owns the native half of video fullscreen: orientation, system bars, cutout.
 *
 * The rule this class exists to enforce: the decision to go fullscreen is never
 * taken by a web API. `screen.orientation.lock()` throws outside real fullscreen and
 * `requestFullscreen()` is gated on transient user activation, so a rotation-triggered
 * call from JavaScript succeeds only by accident — which is why the previous attempt
 * worked roughly one time in a hundred. Orientation and bars are decided here; the
 * page is only asked to grow its player element.
 *
 * Several deliberately deprecated APIs are used here (FLAG_FULLSCREEN,
 * isStatusBarContrastEnforced, systemUiVisibility) purely as diagnostics and as a
 * fallback for hide(statusBars()) failing to stick. Deprecated is not the same as
 * removed: all three still work on Android 15.
 */
class VideoFullscreenController(
    private val activity: Activity,
    private val webViewManager: WebViewManager
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** True while either layer is engaged. Compose-observable. */
    var isFullscreenActive by mutableStateOf(false)
        private set

    /**
     * Whether the chrome-hiding stylesheet is applied. True for the whole of native
     * fullscreen too, because YouTube's masthead reappears if it is dropped, so this
     * is not a proxy for "our own UI is on screen".
     */
    var isCssFullscreen by mutableStateOf(false)
        private set

    var isNativeFullscreen by mutableStateOf(false)
        private set

    private var lastWasLandscape = false

    /** One auto-promote attempt per entry, so a recomposition cannot re-tap. */
    private var autoPromoteDone = false

    /**
     * Set when the user leaves fullscreen by hand while still in landscape. Cleared on
     * the next orientation flip. Without it, dismissing fullscreen just handed them
     * straight into the other kind of fullscreen.
     */
    private var dismissedUntilFlip = false

    /** True while we are tearing fullscreen down ourselves, see [exitAll]. */
    private var tearingDown = false

    private var barsGuard: Runnable? = null
    private var lastForcedHideAt = 0L

    private val fsMode get() = BrowserMode.YOUTUBE

    fun apply(decision: FullscreenDecision, isLandscape: Boolean) {
        if (isLandscape != lastWasLandscape) {
            dismissedUntilFlip = false
            lastWasLandscape = isLandscape
        }
        when (decision) {
            FullscreenDecision.Stay -> Unit
            FullscreenDecision.Exit -> exitAll()
            FullscreenDecision.Enter -> enter()
        }
    }

    // ---- enter / exit ---------------------------------------------------------

    private fun enter() {
        if (dismissedUntilFlip) {
            Log.d(TAG, "enter: suppressed, dismissed this orientation")
            return
        }

        pinWindowForVideo()

        if (isCssFullscreen || isNativeFullscreen) {
            if (isCssFullscreen) webViewManager.setFullscreenCss(fsMode, true)
            return
        }

        applyCss()
    }

    private fun applyCss() {
        isCssFullscreen = true
        isFullscreenActive = true
        webViewManager.setFullscreenCss(fsMode, true)
        Log.d(TAG, "enter: css fullscreen")
        attemptAutoPromote()
    }

    /**
     * Taps YouTube's own fullscreen button so YouTube runs its own fullscreen path.
     *
     * The CSS layer is only a layout pin. On its own it leaves the player wearing its
     * inline chrome, which on the mobile-web player means no control bar and no seek
     * bar at all: those live in #player-control-overlay, a sibling of #movie_player
     * that the pin deliberately covers.
     *
     * So the pin is only the instant before, and the real event is the tap. It has to
     * be a real touch: requestFullscreen() demands transient user activation, which
     * evaluateJavascript cannot manufacture and which no synthetic DOM event grants,
     * because Blink only counts input arriving over the real input pipeline. Once the
     * tap lands, YouTube takes over and renders everything it normally would.
     */
    private fun attemptAutoPromote() {
        if (autoPromoteDone) return
        autoPromoteDone = true
        mainHandler.postDelayed({ promoteUntil(SystemClock.uptimeMillis() + AUTO_PROMOTE_DEADLINE_MS) }, AUTO_PROMOTE_DELAY_MS)
    }

    /**
     * Retried until the deadline rather than fired once. YouTube rebuilds the player
     * around a rotation and the control overlay holding the fullscreen button is not
     * always there yet when we first look, which cost roughly one attempt in three on
     * device. [onAttempted] reports whether a tap was actually dispatched, because the
     * button lookup is asynchronous and only it knows the answer.
     */
    private fun promoteUntil(deadline: Long) {
        if (!isFullscreenActive || isNativeFullscreen) return
        if (SystemClock.uptimeMillis() >= deadline) {
            Log.w(TAG, "auto-promote: gave up after ${AUTO_PROMOTE_DEADLINE_MS}ms; staying on css")
            logBars("promote-timeout")
            return
        }
        webViewManager.tapFullscreenButton(fsMode) { tapped ->
            if (tapped) return@tapFullscreenButton
            if (!isFullscreenActive || isNativeFullscreen) return@tapFullscreenButton
            mainHandler.postDelayed({ promoteUntil(deadline) }, AUTO_PROMOTE_RETRY_MS)
        }
    }

    private fun exitAll() {
        autoPromoteDone = false

        // hideCustomView() re-enters through onNativeFullscreenHidden(). Mark the
        // teardown so a dismissal latch is only recorded for exits the user actually
        // performed on screen, not for ours.
        tearingDown = true
        try {
            if (isNativeFullscreen) {
                webViewManager.exitNativeFullscreen(fsMode)
                webViewManager.hideCustomView()
                isNativeFullscreen = false
            }

            if (isCssFullscreen) {
                isCssFullscreen = false
                webViewManager.setFullscreenCss(fsMode, false)
            }
        } finally {
            tearingDown = false
        }

        isFullscreenActive = false
        restoreWindow()
        logBars("exit")
    }

    /** Back button, or any explicit user exit. Must not immediately re-engage. */
    fun exitByUser() {
        dismissedUntilFlip = true
        exitAll()
    }

    /** Activity stopped, PiP entered, tab left: unconditional teardown. */
    fun reset() {
        dismissedUntilFlip = false
        exitAll()
    }

    // ---- window state ---------------------------------------------------------

    /**
     * Hide the system bars and keep them hidden.
     *
     * Ordering matters and was the original bug: the orientation request used to be
     * posted to the decor queue *before* hide() ran, so `setRequestedOrientation` was
     * the last thing to touch the window. It relayouts, Android re-showed the status
     * bar, and because the device was already in landscape the requested value matched
     * the current one, so no configuration change was dispatched and the re-assert
     * hooks never fired. Orientation first, bars after, then again once it settles.
     */
    @Suppress("DEPRECATION")
    private fun pinWindowForVideo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Keeps the status/cutout strip from being scrimmed over the video.
            activity.window.isStatusBarContrastEnforced = false
        }
        // Belt and braces for the status bar: hide(statusBars()) was not sticking on
        // at least one device, and FLAG_FULLSCREEN is the older path that still is
        // honoured on Android 15.
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        startBarsGuard()
        hideSystemBars(force = true)

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val decor = activity.window.decorView
        decor.post { hideSystemBars() }
        decor.postDelayed({ hideSystemBars() }, SETTLE_DELAY_MS)
        decor.postDelayed({ logBars("settled") }, SETTLE_DELAY_MS + SETTLE_DELAY_MS)

        logBars("enter")
    }

    @Suppress("DEPRECATION")
    private fun restoreWindow() {
        stopBarsGuard()
        activity.window.decorView.post {
            // Matches the manifest, which declares no screenOrientation, so this
            // hands control back to the user's rotation-lock setting.
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isStatusBarContrastEnforced = true
        }
        insetsController().show(WindowInsetsCompat.Type.systemBars())
    }

    private fun insetsController(): WindowInsetsControllerCompat =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)

    private fun hideSystemBars(force: Boolean = false) {
        if (!isFullscreenActive && !force) return
        val controller = insetsController()
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    // ---- bars guard -----------------------------------------------------------

    /**
     * Re-hides the bars whenever something shows them again.
     *
     * Rotation, focus changes and the cutout pass can all re-show system bars, and
     * listening for configuration changes does not catch them: the Activity is never
     * recreated and, when the requested orientation already matches the current one,
     * no configuration change is dispatched at all. Polled rather than hooked into
     * [android.view.View.setOnApplyWindowInsetsListener] because there is no way to
     * read back a previous listener, and clobbering one would break insets for the
     * rest of the session.
     *
     * Only acts while a bar is actually visible, so it is a no-op almost every tick.
     * The grace period leaves a deliberate swipe-to-peek alone briefly.
     */
    private fun startBarsGuard() {
        if (barsGuard != null) return
        val runnable = object : Runnable {
            override fun run() {
                barsGuard = null
                if (!isFullscreenActive) return
                // ViewCompat, not decorView.rootWindowInsets: the platform
                // WindowInsets.isVisible(int) needs API 30 and this app supports 24.
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                val barsVisible = insets?.isVisible(WindowInsetsCompat.Type.systemBars()) == true
                if (barsVisible &&
                    SystemClock.uptimeMillis() - lastForcedHideAt >= PEEK_GRACE_MS
                ) {
                    lastForcedHideAt = SystemClock.uptimeMillis()
                    hideSystemBars()
                    logBars("guard")
                }
                barsGuard = this
                mainHandler.postDelayed(this, GUARD_INTERVAL_MS)
            }
        }
        barsGuard = runnable
        mainHandler.postDelayed(runnable, GUARD_INTERVAL_MS)
    }

    private fun stopBarsGuard() {
        barsGuard?.let { mainHandler.removeCallbacks(it) }
        barsGuard = null
    }

    // ---- diagnostics ----------------------------------------------------------

    /**
     * Reports what the window actually thinks, so this can be measured instead of
     * guessed at. Everything needed to interpret a failure is on one line.
     */
    @Suppress("DEPRECATION")
    private fun logBars(whenTag: String) {
        val decor = activity.window.decorView
        val insets = ViewCompat.getRootWindowInsets(decor)
        val flags = decor.systemUiVisibility
        Log.d(
            TAG,
            "bars[$whenTag] " +
                "active=$isFullscreenActive css=$isCssFullscreen native=$isNativeFullscreen " +
                "statusVisible=${insets?.isVisible(WindowInsetsCompat.Type.statusBars())} " +
                "navVisible=${insets?.isVisible(WindowInsetsCompat.Type.navigationBars())} " +
                "windowVis=${decor.windowVisibility} " +
                "flagFullscreen=${(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0} " +
                "sysUiVis=$flags " +
                "screenW=${activity.resources.displayMetrics.widthPixels} " +
                "screenH=${activity.resources.displayMetrics.heightPixels}"
        )
    }

    /**
     * Some OEM skins and rotation passes drop the hidden-bar state. Re-assert it
     * without re-running any of the entry logic.
     */
    fun reassertIfActive() {
        if (!isFullscreenActive) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        webViewManager.fullscreenView?.requestLayout()
        hideSystemBars()
        if (isCssFullscreen) {
            webViewManager.setFullscreenCss(fsMode, true)
        }
        logBars("reassert")
    }

    // ---- callbacks from WebViewManager ---------------------------------------

    fun onNativeFullscreenShown() {
        isNativeFullscreen = true
        Log.d(TAG, "enter: native fullscreen (after auto-promote=$autoPromoteDone)")
        // Keep the chrome-hiding CSS on. Native fullscreen here is usually
        // documentElement.requestFullscreen(), which renders the whole page, so
        // without the stylesheet YouTube's masthead stays on screen.
        isCssFullscreen = true
        isFullscreenActive = true
        webViewManager.setFullscreenCss(fsMode, true)
        pinWindowForVideo()
        val fv = webViewManager.fullscreenView
        Log.d(
            TAG,
            "enter: native fullscreen view=${fv?.javaClass?.simpleName} " +
                "size=${fv?.width}x${fv?.height} visible=${fv?.isShown}"
        )
    }

    fun onNativeFullscreenHidden() {
        isNativeFullscreen = false
        if (isFullscreenActive) {
            isFullscreenActive = false
            restoreWindow()
        }
        // They exited by hand. The resolver resolves to Enter again on the very next
        // recomposition, so latch it or we drag them straight back in.
        if (lastWasLandscape && !tearingDown) dismissedUntilFlip = true
        Log.d(TAG, "exit: native fullscreen")
    }
}