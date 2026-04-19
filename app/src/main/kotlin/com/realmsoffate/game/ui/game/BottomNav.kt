package com.realmsoffate.game.ui.game

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class GameTab(val label: String, val icon: ImageVector) {
    Chat("Chat", Icons.Filled.ChatBubble),
    Character("Character", Icons.Filled.Person),
    Journal("Journal", Icons.AutoMirrored.Filled.MenuBook)
}

@Composable
internal fun GameBottomNav(
    selected: GameTab,
    onSelect: (GameTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        GameTab.entries.forEach { t ->
            NavigationBarItem(
                selected = t == selected,
                onClick = { onSelect(t) },
                icon = { Icon(t.icon, t.label) },
                label = { Text(t.label) }
            )
        }
    }
}
