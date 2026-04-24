# Chat Declutter — Implementation Plan

Reference mockup: [`2026-04-23-chat-declutter-mockup.html`](./2026-04-23-chat-declutter-mockup.html) (clean version)

## Strategy

The declutter breaks into **7 independent slices**. Each touches a narrow region of the UI layer and can be executed by a separate Claude instance with the self-contained prompt below. No slice depends on another's implementation — only on their non-interference. All slices leave `BubbleFrame` signature compatible (new params default to preserving old behavior) so parallel work doesn't collide.

**Suggested ordering if running sequentially:** D → B → C → E → A → F → G (low-risk visual first, structural last). Parallel is fine too; each slice's verification catches its own regressions.

**Deploy + verify contract for every slice:**
1. Edit the files listed.
2. `gradle assembleDebug` must pass.
3. `gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735`
4. Run P0+P1 from `.cursor/rules/debug-bridge-test-procedures.mdc` — confirm a full turn renders without crashes and the slice's specific visual change is present.
5. Commit with a focused message referencing the slice letter.

Never claim runtime verification from a compile-only pass. If the emulator isn't available, say so explicitly.

---

## Slice A — Bubble labels dropped, avatars removed

**Files:** `app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt`

**Current state:**
- `BubbleFrame` (lines 98–161) renders a 36dp gradient avatar circle + uppercase label row above content.
- `PlayerBubble` (187–225) passes `avatarInitial`, `label = displayName`.
- `NpcDialogueBubble` (331–370) passes `avatarInitial`, `label = name`.

**Target state:**
- No avatars in the feed. No standalone label row.
- For NPC bubbles: the speaker's name appears **inline** as the first token of the quote text, colored with the NPC accent, weight 600, not italic. The rest of the quote stays italic.
- For player bubbles: no label. Right-alignment of the row already signals ownership. Max-width ~85%.
- An NPC bubble gets a 6dp leading accent-colored dot outside the bubble (small vertical row with the dot at top, bubble flex=1).
- Tail-corner treatment on the bubble stays.

**Prompt for the slice instance:**

> You are implementing Slice A of a chat UI declutter for a Kotlin/Compose Android RPG at `/mnt/GD2/Backup/Documents/Repositories/RealmsAndroid`.
>
> Goal: remove avatars and uppercase labels from player and NPC dialogue bubbles. Replace NPC identity with an inline colored name at the start of the quote plus a 6dp leading accent dot outside the bubble. Player bubbles lose their label entirely; right-alignment signals ownership.
>
> Files to edit: `app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt` only.
>
> Specifically:
> 1. Simplify `BubbleFrame` (lines 98–161): keep the tail-corner shape logic and surface/border, but stop rendering `BubbleAvatar` and the label row. You can leave the `avatarInitial`/`label` params in place with `_ =` suppression or delete them — deleting is cleaner since you own all callers in this file.
> 2. Update `PlayerBubble` (187–225): remove `avatarInitial`, `label`, `avatarOnRight` args from the `BubbleFrame` call. Wrap the bubble in a `Row(horizontalArrangement = Arrangement.End)` with a `Modifier.fillMaxWidth(0.85f)` on the bubble. Keep the italic right-aligned text and tail-corner on top-right.
> 3. Update `NpcDialogueBubble` (331–370): wrap the bubble in a `Row` with a 6dp `Box` dot (accent color) at top-left, small vertical padding to align with first line. Prepend the NPC name inline with the quote: `Text` using an `AnnotatedString` where `name + " "` has `color = accent, fontWeight = 600, fontStyle = Normal` and the rest is the existing italic parseInline result. Drop the `label` param from `BubbleFrame`.
> 4. Keep `npcColor` and `resolveNpcDisplayName` unchanged.
>
> Do **not** touch `NarrationBlock.kt`, `ChatFeed.kt`, or any other file. Other slices are editing those.
>
> After editing: `gradle assembleDebug`, then deploy and verify per CLAUDE.md P0+P1. Confirm that (a) no avatar circles appear, (b) no uppercase labels appear, (c) NPC name shows inline in accent color, (d) player bubble is right-aligned without a label. Commit with message `refactor(ui): drop bubble avatars + labels, inline NPC names (slice A)`.

**Size:** Small (~80 LOC delta).

---

## Slice B — Narrator prose: strip chrome, replace with left rail

**Files:** `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt`

**Current state:** `NarratorProseBubble` (192–246) renders a Surface with border, a centered header row (book icon + "NARRATOR" label + expand chevron), and the prose below. Always collapsible; `isLatestTurn` pre-expands.

**Target state:** No Surface. No header row. No chevron. The prose text renders directly in a Column with a 2dp left border stroke (secondary color) and 16dp start padding. Body text remains `NarrationMarkdown`. Italic asides inside the prose keep their existing rendering. Drop the expand/collapse interaction entirely — prose is always visible.

**Prompt for the slice instance:**

> You are implementing Slice B of a chat UI declutter for a Kotlin/Compose Android RPG at `/mnt/GD2/Backup/Documents/Repositories/RealmsAndroid`.
>
> Goal: strip the narrator prose bubble of its Surface/border/header chrome. Replace with a left-rail accent line and always-visible prose. Remove the expand/collapse toggle — it's meaningless when latest turns are pre-expanded anyway.
>
> Files to edit: `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt` only.
>
> Specifically:
> 1. Rewrite `NarratorProseBubble` (192–246) as a `Column` with `Modifier.fillMaxWidth().drawBehind { /* 2dp left line in secondary color */ }.padding(start = 16.dp, vertical = 8.dp)`. Or use a `Row` with a `Box(Modifier.width(2.dp).fillMaxHeight().background(secondary))` followed by the prose column.
> 2. Remove the header `Row` (icon + "NARRATOR" + chevron).
> 3. Remove `var expanded` state. Delete the `Surface(onClick = { expanded = !expanded })` wrapper.
> 4. The `NarrationMarkdown` call and font-scale logic stay.
> 5. The `isLatestTurn` param can be dropped from the signature if no callers still need it — check `NarrationBlock.kt:59–63` (the only caller). Leaving it unused is fine.
>
> Do **not** touch `ActionPill`, `NarratorAsideLine`, `NpcActionLine`, `PlayerActionLine`, or the legacy `splitNarration` path. Other slices handle those.
>
> After editing: `gradle assembleDebug`, then deploy and verify per CLAUDE.md P0+P1. Confirm (a) no book icon or "NARRATOR" header appears, (b) no chevron, (c) prose renders with a subtle left rule, (d) long prose still renders in full. Commit `refactor(ui): narrator prose as left-rail, drop header chrome (slice B)`.

**Size:** Small (~40 LOC delta).

---

## Slice C — Action pills → inline italic lines with leading dot

**Files:** `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt`

**Current state:** `ActionPill` (467–501) is a full-width Surface with 12% tinted background, 12dp icon, and italic text. Used by `NpcActionLine`, `NarratorAsideLine`, `PlayerActionLine` with three different accents.

**Target state:** No Surface, no background tint, no icon. Each variant becomes an inline `Row` with a 4dp circular dot (variant accent) + italic onSurfaceVariant text at 12.5sp. Same fillMaxWidth, left-aligned.

**Prompt for the slice instance:**

> You are implementing Slice C of a chat UI declutter for a Kotlin/Compose Android RPG at `/mnt/GD2/Backup/Documents/Repositories/RealmsAndroid`.
>
> Goal: replace the three `ActionPill` variants (NPC action, narrator aside, player action) with a single lightweight inline line style — italic muted text preceded by a 4dp colored dot indicating the variant. No icons, no background fills.
>
> Files to edit: `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt` only.
>
> Specifically:
> 1. Replace the `ActionPill` composable (lines 467–501) with a new `InlineActionLine(text, accent)` that renders: `Row(verticalAlignment = CenterVertically) { Box(size=4.dp, circle, background=accent); Spacer(6.dp); Text(text, fontStyle=Italic, fontSize=12.5sp, color=onSurfaceVariant) }` wrapped with `Modifier.fillMaxWidth().padding(horizontal=4.dp, vertical=2.dp)`.
> 2. Rewire the three callers:
>    - `NpcActionLine(text, accentColor)` → `InlineActionLine(text, accentColor)`
>    - `NarratorAsideLine(text)` → `InlineActionLine(text, MaterialTheme.colorScheme.secondary)`
>    - `PlayerActionLine(text)` → `InlineActionLine(text, MaterialTheme.colorScheme.tertiary)`
> 3. Drop the unused `Icon`, `DirectionsRun`, `AutoAwesome`, `Bolt`, `TextAlign` imports if they're no longer referenced in the file (check; some may still be needed for the collapse chevron area if Slice B isn't yet merged — safe to leave imports in place).
>
> Do **not** touch `NarratorProseBubble` (Slice B), `StatChangePills`, or `BubbleFrame`.
>
> After editing: `gradle assembleDebug`, then deploy and verify per CLAUDE.md P0+P1. Trigger a turn with narrator asides and NPC actions (the `[Narrator aside]` test prompt in P1 covers this). Confirm (a) no tinted pill backgrounds, (b) 4dp colored dots appear at the left of each action line, (c) text reads as italic muted. Commit `refactor(ui): action lines as inline dot + italic (slice C)`.

**Size:** Small (~60 LOC delta).

---

## Slice D — Turn divider → silent hairline gap

**Files:** `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt`

**Current state:** `TurnDivider(turnNumber)` at 164–187 renders two horizontal rules with centered "TURN N" text, called for every narration after the first.

**Target state:** Replace with a 40dp wide × 2dp tall centered hairline `Box` in `outlineVariant` color. No text. Same insertion point.

**Prompt for the slice instance:**

> You are implementing Slice D of a chat UI declutter for a Kotlin/Compose Android RPG at `/mnt/GD2/Backup/Documents/Repositories/RealmsAndroid`.
>
> Goal: replace the full-width "TURN N" divider with a tiny centered hairline gap. Turn count is noise; the visual flow change is sufficient wayfinding.
>
> Files to edit: `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt` only.
>
> Specifically:
> 1. Replace `TurnDivider(turnNumber)` at lines 164–187 with a `TurnGap()` composable that renders a `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Box(Modifier.size(width=40.dp, height=2.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.6f))) }` with `padding(vertical=6.dp)`.
> 2. Update the caller at line 75 — drop the `turnNumber` computation at 74 entirely. Call `TurnGap()`.
> 3. Remove unused imports (`sp` may no longer be needed if no other users remain).
>
> Do **not** touch anything else in the file (narration dispatch, merchant chips, error card).
>
> After editing: `gradle assembleDebug`, then deploy and verify per CLAUDE.md P0+P1. Confirm the "TURN 2", "TURN 3", etc. labels are gone and replaced with a short centered hairline between turns. Commit `refactor(ui): turn divider as hairline gap (slice D)`.

**Size:** Small (~20 LOC delta).

---

## Slice E — Stat pills collapsed to text strip + check inlined

**Files:** `app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt` and `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt`

**Current state:**
- `StatChangePills` (229–329) builds a `FlowRow` of Surface chips with emoji glyphs (♥ 💰 ★ ⚖ 💡) — up to 8+ chips per turn.
- `SystemLine` (477–499) renders check PASS/FAIL as its own center-aligned Surface row, dispatched from `ChatFeed` at line 92 via `DisplayMessage.System`.

**Target state:**
- `StatChangePills` renders as a single `Text` line with dot-separated tokens — red-tinted for losses, green-tinted for gains, muted for neutral. No emoji. No chip shapes.
- The check PASS/FAIL appears as a **leading inline pill** on that same stat-strip line rather than as a standalone `SystemLine`. When no stat changes happen, the check chip still renders as a compact standalone row.
- Overflow behavior: if item gain/loss lists exceed 3 entries each, truncate to `+Silver Vial, +Folded Note, +2 more…` with `+2 more` tappable (stub the click for now).

**Prompt for the slice instance:**

> You are implementing Slice E of a chat UI declutter for a Kotlin/Compose Android RPG at `/mnt/GD2/Backup/Documents/Repositories/RealmsAndroid`.
>
> Goal: collapse the stat-change pill cloud into one muted dot-separated text strip, and attach check PASS/FAIL results to the same strip as a leading colored chip instead of rendering them as standalone center rows.
>
> Files to edit:
> - `app/src/main/kotlin/com/realmsoffate/game/ui/game/MessageBubbles.kt` (rewrite `StatChangePills`, simplify `SystemLine`)
> - `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt` (change how `DisplayMessage.System` is dispatched so check lines attach to the preceding narration block)
>
> Specifically:
> 1. Rewrite `StatChangePills(msg)` (MessageBubbles.kt:229–329): build an `AnnotatedString` of segments, each a `String` + color (red for HP loss / item loss, green-ish tertiary for HP gain / XP / items gained / conditions, secondary for gold, muted default for moral/rep). Join with `" · "` in `outline` color. Render as a single `Text(style = labelSmall, fontVariantNumeric = tabular if available, modifier = fillMaxWidth, padding(horizontal=18.dp, vertical=4.dp))`. No `Surface` wrappers, no emoji.
>    - Item gains/losses: cap at first 3; if more, append `, +N more…` as a clickable span (hook up to an empty lambda for now — future work can wire to inventory).
> 2. Change the check-result flow. In `ChatFeed.kt` at the dispatch on line 92, when a `DisplayMessage.System` follows a `DisplayMessage.Narration`, suppress the standalone `SystemLine` and attach the check text as a leading chip to the preceding narration's stat strip. The cleanest way: give `DisplayMessage.Narration` a transient `checkResult: String?` field at render time. But don't touch the state shape — instead, in `ChatFeed.kt`, look ahead/behind: when rendering a `DisplayMessage.Narration`, pass the next message's text (if it's a System line starting with ✓ or ✗) as a `checkText: String?` param to `NarrationBlock`, and skip rendering that System line in the next iteration. Use a `skipNext` boolean tracked outside the `itemsIndexed` loop — or easier: transform `state.messages` into a derived list where trailing check Systems are folded into their preceding Narration, computed in a `remember(state.messages) { ... }` block.
>    - Pass the folded check text through `NarrationBlock` → `StatChangePills` (new param `checkText: String?`).
>    - Render the check as a pill at the start of the strip: green tint for ✓, red tint for ✗.
> 3. `SystemLine` stays in place for non-check System messages (merchant open/close, save notices, etc.). Only the ✓/✗ variants fold in.
>
> Do **not** change the state types or reducers. This is a pure render-time fold.
>
> After editing: `gradle assembleDebug`, then deploy and verify per CLAUDE.md P0+P1. Trigger a check-heavy turn (DC roll) and a loot-heavy turn (merchant transaction). Confirm (a) stat changes appear as one inline text strip with dot separators, no chip surfaces, no emoji, (b) check result shows as a colored leading chip on the strip not a separate row, (c) non-check system lines still render their own row. Commit `refactor(ui): stat strip + inline check chip (slice E)`.

**Size:** Medium (~150 LOC delta, two files, one behavior change in the dispatch).

**Risk:** Medium. The check-fold logic is the only non-trivial piece. If it fails, the check line double-renders (once folded, once standalone) or disappears entirely. The slice's verification catches both.

---

## Slice F — Past-turn dimming

**Files:** `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt` and `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt`

**Current state:** Every turn renders at full opacity. `isLatestTurn` is passed to `NarrationBlock` at `ChatFeed.kt:83` but only used to gate swipe interactivity and (previously) the prose expand state.

**Target state:** Past turns render at 55% opacity on the narrator prose, NPC dialogue bubbles, and action lines. Player bubbles stay at full opacity (they're the user's own voice, shouldn't fade). Stat strips also fade. Only the latest turn is full brightness.

**Prompt for the slice instance:**

> You are implementing Slice F of a chat UI declutter for a Kotlin/Compose Android RPG at `/mnt/GD2/Backup/Documents/Repositories/RealmsAndroid`.
>
> Goal: dim past turns to 55% opacity so the latest beat reads as the visually brightest content. Player bubbles (the user's own voice) stay at full opacity regardless.
>
> Files to edit:
> - `app/src/main/kotlin/com/realmsoffate/game/ui/game/NarrationBlock.kt`
> - `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt` (already passes `isLatestTurn`; verify)
>
> Specifically:
> 1. In `NarrationBlock`, wrap the non-player content in a `Box(Modifier.alpha(if (isLatestTurn) 1f else 0.55f))`. Structured-segments path at lines 54–119: wrap each branch's composable call **except** `NarrationSegmentData.PlayerAction` and `NarrationSegmentData.PlayerDialog` with the alpha. The legacy path at 123–184: same pattern — prose bubble, NPC dialogue, asides get dimmed; player dialogue does not.
> 2. Simpler alternative: wrap the whole `Column` in `NarrationBlock` (line 53) with `Modifier.alpha(if (isLatestTurn) 1f else 0.55f)`, then for each `PlayerDialog`/`PlayerBubble` branch inside, wrap that specific composable with `Modifier.alpha(1f / if (isLatestTurn) 1f else 0.55f)` to counter-scale back to 1.0. Note: `alpha` multiplies, so wrapping a child in `alpha(1f / 0.55f)` overshoots alpha > 1, which clamps. **Don't do this — the clamp masks the intent.** Use the per-branch approach.
> 3. Also dim the `StatChangePills` output when `!isLatestTurn`. Add an `isLatest: Boolean` param to `StatChangePills` or wrap the call site.
> 4. In `ChatFeed.kt`, confirm `isLatestTurn` at line 83 correctly flags the last narration. It currently handles the case where a System line trails the narration. After Slice E folds check Systems, revisit: you may want `isLatestTurn = (idx == state.messages.lastIndex)` simplified. Don't over-correct — keep existing logic if Slice E hasn't merged yet.
>
> Do **not** touch the `PlayerBubble` composable itself (Slice A handles its structure). Just apply alpha at the call site inside `NarrationBlock`.
>
> After editing: `gradle assembleDebug`, then deploy and verify per CLAUDE.md P0+P1. Trigger at least 3 turns. Confirm (a) earlier turns visibly dimmed, (b) latest turn fully bright, (c) player bubbles (the user's own dialogue) stay bright in past turns too. Commit `refactor(ui): dim past turns, keep player voice bright (slice F)`.

**Size:** Small (~50 LOC delta).

---

## Slice G — Merchant chips → footer dock above input

**Files:** `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt` and `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt`

**Current state:** `ChatFeed.kt:101–127` appends a `Row` of `AssistChip(Store)` items inside the LazyColumn whenever `state.availableMerchants.isNotEmpty()`. So every turn in a town shows the chip row again in the feed.

**Target state:** Remove the LazyColumn row. Instead, render a single persistent dock pill above the `ChatInput` — a `Surface` with an icon, "Shops nearby (N)" text, and a trailing chevron, tappable to open a merchant picker (reuse whatever the `AssistChip` onClick does today). If there's one merchant, tapping opens it directly; if multiple, open a small bottom-sheet picker (or — for this slice — just route to the first merchant and leave multi-merchant picker as a TODO comment).

**Prompt for the slice instance:**

> You are implementing Slice G of a chat UI declutter for a Kotlin/Compose Android RPG at `/mnt/GD2/Backup/Documents/Repositories/RealmsAndroid`.
>
> Goal: move the per-turn merchant chip row out of the chat feed and into a persistent footer dock that appears above the chat input when merchants are available.
>
> Files to edit:
> - `app/src/main/kotlin/com/realmsoffate/game/ui/game/ChatFeed.kt` (remove the inline chip row)
> - `app/src/main/kotlin/com/realmsoffate/game/ui/game/GameScreen.kt` (add the footer dock above ChatInput)
>
> Before editing, read `GameScreen.kt` to understand the current layout — the file is 375 lines. Find where `ChatInput` is composed and where `onOpenShop` is passed through.
>
> Specifically:
> 1. In `ChatFeed.kt`, delete lines 101–127 (the `if (state.availableMerchants.isNotEmpty() && !state.isGenerating)` item block). Remove `onOpenShop` from `ChatFeed`'s parameter list if no other callers use it inside the feed — grep first.
> 2. In `GameScreen.kt`, add a new composable `MerchantDock(merchants: List<String>, onOpen: (String) -> Unit)` that renders a `Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, outlineVariant), modifier = Modifier.padding(horizontal=12.dp, vertical=4.dp).fillMaxWidth())` containing a `Row` with a shop icon, `"Shops nearby: ${merchants.joinToString(" · ")}"` text, and a trailing chevron. Tap behavior: if `merchants.size == 1`, call `onOpen(merchants[0])`; else call `onOpen(merchants[0])` and add a `// TODO: multi-merchant picker sheet` comment.
> 3. In `GameScreen.kt`, render `MerchantDock` conditionally (`if (state.availableMerchants.isNotEmpty() && !state.isGenerating)`) **immediately above** the `ChatInput` composable, not inside it.
> 4. Thread `onOpenShop` down to `MerchantDock` from the existing parent.
>
> Do **not** change the `availableMerchants` state or the shop-opening logic in the VM. Pure UI move.
>
> After editing: `gradle assembleDebug`, then deploy and verify per CLAUDE.md P0+P1. Enter a town scene (use the debug macro if available). Confirm (a) no merchant chips appear in the feed after a turn, (b) a persistent "Shops nearby" dock appears above the chat input, (c) tapping the dock opens the shop dialog. Commit `refactor(ui): merchant dock above input, drop inline chips (slice G)`.
>
> Known caveat: if a turn has only just introduced a merchant, the dock is what the player sees — no celebratory in-feed beat. Acceptable tradeoff for the declutter; flag to the user if the change feels too quiet.

**Size:** Medium (~120 LOC delta, two files).

---

## Dependencies and merge order

No slice reads or writes the same line another does. Safe parallel breakdown:

```
Pod 1 (visual-low-risk):    D  (ChatFeed turn gap)
Pod 2 (bubbles):             A  (MessageBubbles frame)
Pod 3 (narrator):            B  (NarrationBlock prose)
Pod 4 (actions):             C  (NarrationBlock action lines)
Pod 5 (state strip):         E  (MessageBubbles + ChatFeed fold)
Pod 6 (dimming):             F  (NarrationBlock alpha) — merge AFTER A, B, C
Pod 7 (dock):                G  (ChatFeed + GameScreen)
```

Slice F should land last because it applies alpha to call sites modified by A/B/C; merging F first means F touches old code that A/B/C then rewrite, creating merge conflicts.

Slice E touches `ChatFeed.kt` in the dispatch loop. Slice D also touches `ChatFeed.kt` but only the `TurnDivider` function — no overlap. Slice G also touches `ChatFeed.kt` but only the merchant-chips block (lines 101–127) — also no overlap.

If conflicts still arise, they're small (tens of lines) and local.

## After all slices merge

Open [`2026-04-23-chat-declutter-mockup.html`](./2026-04-23-chat-declutter-mockup.html) next to the emulator for a final visual gut-check. If the real app matches the clean mockup's feel, declutter is done. If any slice feels too bare in context (e.g., NPC identity is hard to read without labels), tune that one slice rather than backing out the set.

Taste calls still open (from mockup notes) — decide before or after shipping:
- Avatars: gone entirely (current plan) vs. keep 20dp on latest turn only.
- Turn gap: silent hairline (current plan) vs. tiny "T7" superscript for deep scrollback.
- Past-turn dimming threshold: always-on (current plan) vs. only when feed > N turns.
- Stat strip: dot-separated text (current plan) vs. emoji-only (no words).
