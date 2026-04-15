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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.realmsoffate.game.data.Choice
import com.realmsoffate.game.data.NarrationSegmentData
import com.realmsoffate.game.game.DisplayMessage
import com.realmsoffate.game.game.GameUiState
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.ui.dice.DiceRollerDialog
import com.realmsoffate.game.ui.map.WorldMapScreen
import com.realmsoffate.game.ui.overlays.FeatSelectionOverlay
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

/** Composition-local font scale. 1.0 = default, 0.7 = smallest, 1.6 = largest. */
val LocalFontScale = compositionLocalOf { 1.0f }

/** Which panel (or fullscreen page) is active. */
enum class Panel { None, Inventory, Quests, Party, Lore, Journal, Currency, Spells, Stats, Map, Memories, Settings }

/** Main bottom-nav tab — mirrors the web's 5-tab model. */
private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Chat("Chat", Icons.Filled.ChatBubble),
    Items("Items", Icons.Filled.Backpack),
    Stats("Stats", Icons.Filled.QueryStats),
    Map("Map", Icons.Filled.Map),
    More("More", Icons.Filled.MoreHoriz)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: GameViewModel) {
    val state by vm.ui.collectAsState()
    var panel by remember { mutableStateOf(Panel.None) }
    var journalFocusNpc by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(Tab.Chat) }
    var moreOpen by remember { mutableStateOf(false) }
    var choicesOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val fontScale by vm.fontScale.collectAsState()

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

    // Debug dump — writes straight to the app's scoped external-storage dir
    // (/sdcard/Android/data/com.realmsoffate.game/files/debug/) with no file
    // picker. The desktop `pulldebug` fish function yanks the newest file into
    // ~/Downloads/ over ADB, ready for Claude to read.
    fun dumpDebugToFile() {
        val dir = java.io.File(context.getExternalFilesDir(null), "debug")
        if (!dir.exists()) dir.mkdirs()
        val filename = vm.debugDumpFilename()
        val file = java.io.File(dir, filename)
        runCatching {
            file.writeText(vm.exportDebugDump())
            vm.postSystemMessage("Debug dump saved: $filename. Run 'pulldebug' on the PC.")
        }.onFailure {
            vm.postSystemMessage("Debug dump failed: ${it.message}")
        }
    }

    // Auto-scroll: jump to the player bubble that started the current turn so
    // the entire turn reads top-down. Falls back to the latest message when no
    // player bubble has been recorded yet (e.g. seed/intro narration).
    LaunchedEffect(state.messages.size, state.turnStartIndex) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val target = if (state.turnStartIndex in state.messages.indices)
            state.turnStartIndex else state.messages.size - 1
        listState.animateScrollToItem(target)
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
        WorldMapScreen(
            state = state,
            onClose = { panel = Panel.None; tab = Tab.Chat },
            onTravel = { loc -> vm.startTravel(loc.id); panel = Panel.None; tab = Tab.Chat },
            onCancelTravel = { vm.cancelTravel() },
            isTraveling = state.travelState != null
        )
        return
    }

    CompositionLocalProvider(
        LocalFontScale provides fontScale
    ) {

    Scaffold(
        topBar = { GameTopBar(state) },
        bottomBar = {
            // imePadding keeps the input + hotbar visible above the soft keyboard.
            Column(Modifier.imePadding()) {
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
                    onTargetPrompt = { vm.requestTargetPrompt(it) },
                    onSpellsOpen = { panel = Panel.Spells }
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
            AnimatedVisibility(visible = state.currentScene != "default" && state.combat == null) {
                SceneBanner(state.currentScene, state.currentSceneDesc)
            }
            // Combat HUD — visible only during battle scenes.
            state.combat?.let { combat -> CombatHud(combat, state.npcLog, state.turns) }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(state.messages) { idx, msg ->
                    when (msg) {
                        is DisplayMessage.Player -> PlayerBubble(msg.text, state.character?.name)
                        is DisplayMessage.Narration -> {
                            NarrationBlock(
                                msg.text, state.character?.name, msg, msg.segments,
                                npcLog = state.npcLog,
                                isLatestTurn = idx == state.messages.lastIndex || (idx == state.messages.size - 2 && state.messages.lastOrNull() is DisplayMessage.System),
                                bookmarks = state.bookmarks,
                                onToggleBookmark = { vm.toggleBookmark(it) },
                                onNpcTap = { /* future use */ },
                                onNpcReply = { npcName ->
                                    // Pre-fill input with reply context
                                    input = "I say to $npcName: "
                                    focus.clearFocus()
                                },
                                onNpcReaction = { npcName, npcQuote, reaction ->
                                    val ctx = npcQuote.take(120)
                                    val reactionText = if (reaction.startsWith("emoji:")) {
                                        val emoji = reaction.removePrefix("emoji:")
                                        "I react to $npcName saying \"$ctx\" with $emoji (interpret this emoji's real-world cultural meaning and roleplay my character's reaction)"
                                    } else when (reaction) {
                                        "approve" -> "I nod approvingly at $npcName's words: \"$ctx\""
                                        "disapprove" -> "I shake my head at $npcName after hearing: \"$ctx\""
                                        "laugh" -> "I laugh at $npcName saying: \"$ctx\""
                                        "angry" -> "I glare angrily at $npcName for saying: \"$ctx\""
                                        "question" -> "I question what $npcName meant by: \"$ctx\""
                                        "shocked" -> "I stare at $npcName in shock after hearing: \"$ctx\""
                                        "suspicious" -> "I narrow my eyes suspiciously at $npcName after: \"$ctx\""
                                        else -> "I react to $npcName saying: \"$ctx\""
                                    }
                                    vm.submitAction(reactionText)
                                },
                                onAttackNpc = { npcName ->
                                    vm.requestTargetPrompt(com.realmsoffate.game.ui.overlays.TargetPromptSpec(
                                        title = "Attack $npcName",
                                        verb = "I attack $npcName",
                                        recentTargets = listOf(npcName)
                                    ))
                                },
                                onOpenJournal = { npcName ->
                                    journalFocusNpc = npcName
                                    panel = Panel.Journal
                                },
                                onOpenStats = {
                                    panel = Panel.Stats
                                }
                            )
                        }
                        is DisplayMessage.Event -> EventCard(msg.icon, msg.title, msg.text)
                        is DisplayMessage.System -> SystemLine(msg.text)
                    }
                }
                if (state.isGenerating) {
                    item { NarratorThinking() }
                }
                if (state.availableMerchants.isNotEmpty() && !state.isGenerating) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.availableMerchants.forEach { merchant ->
                                Surface(
                                    onClick = { vm.openShop(merchant) },
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Store, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(merchant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                state.error?.let { err ->
                    item {
                        LaunchedEffect(err) {
                            kotlinx.coroutines.delay(5000)
                            vm.clearError()
                        }
                        Card(
                            onClick = { vm.clearError() },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                err,
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

    state.preRoll?.let { pre ->
        PreRollDialog(
            pre = pre,
            onConfirm = { vm.confirmPreRoll() }
        )
    }

    state.deathSave?.let { saves ->
        DeathSaveDialog(
            saves = saves,
            onRoll = { vm.rollDeathSave() }
        )
    }

    val levelUp by vm.pendingLevelUpFlow.collectAsState()
    levelUp?.let { lv ->
        val statPts by vm.pendingStatPoints.collectAsState()
        LevelUpOverlay(
            level = lv,
            statPoints = statPts,
            onAssignStat = { vm.assignStatPoint(it) },
            onDismiss = { vm.dismissLevelUp() }
        )
    }

    val showFeat by vm.pendingFeat.collectAsState()
    if (showFeat) {
        FeatSelectionOverlay(
            currentFeats = state.character?.feats.orEmpty(),
            onSelect = { vm.selectFeat(it) },
            onDismiss = { vm.dismissFeat() }
        )
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
                    "debug" -> dumpDebugToFile()
                    "shortrest" -> vm.shortRest()
                    "longrest" -> vm.longRest()
                    "menu" -> vm.returnToTitle()
                    "setup" -> vm.backToApiSetup()
                    "settings" -> panel = Panel.Settings
                    else -> panel = when (action) {
                        "spells" -> Panel.Spells
                        "lore" -> Panel.Lore
                        "journal" -> Panel.Journal
                        "memories" -> Panel.Memories
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
        Panel.Journal -> JournalPanel(state, focusNpc = journalFocusNpc, onClose = { journalFocusNpc = null; panel = Panel.None; tab = Tab.Chat })
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
                val currentLocName = state.worldMap?.locations?.getOrNull(state.currentLoc)?.name.orEmpty()
                val activeCombat = state.combat
                val recentTargets = if (activeCombat != null) {
                    // During combat, show entities from the initiative order
                    activeCombat.order
                        .filter { !it.isPlayer }
                        .map { it.name }
                        .distinct()
                        .take(8)
                } else {
                    val nearbyNpcs = state.npcLog
                        .filter { it.lastLocation == currentLocName }
                        .sortedByDescending { it.lastSeenTurn }
                        .take(6)
                        .map { it.name }
                    (nearbyNpcs + state.party.map { it.name }).distinct().take(8)
                }
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
        Panel.Memories -> MemoriesPanel(
            state,
            onClose = { panel = Panel.None; tab = Tab.Chat },
            onDelete = { vm.removeBookmark(it) }
        )
        Panel.Settings -> SettingsPanel(
            fontScale = fontScale,
            onFontScaleChange = { vm.setFontScale(it) },
            onClose = { panel = Panel.None; tab = Tab.Chat }
        )
        else -> {}
    }

    val tutorialStep by vm.tutorialStep.collectAsState()
    tutorialStep?.let { step ->
        TutorialOverlay(
            step = step,
            onNext = { vm.advanceTutorial() },
            onDismiss = { vm.dismissTutorial() }
        )
    }

    } // end CompositionLocalProvider
}

// ============================================================
// MEMORIES PANEL — bookmarked narration/event moments
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoriesPanel(state: GameUiState, onClose: () -> Unit, onDelete: (String) -> Unit = {}) {
    val realms = RealmsTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bookmark, null, Modifier.size(20.dp), tint = realms.goldAccent)
                Spacer(Modifier.width(8.dp))
                Text("MEMORIES", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))
            if (state.bookmarks.isEmpty()) {
                Text(
                    "No moments pinned yet.\nTap the bookmark icon on any message bubble to save it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.bookmarks) { text ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Filled.Bookmark,
                                    null,
                                    Modifier.size(16.dp).padding(top = 2.dp),
                                    tint = realms.goldAccent
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDelete(text) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        "Remove",
                                        Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

// ============================================================
// SETTINGS PANEL — bubble size + text size
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPanel(
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onClose: () -> Unit
) {
    val realms = RealmsTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, null, Modifier.size(20.dp), tint = realms.goldAccent)
                Spacer(Modifier.width(8.dp))
                Text("SETTINGS", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(20.dp))

            Text(
                "FONT SIZE",
                style = MaterialTheme.typography.labelLarge,
                color = realms.goldAccent,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Adjusts text size across all chat bubbles",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // Preview text at current scale
            Surface(
                color = Color(0xFF1A1030).copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "The tavern door creaks open. A cold wind follows you inside.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = (15f * fontScale).sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = Color(0xFFE8E1F0),
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Slider
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "A",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = fontScale,
                    onValueChange = { onFontScaleChange(it) },
                    valueRange = 0.7f..1.6f,
                    steps = 0,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "A",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "${"%.0f".format(fontScale * 100)}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

// ============================================================
// TUTORIAL OVERLAY — guided first-play walkthrough
// ============================================================

@Composable
private fun TutorialOverlay(step: Int, onNext: () -> Unit, onDismiss: () -> Unit) {
    val realms = RealmsTheme.colors

    data class TutStep(val title: String, val message: String, val icon: String, val alignment: Alignment)

    val steps = listOf(
        TutStep("Welcome, Adventurer", "Welcome to Realms! The narrator will tell your story. Each turn, the world reacts to your choices. Let's learn the basics.", "\u2694\uFE0F", Alignment.Center),
        TutStep("The Story Feed", "This is the story feed. The narrator describes the world, NPCs talk in colored bubbles, and your choices play out here.", "\uD83D\uDCDC", Alignment.Center),
        TutStep("Your Actions", "Type what you want to do here. Say anything — talk to NPCs, examine objects, cast spells, or just explore.", "\u270D\uFE0F", Alignment.BottomCenter),
        TutStep("Choices", "After each turn, you'll get choices. Tap the choices button to see them — or type your own action!", "\uD83C\uDFAD", Alignment.BottomEnd),
        TutStep("Dice Rolls", "When you pick a skill-based action, you'll roll a d20. Watch it spin — the narrator decides if you pass based on the total.", "\uD83C\uDFB2", Alignment.Center),
        TutStep("Navigation", "Use the bottom tabs to check your inventory, stats, map, and more.", "\uD83E\uDDED", Alignment.BottomCenter),
        TutStep("Swipe Actions", "Swipe right on any message to examine. Swipe left on NPC dialogue to attack.", "\uD83D\uDC46", Alignment.Center),
        TutStep("Bookmarks", "Tap the bookmark icon on any bubble to save favourite moments to your Memories.", "\uD83D\uDD16", Alignment.Center),
        TutStep("You're Ready!", "Go forth, adventurer. Make choices. Face consequences. Try not to die on the first turn.\n\n...No promises from the narrator.", "\uD83C\uDF1F", Alignment.Center)
    )

    val current = steps.getOrNull(step) ?: return
    val isLast = step == steps.lastIndex

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onNext),
        contentAlignment = current.alignment
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 340.dp)
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(current.icon, fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    current.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = realms.goldAccent,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                // Progress dots
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(if (i == step) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        i == step -> realms.goldAccent
                                        i < step  -> realms.goldAccent.copy(alpha = 0.4f)
                                        else      -> MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = if (isLast) onDismiss else onNext,
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (isLast) "Begin!" else "Next",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
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
            // ----- Row 1: name | level badge | combat indicator | party icons
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
                if (state.party.isNotEmpty()) {
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
    onTargetPrompt: (TargetPromptSpec) -> Unit,
    onSpellsOpen: () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth()) {
        // ---- Action bar — attack/heavy/spells chips, visible during combat ----
        state.character?.let { ch ->
            if (state.currentScene == "battle") {
                val recentTargets = if (state.combat != null) {
                    state.combat.order
                        .filter { !it.isPlayer }
                        .map { it.name }
                        .distinct()
                        .take(8)
                } else {
                    val currentLocName = state.worldMap?.locations?.getOrNull(state.currentLoc)?.name.orEmpty()
                    val nearbyNpcs = state.npcLog
                        .filter { it.lastLocation == currentLocName }
                        .sortedByDescending { it.lastSeenTurn }
                        .take(6)
                        .map { it.name }
                    (nearbyNpcs + state.party.map { it.name }).distinct().take(8)
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionChip(Icons.Filled.GpsFixed, "Attack", onClick = {
                            onTargetPrompt(
                                TargetPromptSpec(
                                    title = "Light Attack",
                                    verb = "I attack",
                                    recentTargets = recentTargets
                                )
                            )
                        })
                        ActionChip(Icons.Filled.Bolt, "Heavy", onClick = {
                            onTargetPrompt(
                                TargetPromptSpec(
                                    title = "Heavy Attack",
                                    verb = "I strike with a heavy blow at",
                                    recentTargets = recentTargets
                                )
                            )
                        })
                        ActionChip(Icons.Filled.AutoAwesome, "Spells", onClick = { onSpellsOpen() })
                    }
                }
            }
        }
        // ---- Input row (its own surface so the divider between hotbar +
        //      input is visually obvious on mobile) ----
        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
                    modifier = Modifier.size(52.dp),  // bigger send target
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
}

@Composable
private fun ActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(46.dp)  // bigger touch target
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
        }
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
private fun SpellChip(slot: Int, name: String, icon: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(46.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$slot",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(icon, fontSize = 16.sp)
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
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
        tonalElevation = 6.dp,
        modifier = Modifier.navigationBarsPadding()
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
                        Icon(t.icon, contentDescription = t.label)
                    }
                },
                label = {
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}

@Composable
private fun ChoicesFab(count: Int, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
    ) {
        Icon(Icons.Filled.Checklist, contentDescription = "Choices")
    }
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
        Icons.Filled.Save to ("Save" to "save"),
        Icons.Filled.Download to ("Download" to "download"),
        Icons.Filled.FolderOpen to ("Load" to "menu"),
        Icons.Filled.Home to ("Main Menu" to "menu"),
        Icons.Filled.AutoAwesome to ("Spells" to "spells"),
        Icons.AutoMirrored.Filled.MenuBook to ("Lore" to "lore"),
        Icons.Filled.Book to ("Journal" to "journal"),
        Icons.Filled.BookmarkBorder to ("Memories" to "memories"),
        Icons.Filled.CurrencyExchange to ("Currency" to "currency"),
        Icons.Filled.Groups to ("Party" to "party"),
        @Suppress("DEPRECATION") Icons.Filled.Assignment to ("Quests" to "quests"),
        Icons.Filled.HotelClass to ("Short Rest" to "shortrest"),
        Icons.Filled.NightsStay to ("Long Rest" to "longrest"),
        Icons.Filled.Tune to ("Settings" to "settings"),
        Icons.Filled.BugReport to ("Debug Dump" to "debug"),
        Icons.Filled.Settings to ("Setup" to "setup")
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
                items(items) { (icon, pair) ->
                    val (label, action) = pair
                    MoreTile(icon = icon, label = label, onClick = { onAction(action) })
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

@Composable
private fun MoreTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
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

/**
 * Pre-roll preview — the player sees the d20 + breakdown BEFORE the action
 * is sent to the narrator. Designed to read like a small ritual:
 *
 *   YOU ATTEMPT
 *   ┃ "I attack the goblin"
 *
 *           ◆ 14 ◆            (hex d20 silhouette, large numeral)
 *
 *   ATHLETICS · STR check
 *
 *   d20 roll              14
 *   Ability (STR)          +3
 *   Proficiency            +2
 *   ─────────────────────────
 *   TOTAL                  19
 *
 *   The narrator sets the DC.
 *
 *   [ Take Back ]   [ Send It ]
 */
@Composable
private fun PreRollDialog(
    pre: com.realmsoffate.game.game.PreRollDisplay,
    onConfirm: () -> Unit
) {
    val realms = RealmsTheme.colors
    val dieColor = when {
        pre.roll == 20 -> realms.critGold
        pre.roll == 1 -> realms.fumbleRed
        else -> MaterialTheme.colorScheme.primary
    }
    val glowColor = when {
        pre.roll == 20 -> realms.critGlow
        pre.roll == 1 -> realms.fumbleGlow
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    }
    // Animated entrance — quick scale-in + fade so it feels like a card flipping.
    val entrance = remember(pre) { androidx.compose.animation.core.Animatable(0.85f) }
    val alpha = remember(pre) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(pre) {
        alpha.animateTo(1f, androidx.compose.animation.core.tween(160))
        entrance.animateTo(1f, androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ))
    }
    var displayedRoll by remember(pre) { mutableIntStateOf((1..20).random()) }
    var spinComplete by remember(pre) { mutableStateOf(false) }
    LaunchedEffect(pre) {
        // Fast spin phase: 15 random numbers at ~50ms each
        repeat(15) {
            displayedRoll = (1..20).random()
            kotlinx.coroutines.delay(50)
        }
        // Deceleration phase: 8 numbers, slowing down
        val delays = listOf(80L, 100L, 130L, 170L, 220L, 280L, 350L, 450L)
        delays.forEach { d ->
            displayedRoll = (1..20).random()
            kotlinx.coroutines.delay(d)
        }
        // Final reveal
        displayedRoll = pre.roll
        spinComplete = true
    }
    // Pulse the halo when crit/fumble — subtle but it sells the moment.
    val haloPulse = if (pre.crit) {
        val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "preroll-halo")
        infinite.animateFloat(
            initialValue = 0.7f, targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(700),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "preroll-halo-anim"
        ).value
    } else 1f

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onConfirm,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 10.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .alpha(alpha.value)
                .scale(entrance.value)
        ) {
            Column(
                Modifier
                    .padding(horizontal = 22.dp, vertical = 22.dp)
                    .widthIn(min = 300.dp, max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---- Action quote header ----
                Text(
                    "YOU ATTEMPT",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(if (pre.action.length > 60) 36.dp else 22.dp)
                            .background(dieColor.copy(alpha = 0.7f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "\u201C${pre.action}\u201D",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(20.dp))

                // ---- d20 silhouette ----
                Box(
                    Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer halo — soft radial glow, brighter on crit/fumble.
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        drawCircle(
                            color = glowColor.copy(alpha = glowColor.alpha * haloPulse),
                            radius = size.minDimension / 2f
                        )
                    }
                    // Hex d20 face — drawn as a regular hexagon with an inner triangle
                    // that evokes one face of an icosahedron.
                    androidx.compose.foundation.Canvas(Modifier.size(110.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.minDimension / 2f * 0.95f
                        val outer = androidx.compose.ui.graphics.Path().apply {
                            for (i in 0 until 6) {
                                val a = (i * 60.0 - 30.0) * kotlin.math.PI / 180.0
                                val x = cx + (r * kotlin.math.cos(a)).toFloat()
                                val y = cy + (r * kotlin.math.sin(a)).toFloat()
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                        val inner = androidx.compose.ui.graphics.Path().apply {
                            val rr = r * 0.55f
                            for (i in 0 until 3) {
                                val a = (i * 120.0 - 90.0) * kotlin.math.PI / 180.0
                                val x = cx + (rr * kotlin.math.cos(a)).toFloat()
                                val y = cy + (rr * kotlin.math.sin(a)).toFloat()
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                        drawPath(outer, dieColor.copy(alpha = if (spinComplete) 0.14f else 0.08f))
                        drawPath(
                            outer, if (spinComplete) dieColor else dieColor.copy(alpha = 0.4f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (spinComplete) 3.5f else 2f)
                        )
                        drawPath(
                            inner, dieColor.copy(alpha = 0.35f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                        )
                    }
                    // The number itself — the only thing that matters.
                    Text(
                        displayedRoll.toString(),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        color = if (spinComplete) dieColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.displayLarge
                    )
                }

                // ---- Crit / fumble flourish ----
                if (pre.crit && spinComplete) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (pre.roll == 20) "🌟 NATURAL TWENTY"
                        else "💀 NATURAL ONE",
                        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp),
                        color = dieColor,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (pre.roll == 20) "Fate favours you. Whatever the DC, you pass."
                        else "The dice mock you. Whatever the DC, you fail.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(Modifier.height(if (pre.crit) 14.dp else 18.dp))

                // ---- Everything below slides in after the dice spin completes ----
                val totalScale = androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (spinComplete) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                    ),
                    label = "total-reveal"
                )
                val totalAlpha = androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (spinComplete) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(300),
                    label = "total-alpha"
                )

                AnimatedVisibility(
                    visible = spinComplete,
                    enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) +
                            slideInVertically(initialOffsetY = { it / 3 })
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // ---- Skill / ability label ----
                        if (pre.skill != null) {
                            Text(
                                "${pre.skill.uppercase()} · ${pre.ability} check",
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(10.dp))
                        } else {
                            Text(
                                "FREE ACTION",
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        // ---- Vertical breakdown ----
                        BreakdownRow("d20 roll", pre.roll.toString(), dieColor, isHero = true)
                        BreakdownRow(
                            "Ability (${pre.ability})",
                            formatSignedRoll(pre.mod),
                            MaterialTheme.colorScheme.secondary
                        )
                        if (pre.prof != 0) {
                            BreakdownRow(
                                "Proficiency",
                                formatSignedRoll(pre.prof),
                                MaterialTheme.colorScheme.tertiary
                            )
                        }
                        // Total separator — gradient gold line.
                        Box(
                            Modifier
                                .padding(vertical = 6.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            realms.goldAccent.copy(alpha = 0.6f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Row(
                            Modifier.fillMaxWidth().scale(totalScale.value).alpha(totalAlpha.value),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "TOTAL",
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                color = realms.goldAccent,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                pre.total.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                color = realms.goldAccent,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "The narrator sets the DC.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))

                // ---- Button ----
                Button(
                    onClick = onConfirm,
                    enabled = spinComplete,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = dieColor)
                ) {
                    Text(
                        "Send It",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp)
                    )
                }
            }
        }
    }
}

/**
 * One line of the pre-roll breakdown — label on the left, signed value on the
 * right in the row's accent colour. Hero row (the d20 itself) gets larger,
 * unsigned text since it's the source of everything.
 */
@Composable
private fun BreakdownRow(label: String, value: String, accent: Color, isHero: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = if (isHero) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.titleSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatSignedRoll(n: Int) = if (n >= 0) "+$n" else n.toString()

@Composable
private fun CombatHud(combat: com.realmsoffate.game.game.CombatState, npcLog: List<com.realmsoffate.game.data.LogNpc> = emptyList(), currentTurn: Int = 0) {
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
                    val turnColor = if (active.isPlayer) realms.goldAccent else MaterialTheme.colorScheme.error
                    Surface(
                        color = turnColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (active.isPlayer) "\u2605" else "\u2620",
                                style = MaterialTheme.typography.labelSmall,
                                color = turnColor
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                active.name.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = turnColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
            // Enemies now appear directly in the initiative order via [ENEMY] tags
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
    val isEnemy = !c.isPlayer && c.initiative > 0 // enemies added via [ENEMY] tags
    val chipBg = when {
        active -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        isEnemy -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val chipBorder = when {
        active -> MaterialTheme.colorScheme.error
        isEnemy -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        color = chipBg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.border(1.dp, chipBorder, RoundedCornerShape(10.dp))
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.isPlayer) Text("\u2605", style = MaterialTheme.typography.labelSmall, color = realms.goldAccent)
                else if (isEnemy) Text("\u2620", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
private fun SceneBanner(scene: String, desc: String) {
    var expanded by remember(scene, desc) { mutableStateOf(false) }
    // Heuristic: if the description is short enough to fit, no need for the
    // expand affordance — keeps the chevron from showing on one-liners.
    val isLong = desc.length > 80
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "scene-chevron-rotation"
    )
    Surface(
        onClick = { if (isLong) expanded = !expanded },
        enabled = isLong,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .animateContentSize(animationSpec = androidx.compose.animation.core.tween(220)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(sceneEmoji(scene), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    scene.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (desc.isNotBlank()) Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = if (expanded) TextOverflow.Visible else TextOverflow.Ellipsis
                )
            }
            if (isLong) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }
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

@Composable
private fun PlayerBubble(
    text: String,
    characterName: String?,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {}
) {
    val realms = RealmsTheme.colors
    val displayName = characterName ?: "You"
    val initial = displayName.take(1).uppercase()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.85f).border(
                1.dp, realms.goldAccent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)
            )
        ) {
            Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "$displayName:",
                        style = MaterialTheme.typography.titleSmall,
                        color = realms.goldAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    val cleanText = text
                        .removeSurrounding("\"")
                        .removeSurrounding("\u201C", "\u201D")
                        .trim()
                    Text(
                        text = com.realmsoffate.game.util.parseInline("\u201C$cleanText\u201D"),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = (15f * LocalFontScale.current).sp
                        )
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(realms.goldAccent.copy(alpha = 0.25f))
                            .border(1.dp, realms.goldAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initial, style = MaterialTheme.typography.labelSmall, color = realms.goldAccent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        onClick = onToggleBookmark,
                        color = if (isBookmarked) realms.goldAccent.copy(alpha = 0.22f)
                                else Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Bookmark",
                            modifier = Modifier.padding(3.dp),
                            tint = if (isBookmarked) realms.goldAccent
                                   else realms.goldAccent.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatChangePills(msg: DisplayMessage.Narration) {
    val realms = RealmsTheme.colors
    val pills = mutableListOf<Triple<String, Color, Color>>() // text, bg, fg

    if (msg.hpBefore != msg.hpAfter) {
        val hpDiff = msg.hpAfter - msg.hpBefore
        val lost = hpDiff < 0
        pills.add(Triple(
            "${if (hpDiff > 0) "+" else ""}$hpDiff HP",
            if (lost) realms.fumbleRed.copy(alpha = 0.2f) else realms.success.copy(alpha = 0.2f),
            if (lost) realms.fumbleRed else realms.success
        ))
    }
    if (msg.goldBefore != msg.goldAfter) {
        val diff = msg.goldAfter - msg.goldBefore
        pills.add(Triple(
            "${if (diff > 0) "+" else ""}${diff}g",
            realms.goldAccent.copy(alpha = 0.2f),
            realms.goldAccent
        ))
    }
    if (msg.xpGained > 0) {
        pills.add(Triple(
            "+${msg.xpGained} XP",
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.secondary
        ))
    }
    // Status effects gained
    msg.conditionsAdded.forEach { condition ->
        pills.add(Triple(
            "+$condition",
            Color(0xFFB197FF).copy(alpha = 0.2f),
            Color(0xFFB197FF)
        ))
    }
    // Status effects removed
    msg.conditionsRemoved.forEach { condition ->
        pills.add(Triple(
            "-$condition",
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onSurfaceVariant
        ))
    }
    // Items gained
    msg.itemsGained.forEach { item ->
        pills.add(Triple(
            "+$item",
            realms.success.copy(alpha = 0.15f),
            realms.success
        ))
    }
    // Items lost
    msg.itemsRemoved.forEach { item ->
        pills.add(Triple(
            "-$item",
            realms.fumbleRed.copy(alpha = 0.15f),
            realms.fumbleRed
        ))
    }
    // Morality shift
    if (msg.moralDelta != 0) {
        val good = msg.moralDelta > 0
        pills.add(Triple(
            "${if (good) "+" else ""}${msg.moralDelta} Moral",
            if (good) Color(0xFF81C784).copy(alpha = 0.2f) else Color(0xFFE57373).copy(alpha = 0.2f),
            if (good) Color(0xFF81C784) else Color(0xFFE57373)
        ))
    }
    // Faction reputation changes
    msg.repDeltas.forEach { (faction, delta) ->
        val positive = delta > 0
        pills.add(Triple(
            "${if (positive) "+" else ""}$delta $faction",
            if (positive) Color(0xFF64B5F6).copy(alpha = 0.2f) else Color(0xFFFF8A65).copy(alpha = 0.2f),
            if (positive) Color(0xFF64B5F6) else Color(0xFFFF8A65)
        ))
    }

    if (pills.isNotEmpty()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            pills.forEach { (text, bg, fg) ->
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        color = fg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Resolves a raw NPC ref (may be a slug id, a display name, or an arbitrary
 * string) to the NPC's current display name from the log. Falls back to the
 * raw ref if no match — that keeps rendering safe for new NPCs whose
 * [NPC_MET] hasn't been applied yet and for legacy name-form refs.
 */
private fun resolveNpcDisplayName(
    ref: String,
    npcLog: List<com.realmsoffate.game.data.LogNpc>
): String {
    if (ref.isBlank()) return ref
    val byId = npcLog.firstOrNull { it.id == ref }
    if (byId != null && byId.name.isNotBlank()) return byId.name
    val byName = npcLog.firstOrNull { it.name.equals(ref, ignoreCase = true) }
    if (byName != null && byName.name.isNotBlank()) return byName.name
    return ref
}

@Composable
private fun NarrationBlock(
    text: String,
    characterName: String? = null,
    msg: DisplayMessage.Narration? = null,
    structuredSegments: List<NarrationSegmentData> = emptyList(),
    npcLog: List<com.realmsoffate.game.data.LogNpc> = emptyList(),
    isLatestTurn: Boolean = false,
    bookmarks: List<String> = emptyList(),
    onToggleBookmark: (String) -> Unit = {},
    onNpcTap: (name: String) -> Unit = {},
    onNpcReply: (name: String) -> Unit = {},
    onNpcReaction: (name: String, quote: String, reaction: String) -> Unit = { _, _, _ -> },
    onAttackNpc: (name: String) -> Unit = {},
    onOpenJournal: (name: String) -> Unit = {},
    onOpenStats: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (structuredSegments.isNotEmpty()) {
            // ---- Structured rendering: tags parsed reliably ----
            // 1. Collect only Prose segments for the top bubble
            val allProse = structuredSegments
                .filterIsInstance<NarrationSegmentData.Prose>()
                .joinToString("\n\n") { it.text }
            if (allProse.isNotBlank()) {
                val proseBookmarkKey = allProse.take(300)
                val isProseMarked = bookmarks.contains(proseBookmarkKey)
                NarratorBubble(
                    text = allProse,
                    isBookmarked = isProseMarked,
                    onToggleBookmark = { onToggleBookmark(proseBookmarkKey) }
                )
            }

            // 2. Non-prose segments in order
            structuredSegments.forEach { seg ->
                when (seg) {
                    is NarrationSegmentData.Prose -> { /* already rendered above */ }
                    is NarrationSegmentData.Aside -> {
                        val asideKey = seg.text.take(300)
                        NarratorQuipBubble(
                            text = seg.text,
                            isBookmarked = bookmarks.contains(asideKey),
                            onToggleBookmark = { onToggleBookmark(asideKey) }
                        )
                    }
                    is NarrationSegmentData.NpcDialog -> {
                        val displayName = resolveNpcDisplayName(seg.name, npcLog)
                        SwipeableMessage(
                            onSwipeLeft = if (isLatestTurn) {{ onAttackNpc(seg.name) }} else {{}},
                            onSwipeRight = { onOpenJournal(seg.name) },
                            leftLabel = if (isLatestTurn) "Attack" else null,
                            rightLabel = "Journal",
                            leftIcon = if (isLatestTurn) Icons.Filled.Bolt else null,
                            rightIcon = Icons.Filled.Book
                        ) {
                            NpcDialogueBubble(
                                name = displayName,
                                quote = seg.text,
                                isBookmarked = bookmarks.contains("${seg.name}: ${seg.text}".take(300)),
                                onToggleBookmark = { onToggleBookmark("${seg.name}: ${seg.text}".take(300)) },
                                onTap = if (isLatestTurn) {{ onNpcReply(seg.name) }} else {{}},
                                onReaction = if (isLatestTurn) {{ reaction: String -> onNpcReaction(seg.name, seg.text, reaction) }} else {{}},
                                isInteractive = isLatestTurn
                            )
                        }
                    }
                    is NarrationSegmentData.PlayerDialog -> {
                        SwipeableMessage(
                            onSwipeLeft = {},
                            onSwipeRight = { onOpenStats() },
                            leftLabel = null,
                            rightLabel = "Stats",
                            leftIcon = null,
                            rightIcon = Icons.Filled.QueryStats
                        ) {
                            PlayerBubble(
                                text = seg.text,
                                characterName = characterName
                            )
                        }
                    }
                    is NarrationSegmentData.PlayerAction -> {
                        NarratorQuipBubble(text = seg.text)
                    }
                    is NarrationSegmentData.NpcAction -> {
                        // Parser formats seg.text as a natural sentence (handles
                        // pronouns, determiners, leaked dialog). When the text begins
                        // with the raw ref (slug or bare name), replace it with the
                        // resolved display name so the player never sees slugs.
                        val displayName = resolveNpcDisplayName(seg.name, npcLog)
                        val actionText = if (seg.name.isNotBlank() && displayName != seg.name &&
                            seg.text.startsWith(seg.name, ignoreCase = true)) {
                            displayName + seg.text.substring(seg.name.length)
                        } else {
                            seg.text
                        }
                        NarratorQuipBubble(text = actionText)
                    }
                }
            }
        } else {
            // ---- Legacy fallback: regex-based splitting for old saves ----
            val segments = splitNarration(text, characterName)
            val allProse = segments.filterIsInstance<NarrationSegment.Prose>()
                .joinToString("\n\n") { it.text }
            val nonProseSegments = segments.filter { it !is NarrationSegment.Prose }

            if (allProse.isNotBlank()) {
                val proseBookmarkKey = allProse.take(300)
                val isProseMarked = bookmarks.contains(proseBookmarkKey)
                NarratorBubble(
                    text = allProse,
                    isBookmarked = isProseMarked,
                    onToggleBookmark = { onToggleBookmark(proseBookmarkKey) }
                )
            }

            nonProseSegments.forEach { seg ->
                when (seg) {
                    is NarrationSegment.NarratorQuip -> {
                        NarratorQuipBubble(text = seg.text)
                    }
                    is NarrationSegment.Dialogue -> {
                        SwipeableMessage(
                            onSwipeLeft = { onAttackNpc(seg.name) },
                            onSwipeRight = { onOpenJournal(seg.name) },
                            leftLabel = "Attack",
                            rightLabel = "Journal",
                            leftIcon = Icons.Filled.Bolt,
                            rightIcon = Icons.Filled.Book
                        ) {
                            NpcDialogueBubble(
                                name = seg.name,
                                quote = seg.quote,
                                isBookmarked = bookmarks.contains("${seg.name}: ${seg.quote}".take(300)),
                                onToggleBookmark = { onToggleBookmark("${seg.name}: ${seg.quote}".take(300)) },
                                onTap = { onNpcReply(seg.name) },
                                onReaction = { reaction -> onNpcReaction(seg.name, seg.quote, reaction) }
                            )
                        }
                    }
                    is NarrationSegment.PlayerDialogue -> {
                        SwipeableMessage(
                            onSwipeLeft = {},
                            onSwipeRight = { onOpenStats() },
                            leftLabel = null,
                            rightLabel = "Stats",
                            leftIcon = null,
                            rightIcon = Icons.Filled.QueryStats
                        ) {
                            PlayerBubble(
                                text = seg.quote,
                                characterName = seg.name
                            )
                        }
                    }
                    is NarrationSegment.Action -> {
                        NarratorQuipBubble(text = seg.text)
                    }
                    else -> {}
                }
            }
        }

        // Stat change pills at the bottom (always shown)
        if (msg != null) {
            StatChangePills(msg)
        }
    }
}

/** Wraps any bubble with a small bookmark reaction button floating at bottom-right. */
@Composable
private fun BubbleWithReaction(
    isBookmarked: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Box {
        content()
        Surface(
            onClick = onToggle,
            color = if (isBookmarked) RealmsTheme.colors.goldAccent.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(24.dp)
        ) {
            Icon(
                if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = "Bookmark",
                modifier = Modifier.padding(4.dp),
                tint = if (isBookmarked) RealmsTheme.colors.goldAccent
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
        }
    }
}

private sealed class NarrationSegment {
    data class Prose(val text: String) : NarrationSegment()
    data class Dialogue(val name: String, val quote: String) : NarrationSegment()
    data class PlayerDialogue(val name: String, val quote: String) : NarrationSegment()
    data class Action(val text: String) : NarrationSegment()
    /** Narrator aside / quip — italic commentary like *I've seen this before.* */
    data class NarratorQuip(val text: String) : NarrationSegment()
}

/** Split narration text into typed segments for distinct rendering. */
private fun splitNarration(text: String, characterName: String? = null): List<NarrationSegment> {
    val segments = mutableListOf<NarrationSegment>()

    // First pass: extract [SNARK]...[/SNARK] tags and replace with placeholders
    val snarkPattern = Regex("""\[SNARK](.*?)\[/SNARK]""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    val snarks = mutableListOf<String>()
    val cleaned = snarkPattern.replace(text) { match ->
        snarks.add(match.groupValues[1].trim())
        "\n@@SNARK_${snarks.size - 1}@@\n"
    }

    val lines = cleaned.lines()
    var proseBuffer = StringBuilder()
    var i = 0

    // Regex for standalone italic sentences embedded in prose (narrator asides)
    val embeddedQuip = Regex("""(?:^|\n)\s*\*([^*\n]{6,})\*\s*(?:\n|$)""")
    // Regex for dialogue embedded in prose: "Name says/said/asks/shouts, "quote""
    // or: "quote," Name says. Captures attributed speech within narrative paragraphs.
    val QQ = "\u201C\u201D\""
    val proseDialogue = Regex(
        "(?:^|\\.\\s+)\\**([A-Z][a-z]+(?:\\s[A-Z][a-z]+)?)\\**\\s+" +
        "(?:says?|said|asks?|asked|whispers?|whispered|shouts?|shouted|mutters?|muttered|growls?|growled|replies?|replied|hisses?|hissed|calls?|called|snaps?|snapped|laughs?|laughed|sighs?|sighed|screams?|screamed|barks?|barked|cries?|cried|murmurs?|murmured|exclaims?|exclaimed)" +
        "[,:]?\\s*[" + QQ + "]([^" + QQ + "]{3,}?)[" + QQ + "]",
        RegexOption.IGNORE_CASE
    )

    fun flushProse() {
        val t = proseBuffer.toString().trim()
        if (t.isNotBlank()) {
            // Split long prose into paragraph-sized chunks
            val paragraphs = t.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }
            for (para in paragraphs) {
                // First: extract any embedded quips (italic asides)
                val quipMatches = embeddedQuip.findAll(para).toList()
                // Second: extract attributed dialogue from prose ("Name says, ...")
                val dialogueMatches = proseDialogue.findAll(para).toList()

                if (quipMatches.isEmpty() && dialogueMatches.isEmpty()) {
                    segments.add(NarrationSegment.Prose(para))
                } else {
                    // Merge all extraction ranges and split the paragraph
                    data class Extraction(val range: IntRange, val segment: NarrationSegment)
                    val extractions = mutableListOf<Extraction>()
                    quipMatches.forEach { m ->
                        extractions.add(Extraction(m.range, NarrationSegment.NarratorQuip(m.groupValues[1].trim())))
                    }
                    dialogueMatches.forEach { m ->
                        val name = m.groupValues[1].trim().removeSurrounding("*")
                        val quote = m.groupValues[2].trim()
                        val isPC = characterName != null && name.equals(characterName, ignoreCase = true)
                        val seg = if (isPC) NarrationSegment.PlayerDialogue(name, quote)
                                  else NarrationSegment.Dialogue(name, quote)
                        extractions.add(Extraction(m.range, seg))
                    }
                    // Sort by position and split
                    val sorted = extractions.sortedBy { it.range.first }
                    var lastEnd = 0
                    for (ext in sorted) {
                        if (ext.range.first < lastEnd) continue // overlapping, skip
                        val before = para.substring(lastEnd, ext.range.first).trim()
                        if (before.isNotBlank()) segments.add(NarrationSegment.Prose(before))
                        segments.add(ext.segment)
                        lastEnd = ext.range.last + 1
                    }
                    val after = para.substring(lastEnd.coerceAtMost(para.length)).trim()
                    if (after.isNotBlank()) segments.add(NarrationSegment.Prose(after))
                }
            }
        }
        proseBuffer = StringBuilder()
    }

    while (i < lines.size) {
        val line = lines[i].trim()

        // Detect [SNARK] placeholder
        val snarkIdx = Regex("""^@@SNARK_(\d+)@@$""").find(line)
        if (snarkIdx != null) {
            flushProse()
            val idx2 = snarkIdx.groupValues[1].toIntOrNull()
            if (idx2 != null && idx2 in snarks.indices) {
                segments.add(NarrationSegment.NarratorQuip(snarks[idx2]))
            }
            i++
            continue
        }

        // Detect NPC/player dialogue: **Name:** or EMOJI **Name:** patterns
        // The colon MUST be inside the bold markers to avoid matching generic bold text.
        if (line.contains(":**")) {
            // Match **Name:** with optional emoji prefix and optional trailing content
            val nameMatch = Regex("""^[^\*]*\*\*([^*:]+?):\*\*(.*)$""").find(line)
            if (nameMatch != null) {
                flushProse()
                val name = nameMatch.groupValues[1].trim()
                val trailing = nameMatch.groupValues[2].trim()
                val isPlayer = characterName != null && name.equals(characterName, ignoreCase = true)

                // Strategy 1: Look ahead for blockquote on next line(s)
                val nextIdx = i + 1
                if (nextIdx < lines.size && lines[nextIdx].trim().startsWith(">")) {
                    // Collect all consecutive blockquote lines as one quote
                    val quoteLines = mutableListOf<String>()
                    var j = nextIdx
                    while (j < lines.size && lines[j].trim().startsWith(">")) {
                        quoteLines.add(lines[j].trim().removePrefix(">").trim()
                            .removeSurrounding("\"").removeSurrounding("\u201C", "\u201D"))
                        j++
                    }
                    val fullQuote = quoteLines.joinToString(" ")
                    if (isPlayer) segments.add(NarrationSegment.PlayerDialogue(name, fullQuote))
                    else segments.add(NarrationSegment.Dialogue(name, fullQuote))
                    i = j
                    continue
                }

                // Strategy 2: Inline quoted text after **Name:**
                val cleanTrailing = trailing.removeSurrounding("*").trim()
                if (cleanTrailing.contains("\"") || cleanTrailing.contains("\u201C")) {
                    val q = cleanTrailing.replace(Regex("[\"\u201C\u201D]"), "").trim()
                    if (q.isNotBlank()) {
                        if (isPlayer) segments.add(NarrationSegment.PlayerDialogue(name, q))
                        else segments.add(NarrationSegment.Dialogue(name, q))
                        i++
                        continue
                    }
                }

                // Strategy 3: Any remaining text after **Name:** is speech (no quotes)
                if (cleanTrailing.isNotBlank() && cleanTrailing.length >= 3) {
                    if (isPlayer) segments.add(NarrationSegment.PlayerDialogue(name, cleanTrailing))
                    else segments.add(NarrationSegment.Dialogue(name, cleanTrailing))
                    i++
                    continue
                }

                // Strategy 4: Name line alone — look ahead for any non-empty line as speech
                if (nextIdx < lines.size) {
                    val nextLine = lines[nextIdx].trim()
                    if (nextLine.isNotBlank() && !nextLine.startsWith("*") && !nextLine.startsWith("[")) {
                        val q = nextLine.removeSurrounding("\"").removeSurrounding("\u201C", "\u201D")
                        if (isPlayer) segments.add(NarrationSegment.PlayerDialogue(name, q))
                        else segments.add(NarrationSegment.Dialogue(name, q))
                        i = nextIdx + 1
                        continue
                    }
                }

                // Fallback: just the name, no quote found — skip it
                i++
                continue
            }
        }

        // Detect narrator asides/quips: line is *italic* or mostly italic commentary
        // Patterns: *entire line italic*, or line that is ONLY an italic phrase
        if (!line.startsWith("**") && line.length >= 8) {
            // Entire line wrapped in *...*
            if (line.startsWith("*") && line.endsWith("*") && line.count { it == '*' } == 2) {
                flushProse()
                segments.add(NarrationSegment.NarratorQuip(line.removeSurrounding("*")))
                i++
                continue
            }
            // Line starts and ends with italic markers but may have trailing punctuation
            val quipMatch = Regex("""^\*([^*]+)\*[.!?…]*$""").find(line)
            if (quipMatch != null && quipMatch.groupValues[1].length >= 6) {
                flushProse()
                segments.add(NarrationSegment.NarratorQuip(quipMatch.groupValues[1]))
                i++
                continue
            }
        }

        // Detect parenthetical actions: (something happens)
        if (line.startsWith("(") && line.endsWith(")")) {
            flushProse()
            segments.add(NarrationSegment.Action(line.removeSurrounding("(", ")")))
            i++
            continue
        }

        // Detect blockquote dialogue without a preceding name
        if (line.startsWith("> ")) {
            flushProse()
            val quote = line.removePrefix("> ").trim()
                .removeSurrounding("\"").removeSurrounding("\u201C", "\u201D")
            segments.add(NarrationSegment.Dialogue("", quote))
            i++
            continue
        }

        // Everything else is prose
        proseBuffer.appendLine(line)
        i++
    }
    flushProse()
    return segments
}

@Composable
private fun NarratorBubble(
    text: String,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {}
) {
    var showFullProse by remember { mutableStateOf(false) }
    val summary = summarizeProse(text)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            onClick = { showFullProse = true },
            color = Color(0xFF1A1030).copy(alpha = 0.85f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(
                    1.dp,
                    Color(0xFFB197FF).copy(alpha = 0.3f),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header row with icon + bookmark
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(28.dp)) // balance the bookmark on the right
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFB197FF).copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "NARRATOR",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                            color = Color(0xFFB197FF).copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        onClick = onToggleBookmark,
                        color = if (isBookmarked) RealmsTheme.colors.goldAccent.copy(alpha = 0.22f)
                                else Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Bookmark",
                            modifier = Modifier.padding(4.dp),
                            tint = if (isBookmarked) RealmsTheme.colors.goldAccent
                                   else Color(0xFFB197FF).copy(alpha = 0.4f)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Summary text
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = (15f * LocalFontScale.current).sp),
                    color = Color(0xFFE8E1F0),
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                // Tap hint
                Text(
                    "Tap to read full passage",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB197FF).copy(alpha = 0.5f)
                )
            }
        }
    }

    // Full prose popup dialog
    if (showFullProse) {
        ProseDetailDialog(
            text = text,
            onDismiss = { showFullProse = false }
        )
    }
}

/** Extracts a 1-3 sentence summary from the full prose text. */
private fun summarizeProse(text: String): String {
    // Strip markdown formatting for the summary
    val clean = text
        .replace(Regex("#{1,3}\\s+"), "")
        .replace(Regex("\\*{1,3}"), "")
        .replace(Regex("`[^`]+`"), "")
        .replace(Regex("~~[^~]+~~"), "")
        .replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^[-*]\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("---+|\\*\\*\\*+"), "")
        .replace(Regex("\\n{2,}"), " ")
        .replace(Regex("\\n"), " ")
        .trim()

    // Take first 2-3 sentences
    val sentences = clean.split(Regex("(?<=[.!?])\\s+"))
        .filter { it.isNotBlank() }

    return when {
        sentences.size <= 2 -> clean.take(200)
        else -> sentences.take(3).joinToString(" ").take(200)
    }.let { if (it.length >= 197) "$it..." else it }
}

@Composable
private fun ProseDetailDialog(text: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1A1030),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .border(
                    1.dp,
                    Color(0xFFB197FF).copy(alpha = 0.3f),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color(0xFFB197FF)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "NARRATOR",
                        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp),
                        color = Color(0xFFB197FF),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            "Close",
                            tint = Color(0xFFB197FF).copy(alpha = 0.7f)
                        )
                    }
                }
                // Gradient divider
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xFFB197FF).copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Spacer(Modifier.height(12.dp))
                // Scrollable prose content with full markdown
                val scrollState = rememberScrollState()
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    NarrationMarkdown(
                        text,
                        baseStyle = NarrationBodyStyle.copy(
                            color = Color(0xFFE8E1F0)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun NarratorQuipBubble(
    text: String,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {}
) {
    // Rendered as plain, smaller, centered gray italic text — no surface, no border, no header.
    // Extra vertical padding gives breathing room between the quip and neighboring bubbles.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontSize = (13f * LocalFontScale.current).sp
            ),
            color = Color(0xFF9E9AA8),
            textAlign = TextAlign.Center
        )
    }
}

/** Stable color palette for NPC dialogue — each NPC gets a consistent hue derived from their name. */
private val npcColorPalette = listOf(
    Color(0xFF4A9E5E) to Color(0xFF1B3D23), // green
    Color(0xFF5B7FC7) to Color(0xFF1C2D4A), // blue
    Color(0xFFD4A843) to Color(0xFF3D3118), // gold
    Color(0xFFC44040) to Color(0xFF3D1818), // red
    Color(0xFF8B6CC7) to Color(0xFF2D1F42), // purple
    Color(0xFF4AA8A8) to Color(0xFF1A3636), // teal
    Color(0xFFCC6633) to Color(0xFF3D2010), // orange
    Color(0xFFAA44AA) to Color(0xFF361836), // magenta
    Color(0xFF6A9E3A) to Color(0xFF223312), // lime
    Color(0xFF5577CC) to Color(0xFF1A2540), // steel blue
)

private fun npcColor(name: String): Pair<Color, Color> {
    if (name.isBlank()) return npcColorPalette[0]
    val idx = (name.lowercase().hashCode() and 0x7FFFFFFF) % npcColorPalette.size
    return npcColorPalette[idx]
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NpcDialogueBubble(
    name: String,
    quote: String,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onTap: () -> Unit = {},
    onReaction: (String) -> Unit = {},
    isInteractive: Boolean = true
) {
    val (accent, bgTint) = npcColor(name)
    var showReactions by remember { mutableStateOf(false) }
    var appliedReaction by remember { mutableStateOf<String?>(null) }

    Column {
        // Bubble + overlapping reaction pill
        Box(Modifier.padding(bottom = if (appliedReaction != null) 8.dp else 0.dp)) {
            Surface(
                color = bgTint.copy(alpha = 0.75f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .combinedClickable(
                        onClick = { if (isInteractive) onTap() },
                        onLongClick = { if (isInteractive) showReactions = !showReactions }
                    )
            ) {
                Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)) {
                    if (name.isNotBlank()) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape)
                                .background(accent.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                name.take(1).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        if (name.isNotBlank()) {
                            Text(
                                "$name:",
                                style = MaterialTheme.typography.titleSmall,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        val cleanQuote = quote
                            .removeSurrounding("\"")
                            .removeSurrounding("\u201C", "\u201D")
                            .removeSurrounding("'")
                            .removeSurrounding("\u2018", "\u2019")
                            .trim()
                        Text(
                            text = com.realmsoffate.game.util.parseInline("\u201C$cleanQuote\u201D"),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = (15f * LocalFontScale.current).sp
                            )
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        onClick = onToggleBookmark,
                        color = if (isBookmarked) RealmsTheme.colors.goldAccent.copy(alpha = 0.22f)
                                else Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp).align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Bookmark",
                            modifier = Modifier.padding(4.dp),
                            tint = if (isBookmarked) RealmsTheme.colors.goldAccent
                                   else accent.copy(alpha = 0.4f)
                        )
                    }
                }
            }
            // Applied reaction pill — overlapping bottom-right
            appliedReaction?.let { emoji ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-12).dp, y = 10.dp)
                        .height(26.dp)
                        .widthIn(min = 34.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Text(emoji, fontSize = 14.sp)
                    }
                }
            }
        }

        // Reaction picker — compact row below bubble, appears on long-press
        AnimatedVisibility(
            visible = showReactions,
            enter = fadeIn(animationSpec = tween(150)) + androidx.compose.animation.expandVertically(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(100)) + androidx.compose.animation.shrinkVertically(animationSpec = tween(100))
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val reactions = listOf(
                        "\uD83D\uDC4D" to "approve",
                        "\uD83D\uDC4E" to "disapprove",
                        "\uD83D\uDE02" to "laugh",
                        "\uD83D\uDE20" to "angry",
                        "\u2753" to "question",
                        "\uD83D\uDE31" to "shocked",
                        "\uD83E\uDD14" to "suspicious"
                    )
                    reactions.forEach { (emoji, action) ->
                        Surface(
                            onClick = {
                                appliedReaction = emoji
                                showReactions = false
                                onReaction(action)
                            },
                            color = Color.Transparent,
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                    }
                    // "+" button for custom emoji via system keyboard
                    Box {
                        var emojiCapture by remember { mutableStateOf("") }
                        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                        Surface(
                            onClick = {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            },
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        // Invisible text field — captures emoji from keyboard
                        androidx.compose.foundation.text.BasicTextField(
                            value = emojiCapture,
                            onValueChange = { v ->
                                if (v.isNotBlank()) {
                                    appliedReaction = v
                                    showReactions = false
                                    emojiCapture = ""
                                    onReaction("emoji:$v")
                                }
                            },
                            modifier = Modifier
                                .size(1.dp)
                                .alpha(0f)
                                .focusRequester(focusRequester),
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableMessage(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    leftLabel: String? = "Attack",
    rightLabel: String? = "Examine",
    leftIcon: androidx.compose.ui.graphics.vector.ImageVector? = Icons.Filled.Bolt,
    rightIcon: androidx.compose.ui.graphics.vector.ImageVector? = Icons.Filled.Search,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val threshold = 100f

    Box(Modifier.fillMaxWidth()) {
        // Background action labels revealed by swipe
        Row(
            Modifier
                .matchParentSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side (revealed on swipe right) — Examine
            AnimatedVisibility(visible = offsetX > 30f && rightLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    rightIcon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        rightLabel.orEmpty(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // Right side (revealed on swipe left) — Attack (only for dialogue bubbles)
            AnimatedVisibility(visible = offsetX < -30f && leftLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        leftLabel.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    leftIcon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        // The actual message content — draggable
        Box(
            Modifier
                .offset { IntOffset(offsetX.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > threshold) onSwipeRight()
                            else if (offsetX < -threshold) onSwipeLeft()
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-200f, 200f)
                        }
                    )
                }
        ) {
            content()
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
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
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
    val realms = RealmsTheme.colors
    // Colorize check-result lines so PASS/FAIL pops in the chat feed.
    val (bg, fg) = when {
        text.startsWith("✓") -> realms.success.copy(alpha = 0.18f) to realms.success
        text.startsWith("✗") -> realms.fumbleRed.copy(alpha = 0.18f) to realms.fumbleRed
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = if (text.startsWith("✓") || text.startsWith("✗")) FontWeight.Bold else FontWeight.Normal,
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
