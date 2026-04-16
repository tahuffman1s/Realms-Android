package com.realmsoffate.game.ui.game

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.ui.map.WorldMapScreen
import com.realmsoffate.game.ui.overlays.FeatSelectionOverlay
import com.realmsoffate.game.ui.overlays.InitiativeOverlay
import com.realmsoffate.game.ui.overlays.LevelUpOverlay
import com.realmsoffate.game.ui.overlays.RestOverlay
import com.realmsoffate.game.ui.overlays.ShopOverlay
import com.realmsoffate.game.ui.overlays.TargetPromptDialog
import com.realmsoffate.game.ui.overlays.TargetPromptSpec
import com.realmsoffate.game.ui.panels.*

/** Composition-local font scale. 1.0 = default, 0.7 = smallest, 1.6 = largest. */
val LocalFontScale = compositionLocalOf { 1.0f }

/** Which panel (or fullscreen page) is active. */
enum class Panel { None, Inventory, Quests, Party, Lore, Journal, Currency, Spells, Stats, Map, Memories, Settings }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: GameViewModel) {
    val state by vm.ui.collectAsState()
    var panel by remember { mutableStateOf(Panel.None) }
    var journalFocusNpc by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(GameTab.Chat) }
    var choicesOpen by remember { mutableStateOf(false) }
    var showCharacterChooser by remember { mutableStateOf(false) }
    var showJournalChooser by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val topBarCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 100
        }
    }
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

    // Lists of sub-panels in each tab group — used for cycling on re-tap.
    val characterPanels = listOf(Panel.Inventory, Panel.Stats, Panel.Spells, Panel.Currency)
    val journalPanels   = listOf(Panel.Quests, Panel.Journal, Panel.Lore, Panel.Party, Panel.Memories)

    // When a tab changes, open the default sub-panel for that group — but only
    // if the current panel isn't already in that group (avoids resetting position
    // when a panel opens a sibling via onOpenJournal / onOpenStats etc.).
    LaunchedEffect(tab) {
        when (tab) {
            GameTab.Chat      -> panel = Panel.None
            GameTab.Map       -> panel = Panel.Map
            GameTab.Character -> if (panel !in characterPanels) panel = Panel.Inventory
            GameTab.Journal   -> if (panel !in journalPanels)   panel = Panel.Quests
        }
    }

    // Full-screen map takes over the whole screen.
    if (panel == Panel.Map) {
        WorldMapScreen(
            state = state,
            onClose = { panel = Panel.None; tab = GameTab.Chat },
            onTravel = { loc -> vm.startTravel(loc.id); panel = Panel.None; tab = GameTab.Chat },
            onCancelTravel = { vm.cancelTravel() },
            isTraveling = state.travelState != null
        )
        return
    }

    val systemFontScale = LocalConfiguration.current.fontScale
    CompositionLocalProvider(
        LocalFontScale provides fontScale * systemFontScale
    ) {

    Scaffold(
        topBar = { GameTopBar(state, collapsed = topBarCollapsed, onSettingsClick = { panel = Panel.Settings }) },
        bottomBar = {
            // imePadding keeps the input + hotbar visible above the soft keyboard.
            Column(Modifier.imePadding()) {
                if (tab == GameTab.Chat) {
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
                        onSpellsOpen = { panel = Panel.Spells },
                        onChoicesOpen = { choicesOpen = true }
                    )
                }
                GameBottomNav(
                    selected = tab,
                    onSelect = { newTab ->
                        when {
                            // Tapping Chat always returns to the main view.
                            newTab == GameTab.Chat -> {
                                panel = Panel.None
                                tab = newTab
                            }
                            // Tapping Character (whether already selected or not) shows the chooser.
                            newTab == GameTab.Character -> {
                                tab = newTab
                                showCharacterChooser = true
                            }
                            // Tapping Journal (whether already selected or not) shows the chooser.
                            newTab == GameTab.Journal -> {
                                tab = newTab
                                showJournalChooser = true
                            }
                            // Switching to a new tab — LaunchedEffect handles opening the default sub-panel.
                            else -> tab = newTab
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (tab == GameTab.Chat && !state.isGenerating && state.combat != null) {
                FloatingActionButton(
                    onClick = {
                        vm.requestTargetPrompt(TargetPromptSpec(
                            title = "Attack",
                            verb = "I attack",
                            recentTargets = state.combat!!.order.filter { !it.isPlayer }.map { it.name }
                        ))
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Icon(Icons.Default.GpsFixed, "Attack")
                }
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
            ChatFeed(
                state = state,
                listState = listState,
                bookmarks = state.bookmarks,
                onToggleBookmark = { vm.toggleBookmark(it) },
                onNpcReply = { npcName ->
                    // Pre-fill input with reply context; keep keyboard open
                    input = "I say to $npcName: "
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
                onOpenStats = { panel = Panel.Stats },
                onOpenShop = { vm.openShop(it) },
                onClearError = { vm.clearError() },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
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

    // Character sub-panel chooser
    if (showCharacterChooser) {
        AlertDialog(
            onDismissRequest = { showCharacterChooser = false },
            title = { Text("Character") },
            text = {
                Column {
                    listOf(
                        Triple("📊", "Stats", Panel.Stats),
                        Triple("🎒", "Inventory", Panel.Inventory),
                        Triple("✨", "Spells", Panel.Spells),
                        Triple("💰", "Currency", Panel.Currency)
                    ).forEach { (icon, label, dest) ->
                        TextButton(
                            onClick = { panel = dest; showCharacterChooser = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "$icon  $label",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Rest",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                    TextButton(
                        onClick = { vm.shortRest(); showCharacterChooser = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "🌤  Short Rest",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextButton(
                        onClick = { vm.longRest(); showCharacterChooser = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "🌙  Long Rest",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Journal sub-panel chooser
    if (showJournalChooser) {
        AlertDialog(
            onDismissRequest = { showJournalChooser = false },
            title = { Text("Journal") },
            text = {
                Column {
                    listOf(
                        Triple("📋", "Quests", Panel.Quests),
                        Triple("📖", "NPCs", Panel.Journal),
                        Triple("📚", "Lore", Panel.Lore),
                        Triple("⚔️", "Party", Panel.Party),
                        Triple("🔖", "Memories", Panel.Memories)
                    ).forEach { (icon, label, dest) ->
                        TextButton(
                            onClick = { panel = dest; showJournalChooser = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "$icon  $label",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    when (panel) {
        Panel.Inventory -> InventoryPanel(state, onClose = { panel = Panel.None; tab = GameTab.Chat }, onEquip = vm::equipToggle)
        Panel.Quests -> QuestsPanel(state, onClose = { panel = Panel.None; tab = GameTab.Chat }, onAbandon = vm::abandonQuest)
        Panel.Party -> PartyPanel(state, onClose = { panel = Panel.None; tab = GameTab.Chat }, onDismiss = vm::dismissCompanion)
        Panel.Lore -> LorePanel(state, onClose = { panel = Panel.None; tab = GameTab.Chat })
        Panel.Journal -> JournalPanel(state, focusNpc = journalFocusNpc, onClose = { journalFocusNpc = null; panel = Panel.None; tab = GameTab.Chat })
        Panel.Currency -> CurrencyPanel(
            state = state,
            onClose = { panel = Panel.None; tab = GameTab.Chat },
            onExchange = vm::exchange
        )
        Panel.Spells -> SpellsPanel(
            state = state,
            onClose = { panel = Panel.None; tab = GameTab.Chat },
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
                tab = GameTab.Chat
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
        Panel.Stats -> StatsPanel(state, onClose = { panel = Panel.None; tab = GameTab.Chat })
        Panel.Memories -> MemoriesPanel(
            state,
            onClose = { panel = Panel.None; tab = GameTab.Chat },
            onDelete = { vm.removeBookmark(it) }
        )
        Panel.Settings -> SettingsPanel(
            fontScale = fontScale,
            onFontScaleChange = { vm.setFontScale(it) },
            onClose = { panel = Panel.None; tab = GameTab.Chat },
            onExportSave = {
                val json = vm.exportCurrentJson()
                if (json != null) {
                    val filename = "realms_save_${System.currentTimeMillis()}.json"
                    exportLauncher.launch(filename)
                } else {
                    vm.postSystemMessage("Nothing to export yet.")
                }
            },
            onDebugDump = { dumpDebugToFile() },
            onReturnToTitle = { vm.returnToTitle() }
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


