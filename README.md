# Realms of Fate

A native Android RPG where every adventure is narrated by an AI dungeon master. Built with Kotlin, Jetpack Compose, and D&D 5e mechanics. Each playthrough generates a unique world with factions, NPCs, quests, morality, and consequences that ripple across sessions.

Ported from a single-file HTML original with full feature parity.

## How it works

You type what your character does. The AI narrates what happens next, rolling dice, tracking NPCs, advancing quests, and mutating the world behind the scenes. A structured tag protocol (`[DAMAGE:N]`, `[QUEST_START:...]`, `[NPC_MET:...]`, etc.) bridges the AI's prose with real game mechanics — HP, inventory, faction reputation, and 20+ other state variables update automatically each turn.

The narrator is modeled after the Baldur's Gate 3 narrator: sardonic, omniscient, and willing to let you fail spectacularly. Narrator asides ("Well, that was genuinely painful to watch") appear as distinct elements between prose blocks.

## Tech stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin 2.2 |
| UI | Jetpack Compose, Material 3 with Material You dynamic color |
| Architecture | Single `ComponentActivity`, `GameViewModel` (StateFlow), pure reducers |
| AI | DeepSeek V3 via OkHttp (Gemini + Claude supported but dormant) |
| Persistence | DataStore (prefs), JSON save slots via kotlinx.serialization |
| Map | Compose `Canvas` — no external mapping library |
| Target | SDK 34 (Android 14), min SDK 26 (Android 8) |

## Game systems

**World generation** — Seeded procedural worlds with 11-15 locations, roads, rivers, terrain, and local points of interest. Deterministic from seed so saves reload identically.

**Factions & lore** — 8-12 factions per world with government types, economies, currencies, rulers, and dynasty history. 300+ NPC name pool. Historical timeline spanning primordial through present era.

**World mutations** — 2-3 per playthrough from a pool of 16 (The Dead Walk, Eternal Winter, Fey Crossing, Dragon Tyranny, etc.). Each injects a narrator prompt that colors every scene.

**D&D 5e mechanics** — 11 races, 12 classes, point-buy abilities, proficiency scaling, spell slots, 34-entry spell database, skill checks (d20 + mod + prof vs DC), death saves, short/long rest.

**Morality & reputation** — Morality tracks -100 to +100 across 7 tiers. Per-faction reputation affects prices, NPC reactions, and available choices.

**Multi-currency economy** — Each faction mints its own currency. Exchange rates reflect economic wealth. Merchants accept local currency or gold.

**Dynamic events** — Weighted random world events (faction mobilizations, assassinations, festivals, storms) trigger based on turns elapsed and player context.

**NPC tracking** — Stable slug IDs persist across 100+ turns. Dialogue history (last 5 lines per NPC), memorable AI-curated quotes, relationship tracking, location awareness.

## Prompt engineering

The AI integration is the core of the game. Key design decisions:

- **Prompt caching** — The system prefix (DS_PREFIX + SYS + world palette) is stable across turns. Dynamic state goes in the user message so DeepSeek's cache hits the full prefix, saving 70%+ of tokens on subsequent turns.
- **Structured output** — A `[METADATA]{JSON}` block carries all mechanical state (damage, XP, items, NPC updates, quest changes). Regex fallback exists as a safety net but hasn't fired in production.
- **Skill classification** — Freeform player actions get a lightweight pre-call: "What D&D 5e skill fits this?" Returns a skill name for the d20 check, or null if no check needed.
- **Per-turn reminder** — A short trailer appended to the last user message reinforces tag structure, narrative voice, and mechanical rules late in long conversations.

## Building

**Requirements:** Gradle 9.4+, AGP 9.0, Kotlin 2.2, Android SDK 34, JDK 17+.

```bash
# First time — set your SDK path
cp local.properties.sample local.properties
# Edit local.properties: sdk.dir=/path/to/Android/Sdk

# Debug APK
gradle assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Release APK (needs signing config)
gradle assembleRelease

# Lint
gradle lint

# Tests
gradle test
```

## Cursor / AI agents

Detailed workflow, deploy checks, and testing policies live in **`.cursor/rules/`** (`*.mdc`). **`CLAUDE.md`** is a short index to those rules.

## First run

1. Launch the app.
2. Pick a provider and paste your API key (stored on-device via DataStore).
3. Create a character — race, class, abilities, appearance.
4. The narrator takes over.

## Project structure

```
app/src/main/kotlin/com/realmsoffate/game/
├── MainActivity.kt                  Single activity, Compose root
├── data/
│   ├── Models.kt                    Character, WorldMap, Quest, Item, NPC, SaveData
│   ├── TagParser.kt                 [METADATA] JSON + legacy regex tag extraction
│   ├── AiRepository.kt             OkHttp client, DeepSeek prompt caching
│   ├── Prompts.kt                   SYS + DS_PREFIX + per-turn reminder
│   ├── PreferencesStore.kt          DataStore prefs
│   └── SaveStore.kt                 JSON save slots
├── game/
│   ├── GameViewModel.kt             State + turn pipeline (1741 lines)
│   ├── WorldGen.kt                  Seeded procedural world generator
│   ├── LoreGen.kt                   Factions, NPCs, history, rumors
│   ├── BackstoryGen.kt              Player backstory (secret, enemy, prophecy)
│   ├── Scenarios.kt                 18 opening scene templates
│   ├── Mutations.kt                 16 world mutations with narrator prompts
│   ├── WorldEvents.kt               Dynamic event triggers
│   ├── Races.kt                     11 races with physique text
│   ├── Classes.kt                   12 classes with starting gear
│   ├── Spells.kt                    34-entry spell/ability database
│   ├── Feats.kt                     Level-up feat selection
│   ├── Dice.kt                      d20 + NdM±K formula parser
│   └── reducers/
│       ├── CharacterReducer.kt      HP, XP, inventory, conditions
│       ├── CombatReducer.kt         Enemy HP, initiative, rounds
│       ├── NpcLogReducer.kt         NPC meet/update/death/dialogue
│       ├── QuestAndPartyReducer.kt  Quest lifecycle, party joins/leaves
│       └── WorldReducer.kt          Faction updates, travel, events
├── ui/
│   ├── theme/                       Material You + Cinzel/Crimson fonts
│   ├── setup/                       API setup, title, character creation, death
│   ├── game/                        Main game screen + top/bottom bars
│   ├── map/                         Compose Canvas world map
│   ├── panels/                      Inventory, quests, party, lore, stats, etc.
│   ├── overlays/                    Shop, target prompt, rest, level-up
│   └── dice/                        d20 roll animation
└── util/
    └── Markdown.kt                  Narrator prose markdown renderer
```

## Testing

Integration tests cover the per-turn state-mutation pipeline via Robolectric:

- `ApplyParsedIntegrationTest` — 20 tests across all reducer domains
- `GameStateFixture` + `ParsedReplyBuilder` — test harness for constructing game state and AI responses

Run with `gradle test`.

## Roadmap

See [ROADMAP.md](ROADMAP.md) for shipped phases, pending work, and strategic concerns. Key threads:

- **AI Reliability** (Phases 1-4 shipped) — prompt caching, stable NPC IDs, JSON metadata, few-shot polish
- **Parser** (Phases A-B shipped) — tokenizer + stack parser replacing regex
- **GameViewModel Refactor** (Phases I-III shipped) — reducer extraction, handler extraction, `applyParsed` down from 528 to ~140 lines, GameViewModel from 2131 to 1389 lines

## License

Same as the parent project.
