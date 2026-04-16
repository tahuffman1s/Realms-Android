# UI Phase 1: Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish a clean Material You design system foundation — semantic color tokens, correct typography, theme-aware markdown rendering — so all subsequent UI phases build on consistent tokens instead of hardcoded values.

**Architecture:** Add bubble palette tokens to `Extended.kt`, fix fonts and type scale in `Fonts.kt`/`Type.kt`, make markdown renderer theme-aware, fix activity theme, then sweep hardcoded colors in GameScreen.kt and Panels.kt. Each task is independently committable.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Google Fonts

---

### Task 1: Add Bubble Palette Tokens to Extended.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/theme/Extended.kt`

- [ ] **Step 1: Add bubble color fields to `RealmsExtendedColors`**

Add these fields after `rarityLegendary` (line 31):

```kotlin
    val narratorBubble: Color,
    val narratorOnBubble: Color,
    val npcBubble: Color,
    val npcOnBubble: Color,
    val playerBubble: Color,
    val playerOnBubble: Color,
    val asideBubble: Color,
    val asideOnBubble: Color,
    val systemBubble: Color,
    val systemOnBubble: Color,
```

- [ ] **Step 2: Add dark values to `DarkExtended`**

Add after `rarityLegendary = Color(0xFFFFD76A)` (line 49):

```kotlin
    narratorBubble = Color(0xFF2A262F),
    narratorOnBubble = Color(0xFFE8E1F0),
    npcBubble = Color(0xFF1E2A3A),
    npcOnBubble = Color(0xFFD0E0F0),
    playerBubble = Color(0xFF3A2C55),
    playerOnBubble = Color(0xFFE4D7FF),
    asideBubble = Color(0xFF1A1030),
    asideOnBubble = Color(0xFFB197FF),
    systemBubble = Color(0x1AFFFFFF),
    systemOnBubble = Color(0xFF9E9AA8),
```

- [ ] **Step 3: Add light values to `LightExtended`**

Add after `rarityLegendary = Color(0xFFB8891A)` (line 67):

```kotlin
    narratorBubble = Color(0xFFF0EDE6),
    narratorOnBubble = Color(0xFF1C1B1F),
    npcBubble = Color(0xFFE3ECF5),
    npcOnBubble = Color(0xFF1A2533),
    playerBubble = Color(0xFFEADDFF),
    playerOnBubble = Color(0xFF21005D),
    asideBubble = Color(0xFFE8E1F0),
    asideOnBubble = Color(0xFF6750A4),
    systemBubble = Color(0x1A000000),
    systemOnBubble = Color(0xFF49454F),
```

- [ ] **Step 4: Run build to verify compilation**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/theme/Extended.kt
git commit -m "Add bubble palette tokens to RealmsExtendedColors"
```

---

### Task 2: Fix Typography and Fonts

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/theme/Fonts.kt:36`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/theme/Type.kt:31-32`

- [ ] **Step 1: Remove fake SemiBold from CrimsonTextFontFamily**

In `Fonts.kt`, replace lines 33-38:

```kotlin
val CrimsonTextFontFamily: FontFamily = FontFamily(
    Font(googleFont = crimsonText, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = crimsonText, fontProvider = googleFontProvider, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(googleFont = crimsonText, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = crimsonText, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)
```

With:

```kotlin
val CrimsonTextFontFamily: FontFamily = FontFamily(
    Font(googleFont = crimsonText, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = crimsonText, fontProvider = googleFontProvider, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(googleFont = crimsonText, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)
```

- [ ] **Step 2: Differentiate headlineSmall and titleLarge**

In `Type.kt`, line 31 currently has `headlineSmall` at `18.sp` (same as `titleLarge` on line 32). Change line 31:

```kotlin
    headlineSmall = TextStyle(fontFamily = TitleSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.5.sp),
```

This gives `headlineSmall = 20sp` and `titleLarge = 18sp` — proper hierarchy.

- [ ] **Step 3: Run build to verify**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/theme/Fonts.kt app/src/main/kotlin/com/realmsoffate/game/ui/theme/Type.kt
git commit -m "Fix typography: remove fake SemiBold, differentiate headlineSmall/titleLarge"
```

---

### Task 3: Fix Activity Theme

**Files:**
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-night/themes.xml` (if exists, otherwise just values/)

- [ ] **Step 1: Change the activity theme parent**

In `app/src/main/res/values/themes.xml`, find:

```xml
    <style name="Theme.RealmsOfFate.Main" parent="android:Theme.Material.Light.NoActionBar">
```

Replace with:

```xml
    <style name="Theme.RealmsOfFate.Main" parent="android:Theme.Material.NoActionBar">
```

This prevents the white flash on dark-preference devices. The `windowBackground` is already set to `@color/bg_dark`.

If there is a `values-night/themes.xml`, make the same change there. If the second themes.xml file already uses `Theme.Material.NoActionBar`, leave it.

- [ ] **Step 2: Run build to verify**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/
git commit -m "Fix activity theme parent to prevent light flash on dark devices"
```

---

### Task 4: Make Markdown Renderer Theme-Aware

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/util/Markdown.kt`

- [ ] **Step 1: Change `parseInline` to accept Color parameters instead of Long hex**

Replace the function signature and the three hardcoded color usages. Replace the entire `parseInline` function (lines 137-196):

```kotlin
fun parseInline(
    s: String,
    boldColor: Color = Color.Unspecified,
    italicColor: Color = Color.Unspecified,
    codeBackground: Color = Color.Unspecified,
    codeText: Color = Color.Unspecified
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        // ***bold italic***
        if (i + 2 < s.length && s[i] == '*' && s[i + 1] == '*' && s[i + 2] == '*') {
            val end = s.indexOf("***", i + 3)
            if (end > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = boldColor)) {
                    append(s.substring(i + 3, end))
                }
                i = end + 3; continue
            }
        }
        // **bold**
        if (i + 1 < s.length && s[i] == '*' && s[i + 1] == '*') {
            val end = s.indexOf("**", i + 2)
            if (end > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                    append(s.substring(i + 2, end))
                }
                i = end + 2; continue
            }
        }
        // *italic* — only match if this is NOT an unclosed ** that fell through
        if (s[i] == '*' && !(i + 1 < s.length && s[i + 1] == '*')) {
            val end = s.indexOf('*', i + 1)
            if (end > 0) {
                withStyle(SpanStyle(
                    fontStyle = FontStyle.Italic,
                    color = italicColor
                )) {
                    append(s.substring(i + 1, end))
                }
                i = end + 1; continue
            }
        }
        // ~~strikethrough~~
        if (i + 1 < s.length && s[i] == '~' && s[i + 1] == '~') {
            val end = s.indexOf("~~", i + 2)
            if (end > 0) {
                withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                    append(s.substring(i + 2, end))
                }
                i = end + 2; continue
            }
        }
        // `code`
        if (s[i] == '`') {
            val end = s.indexOf('`', i + 1)
            if (end > 0) {
                withStyle(SpanStyle(background = codeBackground, fontFamily = FontFamily.Monospace, color = codeText)) {
                    append(s.substring(i + 1, end))
                }
                i = end + 1; continue
            }
        }
        append(s[i])
        i++
    }
}
```

Key changes:
- Parameters are `Color` instead of `Long`
- Default is `Color.Unspecified` (transparent/inherit) instead of hardcoded hex
- The `*italic*` branch adds a guard: `!(i + 1 < s.length && s[i + 1] == '*')` to prevent unclosed `**` from falling through

- [ ] **Step 2: Update the caller in `NarrationMarkdown` to pass theme colors**

In the same file, update all calls to `parseInline(...)` inside `NarrationMarkdown` to pass theme-derived colors. The `NarrationMarkdown` composable already has `val realms = RealmsTheme.colors` and access to `MaterialTheme.colorScheme`.

After `val realms = RealmsTheme.colors` (line 39), add:

```kotlin
    val boldColor = realms.goldAccent
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val codeText = MaterialTheme.colorScheme.onSurfaceVariant
```

Then find-and-replace every `parseInline(` call inside `NarrationMarkdown` that doesn't already pass colors, and change them to:

```kotlin
parseInline(lineContent, boldColor = boldColor, codeBackground = codeBackground, codeText = codeText)
```

There are 7 calls to `parseInline` in the file (lines 48, 59, 71, 105, 118, 127, and the `else` branch). Update all of them.

- [ ] **Step 3: Add the `Color` import if not already present**

Make sure `import androidx.compose.ui.graphics.Color` is at the top of the file (it already is at line 8, so this should be fine).

- [ ] **Step 4: Run build to verify**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run tests to verify no regressions**

Run: `gradle test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/util/Markdown.kt
git commit -m "Make markdown renderer theme-aware: replace hardcoded colors with tokens"
```

---

### Task 5: Replace Hardcoded Colors in GameScreen.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

This is a mechanical find-and-replace task. The goal is to replace every `Color(0xFF...)` literal with a semantic token from `RealmsTheme.colors` or `MaterialTheme.colorScheme`.

- [ ] **Step 1: Map each hardcoded color to its replacement**

Use this mapping for all replacements:

| Hardcoded | Semantic replacement | Used for |
|-----------|---------------------|----------|
| `Color(0xFF1A1030)` | `realms.asideBubble` | Dark overlay backgrounds |
| `Color(0xFFE8E1F0)` | `MaterialTheme.colorScheme.onSurface` | Text on dark surfaces |
| `Color(0xFFB197FF)` | `MaterialTheme.colorScheme.secondary` | Purple accent / borders |
| `Color(0xFF9E9AA8)` | `MaterialTheme.colorScheme.onSurfaceVariant` | Muted text |
| `Color(0xFF81C784)` | `realms.success` | Positive stat deltas |
| `Color(0xFFE57373)` | `realms.fumbleRed` | Negative stat deltas |
| `Color(0xFF64B5F6)` | `realms.info` | Informational deltas |
| `Color(0xFFFF8A65)` | `realms.warning` | Warning deltas |
| `Color(0xFFFF8AC6)` | `MaterialTheme.colorScheme.tertiary` | Charmed condition |
| `Color(0xFF4A9E5E)` / `Color(0xFF1B3D23)` | `realms.success` / `realms.success.copy(alpha = 0.3f)` | Backstory card green |
| `Color(0xFF5B7FC7)` / `Color(0xFF1C2D4A)` | `realms.info` / `realms.info.copy(alpha = 0.3f)` | Backstory card blue |
| `Color(0xFFD4A843)` / `Color(0xFF3D3118)` | `realms.goldAccent` / `realms.goldAccent.copy(alpha = 0.3f)` | Backstory card gold |
| `Color(0xFFC44040)` / `Color(0xFF3D1818)` | `realms.fumbleRed` / `realms.fumbleRed.copy(alpha = 0.3f)` | Backstory card red |
| `Color(0xFF8B6CC7)` / `Color(0xFF2D1F42)` | `MaterialTheme.colorScheme.secondary` / `MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)` | Backstory card purple |
| `Color(0xFF4AA8A8)` / `Color(0xFF1A3636)` | `realms.info.copy(green = 0.66f)` or define a new `teal` token — simplest: use `realms.success` | Backstory card teal |

- [ ] **Step 2: Apply replacements across GameScreen.kt**

Ensure `val realms = RealmsTheme.colors` is available at every scope where replacements are made. If a composable function doesn't already have it, add it at the top of the function.

Replace all ~28 `Color(0xFF...)` occurrences using the mapping above. The backstory color pairs list (lines 2918-2923) should become:

```kotlin
    realms.success to realms.success.copy(alpha = 0.3f),
    realms.info to realms.info.copy(alpha = 0.3f),
    realms.goldAccent to realms.goldAccent.copy(alpha = 0.3f),
    realms.fumbleRed to realms.fumbleRed.copy(alpha = 0.3f),
    MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
    realms.success to realms.success.copy(alpha = 0.3f),
```

Note: the backstory color list is inside a composable context (`@Composable` function), so `MaterialTheme` and `RealmsTheme` are accessible.

- [ ] **Step 3: Run build to verify**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run tests**

Run: `gradle test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Replace hardcoded colors in GameScreen with theme tokens"
```

---

### Task 6: Replace Hardcoded Colors in Panels.kt

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/panels/Panels.kt`

- [ ] **Step 1: Find and replace hardcoded colors**

Search for `Color(0x` in Panels.kt. The main offenders are the 8 `BackstoryCard` calls (around lines 2318-2326) with inline color pairs. Apply the same mapping as Task 5.

Also check for any `Color(0xFFB197FF)`, `Color(0xFFE8E1F0)`, etc. and replace with `MaterialTheme.colorScheme.secondary`, `MaterialTheme.colorScheme.onSurface`, etc.

- [ ] **Step 2: Run build to verify**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run tests**

Run: `gradle test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/panels/Panels.kt
git commit -m "Replace hardcoded colors in Panels with theme tokens"
```

---

### Task 7: Wire LocalFontScale to System Accessibility

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt` (wherever `LocalFontScale` is defined/provided)

- [ ] **Step 1: Find where LocalFontScale is defined and provided**

Search GameScreen.kt for `LocalFontScale`. It should be a `compositionLocalOf` that defaults to `1.0f`. The fix is to multiply the app's custom scale by the system's `LocalConfiguration.current.fontScale` when providing the value.

Find the `CompositionLocalProvider(LocalFontScale provides ...)` call and change the provided value from the app's `fontScale` to `fontScale * LocalConfiguration.current.fontScale`. This makes the app respect BOTH the in-app slider AND the system accessibility setting.

If `LocalFontScale` is provided as just `fontScale` (from the VM's preferences), change to:

```kotlin
val systemScale = LocalConfiguration.current.fontScale
CompositionLocalProvider(LocalFontScale provides fontScale * systemScale) {
```

Add `import android.content.res.Configuration` and `import androidx.compose.ui.platform.LocalConfiguration` if not already present.

- [ ] **Step 2: Run build to verify**

Run: `gradle assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt
git commit -m "Wire LocalFontScale to respect Android system font accessibility"
```

---

### Task 8: Final Verification

- [ ] **Step 1: Run full test suite**

Run: `gradle test`
Expected: All 54 tests pass.

- [ ] **Step 2: Run release build**

Run: `gradle assembleRelease`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Install and verify on device**

Run: `gradle installDebug && adb shell am start -n com.realmsoffate.game/.MainActivity`

Visually verify:
- No white flash on launch (dark theme)
- Narration text uses gold bold, not hardcoded hex gold
- Code spans in markdown use surface variant colors
- GameScreen has no raw purple/dark hex colors visible in light mode
- Font sizes respect system Large Text setting

- [ ] **Step 4: Commit verification note**

No code changes — just confirm everything works. If issues found, fix and commit individually.
