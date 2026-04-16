package com.realmsoffate.game.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.launch
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
import com.realmsoffate.game.ui.theme.RealmsSpacing
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
                .padding(horizontal = RealmsSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))
            // Crisp vector sword (Lawnicons-style, same drawable as the launcher icon).
            // Renders sharp at any size — the previous emoji rasterised blurry at 72sp.
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(
                    id = com.realmsoffate.game.R.drawable.ic_launcher_foreground
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(140.dp)
            )
            Spacer(Modifier.height(8.dp))
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
                if (slots.isNotEmpty()) {
                    SecondaryTile(
                        icon = Icons.Default.FolderOpen,
                        label = "Load (${slots.size})",
                        enabled = true,
                        onClick = { loadSheet = true },
                        modifier = Modifier.weight(1f)
                    )
                }
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

            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { vm.resetTutorial() }) {
                Text(
                    "Replay Tutorial",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = MaterialTheme.shapes.medium,
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
                Modifier.fillMaxSize().padding(horizontal = RealmsSpacing.xl),
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
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier.height(62.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = RealmsSpacing.m),
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Optimistic list — slots removed immediately; restored if Undo is tapped
    var visibleSlots by remember(slots) { mutableStateOf(slots) }

    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Box {
            Column(Modifier.padding(horizontal = RealmsSpacing.xl, vertical = RealmsSpacing.s)) {
                Text("LOAD SAVE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                if (visibleSlots.isEmpty()) {
                    Text("No saves yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 500.dp)) {
                        items(visibleSlots, key = { it.slot }) { s ->
                            SaveRow(
                                s = s,
                                onPick = { onPick(s) },
                                onDelete = {
                                    // Optimistically remove
                                    visibleSlots = visibleSlots.filter { it.slot != s.slot }
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Deleted",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            // Restore the slot at its original position
                                            visibleSlots = slots
                                        } else {
                                            onDelete(s)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SaveRow(s: SaveSlotMeta, onPick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onPick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = RealmsSpacing.m, vertical = RealmsSpacing.m),
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
        Column(Modifier.padding(horizontal = RealmsSpacing.xl, vertical = RealmsSpacing.s)) {
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
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().border(
                                1.dp, realms.fumbleRed.copy(alpha = 0.35f), MaterialTheme.shapes.medium
                            )
                        ) {
                            Row(
                                Modifier.padding(horizontal = RealmsSpacing.m, vertical = RealmsSpacing.m),
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
    val realms = RealmsTheme.colors
    val moralLabel = when {
        entry.morality > 30 -> "Virtuous"
        entry.morality > 0 -> "Good"
        entry.morality == 0 -> "Neutral"
        entry.morality > -30 -> "Questionable"
        else -> "Villainous"
    }
    val moralColor = when {
        entry.morality > 0 -> realms.success
        entry.morality == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> realms.fumbleRed
    }
    val categoryIcon = { cat: String -> when (cat) {
        "birth", "start" -> "🌅"
        "levelup" -> "⬆️"
        "quest" -> "📜"
        "travel" -> "🗺️"
        "event" -> "🌍"
        "combat", "battle" -> "⚔️"
        "death" -> "💀"
        else -> "•"
    }}

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .fillMaxHeight(0.85f)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // ---- Header: dark banner with name + epitaph ----
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.surface)
                            )
                        )
                        .padding(RealmsSpacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚰️", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            entry.characterName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "L${entry.level} ${entry.race} ${entry.cls}",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        if (entry.worldName.isNotBlank()) {
                            Text(
                                entry.worldName,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = realms.fumbleRed.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                entry.causeOfDeath,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                ),
                                color = realms.fumbleRed,
                                modifier = Modifier.padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.s),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // ---- Stats row ----
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = RealmsSpacing.xl, vertical = RealmsSpacing.m),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GraveStat("Turns", entry.turns.toString(), MaterialTheme.colorScheme.primary)
                    GraveStat("XP", "${entry.xp}", MaterialTheme.colorScheme.secondary)
                    GraveStat("Gold", "${entry.gold}g", realms.goldAccent)
                    GraveStat(moralLabel, "${entry.morality}", moralColor)
                }
                HorizontalDivider(Modifier.padding(horizontal = RealmsSpacing.xl), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // ---- Companions ----
                if (entry.companions.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = RealmsSpacing.xl, vertical = 10.dp)) {
                        Text("COMPANIONS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            entry.companions.forEach { name ->
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
                                            contentAlignment = Alignment.Center
                                        ) { Text(name.take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
                                        Spacer(Modifier.width(6.dp))
                                        Text(name, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- Mutations ----
                if (entry.mutations.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = RealmsSpacing.xl, vertical = 6.dp)) {
                        Text("WORLD MUTATIONS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        entry.mutations.forEach { m ->
                            Text("• $m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // ---- Backstory ----
                entry.backstoryText?.let { bs ->
                    if (bs.isNotBlank()) {
                        Column(Modifier.padding(horizontal = RealmsSpacing.xl, vertical = 6.dp)) {
                            Text("BACKSTORY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(RealmsSpacing.xs))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    bs,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp),
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(horizontal = RealmsSpacing.xl, vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // ---- Timeline ----
                Column(Modifier.padding(horizontal = RealmsSpacing.xl, vertical = 6.dp)) {
                    Text("LIFE STORY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(RealmsSpacing.s))
                    if (entry.timeline.isEmpty()) {
                        Text("No story recorded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        entry.timeline.forEachIndexed { idx, t ->
                            Row(Modifier.padding(bottom = 8.dp)) {
                                // Timeline dot + line
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp)) {
                                    Text(categoryIcon(t.category), fontSize = 14.sp)
                                    if (idx < entry.timeline.lastIndex) {
                                        Box(
                                            Modifier
                                                .width(2.dp)
                                                .height(20.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            shape = MaterialTheme.shapes.extraSmall
                                        ) {
                                            Text(
                                                "T${t.turn}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            t.category.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(t.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }
                    }
                }

                // ---- Close button ----
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = RealmsSpacing.xl, vertical = RealmsSpacing.m).height(48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Rest in Peace", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun GraveStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
