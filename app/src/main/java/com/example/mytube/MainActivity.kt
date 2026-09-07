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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.mytube.ui.BrowserScreen
import com.example.mytube.ui.SettingsSheet
import com.example.mytube.ui.theme.MyTubeTheme
import com.example.mytube.util.Constants
import com.example.mytube.viewmodel.BrowserViewModel
import com.example.mytube.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    private lateinit var browserViewModel: BrowserViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    private var isInPipMode by mutableStateOf(false)

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        }

        browserViewModel = ViewModelProvider(this)[BrowserViewModel::class.java]
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        browserViewModel.onSleepTimerFired = {
            finishAffinity()
        }

        browserViewModel.onExitApp = {
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
                    browserViewModel.isLocked.value -> Unit
                    browserViewModel.webViewManager.canGoBack -> browserViewModel.goBack()
                    else -> browserViewModel.requestExit()
                }
            }
        })

        setContent {
            MyTubeTheme {
                var showSettings by remember { mutableStateOf(false) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BrowserScreen(
                        viewModel = browserViewModel,
                        onSettingsClick = { showSettings = true },
                        isInPipMode = isInPipMode,
                        modifier = Modifier.padding(innerPadding)
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

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
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
        if (browserViewModel.bgPlaybackEnabled.value) {
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
            if (!wm.isFullscreen) {
                wm.scrollVideoIntoView()
                wm.setVideoOnlyMode(true)
            }
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(Constants.PIP_RATIO_WIDTH, Constants.PIP_RATIO_HEIGHT))
                .build()
            enterPictureInPictureMode(params)
            return
        }
        if (browserViewModel.bgPlaybackEnabled.value) {
            wm.evaluateJs("window.setBackgroundMode && window.setBackgroundMode(true)")
            wm.evaluateJs("window.MyTubeBgTick && window.MyTubeBgTick()")
        } else {
            wm.evaluateJs("window.MyTubePause && window.MyTubePause()")
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
