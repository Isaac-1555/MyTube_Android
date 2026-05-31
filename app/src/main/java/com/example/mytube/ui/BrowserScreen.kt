package com.example.mytube.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mytube.ui.components.BrowserWebView
import com.example.mytube.viewmodel.BrowserViewModel

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onSettingsClick: () -> Unit,
    onPipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mgr = viewModel.webViewManager

    Column(modifier = modifier.fillMaxSize()) {
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
            onPip = onPipClick,
            onSettings = onSettingsClick,
            isBookmarked = false, // TODO: wire up
            onToggleBookmark = { viewModel.toggleBookmark(mgr.currentUrl, mgr.pageTitle) }
        )
    }
}

@Composable
private fun BottomControls(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onPip: () -> Unit,
    onSettings: () -> Unit,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit
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
        IconButton(onClick = onToggleBookmark) {
            Icon(
                if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = "Bookmark"
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onPip) {
            Icon(Icons.Default.PictureInPicture, contentDescription = "PiP")
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}
