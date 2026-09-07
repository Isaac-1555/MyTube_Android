package com.example.mytube.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mytube.ui.components.BrowserWebView
import com.example.mytube.util.Constants
import com.example.mytube.viewmodel.BrowserViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onSettingsClick: () -> Unit,
    isInPipMode: Boolean,
    modifier: Modifier = Modifier
) {
    val mgr = viewModel.webViewManager
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val showExitDialog by viewModel.showExitDialog.collectAsState()
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    val isFullscreen = mgr.fullscreenView != null

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isInPipMode) {
                AnimatedVisibility(visible = mgr.isLoading) {
                    LinearProgressIndicator(
                        progress = { mgr.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                BrowserWebView(webViewManager = mgr)
                if (!isInPipMode) {
                    SwipeEdge(
                        side = SwipeEdgeSide.Start,
                        onSwipe = {
                            if (!viewModel.goBack()) {
                                viewModel.requestExit()
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    SwipeEdge(
                        side = SwipeEdgeSide.End,
                        onSwipe = { viewModel.goForward() },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }

            if (!isInPipMode) {
                BottomControls(
                    sleepTimerRemaining = sleepTimerRemaining,
                    isLocked = isLocked,
                    onYoutube = { viewModel.loadUrl("https://youtube.com") },
                    onYoutubeMusic = { viewModel.loadUrl("https://music.youtube.com") },
                    onSettings = onSettingsClick,
                    onSleepTimer = { showSleepTimerSheet = true },
                    onToggleLock = { viewModel.toggleLock() }
                )
            }
        }

        mgr.fullscreenView?.let { fullView ->
            key(fullView.hashCode()) {
                AndroidView(
                    factory = { fullView },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissExitDialog() },
                title = { Text("Exit MyTube?") },
                text = { Text("You are at the beginning of your browsing history.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmExit() }) { Text("Exit") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissExitDialog() }) { Text("Cancel") }
                }
            )
        }

        if (isLocked && !isInPipMode) {
            LockOverlay(modifier = Modifier.fillMaxSize())
            LockChip(
                locked = true,
                onToggle = { viewModel.toggleLock() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        } else if (!isLocked && isFullscreen && !isInPipMode) {
            LockChip(
                locked = false,
                onToggle = { viewModel.toggleLock() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
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
    sleepTimerRemaining: Long?,
    isLocked: Boolean,
    onYoutube: () -> Unit,
    onYoutubeMusic: () -> Unit,
    onSettings: () -> Unit,
    onSleepTimer: () -> Unit,
    onToggleLock: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onYoutube, enabled = !isLocked) {
            Icon(Icons.Default.Videocam, contentDescription = "YouTube")
        }
        IconButton(onClick = onYoutubeMusic, enabled = !isLocked) {
            Icon(Icons.Default.LibraryMusic, contentDescription = "YouTube Music")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSleepTimer, enabled = !isLocked) {
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
        IconButton(onClick = onSettings, enabled = !isLocked) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
        IconButton(onClick = onToggleLock) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (isLocked) "Unlock screen" else "Lock screen",
                tint = Color.White
            )
        }
    }
}

private enum class SwipeEdgeSide { Start, End }

@Composable
private fun SwipeEdge(
    side: SwipeEdgeSide,
    onSwipe: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(Constants.SWIPE_EDGE_WIDTH_DP.dp)
            .systemGestureExclusion()
            .pointerInput(side) {
                val thresholdPx = Constants.SWIPE_THRESHOLD_DP.dp.toPx()
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f
                    var dy = 0f
                    var decided = false
                    var triggered = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (decided) {
                            event.changes.forEach { it.consume() }
                        }
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                        if (!decided && (abs(dx) > slop || abs(dy) > slop)) {
                            // Vertical intent: hand the gesture back to the page.
                            decided = abs(dx) > abs(dy)
                            if (!decided) break
                        }
                        if (decided && !triggered && abs(dx) >= thresholdPx) {
                            val isBackSwipe = side == SwipeEdgeSide.Start && dx > 0
                            val isForwardSwipe = side == SwipeEdgeSide.End && dx < 0
                            if (isBackSwipe || isForwardSwipe) {
                                triggered = true
                                onSwipe()
                            }
                            // Wrong direction: swallow the rest, no page reaction.
                        }
                    }
                }
            }
    )
}

@Composable
private fun LockOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.08f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
    )
}

@Composable
private fun LockChip(
    locked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.background(
            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            CircleShape
        )
    ) {
        Icon(
            imageVector = if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = if (locked) "Unlock screen" else "Lock screen",
            tint = Color.White
        )
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
