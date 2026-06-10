package com.example.mytube.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mytube.ui.components.BrowserWebView
import com.example.mytube.viewmodel.BrowserViewModel

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mgr = viewModel.webViewManager
    var showBlackOverlay by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = mgr.isLoading) {
                LinearProgressIndicator(
                    progress = { mgr.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                BrowserWebView(webViewManager = mgr)
            }

            BottomControls(
                canGoBack = mgr.canGoBack,
                canGoForward = mgr.canGoForward,
                onBack = { viewModel.goBack() },
                onForward = { viewModel.goForward() },
                onReload = { viewModel.reload() },
                onYoutubeMusic = { viewModel.loadUrl("https://music.youtube.com") },
                onSettings = onSettingsClick,
                onBlackOverlay = { showBlackOverlay = !showBlackOverlay }
            )
        }

        mgr.fullscreenView?.let { fullView ->
            key(fullView.hashCode()) {
                AndroidView(
                    factory = { fullView },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (showBlackOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showBlackOverlay = false }
            )
        }
    }
}

@Composable
private fun BottomControls(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onYoutubeMusic: () -> Unit,
    onSettings: () -> Unit,
    onBlackOverlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
        }
        IconButton(onClick = onReload) {
            Icon(Icons.Default.Refresh, contentDescription = "Reload")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onYoutubeMusic) {
            Icon(Icons.Default.LibraryMusic, contentDescription = "YouTube Music")
        }
        IconButton(onClick = onBlackOverlay) {
            Icon(Icons.Default.DarkMode, contentDescription = "Screen off")
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}
