# Visual Overhaul + Navigation Restructure

## Overview

Restructure navigation so all panels are reachable from the UI (not just slash commands), convert Character and Journal from bottom sheets to full-screen tabbed pagers, fix bugs, remove dead code, and finish visual consistency across all panels.

## Navigation Architecture

### Bottom Nav (4 tabs — unchanged count)

| Tab | Icon | Content |
|-----|------|---------|
| Chat | ChatBubble | Message feed + input bar (unchanged) |
| Map | Map | World map canvas (unchanged) |
| Character | Person | **New**: Full-screen HorizontalPager with sub-tabs |
| Journal | MenuBook | **New**: Full-screen HorizontalPager with sub-tabs |

### Character Pager Sub-tabs

`ScrollableTabRow` with 5 tabs: **Stats**, **Inventory**, **Spells**, **Party**, **Currency**

Each tab renders its existing page composable directly (StatsPage, InventoryPage, SpellsPage, PartyPage, CurrencyPage) — but without the `PanelSheet` wrapper. The pages become plain `@Composable` column content inside the pager.

### Journal Pager Sub-tabs

`ScrollableTabRow` with 3 tabs: **Quests**, **NPCs**, **Lore**

Same pattern — QuestsPage, NpcJournalPage, LorePage render as column content inside the pager.

### Remaining Bottom Sheets

- **Memories** — opened by new bookmark icon in TopBar (right side, next to settings gear)
- **Settings** — opened by gear icon in TopBar (already exists)
- **Shop** — opened contextually by merchant interaction (unchanged)
- **Target Prompt** — opened contextually by combat (unchanged)

### Top Bar Changes

Add a bookmark (`Icons.Outlined.BookmarkBorder`) icon button to the right side of the top bar, between the existing content and the settings gear. Tapping it opens `Panel.Memories` as a ModalBottomSheet.

---

## Bug Fixes

### 1. Journal tab mapping (P0)

`GameScreen.kt` line ~106: Change `GameTab.Journal -> panel = Panel.Quests` to render the JournalPager (no longer a panel dispatch — it's a full-screen view).

### 2. Memories accessibility (P0)

- Add bookmark icon to TopBar (described above)
- Add `/memories` to `handleSlashCommand` in the ViewModel or ChatInput

### 3. Dead code removal

- Delete `MoreMenuSheet` + `MoreTile` from `GameOverlays.kt` (~lines 393-458)
- Delete `SpellChip` from `ChatInput.kt` (~lines 172-209)

### 4. Deprecated API

- `LorePage.kt`: `LoreTab.values()` → `LoreTab.entries` (3 occurrences)

---

## Visual Changes

### A. Panel pages: bottom sheet → inline content

All 8 panel pages (Stats, Inventory, Spells, Party, Currency, Quests, NPCs, Lore) currently wrap in `PanelSheet(title, subtitle, onClose) { ... }`. This must change:

- Each page gets a new parameter signature: remove `onClose`, add `modifier: Modifier = Modifier`
- Remove the `PanelSheet` call — the page becomes a plain scrollable `Column`
- The pager host (CharacterPager/JournalPager) provides the title via `ScrollableTabRow`, not the page itself
- Pages that still need to be opened as standalone sheets (e.g., from slash commands or chat bubble taps) get a thin wrapper: `PanelSheet(title, onClose) { XxxPage(vm) }`

### B. StatsPage visual rework

Convert all raw `Surface` patterns to `RealmsCard`. Restructure layout:
- Header row: character name, level badge, HP bar, AC shield
- Ability scores: 2x3 grid of `RealmsCard` tiles (STR, DEX, CON, INT, WIS, CHA)
- Morality: `RealmsProgressBar` with label
- Conditions: horizontal chip row
- Backstory: expandable `RealmsCard`

### C. Remaining raw Surface cleanup

Convert remaining raw `Surface`-as-card patterns to `RealmsCard` across:
- `InventoryPage.kt` — BackpackCell, RarityTag, TypeTag
- `QuestsPage.kt` — reward chip
- `LorePage.kt` — LivingWorldRow, faction chips
- `NpcJournalPage.kt` — TraitChip, RelationshipTag
- `CurrencyPage.kt` — ExchangeCard, wealth header

### D. ModalBottomSheet cleanup

Remove all `shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)` overrides from ModalBottomSheet calls. M3's default shape handles this. Affects:
- `GameOverlays.kt` (4 occurrences)
- `TitleScreen.kt` (2 occurrences)
- `ShopOverlay.kt` (1 occurrence, already using `shapes.extraLarge`)

### E. Off-grid padding cleanup

Fix remaining non-token padding values:
- `GameOverlays.kt` — `padding(horizontal = 18.dp)` → `RealmsSpacing.l`
- `NarrationBlock.kt` — `padding(14.dp)` → `RealmsSpacing.m`
- Various `Spacer(6.dp)` → `RealmsSpacing.xs`

---

## New Files

| File | Responsibility |
|------|---------------|
| `ui/game/CharacterPager.kt` | `HorizontalPager` + `ScrollableTabRow` wrapping Stats, Inventory, Spells, Party, Currency pages |
| `ui/game/JournalPager.kt` | `HorizontalPager` + `ScrollableTabRow` wrapping Quests, NPCs, Lore pages |

## Modified Files

| File | Change |
|------|--------|
| `GameScreen.kt` | Character/Journal tabs render pagers instead of dispatching panels. Remove panel branches for Stats/Inventory/Spells/Party/Currency/Quests/Journal/Lore. Keep Memories + Settings as sheets. |
| `TopBar.kt` | Add bookmark icon for Memories |
| `BottomNav.kt` | No change needed — tab enum already correct |
| `StatsPage.kt` | Visual rework with RealmsCard, grid layout |
| `InventoryPage.kt` | Dual mode: inline content (in pager) or PanelSheet (from slash command). Fix remaining raw Surfaces. |
| `SpellsPage.kt` | Same dual mode pattern |
| `PartyPage.kt` | Same dual mode pattern |
| `CurrencyPage.kt` | Same dual mode pattern. Fix raw Surfaces. |
| `QuestsPage.kt` | Same dual mode pattern. Fix raw Surfaces. |
| `NpcJournalPage.kt` | Same dual mode pattern. Fix raw Surfaces. |
| `LorePage.kt` | Same dual mode pattern. Fix .values()→.entries. Fix raw Surfaces. |
| `GameOverlays.kt` | Delete dead code. Fix ModalBottomSheet shapes. Fix off-grid padding. |
| `ChatInput.kt` | Delete SpellChip dead code. Add /memories command. |

---

## Chat Feed Redesign

The chat feed gets a personality overhaul — every segment type becomes visually distinct.

### Turn Dividers

Between each narrator turn, render an ornamental divider:
- Centered `TURN N` label in `labelSmall`, `outline` color, 2sp letter-spacing, serif font
- Horizontal gradient lines on each side: `transparent` → `outlineVariant` → `transparent`
- Padding: 16dp top, 8dp bottom

### Prose Narration — Book Margin Style

Replace the plain dark rectangle with a left-accent-bar treatment:
- Surface: `narratorBubble` color, `shapes.medium`
- Left border: 3dp solid `asideOnBubble` at 30% alpha (the purple accent creates a book-margin feel)
- Padding: 14dp horizontal, 12dp vertical
- Content: `NarrationMarkdown` as before (Crimson Text serif, 16sp)
- Bookmark icon remains in bottom-right corner

### NPC Action — Attributed to Character

Currently identical to aside. Change to:
- Left-aligned, no background surface
- Left border: 2dp solid NPC's accent color at 30% alpha
- Padding-left: 10dp after the border
- Text: `bodySmall`, italic, NPC accent color at 80% alpha
- This makes NPC actions visually attributable to the speaking NPC

### Narrator Aside — Tilde Wrapper

Currently identical to NPC action. Change to:
- Centered, no background
- Text wrapped in `~ tildes ~` decorators
- Color: `asideOnBubble` at 70% alpha
- Typography: `bodySmall`, italic
- Visually distinct from NPC actions (centered vs left-aligned, purple vs NPC color)

### Player Action — Right-aligned with Gold Accent

Currently identical to aside. Change to:
- Right-aligned, no background surface
- Right border: 2dp solid `goldAccent` at 30% alpha
- Padding-right: 10dp before the border
- Text: `bodySmall`, italic, `goldAccent` at 80% alpha
- Mirrors NPC action but on the player's side

### NPC Dialog Bubble — Avatar Column

Rework from inline avatar to column layout:
- Left column: 32dp circle avatar with gradient background (NPC accent at 40% → 15%), 1.5dp accent border, initial letter in `titleSmall`
- Right column (bubble): NPC bgTint at 12% alpha (lighter than current 75%), 1dp accent border at 20% alpha
- Asymmetric corner radius: `RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)` — creates a speech-bubble tail pointing at the avatar
- Speaker name: uppercased in `labelSmall`, accent color, 0.5sp letter-spacing
- Quote text: `bodyMedium`, italic, `npcOnBubble` at 95% alpha

### Player Dialog Bubble — Mirrored Layout

Mirror of NPC dialog:
- Right column: 32dp gold avatar with gradient, gold border, initial letter
- Left column (bubble): `playerBubble` at 40% alpha, 1dp gold border at 30% alpha
- Asymmetric corners: `RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp)` — tail points right
- No "You:" label — the gold avatar with initial is sufficient
- Quote text: `bodyMedium`, italic, `playerOnBubble`

### Scene Banner — Dramatic Entry

Replace the flat `secondaryContainer` surface:
- Background: `Brush.linearGradient` from `playerBubble` at 30% alpha to `npcBubble` at 30% alpha (135°)
- Left border: 3dp solid `secondary`
- Shape: `shapes.medium`
- Layout: emoji icon at `titleMedium` + name in `labelLarge` / `secondary` + description in `bodySmall` / `onSurfaceVariant`
- Margin: horizontal 12dp

### Stat Change Pills — Icons

Add small emoji prefixes to stat pills for personality:
- HP: heart ♥ prefix
- XP: star ★ prefix
- Gold: coin prefix (already has 💰)
- Rep: lightbulb 💡 prefix
- Items: sword/shield prefix

---

## Files Changed by Chat Redesign

| File | Change |
|------|--------|
| `MessageBubbles.kt` | Rework `NpcDialogueBubble` (avatar column, asymmetric corners, lighter bg), `PlayerBubble` (mirrored avatar column, remove "You:" label, asymmetric corners), `SceneBanner` (gradient + accent bar), `SystemLine` (unchanged), `EventCard` (unchanged), stat pill icons |
| `NarrationBlock.kt` | Rework `NarratorProseBubble` (add left accent bar), create `NpcActionLine` (left-aligned with NPC color accent), create `NarratorAsideLine` (centered with tildes), create `PlayerActionLine` (right-aligned with gold accent). Replace `NarratorQuipBubble` dispatching with distinct composables per segment type. |
| `ChatFeed.kt` | Add `TurnDivider` composable rendered between turns. Detect turn boundaries from message list. |

---

## What Does NOT Change

- Map screen (unchanged)
- Death screen, Title screen, Character Creation, API setup (unchanged)
- Game logic, ViewModel, data layer (unchanged)
- Theme tokens, shared components (unchanged — we use the foundation from the design system migration)
