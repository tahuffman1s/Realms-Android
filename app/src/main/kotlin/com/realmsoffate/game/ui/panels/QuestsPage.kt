package com.realmsoffate.game.ui.panels

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.ui.theme.RealmsTheme

// ----------------- QUESTS -----------------

internal enum class QuestFilter(val label: String, val icon: String, val status: String?) {
    Active("Active", "📜", "active"),
    Done("Done", "✅", "completed"),
    Failed("Failed", "❌", "failed"),
    All("All", "∗", null)
}

@Composable
internal fun QuestsPanel(state: GameUiState, onClose: () -> Unit, onAbandon: (String) -> Unit) {
    var filter by remember { mutableStateOf(QuestFilter.Active) }
    val filtered = state.quests.filter { filter.status == null || it.status == filter.status }
    PanelSheet(
        "\uD83D\uDCDC  Quests",
        subtitle = if (state.quests.isEmpty()) null else {
            val active = state.quests.count { it.status == "active" }
            val done = state.quests.count { it.status == "completed" }
            "$active active · $done completed"
        },
        onClose = onClose
    ) {
        if (state.quests.isEmpty()) {
            EmptyState("\uD83D\uDCDC", "No quests yet. The world waits.")
            return@PanelSheet
        }
        FilterTabs(
            tabs = QuestFilter.entries.map { it.label to it.icon },
            selectedIndex = QuestFilter.entries.indexOf(filter),
            onSelect = { filter = QuestFilter.entries[it] }
        )
        Spacer(Modifier.height(6.dp))
        if (filtered.isEmpty()) {
            Text(
                "No ${filter.label.lowercase()} quests.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@PanelSheet
        }
        LazyColumn(
            Modifier.padding(horizontal = 14.dp).heightIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered) { q ->
                val realms = RealmsTheme.colors
                val accent = when (q.status) {
                    "completed" -> realms.success
                    "failed" -> realms.fumbleRed
                    else -> MaterialTheme.colorScheme.primary
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().border(
                        1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(q.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusTag(q.status, accent)
                        }
                        Text(
                            "${q.type.uppercase()} · ${q.giver}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(q.desc, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        q.objectives.forEachIndexed { i, obj ->
                            val done = q.completed.getOrElse(i) { false }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (done) "\u2714" else "\u25CB",
                                    color = if (done) realms.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    obj,
                                    style = MaterialTheme.typography.bodySmall,
                                    textDecoration = if (done) TextDecoration.LineThrough else null,
                                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (q.reward.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color = realms.goldAccent.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Reward: ${q.reward}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = realms.goldAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (q.status == "active") {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { onAbandon(q.id) }) {
                                Text("Abandon", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusTag(status: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
        Text(
            status.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
