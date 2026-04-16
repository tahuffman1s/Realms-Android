# Realms of Fate — Roadmap

Phases that have shipped, phases queued, and phases deliberately deferred or
declined. Updated at each commit. Read top-to-bottom for full context, or skim
the **Status** column.

## Index

- [AI Reliability](#ai-reliability) — Phases 1–5 (DeepSeek output stability)
- [Parser](#parser) — Phases A–E (tokenizer + JSON metadata refinements)
- [GameViewModel Refactor](#gameviewmodel-refactor) — Phases I–IV (god-method extraction)
- [Bug Fixes](#bug-fixes) — critical gameplay and data-integrity bugs
- [Build System](#build-system) — Gradle, SDK, versioning, release pipeline
- [Tactical Backlog](#tactical-backlog) — small fixes flagged during playtests
- [UI/UX](#uiux) — bugs, UX problems, design system, accessibility
- [Gameplay Rework](#gameplay-rework) — mechanically incomplete systems
- [Strategic Concerns](#strategic-concerns) — long-horizon items not yet phases

---

## AI Reliability

| Phase | What | Status |
|---|---|---|
| 1 | Prompt caching restructure (stable session prefix, slim per-turn user, history window 40) | ✅ Shipped |
| 2 | Stable NPC/faction slug IDs with save migration + roster injection | ✅ Shipped |
| 3 | `[METADATA]` JSON block for mechanical state, regex fallback as safety net | ✅ Shipped |
| 4 | Few-shot prompt polish (~22% word reduction, BAD/GOOD example pairs) | ✅ Shipped |
| 5 | Narrative + extractor call split | 🟡 Deferred indefinitely |

### Phase 5 gating

Don't pursue unless `SOURCE:JSON` drops below ~95% over a meaningful window
(say 20+ varied sessions). Current observed compliance: **100%** on every dump
analyzed. The infrastructure to monitor this exists (`SOURCE:` line in debug
dumps + `pulldebug` workflow). Watch the ratio over time; act on data, not
speculation.

If compliance does drift and Phase 5 becomes warranted: split each turn into
(a) a narrative-only call producing tagged prose, then (b) a short extractor
call taking the narrative + action + dice and emitting strict `TurnMetadata`
JSON. DeepSeek pricing absorbs the second call cheaply.

---

## Parser

| Phase | What | Status |
|---|---|---|
| A | Tag-fragment post-strip hotfix on body cleaners | ✅ Shipped |
| B | Tokenizer + stack parser, type-safe tokens, 12 unit tests, eliminates Bug #1 by construction | ✅ Shipped |
| C | NPC display-name substitution in `parsed.narration` body | ✅ Shipped |
| D | Retire regex mechanical-tag fallback (delete the `else` branch in `parse()` + `prose*` fallbacks) | 🟡 Gated on data soak |
| E | Split `ParsedReply` into `NarrativeContent` + `TurnMechanics` | 🟢 Optional, deferred |

### Phase C — substitute NPC display names in narration body ✅

Shipped as a post-parse pass in `applyParsed` (GameViewModel). After
`TagParser.parse()` returns, NPC slug IDs in `parsed.narration` are replaced
with display names from `state.npcLog` before constructing
`DisplayMessage.Narration`. Parser stays stateless — name resolution is a
ViewModel concern. 2 new tests in `ApplyParsedIntegrationTest`.

### Phase D — retire regex mechanical-tag fallback

The `else { for (m in tagPattern.findAll(raw)) { when (type) { "DAMAGE" -> ... } } }`
branch in `TagParser.parse()` (~150 lines) and the `prose*` fallback regexes
(`proseDamage`, `proseHeal`, etc.) are dead weight that runs every turn as a
safety net. Phase 3's JSON metadata path is hitting `SOURCE:JSON` at 100%.

**Gating rule:** delete only after **≥20 distinct sessions** across multiple
characters/scenarios all show `SOURCE:REGEX_FALLBACK` at 0%. This is
behavioral evidence, not a date.

When the gate clears:
- Delete the `else` branch + `tagPattern` regex + per-tag `when (type)` logic.
- Delete `proseDamage`, `proseHeal`, `proseGoldGain`, `proseGoldLost`,
  `proseXp`, `dialoguePattern`, `dialogueFallback`.
- Delete legacy `SNARK` pattern handling.
- On `metadata.decodeFromString` failure, emit an explicit `ParseError` field
  on `ParsedReply` so the UI can surface "AI emitted malformed turn — reroll?"
  rather than silently dropping state. Much safer than today's regex-fallback
  silent drift.
- `ParseSource` enum becomes `{ JSON, PARSE_ERROR }`.

### Phase E — `ParsedReply` split

`ParsedReply` is a ~30-field grab-bag. Cleaner shape: `NarrativeContent +
TurnMechanics`. Cosmetic, not a bug source. **Don't pursue proactively.**
Amortize the refactor against the next time `applyParsed` (or whatever it
becomes after Phase II) needs material changes for another feature.

---

## GameViewModel Refactor

| Phase | What | Status |
|---|---|---|
| I | Robolectric + GameStateFixture + 8 integration tests covering every applyParsed domain | ✅ Shipped |
| II.1 | `CharacterReducer` (HP/XP/gold/inventory/conditions/level-up) | ✅ Shipped |
| II.2 | `NpcLogReducer` (merge / auto-register / dialog / quotes / deaths / updates) | ✅ Shipped |
| II.3 | `WorldReducer` (faction updates / leader cascade / lore entries) | ✅ Shipped |
| II.4 | `QuestReducer` + `PartyReducer` | ✅ Shipped |
| II.5 | `CombatReducer` (extracted from `submitAction` tail) | ✅ Shipped |
| III | Extract VM-level domain handlers (Merchant / Rest / Save / Progression) | ✅ Shipped |
| IV | Split `GameViewModel` into multiple ViewModels | 🔴 Don't do |

**Result:** `GameViewModel.kt` 2131 → 1349 lines (−36.7%). `applyParsed`
is ~140 lines (down from 528). 5 reducers totaling 817 lines, each pure with
typed results. 4 handlers totaling 484 lines. 54 tests across 6 test classes,
all green.

### Phase III — extract VM-level domain handlers ✅

Shipped in `7629c37`. Four handlers extracted:

- [x] **III.1. `MerchantHandler`** (130 lines, 9 tests) — `openShop`,
  `buyItem`, `sellItem`, `buybackItem`, `exchange`, `haggle`.
- [x] **III.2. `RestHandler`** (115 lines, 7 tests) — `shortRest`, `longRest`,
  `rollDeathSave`, `die`.
- [x] **III.3. `SaveService`** (182 lines, 5 tests) — `snapshotSaveData`,
  `loadSlot`, `importSave`, `exportCurrentJson`, `exportFilename`,
  `debugDumpFilename`, `refreshSlots`, `deleteSlot`, `exhumeGrave`.
- [x] **III.4. `ProgressionHandler`** (57 lines, 7 tests) — `assignStatPoint`,
  `selectFeat`, `dismissLevelUp`, `dismissFeat`.

VM method signatures unchanged — one-liner delegates. Compose call sites
untouched.

### Phase IV — DON'T

Splitting `GameViewModel` into separate ViewModels (`GameOrchestratorVM` +
`CharacterActionsVM` + `MerchantActionsVM` + `MetaVM`) is tempting in theory
but:

- Changes every Compose call site (`vm.shortRest` → `charVm.shortRest`).
- Multiplies state-flow collection at the UI layer.
- Doesn't materially improve testability vs. Phase II/III reducers/handlers
  already being pure functions.

**Reconsider only if** `GameViewModel.kt` creeps back up past ~1500 lines
(currently 1349 after Phase III + dead code cleanup), OR a second VM consumer
appears (companion app, etc.).

---

## Bug Fixes

Critical bugs found via codebase audit and fixed:

| Bug | Fix | Status |
|-----|-----|--------|
| Handler shared-state mutation — `MerchantHandler`, `RestHandler`, `ProgressionHandler` mutated live `Character` object directly before `copy()` | Added `Character.deepCopy()` that copies all 7 mutable collections; all handler methods deep-copy before mutating | ✅ Fixed |
| Level-up overwrites damage — `CharacterReducer` set `hp = maxHp` on level-up, discarding any damage dealt in the same turn | Changed to additive: `hp = (hp + hpGain).coerceAtMost(maxHp)` | ✅ Fixed |
| Spell slots cap at level 8 — `SpellSlots.slotsForLevel` coerced level to 1–8; levels 9–20 got level-8 slot counts | Extended `FULL_CASTER` and `HALF_CASTER` tables to level 20 with D&D 5e progression | ✅ Fixed |
| Dead code — 322 lines of unreachable code (`DiceRollerDialog`, `CheckDisplay`, `inferAbilityFromAction`, unused data classes, vestigial provider scaffolding) | Deleted all dead code across 7 files | ✅ Cleaned |

### Remaining known issues (moderate/low severity)

- **Bookmarks not persisted** — `bookmarks` list is never saved to `SaveData`.
  All bookmarks lost on reload.
- **Graveyard uses location name, not world name** — death screen shows a
  location name (e.g. "Thornhaven") instead of `worldLore.worldName`.
- **Travel has no pathfinding** — can only travel to directly adjacent
  locations. Tapping a distant location silently does nothing.
- **Character appearance not in AI prompts** — appearance data (skin, hair,
  build, gender, age) is collected but never injected into the narrator prompt.
- **Morality not displayed in-game** — tracked and saved but no HUD indicator.
- **Feat "Lucky" has no mechanical effect** — empty apply lambda.
- **`worldEventHook` and `timeOfDay` on ParsedReply** — parsed but never
  consumed by any reducer or VM code.

---

## Build System

| Change | Status |
|--------|--------|
| Migrate to system Gradle 9.4 (pacman), remove wrapper | ✅ Shipped |
| Upgrade AGP 8.5.2 → 9.0.1, Kotlin 2.0.20 → 2.2.10 | ✅ Shipped |
| All SDK components via pacman/AUR, no sdkmanager | ✅ Shipped |
| Semver versioning (0.1.0), computed versionCode | ✅ Shipped |
| Tag-driven GitHub Actions release pipeline | ✅ Shipped |
| APK signing (local keystore.properties + CI GitHub Secrets) | ✅ Shipped |
| APK renamed to `realms-of-fate-vX.Y.Z-release.apk` in CI | ✅ Shipped |

---

## Tactical Backlog

Small bugs/quirks surfaced during playtests that don't warrant a phase:

- ~~**Quest objectives accumulate near-duplicates**~~ — ✅ Fixed. Substring
  containment dedup in `QuestAndPartyReducer`: if the new objective contains
  an existing one (or vice versa), treats as a match. Longer text replaces
  shorter.
- ~~**`availableMerchants` accumulates forever**~~ — ✅ Fixed. Merchant list
  clears on scene change. New `[MERCHANT_AVAILABLE:]` tags in the same turn
  repopulate.
- ~~**AI uses "Unnamed X" as initial NPC display name**~~ — ✅ Fixed. Added
  BAD/GOOD prompt example reinforcing real-name slugs over generic descriptors.
- ~~**Travel progress is dice-dependent**~~ — ✅ Fixed. Pinned to flat 3
  leagues/turn.
- **`logTimeline` is still a VM side-effect, not a reducer output** — every
  reducer returns timeline entries which the VM drains. The VM's own
  `logTimeline()` calls (used in `submitAction`, save load, etc.) still write
  directly to the timeline list. Could move to a `TimelineService` for
  consistency. Low priority.
- ~~**`parsed.narration` orphan subjects**~~ — ✅ Fixed by Parser Phase C.

---

## UI Rework

Full UI overhaul to make the app feel like a polished native Android app.
Chat feels like iMessage/Google Messages with Material You theming. All
audited bugs, UX problems, and accessibility gaps are addressed within the
phases. Full spec: `docs/superpowers/specs/2026-04-16-ui-rework-design.md`.

Ship in order: Phase 1 → 2 → 3 → 4, then 5 and 6 in any order.

| Phase | What | Status | Fixes |
|-------|------|--------|-------|
| 1 | **Design System** — semantic color tokens, typography cleanup, markdown renderer, activity theme | 🔴 Next | Hardcoded colors, fake SemiBold, light flash, system font a11y |
| 2 | **Chat Experience** — split GameScreen.kt (3.3k lines), document-order segments, collapsible narration, input UX, empty state, error handling | 🔴 Blocked on 1 | Narration behind tap, segment reorder, focus bug, swipe guard, a11y touch targets |
| 3 | **Game Chrome** — two-row collapsing top bar, 4-tab bottom nav (Chat/Map/Character/Journal), contextual FAB | 🔴 Blocked on 2 | Tab.More re-entrancy, 110dp location cap, choices badge, combat chip colors |
| 4 | **Panels** — split Panels.kt (2.4k lines) into CharacterTab + JournalTab + standalone sheets | 🔴 Blocked on 3 | Lore tab dismiss conflict, NPC card animation, equip toggle, sell qty, party height |
| 5 | **Setup Flow** — ApiSetup, Title, CharacterCreation, Death screen polish | 🟢 After 3 | Save delete confirm, API key feedback, dead provider picker, step extraction |
| 6 | **Map + Combat** — map fixes, combat HUD polish | 🟢 After 3 | Scale bar zoom, banner overlap, unreachable dialog, initiative chip overflow |

### Phase 1 — Design System

- [ ] Replace ~20 hardcoded `Color(0xFF...)` with `RealmsTheme.colors` /
  `MaterialTheme.colorScheme` tokens
- [ ] Add bubble palette tokens to `Extended.kt`: `narratorBubble`,
  `npcBubble`, `playerBubble`, `asideBubble`, `systemBubble` (+ on-colors)
- [ ] Remove fake `SemiBold` from `CrimsonTextFontFamily`
- [ ] Differentiate `titleLarge` (18sp) / `headlineSmall` (20sp)
- [ ] Wire `LocalFontScale` to Android system font accessibility
- [ ] Fix `parseInline` bold color and code span colors to use theme tokens
- [ ] Fix unclosed `**bold**` fallthrough in markdown parser
- [ ] Change `themes.xml` parent to `Material3.DarkNoActionBar`

### Phase 2 — Chat Experience

- [ ] Split `GameScreen.kt` → `ChatFeed.kt`, `MessageBubbles.kt`,
  `NarrationBlock.kt`, `ChatInput.kt`, `GameScreen.kt` (scaffold only)
- [ ] Render segments in document order (fix prose merging/reorder)
- [ ] Bubble styles: left narrator, centered aside pill, left NPC with name
  chip, right player, left system
- [ ] Collapsible narration: latest turn full, older turns auto-collapse
- [ ] Delete `ProseDetailDialog`
- [ ] Input: `enabled` tied to `isGenerating`, placeholder swap, `maxLines = 5`
- [ ] Fix `onNpcReply` to `requestFocus()` not `clearFocus()`
- [ ] Fix legacy swipe-attack `isLatestTurn` guard
- [ ] Empty state card when `messages.isEmpty()`
- [ ] Error card: countdown ring, X dismiss, no silent auto-dismiss
- [ ] Scroll-down indicator chip
- [ ] Touch targets minimum 48dp (bookmarks, swipe a11y)

### Phase 3 — Game Chrome

- [ ] Two-row collapsing top bar: row 1 pinned (name + HP + gear),
  row 2 collapses (location + turn + gold)
- [ ] 4-tab bottom nav: Chat, Map, Character, Journal
- [ ] Contextual FAB: choices (badge), combat (sword), rest (campfire),
  hidden when no action
- [ ] Settings gear icon → settings bottom sheet
- [ ] Currency/exchange under Character > Stats

### Phase 4 — Panels

- [ ] Split `Panels.kt` → `CharacterTab.kt` (`HorizontalPager`: Stats,
  Inventory, Spells) + `JournalTab.kt` (`HorizontalPager`: Quests, NPCs,
  Lore, Party) + `SettingsSheet.kt` + `ChoicesSheet.kt`
- [ ] Extract page composables: `StatsPage`, `InventoryPage`, `SpellsPage`,
  `QuestsPage`, `NpcJournalPage`, `LorePage`, `PartyPage`
- [ ] Lore: `FilterChip` pills instead of `ScrollableTabRow`
- [ ] NPC detail: `AnimatedVisibility(expandVertically())`
- [ ] Inventory: equip reads latest VM state
- [ ] Party: `heightIn(max)` constraint
- [ ] Quest: `.entries` not `.values()`
- [ ] Shop: sell price × quantity

### Phase 5 — Setup Flow

- [ ] ApiSetup: strip provider picker, key format hint, confirm-only save
- [ ] Title: Material You cards, swipe-to-delete with undo Snackbar, hide
  empty Load tile, collapsible graveyard
- [ ] CharacterCreation: `LinearProgressIndicator`, step extraction, single
  back button, clearer point-buy
- [ ] Death: fix `moralityColor` dead code, loading placeholder

### Phase 6 — Map + Combat

- [ ] Map: scale bar updates with zoom, banner/pills no overlap, unreachable
  location toast, empty state, Material You dialog surfaces
- [ ] Combat: initiative chip `maxLines = 1` + ellipsis, player chip gold
  border, enemy chips error container

---

## Gameplay Rework

Systems that are tracked/displayed but mechanically incomplete:

- [ ] **Spell slots never decrement on cast** — slots tracked, displayed,
  restored on rest, but casting doesn't consume them. Spellcasters have
  infinite resources. `Spells.kt`, `GameViewModel.kt`.
- [ ] **Racial bonuses ignored** — `RaceDef.applyTo()` exists with per-race
  stat bonuses but is never called. All races give a free +2/+1 to any stats.
  `Races.kt`, `CharacterCreationScreen.kt`.
- [ ] **Travel is adjacent-only, no pathfinding** — `startTravel()` requires a
  direct road. No multi-hop routing. `GameViewModel.kt:427`.
- [ ] **Morality not displayed in-game** — tracked, saved, passed to AI, but
  no HUD indicator or panel entry. Players can't see their alignment.
- [ ] **Character appearance not in AI prompts** — collected at creation but
  never injected into the narrator prompt. `Prompts.kt`.
- [ ] **Feat secondary effects unimplemented** — Alert (+5 initiative), Lucky
  (reroll), Sharpshooter (ignore cover), GWM (+10 crit damage) are flavor
  text only. `Feats.kt`.
- [ ] **World event effects are prompt-only** — events describe price changes,
  quest unlocks, etc. but no client-side state enforces them.
  `WorldEvents.kt`.

---

## Strategic Concerns

Long-horizon items that aren't phases yet because the right time to address
them isn't obvious:

### Context window will eventually bite

At ~18k tokens/turn at turn 6 with ~1k growth per turn, DeepSeek V3's 64k
ceiling lands around turn 50. The 40-message history window helps but doesn't
hard-cap.

**Likely intervention:** summarize older turns into a stable "session memory"
appended to the cached system prompt. Each summary collapses 5–10 turns into a
paragraph. Implementation cost: medium (need to design the summarization
prompt + extraction). Don't pre-build — wait until a real player session hits
the wall, then size the fix to actual usage patterns.

### No true integration test coverage outside `applyParsed`

The 8 integration tests cover the per-turn state-mutation path. Phase III
added 28 handler-level tests (merchant, rest, save, progression). Phase C,
tactical backlog, and bug fix work added 6 more integration tests, bringing
the total to 54. Still not covered:
- `submitAction` end-to-end (with mocked `AiRepository`)
- Save/load round-trip
- Travel state lifecycle

**Likely intervention:** add end-to-end tests when `submitAction` or the
save pipeline next gets material changes. The handler-level tests from
Phase III cover the domain logic; the gap is now orchestration, not logic.

### Save format discipline is soft

`SaveData.version = 2` is hardcoded. New fields rely on `ignoreUnknownKeys =
true` for forward-compat and default values for backward-compat. Works today.
When a breaking shape change happens (collapsing two fields into one, or
restructuring `WorldLore`), there's no migration framework — you'll invent
one ad-hoc.

**Likely intervention:** when the next breaking change is needed, write a
small `SaveMigrator` with versioned step functions (v2 → v3 → v4). Apply
during `fromJson`. Don't pre-build.

### `Quest.completed` and `MutableList` inside data classes

`Quest` carries a `MutableList<String> objectives` and `MutableList<Boolean>
completed`. The reducers mutate these in place to preserve pre-extraction
behavior. True immutability would mean rebuilding the Quest on every update —
fine for correctness, slightly wasteful for high-update turns. Defer.

### "Phase II.6+" — message-list construction

The display-message-list construction in `applyParsed` (player bubble dedup
check, narration message build with stat deltas) is ~40 lines that could
become a `MessageBuilder` reducer. Not done in Phase II because it's
output-formatting, not state mutation. If/when the message format changes
substantially, lift it then.
