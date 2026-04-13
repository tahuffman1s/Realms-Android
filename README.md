# Realms of Fate — Android (Material You)

A native Kotlin / Jetpack Compose port of the [Realms of Fate](../Realms) web game. AI-narrated D&D 5e sandbox RPG with three model backends (Gemini, DeepSeek, Claude), a procedurally-generated world, factions, quests, morality, reputation, and a Google-Maps-style world map.

## What's inside

**Tech stack**
- Kotlin 2.0 + Jetpack Compose (Material 3, with **Material You dynamic color** on Android 12+)
- Single `ComponentActivity`, state held in `GameViewModel` (StateFlow)
- `DataStore` for prefs + API key
- JSON file save slots via `kotlinx.serialization`
- `OkHttp` for all three AI providers
- Compose `Canvas` for the world map (no external mapping library)
- **Min SDK 26 (Android 8), target SDK 34 (Android 14)**

**Preserved from the web version**
- All three AI providers (Gemini, DeepSeek, Claude) with the **same optimized DeepSeek prompt + sampling** (temperature 0.9, top\_p 0.95, frequency\_penalty 0.3, presence\_penalty 0.1, max\_tokens 900, cache-stable system prefix)
- Full tag vocabulary: `[SCENE:…]`, `[DAMAGE:N]`, `[HEAL:N]`, `[XP:N]`, `[GOLD:N]`, `[ITEM:…]`, `[CHECK:…]`, `[NPC_MET:…]`, `[QUEST_*]`, `[SHOP:…]`, `[TRAVEL:…]`, `[PARTY_JOIN:…]`, `[TIME:…]`, `[MORAL:…]`, `[REP:…]`, `[CHOICES]…[/CHOICES]`
- Procedural world gen with locations / roads / terrain / rivers / lakes
- Lore gen with factions (name, type, ruler, currency, baseloc), NPCs, primordial events, world mutations
- Backstory generation (origin, motivation, flaw, bond, dark secret, personal enemy, lost item, prophecy)
- World-event system (faction mobilization, assassinations, festivals, fires, storms — 7 templates)
- Morality (-100..100), faction reputation, XP & level thresholds
- NPC log / journal, party management, quests with objectives, merchant stocks
- Spells, spell slots, spell hotbar
- Dice-backed ability checks with crit detection
- All side panels (inventory, quests, party, lore, journal, currency, spells, stats) as Material 3 bottom sheets
- Google-Maps-style world map: pan/pinch/zoom, faction-territory toggle, style switch (default ↔ terrain), center-on-player, scale bar, compass, Google teardrop pins, label halos, two-tier roads (minor white + yellow highway), blue "you are here" dot

**Deliberately removed (per the brief)**
- **Three.js**: gone. Dice rolls are a pure Compose animation. No 3D anywhere.
- **Appearance creator** + **3D character model in the top bar**: replaced by a single monogram tile derived from the character name.

## Building

### Requirements
- **Android Studio Koala or newer** (AGP 8.5, Kotlin 2.0)
- Android SDK 34 installed
- JDK 17 bundled with Android Studio is fine

### First-time setup

```bash
cd RealmsAndroid

# Tell Gradle where your Android SDK lives (Android Studio usually creates this automatically)
cp local.properties.sample local.properties
# Edit local.properties and set sdk.dir=/path/to/Android/Sdk

# If gradle-wrapper.jar is missing, either:
#   a) open the project in Android Studio once (it downloads the wrapper), OR
#   b) run `gradle wrapper` if you have a standalone Gradle 8.x on PATH
```

### Build a debug APK

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### Install on a device

```bash
./gradlew installDebug         # USB-connected or emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release build

```bash
./gradlew assembleRelease      # needs signing config
```

## First run

1. Launch the app.
2. Pick a provider (Gemini is free; DeepSeek is nearly free; Claude is best quality).
3. Paste your API key — it stays on-device via `DataStore`.
4. Create a character (race + class + abilities).
5. The narrator takes over.

## Project structure

```
app/src/main/kotlin/com/realmsoffate/game/
├── MainActivity.kt               # single activity, Compose root
├── RealmsApp.kt                  # Application
├── data/
│   ├── Models.kt                 # Character, WorldMap, Quest, Item, etc.
│   ├── TagParser.kt              # Parses [TAG:value] from AI output
│   ├── AiProvider.kt             # Gemini / DeepSeek / Claude enum
│   ├── AiRepository.kt           # OkHttp calls, DeepSeek tuned
│   ├── Prompts.kt                # SYS + DS_PREFIX
│   ├── PreferencesStore.kt       # DataStore prefs
│   └── SaveStore.kt              # JSON save slots
├── game/
│   ├── GameViewModel.kt          # State + turn pipeline
│   ├── WorldGen.kt               # Procedural world generator
│   ├── LoreGen.kt                # Factions, NPCs, mutations
│   ├── BackstoryGen.kt           # Player backstory
│   ├── WorldEvents.kt            # Dynamic world events
│   ├── Races.kt                  # Race definitions + physique templates
│   ├── Classes.kt                # Class definitions + starting gear
│   ├── Spells.kt                 # Spells + slots
│   └── Dice.kt                   # d20 + dice formulas
├── ui/
│   ├── theme/                    # Material You theme + typography
│   ├── setup/                    # ApiSetup + CharacterCreation
│   ├── game/                     # GameScreen + top/bottom bars
│   ├── map/                      # Compose-Canvas world map
│   ├── panels/                   # Inventory/Quests/Party/… bottom sheets
│   └── dice/                     # d20 roll dialog (Compose animations)
└── util/
    └── Markdown.kt               # Narration inline markdown renderer
```

## DeepSeek tuning notes

The DeepSeek path uses the same optimizations carried over from the web version:
- **Cache-stable system prefix**: `DS_PREFIX + SYS` is built once per call from `const` sources. Dynamic per-turn state (character sheet, world events, quest list, inventory) goes in the **user** message so DeepSeek's prompt cache can hit the full prefix.
- **Sampling**: `temperature=0.9`, `top_p=0.95`, `frequency_penalty=0.3`, `presence_penalty=0.1` — matches DeepSeek's documented creative-writing sweet spot while preserving tag strictness.
- **Reminder shim**: the last user message gets a short `[REMINDER: …]` trailer so DS does not forget tag structure late in long conversations.
- `max_tokens=900` to avoid truncated `[CHOICES]` blocks on long scenes.

## Android-specific optimizations

- **Material You**: `dynamicDarkColorScheme` / `dynamicLightColorScheme` on Android 12+; graceful brand-palette fallback on 8–11.
- **Edge-to-edge** with `enableEdgeToEdge()`, transparent system bars, proper insets handling on all panels.
- **Adaptive launcher icon** with monochrome variant (themed icons on Android 13+).
- **Splash screen** via `core-splashscreen`.
- **R8 minification + resource shrinking** enabled for release builds.
- **Configuration changes** (rotation, dark mode toggle) preserved via ViewModel.
- **Predictive back gesture** support is automatic (single-activity Compose with stable root).
- **Data extraction rules** exclude the API key from cloud backup; saves are included.
- **Text input** uses `adjustResize` so narration scrolls behind the keyboard, not under it.
- **Save game persistence** via app-private `files/saves/*.json`.

## License

Same as the parent project.
