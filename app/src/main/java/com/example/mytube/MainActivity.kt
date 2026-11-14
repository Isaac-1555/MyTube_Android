package com.example.mytube

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.mytube.browser.BrowserMode
import com.example.mytube.browser.VideoFullscreenController
import com.example.mytube.ui.BrowserScreen
import com.example.mytube.ui.SettingsSheet
import com.example.mytube.ui.theme.MyTubeTheme
import com.example.mytube.util.Constants
import com.example.mytube.viewmodel.BrowserViewModel
import com.example.mytube.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    private lateinit var browserViewModel: BrowserViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var fullscreenController: VideoFullscreenController

    private var isInPipMode by mutableStateOf(false)

    /**
     * Set while the app is on its way into a PiP window, so [onPause] knows
     * that the window is about to be on screen and the app is not backgrounded.
     */
    private var pipRequested = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        browserViewModel = ViewModelProvider(this)[BrowserViewModel::class.java]
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        fullscreenController = VideoFullscreenController(
            activity = this,
            webViewManager = browserViewModel.webViewManager
        )
        browserViewModel.webViewManager.onCustomViewShown = {
            fullscreenController.onNativeFullscreenShown()
        }
        browserViewModel.webViewManager.onCustomViewHidden = {
            fullscreenController.onNativeFullscreenHidden()
        }

        browserViewModel.onSleepTimerFired = {
            fullscreenController.reset()
            finishAffinity()
        }

        browserViewModel.onExitApp = {
            fullscreenController.reset()
            finishAffinity()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isInPictureInPictureMode) {
                    // Let the system dismiss the PiP window.
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                    return
                }
                when {
                    // Fullscreen first. Without this branch there is no way out of a
                    // forced-landscape video except rotating the phone.
                    fullscreenController.isFullscreenActive -> fullscreenController.exitByUser()
                    browserViewModel.isLocked.value -> Unit
                    browserViewModel.webViewManager.canGoBack -> browserViewModel.goBack()
                    else -> browserViewModel.requestExit()
                }
            }
        })

        setContent {
            MyTubeTheme {
                var showSettings by remember { mutableStateOf(false) }

                val fullscreenActive = fullscreenController.isFullscreenActive

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = if (fullscreenActive) {
                        WindowInsets(0, 0, 0, 0)
                    } else {
                        WindowInsets.systemBars
                    }
                ) { innerPadding ->
                    BrowserScreen(
                        viewModel = browserViewModel,
                        onSettingsClick = { showSettings = true },
                        isInPipMode = isInPipMode,
                        fullscreenController = fullscreenController,
                        modifier = Modifier.padding(if (fullscreenActive) {
                            PaddingValues(0.dp)
                        } else {
                            innerPadding
                        })
                    )
                }

                if (showSettings) {
                    SettingsSheet(
                        viewModel = settingsViewModel,
                        onDismiss = { showSettings = false }
                    )
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // configChanges in the manifest covers orientation, so the Activity is never
        // recreated and this is the only place a rotation becomes visible natively.
        fullscreenController.onConfigChanged()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) fullscreenController.reassertIfActive()
    }

    override fun onStop() {
        super.onStop()
        // Never leave the app pinned to landscape with the bars hidden.
        if (::fullscreenController.isInitialized) fullscreenController.reset()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        if (!isInPictureInPictureMode) pipRequested = false
        if (isInPictureInPictureMode && ::fullscreenController.isInitialized) {
            fullscreenController.reset()
        }
        if (isInPictureInPictureMode) {
            // The PiP window is on screen and playing, so this is not a
            // background state: the playback service must not run its
            // background heartbeat against it.
            browserViewModel.playbackManager.setAppInForeground(true)
            // The window goes away on the way into the PiP surface, and a hidden
            // page has its media paused by the browser.
            browserViewModel.webViewManager.setKeepPageVisible(true)
            // The page's own player pauses the video when the window becomes a
            // PiP window. It is resumed once, and the pause is not reported.
            browserViewModel.webViewManager.evaluateJs(
                "window.MyTubePipKeepAlive && window.MyTubePipKeepAlive(true)"
            )
        } else {
            browserViewModel.webViewManager.evaluateJs(
                "window.MyTubePipKeepAlive && window.MyTubePipKeepAlive(false)"
            )
        }
        browserViewModel.webViewManager.setVideoOnlyMode(isInPictureInPictureMode)
        logPageState(if (isInPictureInPictureMode) "pip-in" else "pip-out")
    }

    /**
     * Reports what the page has been told to do, so a device log shows whether a
     * mode was left behind.
     *
     * A mode that is left on is what makes the page behave strangely much later:
     * the stylesheet stays on, the pins stay on, and the controls stay out of
     * reach.
     */
    private fun logPageState(tag: String) {
        browserViewModel.webViewManager.evaluateJsInFromMainThread(
            BrowserMode.YOUTUBE,
            "window.MyTubeFsState && window.MyTubeFsState()"
        ) { raw ->
            Log.d("MyTubeState", "page[$tag] $raw")
        }
    }

    override fun onResume() {
        super.onResume()
        mainHandler.removeCallbacks(backgroundModeRunnable)
        // The app is in front again, so it did not end up in a PiP window.
        pipRequested = false
        browserViewModel.playbackManager.setAppInForeground(true)
        browserViewModel.webViewManager.setKeepPageVisible(true)
        // Not while in PiP: the PiP window is still on screen and the page has to
        // stay stripped down to the video.
        if (!isInPictureInPictureMode) {
            browserViewModel.webViewManager.setVideoOnlyMode(false)
        }
        browserViewModel.webViewManager.evaluateJs("window.setBackgroundMode && window.setBackgroundMode(false)")
        maybeRequestNotificationPermission()
    }

    override fun onPause() {
        super.onPause()
        // In PiP the WebView is still on screen and still playing. Treating that
        // as "backgrounded" started the background tickers - a 1.5s page poll
        // and a 2s service heartbeat - for a window that was visibly playing.
        if (isInPictureInPictureMode || pipRequested) {
            browserViewModel.playbackManager.setAppInForeground(true)
            browserViewModel.webViewManager.setKeepPageVisible(true)
            return
        }
        // The system PiP button enters PiP without onUserLeaveHint, so the PiP
        // callback arrives after this one. The decision is taken a moment later,
        // when it is known which of the two happened.
        mainHandler.removeCallbacks(backgroundModeRunnable)
        mainHandler.postDelayed(backgroundModeRunnable, BACKGROUND_DECISION_DELAY_MS)
    }

    /**
     * Puts the app into background-playback mode, once it is known that the app
     * is really off screen and not on its way into a PiP window.
     */
    private val backgroundModeRunnable = Runnable {
        if (isInPictureInPictureMode || pipRequested) {
            browserViewModel.playbackManager.setAppInForeground(true)
            browserViewModel.webViewManager.setKeepPageVisible(true)
            return@Runnable
        }
        browserViewModel.playbackManager.setAppInForeground(false)
        if (browserViewModel.bgPlaybackEnabled.value && browserViewModel.playbackManager.isPlaying) {
            // The playback is meant to run in the background, and a page the
            // browser considers hidden has its media paused.
            browserViewModel.webViewManager.setKeepPageVisible(true)
            browserViewModel.webViewManager.evaluateJs("window.setBackgroundMode && window.setBackgroundMode(true)")
            browserViewModel.webViewManager.evaluateJs("window.MyTubeBgTick && window.MyTubeBgTick()")
        } else {
            browserViewModel.webViewManager.setKeepPageVisible(false)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val wm = browserViewModel.webViewManager
        val wantsPip = settingsViewModel.autoPip.value &&
            browserViewModel.playbackManager.isPlaying &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !isInPictureInPictureMode
        if (wantsPip) {
            // Landscape fullscreen and PiP both want the window. PiP wins, so tear
            // fullscreen down before handing the surface over.
            pipRequested = true
            // The window goes away on the way into the PiP surface, and a page
            // the browser considers hidden has its media paused - which is what
            // made the picture stop and the notification flip.
            browserViewModel.webViewManager.setKeepPageVisible(true)
            // The page's own player pauses the video when the window becomes a
            // PiP window, so the resume has to be armed before it happens.
            browserViewModel.webViewManager.evaluateJs(
                "window.MyTubePipKeepAlive && window.MyTubePipKeepAlive(true)"
            )
            fullscreenController.reset()
            wm.scrollVideoIntoView()
            wm.setVideoOnlyMode(true)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(Constants.PIP_RATIO_WIDTH, Constants.PIP_RATIO_HEIGHT))
                .build()
            enterPictureInPictureMode(params)
            return
        }
        val bgEnabled = browserViewModel.bgPlaybackEnabled.value
        if (bgEnabled && browserViewModel.playbackManager.isPlaying) {
            wm.evaluateJs("window.setBackgroundMode && window.setBackgroundMode(true)")
            wm.evaluateJs("window.MyTubeBgTick && window.MyTubeBgTick()")
        } else {
            if (!bgEnabled) {
                wm.evaluateJs("window.MyTubePause && window.MyTubePause()")
            }
            wm.evaluateJs("window.setBackgroundMode && window.setBackgroundMode(false)")
            browserViewModel.playbackManager.stopService()
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        if (browserViewModel.notifPermissionRequested.value) return
        browserViewModel.markNotifPermissionRequested()
        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        /**
         * How long the app waits before it decides that it is really backgrounded.
         * The PiP callback arrives after onPause, so the decision cannot be made
         * in onPause itself.
         */
        const val BACKGROUND_DECISION_DELAY_MS = 250L
    }
}
