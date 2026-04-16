# Realms of Fate — Roadmap

Phases that have shipped, phases queued, and phases deliberately deferred or
declined. Updated at each commit. Read top-to-bottom for full context, or skim
the **Status** column.

## Index

- [AI Reliability](#ai-reliability) — Phases 1–5 (DeepSeek output stability)
- [Parser](#parser) — Phases A–E (tokenizer + JSON metadata refinements)
- [GameViewModel Refactor](#gameviewmodel-refactor) — Phases I–IV (god-method extraction)
- [Tactical Backlog](#tactical-backlog) — small fixes flagged during playtests
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
| C | NPC display-name substitution in `parsed.narration` body | 🟡 Pending |
| D | Retire regex mechanical-tag fallback (delete the `else` branch in `parse()` + `prose*` fallbacks) | 🟡 Gated on data soak |
| E | Split `ParsedReply` into `NarrativeContent` + `TurnMechanics` | 🟢 Optional, deferred |

### Phase C — substitute NPC display names in narration body

**Bug:** `parsed.narration` is built by stripping all tags from the raw
response, leaving body text without subjects. `[NPC_ACTION:scroll-merchant]
stares at the coins[/NPC_ACTION]` becomes "stares at the coins" with no
subject.

**Scope:** cosmetic only. The in-game UI renders via `segments` (which carry
NPC ID and resolve to displayName). `parsed.narration` is only used in:
1. Debug dump text (looks weird to humans reading dumps).
2. The `dialoguePattern` / `dialogueFallback` regex scan in `TagParser` — but
   that path is now dead-code-ish since structured `[NPC_DIALOG:id]` extraction
   handles it directly.

**Two-line approach:** in the narration-strip step, substitute the NPC's
display name from `npcLog` for the `[NPC_ACTION:id]` / `[NPC_DIALOG:id]` body.
Requires threading `npcLog` into `TagParser.parse()` OR moving narration
assembly out of the parser and into the ViewModel where `npcLog` is already
available.

Prefer the second approach — it's cleaner architecturally and a precondition
for Phase D's regex-fallback removal anyway.

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

**Result:** `GameViewModel.kt` 2131 → 1389 lines (−34.8%). `applyParsed`
is ~140 lines (down from 528). 5 reducers totaling 817 lines, each pure with
typed results. 4 handlers totaling 484 lines. 48 tests across 6 test classes,
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
(currently 1389 after Phase III), OR a second VM consumer appears
(companion app, etc.).

---

## Tactical Backlog

Small bugs/quirks surfaced during playtests that don't warrant a phase:

- **Quest objectives accumulate near-duplicates** — `quest_updates` appends a
  new objective AS already-complete when its text doesn't match an existing
  objective. AI sometimes slightly rephrases the original objective ("Find
  someone who studies rifts" vs. "Find someone who studies rifts — the scroll
  merchant might point you"), so the journal grows cluttered. Two-prong fix:
  prompt nudge + reducer guard against fuzzy-match dupes.
- **`availableMerchants` accumulates forever** — never pruned on scene change.
  Visiting a merchant once means the shop button stays for the rest of the
  game. Either prune on `scene` change away from the merchant's location, or
  expire after N turns away.
- **AI uses "Unnamed X" as initial NPC display name** — workaround: the
  rename mechanic lets a later turn replace it with a real name. Underlying
  cause: prompt tells AI "must have a name" but interpretation drifts. One
  more BAD/GOOD example pair would tighten this.
- **Travel progress is dice-dependent** — `2 + (roll % 3)` leagues per turn
  means the same long journey takes wildly different turn counts depending on
  the player's d20 luck. Either pin the formula or surface the variance.
- **`logTimeline` is still a VM side-effect, not a reducer output** — every
  reducer returns timeline entries which the VM drains. The VM's own
  `logTimeline()` calls (used in `submitAction`, save load, etc.) still write
  directly to the timeline list. Could move to a `TimelineService` for
  consistency. Low priority.
- **`parsed.narration` orphan subjects** — covered by Parser Phase C above.

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
added 28 handler-level tests (merchant, rest, save, progression), bringing
the total to 48. Still not covered:
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
