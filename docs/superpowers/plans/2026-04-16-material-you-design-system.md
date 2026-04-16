# Material You Design System Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce Material You token usage, extract shared components, and eliminate hardcoded values across the entire UI layer — zero behavior changes.

**Architecture:** Build a foundation layer (spacing/elevation tokens + 8 shared components in `ui/components/`), then mechanically propagate tokens across all 27 UI files in 4 parallel phases. Each phase's files are independent and can be migrated concurrently.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 (already in use), Material You dynamic color (already enabled)

---

## File Structure

### New files

| File | Responsibility |
|------|---------------|
| `ui/theme/Tokens.kt` | `RealmsSpacing` object + `RealmsElevation` object |
| `ui/components/RealmsCard.kt` | Card variants (filled/outlined/elevated) wrapping M3 Card |
| `ui/components/RealmsProgressBar.kt` | Unified progress bar wrapping M3 LinearProgressIndicator |
| `ui/components/SectionHeader.kt` | Section label replacing SectionCap + inline duplicates |
| `ui/components/StatusTag.kt` | Compact pill badge for status/relationship/rarity |
| `ui/components/EmptyState.kt` | Centered empty-list placeholder (moved from PanelShared) |
| `ui/components/FilterTabRow.kt` | Pill-style filter tabs (moved from PanelShared) |
| `ui/components/PanelSheet.kt` | Modal bottom sheet wrapper (moved from PanelShared) |
| `ui/components/WealthBars.kt` | 5-segment gold bar indicator (moved from PanelShared) |

### Modified files

| File | What changes |
|------|-------------|
| `ui/theme/Extended.kt` | Add `npcPalette` + `scrimOverlay` fields |
| `util/Markdown.kt` | (no change — just receives `formatSigned` neighbor) |
| 8 panel files | Import from `ui.components`, replace Surface→RealmsCard, inline shapes→tokens, inline padding→tokens |
| 10 game screen files | Same replacements + NPC palette migration, progress bar unification |
| 3 overlay files | Same replacements |
| 5 setup/map files | Same replacements + graveyard gradient, purple highlight, private SectionHeader deletion |

### Deleted files

| File | Reason |
|------|--------|
| `ui/panels/PanelShared.kt` | All composables moved to `ui/components/` |

---

## Phase 0: Foundation

### Task 1: Create Tokens.kt

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/theme/Tokens.kt`

- [ ] **Step 1: Create Tokens.kt with RealmsSpacing and RealmsElevation**

```kotlin
package com.realmsoffate.game.ui.theme

import androidx.compose.ui.unit.dp

object RealmsSpacing {
    val xxs = 2.dp
    val xs  = 4.dp
    val s   = 8.dp
    val m   = 12.dp
    val l   = 16.dp
    val xl  = 20.dp
    val xxl = 24.dp
}

object RealmsElevation {
    val low    = 2.dp
    val medium = 6.dp
    val high   = 10.dp
}
```

- [ ] **Step 2: Verify it compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

---

### Task 2: Add npcPalette and scrimOverlay to Extended.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/theme/Extended.kt`

- [ ] **Step 1: Add new fields to RealmsExtendedColors data class**

Add after `systemOnBubble: Color` (line 41):

```kotlin
    val scrimOverlay: Color,
    val npcPalette: List<Pair<Color, Color>>
```

- [ ] **Step 2: Add values to DarkExtended**

Add after `systemOnBubble = Color(0xFF9E9AA8)` (line 69):

```kotlin
    scrimOverlay = Color(0xB3000000),
    npcPalette = listOf(
        Color(0xFF4A9E5E) to Color(0xFF1B3D23),
        Color(0xFF5B7FC7) to Color(0xFF1C2D4A),
        Color(0xFFD4A843) to Color(0xFF3D3118),
        Color(0xFFC44040) to Color(0xFF3D1818),
        Color(0xFF8B6CC7) to Color(0xFF2D1F42),
        Color(0xFF4AA8A8) to Color(0xFF1A3636),
        Color(0xFFCC6633) to Color(0xFF3D2010),
        Color(0xFFAA44AA) to Color(0xFF361836),
        Color(0xFF6A9E3A) to Color(0xFF223312),
        Color(0xFF5577CC) to Color(0xFF1A2540),
    )
```

- [ ] **Step 3: Add values to LightExtended**

Add after `systemOnBubble = Color(0xFF49454F)` (line 97):

```kotlin
    scrimOverlay = Color(0xB3000000),
    npcPalette = listOf(
        Color(0xFF2D6B3A) to Color(0xFFDFF0E3),
        Color(0xFF3A5A9E) to Color(0xFFDDE6F5),
        Color(0xFF8A6E1A) to Color(0xFFF5EDD0),
        Color(0xFF9E2020) to Color(0xFFF5D8D8),
        Color(0xFF6A4A9E) to Color(0xFFEADFF5),
        Color(0xFF2D7A7A) to Color(0xFFD8F0F0),
        Color(0xFF9E4A1A) to Color(0xFFF5E0D0),
        Color(0xFF8A2D8A) to Color(0xFFF0D8F0),
        Color(0xFF4A7A20) to Color(0xFFE0EDCF),
        Color(0xFF3A5599) to Color(0xFFD8E0F5),
    )
```

- [ ] **Step 4: Verify it compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

---

### Task 3: Create RealmsCard.kt

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/components/RealmsCard.kt`

- [ ] **Step 1: Create RealmsCard component**

```kotlin
package com.realmsoffate.game.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.ui.theme.RealmsSpacing

@Composable
internal fun RealmsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    outlined: Boolean = false,
    accentColor: Color? = null,
    selected: Boolean = false,
    elevation: Dp = 0.dp,
    contentPadding: Dp = RealmsSpacing.m,
    content: @Composable ColumnScope.() -> Unit
) {
    val backgroundColor = when {
        selected && accentColor != null -> accentColor.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val border = when {
        outlined && accentColor != null && selected -> BorderStroke(1.dp, accentColor)
        outlined && accentColor != null -> BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
        else -> null
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = backgroundColor,
            tonalElevation = elevation,
            border = border,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = backgroundColor,
            tonalElevation = elevation,
            border = border,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

---

### Task 4: Create RealmsProgressBar.kt

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/components/RealmsProgressBar.kt`

- [ ] **Step 1: Create RealmsProgressBar component**

```kotlin
package com.realmsoffate.game.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun RealmsProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.extraSmall),
        color = color,
        trackColor = trackColor,
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

---

### Task 5: Create SectionHeader.kt

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/components/SectionHeader.kt`

- [ ] **Step 1: Create SectionHeader component**

```kotlin
package com.realmsoffate.game.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.realmsoffate.game.ui.theme.RealmsSpacing

@Composable
internal fun SectionHeader(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.padding(vertical = RealmsSpacing.xs)
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

---

### Task 6: Create StatusTag.kt

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/components/StatusTag.kt`

- [ ] **Step 1: Create StatusTag component**

```kotlin
package com.realmsoffate.game.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.realmsoffate.game.ui.theme.RealmsSpacing

@Composable
internal fun StatusTag(
    label: String,
    color: Color
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xxs)
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

---

### Task 7: Move EmptyState, FilterTabRow, PanelSheet, WealthBars to ui/components/

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/components/EmptyState.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/components/FilterTabRow.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/components/PanelSheet.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/components/WealthBars.kt`

- [ ] **Step 1: Create EmptyState.kt**

```kotlin
package com.realmsoffate.game.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.realmsoffate.game.ui.theme.RealmsSpacing

@Composable
internal fun EmptyState(icon: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(RealmsSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(RealmsSpacing.s))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 2: Create FilterTabRow.kt**

```kotlin
package com.realmsoffate.game.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.realmsoffate.game.ui.theme.RealmsSpacing

@Composable
internal fun FilterTabRow(
    tabs: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        Modifier
            .padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.xs)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.s)
    ) {
        tabs.forEachIndexed { i, (label, icon) ->
            val selected = i == selectedIndex
            Surface(
                onClick = { onSelect(i) },
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    Modifier.padding(horizontal = RealmsSpacing.s, vertical = RealmsSpacing.xs),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(icon, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(RealmsSpacing.xs))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Create PanelSheet.kt**

```kotlin
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.realmsoffate.game.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.realmsoffate.game.ui.theme.RealmsSpacing

@Composable
internal fun PanelSheet(
    title: String,
    subtitle: String? = null,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = RealmsSpacing.l, vertical = RealmsSpacing.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(RealmsSpacing.xs))
        content()
        Spacer(Modifier.navigationBarsPadding().height(RealmsSpacing.m))
    }
}
```

- [ ] **Step 4: Create WealthBars.kt**

```kotlin
package com.realmsoffate.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.ui.theme.RealmsTheme

@Composable
internal fun WealthBars(wealth: Int) {
    val realms = RealmsTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(RealmsSpacing.xxs)) {
        repeat(5) { i ->
            Box(
                Modifier
                    .width(14.dp)
                    .height(RealmsSpacing.xs)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (i < wealth) realms.goldAccent
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
            )
        }
    }
}
```

- [ ] **Step 5: Verify all compile**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

---

### Task 8: Move formatSigned to util, delete PanelShared.kt

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/util/Format.kt`
- Delete: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/PanelShared.kt`

- [ ] **Step 1: Create Format.kt**

```kotlin
package com.realmsoffate.game.util

fun formatSigned(n: Int): String = if (n >= 0) "+$n" else n.toString()
```

- [ ] **Step 2: Delete PanelShared.kt**

Delete the file `app/src/main/kotlin/com/realmsoffate/game/ui/panels/PanelShared.kt`.

**Do NOT delete yet** — the panel files still reference the old composables by same-package unqualified call. Proceed to Phase 1, which updates all panel files to import from `ui.components` and `util`. After Phase 1 is complete, delete PanelShared.kt.

Actually: since all composables in PanelShared are `internal` to `ui.panels` and accessed by same-package unqualified call, the panel files will get compilation errors once we delete PanelShared. The correct order is:

1. Create Format.kt (this step)
2. Complete Phase 1 (all panel files add imports from `ui.components` and `util`)
3. Delete PanelShared.kt (Task 9, after Phase 1)

- [ ] **Step 3: Verify Format.kt compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL` (PanelShared still exists, panels still reference it, no errors)

---

### Task 9: Commit Phase 0 foundation

- [ ] **Step 1: Commit all new files**

Run:
```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/theme/Tokens.kt \
       app/src/main/kotlin/com/realmsoffate/game/ui/theme/Extended.kt \
       app/src/main/kotlin/com/realmsoffate/game/ui/components/ \
       app/src/main/kotlin/com/realmsoffate/game/util/Format.kt
git commit -m "Phase 0: Add design system tokens and shared component library

Add RealmsSpacing + RealmsElevation tokens, npcPalette + scrimOverlay
to extended colors, and 8 shared components in ui/components/ (RealmsCard,
RealmsProgressBar, SectionHeader, StatusTag, EmptyState, FilterTabRow,
PanelSheet, WealthBars). Move formatSigned to util/Format.kt."
```

---

## Phase 1: Migrate Panels (8 files, all independent — run in parallel)

Every panel file gets the same set of changes:
1. Add imports from `com.realmsoffate.game.ui.components.*` and `com.realmsoffate.game.util.formatSigned` (where used)
2. Replace `SectionCap(...)` → `SectionHeader(...)`
3. Replace `FilterTabs(...)` → `FilterTabRow(...)`
4. Replace `Surface(...) { Column(...) { ... } }` card patterns → `RealmsCard(...) { ... }`
5. Replace `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*` per the token mapping
6. Replace inline `padding(N.dp)` → `RealmsSpacing.*` (nearest token)
7. Replace `LinearProgressIndicator` (if any) → `RealmsProgressBar`
8. Remove unused `import ...RoundedCornerShape`

### Task 10: Migrate InventoryPage.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/InventoryPage.kt`

- [ ] **Step 1: Add imports**

Add at top of file:
```kotlin
import com.realmsoffate.game.ui.components.EmptyState
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

- [ ] **Step 2: Apply mechanical replacements**

Throughout the file:
- `SectionCap(` → `SectionHeader(`
- `RoundedCornerShape(14.dp)` → `MaterialTheme.shapes.medium`
- `RoundedCornerShape(10.dp)` → `MaterialTheme.shapes.small`
- `padding(12.dp)` → `padding(RealmsSpacing.m)`
- `padding(14.dp)` → `padding(RealmsSpacing.m)`
- `padding(horizontal = 14.dp)` → `padding(horizontal = RealmsSpacing.l)`
- Replace card-like `Surface(onClick, color = surfaceVariant.copy(...), shape = ...) { Column(Modifier.padding(...)) { ... } }` with `RealmsCard(onClick = ..., outlined = hasAccentBorder, accentColor = rarityColor, selected = isSelected) { ... }`
- Where a `Surface` has a `.border(1.dp, color, shape)` modifier, switch to `RealmsCard(outlined = true, accentColor = color)`
- Remove unused `import androidx.compose.foundation.shape.RoundedCornerShape`

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 11: Migrate QuestsPage.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/QuestsPage.kt`

- [ ] **Step 1: Add imports**

```kotlin
import com.realmsoffate.game.ui.components.EmptyState
import com.realmsoffate.game.ui.components.FilterTabRow
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.components.StatusTag
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

- [ ] **Step 2: Apply mechanical replacements**

- `FilterTabs(` → `FilterTabRow(`
- `RoundedCornerShape(16.dp)` → `MaterialTheme.shapes.medium`
- `RoundedCornerShape(10.dp)` → `MaterialTheme.shapes.small`
- Replace quest card `Surface` with `RealmsCard(outlined = true, accentColor = accent)`
- Inline status pill composable → `StatusTag(status.label, accent)`
- Replace inline padding values with `RealmsSpacing.*`
- Remove unused shape import

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 12: Migrate PartyPage.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/PartyPage.kt`

- [ ] **Step 1: Add imports**

```kotlin
import com.realmsoffate.game.ui.components.EmptyState
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

- [ ] **Step 2: Apply mechanical replacements**

- `RoundedCornerShape(16.dp)` → `MaterialTheme.shapes.medium`
- Replace party member `Surface` with `RealmsCard`
- `padding(14.dp)` → `padding(RealmsSpacing.m)`
- `Spacer(Modifier.width(10.dp))` → `Spacer(Modifier.width(RealmsSpacing.s))`
- Remove unused shape import

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 13: Migrate LorePage.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/LorePage.kt`

- [ ] **Step 1: Add imports**

```kotlin
import com.realmsoffate.game.ui.components.EmptyState
import com.realmsoffate.game.ui.components.FilterTabRow
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.components.WealthBars
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.util.formatSigned
```

- [ ] **Step 2: Apply mechanical replacements**

- `SectionCap(` → `SectionHeader(`
- `FilterTabs(` → `FilterTabRow(`
- All `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- All inline padding → `RealmsSpacing.*`
- Card-like `Surface` → `RealmsCard`
- Inline section labels (`Text("REALM", style = labelLarge, color = primary)`) → `SectionHeader("REALM")`
- **Keep** era-divider color overrides: `SectionHeader(text, color = realms.goldAccent)` etc.
- Remove unused shape import

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 14: Migrate NpcJournalPage.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/NpcJournalPage.kt`

- [ ] **Step 1: Add imports**

```kotlin
import com.realmsoffate.game.ui.components.EmptyState
import com.realmsoffate.game.ui.components.FilterTabRow
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.components.StatusTag
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

- [ ] **Step 2: Apply mechanical replacements**

- `FilterTabs(` → `FilterTabRow(`
- NPC list item `Surface` → `RealmsCard(onClick = ...)`
- NPC detail card `Surface` → `RealmsCard(outlined = true, accentColor = primary)`
- Inline section labels (APPEARANCE, YOUR ASSESSMENT, RECENT DIALOGUE) → `SectionHeader(...)`
- Relationship tag → `StatusTag(relationship, color)`
- `RoundedCornerShape(14.dp)` → `MaterialTheme.shapes.medium`
- `RoundedCornerShape(16.dp)` → `MaterialTheme.shapes.medium`
- All inline padding → `RealmsSpacing.*`
- Remove unused shape import

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 15: Migrate CurrencyPage.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/CurrencyPage.kt`

- [ ] **Step 1: Add imports**

```kotlin
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.components.WealthBars
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.util.formatSigned
```

- [ ] **Step 2: Apply mechanical replacements**

- `SectionCap(` → `SectionHeader(`
- All `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- Card-like `Surface` → `RealmsCard`
- All inline padding → `RealmsSpacing.*`
- Remove unused shape import

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 16: Migrate SpellsPage.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/SpellsPage.kt`

- [ ] **Step 1: Add imports**

```kotlin
import com.realmsoffate.game.ui.components.EmptyState
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

- [ ] **Step 2: Apply mechanical replacements**

- Spell card `Surface` → `RealmsCard(onClick = onClick, outlined = true, accentColor = if (selected) primary else outlineVariant, selected = selected)`
- Spell detail card `Surface` → `RealmsCard`
- `RoundedCornerShape(12.dp)` → `MaterialTheme.shapes.medium`
- `RoundedCornerShape(10.dp)` → `MaterialTheme.shapes.small`
- Button `shape = RoundedCornerShape(10.dp)` → `shape = MaterialTheme.shapes.small`
- All inline padding → `RealmsSpacing.*`
- Remove unused shape import

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 17: Migrate StatsPage.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/StatsPage.kt`

- [ ] **Step 1: Add imports**

```kotlin
import com.realmsoffate.game.ui.components.PanelSheet
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.components.RealmsProgressBar
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.util.formatSigned
```

- [ ] **Step 2: Apply mechanical replacements**

- `SectionCap(` → `SectionHeader(`
- Replace morality `LinearProgressIndicator(progress = { pct.coerceIn(...) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)), color = mcolor, trackColor = ...)` → `RealmsProgressBar(progress = pct, color = mcolor)`
- All `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- Card-like `Surface` → `RealmsCard`
- All inline padding → `RealmsSpacing.*`
- Remove unused shape, clip imports

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 18: Delete PanelShared.kt and commit Phase 1

- [ ] **Step 1: Delete PanelShared.kt**

Delete: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/PanelShared.kt`

- [ ] **Step 2: Verify full compilation**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL` — all panels now import from `ui.components`

- [ ] **Step 3: Commit**

```bash
git add -A app/src/main/kotlin/com/realmsoffate/game/ui/panels/
git commit -m "Phase 1: Migrate all panels to shared components and tokens

All 8 panel files now import from ui/components/ instead of PanelShared.
Replace Surface-as-card with RealmsCard, inline shapes with
MaterialTheme.shapes, inline padding with RealmsSpacing, SectionCap
with SectionHeader, FilterTabs with FilterTabRow. Delete PanelShared.kt."
```

---

## Phase 2: Migrate Game Screen (10 files, all independent — run in parallel)

### Task 19: Migrate MessageBubbles.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt`

- [ ] **Step 1: Replace npcColorPalette with RealmsTheme.colors.npcPalette**

Delete the `npcColorPalette` val (lines 67–78) and update `npcColor()` (line 81):

```kotlin
internal fun npcColor(name: String): Pair<Color, Color> {
    val palette = RealmsTheme.colors.npcPalette
    if (name.isBlank()) return palette[0]
    val idx = (name.lowercase().hashCode() and 0x7FFFFFFF) % palette.size
    return palette[idx]
}
```

Note: `npcColor` is called from a `@Composable` context (inside bubble composables), so `RealmsTheme.colors` is accessible. If it's called from a non-composable context, pass the palette as a parameter instead.

- [ ] **Step 2: Apply mechanical replacements**

- All `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- All inline padding → `RealmsSpacing.*`
- `Color.Black.copy(alpha = ...)` scrim usages → `RealmsTheme.colors.scrimOverlay` (if present)
- Remove unused imports

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 20: Migrate TopBar.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/TopBar.kt`

- [ ] **Step 1: Add imports and replace progress bars**

Add:
```kotlin
import com.realmsoffate.game.ui.components.RealmsProgressBar
import com.realmsoffate.game.ui.theme.RealmsElevation
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

Replace HP bar (lines 230–235):
```kotlin
// Before:
LinearProgressIndicator(
    progress = { pct },
    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
    color = color,
    trackColor = MaterialTheme.colorScheme.surfaceVariant
)

// After:
RealmsProgressBar(progress = pct, color = color)
```

Replace XP bar (lines 256–261):
```kotlin
// Before:
LinearProgressIndicator(
    progress = { pct },
    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)),
    color = MaterialTheme.colorScheme.secondary,
    trackColor = MaterialTheme.colorScheme.surfaceVariant
)

// After:
RealmsProgressBar(
    progress = pct,
    color = MaterialTheme.colorScheme.secondary,
    height = 4.dp
)
```

- [ ] **Step 2: Apply remaining mechanical replacements**

- `tonalElevation = 2.dp` → `tonalElevation = RealmsElevation.low`
- All `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- All inline padding → `RealmsSpacing.*`
- Remove unused imports (`LinearProgressIndicator`, `RoundedCornerShape`, `clip`)

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 21: Migrate CombatHud.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/CombatHud.kt`

- [ ] **Step 1: Replace Box-in-Box with RealmsProgressBar**

Add import:
```kotlin
import com.realmsoffate.game.ui.components.RealmsProgressBar
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

Replace (lines 140–153):
```kotlin
// Before:
Box(
    Modifier.width(60.dp).height(4.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
) {
    Box(
        Modifier.fillMaxWidth(pct).fillMaxHeight()
            .background(hpColor)
    )
}

// After:
RealmsProgressBar(
    progress = pct,
    color = hpColor,
    height = 4.dp,
    modifier = Modifier.width(60.dp)
)
```

- [ ] **Step 2: Apply remaining mechanical replacements**

- All `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- All inline padding → `RealmsSpacing.*`
- Remove unused imports

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 22: Migrate GameDialogs.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameDialogs.kt`

- [ ] **Step 1: Apply mechanical replacements**

Add imports:
```kotlin
import com.realmsoffate.game.ui.theme.RealmsElevation
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

- `tonalElevation = 10.dp` → `tonalElevation = RealmsElevation.high`
- `RoundedCornerShape(24.dp)` → `MaterialTheme.shapes.large`
- `padding(horizontal = 22.dp, vertical = 22.dp)` → `padding(RealmsSpacing.xxl)`
- All other inline padding → `RealmsSpacing.*`
- Remove unused imports

- [ ] **Step 2: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 23: Migrate GameOverlays.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameOverlays.kt`

- [ ] **Step 1: Apply mechanical replacements**

Add imports:
```kotlin
import com.realmsoffate.game.ui.theme.RealmsElevation
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

- `tonalElevation = 8.dp` → `tonalElevation = RealmsElevation.medium`
- All `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- All inline padding → `RealmsSpacing.*`
- `Color.Black.copy(alpha = 0.7f)` → `RealmsTheme.colors.scrimOverlay` (where used as scrim backdrop)
- Remove unused imports

- [ ] **Step 2: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 24: Migrate ChatInput.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatInput.kt`

- [ ] **Step 1: Apply mechanical replacements**

Add import:
```kotlin
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.ui.theme.RealmsElevation
```

- `tonalElevation = 4.dp` → `tonalElevation = RealmsElevation.low`
- All `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- All inline padding → `RealmsSpacing.*`
- Remove unused imports

- [ ] **Step 2: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 25: Migrate GameScreen.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

- [ ] **Step 1: Apply mechanical replacements**

Add import:
```kotlin
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

- All `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- All inline padding → `RealmsSpacing.*`
- Remove unused imports

- [ ] **Step 2: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 26: Migrate NarrationBlock.kt, ChatFeed.kt, BottomNav.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/BottomNav.kt`

- [ ] **Step 1: Apply mechanical replacements to all three**

For each file:
- Add `import com.realmsoffate.game.ui.theme.RealmsSpacing` (and `RealmsElevation` if elevation values are present)
- Replace all `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- Replace all inline padding → `RealmsSpacing.*`
- Replace `Color.Black.copy(alpha = ...)` → `RealmsTheme.colors.scrimOverlay` where appropriate
- Remove unused imports

- [ ] **Step 2: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 27: Commit Phase 2

- [ ] **Step 1: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/
git commit -m "Phase 2: Migrate game screen files to tokens and shared components

Replace Surface-as-card with RealmsCard, inline shapes with
MaterialTheme.shapes, inline padding with RealmsSpacing, progress bars
with RealmsProgressBar, elevation with RealmsElevation. Move NPC color
palette to RealmsExtendedColors."
```

---

## Phase 3: Migrate Overlays (3 files, all independent — run in parallel)

### Task 28: Migrate Overlays.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/overlays/Overlays.kt`

- [ ] **Step 1: Apply mechanical replacements**

Add imports:
```kotlin
import com.realmsoffate.game.ui.theme.RealmsElevation
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

- `tonalElevation = 6.dp` → `tonalElevation = RealmsElevation.medium`
- `RoundedCornerShape(24.dp)` → `MaterialTheme.shapes.large`
- `RoundedCornerShape(12.dp)` → `MaterialTheme.shapes.medium`
- `padding(horizontal = 24.dp, vertical = 22.dp)` → `padding(RealmsSpacing.xxl)`
- All other inline padding → `RealmsSpacing.*`
- **Keep campfire Canvas colors inline** (lines ~290-430 — flame/sky/ember colors are not semantic)
- Remove unused imports

- [ ] **Step 2: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 29: Migrate ShopOverlay.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/overlays/ShopOverlay.kt`

- [ ] **Step 1: Apply mechanical replacements**

Add imports:
```kotlin
import com.realmsoffate.game.ui.components.RealmsCard
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

- Replace shop item `Surface` cards → `RealmsCard`
- All `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- Button `shape = RoundedCornerShape(10.dp)` → `shape = MaterialTheme.shapes.small`
- All inline padding → `RealmsSpacing.*`
- Remove unused imports

- [ ] **Step 2: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 30: Migrate TargetPromptDialog.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/overlays/TargetPromptDialog.kt`

- [ ] **Step 1: Apply mechanical replacements**

Add import:
```kotlin
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

- All `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- All inline padding → `RealmsSpacing.*`
- Remove unused imports

- [ ] **Step 2: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 31: Commit Phase 3

- [ ] **Step 1: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/overlays/
git commit -m "Phase 3: Migrate overlays to tokens and shared components

Replace inline shapes, padding, and elevation with design system tokens.
Shop items use RealmsCard. Campfire Canvas colors remain inline."
```

---

## Phase 4: Migrate Setup + Map (5 files, all independent — run in parallel)

### Task 32: Migrate CharacterCreationScreen.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/setup/CharacterCreationScreen.kt`

- [ ] **Step 1: Delete private SectionHeader, add import for shared one**

Delete lines 531–534:
```kotlin
@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
```

Add import:
```kotlin
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

All existing `SectionHeader(...)` calls continue to work — they now resolve to the shared component.

- [ ] **Step 2: Apply mechanical replacements**

- `RoundedCornerShape(14.dp)` → `MaterialTheme.shapes.medium`
- `RoundedCornerShape(18.dp)` → `MaterialTheme.shapes.medium` (snap to nearest token)
- Button `shape = RoundedCornerShape(14.dp)` → `shape = MaterialTheme.shapes.medium`
- `padding(horizontal = 22.dp)` → `padding(horizontal = RealmsSpacing.xxl)`
- All other inline padding → `RealmsSpacing.*`
- Remove unused imports

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 33: Migrate TitleScreen.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/setup/TitleScreen.kt`

- [ ] **Step 1: Replace graveyard gradient hardcoded colors**

Add imports:
```kotlin
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

Replace (lines 497–499):
```kotlin
// Before:
Brush.verticalGradient(
    listOf(Color(0xFF1A0A0A), Color(0xFF2D1111), MaterialTheme.colorScheme.surface)
)

// After:
Brush.verticalGradient(
    listOf(
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.surface
    )
)
```

- [ ] **Step 2: Apply mechanical replacements**

- `RoundedCornerShape(24.dp)` → `MaterialTheme.shapes.large`
- `RoundedCornerShape(18.dp)` → `MaterialTheme.shapes.medium`
- `RoundedCornerShape(14.dp)` → `MaterialTheme.shapes.medium`
- `padding(24.dp)` → `padding(RealmsSpacing.xxl)`
- `padding(horizontal = 22.dp)` → `padding(horizontal = RealmsSpacing.xxl)`
- All other inline padding → `RealmsSpacing.*`
- Remove unused imports

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 34: Migrate DeathScreen.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/setup/DeathScreen.kt`

- [ ] **Step 1: Replace private formatSigned with import, add SectionHeader**

Add imports:
```kotlin
import com.realmsoffate.game.ui.components.SectionHeader
import com.realmsoffate.game.ui.theme.RealmsSpacing
import com.realmsoffate.game.util.formatSigned
```

Delete the private `formatSigned` copy (line 245):
```kotlin
private fun formatSigned(n: Int) = if (n >= 0) "+$n" else n.toString()
```

- [ ] **Step 2: Apply mechanical replacements**

- Inline section label `Text("WORLD CONDITIONS", style = labelLarge, color = primary)` → `SectionHeader("WORLD CONDITIONS")`
- Same for FELLOW TRAVELLERS, UNFINISHED STORY, LIFE STORY
- Button `shape = RoundedCornerShape(14.dp)` → `shape = MaterialTheme.shapes.medium`
- All other `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- All inline padding → `RealmsSpacing.*`
- Remove unused imports

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 35: Migrate ApiSetupScreen.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/setup/ApiSetupScreen.kt`

- [ ] **Step 1: Replace hardcoded purple with theme token**

Add import:
```kotlin
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

Replace both instances of `Color(0xFFB197FF)`:

Line 79: `color = Color(0xFFB197FF)` → `color = MaterialTheme.colorScheme.secondary`
Line 105: `tint = Color(0xFFB197FF)` → `tint = MaterialTheme.colorScheme.secondary`

- [ ] **Step 2: Apply mechanical replacements**

- `RoundedCornerShape(14.dp)` → `MaterialTheme.shapes.medium`
- `padding(14.dp)` → `padding(RealmsSpacing.m)`
- All other inline padding → `RealmsSpacing.*`
- Card-like `Surface` → `RealmsCard` (if applicable)
- Remove unused imports

- [ ] **Step 3: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 36: Migrate WorldMapScreen.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/map/WorldMapScreen.kt`

- [ ] **Step 1: Apply mechanical replacements**

Add imports:
```kotlin
import com.realmsoffate.game.ui.theme.RealmsElevation
import com.realmsoffate.game.ui.theme.RealmsSpacing
```

- Elevation values → `RealmsElevation.*` (e.g. `tonalElevation = 6.dp` → `RealmsElevation.medium`)
- `Color.Black.copy(alpha = ...)` (scrim backdrop, if present) → `RealmsTheme.colors.scrimOverlay`
- All `RoundedCornerShape(N.dp)` → `MaterialTheme.shapes.*`
- All inline padding → `RealmsSpacing.*`
- **Keep road Canvas colors inline** (terrain painting, not semantic UI)
- Remove unused imports

- [ ] **Step 2: Verify compiles**

Run: `gradle compileDebugKotlin 2>&1 | tail -5`

---

### Task 37: Commit Phase 4

- [ ] **Step 1: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/setup/ \
       app/src/main/kotlin/com/realmsoffate/game/ui/map/
git commit -m "Phase 4: Migrate setup screens and map to tokens and shared components

Delete private SectionHeader in CharacterCreation (use shared).
Replace graveyard gradient hardcoded colors with theme tokens.
Replace ApiSetup purple highlight with colorScheme.secondary.
Delete private formatSigned in DeathScreen (use util/Format.kt).
Replace inline shapes, padding, elevation throughout."
```

---

## Phase 5: Final Verification

### Task 38: Run full test suite and build

- [ ] **Step 1: Run tests**

Run: `gradle test 2>&1 | tail -20`
Expected: All tests pass (no behavior changes — pure appearance refactor)

- [ ] **Step 2: Build and install**

Run: `gradle installDebug && adb shell am start -n com.realmsoffate.game/.MainActivity`
Expected: App launches, all screens render correctly

- [ ] **Step 3: Verify no remaining hardcoded shapes**

Run: `grep -rn "RoundedCornerShape(" app/src/main/kotlin/com/realmsoffate/game/ui/ --include="*.kt" | grep -v "theme/" | grep -v "Canvas" | head -20`
Expected: Zero results outside theme files and Canvas drawing code

- [ ] **Step 4: Verify no remaining PanelShared references**

Run: `grep -rn "PanelShared\|SectionCap\|FilterTabs(" app/src/main/kotlin/ --include="*.kt" | head -10`
Expected: Zero results (all renamed/moved)
