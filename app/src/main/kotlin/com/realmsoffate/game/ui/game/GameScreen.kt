package com.realmsoffate.game.ui.game

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.game.GameViewModel
import com.realmsoffate.game.ui.overlays.CheatsOverlay
import com.realmsoffate.game.ui.overlays.FeatSelectionOverlay
import com.realmsoffate.game.ui.overlays.InitiativeOverlay
import com.realmsoffate.game.ui.overlays.LevelUpOverlay
import com.realmsoffate.game.ui.overlays.ShopOverlay
import com.realmsoffate.game.ui.overlays.TargetPromptDialog
import com.realmsoffate.game.ui.overlays.TargetPromptSpec
import com.realmsoffate.game.ui.panels.*

/** Composition-local font scale. 1.0 = default, 0.7 = smallest, 1.6 = largest. */
val LocalFontScale = compositionLocalOf { 1.0f }

/** Which panel (or fullscreen page) is active. */
enum class Panel { None, Inventory, Quests, Party, Lore, Journal, Currency, Spells, Stats, Settings }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: GameViewModel) {
    val state by vm.ui.collectAsState()
    var panel by remember { mutableStateOf(Panel.None) }
    var journalFocusNpc by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(GameTab.Chat) }
    var choicesOpen by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val debugFontScale by com.realmsoffate.game.debug.DebugHook.fontScaleOverride.collectAsState()
    val vmFontScale by vm.fontScale.collectAsState()
    val fontScale = debugFontScale ?: vmFontScale

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
            GameTab.Chat -> panel = Panel.None
            GameTab.Character -> panel = Panel.None
            GameTab.Journal -> panel = Panel.None
        }
    }

    // When a transient panel (settings/spells sheet) targets a pager tab, sync tab then clear `panel`.
    LaunchedEffect(panel) {
        when (panel) {
            Panel.Inventory, Panel.Spells, Panel.Stats, Panel.Party, Panel.Currency -> {
                tab = GameTab.Character
                panel = Panel.None
            }
            Panel.Quests, Panel.Journal, Panel.Lore -> {
                tab = GameTab.Journal
                panel = Panel.None
            }
            else -> {} // Settings, None — handled elsewhere
        }
    }

    var showCheatsOverlay by remember { mutableStateOf(false) }
    val cheatsEnabled by vm.cheatsEnabled.collectAsState()
    val infiniteGold by vm.cheatInfiniteGold.collectAsState()
    val unnaturalTwenty by vm.cheatUnnaturalTwenty.collectAsState()
    val loser by vm.cheatLoser.collectAsState()

    val systemFontScale = LocalConfiguration.current.fontScale
    CompositionLocalProvider(
        LocalFontScale provides fontScale * systemFontScale
    ) {

    Scaffold(
        topBar = {
            GameTopBar(
                state,
                showSceneContext = tab == GameTab.Chat &&
                    state.currentScene != "default" &&
                    state.combat == null,
                onSettingsClick = { panel = Panel.Settings },
                infiniteGold = infiniteGold,
                cheatsEnabled = cheatsEnabled,
                onCheatsClick = { showCheatsOverlay = true },
            )
        },
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
                                vm.submitAction(input)
                                input = ""
                                focus.clearFocus()
                            }
                        },
                        onTargetPrompt = { vm.requestTargetPrompt(it) },
                        onSpellsOpen = { panel = Panel.Spells },
                        hasChoices = state.currentChoices.isNotEmpty(),
                        onChoicesOpen = { choicesOpen = true }
                    )
                }
                GameBottomNav(
                    selected = tab,
                    onSelect = { newTab ->
                        if (newTab == GameTab.Chat) panel = Panel.None
                        tab = newTab
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                GameTab.Chat -> {
                    ChatFeed(
                        state = state,
                        listState = listState,
                        onNpcReply = { npcName ->
                            // Pre-fill input with reply context; keep keyboard open
                            input = "I say to $npcName: "
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
                            tab = GameTab.Journal
                        },
                        onOpenStats = { tab = GameTab.Character },
                        onOpenShop = { vm.openShop(it) },
                        onClearError = { vm.clearError() },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                GameTab.Character -> {
                    CharacterPager(
                        state = state,
                        onEquip = vm::equipToggle,
                        onDismiss = vm::dismissCompanion,
                        onHotbar = vm::updateHotbar,
                        onCast = { spell ->
                            val activeCombat = state.combat
                            val recentTargets = if (activeCombat != null) {
                                activeCombat.order
                                    .filter { !it.isPlayer }
                                    .map { it.name }
                                    .distinct()
                                    .take(8)
                            } else emptyList()
                            tab = GameTab.Chat
                            if (isSelfCastable(spell)) {
                                vm.submitAction("I cast ${spell.name} on myself")
                            } else {
                                vm.requestTargetPrompt(
                                    TargetPromptSpec(
                                        title = "Cast ${spell.name}",
                                        verb = "I cast ${spell.name} at",
                                        selfCastable = isSelfCastable(spell),
                                        recentTargets = recentTargets
                                    )
                                )
                            }
                        }
                    )
                }
                GameTab.Journal -> {
                    JournalPager(
                        state = state,
                        onAbandon = vm::abandonQuest,
                        focusNpc = journalFocusNpc
                    )
                }
            }
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

    if (showCheatsOverlay) {
        CheatsOverlay(
            unnaturalTwenty = unnaturalTwenty,
            loser = loser,
            infiniteGold = infiniteGold,
            characterLevel = state.character?.level ?: 1,
            onToggleUnnaturalTwenty = { vm.setUnnaturalTwenty(it) },
            onToggleLoser = { vm.setLoser(it) },
            onToggleInfiniteGold = { vm.setInfiniteGold(it) },
            onApplyOverprepared = {
                vm.applyOverprepared()
                showCheatsOverlay = false
            },
            onDismiss = { showCheatsOverlay = false }
        )
    }

    when (panel) {
        Panel.Settings -> {
            val balance by vm.balanceUsd.collectAsState()
            // Auto-refresh when Settings opens; the VM's 60s cache prevents hammering.
            LaunchedEffect(Unit) { vm.refreshBalance() }
            SettingsPanel(
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
                onReturnToTitle = { vm.returnToTitle() },
                balanceUsd = balance,
                onRefreshBalance = { vm.refreshBalance(force = true) }
            )
        }
        else -> {}
    }

    } // end CompositionLocalProvider
}


