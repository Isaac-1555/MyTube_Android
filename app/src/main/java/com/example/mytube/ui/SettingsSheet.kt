package com.example.mytube.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.mytube.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bgPlayback by viewModel.backgroundPlayback.collectAsState()
    val autoPip by viewModel.autoPip.collectAsState()
    val adblockEnabled by viewModel.adblockEnabled.collectAsState()
    val autoHideBar by viewModel.autoHideBar.collectAsState()
    val moviesSource by viewModel.moviesSourceUrl.collectAsState(initial = null)
    val animeSource by viewModel.animeSourceUrl.collectAsState(initial = null)
    var moviesText by remember(moviesSource) { mutableStateOf(moviesSource ?: "") }
    var animeText by remember(animeSource) { mutableStateOf(animeSource ?: "") }

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
                SettingsToggle("Auto-hide Bottom Bar", autoHideBar) {
                    viewModel.setAutoHideBar(it)
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Sources", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Movie and anime mirrors rotate or get taken down. If a tab stops loading, paste the current home URL here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SourceUrlField(
                    label = "Movies URL",
                    value = moviesText,
                    onValueChange = { moviesText = it },
                    onSave = { viewModel.setMoviesSourceUrl(moviesText) }
                )
                Spacer(Modifier.height(8.dp))
                SourceUrlField(
                    label = "Anime URL",
                    value = animeText,
                    onValueChange = { animeText = it },
                    onSave = { viewModel.setAnimeSourceUrl(animeText) }
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SourceUrlField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            )
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSave) { Text("Save") }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    onValueChange("")
                    onSave()
                }
            ) { Text("Reset to default") }
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


