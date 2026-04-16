# UI Phase 4: Panels Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split Panels.kt (2,427 lines, 42 composables) into focused files matching the 4-tab navigation. Fix all panel UX bugs identified in the audit.

**Architecture:** Extract panel groups into dedicated files. The Character tab combines Stats + Inventory + Spells. The Journal tab combines Quests + NPCs + Lore + Party. Currency moves under Stats. Each extraction is a pure move with targeted bug fixes.

**Tech Stack:** Kotlin, Jetpack Compose Material 3

---

## File Structure

| New file | Content | Source lines |
|----------|---------|-------------|
| `PanelShared.kt` | `PanelSheet`, `EmptyState`, `SectionCap`, `FilterTabs` | 38-138 |
| `InventoryPage.kt` | `InventoryPanel` + 7 helpers | 143-466 |
| `QuestsPage.kt` | `QuestsPanel` + `StatusTag` + `QuestFilter` enum | 470-596 |
| `PartyPage.kt` | `PartyPanel` | 601-647 |
| `LorePage.kt` | `LorePanel` + 10 sub-composables + `LoreTab` enum | 651-1249 |
| `NpcJournalPage.kt` | `JournalPanel` + 5 helpers + `JournalFilter` enum | 1253-1663 |
| `CurrencyPage.kt` | `CurrencyPanel` + 3 helpers | 1668-1996 |
| `SpellsPage.kt` | `SpellsPanel` + 4 helpers + `spellLevelLabels` | 2000-2222 |
| `StatsPage.kt` | `StatsPanel` + 3 helpers + `formatSigned` | 2227-2427 |

After extraction, `Panels.kt` is deleted entirely.

---

### Task 1: Extract PanelShared.kt + InventoryPage.kt + QuestsPage.kt

Extract the shared infrastructure and first two panel groups.

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/PanelShared.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/InventoryPage.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/QuestsPage.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/Panels.kt`

- [ ] **Step 1: Create PanelShared.kt** with `PanelSheet`, `EmptyState`, `SectionCap`, `FilterTabs` (lines 38-138). All become `internal`.

- [ ] **Step 2: Create InventoryPage.kt** with `InventoryPanel` + `EquippedSlot`, `SelectedItemCard`, `BackpackCell`, `RarityTag`, `TypeTag`, `rarityColor`, `itemEmoji`, `itemIconFor` (lines 143-466). `InventoryPanel` is `internal`, rest `private`.

**Bug fix:** In `InventoryPage.kt`, the equip-toggle uses a local `selected` var that can go stale. Change the detail card to re-resolve `selected` from `state.character?.inventory` on each recomposition rather than holding a local copy.

- [ ] **Step 3: Create QuestsPage.kt** with `QuestsPanel`, `StatusTag`, `QuestFilter` enum (lines 470-596). `QuestsPanel` is `internal`, rest `private`.

**Bug fix:** Replace `QuestFilter.values()` with `QuestFilter.entries`.

- [ ] **Step 4: Remove extracted code from Panels.kt.**

- [ ] **Step 5: Verify** — `gradle test` and `gradle assembleDebug` both pass.

- [ ] **Step 6: Commit**
```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/panels/
git commit -m "Extract PanelShared, InventoryPage, QuestsPage from Panels.kt"
```

---

### Task 2: Extract PartyPage.kt + LorePage.kt

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/PartyPage.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/LorePage.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/Panels.kt`

- [ ] **Step 1: Create PartyPage.kt** with `PartyPanel` (lines 601-647). `internal`.

**Bug fix:** Add `Modifier.heightIn(max = 400.dp)` to the `LazyColumn` inside `PartyPanel` to prevent it from expanding past the screen.

- [ ] **Step 2: Create LorePage.kt** with `LorePanel` + all 10 sub-composables + `LoreTab` enum (lines 651-1249). `LorePanel` is `internal`, rest `private`.

**Bug fix:** Replace `ScrollableTabRow` with `FilterTabs` (the shared pill-row composable from `PanelShared.kt`) to eliminate the horizontal-swipe vs sheet-dismiss gesture conflict.

- [ ] **Step 3: Remove extracted code from Panels.kt.**

- [ ] **Step 4: Verify** — `gradle test` and `gradle assembleDebug` both pass.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/panels/
git commit -m "Extract PartyPage, LorePage from Panels.kt; fix party height and lore tab gesture"
```

---

### Task 3: Extract NpcJournalPage.kt + CurrencyPage.kt

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/NpcJournalPage.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/CurrencyPage.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/Panels.kt`

- [ ] **Step 1: Create NpcJournalPage.kt** with `JournalPanel` + `NpcDetailCard`, `DialogueLine`, `TraitChip`, `RelationshipTag`, `extractTraitChips`, `JournalFilter` enum (lines 1253-1663). `JournalPanel` is `internal`, rest `private`.

**Bug fix:** Wrap `NpcDetailCard` appearance in `AnimatedVisibility(enter = expandVertically(), exit = shrinkVertically())` so the detail card animates in/out instead of abruptly appearing.

- [ ] **Step 2: Create CurrencyPage.kt** with `CurrencyPanel` + `CurrencyFactionCard`, `ExchangeCard`, `RatesTable` (lines 1668-1996). `CurrencyPanel` is `internal`, rest `private`.

- [ ] **Step 3: Remove extracted code from Panels.kt.**

- [ ] **Step 4: Verify** — `gradle test` and `gradle assembleDebug` both pass.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/panels/
git commit -m "Extract NpcJournalPage, CurrencyPage from Panels.kt; fix NPC detail animation"
```

---

### Task 4: Extract SpellsPage.kt + StatsPage.kt, Delete Panels.kt

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/SpellsPage.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/StatsPage.kt`
- Delete: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/Panels.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Create SpellsPage.kt** with `SpellsPanel` + `SpellSlotStrip`, `SlotDiamond`, `SpellCard`, `SpellDetailCard`, `spellLevelLabels` (lines 2000-2222). `SpellsPanel` is `internal`, rest `private`.

**Bug fix:** In `SpellsPanel`, multiply sell price by `item.qty` — actually this is ShopOverlay, not SpellsPanel. No spell-specific fix needed here.

- [ ] **Step 2: Create StatsPage.kt** with `StatsPanel` + `StatTile`, `BackstoryCard`, `AbilityTile`, `formatSigned` (lines 2227-2427). `StatsPanel` is `internal`, rest `private`.

- [ ] **Step 3: Delete Panels.kt** — all code has been extracted. Verify no imports reference `Panels.kt` directly (they reference the individual composable names, which are now in separate files in the same package).

- [ ] **Step 4: Update GameScreen.kt imports** if needed. Since all panel composables are in the same `com.realmsoffate.game.ui.panels` package and GameScreen.kt imports them by name (e.g., `InventoryPanel`), the wildcard import `import com.realmsoffate.game.ui.panels.*` should still resolve. If GameScreen uses specific imports, no change needed.

- [ ] **Step 5: Verify** — `gradle test` and `gradle assembleDebug` both pass.

- [ ] **Step 6: Commit**
```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/panels/
git commit -m "Extract SpellsPage, StatsPage; delete Panels.kt (2,427 lines → 9 focused files)"
```

---

### Task 5: Fix ShopOverlay Sell Quantity Bug

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/overlays/ShopOverlay.kt`

- [ ] **Step 1: Read ShopOverlay.kt** and find the sell price calculation (around line 141-148). The sell price is computed per-item but doesn't account for stack quantity.

- [ ] **Step 2: Fix** — multiply the sell value by `item.qty` so selling a stack of 10 potions yields 10x the per-unit price. Find the `sellValue` function or inline calculation and apply the multiplier.

Also fix the buyback tab state bug: when `buybackStock` empties while `tab == "back"`, reset `tab` to `"buy"`. Add a `LaunchedEffect(buybackStock)` or a conditional check.

- [ ] **Step 3: Verify** — `gradle test` and `gradle assembleDebug` both pass.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/overlays/ShopOverlay.kt
git commit -m "Fix shop: sell price accounts for stack quantity, buyback tab state reset"
```

---

### Task 6: Final Verification

- [ ] **Step 1: Verify Panels.kt is deleted**
```bash
ls app/src/main/kotlin/com/realmsoffate/game/ui/panels/Panels.kt
```
Expected: No such file

- [ ] **Step 2: List all panel files**
```bash
wc -l app/src/main/kotlin/com/realmsoffate/game/ui/panels/*.kt
```

- [ ] **Step 3: Run full tests + release build**
```bash
gradle test && gradle assembleRelease
```

- [ ] **Step 4: Commit any cleanup**
```bash
git add -A && git commit -m "Phase 4 cleanup: remove unused imports"
```
