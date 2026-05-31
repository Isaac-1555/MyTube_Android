package com.example.mytube

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        }

        browserViewModel = ViewModelProvider(this)[BrowserViewModel::class.java]
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        setContent {
            MyTubeTheme {
                var showSettings by remember { mutableStateOf(false) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BrowserScreen(
                        viewModel = browserViewModel,
                        onSettingsClick = { showSettings = true },
                        onPipClick = { enterPip() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                if (showSettings) {
                    SettingsSheet(
                        viewModel = settingsViewModel,
                        onDismiss = { showSettings = false },
                        onBookmarkClick = { url ->
                            showSettings = false
                            browserViewModel.loadUrl(url)
                        }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        browserViewModel.webViewManager.webView?.onResume()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (browserViewModel.autoPipEnabled.value) {
            enterPip()
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(Constants.PIP_RATIO_WIDTH, Constants.PIP_RATIO_HEIGHT))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}
