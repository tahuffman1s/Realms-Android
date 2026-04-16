# Material You Design System Migration

## Overview

Refactor the UI layer to properly follow Material You guidelines. The app already uses Material 3 with dynamic color enabled — the work is enforcing token usage, extracting shared components, and eliminating hardcoded values. Pure appearance + code quality refactor. Zero behavior changes.

**Approach**: Material You structure, fantasy soul. Dynamic color drives app chrome (top bar, bottom nav, FAB, dialogs). Extended color palette (gold crits, rarity tiers, NPC bubbles) stays fixed as semantic tokens that don't change with wallpaper.

## Scope

- 27 files modified, 8 new component files, 1 file deleted (PanelShared.kt)
- Navigation architecture unchanged (manual `when(screen)` state machine)
- Typography unchanged (Cinzel + Crimson Text)
- Canvas art unchanged (campfire, map terrain)
- Game logic, ViewModel, data layer untouched
- Tests should pass without modification

---

## Section 1: Token System

### Spacing — `RealmsSpacing`

Object in `ui/theme/Tokens.kt`. All inline padding/margin values snap to the nearest token. Current inconsistency (14/16/18/20/22dp screen margins) collapses to `l = 16.dp` everywhere.

| Token | Value | Usage |
|-------|-------|-------|
| `xxs` | 2.dp | Pip gaps, wealth bar segments |
| `xs` | 4.dp | Tight inner gaps, section cap padding |
| `s` | 8.dp | Row spacing, chip gaps, default element spacing |
| `m` | 12.dp | Compact card padding, list item padding |
| `l` | 16.dp | Universal screen margin, standard card padding |
| `xl` | 20.dp | Section-to-section vertical gaps |
| `xxl` | 24.dp | Dialog/overlay internal padding |

### Elevation — `RealmsElevation`

Object in `ui/theme/Tokens.kt`. Replaces 6 different ad-hoc tonal elevation values.

| Token | Value | Usage |
|-------|-------|-------|
| `low` | 2.dp | Top bar, input bar, player bubble |
| `medium` | 6.dp | Overlays, rest dialog, map info panel |
| `high` | 10.dp | Pre-roll dialog, critical overlays |

### Shape Enforcement

No new shape values. `RealmsShapes` already defines the correct tokens. The change is referencing them via `MaterialTheme.shapes.*` instead of inline `RoundedCornerShape(N.dp)`:

| Current inline | Token |
|---|---|
| `RoundedCornerShape(6.dp)` | `MaterialTheme.shapes.extraSmall` |
| `RoundedCornerShape(10.dp)` | `MaterialTheme.shapes.small` |
| `RoundedCornerShape(14.dp)` | `MaterialTheme.shapes.medium` |
| `RoundedCornerShape(20.dp)` | `MaterialTheme.shapes.large` |
| `RoundedCornerShape(28.dp)` | `MaterialTheme.shapes.extraLarge` |

`ModalBottomSheet` top corners: use `BottomSheetDefaults.ExpandedShape` (M3 default) instead of overriding.

---

## Section 2: Shared Component Library

New `ui/components/` package. 8 composables extracted from duplicated patterns across 20+ files. All components are `internal` to the module.

### RealmsCard

Wraps M3 Card behind a single API with game-specific defaults. Replaces ~25 `Surface`-as-card patterns.

```kotlin
@Composable
fun RealmsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    outlined: Boolean = false,
    accentColor: Color? = null,
    selected: Boolean = false,
    elevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
)
```

Three variants:
- **Filled** (default): `surfaceVariant.copy(0.45f)` background, `shapes.medium`
- **Outlined**: Border from `accentColor` param. Used for quests, selected spells, equipped items
- **Elevated**: `tonalElevation` from `elevation` param. Used for overlays, dialogs

When `selected = true` and `accentColor` is set, the card tints its background with `accentColor.copy(0.1f)` and shows a full-opacity border. When `onClick` is non-null, the card is clickable.

### RealmsProgressBar

Unifies 4 separate progress bar implementations (HP, XP, morality, combat HP) into one. Wraps M3 `LinearProgressIndicator`.

```kotlin
@Composable
fun RealmsProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
)
```

Callers still decide color logic (HP tier, morality sign, fixed secondary). The component ensures consistent height, corner radius (`shapes.extraSmall`), and track color. Replaces both `LinearProgressIndicator` calls and the custom Box-in-Box bar in CombatHud.

### SectionHeader

Unifies `SectionCap` (PanelShared), private `SectionHeader` (CharacterCreation), and ~15 inline `Text(labelLarge, primary)` calls.

```kotlin
@Composable
fun SectionHeader(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary
)
```

Optional `color` override for era-specific headers in LorePage (goldAccent, fumbleRed, etc.).

### StatusTag

Compact pill badge for quest status, NPC relationship, rarity tier.

```kotlin
@Composable
fun StatusTag(
    label: String,
    color: Color
)
```

Renders as `Surface(shape = shapes.small, color = color.copy(0.15f))` with `Text(label, color = color, labelSmall)`.

### EmptyState

Moved from PanelShared. Updated to use spacing tokens. Same API.

```kotlin
@Composable
fun EmptyState(icon: String, text: String)
```

### FilterTabRow

Moved from PanelShared as `FilterTabs`. Updated to use spacing tokens and `shapes.small`.

```kotlin
@Composable
fun FilterTabRow(
    tabs: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
)
```

### PanelSheet

Moved from PanelShared. Updated to use spacing tokens. Removes explicit `RoundedCornerShape(28.dp)` top corner override (uses M3 default).

### WealthBars

Moved from PanelShared. Updated to use `RealmsSpacing.xxs` for segment gap and `shapes.extraSmall` clip.

### File structure after migration

```
ui/
├── components/          ← NEW
│   ├── RealmsCard.kt
│   ├── RealmsProgressBar.kt
│   ├── SectionHeader.kt
│   ├── StatusTag.kt
│   ├── EmptyState.kt
│   ├── FilterTabRow.kt
│   ├── PanelSheet.kt
│   └── WealthBars.kt
├── theme/               ← +Tokens.kt added
├── game/                ← consumers
├── panels/              ← PanelShared.kt deleted
├── overlays/
├── setup/
└── map/
```

`PanelShared.kt` is fully dissolved. `formatSigned` utility moves to `util/`.

---

## Section 3: Color Cleanup

### Dynamic vs Fixed split

- **Dynamic** (from wallpaper): `primary`, `secondary`, `tertiary`, `surface`, `surfaceVariant`, `background`, containers, `outline`, `error` — used for app chrome (top bar, bottom nav, FAB, buttons, dialogs, scaffolds, input fields, card backgrounds, dividers)
- **Fixed** (`RealmsExtendedColors`): Crit/fumble, success/warning/info, gold accent, rarity tiers, bubble pairs, NPC palette, scrim overlay — used for game content (message bubbles, dice results, item rarity, NPC colors, status effects, lore era dividers)

### Hardcoded colors to migrate (~39 instances)

| File | Count | What | Action |
|------|-------|------|--------|
| MessageBubbles.kt | 20 | NPC dialogue palette (10 color pairs) | Move to `RealmsExtendedColors.npcPalette: List<Pair<Color, Color>>` |
| Overlays.kt | 13 | Campfire/sky Canvas animation | Keep inline — pure Canvas painting |
| TitleScreen.kt | 2 | Graveyard entry gradient | Replace with `errorContainer` → `surface` gradient |
| ApiSetupScreen.kt | 2 | Purple provider highlight | Replace with `secondaryContainer` |
| WorldMapScreen.kt | 2 | Road fill/case terrain colors | Keep inline — terrain Canvas |
| Various (10+ files) | ~15 | `Color.Black.copy(alpha)` scrim overlays | Add `scrimOverlay` to extended colors |
| Various | ~8 | `Color.White` text-over-image | Replace with `inverseOnSurface` where applicable |

Result: ~24 migrate to theme tokens, ~15 stay inline (Canvas art).

### New extended color tokens

Added to `RealmsExtendedColors`:

- `scrimOverlay: Color` — replaces `Color.Black.copy(alpha = 0.7f)` across 10+ files. Dark: `Color(0xB3000000)`, Light: `Color(0xB3000000)`.
- `npcPalette: List<Pair<Color, Color>>` — 10 NPC dialogue bubble/text pairs, dark + light variants. Indexed by NPC name hash (existing pattern, just relocated from inline array).

Total: 25 existing + 2 new = 27 tokens (plus the palette list).

---

## Section 4: Migration Strategy

### Phase 0 — Foundation

Create the token and component layer.

- Add `Tokens.kt` to `ui/theme/` with `RealmsSpacing` and `RealmsElevation`
- Create `ui/components/` with all 8 shared composables
- Add `npcPalette` + `scrimOverlay` to `RealmsExtendedColors` (both dark and light)
- Delete `PanelShared.kt` (contents moved to components/)
- Move `formatSigned` to `util/`

~6 new files, 1 deleted, 2 modified.

### Phase 1 — Panels (9 files, parallel)

InventoryPage, QuestsPage, PartyPage, LorePage, NpcJournalPage, CurrencyPage, SpellsPage, StatsPage. Each is independent.

- Import from `ui.components` instead of `ui.panels.PanelShared`
- Surface-as-card → `RealmsCard`
- Inline shapes → `MaterialTheme.shapes.*`
- Inline padding → `RealmsSpacing.*`
- Inline section headers → `SectionHeader`

### Phase 2 — Game screen (10 files, parallel)

GameScreen, MessageBubbles, NarrationBlock, GameDialogs, GameOverlays, ChatInput, TopBar, CombatHud, ChatFeed, BottomNav. Each is independent.

Same mechanical replacements plus:
- MessageBubbles.kt: move `npcColorPalette` → `RealmsTheme.colors.npcPalette`
- TopBar.kt: HP/XP bars → `RealmsProgressBar`
- CombatHud.kt: Box bar → `RealmsProgressBar`
- GameDialogs.kt: elevation → `RealmsElevation.high`

### Phase 3 — Overlays (3 files, parallel)

- Overlays.kt: elevation/shapes → tokens. Campfire Canvas colors stay inline.
- ShopOverlay.kt: Surface cards → `RealmsCard`, padding → spacing tokens
- TargetPromptDialog.kt: shapes + padding → tokens

### Phase 4 — Setup + Map (5 files, parallel)

- CharacterCreationScreen.kt: delete private `SectionHeader`, use shared. Shapes + padding → tokens.
- TitleScreen.kt: graveyard gradient → theme colors. Shapes + padding → tokens.
- DeathScreen.kt: button shapes → tokens, section headers → `SectionHeader`.
- ApiSetupScreen.kt: purple highlight → `secondaryContainer`. Shapes → tokens.
- WorldMapScreen.kt: elevation → tokens, scrim → `scrimOverlay`. Road Canvas colors stay.

### Mechanical checklist (every file in Phases 1–4)

1. `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
2. `padding(N.dp)` → `padding(RealmsSpacing.*)`
3. `tonalElevation = N.dp` → `tonalElevation = RealmsElevation.*`
4. `Surface(shape, color, border) { Column { ... } }` → `RealmsCard(...) { ... }`
5. `Color(0x...)` / `Color.Black.copy(...)` → theme token or `RealmsTheme.colors.*`
6. Remove unused `import ...RoundedCornerShape` where possible

### What does NOT change

- Navigation architecture (manual `when(screen)` state machine)
- Typography (Cinzel + Crimson Text identity, `RealmsTypography` unchanged)
- Canvas art (campfire flames, map terrain, sky gradients keep inline colors)
- Game logic, ViewModel, data layer (zero changes)
- Message bubble layout (only colors change to theme refs)
