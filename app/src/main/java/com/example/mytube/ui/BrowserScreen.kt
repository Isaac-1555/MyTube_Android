package com.example.mytube.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mytube.ui.components.BrowserWebView
import com.example.mytube.viewmodel.BrowserViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mgr = viewModel.webViewManager
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()
    var showSleepTimerSheet by remember { mutableStateOf(false) }

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
                sleepTimerRemaining = sleepTimerRemaining,
                onBack = { viewModel.goBack() },
                onForward = { viewModel.goForward() },
                onReload = { viewModel.reload() },
                onYoutubeMusic = { viewModel.loadUrl("https://music.youtube.com") },
                onYoutube = { viewModel.loadUrl("https://youtube.com") },
                onSettings = onSettingsClick,
                onSleepTimer = { showSleepTimerSheet = true }
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

        if (showSleepTimerSheet) {
            SleepTimerSheet(
                initialMinutes = sleepTimerRemaining?.let { ((it + 59_999) / 60_000).toInt() },
                onDismiss = { showSleepTimerSheet = false },
                onSetTimer = { viewModel.setSleepTimer(it) },
                onCancelTimer = { viewModel.setSleepTimer(0) }
            )
        }
    }
}

@Composable
private fun BottomControls(
    canGoBack: Boolean,
    canGoForward: Boolean,
    sleepTimerRemaining: Long?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onYoutube: () -> Unit,
    onYoutubeMusic: () -> Unit,
    onSettings: () -> Unit,
    onSleepTimer: () -> Unit
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
        IconButton(onClick = onYoutube) {
            Icon(Icons.Default.Videocam, contentDescription = "YouTube")
        }
        IconButton(onClick = onYoutubeMusic) {
            Icon(Icons.Default.LibraryMusic, contentDescription = "YouTube Music")
        }
        IconButton(onClick = onSleepTimer) {
            Icon(Icons.Default.Bedtime, contentDescription = "Sleep timer")
        }
        sleepTimerRemaining?.let { remaining ->
            Text(
                text = formatRemaining(remaining),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    initialMinutes: Int?,
    onDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMinutes by remember { mutableStateOf(initialMinutes ?: 30) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Sleep Timer", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (initialMinutes != null) "Timer set: $initialMinutes min" else "App will close after the timer ends",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            MinuteWheel(
                values = (1..120).toList(),
                selected = selectedMinutes,
                onSelectedChange = { selectedMinutes = it },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        onCancelTimer()
                        onDismiss()
                    }
                ) {
                    Text("Off")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        onSetTimer(selectedMinutes)
                        onDismiss()
                    }
                ) {
                    Text("Start")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MinuteWheel(
    values: List<Int>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 44.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.roundToPx() }
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val halfVisible = 2
    val initialIndex = values.indexOf(selected).coerceAtLeast(0)

    LaunchedEffect(Unit) {
        listState.scrollToItem(initialIndex, 0)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val offset = listState.firstVisibleItemIndex * itemHeightPx + listState.firstVisibleItemScrollOffset
            val index = ((offset + itemHeightPx / 2) / itemHeightPx)
                .coerceIn(0, values.lastIndex)
            values[index]
        }.collectLatest { onSelectedChange(it) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight * (halfVisible * 2 + 1)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(12.dp)
                )
        )
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * halfVisible)
        ) {
            itemsIndexed(values) { index, value ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms + 999) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }
}
