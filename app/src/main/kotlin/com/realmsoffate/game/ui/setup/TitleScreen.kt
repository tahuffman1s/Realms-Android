package com.realmsoffate.game.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.GraveyardEntry
import com.realmsoffate.game.data.SaveSlotMeta
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.ui.theme.RealmsTheme

/**
 * Title / main menu — sits between the API setup flow and character creation.
 *
 *   ⚔️  REALMS
 *   ▶ Continue Adventure  (if an autosave exists)
 *   ✨ New Adventure
 *   📂 Load Save
 *   📥 Import File
 *   ⚰️ Graveyard
 *   ⚙ Back to setup
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleScreen(vm: GameViewModel) {
    val slots by vm.saveSlots.collectAsState()
    val graves by vm.graveyard.collectAsState()
    val realms = RealmsTheme.colors
    val context = LocalContext.current

    var loadSheet by remember { mutableStateOf(false) }
    var graveSheet by remember { mutableStateOf(false) }
    var selectedGrave by remember { mutableStateOf<GraveyardEntry?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
                text?.let(vm::importSave)
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))
            Text("\u2694\uFE0F", fontSize = 72.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "REALMS",
                style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 8.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "A D&D 5e narrative",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(40.dp))

            val latest = slots.firstOrNull()

            // Primary action: continue most recent save
            if (latest != null) {
                PrimaryTile(
                    icon = Icons.Default.PlayArrow,
                    title = "Continue",
                    subtitle = "${latest.characterName} · L${latest.level} ${latest.cls} · Turn ${latest.turns}",
                    onClick = { vm.continueLatest() },
                    gradient = true
                )
                Spacer(Modifier.height(10.dp))
            }

            // New run
            PrimaryTile(
                icon = Icons.Default.Add,
                title = "New Adventure",
                subtitle = "Forge a fresh hero",
                onClick = { vm.goToCharacterCreation() },
                gradient = latest == null
            )
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryTile(
                    icon = Icons.Default.FolderOpen,
                    label = "Load (${slots.size})",
                    enabled = slots.isNotEmpty(),
                    onClick = { loadSheet = true },
                    modifier = Modifier.weight(1f)
                )
                SecondaryTile(
                    icon = Icons.Default.FileUpload,
                    label = "Import",
                    enabled = true,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryTile(
                    icon = Icons.Default.FolderOpen,
                    label = "Graveyard (${graves.size})",
                    enabled = graves.isNotEmpty(),
                    onClick = { graveSheet = true },
                    modifier = Modifier.weight(1f),
                    accent = realms.fumbleRed,
                    iconText = "\u26B0"
                )
                SecondaryTile(
                    icon = Icons.Default.Settings,
                    label = "API Setup",
                    enabled = true,
                    onClick = { vm.backToApiSetup() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "AI Game Master · Compose Dice · Dynamic World",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(48.dp))
        }
    }

    if (loadSheet) {
        LoadSheet(
            slots = slots,
            onPick = { vm.loadSlot(it.slot); loadSheet = false },
            onDelete = { vm.deleteSlot(it.slot) },
            onDismiss = { loadSheet = false }
        )
    }
    if (graveSheet) {
        GraveyardSheet(
            graves = graves,
            onPick = { selectedGrave = it },
            onForget = { vm.exhumeGrave(it) },
            onDismiss = { graveSheet = false }
        )
    }
    selectedGrave?.let {
        GraveDetailDialog(entry = it, onDismiss = { selectedGrave = null })
    }
}

@Composable
private fun PrimaryTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    gradient: Boolean
) {
    val realms = RealmsTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (gradient) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (gradient) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().height(74.dp)
    ) {
        Box(
            modifier = if (gradient) Modifier.background(
                Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, realms.goldAccent))
            ) else Modifier,
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SecondaryTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    iconText: String? = null
) {
    val contentColor = accent ?: MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier.height(62.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconText != null) {
                Text(iconText, fontSize = 22.sp, color = contentColor)
            } else {
                Icon(icon, null, Modifier.size(22.dp), tint = contentColor)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadSheet(
    slots: List<SaveSlotMeta>,
    onPick: (SaveSlotMeta) -> Unit,
    onDelete: (SaveSlotMeta) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Text("LOAD SAVE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            if (slots.isEmpty()) {
                Text("No saves yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 500.dp)) {
                    items(slots) { s ->
                        SaveRow(s, onPick = { onPick(s) }, onDelete = { onDelete(s) })
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun SaveRow(s: SaveSlotMeta, onPick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onPick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.characterName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.characterName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "L${s.level} ${s.race} ${s.cls} · Turn ${s.turns}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${s.scene.uppercase()} · ${s.savedAt.take(19).replace("T", " ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraveyardSheet(
    graves: List<GraveyardEntry>,
    onPick: (GraveyardEntry) -> Unit,
    onForget: (GraveyardEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val realms = RealmsTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Text("GRAVEYARD", style = MaterialTheme.typography.labelLarge, color = realms.fumbleRed)
            Spacer(Modifier.height(4.dp))
            Text(
                "Those who walked these realms and fell.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            if (graves.isEmpty()) {
                Text("None, yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 540.dp)) {
                    items(graves) { g ->
                        Surface(
                            onClick = { onPick(g) },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().border(
                                1.dp, realms.fumbleRed.copy(alpha = 0.35f), RoundedCornerShape(14.dp)
                            )
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("\u26B0", fontSize = 28.sp, color = realms.fumbleRed)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(g.characterName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "L${g.level} ${g.race} ${g.cls} · ${g.turns} turns",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        g.causeOfDeath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = realms.fumbleRed,
                                        maxLines = 1
                                    )
                                }
                                IconButton(onClick = { onForget(g) }) { Icon(Icons.Outlined.Delete, "Forget") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun GraveDetailDialog(entry: GraveyardEntry, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(entry.characterName, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "L${entry.level} ${entry.race} ${entry.cls}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("${entry.turns} turns · ${entry.xp} XP · ${entry.gold}g", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Text(entry.causeOfDeath, style = MaterialTheme.typography.bodyMedium, color = RealmsTheme.colors.fumbleRed)
                Spacer(Modifier.height(12.dp))
                Text("LIFE STORY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                entry.timeline.forEach { t ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(
                            "T${t.turn}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(36.dp)
                        )
                        Text(t.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    )
}
