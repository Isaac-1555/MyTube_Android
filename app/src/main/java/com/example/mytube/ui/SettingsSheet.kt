package com.example.mytube.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mytube.data.entity.BookmarkEntity
import com.example.mytube.data.entity.ScriptEntity
import com.example.mytube.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onBookmarkClick: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bgPlayback by viewModel.backgroundPlayback.collectAsState()
    val autoPip by viewModel.autoPip.collectAsState()
    val adblockEnabled by viewModel.adblockEnabled.collectAsState()

    var bookmarks by remember { mutableStateOf<List<BookmarkEntity>>(emptyList()) }
    var scripts by remember { mutableStateOf<List<ScriptEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        bookmarks = viewModel.getBookmarks()
        scripts = viewModel.getScripts()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
            }

            item {
                SettingsToggle("Background Playback", bgPlayback) {
                    viewModel.setBackgroundPlayback(it)
                }
                SettingsToggle("Auto PiP", autoPip) {
                    viewModel.setAutoPip(it)
                }
                SettingsToggle("Ad Blocking", adblockEnabled) {
                    viewModel.setAdblockEnabled(it)
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            }

            item {
                Text("Scripts", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }

            items(scripts, key = { it.id }) { script ->
                ScriptItem(script = script, onToggle = { enabled ->
                    viewModel.toggleScript(script.id, enabled)
                    scripts = scripts.map { if (it.id == script.id) it.copy(enabled = enabled) else it }
                })
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Bookmarks", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }

            if (bookmarks.isEmpty()) {
                item {
                    Text(
                        "No bookmarks yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(bookmarks, key = { it.url }) { bookmark ->
                BookmarkItem(
                    bookmark = bookmark,
                    onClick = { onBookmarkClick(bookmark.url) },
                    onDelete = {
                        viewModel.removeBookmark(bookmark.url)
                        bookmarks = bookmarks.filter { it.url != bookmark.url }
                    }
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ScriptItem(script: ScriptEntity, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(script.name, modifier = Modifier.weight(1f))
        Switch(checked = script.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun BookmarkItem(bookmark: BookmarkEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = bookmark.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete bookmark")
        }
    }
}
