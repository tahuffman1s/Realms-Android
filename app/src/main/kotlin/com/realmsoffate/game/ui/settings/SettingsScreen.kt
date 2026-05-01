package com.realmsoffate.game.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.BuildConfig
import com.realmsoffate.game.data.updater.ReleaseInfo
import com.realmsoffate.game.data.updater.UpdateChannel
import com.realmsoffate.game.data.updater.UpdateRepositoryHolder
import com.realmsoffate.game.data.updater.UpdateState
import com.realmsoffate.game.game.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: GameViewModel) {
    val repo = remember { UpdateRepositoryHolder.instance }
    val state by repo.state.collectAsState()
    val channel by repo.observableChannel().collectAsState(initial = UpdateChannel.DEFAULT)
    val lastChecked by repo.observableLastCheckedAt().collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { vm.closeSettings() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection("App") {
                LabeledRow("Version", BuildConfig.VERSION_NAME)
            }

            SettingsSection("Updates") {
                LabeledRow("Last checked", relativeTime(lastChecked))
                ChannelToggleRow(
                    channel = channel,
                    onChange = { repo.setChannel(it) }
                )
                CheckNowRow(
                    enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                    onCheck = { repo.check(force = true) }
                )
                UpdateStatusRow(state = state, onUpdate = { release -> repo.startDownload(release) })
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChannelToggleRow(channel: UpdateChannel, onChange: (UpdateChannel) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Include prereleases", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = channel == UpdateChannel.INCLUDE_PRERELEASES,
            onCheckedChange = { on ->
                onChange(if (on) UpdateChannel.INCLUDE_PRERELEASES else UpdateChannel.STABLE_ONLY)
            }
        )
    }
}

@Composable
private fun CheckNowRow(enabled: Boolean, onCheck: () -> Unit) {
    TextButton(onClick = onCheck, enabled = enabled) { Text("Check for updates now") }
}

@Composable
private fun UpdateStatusRow(state: UpdateState, onUpdate: (ReleaseInfo) -> Unit) {
    when (state) {
        is UpdateState.Available ->
            TextButton(onClick = { onUpdate(state.release) }) { Text("Update to ${state.release.tag}") }
        is UpdateState.Downloading ->
            Text("Downloading… ${state.progress}%", style = MaterialTheme.typography.bodyMedium)
        is UpdateState.ReadyToInstall ->
            TextButton(onClick = { onUpdate(state.release) }) { Text("Install ${state.release.tag}") }
        is UpdateState.Error ->
            Text(state.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate -> Unit
    }
}

private fun relativeTime(epochMs: Long?): String {
    if (epochMs == null) return "Never"
    val deltaMs = System.currentTimeMillis() - epochMs
    if (deltaMs < 60_000L) return "Just now"
    if (deltaMs < 3_600_000L) return "${deltaMs / 60_000L}m ago"
    if (deltaMs < 86_400_000L) return "${deltaMs / 3_600_000L}h ago"
    return "${deltaMs / 86_400_000L}d ago"
}
