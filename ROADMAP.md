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

## UI/UX

Issues found via comprehensive UI audit. Organized by priority.

### Bugs (fix before release)

- [ ] **Tab.More sheet doesn't reopen on second tap** — `LaunchedEffect(tab)`
  won't re-fire when the same key is set again. `GameScreen.kt:157–165`.
- [ ] **`onNpcReply` clears keyboard focus instead of requesting it** —
  `clearFocus()` called after prefilling text field, forcing a second tap.
  `GameScreen.kt:252–256`.
- [ ] **Legacy narration swipe-attack fires on old turns** — missing
  `isLatestTurn` guard in the `splitNarration` path. `GameScreen.kt:2396–2413`.
- [ ] **Save delete has no confirmation dialog** — one mis-tap destroys a save
  permanently. `TitleScreen.kt:351`.
- [ ] **ShopOverlay buyback tab state stuck** — when buyback stock empties while
  tab is `"back"`, the tab disappears but state stays `"back"`, rendering
  empty forever. `ShopOverlay.kt:74–76`.
- [ ] **LevelUpOverlay dismissible with unassigned points** —
  `dismissOnClickOutside = true` lets the player lose stat points by
  accident. `Overlays.kt:53`.
- [ ] **Markdown bold/italic parser falls through on unclosed bold** — unclosed
  `**` falls into the `*italic*` branch, consuming mid-span content.
  `Markdown.kt:161–169`.
- [ ] **Map scale bar label doesn't update with zoom** — always reads
  "6 leagues" regardless of zoom level. `WorldMapScreen.kt:181`.

### UX Problems (fix for quality)

- [ ] **Narration hidden behind tap** — `NarratorBubble` shows 3-line summary;
  full prose requires tapping into a modal dialog. The narration IS the game.
  `GameScreen.kt:2695–2781`.
- [ ] **Structured segment ordering merges prose incorrectly** — all `Prose`
  segments rendered at top, all NPC dialogue after, losing natural scene flow.
  `GameScreen.kt:2291–2373`.
- [ ] **Input field has no disabled visual during AI generation** — player can
  type freely with no "waiting" cue. `GameScreen.kt:1156–1177`.
- [ ] **Error card auto-dismisses silently in 5s** — no animation, countdown,
  or visual hint that it's tappable. `GameScreen.kt:322–340`.
- [ ] **Location name hard-capped at 110dp** — long names truncated regardless
  of screen width. `GameScreen.kt:1073`.
- [ ] **Empty chat state shows nothing** — blank screen with no placeholder on
  first load. `GameScreen.kt:235–341`.
- [ ] **Long NPC names overflow in dialogue bubbles and combat chips** — no
  `maxLines`/`overflow` constraint. `GameScreen.kt:2983`, `1878`.
- [ ] **Map travel dialog opens for unreachable locations** — shows dialog with
  only a Close button and no explanation. `WorldMapScreen.kt:234–261`.
- [ ] **Map traveling banner overlaps distance pills** — both use
  `BottomCenter` alignment. `WorldMapScreen.kt:309–351`.
- [ ] **Lore panel `ScrollableTabRow` conflicts with sheet dismiss gesture** —
  horizontal swipe mis-routed as sheet dismiss. `Panels.kt:668–689`.
- [ ] **Journal NPC detail card appears without animation** — abrupt layout
  shift. `Panels.kt:1336–1342`.
- [ ] **No error feedback for invalid API key format** — CTA silently disabled
  with no hint. `ApiSetupScreen.kt:41, 107`.
- [ ] **Shop sell price ignores item quantity** — selling a stack of 10 potions
  yields the same gold as selling one. `ShopOverlay.kt:141–148`.

### Design System Cleanup

- [ ] **~15 hardcoded `Color(0xFF...)` in GameScreen.kt** — dark-mode-only
  raw hex values; broken in light mode. Replace with `RealmsTheme.colors`
  or `MaterialTheme.colorScheme`. Lines 660, 670, 2698, 2758, 2819, 2883, 2910.
- [ ] **Hardcoded colors in Markdown.kt** — `parseInline` bold color
  (`0xFFD4A843`) and code span colors are dark-mode only. Lines 137, 187.
- [ ] **8 `BackstoryCard` calls with inline colors** — duplicates tokens
  already in `Extended.kt`. `Panels.kt:2318–2326`.
- [ ] **`CrimsonTextFontFamily` registers non-existent SemiBold weight** —
  system synthesizes bold artificially. `Fonts.kt:36`.
- [ ] **Activity theme parent is `Material.Light`** — flashes white on
  dark-preference devices before Compose paints. `themes.xml:15`.

### Accessibility

- [ ] **App font scale ignores Android system font setting** —
  `LocalFontScale` not wired to system accessibility. `GameScreen.kt:2112`.
- [ ] **Bookmark touch targets below 48dp minimum** — `24–28dp` throughout.
  `GameScreen.kt:2134, 2463, 2742`.
- [ ] **Swipe/long-press gestures have no a11y alternative** — no
  `Modifier.semantics` actions for screen reader users.
  `GameScreen.kt:3134–3203`.

### Improvements (nice to have)

- [ ] **Choices FAB should show count badge** — `count` param accepted but
  never displayed. `GameScreen.kt:1299–1308`.
- [ ] **Active player combat chip should use gold, not error red** — visually
  indistinct from enemy chips. `GameScreen.kt:1860–1867`.
- [ ] **Party overflow `+N` should be tappable** — open Party panel on tap.
  `GameScreen.kt:968–990`.
- [ ] **Input field `maxLines = 3` clips long actions** — should be 5 or
  scrollable. `GameScreen.kt:1163`.

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
