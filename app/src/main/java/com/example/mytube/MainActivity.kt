package com.example.mytube

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
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
            webViewManager = browserViewModel.webViewManager,
            nativeFullscreenEnabled = { settingsViewModel.nativeVideoFullscreen.value }
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
        fullscreenController.reassertIfActive()
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
        if (isInPictureInPictureMode && ::fullscreenController.isInitialized) {
            fullscreenController.reset()
        }
        browserViewModel.webViewManager.setVideoOnlyMode(isInPictureInPictureMode)
    }

    override fun onResume() {
        super.onResume()
        browserViewModel.playbackManager.setAppInForeground(true)
        browserViewModel.webViewManager.setVideoOnlyMode(false)
        browserViewModel.webViewManager.evaluateJs("window.setBackgroundMode && window.setBackgroundMode(false)")
        maybeRequestNotificationPermission()
    }

    override fun onPause() {
        super.onPause()
        browserViewModel.playbackManager.setAppInForeground(false)
        if (browserViewModel.bgPlaybackEnabled.value && browserViewModel.playbackManager.isPlaying) {
            browserViewModel.webViewManager.evaluateJs("window.setBackgroundMode && window.setBackgroundMode(true)")
            browserViewModel.webViewManager.evaluateJs("window.MyTubeBgTick && window.MyTubeBgTick()")
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
}
