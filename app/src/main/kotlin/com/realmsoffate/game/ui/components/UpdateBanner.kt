package com.realmsoffate.game.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.data.updater.ReleaseInfo
import com.realmsoffate.game.data.updater.UpdateRepositoryHolder
import com.realmsoffate.game.data.updater.UpdateState

/**
 * Title-screen banner. Shows for `Available` (when not dismissed),
 * `Downloading`, and `ReadyToInstall`. Hidden in all other states.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val repo = remember { UpdateRepositoryHolder.instance }
    val state by repo.state.collectAsState()
    val release: ReleaseInfo? = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.ReadyToInstall -> s.release
        else -> null
    }
    if (release == null) return

    var dismissedThisSession by remember { mutableStateOf(false) }
    var bannerVisible by remember { mutableStateOf(true) }
    LaunchedEffect(release.tag) {
        bannerVisible = repo.bannerVisibleFor(release)
    }
    if (!bannerVisible || dismissedThisSession) return

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Update available: ${release.tag}", style = MaterialTheme.typography.titleSmall)
            Text(
                release.body.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: release.name,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            when (val s = state) {
                is UpdateState.Available -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { repo.startDownload(release) }) { Text("Update") }
                    TextButton(onClick = { dismissedThisSession = true }) { Text("Later") }
                    TextButton(onClick = {
                        repo.dismissBannerForVersion(release.tag)
                        dismissedThisSession = true
                    }) { Text("Skip this version") }
                }
                is UpdateState.Downloading -> Column {
                    Text("Downloading… ${s.progress}%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { s.progress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                    TextButton(onClick = { repo.cancelDownload() }) { Text("Cancel") }
                }
                is UpdateState.ReadyToInstall ->
                    Button(onClick = { repo.startDownload(release) }) { Text("Install") }
                else -> Unit
            }
        }
    }
}
