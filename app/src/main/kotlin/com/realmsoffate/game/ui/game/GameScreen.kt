package com.realmsoffate.game.ui.game

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realmsoffate.game.data.Choice
import com.realmsoffate.game.game.GameUiState
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
import com.realmsoffate.game.ui.theme.RealmsTheme

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

    val systemFontScale = LocalConfiguration.current.fontScale
    CompositionLocalProvider(
        LocalFontScale provides fontScale * systemFontScale
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
                color = realms.asideBubble.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "The tavern door creaks open. A cold wind follows you inside.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = (15f * fontScale).sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
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
// INPUT BAR + BOTTOM NAV + FLOATING CHOICES FAB
// ============================================================

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


