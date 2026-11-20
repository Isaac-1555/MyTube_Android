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
import org.json.JSONObject

private const val TAG = "MyTubeFullscreen"

/** Re-hide the bars after the orientation relayout has had time to revert them. */
private const val SETTLE_DELAY_MS = 150L

/** Grace window in which a revealed bar is left alone so a swipe can be peeked at. */
private const val PEEK_GRACE_MS = 1_200L

/** How often the guard checks whether a system bar has come back. */
private const val GUARD_INTERVAL_MS = 250L

/** How long the page is given to leave fullscreen before it is forced out. */
private const val NATIVE_EXIT_TIMEOUT_MS = 600L

/**
 * How long to wait before asking the page to lay out again.
 *
 * The rotation back to portrait is not over when the orientation is requested:
 * the window is resized a moment later, and the page has to be told about it.
 */
private const val VIEWPORT_REFRESH_DELAY_MS = 400L

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
 * How often, and for how long, to ask the page which element it put into
 * fullscreen after the custom view is handed over.
 *
 * `document.fullscreenElement` is still null at the moment
 * [android.webkit.WebChromeClient.onShowCustomView] runs. On device it becomes
 * #player-container-id about 200ms later, so a single immediate check always
 * answered "page" and kept the stylesheet pin on top of YouTube's own
 * fullscreen layout.
 */
private const val OWNER_RETRY_MS = 120L
private const val OWNER_DEADLINE_MS = 1_200L

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
     * Whether the chrome-hiding stylesheet is applied.
     *
     * True for the whole of native fullscreen when YouTube put the whole document
     * into it - the masthead reappears if the stylesheet is dropped.
     *
     * False when YouTube put the player itself into fullscreen: YouTube draws its
     * own control bar in that case, and the app's pin only moved that control bar
     * out of the visible area.
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

    /** True while the page has been asked to leave fullscreen but has not yet. */
    private var pendingNativeExit = false

    /** True while a layout refresh is owed to the page, see [scheduleViewportRefresh]. */
    private var viewportRefreshPending = false

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
        logModel("enter")
        attemptAutoPromote()
    }

    /**
     * Reports what the page thinks its player looks like.
     *
     * The player is rebuilt by YouTube between releases and the app's selectors
     * have to be checked against it, so this says what was actually found rather
     * than what was assumed.
     */
    private fun logModel(tag: String) {
        webViewManager.evaluateJsInFromMainThread(
            fsMode,
            "window.MyTubeFsModel && window.MyTubeFsModel()"
        ) { json ->
            Log.d(TAG, "model[$tag] $json")
        }
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
                // Ask the page to leave fullscreen and let it do so on its own
                // schedule. Forcing the custom view down straight away put the
                // picture back while the page still believed it was fullscreen,
                // and that is what left the video hanging off the screen after a
                // rotation back to portrait.
                webViewManager.exitNativeFullscreen(fsMode)
                isNativeFullscreen = false
                pendingNativeExit = true
                mainHandler.postDelayed(forceNativeExit, NATIVE_EXIT_TIMEOUT_MS)
            }

            if (isCssFullscreen) {
                isCssFullscreen = false
            }
            // Always told, not only when the flag says the stylesheet is on. The
            // flag and the page can disagree - a reload, a rotation, a mode that
            // was left behind - and a page that still carries the stylesheet
            // keeps the video pinned and YouTube's controls out of reach.
            webViewManager.setFullscreenCss(fsMode, false)
        } finally {
            tearingDown = false
        }

        isFullscreenActive = false
        restoreWindow()
        scheduleViewportRefresh()
        logBars("exit")
        logModel("exit")
    }

    /** The page did not leave fullscreen on its own. Take it down. */
    private val forceNativeExit = Runnable {
        if (!pendingNativeExit) return@Runnable
        pendingNativeExit = false
        webViewManager.hideCustomView()
    }

    /**
     * Asks the page to lay out for the size the window has now.
     *
     * Scheduled rather than done straight away: the rotation back to portrait is
     * requested here but happens a moment later, and the page has to be told
     * about the new size or it keeps the landscape picture.
     */
    private fun scheduleViewportRefresh() {
        viewportRefreshPending = true
        mainHandler.removeCallbacks(viewportRefreshRunnable)
        mainHandler.postDelayed(viewportRefreshRunnable, VIEWPORT_REFRESH_DELAY_MS)
    }

    private val viewportRefreshRunnable = Runnable {
        viewportRefreshPending = false
        webViewManager.refreshViewport(fsMode)
    }

    /**
     * The window changed shape. The page has to be told, or it keeps the layout
     * it had in the other orientation.
     */
    fun onConfigChanged() {
        if (viewportRefreshPending) {
            mainHandler.removeCallbacks(viewportRefreshRunnable)
            mainHandler.postDelayed(viewportRefreshRunnable, 120)
        }
        reassertIfActive()
    }

    /** Back button, or any explicit user exit. Must not immediately re-engage. */
    fun exitByUser() {
        dismissedUntilFlip = true
        exitAll()
    }

    /** Activity stopped, PiP entered, tab left: unconditional teardown. */
    fun reset() {
        dismissedUntilFlip = false
        if (!isFullscreenActive && !isCssFullscreen && !isNativeFullscreen && !pendingNativeExit) {
            // Nothing to tear down. Do not poke the page or the window: a reset
            // on PiP entry used to force a viewport refresh and a late reassert
            // right as the PiP window settled, which stalled playback.
            mainHandler.removeCallbacks(viewportRefreshRunnable)
            viewportRefreshPending = false
            autoPromoteDone = false
            return
        }
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
        isFullscreenActive = true
        pinWindowForVideo()
        val fv = webViewManager.fullscreenView
        Log.d(
            TAG,
            "enter: native fullscreen view=${fv?.javaClass?.simpleName} " +
                "size=${fv?.width}x${fv?.height} visible=${fv?.isShown}"
        )
        // What YouTube put into fullscreen decides who owns the layout.
        //
        // If the whole document is in fullscreen, the app's stylesheet still has
        // to strip the page around the video - without it YouTube's masthead
        // stays on screen.
        //
        // If the player is in fullscreen, YouTube draws its own controls and
        // puts them where they belong. The app's pin then only moves them
        // somewhere else: the control bar came up outside the visible area and
        // the tap on the gear fell through to the video and came out as a
        // play/pause. So the pin is released and YouTube is left alone.
        //
        // The answer is not available yet at this point. document.fullscreenElement
        // is still null when the custom view is handed over and only becomes
        // #player-container-id a moment later, so asking once always read it as
        // "page" and kept the pin. It is asked again until the page says.
        resolveFullscreenOwner(SystemClock.uptimeMillis() + OWNER_DEADLINE_MS, 0)
    }

    /**
     * Asks the page which element it actually put into fullscreen, with retries.
     *
     * A null or "not in fullscreen" answer means the page has not caught up
     * with the custom view yet; it is asked again shortly. At the deadline the
     * pin is left as it was, which is the state that worked before this question
     * existed.
     */
    private fun resolveFullscreenOwner(deadline: Long, attempt: Int) {
        if (!isNativeFullscreen) return
        webViewManager.evaluateJsInFromMainThread(
            fsMode,
            "window.MyTubeFsFullscreenElement && window.MyTubeFsFullscreenElement()"
        ) { raw ->
            if (!isNativeFullscreen) return@evaluateJsInFromMainThread
            val owner = parseFullscreenOwner(raw)
            if (owner == null || !owner.fullscreen) {
                if (SystemClock.uptimeMillis() < deadline) {
                    mainHandler.postDelayed(
                        { resolveFullscreenOwner(deadline, attempt + 1) },
                        OWNER_RETRY_MS
                    )
                } else {
                    Log.d(TAG, "enter: fullscreen owner unknown after $attempt tries; page pin kept")
                }
                return@evaluateJsInFromMainThread
            }
            if (owner.isPlayer) {
                isCssFullscreen = false
                webViewManager.setFullscreenCss(fsMode, false)
                Log.d(TAG, "enter: native fullscreen of the player; page pin released")
            } else {
                isCssFullscreen = true
                webViewManager.setFullscreenCss(fsMode, true)
                Log.d(TAG, "enter: native fullscreen of the page; page pin kept")
            }
        }
    }

    /** The answer from [video_state.js]'s MyTubeFsFullscreenElement. */
    private data class FullscreenOwner(
        val fullscreen: Boolean,
        val isPlayer: Boolean
    )

    /**
     * Anything that cannot be read is "not answered yet", so the caller retries
     * instead of deciding on a guess.
     */
    private fun parseFullscreenOwner(raw: String?): FullscreenOwner? {
        val body = raw?.trim().orEmpty()
        if (body.isEmpty() || body == "null" || body == "undefined") return null
        return runCatching {
            val obj = JSONObject(body)
            FullscreenOwner(
                fullscreen = obj.optBoolean("fullscreen", false),
                isPlayer = obj.optBoolean("isPlayer", false)
            )
        }.getOrNull()
    }

    fun onNativeFullscreenHidden() {
        // The page left fullscreen on its own. The forced exit is not needed.
        pendingNativeExit = false
        mainHandler.removeCallbacks(forceNativeExit)
        isNativeFullscreen = false
        if (isFullscreenActive) {
            isFullscreenActive = false
            restoreWindow()
        }
        // The page's own fullscreen is over, so the page pin has no owner left.
        // A pin left on here stripped the page in portrait and kept YouTube's
        // controls out of reach - the bug that made the video untappable after
        // leaving fullscreen. App-initiated teardown already did this in exitAll.
        if (!tearingDown) {
            isCssFullscreen = false
            webViewManager.setFullscreenCss(fsMode, false)
        }
        // They exited by hand. The resolver resolves to Enter again on the very next
        // recomposition, so latch it or we drag them straight back in.
        if (lastWasLandscape && !tearingDown) dismissedUntilFlip = true
        Log.d(TAG, "exit: native fullscreen")
    }
}