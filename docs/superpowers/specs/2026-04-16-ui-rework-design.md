# UI Rework Design

## Overview

Full rework of the UI layer to feel like a polished native Android app. Maintains
all existing features but streamlines navigation, fixes all audited bugs, and
establishes a consistent Material You design system.

The chat should feel like iMessage/Google Messages — clean bubbles, left/right
alignment, smooth animations. Narration flows naturally in document order with
collapsible older turns.

## Implementation Phases

The rework is split into 6 independent phases, each shippable on its own:

1. **Design System** — theme tokens, typography, bubble palette (foundation)
2. **Chat Experience** — message feed, bubbles, input, scroll, narration collapse
3. **Game Chrome** — top bar, bottom nav, contextual FAB
4. **Panels** — Character tab, Journal tab, settings sheet, choices sheet
5. **Setup Flow** — API setup, title, character creation, death screen
6. **Map + Combat** — map fixes, combat HUD polish

Phases must ship in order: 1 before 2 (chat depends on tokens), 2 before 3
(chrome wraps the chat), 3 before 4 (panels live inside the new nav). Phases
5 and 6 are independent and can ship in any order after 3.

---

## Phase 1: Design System

### Color tokens

Replace all ~20 `Color(0xFF...)` hardcoded literals in GameScreen.kt, Panels.kt,
and Markdown.kt with semantic tokens.

Add to `Extended.kt` (both `DarkExtended` and `LightExtended`):

```
narratorBubble / narratorOnBubble    — narration background and text
npcBubble / npcOnBubble              — NPC dialogue background and text
playerBubble / playerOnBubble        — player action background and text
asideBubble / asideOnBubble          — narrator aside pill
systemBubble / systemOnBubble        — system messages
```

Dynamic color aware — derive from `MaterialTheme.colorScheme` surfaces where
possible, with fallback values for pre-Android-12 devices.

### Typography

- Remove fake `SemiBold` weight from `CrimsonTextFontFamily` in `Fonts.kt`.
- Differentiate `titleLarge` (18sp) and `headlineSmall` (20sp) in `Type.kt`.
- Wire `LocalFontScale` to respect Android system font accessibility via
  `LocalConfiguration.fontScale` so the app honors system-wide Large Text.

### Markdown renderer

- `parseInline` bold color: read from `RealmsTheme.colors.goldAccent` via a
  parameter instead of hardcoded `0xFFD4A843`.
- Code span colors: use `MaterialTheme.colorScheme.surfaceVariant` /
  `onSurfaceVariant` instead of raw hex.
- Fix the unclosed `**bold**` fallthrough into `*italic*` branch.

### Activity theme

Change `themes.xml` parent from `Theme.Material.Light.NoActionBar` to
`Theme.Material3.DarkNoActionBar` (or a dynamic parent) to eliminate the
white flash on dark-preference devices.

---

## Phase 2: Chat Experience

### File split

`GameScreen.kt` (3,311 lines) splits into:

| New file | Responsibility | Approx lines |
|----------|---------------|-------------|
| `ChatFeed.kt` | `LazyColumn`, scroll behavior, empty state, scroll indicator | ~200 |
| `MessageBubbles.kt` | All bubble composables: player, narration, NPC, aside, system | ~400 |
| `NarrationBlock.kt` | Collapsible narration: full on latest turn, collapsed on older | ~200 |
| `ChatInput.kt` | Text field, send button, disabled state, NPC reply prefill | ~100 |
| `GameScreen.kt` | Scaffold, state collection, wiring (dramatically smaller) | ~300 |

### Message rendering — document order

Each `DisplayMessage.Narration` carries `segments: List<NarrationSegmentData>`.
Render segments in the order the AI wrote them:

- `NarratorProse` — left-aligned bubble, serif font, `narratorBubble` color
- `NarratorAside` — centered pill, italic, `asideBubble`, 80% max width
- `NpcDialog` — left-aligned bubble with name chip accent, `npcBubble`
- `NpcAction` — left-aligned, italic, no bubble background
- `PlayerAction` — right-aligned, `playerBubble`
- `PlayerDialog` — right-aligned, quote styling

No prose merging or reordering. Segments flow top-to-bottom as written.

### Collapsible narration

- Latest turn: all segments rendered fully inline.
- Older turns: auto-collapse to 2-3 line prose preview. Tap to expand in-place
  (accordion `AnimatedVisibility`). No modal dialog.
- Delete `ProseDetailDialog`.

### Chat input

- `enabled = !state.isGenerating` on the `OutlinedTextField` itself.
- Placeholder: "What do you do?" when idle, "Narrator is writing..." during gen.
- `maxLines = 5` with internal vertical scroll.
- `onNpcReply`: call `focusRequester.requestFocus()` instead of `clearFocus()`.
- Send button uses `MaterialTheme.colorScheme.primary`.

### Empty state

When `messages.isEmpty()`, show a centered card with character name/race/class
and "Your story begins..." — not a blank screen.

### Scroll behavior

On new turn: `animateScrollToItem(turnStartIndex)`. When content extends below
the fold, show a floating "scroll down" chip at the bottom edge of the feed.

### Error handling

Error card: 10-second countdown ring, X dismiss button, no silent auto-dismiss.
Red accent from `MaterialTheme.colorScheme.error`.

---

## Phase 3: Game Chrome

### Top bar — two-row collapsing

Row 1 (pinned): character name + HP bar (proportional, colored) + settings gear.

Row 2 (collapses on scroll via `exitUntilCollapsed`): location icon + full-width
name (no 110dp cap) + turn counter + gold.

### Bottom navigation — 4 tabs + contextual FAB

| Tab | Content |
|-----|---------|
| Chat | Message feed (default) |
| Map | World map with travel |
| Character | Tabbed: Stats, Inventory, Spells |
| Journal | Tabbed: Quests, NPCs, Lore, Party |

Settings: gear icon in top bar → settings bottom sheet.

Currency/Exchange: moves under Character > Stats as an expandable card.

### Contextual FAB (Chat tab only)

| Game state | FAB |
|-----------|-----|
| Default (no action needed) | Hidden |
| Choices available | Choices icon + badge count → choices sheet |
| Combat active | Sword icon → attack target picker |
| Safe location | Campfire icon → rest options |

FAB only appears when there is a contextual action. No permanent FAB.

---

## Phase 4: Panels

### File split

`Panels.kt` (2,426 lines) splits into:

| New file | Content |
|----------|---------|
| `CharacterTab.kt` | `HorizontalPager` wrapper: Stats, Inventory, Spells |
| `StatsPage.kt` | Ability scores, HP, AC, proficiency, currency card |
| `InventoryPage.kt` | Grid + equip + item detail |
| `SpellsPage.kt` | Slot strip + hotbar + known spells |
| `JournalTab.kt` | `HorizontalPager` wrapper: Quests, NPCs, Lore, Party |
| `QuestsPage.kt` | Active/completed/failed with filter chips |
| `NpcJournalPage.kt` | NPC cards + detail with `AnimatedVisibility` |
| `LorePage.kt` | World/factions/history/rumors with filter chips |
| `PartyPage.kt` | Companion list with dismiss |
| `SettingsSheet.kt` | Font scale, debug dump, export save |
| `ChoicesSheet.kt` | AI-offered choices |

### Bug fixes baked in

- Inventory equip-toggle reads from latest VM state, not local copy.
- Party list gets `heightIn(max = 400.dp)` constraint.
- Lore uses `FilterChip` pill row instead of `ScrollableTabRow` (fixes sheet
  dismiss conflict).
- NPC detail card wrapped in `AnimatedVisibility(expandVertically())`.
- Quest filter uses `.entries` instead of deprecated `.values()`.
- Sell price multiplied by `item.qty`.

---

## Phase 5: Setup Flow

### ApiSetupScreen

- Strip dead provider picker. Single DeepSeek-focused screen.
- API key field with inline format hint ("Keys start with sk-").
- Validation feedback shown below the field.
- `setApiKey` on confirm only, not on every keystroke.
- Dark background matching game theme.

### TitleScreen

- "New Game" and "Continue" as prominent Material You buttons.
- Save slots as a `LazyColumn` of cards.
- Swipe-to-delete with undo `Snackbar` (replaces instant delete with no confirm).
- Graveyard as collapsible section.
- Hide "Load (0)" when no saves exist.

### CharacterCreationScreen

- Keep 6-step wizard.
- `LinearProgressIndicator` for step tracking.
- Each step extracted to its own composable.
- Point-buy: ability score cards with +/- buttons and remaining-points badge.
- Single back button (bottom bar only, remove duplicate top bar back).

### DeathScreen

- Fix `moralityColor` dead code (`takeIf { false }` → direct `else`).
- "Loading..." placeholder instead of blank frame on slow start.
- Graveyard world name already fixed (uses `worldLore.worldName`).

---

## Phase 6: Map + Combat

### Map

- Scale bar label updates with zoom level.
- Travel banner and distance pills: conditional visibility to prevent overlap.
- Unreachable locations: suppress dialog or show toast "No road — travel to
  a neighbor first."
- Empty state: "Generating world..." when locations list is empty.
- Dialog surfaces use Material You colors.

### Combat HUD

- Initiative chip: `maxLines = 1, overflow = TextOverflow.Ellipsis`.
- Active player chip border: `goldAccent` instead of `error` red.
- Enemy chips: `errorContainer` color (already correct).

---

## Accessibility (applied across all phases)

- Wire `LocalFontScale` to Android system font setting (Phase 1).
- All touch targets minimum 48x48dp (Phases 2-4).
- Swipe gestures get `Modifier.semantics` actions with long-press alternatives
  (Phase 2).
- Bookmark icons enlarged to 48dp touch target (Phase 2).

## Testing Strategy

- No Compose UI tests (Robolectric limitations with Compose).
- Visual verification on device after each phase.
- Existing 54 unit/integration tests remain green (UI changes don't affect
  game logic).
- Each phase: `gradle test` + `gradle assembleRelease` + manual device check.
