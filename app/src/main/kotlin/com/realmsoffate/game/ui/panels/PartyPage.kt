package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.theme.RealmsTheme

@Composable
internal fun PartyPanel(state: GameUiState, onClose: () -> Unit, onDismiss: (String) -> Unit) {
    PanelSheet(
        "\uD83D\uDC65  Party",
        subtitle = if (state.party.isEmpty()) null else "${state.party.size} companions",
        onClose = onClose
    ) {
        if (state.party.isEmpty()) {
            EmptyState("\uD83D\uDC64", "You travel alone.")
            return@PanelSheet
        }
        LazyColumn(
            Modifier
                .padding(horizontal = 14.dp)
                .heightIn(max = 400.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.party) { c ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    c.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${c.race} ${c.role} · L${c.level}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onDismiss(c.name) }) { Text("Dismiss") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "HP ${c.hp}/${c.maxHp}",
                            style = MaterialTheme.typography.labelMedium,
                            color = RealmsTheme.colors.success
                        )
                        if (c.personality.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                c.personality,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
