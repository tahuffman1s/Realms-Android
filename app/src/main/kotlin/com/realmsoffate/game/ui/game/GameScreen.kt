package com.realmsoffate.game.ui.game

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.Choice
import com.realmsoffate.game.game.DisplayMessage
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.game.WeatherSystem
import com.realmsoffate.game.ui.dice.DiceRollerDialog
import com.realmsoffate.game.ui.map.WorldMapScreen
import com.realmsoffate.game.ui.overlays.InitiativeOverlay
import com.realmsoffate.game.ui.overlays.LevelUpOverlay
import com.realmsoffate.game.ui.overlays.RestOverlay
import com.realmsoffate.game.ui.overlays.ShopOverlay
import com.realmsoffate.game.ui.overlays.TargetPromptDialog
import com.realmsoffate.game.ui.overlays.TargetPromptSpec
import com.realmsoffate.game.ui.panels.*
import com.realmsoffate.game.ui.theme.NarrationBodyStyle
import com.realmsoffate.game.ui.theme.RealmsTheme
import com.realmsoffate.game.util.NarrationMarkdown

/** Which panel (or fullscreen page) is active. */
enum class Panel { None, Inventory, Quests, Party, Lore, Journal, Currency, Spells, Stats, Map }

/** Main bottom-nav tab — mirrors the web's 5-tab model. */
private enum class Tab(val label: String, val icon: String) {
    Chat("Chat", "\uD83D\uDCDC"),
    Items("Items", "\uD83C\uDF92"),
    Stats("Stats", "\uD83D\uDCCA"),
    Map("Map", "\uD83D\uDDFA\uFE0F"),
    More("More", "\u22EF")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: GameViewModel) {
    val state by vm.ui.collectAsState()
    var panel by remember { mutableStateOf(Panel.None) }
    var tab by remember { mutableStateOf(Tab.Chat) }
    var moreOpen by remember { mutableStateOf(false) }
    var choicesOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Export JSON launcher — wired to the More menu's Download tile.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val json = vm.exportCurrentJson()
            if (json != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                vm.postSystemMessage("Save exported.")
            } else {
                vm.postSystemMessage("Nothing to export yet.")
            }
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    // When a tab maps to a panel, open it. Chat is the main game view (no panel).
    LaunchedEffect(tab) {
        when (tab) {
            Tab.Chat -> Unit // stay on main
            Tab.Items -> panel = Panel.Inventory
            Tab.Stats -> panel = Panel.Stats
            Tab.Map -> panel = Panel.Map
            Tab.More -> moreOpen = true
        }
    }

    // Full-screen map takes over the whole screen.
    if (panel == Panel.Map) {
        WorldMapScreen(state = state, onClose = { panel = Panel.None; tab = Tab.Chat })
        return
    }

    Scaffold(
        topBar = { GameTopBar(state) },
        bottomBar = {
            Column {
                GameInputBar(
                    state = state,
                    input = input,
                    onInputChange = { input = it },
                    onSend = {
                        if (input.isNotBlank() && !state.isGenerating) {
                            val handled = handleSlashCommand(input, vm) { panel = it }
                            if (!handled) vm.submitAction(input)
                            input = ""
                            focus.clearFocus()
                        }
                    },
                    onTargetPrompt = { vm.requestTargetPrompt(it) }
                )
                BottomNav(
                    selected = tab,
                    hasChoices = state.currentChoices.isNotEmpty(),
                    onSelect = { t ->
                        // Allow selecting Chat to collapse any open panel.
                        if (t == Tab.Chat) { panel = Panel.None; moreOpen = false }
                        tab = t
                    }
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = state.currentChoices.isNotEmpty() && !state.isGenerating && panel == Panel.None && tab == Tab.Chat,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ChoicesFab(
                    count = state.currentChoices.size,
                    onClick = { choicesOpen = true }
                )
            }
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.End,
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            AnimatedVisibility(visible = state.currentScene != "default") {
                SceneBanner(state.currentScene, state.currentSceneDesc, state.timeOfDay)
            }
            // Combat HUD — visible only during battle scenes.
            state.combat?.let { combat -> CombatHud(combat) }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(state.messages) { msg ->
                    when (msg) {
                        is DisplayMessage.Player -> PlayerBubble(msg.text)
                        is DisplayMessage.Narration -> NarrationBlock(msg.text)
                        is DisplayMessage.Event -> EventCard(msg.icon, msg.title, msg.text)
                        is DisplayMessage.System -> SystemLine(msg.text)
                    }
                }
                if (state.isGenerating) {
                    item { NarratorThinking() }
                }
                state.error?.let {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    state.lastCheck?.let { check ->
        DiceRollerDialog(check, onClose = { vm.dismissLastCheck() })
    }

    state.deathSave?.let { saves ->
        DeathSaveDialog(
            saves = saves,
            onRoll = { vm.rollDeathSave() }
        )
    }

    val levelUp by vm.pendingLevelUpFlow.collectAsState()
    levelUp?.let { lv ->
        LevelUpOverlay(level = lv, onDismiss = { vm.dismissLevelUp() })
    }

    val restKind by vm.restOverlay.collectAsState()
    restKind?.let { kind ->
        RestOverlay(kind = kind, onDismiss = { vm.dismissRest() })
    }

    val showInitiative by vm.showInitiative.collectAsState()
    if (showInitiative) {
        InitiativeOverlay(onDismiss = { vm.dismissInitiative() })
    }

    val targetPrompt by vm.targetPrompt.collectAsState()
    targetPrompt?.let { spec ->
        TargetPromptDialog(
            spec = spec,
            onSubmit = { action ->
                vm.dismissTargetPrompt()
                vm.submitAction(action)
            },
            onSelf = {
                vm.dismissTargetPrompt()
                vm.submitAction("${spec.verb.trimEnd()} myself.")
            },
            onDismiss = { vm.dismissTargetPrompt() }
        )
    }

    val activeShop by vm.activeShop.collectAsState()
    val buybackStocks by vm.buybackStocks.collectAsState()
    activeShop?.let { merchantName ->
        val stock = state.merchantStocks[merchantName].orEmpty()
        val ch = state.character
        if (ch != null) {
            ShopOverlay(
                merchant = merchantName,
                stock = stock,
                character = ch,
                onBuy = { item, price -> vm.buyItem(merchantName, item, price) },
                onSell = { item, price -> vm.sellItem(merchantName, item, price) },
                onBuyback = { item, price -> vm.buybackItem(merchantName, item, price) },
                onHaggle = { cha -> vm.haggle(cha()) },
                buybackStock = buybackStocks[merchantName].orEmpty(),
                onClose = { vm.dismissShop() }
            )
        }
    }

    if (choicesOpen && state.currentChoices.isNotEmpty()) {
        ChoicesSheet(
            choices = state.currentChoices,
            onPick = { c -> choicesOpen = false; vm.pickChoice(c) },
            onDismiss = { choicesOpen = false }
        )
    }

    if (moreOpen) {
        MoreMenuSheet(
            onClose = { moreOpen = false; tab = Tab.Chat },
            onAction = { action ->
                moreOpen = false
                tab = Tab.Chat
                when (action) {
                    "save" -> { vm.saveToSlot(); vm.postSystemMessage("Saved.") }
                    "download" -> exportLauncher.launch(vm.exportFilename())
                    "shortrest" -> vm.shortRest()
                    "longrest" -> vm.longRest()
                    "menu" -> vm.returnToTitle()
                    "setup" -> vm.backToApiSetup()
                    else -> panel = when (action) {
                        "spells" -> Panel.Spells
                        "lore" -> Panel.Lore
                        "journal" -> Panel.Journal
                        "currency" -> Panel.Currency
                        "party" -> Panel.Party
                        "quests" -> Panel.Quests
                        else -> Panel.None
                    }
                }
            }
        )
    }

    when (panel) {
        Panel.Inventory -> InventoryPanel(state, onClose = { panel = Panel.None; tab = Tab.Chat }, onEquip = vm::equipToggle)
        Panel.Quests -> QuestsPanel(state, onClose = { panel = Panel.None; tab = Tab.Chat }, onAbandon = vm::abandonQuest)
        Panel.Party -> PartyPanel(state, onClose = { panel = Panel.None; tab = Tab.Chat }, onDismiss = vm::dismissCompanion)
        Panel.Lore -> LorePanel(state, onClose = { panel = Panel.None; tab = Tab.Chat })
        Panel.Journal -> JournalPanel(state, onClose = { panel = Panel.None; tab = Tab.Chat })
        Panel.Currency -> CurrencyPanel(
            state = state,
            onClose = { panel = Panel.None; tab = Tab.Chat },
            onExchange = vm::exchange
        )
        Panel.Spells -> SpellsPanel(
            state = state,
            onClose = { panel = Panel.None; tab = Tab.Chat },
            onHotbar = vm::updateHotbar,
            onCast = { spell ->
                val recentTargets = state.npcLog
                    .sortedByDescending { it.lastSeenTurn }
                    .take(8).map { it.name }
                panel = Panel.None
                tab = Tab.Chat
                vm.requestTargetPrompt(
                    TargetPromptSpec(
                        title = "Cast ${spell.name}",
                        verb = "I cast ${spell.name} at",
                        selfCastable = isSelfCastable(spell),
                        recentTargets = recentTargets
                    )
                )
            }
        )
        Panel.Stats -> StatsPanel(state, onClose = { panel = Panel.None; tab = Tab.Chat })
        else -> {}
    }
}

// ============================================================
// TOP BAR — 2 tight rows matching the web spec
// ============================================================

@Composable
private fun GameTopBar(state: GameUiState) {
    val ch = state.character ?: return
    val realms = RealmsTheme.colors
    val location = state.worldMap?.locations?.getOrNull(state.currentLoc)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // ----- Row 1: name | level badge | combat | time | weather | party icons
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ch.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                LevelBadge(ch.level)
                if (state.currentScene == "battle") {
                    Spacer(Modifier.width(6.dp))
                    CombatIndicator()
                }
                Spacer(Modifier.weight(1f))
                TimeIcon(state.timeOfDay)
                Spacer(Modifier.width(4.dp))
                WeatherIcon(state.weather, isNight = state.timeOfDay == "night")
                if (state.party.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    PartyIcons(state.party.map { it.name })
                }
            }
            Spacer(Modifier.height(8.dp))
            // ----- Row 2: HP text + bar | XP text + bar | gold | location chip
            Row(verticalAlignment = Alignment.CenterVertically) {
                HpInline(ch.hp, ch.maxHp, Modifier.weight(1.2f))
                Spacer(Modifier.width(10.dp))
                XpInline(ch.xp, ch.level, Modifier.weight(1.1f))
                Spacer(Modifier.width(10.dp))
                GoldInline("${ch.gold}", realms.goldAccent)
                if (location != null) {
                    Spacer(Modifier.width(8.dp))
                    LocationInline(location.icon, location.name)
                }
            }
            // ----- Row 3 (only when present): active conditions strip -----
            if (ch.conditions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ConditionsStrip(ch.conditions)
            }
        }
    }
}

@Composable
private fun ConditionsStrip(conditions: List<String>) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        conditions.forEach { c -> ConditionChip(c) }
    }
}

@Composable
private fun ConditionChip(name: String) {
    val realms = RealmsTheme.colors
    val (color, icon) = when (name.lowercase()) {
        "poisoned" -> realms.success to "\uD83E\uDDEA"
        "blessed" -> realms.goldAccent to "\u2728"
        "cursed", "doomed" -> realms.fumbleRed to "\uD83D\uDC80"
        "frightened", "fearful" -> realms.warning to "\uD83D\uDE28"
        "charmed" -> Color(0xFFFF8AC6) to "\uD83D\uDC95"
        "paralyzed", "stunned" -> realms.info to "\u2744\uFE0F"
        "invisible" -> realms.info to "\uD83D\uDC7B"
        "silenced" -> realms.info to "\uD83E\uDD2B"
        "blinded" -> realms.warning to "\uD83D\uDC41\uFE0F"
        "burning", "on fire" -> realms.fumbleRed to "\uD83D\uDD25"
        "bleeding" -> realms.fumbleRed to "\uD83E\uDE78"
        "exhausted", "fatigued" -> realms.warning to "\uD83D\uDE2B"
        "prone" -> realms.warning to "\u2B07\uFE0F"
        "marked", "branded" -> realms.fumbleRed to "\uD83C\uDFAF"
        "raging" -> realms.fumbleRed to "\uD83D\uDE21"
        "hidden", "stealth" -> realms.info to "\uD83D\uDC41\uFE0F"
        else -> realms.info to "\u2728"
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(3.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LevelBadge(level: Int) {
    val realms = RealmsTheme.colors
    Surface(
        color = realms.goldAccent.copy(alpha = 0.18f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            "L$level",
            style = MaterialTheme.typography.labelMedium,
            color = realms.goldAccent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CombatIndicator() {
    val infinite = rememberInfiniteTransition(label = "combatPulse")
    val alpha by infinite.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "cmbAlpha"
    )
    Text(
        "\u2694\uFE0F",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error.copy(alpha = alpha)
    )
}

@Composable
private fun TimeIcon(phase: String) {
    val emoji = when (phase) {
        "dawn" -> "\uD83C\uDF05"
        "day" -> "\u2600\uFE0F"
        "dusk" -> "\uD83C\uDF06"
        "night" -> "\uD83C\uDF1B"
        else -> "\u2600\uFE0F"
    }
    Text(emoji, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun WeatherIcon(weatherId: String, isNight: Boolean) {
    if (weatherId == "clear" && !isNight) return // don't clutter for the default case
    val icon = WeatherSystem.iconFor(weatherId, isNight)
    Text(icon, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun PartyIcons(names: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        names.take(3).forEach { n ->
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(n.take(1).uppercase(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        if (names.size > 3) {
            Text(
                "+${names.size - 3}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun HpInline(hp: Int, maxHp: Int, modifier: Modifier = Modifier) {
    val realms = RealmsTheme.colors
    val pct = (hp.toFloat() / maxHp.toFloat()).coerceIn(0f, 1f)
    val color = when {
        pct < 0.33f -> MaterialTheme.colorScheme.error
        pct < 0.66f -> realms.warning
        else -> realms.success
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                "$hp/$maxHp",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun XpInline(xp: Int, level: Int, modifier: Modifier = Modifier) {
    val base = if (level <= 1) 0 else GameViewModel.levelThreshold(level)
    val next = GameViewModel.levelThreshold(level + 1)
    val span = (next - base).coerceAtLeast(1)
    val pct = ((xp - base).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                "$xp / $next",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun GoldInline(value: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("\uD83D\uDCB0", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}

@Composable
private fun LocationInline(icon: String, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(2.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp)
        )
    }
}

// ============================================================
// INPUT BAR + BOTTOM NAV + FLOATING CHOICES FAB
// ============================================================

@Composable
private fun GameInputBar(
    state: GameUiState,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onTargetPrompt: (TargetPromptSpec) -> Unit
) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        // Action bar — attack + spell hotbar. Each chip opens the target prompt
        // so the player explicitly chooses a target (or picks "On self" for
        // self-castable spells) before the narrator sees it. Hidden if nothing
        // to show, keeping the non-combat UI clean.
        state.character?.let { ch ->
            val hotbar = state.hotbar
            val anyActions = hotbar.any { it != null } || state.currentScene == "battle"
            if (anyActions) {
                val recentTargets = state.npcLog
                    .sortedByDescending { it.lastSeenTurn }
                    .take(8).map { it.name }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionChip("⚔️ Attack", onClick = {
                        onTargetPrompt(
                            TargetPromptSpec(
                                title = "Light Attack",
                                verb = "I attack",
                                recentTargets = recentTargets
                            )
                        )
                    })
                    ActionChip("🗡️ Heavy", onClick = {
                        onTargetPrompt(
                            TargetPromptSpec(
                                title = "Heavy Attack",
                                verb = "I strike with a heavy blow at",
                                recentTargets = recentTargets
                            )
                        )
                    })
                    hotbar.forEachIndexed { idx, spell ->
                        if (spell != null) {
                            val spellDef = com.realmsoffate.game.game.Spells.find(spell)
                            val selfCast = spellDef?.let { isSelfCastable(it) } ?: false
                            SpellChip(slot = idx + 1, name = spell, onClick = {
                                onTargetPrompt(
                                    TargetPromptSpec(
                                        title = "Cast $spell",
                                        verb = "I cast $spell at",
                                        selfCastable = selfCast,
                                        recentTargets = recentTargets
                                    )
                                )
                            })
                        }
                    }
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("What do you do?", style = MaterialTheme.typography.bodyMedium) },
                shape = RoundedCornerShape(24.dp),
                singleLine = false,
                maxLines = 3
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = input.isNotBlank() && !state.isGenerating,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

/**
 * A spell is self-castable when its description targets the caster — heals,
 * buffs, class resources, and the canonical list of martial self-effects.
 */
private fun isSelfCastable(spell: com.realmsoffate.game.game.Spell): Boolean {
    val name = spell.name.lowercase()
    if (spell.school == "Martial") return true
    if ("cure wounds" in name || "healing word" in name || "lay on hands" in name) return true
    if ("shield" == name || "true strike" == name || "misty step" in name) return true
    if ("bless" == name) return true
    if ("hunter's mark" in name) return false // targets enemy
    // Default: heals/buffs that mention "yourself" / "you" / "a creature" in desc.
    val desc = spell.desc.lowercase()
    return "yourself" in desc || "you gain" in desc || "you heal" in desc
}

@Composable
private fun SpellChip(slot: Int, name: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$slot",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(4.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BottomNav(
    selected: Tab,
    hasChoices: Boolean,
    onSelect: (Tab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Tab.values().forEach { t ->
            NavigationBarItem(
                selected = t == selected,
                onClick = { onSelect(t) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (t == Tab.Chat && hasChoices) Badge()
                        }
                    ) {
                        Text(t.icon, style = MaterialTheme.typography.titleMedium)
                    }
                },
                label = { Text(t.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun ChoicesFab(count: Int, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
        icon = { Text("\uD83D\uDCDC", style = MaterialTheme.typography.titleLarge) },
        text = { Text("$count", fontWeight = FontWeight.Bold) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoicesSheet(
    choices: List<Choice>,
    onPick: (Choice) -> Unit,
    onDismiss: () -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("YOUR CHOICES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            choices.forEach { c -> ChoiceTile(c, onClick = { onPick(c) }) ; Spacer(Modifier.height(8.dp)) }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreMenuSheet(
    onClose: () -> Unit,
    onAction: (String) -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val items = listOf(
        "\uD83D\uDCBE" to ("Save" to "save"),
        "\uD83D\uDCE5" to ("Download" to "download"),
        "\uD83D\uDCC2" to ("Load" to "menu"),
        "\uD83C\uDFE0" to ("Main Menu" to "menu"),
        "\uD83D\uDD2E" to ("Spells" to "spells"),
        "\uD83D\uDCDC" to ("Lore" to "lore"),
        "\uD83D\uDCD3" to ("Journal" to "journal"),
        "\uD83D\uDCB1" to ("Currency" to "currency"),
        "\uD83D\uDC65" to ("Party" to "party"),
        "\uD83D\uDCCB" to ("Quests" to "quests"),
        "\u26FA" to ("Short Rest" to "shortrest"),
        "\uD83C\uDFD5\uFE0F" to ("Long Rest" to "longrest"),
        "\u2699\uFE0F" to ("Setup" to "setup")
    )
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("MORE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                items(items) { (emoji, pair) ->
                    val (label, action) = pair
                    MoreTile(emoji = emoji, label = label, onClick = { onAction(action) })
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

@Composable
private fun MoreTile(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ============================================================
// SCENE / NARRATION / CHOICES BUBBLES (kept from earlier pass)
// ============================================================

/**
 * Handles /commands. Returns true if the input was consumed.
 */
private fun handleSlashCommand(
    input: String,
    vm: GameViewModel,
    openPanel: (Panel) -> Unit
): Boolean {
    val cmd = input.trim()
    if (!cmd.startsWith("/")) return false
    val tokens = cmd.removePrefix("/").lowercase().trim().split(Regex("\\s+"))
    val root = tokens.firstOrNull() ?: return true
    when (root) {
        "help" -> vm.postSystemMessage(
            "Commands: /save /map /inv /stats /spells /lore /journal /currency /party /quest /rest /shortrest /menu /help"
        )
        "save" -> { vm.saveToSlot(); vm.postSystemMessage("Saved.") }
        "map" -> openPanel(Panel.Map)
        "inv", "bag", "items" -> openPanel(Panel.Inventory)
        "stats", "sheet" -> openPanel(Panel.Stats)
        "spells" -> openPanel(Panel.Spells)
        "lore" -> openPanel(Panel.Lore)
        "journal", "npcs" -> openPanel(Panel.Journal)
        "currency", "coin", "money" -> openPanel(Panel.Currency)
        "party" -> openPanel(Panel.Party)
        "quest", "quests" -> openPanel(Panel.Quests)
        "rest", "longrest" -> vm.longRest()
        "shortrest" -> vm.shortRest()
        "menu", "title" -> vm.returnToTitle()
        else -> vm.postSystemMessage("Unknown command: /$root. Type /help.")
    }
    return true
}

@Composable
private fun CombatHud(combat: com.realmsoffate.game.game.CombatState) {
    val realms = RealmsTheme.colors
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\u2694\uFE0F", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    "ROUND ${combat.round}",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                combat.active?.let { active ->
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "TURN: ${active.name.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                combat.order.forEachIndexed { idx, c ->
                    InitiativeChip(c, active = idx == combat.activeIndex)
                }
            }
        }
    }
}

@Composable
private fun InitiativeChip(c: com.realmsoffate.game.game.Combatant, active: Boolean) {
    val realms = RealmsTheme.colors
    val pct = (c.hp.toFloat() / c.maxHp.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val hpColor = when {
        pct < 0.33f -> MaterialTheme.colorScheme.error
        pct < 0.66f -> realms.warning
        else -> realms.success
    }
    Surface(
        color = if (active) MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.border(
            1.dp,
            if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(10.dp)
        )
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.isPlayer) Text("\u2605", style = MaterialTheme.typography.labelSmall, color = realms.goldAccent)
                Text(
                    c.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "i${c.initiative}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${c.hp}/${c.maxHp}",
                    style = MaterialTheme.typography.labelSmall,
                    color = hpColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .width(60.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(pct)
                            .fillMaxHeight()
                            .background(hpColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeathSaveDialog(
    saves: com.realmsoffate.game.game.DeathSaveState,
    onRoll: () -> Unit
) {
    val realms = RealmsTheme.colors
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { /* no dismiss — must resolve by rolling */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.widthIn(min = 300.dp)
            ) {
                Column(
                    Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "\uD83D\uDC80",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "DEATH SAVES",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 6.sp),
                        color = realms.fumbleRed
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "You're bleeding out. Roll d20 — ≥10 succeeds, <10 fails. 3 successes stabilise, 3 failures end it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScoreCol("SUCCESSES", saves.successes, realms.success)
                        ScoreCol("FAILURES", saves.failures, realms.fumbleRed)
                    }
                    if (saves.rolls.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Rolls: " + saves.rolls.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onRoll,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = realms.fumbleRed,
                            contentColor = Color.White
                        )
                    ) { Text("ROLL SAVE", fontWeight = FontWeight.Bold, letterSpacing = 3.sp) }
                }
            }
        }
    }
}

@Composable
private fun ScoreCol(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < count) color
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(1.dp, color, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun SceneBanner(scene: String, desc: String, timeOfDay: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(sceneEmoji(scene), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(scene.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                if (desc.isNotBlank()) Text(
                    desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Text(timeOfDayEmoji(timeOfDay), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun sceneEmoji(s: String): String = when (s) {
    "cave" -> "\uD83D\uDD73\uFE0F"; "forest" -> "\uD83C\uDF32"; "tavern" -> "\uD83C\uDF7A"
    "battle" -> "\u2694\uFE0F"; "dungeon" -> "\uD83C\uDFF0"; "town" -> "\uD83C\uDFD8\uFE0F"
    "mountain" -> "\u26F0\uFE0F"; "camp" -> "\u26FA"; "ruins" -> "\uD83D\uDDFF"
    "castle" -> "\uD83C\uDFF0"; "swamp" -> "\uD83D\uDD79\uFE0F"; "ocean" -> "\uD83C\uDF0A"
    "desert" -> "\uD83C\uDFDC\uFE0F"; "temple" -> "\u26EA"; "road" -> "\uD83D\uDEE3\uFE0F"
    "underground" -> "\uD83D\uDD73\uFE0F"; else -> "\uD83C\uDF0C"
}

private fun timeOfDayEmoji(t: String): String = when (t) {
    "dawn" -> "\uD83C\uDF05"; "day" -> "\u2600\uFE0F"
    "dusk" -> "\uD83C\uDF06"; "night" -> "\uD83C\uDF1B"; else -> "\u2600\uFE0F"
}

@Composable
private fun PlayerBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                "\u201C$text\u201D",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun NarrationBlock(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            NarrationMarkdown(text, baseStyle = NarrationBodyStyle)
        }
    }
}

@Composable
private fun EventCard(icon: String, title: String, text: String) {
    val realms = RealmsTheme.colors
    Surface(
        color = realms.warning.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, realms.warning.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(
                    "WORLD EVENT · ${title.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = realms.warning
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text, style = NarrationBodyStyle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SystemLine(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun NarratorThinking() {
    val infinite = rememberInfiniteTransition(label = "think")
    val alpha by infinite.animateFloat(
        0.35f, 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.75f)))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.5f)))
        Spacer(Modifier.width(12.dp))
        Text(
            "The narrator weighs your words\u2026",
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChoiceTile(c: Choice, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("${c.n}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (c.skill.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        c.skill.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
