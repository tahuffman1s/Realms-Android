# Realms of Fate — Developer Guide

Everything you need to understand, build, and extend the project without AI assistance.

---

## Table of Contents

1. [Environment Setup](#1-environment-setup)
2. [Project Layout](#2-project-layout)
3. [Architecture](#3-architecture)
4. [Game State Model](#4-game-state-model)
5. [AI Integration](#5-ai-integration)
6. [Turn Pipeline](#6-turn-pipeline)
7. [Reducers](#7-reducers)
8. [Handlers](#8-handlers)
9. [UI Layer](#9-ui-layer)
10. [Debug Bridge](#10-debug-bridge)
11. [Testing](#11-testing)
12. [Build System](#12-build-system)
13. [Common Tasks](#13-common-tasks)

---

## 1. Environment Setup

### Requirements

| Tool | Version |
|------|---------|
| JDK | 17+ |
| Kotlin | 2.2 |
| Android SDK | 34 (compileSdk) / 26 (minSdk) |
| AGP | 9.0 |
| Gradle | 9.4+ |
| KVM | required for emulator |

### First-time setup (Arch Linux)

```bash
./setup-env.sh
```

This installs JDK, Android SDK, platform tools, system image, and creates a Pixel 7 AVD named `Pixel7`.

### SDK path

```bash
cp local.properties.sample local.properties
# Edit sdk.dir=/path/to/Android/Sdk
```

### Gradle home fix

The project pins `ANDROID_USER_HOME` to a local directory to avoid polluting `~/.android`:

```bash
export ANDROID_USER_HOME=$(pwd)/.android-user
```

This is already baked into `setup-env.sh` and the run alias. If Gradle complains about the AVD not being found, this is the first thing to check.

---

## 2. Project Layout

```
RealmsAndroid/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── kotlin/com/realmsoffate/game/
│       │       ├── MainActivity.kt         — entry point, screen router
│       │       ├── RealmsApp.kt            — Application class, Debug Bridge start
│       │       ├── data/                   — AI, models, prefs, persistence, parsing
│       │       ├── game/                   — ViewModel, world gen, D&D rules, reducers, handlers
│       │       └── ui/                     — all Compose screens, panels, overlays
│       ├── debug/
│       │   └── kotlin/com/realmsoffate/game/debug/   — HTTP Debug Bridge (debug builds only)
│       └── test/
│           └── kotlin/com/realmsoffate/game/          — JVM + Robolectric tests
├── docs/
│   └── DEVELOPER.md                        — this file
├── CLAUDE.md                               — AI agent instructions
├── ROADMAP.md
└── README.md
```

### `data/` — data layer

| File | What it does |
|------|-------------|
| `Models.kt` | Every `@Serializable` data class. Start here to understand any domain type. |
| `AiRepository.kt` | HTTP client for DeepSeek API. One `generate()` call per turn plus `classifyAction()` for skill checks. |
| `AiProvider.kt` | Enum: `DEEPSEEK`, `GEMINI`, `CLAUDE`. Only DeepSeek is active; others are dormant. |
| `Prompts.kt` | The full narrator system prompt (`SYS`), the DeepSeek preamble (`DS_PREFIX`), the per-turn reminder (`PER_TURN_REMINDER`), and `buildSessionSystem()` which assembles the stable, cacheable portion. |
| `TagParser.kt` | Parses raw LLM text into `ParsedReply`. Two paths: JSON `[METADATA]` block and legacy regex fallback. Also runs the Phase-B segment tokenizer for narrative rendering. |
| `NarrativeTokens.kt` | Token types for the Phase-B tokenizer (`NARRATOR_PROSE`, `NPC_DIALOG`, etc.). |
| `SaveStore.kt` | JSON save/load via kotlinx.serialization. Also manages the graveyard for dead characters. |
| `PreferencesStore.kt` | DataStore for API key, provider, font scale. |

### `game/` — domain layer

| File | What it does |
|------|-------------|
| `GameViewModel.kt` | Central orchestrator. All `StateFlow` lives here. Owns `submitAction` → `dispatchToAi` → `applyParsed` pipeline. |
| `WorldGen.kt` | Seeded procedural world: 11-15 locations, roads (Prim's MST), terrain, rivers, lakes. |
| `LoreGen.kt` | Factions (8-12), NPCs (3-6 per faction), world name, primordial history, rumors, 2-3 mutations. |
| `BackstoryGen.kt` | Player character backstory (secret, enemy, prophecy hook). |
| `Scenarios.kt` | 18 opening scene templates that the first AI turn is seeded with. |
| `Mutations.kt` | 16 world mutation definitions (e.g., "The Dead Walk", "Eternal Winter"). Each has a `promptText` injected into the system prompt. |
| `WorldEvents.kt` | Weighted random event templates triggered during `maybeRollWorldEvent`. |
| `CombatSystem.kt` | Initiative rolling, combatant sync. |
| `Dice.kt` | `d20()`, `d(n)`, `NdM±K` formula parser. |
| `Spells.kt` | 34-entry spell/ability database. |
| `SpellSlots.kt` | Slot tables per class/level + short/long rest recharge. |
| `Feats.kt` | Feat definitions for level-up selection. |
| `Classes.kt` | 12 class definitions (hit die, proficiencies, starting gear). |
| `Races.kt` | 11 race definitions + physique template strings. |

### `game/reducers/` — pure state transformations

All reducers are `object` singletons. They take immutable inputs and return result data classes. No `StateFlow`, no Android dependencies.

| File | Domain |
|------|--------|
| `CharacterReducer.kt` | HP, XP, gold, inventory, conditions, level-up detection |
| `NpcLogReducer.kt` | NPC meet/update/death/dialogue, quote tracking |
| `QuestAndPartyReducer.kt` | Quest lifecycle, party joins/leaves |
| `WorldReducer.kt` | Faction field updates, lore history |
| `CombatReducer.kt` | Combat scene transitions, initiative order, enemy HP |

### `game/handlers/` — stateful side effects

Handlers receive `MutableStateFlow` references from the ViewModel and own specific overlay/feature logic.

| File | Domain |
|------|--------|
| `MerchantHandler.kt` | Buy, sell, buyback, currency exchange, haggle |
| `RestHandler.kt` | Short/long rest, death saves, `die()` |
| `ProgressionHandler.kt` | Stat point assignment, feat selection, level-up flows |
| `SaveService.kt` | Save/load/export/import/delete, graveyard |

### `ui/` — Compose layer

```
ui/
├── game/           — GameScreen, ChatFeed, MessageBubbles, NarrationBlock,
│                     ChatInput, TopBar, BottomNav, CharacterPager, JournalPager,
│                     CombatHud, GameDialogs
├── panels/         — StatsPage, InventoryPage, SpellsPage, PartyPage,
│                     CurrencyPage, QuestsPage, LorePage, NpcJournalPage
├── overlays/       — Overlays (LevelUp, Feat, Rest, Initiative),
│                     ShopOverlay, TargetPromptDialog
├── setup/          — ApiSetupScreen, TitleScreen, CharacterCreationScreen, DeathScreen
├── theme/          — Theme, Tokens, Extended (narration colors), Type, Fonts
├── map/            — Compose Canvas world map
├── dice/           — d20 roll animation
└── components/     — RealmsCard, PanelSheet, reusable composables
```

---

## 3. Architecture

### Layer diagram

```
┌─────────────────────────────────────────────┐
│  UI (Jetpack Compose)                       │
│  GameScreen / ChatFeed / panels / overlays  │
│              collectAsState()               │
├─────────────────────────────────────────────┤
│  State (MutableStateFlow)                   │
│  GameViewModel — single source of truth     │
│              calls reducers/handlers        │
├─────────────────────────────────────────────┤
│  Domain (pure functions + handlers)         │
│  CharacterReducer, NpcLogReducer, ...       │
│  MerchantHandler, RestHandler, ...          │
│              calls                          │
├─────────────────────────────────────────────┤
│  Data                                       │
│  AiRepository → DeepSeek API               │
│  TagParser → ParsedReply                   │
│  SaveStore → JSON files                    │
│  PreferencesStore → DataStore              │
└─────────────────────────────────────────────┘
```

### State management

All observable game state lives in one `MutableStateFlow<GameUiState>` (`_ui`) inside `GameViewModel`. The screen router is a separate `MutableStateFlow<Screen>` (`_screen`).

`GameScreen` calls `.collectAsState()` once at the top level and passes values down by parameter. No state hoisting through `remember`.

Handlers (`MerchantHandler`, `RestHandler`, `ProgressionHandler`) hold references to the shared `_ui` flow and write to it directly. This keeps overlay-specific flows local to the handler while preserving a single source of truth.

### Navigation

There is no Compose Navigation graph. Routing is a `when` block in `MainActivity.kt` switching on `vm.screen.collectAsState()`. The `Screen` enum has five values: `ApiSetup`, `Title`, `CharacterCreation`, `Game`, `Death`.

### Coroutines

The ViewModel uses a `viewModelScope`. AI calls run on `Dispatchers.IO`. State mutations are done back on the main thread via `withContext(Dispatchers.Main)` or by assigning to `_ui.value` (safe from any thread for `StateFlow`).

`tryClaimSubmit()` is `@Synchronized` and sets `isGenerating = true` before a coroutine is launched. This is the double-submit guard.

---

## 4. Game State Model

### Runtime: `GameUiState`

Defined inline in `GameViewModel.kt`. Key fields:

```kotlin
data class GameUiState(
    val character: Character?,
    val worldMap: WorldMap?,
    val currentLoc: Int,              // index into worldMap.locations
    val worldLore: WorldLore?,
    val npcLog: List<LogNpc>,         // every NPC ever met
    val party: List<PartyCompanion>,
    val quests: List<Quest>,
    val history: List<ChatMsg>,       // raw LLM messages (capped at 40)
    val messages: List<DisplayMessage>, // rendered chat feed
    val currentChoices: List<Choice>,
    val turns: Int,
    val morality: Int,                // -100 to +100
    val factionRep: Map<String, Int>, // factionId -> reputation
    val combat: CombatState?,         // non-null during battle
    val deathSave: DeathSaveState?,   // non-null at 0 HP
    val preRoll: PreRollDisplay?,     // dice preview before AI call
    val merchantStocks: Map<String, Map<String, Int>>,
    val cachedSessionSystem: String?, // memoized for DeepSeek prefix cache
    // ... overlays: isGenerating, showShop, pendingShopNpc, etc.
)
```

### Persisted: `SaveData`

`SaveData` (in `Models.kt`) is what's written to disk. Structurally mirrors `GameUiState` plus:

- `displayMessages: List<DisplayMessage>` — full chat feed for restore
- `debugLog: List<DebugTurn>` — last 50 AI exchanges (prompt, raw response, parse path)
- `timeline: List<TimelineEntry>` — chronological log for the death screen
- `deathSave: DeathSaveState?` — persisted so a crash during death saves is recoverable
- `version: Int` — currently 2; `migrateIds()` runs on load for legacy saves

Save files live at `files/saves/slot_<name>.json`. Graveyard entries are at `files/graveyard/`.

### Core domain types (all in `Models.kt`)

**`Character`**
```
name, race, cls, level, xp, hp, maxHp, ac, gold
abilities: Abilities (STR/DEX/CON/INT/WIS/CHA)
inventory: List<Item> (mutable)
knownSpells: List<String>
spellSlots: Map<Int, SpellSlotState>
feats: List<String>
conditions: List<String>
currencyBalances: Map<String, Int>   // factionCurrencyId -> amount
backstory: Backstory
appearance: String
```

**`Item`**
```
name, desc, type, rarity, qty
equipped: Boolean
damage: String?    // e.g. "1d8+3"
ac: Int?
```

**`LogNpc`** — one entry per NPC ever encountered
```
id: String              // stable slug, e.g. "prosper-saltblood"
name, race, role
relationship: String    // "friendly", "hostile", "neutral", etc.
dialogueHistory: List<String>   // capped at 20
memorableQuotes: List<String>   // capped at 12
status: String          // "alive", "dead", "unknown"
factionId: String?
locationId: Int?
```

**`Faction`**
```
id: String              // stable slug
name, type
government: GovernmentInfo
economy: EconomyInfo
currency: String        // faction's currency name
status: String
ruler: String
```

**`TurnMetadata`** — the JSON schema the AI fills in each turn, decoded from `[METADATA]{...}[/METADATA]`
```
damage, heal, xp, gold
items: List<ItemDelta>
conditions: List<ConditionDelta>
npcsMet: List<NpcMetEntry>
npcUpdates: List<NpcUpdateEntry>
npcDeaths: List<String>
npcDialogs: Map<String, String>
npcActions: Map<String, String>
questStarts: List<QuestStart>
questUpdates: List<QuestUpdate>
enemies: List<EnemyEntry>
factionUpdates: List<FactionUpdate>
checks: List<SkillCheck>
travelTo: Int?
shops: List<ShopEntry>
partyJoins: List<String>
partyLeaves: List<String>
loreEntries: List<String>
moralityDelta: Int
```

---

## 5. AI Integration

### Provider

Only DeepSeek is active (`model = "deepseek-chat"`). Gemini and Claude variants exist in `AiProvider.kt` but are dormant (no routing logic calls them).

Endpoint: `POST https://api.deepseek.com/v1/chat/completions`

Parameters:
```
max_tokens = 1800
temperature = 1.0
top_p = 0.95
frequency_penalty = 0.3
presence_penalty = 0.1
```

### Prompt structure

Each API call sends two parts:

**System message** (stable, cached):
```
DS_PREFIX + SYS + buildSessionSystem(character, worldLore)
```

`buildSessionSystem()` emits (in order):
1. CHARACTER — name, race, class, level, abilities, proficiency, racial physique
2. BACKSTORY — `character.backstory.promptText`
3. ACTIVE MUTATIONS — full `mutation.promptText` for each active mutation
4. WORLD CONDITIONS — short mutation descriptions
5. WORLD LORE — first primordial history entry
6. WORLD PALETTE — name pool strings from LoreGen (keeps NPC names consistent)

This is deterministic and only changes on level-up or lore mutation. It's stored in `cachedSessionSystem` and reused every turn so DeepSeek's prefix cache hits consistently.

**User message** (volatile, changes every turn):
```
Current HP/gold/AC/location/faction rep/morality
Nearby locations (from worldMap)
Party members
Active quests
Local NPCs with IDs
Known NPC roster: id → name (so the AI uses stable IDs)
Recent world events
Equipped items
Inventory
Last 2 narration segments
---
Player action
Dice result line
PER_TURN_REMINDER (appended at the end)
```

### Skill classification

Before the AI narrative call, a lightweight classification call determines which D&D skill applies:

```
POST /v1/chat/completions
max_tokens = 10
temperature = 0
```

Prompt: "What D&D 5e skill check applies to: [action]? Reply with just the skill name or 'none'."

This result drives the pre-roll preview (which ability modifier to use). Falls back to `localClassifyAction()` (keyword matching) on API failure.

### Response parsing

`TagParser.parse(raw, currentTurn)` takes the raw string and returns `ParsedReply`.

**Path A — JSON metadata** (preferred):
1. Finds `[METADATA]` ... `[/METADATA]` block
2. Normalizes smart quotes (`"` → `"`)
3. Decodes with a lenient `Json` decoder as `TurnMetadata`
4. Extracts prose by stripping the metadata block

**Path B — Regex fallback**:
1. Scans for inline tags: `[DAMAGE:N]`, `[HEAL:N]`, `[NPC_MET:id|name|...]`, etc.
2. Applies prose-extraction regexes for damage/heal/gold/xp if those are zero

Both paths also run the **Phase-B tokenizer** (`tokenizeNarrative` + `buildSegments`):
1. Walks the prose once, emitting typed tokens for structural sections
2. Token types: `NARRATOR_PROSE`, `NARRATOR_ASIDE`, `PLAYER_ACTION`, `NPC_ACTION`, `NPC_DIALOG`, `PLAYER_DIALOG`
3. Builds a segment list used by `NarrationBlock` for rich UI rendering (prose, purple aside pills, NPC dialog bubbles)

`ParseSource` on `ParsedReply` records which path was taken. Visible in `DebugTurn` (accessible via Debug Bridge `/state`).

---

## 6. Turn Pipeline

This is the full path from player tap to state update.

### Step 1: `submitAction(text)`

```
GameViewModel.submitAction(text)
  → tryClaimSubmit()          // sets isGenerating=true, returns false if already running
  → classifyAction(text)      // lightweight AI call to determine skill
  → buildPreRoll(skill, char) // d20 + modifiers
  → _ui.value = ... preRoll = PreRollDisplay(...)
  // AI call deferred — user sees dice preview
```

### Step 2: `confirmPreRoll()`

```
GameViewModel.confirmPreRoll()
  → posts player bubble optimistically
  → dispatchToAi(action, roll, skill)
```

### Step 3: `dispatchToAi(action, roll, skill)`

```
buildSystemPrompt()     // DS_PREFIX + SYS + cachedSessionSystem
buildUserMessage()      // volatile per-turn context + action + dice line
AiRepository.generate(systemPrompt, userMessage, history)
  → POST to DeepSeek
  → returns raw string
TagParser.parse(raw, turns)
  → returns ParsedReply
applyParsed(seed, parsedReply)
  → returns updated GameUiState
CombatReducer.transition(...)
maybeRollWorldEvent()
autosave to "autosave" + character-name slot
```

### Step 4: `applyParsed(seed, parsed)`

The orchestrator. Calls all reducers in order (see §7 for details):

1. `CharacterReducer.apply` — HP, XP, gold, items, conditions, level-up detection
2. Level-up signals → dispatched to `ProgressionHandler`
3. Build display messages (player bubble, narration bubble with stat deltas)
4. Morality + faction rep math (inline in ViewModel)
5. Travel resolution
6. `NpcLogReducer.apply`
7. `WorldReducer.apply`
8. `QuestReducer.apply`
9. `PartyReducer.apply`
10. Merchant stock merge
11. Skill check display message
12. `return state.copy(...all new values)`

---

## 7. Reducers

All reducers are `object` singletons in `game/reducers/`. They are pure — no `StateFlow`, no Android, no IO. Independently testable.

### `CharacterReducer`

```kotlin
fun apply(ch: Character, parsed: ParsedReply, currentTurn: Int): CharacterApplyResult
```

- Applies HP delta: `(ch.hp + parsed.heal - parsed.damage).coerceIn(0, ch.maxHp)`
- Applies XP gain, checks D&D 5e XP thresholds for level-up
- Applies gold delta
- Adds/removes items from inventory
- Adds/removes conditions
- On level-up: increments level, recalculates maxHp, refreshes spell slots, emits `LevelUpSignal`
- `LevelUpSignal.featPending = (newLevel % 4 == 0)`

Returns `CharacterApplyResult(character, hpBefore, goldBefore, levelUpSignal?)`.

### `NpcLogReducer`

```kotlin
fun apply(npcLog, combat, parsed, currentTurn, currentLocName): NpcLogApplyResult
```

- Merges `parsed.npcsMet`: tries ID lookup first, falls back to case-insensitive name
- Auto-stubs any NPC referenced in `npcDialogs`/`npcActions` that isn't already in the log
- Appends dialogue history (capped at 20 entries)
- Appends memorable quotes (capped at 12)
- Marks NPCs in `parsed.npcDeaths` as `status = "dead"`
- Removes dead NPCs from combat initiative order
- Applies `parsed.npcUpdates` (relationship, role, faction, location, status, name rename)

Returns `NpcLogApplyResult(npcLog, combat, systemMessages, timelineEntries)`.

### `WorldReducer`

```kotlin
fun apply(worldLore, npcLog, parsed, currentTurn): WorldApplyResult
```

- Applies `parsed.factionUpdates` by ID (falls back to name match)
- Dead-leader cascade: if a dying NPC is a faction's ruler, marks ruler as "(Deceased)"
- Appends `parsed.loreEntries` as `HistoryEntry(era="recent")` to `worldLore.history`

### `QuestReducer` + `PartyReducer` (both in `QuestAndPartyReducer.kt`)

`QuestReducer.apply`:
- Adds `parsed.questStarts` as new quests
- Updates quest objectives: a superset objective string replaces a subset (dedup logic)
- Marks quests completed/failed via `parsed.questUpdates`

`PartyReducer.apply`:
- Appends `parsed.partyJoins` as `PartyCompanion` entries
- Removes first case-insensitive name match from `parsed.partyLeaves`

### `CombatReducer`

```kotlin
fun transition(scene, combat, character, party, npcLog, parsedEnemies, currentTurn): CombatTransitionResult
```

- If `scene == "battle"` and no active combat: starts fresh via `CombatSystem.startCombat`, emits `showInitiative = true`
- If `scene == "battle"` with active combat: advances the round via `combat.next()`, syncs HP
- Merges `parsedEnemies` into initiative order (update HP if known, add with rolled initiative if new)
- HP-zero enemies → marked dead in NPC log, removed from initiative
- If `scene != "battle"` with active combat: emits "Combat has ended" system message, returns `combat = null`

---

## 8. Handlers

Handlers are classes with constructor-injected `MutableStateFlow<GameUiState>` from the ViewModel.

### `MerchantHandler`

```kotlin
fun buy(itemName: String, price: Int, npcId: String)
fun sell(itemName: String, sellPrice: Int)
fun buyback(itemName: String, price: Int)
fun exchange(fromCurrency: String, toCurrency: String, amount: Int)
fun haggle(npcId: String): Float   // returns price multiplier 0.8–1.0
```

- `buy`: deducts gold, adds item to inventory
- `sell`: removes item, adds gold, records in `buybackList` (capped at 8, stored at price×2)
- `buyback`: reverses a sell at the stored buyback price
- `exchange`: converts currency using faction `economy.wealth` as the rate
- `haggle`: rolls a CHA check, returns 0.8 on success, 0.9 partial, 1.0 failure

### `RestHandler`

```kotlin
fun shortRest()
fun longRest()
fun rollDeathSave()
```

- `shortRest`: rolls `(hitDie + conMod)`, heals HP, calls `SpellSlots.applyShortRest`
- `longRest`: restores full HP and all spell slots, removes non-permanent conditions
- `rollDeathSave`: rolls d20
  - Nat 20 → immediately stable (1 HP)
  - Nat 1 → two failures added
  - 3 successes → stable at 1 HP, dismisses overlay
  - 3 failures → `die()` is called
- `die()`:
  1. Creates `GraveyardEntry` with name, race, class, level, cause, timeline
  2. Calls `SaveStore.bury(graveyardEntry)` — writes to `files/graveyard/`
  3. Deletes both save slots for the character
  4. Sets `_screen.value = Screen.Death`

### `ProgressionHandler`

Exposes three flows:
```kotlin
val pendingLevelUp: StateFlow<LevelUpSignal?>
val pendingStatPoints: StateFlow<Int>
val pendingFeat: StateFlow<Boolean>
```

```kotlin
fun assignStatPoint(stat: AbilityStat)   // increments ability score, decrements pendingStatPoints
fun selectFeat(featName: String)          // appends to character.feats
```

Level-up signal is dispatched from `applyParsed` when `CharacterReducer` detects an XP threshold crossing.

### `SaveService`

```kotlin
fun saveToSlot(slotName: String)
fun loadSlot(slotName: String): Boolean
fun deleteSlot(slotName: String)
fun exportCurrentJson(): String
fun importSave(json: String): Boolean
fun refreshSlots(): List<SlotMetadata>
fun exhumeGrave(id: String)             // loads graveyard entry for death screen review
```

- Save files: `files/saves/slot_<name>.json`
- Graveyard: `files/graveyard/<id>.json`
- `loadSlot` calls `migrateIds()` on the loaded data to backfill stable IDs for old saves
- `snapshotSaveData()` is called inside `saveToSlot` — assembles `SaveData` from current `GameUiState`

---

## 9. UI Layer

### Screen routing

`MainActivity.kt` calls `RealmsRoot(vm)` which switches on `vm.screen.collectAsState()`:

```kotlin
when (screen) {
    Screen.ApiSetup -> ApiSetupScreen(vm)
    Screen.Title -> TitleScreen(vm)
    Screen.CharacterCreation -> CharacterCreationScreen(vm)
    Screen.Game -> GameScreen(vm)
    Screen.Death -> DeathScreen(vm)
}
```

### `GameScreen` structure

```
Scaffold
├── TopBar (GameTopBar)
│   └── HP bar, AC, gold, scene desc (Chat tab only, non-combat)
│       Settings icon → SettingsPanel sheet
├── Body
│   ├── ChatFeed (Chat tab)
│   ├── CharacterPager (Character tab)
│   └── JournalPager (Journal tab)
├── BottomBar
│   ├── GameInputBar (text field + send + spell hotbar + choices)
│   └── GameBottomNav (Chat / Character / Journal tabs)
├── FAB (attack button, visible during combat)
└── Overlays (as Dialogs / ModalBottomSheets)
    ├── PreRollDialog (when state.preRoll != null)
    ├── DeathSaveDialog (when state.deathSave != null)
    ├── LevelUpOverlay
    ├── FeatSelectionOverlay
    ├── RestOverlay
    ├── InitiativeOverlay
    ├── ShopOverlay
    ├── TargetPromptDialog
    ├── ChoicesSheet
    └── SettingsPanel
```

### Chat feed (`ChatFeed.kt` + `MessageBubbles.kt`)

`ChatFeed` renders a `LazyColumn` of `DisplayMessage`. Four composables handle the four message types:

| Type | Composable | Appearance |
|------|-----------|-----------|
| `PLAYER` | `PlayerBubble` | Trailing bubble, player color |
| `NARRATION` | `NarrationBlock` | Leading, uses segment list for rich rendering |
| `EVENT` | `EventCard` | Icon + title card |
| `SYSTEM` | `SystemLine` | Dimmed single line |

`NarrationBlock` reads `message.segments` (built by the Phase-B tokenizer) and renders each segment type differently:
- `NARRATOR_PROSE` → styled text
- `NARRATOR_ASIDE` → purple pill with italic text
- `NPC_DIALOG` → NPC name header + quoted dialog
- `PLAYER_DIALOG` → player dialog in a different color

Auto-scroll anchors to `state.turnStartIndex` (the first message of the current turn).

### Character tab

`CharacterPager` wraps a `HorizontalPager` with 5 tabs:
1. **Stats** — ability scores, saving throw proficiencies, conditions, feats
2. **Inventory** — item list with equip toggle
3. **Spells** — known spells, slot indicators per level, hotbar assignment
4. **Party** — companion cards
5. **Currency** — gold + all faction currencies + exchange UI

### Journal tab

`JournalPager` wraps a `HorizontalPager` with 3 tabs:
1. **Quests** — active / completed / failed, with objectives
2. **Lore** — factions, NPCs, world history, mutations, events
3. **NPC Journal** — per-NPC dialogue history and memorable quotes

### Theme

`RealmsTheme` in `ui/theme/Theme.kt` applies Material You dynamic color (Android 12+) with a fantasy fallback palette. Uses Cinzel (display) and Crimson Text (body) fonts from Google Fonts.

`LocalFontScale` (defined in `GameScreen.kt`) provides user-set font scale to all descendants, multiplied by the Android system font scale.

`ExtendedColors` (in `Extended.kt`) adds narration-type-specific colors (aside, combat, system, etc.) as `MaterialTheme.extended.*`.

---

## 10. Debug Bridge

The Debug Bridge is a plain HTTP server running inside the debug APK only. It lives entirely in `app/src/debug/` and is excluded from release builds.

### Setup

After installing the debug APK:

```bash
adb -s emulator-5554 forward tcp:8735 tcp:8735
```

The server is then reachable at `http://localhost:8735` from the host machine.

`DebugBridge.start()` is called from `RealmsApp.onCreate()`. It holds references to the live `Activity` and `GameViewModel`, set via the `onAttach` callback wired in `MainActivity.onCreate`.

### All endpoints

**State inspection**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/state` | Full `GameUiState` as JSON. Saves snapshot for diff. |
| GET | `/state/diff` | Changes since last `GET /state`. |
| GET | `/state/overlay` | Which overlays are currently active. |

**Commands**

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/input` | `{"text":"..."}` | Calls `vm.submitAction(text)`. Waits 500ms, reports if preRoll queued. |
| POST | `/confirm` | — | Confirms the active preRoll. Dispatches to AI. |
| POST | `/cancel` | — | Dismisses the active preRoll. |
| POST | `/navigate` | `{"screen":"game"}` | Switches screen. Values: `apiSetup`, `title`, `characterCreation`, `game`, `death`. |
| POST | `/tap` | `{"text":"..."}` | Finds a Compose semantics node by text/contentDesc and dispatches touch events. |

**State injection** (for testing without AI calls)

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/inject` | `{field: value, ...}` | Overrides individual `GameUiState` fields. Supports: `turns`, `isGenerating`, `currentScene`, `morality`, `character.hp`, `character.maxHp`, `character.gold`, `character.level`, `character.xp`, `character.ac`, `character.name`, `character.conditions`. |
| POST | `/inject/messages` | `{"messages":[...]}` | Appends display messages to the chat feed. |
| POST | `/inject/reset` | — | Restores the state snapshot from before the first `/inject`. |

**Macros** (multi-step automation)

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/macro/new-game` | `{"name":"Test","class":"fighter","race":"human","skipFirstTurn":true}` | Creates a character and optionally injects a first turn. |
| POST | `/macro/death` | — | Sets HP to 0, navigates to Death screen. |
| POST | `/macro/advance` | `{"turns":N,"mode":"canned"}` | Injects N canned turns (no AI calls). |
| POST | `/macro/simulate-gameplay` | — | Injects a full mid-campaign state with NPCs, quests, party, merchants, and chat history. Useful for UI QA. |

**UI inspection**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/describe` | Text tree of Compose semantics hierarchy with bounds. |
| GET | `/describe?format=json` | JSON version + base64-encoded PNG screenshot. |
| GET | `/describe/annotated` | PNG with color-coded element bounds and `file:line` labels overlaid. |
| GET | `/describe/ui` | Browser-based interactive UI inspector (HTML served by the app). |

**Screenshot and theme**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/screenshot` | Raw PNG of the app viewport. |
| GET | `/theme` | Current theme state. |
| POST | `/theme/fontscale` | Override font scale. |

### Source index

A Gradle task `generateDebugSourceIndex` (runs before `compileDebugKotlin`) walks all `.kt` source files, extracts UI text literals from `Text()`, `Button()`, label parameters, and writes `SourceIndex.kt` to the debug source set. This maps literal text → `"FileName.kt:LineNumber"`. Used by `DescribeEndpoints` to annotate `/describe/annotated` screenshots with source locations.

---

## 11. Testing

### Layout

All tests are JVM + Robolectric (`app/src/test/`). No instrumented tests in `androidTest/`.

Robolectric is configured with:
```kotlin
testOptions {
    unitTests.isIncludeAndroidResources = true
}
```

Tests that use `GameViewModel` must be annotated `@Config(sdk = [34])`.

### Test files

| File | What it tests |
|------|--------------|
| `TagParserTest.kt` | `TagParser.parse()`: JSON path, regex fallback, segment extraction, quote normalization |
| `ApplyParsedIntegrationTest.kt` | `GameViewModel.applyParsed` end-to-end: 20+ scenarios across all reducer domains |
| `MerchantHandlerTest.kt` | Buy/sell/buyback/exchange/haggle logic |
| `ProgressionHandlerTest.kt` | Stat point assignment, feat selection, level-up signal lifecycle |
| `RestHandlerTest.kt` | Short/long rest, death save progression, `die()` |
| `SaveServiceTest.kt` | Save/load round-trip, graveyard write, slot metadata |

### Test harness

**`GameStateFixture.kt`** provides:
```kotlin
fun character(level, xp, hp, maxHp): Character
fun baseState(character, npcLog, quests, factions, turns): GameUiState
fun viewModelWithState(state): GameViewModel      // Robolectric
fun getPendingLevelUp(vm): LevelUpSignal?          // reflective read
fun injectState(vm, state)                         // calls vm.debugInjectState
```

**`ParsedReplyBuilder.kt`** — fluent builder for `ParsedReply`:
```kotlin
ParsedReplyBuilder()
    .damage(10)
    .heal(5)
    .npcMet("prosper-saltblood", "Prosper Saltblood", "merchant", "friendly")
    .questStart("Find the Seal", "Travel to the ruins", "the-seal")
    .build()
```

### Running tests

```bash
./gradlew test
```

### Writing new tests

For reducer tests, use pure function calls — no Robolectric needed:
```kotlin
val result = CharacterReducer.apply(character, parsedReply, turn = 5)
assertEquals(expectedHp, result.character.hp)
```

For integration tests that need a running ViewModel, use `viewModelWithState()` from `GameStateFixture`. The VM's `debugInjectState()` method is available in all builds (it's not in the debug source set — it's a test-only backdoor in the main source).

---

## 12. Build System

### `app/build.gradle.kts` key config

```kotlin
android {
    compileSdk = 34
    defaultConfig {
        applicationId = "com.realmsoffate.game"
        minSdk = 26
        targetSdk = 34
        versionCode = 100
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}
```

### Build variants

**`debug`**:
- `isMinifyEnabled = false`
- Includes `app/src/debug/` source set (entire Debug Bridge)
- `generateDebugSourceIndex` task runs before `compileDebugKotlin`
- Output: `app/build/outputs/apk/debug/app-debug.apk`

**`release`**:
- `isMinifyEnabled = true`, `isShrinkResources = true`
- `proguard-rules.pro` keeps serializable classes and OkHttp internals
- Signs with config from `keystore.properties` (local) or env vars
- Debug source set excluded entirely
- Output: `app/build/outputs/apk/release/app-release.apk`

### Key dependencies

```
Compose BOM 2024.09.02
  material3, animation, foundation, ui-tooling-preview
androidx.navigation:navigation-compose:2.8.2
androidx.datastore:datastore-preferences:1.1.1
kotlinx.serialization.json:1.7.3
kotlinx.coroutines.android:1.9.0
com.squareup.okhttp3:okhttp:4.12.0   (AI HTTP client)
androidx.core:core-splashscreen:1.0.1
robolectric:4.13 (testImplementation)
```

Note: The Debug Bridge HTTP server uses plain `java.net.ServerSocket`, not OkHttp.

---

## 13. Common Tasks

### Add a new world mutation

1. Open `game/Mutations.kt`
2. Add a new `Mutation(id, name, description, promptText)` to the `ALL_MUTATIONS` list
3. The `promptText` is injected verbatim into the system prompt, so write it in narrator voice
4. `LoreGen` randomly picks 2-3 mutations per world, so it will appear automatically

### Add a new NPC update field

1. Add the field to `NpcUpdateEntry` in `Models.kt`
2. Handle it in `NpcLogReducer.apply` where `parsed.npcUpdates` is processed
3. Update the `TurnMetadata` schema in `Models.kt` if the AI needs to emit it
4. Update the system prompt in `Prompts.kt` to instruct the AI on when to use it
5. Add a test in `ApplyParsedIntegrationTest`

### Add a new handler action (e.g., a new merchant action)

1. Add the method to `MerchantHandler.kt`
2. Wire it in `GameViewModel` by exposing a public function that delegates to `merchantHandler.*`
3. Add a UI trigger in `ShopOverlay.kt`
4. Add tests in `MerchantHandlerTest.kt`

### Add a new AI-driven game mechanic

The full path is:
1. Add fields to `TurnMetadata` in `Models.kt`
2. Add parsing logic in `TagParser.kt` (the JSON decoder will pick up new `TurnMetadata` fields automatically if they're `@Serializable`)
3. Add fields to `ParsedReply` (the intermediate parsed form used by reducers)
4. Map `TurnMetadata` fields to `ParsedReply` in the parsing code
5. Handle the field in the appropriate reducer
6. Update the system prompt in `Prompts.kt`
7. Add integration tests

### Add a new screen

1. Add a value to the `Screen` enum
2. Add the Compose screen composable in `ui/setup/` or a new subdirectory
3. Add a `when` branch in `RealmsRoot` in `MainActivity.kt`
4. Set `vm.setScreen(Screen.NewScreen)` wherever navigation should trigger

### Debug a specific turn

1. Install debug APK and forward the port
2. `curl http://localhost:8735/state | jq '.debugLog[-1]'` — shows the last AI exchange
3. Fields: `prompt`, `rawResponse`, `parseSource` (JSON or REGEX_FALLBACK), `parsedReply`
4. Or use `GET /describe/ui` in a browser for a live visual inspector

### Force-test a specific game state

```bash
# Inject low HP for death save testing
curl -X POST http://localhost:8735/inject \
  -H "Content-Type: application/json" \
  -d '{"character.hp": 0}'

# Start a fresh game without entering API key manually
curl -X POST http://localhost:8735/macro/new-game \
  -H "Content-Type: application/json" \
  -d '{"name":"TestChar","class":"fighter","race":"human","skipFirstTurn":true}'

# Simulate a full mid-campaign state for UI testing
curl -X POST http://localhost:8735/macro/simulate-gameplay
```

### Run the full deploy + verify cycle

After editing main source:

```bash
./gradlew installDebug && \
  adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && \
  adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Then run the P0+P1 checks from `.cursor/rules/debug-bridge-test-procedures.mdc`.

---

## Key files quick reference

| What you're looking for | File |
|------------------------|------|
| All data types | `data/Models.kt` |
| AI prompt content | `data/Prompts.kt` |
| AI HTTP client | `data/AiRepository.kt` |
| Response parser | `data/TagParser.kt` |
| Save/load | `data/SaveStore.kt` |
| Full turn pipeline | `game/GameViewModel.kt` |
| HP/XP/inventory logic | `game/reducers/CharacterReducer.kt` |
| NPC tracking | `game/reducers/NpcLogReducer.kt` |
| Quest/party logic | `game/reducers/QuestAndPartyReducer.kt` |
| Faction/lore updates | `game/reducers/WorldReducer.kt` |
| Combat transitions | `game/reducers/CombatReducer.kt` |
| Shop logic | `game/handlers/MerchantHandler.kt` |
| Rest + death saves | `game/handlers/RestHandler.kt` |
| Level-up flow | `game/handlers/ProgressionHandler.kt` |
| Main game screen | `ui/game/GameScreen.kt` |
| Chat rendering | `ui/game/ChatFeed.kt`, `MessageBubbles.kt`, `NarrationBlock.kt` |
| Debug HTTP server | `debug/DebugServer.kt` |
| Debug state endpoints | `debug/StateEndpoints.kt` |
| Debug commands | `debug/CommandEndpoints.kt` |
| Debug macros | `debug/MacroEndpoints.kt` |
| Test integration suite | `test/.../ApplyParsedIntegrationTest.kt` |
| Test state builders | `test/.../GameStateFixture.kt` |
