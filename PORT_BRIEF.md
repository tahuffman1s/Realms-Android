# CLAUDE CODE PROMPT — Android Port of "Realms of Fate"

## MISSION
Port the single-file HTML RPG game `realms_of_fate.html` to a native Android app using Kotlin + Jetpack Compose + Material 3 (Material You). The HTML file is the **single source of truth** — every feature, every UI element, every game mechanic must be replicated exactly. Read the entire HTML file before writing any code.

## CRITICAL RULES
1. **Read the HTML file first.** It's ~470KB. Read it in chunks. Do NOT start coding until you've cataloged every feature.
2. **Feature parity is non-negotiable.** If it exists in the HTML, it must exist in the Android app. No "we'll add that later."
3. **Use Material 3 / Material You** for all UI components. Dynamic color theming with amber/gold seed color.
4. **No Three.js / No 3D animations.** Replace dice rolling, campfire, combat intro, and level-up animations with Compose animations (Lottie or custom). The rest of the game MUST be identical.
5. **Multi-provider AI API system** must work identically — Gemini (free), DeepSeek (~free), Claude ($). Same API endpoints, same prompt engineering, same DS_PREFIX.

## ARCHITECTURE

```
app/
├── data/
│   ├── models/          # Character, WorldMap, WorldLore, Quest, NPC, etc.
│   ├── repository/      # SaveRepository (Room DB), SettingsRepository (DataStore)
│   └── api/             # GeminiApi, DeepSeekApi, ClaudeApi, ApiRouter
├── domain/
│   ├── generation/      # WorldMapGenerator, WorldLoreGenerator, BackstoryGenerator, WeatherSystem
│   ├── mechanics/       # CombatSystem, SpellSystem, TimeSystem, MoralityTracker, DiceRoller
│   └── parsing/         # ResponseParser (tags, choices, NPCs, quests, shops, effects)
├── ui/
│   ├── theme/           # Material You theme with gold/amber seed, dark mode
│   ├── screens/         # TitleScreen, CreateScreen, GameScreen, DeathScreen
│   ├── panels/          # InventoryPanel, StatsPanel, SpellsPanel, LorePanel, MapPanel, etc.
│   ├── components/      # MessageBubble, ChoiceChips, TopBar, BottomNav, BottomSheet, etc.
│   └── overlays/        # DiceOverlay, RestOverlay, CombatOverlay, LevelUpOverlay, ShopOverlay
└── viewmodel/           # GameViewModel (single source of game state)
```

## EVERY FEATURE TO PORT (organized by screen)

### 1. SETUP SCREEN (Provider Selection)
- Three provider buttons: Gemini (FREE), DeepSeek (~FREE), Claude ($3/$15M)
- API key input field with validation per provider
- Provider info text with pricing
- Background color picker (pure black default)
- Key saved to DataStore/SharedPreferences
- "Begin" button validates key format before proceeding

### 2. TITLE SCREEN
- App icon (⚔️), title "Realms of Fate", subtitle "D&D 5e RPG"
- **New Adventure** button (gold, primary)
- **⚔️ Continue Adventure** button (only if current save exists)
- **📂 Load Save** button → opens bottom sheet with all save slots (up to 10)
  - Each slot shows: name, level, race, class, turns, world name, date
  - Tap to load, 🗑 swipe/button to delete
- **Import File** button (load from JSON file)
- **⚰️ Graveyard** button → bottom sheet listing dead characters
  - Tap any → full death screen with scrollable life timeline
  - Remove from graveyard option
- Footer: "AI Game Master • 3D Dice • Dynamic World"

### 3. CHARACTER CREATION (6 steps with progress dots)
**Step 0 — Identity:** Name input + Gender (Male/Female/Non-binary) + Age (Young/Adult/Mature/Elder)
**Step 1 — Appearance:** 3D preview → replace with a 2D character card/illustration. Skin tone (9 colors), Hair color (8), Hair style (8), Build (5). All as tappable chips/circles.
**Step 2 — Race:** 11 races in a 2-column grid. Each shows name, desc, traits. Selected = gold container.
**Step 3 — Class:** 12 classes in a 2-column grid. Each shows emoji, name, desc. Selected = gold container.
**Step 4 — Stats:** Point buy system (27 points). 6 stats (STR/DEX/CON/INT/WIS/CHA) with +/- buttons. Shows modifier. Racial bonus +2/+1 selector. **"Recommended" button** that auto-fills based on selected class. Cost display (13-14 costs 2pts, 15 costs 3pts).
**Step 5 — Confirm:** Summary card showing name, appearance, race, class, all stats with modifiers, HP, AC. Two buttons at bottom.

### 4. GAME SCREEN — TOP BAR (2 tight rows)
**Row 1:** Character name (Cinzel font) | Level badge (gold container) | Combat indicator (⚔️ red, only in battle) | Time emoji (🌅🌆☀️🌙) | Weather icon (if not clear) | Party member icons
**Row 2:** HP text (e.g. "45/52") | HP bar (green>60%, gold>30%, red) | XP text (e.g. "120/400") | XP thin purple bar | Gold (💰45) | Location (📍Thornwick, truncated)

### 5. GAME SCREEN — MESSAGE FEED (scrollable, auto-scroll to turn start)
Message types with distinct styling:
- **DM messages:** Dark surface background, Crimson Text font, full markdown rendering:
  - **Bold**, *italic*, ***bold italic***, `code`, ~~strikethrough~~
  - ### Headers (Cinzel font, gold)
  - > Blockquotes (gold left border, italic, warm background — THIS IS CRITICAL for NPC dialogue)
  - - Bullet lists (gold diamond prefix)
  - --- Horizontal rules (gold tinted)
  - Emoji rendering throughout
- **Player messages:** Right-aligned, gold container background
- **System messages:** Centered, smaller, dimmer. Bold names via markdown.
- **Dice messages:** Shows d20 result with crit/fail styling
- **Turn marker ref** for auto-scroll to turn start when loading completes

### 6. GAME SCREEN — CHOICES
- Floating 📜 button (48px round FAB, right side) — opens bottom sheet with 4 choice buttons
- Tapping a choice sends it as player action
- Bottom sheet dismisses on backdrop tap

### 7. GAME SCREEN — INPUT BAR
- Text input field with placeholder "What do you do?"
- Send button (48px, gold)
- Supports slash commands: /help, /save, /map, /inv, /stats, /spells, /lore, /journal, /currency, /party, /quest, /rest, /shortrest, /download, /load

### 8. GAME SCREEN — ACTION BAR (above input)
- Horizontal scrollable row of small buttons (36px height):
  - ⚔️ Light Attack, 🗡️ Heavy Attack (opens target prompt)
  - Spell hotbar (up to 8 slots, filled from known spells)
  - Each spell shows icon, tapping opens target prompt or self-casts
- Spell slot indicators (diamonds: filled=blue, empty=dark)

### 9. GAME SCREEN — BOTTOM NAVIGATION BAR
5 tabs: 📜 Chat | 🎒 Items | 📊 Stats | 🗺️ Map | ⋯ More
- Chat = default game view
- Items = Inventory panel
- Stats = Character stats panel
- Map = World map
- More = Grid menu

### 10. GAME SCREEN — MORE MENU (bottom sheet grid)
Icons in a grid: 💾 Save | 📥 Download | 📂 Load File | 🏠 Main Menu | 🔮 Spells | 📜 Lore | 📓 Journal | 💱 Currency | 👥 Party | 📋 Quests | ⛺ Short Rest | 🏕️ Long Rest | ⚙️ Setup

### 11. PANELS (each replaces main content area, has close ✕ button)

**Inventory Panel:**
- 2-column equipped slots (Weapon / Armor)
- Selected item detail card with icon, name, rarity color, description
- Equip/Unequip/Use buttons
- 5-column backpack grid with item icons, rarity color bar at bottom, quantity badge
- Item icons determined by name keywords (sword→⚔️, potion→🧪, etc.)
- Rarity colors: common=#9ca3af, uncommon=#22c55e, rare=#3b82f6, epic=#a855f7, legendary=#ff8c00

**Stats Panel:**
- 6 ability scores in 3-column grid (icon, label, value, modifier)
- Stats row: AC, Prof bonus, XP, Morality (with tier icon+color), Gold, Level
- Conditions list
- Racial traits
- Dice roll breakdown UI (when CHECK tag parsed): d20 + ability mod + proficiency = total vs DC

**Spells Panel:**
- Spell slot indicators per level
- Spells grouped by level (Cantrips, 1st, 2nd, 3rd)
- 2-column grid, each spell: icon, name, school, level
- Selected spell detail: full description, damage, Cast Now / Add to Hotbar / Remove from Hotbar buttons
- Cantrips unlimited, leveled spells consume slots

**Lore Panel (5 tabs):**
- **📜 World:** World name, era, World Conditions (mutations with icons+descriptions), Active Conflicts, Powers at Play (faction chips)
- **⚔️ Factions:** Each faction card with: name, type, base location, Government section (type, ruler, trait, years, dynasty, succession, past rulers lineage), Economy section (level with 5-bar indicator, description, currency, exports, imports, tax), Population (size, mood with color), disposition, goal, member NPCs
- **👤 NPCs:** List with name, relationship colored badge, race/role, location. Selected → detail with: appearance section (physical traits parsed into tag chips: Tall, Scarred, Armored etc.), relationship, faction, estimated abilities, player assessment quote, memorable dialogue quotes with turn numbers
- **📖 History:** Chronicle with era sections (color-coded). Each era: decorative header with colored line, "Years X–Y". Events as timeline with colored dots, year, years ago, markdown-rendered text. Ends with "— PRESENT DAY —"
- **🗣️ Rumors:** Each rumor in a card with 🗣️ icon

**World Map (Google Maps Dark Mode style):**
- Full-screen SVG map with exact Google Maps night mode colors:
  - Base: #242f3e, Roads: #38414e / #2b3544, Water: #17263c
  - Parks/forest: #263c3f, Road labels: #9ca5b3
  - Location pins: Google red #EA4335 (teardrop SVG path with drop shadow)
  - Current location: amber #d59563 with pulse ring
  - Player marker: Google blue dot #4285F4 with white border, pulsing animation
- Pinch-to-zoom, pan, two-finger gestures
- Floating controls: back button, location pill (current location name), center button, zoom +/−, faction toggle
- Faction territories as blurred colored regions
- Road distance labels
- Bottom: gradient travel strip with scrollable destination chips (icon, name, distance)
- Undiscovered locations shown as small grey dots

**Character Log (NPC Journal):**
- Filter tabs: All, 🟢 Friendly, 🔴 Hostile, 🟣 Neutral
- Player character card at top
- NPC list with icon, name, relationship badge, race/role, appearance preview
- Selected NPC detail: large icon, name, race/role/age, personality quote, physical appearance section with trait chips, relationship + note, faction, estimated abilities, player assessment, dialogue history with turn numbers

**Quest Panel:**
- Filter tabs: 📜 Active, ✅ Done, ❌ Failed, All
- Quest list with title, status icon/color, type badge (main/side/bounty), description preview, objective count
- Selected quest: title, type, giver, full description in styled card, objectives with checkboxes, reward, location, start/complete turn, Abandon button for active quests

**Party Panel:**
- Player card at top with HP bar
- Companion list with icon, name, level, race/role, HP bar, personality quote
- Selected companion: large icon, name details, HP bar, appearance, abilities grid, faction, home location, joined turn, Dismiss button

**Currency Panel:**
- Total wealth estimate in gold equivalent
- Local currency indicator
- Per-currency cards with amount, faction, economy level (5-bar), exchange rate
- Exchange UI: amount input, conversion preview, exchange/cancel buttons
- Exchange rates table at bottom

**Shop Overlay:**
- Merchant name header
- Buy/Sell tabs
- Item list with names and prices (discounted prices if haggled)
- Haggle button (CHA check, one attempt per shop, up to 20% discount)
- Sold items available for buyback
- Player gold display

### 12. DEATH SCREEN (BitLife-style)
- "REST IN PEACE" header, character name (large, red), level/race/class, world name
- Stats row: turns, XP, gold, morality tier
- World mutations line
- **Scrollable "LIFE STORY" timeline:** Vertical line with color-coded dots:
  - 🟢 Green = party joins, born
  - 🟡 Gold = level ups
  - 🟣 Purple = quests
  - 🔵 Teal = travel/arrivals
  - 🔴 Red = world events, death
  - Each entry: turn number, text
- Cause of death in italics
- Companions section, unfinished backstory
- Two buttons: Main Menu | New Character

### 13. OVERLAYS / ANIMATIONS
- **Dice Roller:** Replace Three.js with a Compose animation — spinning d20 that lands on the result, then shows breakdown (d20 + mod + prof = total). Keep the same visual layout.
- **Campfire/Rest:** Replace Three.js with an animated illustration or Lottie. Show rest type, healing results, slot restoration.
- **Combat Start:** Replace Three.js swords with an animated "INITIATIVE" text with dramatic entrance. Red glow.
- **Level Up:** Full-screen overlay with level number, HP gain, new spells, ASI point allocation (if level 4/8/12/16/19).

### 14. GAME MECHANICS (copy exactly from HTML)

**World Generation:**
- `generateWorldMap()` — 11-14 locations on a 600x500 grid, 4-column layout with jitter. 12 location types (town, city, dungeon, forest, mountain, ruins, swamp, camp, temple, cave, port, desert) with 15-30 names each. Roads connecting 2-3 nearest neighbors. Rivers, lakes, terrain features. Local POIs per location type.
- `generateWorldLore()` — World name (4 patterns), era, 3-7 factions with full government (14 types, ruler, dynasty, succession, past rulers), economy (10 states, currency, exports/imports, tax), population. 6-11 NPCs with names from 110+ pool, 50 titles, 50+ roles, secrets, wealth. Historical events across eras (primordial→founding→dark→growth→recent). 25 rumors. Conflicts.

**18 Starting Scenarios:** Each with unique opening prompt, optional character modifier (gold, conditions, HP), scene hint. Random selection.

**Backstory Generator:** Dark secret (15), personal enemy (12 archetypes with name+faction+location), lost item (10), bond (10), flaw+trigger (12), prophecy (10 + null options for ~65% chance).

**16 World Mutations:** 2-3 per playthrough. Each with id, name, icon, description, AI prompt. Mutation-aware weather, events, and narration.

**19 Dynamic World Events:** Weighted random templates. Contextual triggers: travel (+25%), combat end (+15%), time change (+10%), quest complete (+20%). Base chance grows +4%/turn since last. Min 3-turn cooldown. Categories: faction, NPC, location, supernatural, economic, mutation-specific.

**Contextual Day/Night:** Action-based accumulator (travel=4, craft/wait=3, social/explore=2, combat=1, shop=1). Threshold=6 per phase. AI can force via [TIME:phase] tag.

**13 Weather Types:** Terrain-specific probability tables (12 terrains). 5 mutation modifiers. Night bonuses. Changes on: time advance, travel, long rest, game start.

**Morality (-100 to +100):** 7 tiers (Evil→Saintly). Dual detection: AI tags [MORAL:+/-N] + contextual keyword scan. Affects NPC reactions, available choices.

**Faction Reputation (-100 to +100 per faction):** 5 tiers. AI tags [REP:Faction|+/-N]. Affects prices, access, hostility.

**Racial Physical Traits:** RACE_PHYSICAL object for all 11 races — height, build, features, social reactions, voice, quirks. Sent to AI every turn.

**D&D 5e Mechanics:**
- Point buy (27 points), ability modifiers, proficiency bonus by level
- HP = class hit die + CON mod per level
- AC = 10 + DEX mod + armor
- XP thresholds: level² × 100
- Spell slots: full caster / half caster tables up to level 8
- 30 spells in SPELL_DB (cantrips, level 1-3) with class restrictions
- Skill checks: d20 + ability mod + proficiency vs DC
- Attack rolls, saving throws, damage parsing
- Conditions, item equip/unequip, consumable use
- Short rest (hit die healing) / Long rest (full heal, slots restore, conditions clear)

**Multi-Currency:** Per-faction currencies with exchange rates based on economic wealth. Local currency detection. Exchange UI.

**Save System:**
- Auto-save every turn (when loading goes true→false)
- Manual save via button/command
- Multi-slot (up to 10, keyed by character name)
- Save slot index in separate key
- Death → delete active save, move to graveyard (up to 20)
- Graveyard persists across characters
- Import/export JSON save files

### 15. AI API SYSTEM (CRITICAL — copy exactly)

**Gemini:** `generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`
- System instruction + contents array (user/model roles)
- maxOutputTokens: 800, temperature: 0.9

**DeepSeek:** `api.deepseek.com/v1/chat/completions`
- OpenAI-compatible format
- **DS_PREFIX** (11 sections!) prepended to system prompt:
  1. SCENE tag rules
  2. CHOICES format
  3. All game tags (DAMAGE, HEAL, XP, GOLD, GOLD_LOST, ITEM, CHECK, NPC_MET, QUEST_START, SHOP, TRAVEL, PARTY_JOIN, TIME, MORAL, REP)
  4. Backstory weaving rules
  5. World mutation + combination instructions
  6. Dynamic world events reaction rules
  7. NPC dialogue format (mandatory: *italic action* EMOJI **Name:** > "blockquote")
  8. Length/personality rules
  9. Racial identity rules (short/tall/hated race handling)
  10. Morality & reputation rules with tag values
  11. Weather description rules
- **Per-turn reminder** appended to last user message covering: SCENE, tags, dialogue format, racial physique, mutations+weather, moral/rep tags, word limit
- temp: 0.85, top_p: 0.9, max_tokens: 800

**Claude:** `api.anthropic.com/v1/messages`
- Anthropic format with system + messages
- anthropic-version header, dangerous-direct-browser-access header
- max_tokens: 600

**SYS Prompt:** ~4000 words covering BG3 narrator personality, dice rules, combat, day/night, weather, tags, NPC dialogue format, quest tags, shop tags, travel, skill checks, world lore weaving, world mutations, dynamic events, backstory weaving, racial identity, morality/reputation, markdown formatting with emoji guide.

**Response Parsing:** Extract from AI response:
- [SCENE:type|desc], [DAMAGE:N], [HEAL:N], [XP:N], [GOLD:N], [GOLD_LOST:N]
- [ITEM:name|desc|type|rarity], [REMOVE_ITEM:name]
- [CONDITION:name], [REMOVE_CONDITION:name]
- [CHECK:skill|ability|DC|PASS/FAIL|total]
- [NPC_MET:name|race|role|age|relationship|appearance|personality|thoughts]
- [PARTY_JOIN:name|race|role|level|maxhp|appearance|personality], [PARTY_LEAVE:name]
- [QUEST_START:title|type|desc|giver|objectives|reward], [QUEST_UPDATE], [QUEST_COMPLETE], [QUEST_FAIL]
- [SHOP:merchant|items:prices], [TRAVEL:location], [TIME:phase]
- [MORAL:+/-N], [REP:faction|+/-N]
- [CHOICES]1-4[/CHOICES] or fallback numbered list parsing
- Contextual damage/heal/XP/gold detection from prose (fallback when tags missing)
- NPC detection from bold names in dialogue format
- NPC dialogue extraction for journal

### 16. THEME / STYLING
- Material 3 dark theme
- Seed color: amber/gold (#E2B84A)
- Fonts: Cinzel (headers, titles, UI labels) + Crimson Text (body, narration)
- Color constants matching HTML:
  ```
  bg=#000000, surface=#1D1B20, surface2=#211F26, surface3=#2B2930
  gold=#E2B84A, goldDim=#B8941A, goldContainer=#3F2E00
  red=#F2B8B5, redContainer=#8C1D18
  green=#7DD892, greenContainer=#00522A
  blue=#A0C4FF, purple=#D0BCFF, purpleContainer=#4F378B
  cyan=#7DD8C0
  ```

## HOW TO EXECUTE
1. Read the entire `realms_of_fate.html` file in chunks
2. Create data models for all game entities
3. Port world generation (map, lore, backstory, weather, events, mutations)
4. Port AI API system with all three providers + DS_PREFIX + SYS prompt
5. Port response parser (all tags + fallback detection)
6. Port game mechanics (combat, spells, time, morality, currency)
7. Build UI screens and panels one at a time, comparing against HTML
8. Build overlays (dice, rest, combat, level up, shop)
9. Implement save/load system with Room DB
10. Test every feature against HTML version

## THE HTML FILE IS AT: `realms_of_fate.html`
Read it. It is the law.
