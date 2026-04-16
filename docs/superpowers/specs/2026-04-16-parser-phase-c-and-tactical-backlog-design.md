# Parser Phase C + Tactical Backlog (1-4) Design

## Overview

Two tracks of work in a single implementation pass:

1. **Parser Phase C** -- substitute NPC display names in `parsed.narration`
2. **Tactical Backlog** -- 4 user-visible bug fixes from playtests

All changes are independent and can be implemented in parallel.

---

## Parser Phase C: NPC Name Substitution in Narration

### Problem

`TagParser.parse()` strips NPC tags from raw AI output to produce
`parsed.narration`, but discards the NPC ref in the process. The narration
strip step (TagParser.kt lines 942-953) replaces `[NPC_ACTION:vesper-saltblood]
stares at the coins[/NPC_ACTION]` with just `"stares at the coins"` -- no
subject.

The in-game UI renders via `parsed.segments` which carry NPC IDs and resolve
display names. But `parsed.narration` is used in debug dumps and the fallback
dialogue regex scan, where the missing subjects look wrong.

### Approach

Post-parse substitution in `applyParsed` (GameViewModel.kt), NOT inside
TagParser. The parser stays stateless; name resolution is a ViewModel concern.

**Location:** After `TagParser.parse()` returns (~line 845) and before
`DisplayMessage.Narration` construction (~line 1082) in `applyParsed`.

**Logic:**

```kotlin
var narration = parsed.narration
for (npc in state.npcLog) {
    if (npc.id.isNotEmpty() && narration.contains(npc.id, ignoreCase = true)) {
        narration = narration.replace(npc.id, npc.name, ignoreCase = true)
    }
}
```

This replaces any slug IDs that survived into narration text with the NPC's
display name. Most slug IDs won't appear in narration (they're stripped by the
tag removal), but when the AI uses them in prose or when partial tag parsing
leaves fragments, this catches them.

**Edge cases:**
- Short IDs that could substring-match normal words: mitigated by the slug
  format (e.g., `vesper-saltblood` contains a hyphen, unlikely to match prose).
- NPC not yet in npcLog: no substitution occurs, which is correct -- we can't
  resolve a name we don't know.

### Testing

Add to `ApplyParsedIntegrationTest`:

1. `narration with NPC slug ID gets display name substituted` -- set up npcLog
   with a known NPC, provide narration containing the slug, verify substitution.
2. `narration without NPC refs unchanged` -- verify no false replacements.

---

## Tactical Backlog Item 1: Quest Objective Near-Duplicates

### Problem

`QuestAndPartyReducer` (line 47) deduplicates objectives with exact
case-insensitive matching. The AI sometimes rephrases objectives:
- "Find someone who studies rifts"
- "Find someone who studies rifts -- the scroll merchant might point you"

The second is a superset of the first, so exact match fails and a near-duplicate
is appended.

### Approach

Substring containment check before the existing exact match.

**Logic in QuestAndPartyReducer:**

```kotlin
val oi = q.objectives.indexOfFirst { it.equals(obj, true) }
```

Becomes:

```kotlin
val exactIdx = q.objectives.indexOfFirst { it.equals(obj, true) }
val subIdx = if (exactIdx < 0) {
    q.objectives.indexOfFirst {
        it.contains(obj, true) || obj.contains(it, true)
    }
} else -1
val oi = if (exactIdx >= 0) exactIdx else subIdx
```

When a substring match is found and the new objective is longer (more detailed),
replace the existing objective text with the new one. Mark it complete in either
case.

### Testing

Add to `ApplyParsedIntegrationTest`:

1. `quest objective superset matches existing and replaces` -- new objective
   contains old text plus more detail. Verify old text replaced, marked complete.
2. `quest objective subset matches existing` -- new objective is shorter version
   of existing. Verify no duplicate added, existing marked complete.

---

## Tactical Backlog Item 2: Merchant Pruning on Scene Change

### Problem

`availableMerchants` is only ever appended to (GameViewModel.kt ~line 1203).
Once a merchant is visited, the shop button persists for the entire session
regardless of where the player travels.

### Approach

Clear `availableMerchants` when the scene changes. In `applyParsed`, when
`parsed.scene` is non-null and differs from `state.scene`, reset
`availableMerchants` to an empty list before processing the current turn's
`[MERCHANT_AVAILABLE:]` tags. New shops emitted in the same turn repopulate
normally.

**Location:** Early in `applyParsed`, before the merchant processing block.

**Logic:**

```kotlin
val sceneChanged = parsed.scene != null && parsed.scene != state.scene
val baseMerchants = if (sceneChanged) emptyList() else state.availableMerchants.toMutableList()
```

Then use `baseMerchants` instead of `state.availableMerchants.toMutableList()`
in the existing merchant accumulation code.

### Testing

Add to `ApplyParsedIntegrationTest`:

1. `scene change clears availableMerchants` -- state has merchants, turn has
   new scene, verify merchants cleared. If same turn has new shop tag, verify
   it's the only merchant in the result.

---

## Tactical Backlog Item 3: Travel Progress Fixed Pace

### Problem

`2 + (roll % 3)` (GameViewModel.kt ~line 1110) ties travel speed to the skill
check die. Identical journeys take different numbers of turns depending on luck.
Travel variance is not a meaningful game mechanic.

### Approach

Replace with a flat 3 leagues per turn.

```kotlin
// Before:
val leaguesThisTurn = 2 + (roll % 3)

// After:
val leaguesThisTurn = 3
```

### Testing

Update the existing travel test in `ApplyParsedIntegrationTest` to assert
fixed 3-league progress regardless of dice result.

---

## Tactical Backlog Item 4: NPC "Unnamed X" Prompt Fix

### Problem

The AI sometimes uses generic slug IDs (`guard-1`, `unnamed-merchant`) in NPC
tags. The auto-stub path in `NpcLogReducer` creates a `LogNpc` with
`name = ref`, so the display name becomes the raw slug.

### Approach

Prompt-only fix. Add one BAD/GOOD example pair to `Prompts.kt` in the NPC
naming rules section:

```
BAD:  [NPC_DIALOG:guard-1]"Halt!"[/NPC_DIALOG]
GOOD: [NPC_DIALOG:harlan-voss]"Halt!"[/NPC_DIALOG]
```

Reinforces that every NPC slug must be a real name, not a generic descriptor.

### Testing

No test needed -- prompt text change only.

---

## File Change Summary

| File | Changes |
|------|---------|
| `GameViewModel.kt` | NPC name substitution in narration; merchant prune on scene change; travel pace fix |
| `QuestAndPartyReducer.kt` | Substring containment dedup for quest objectives |
| `Prompts.kt` | BAD/GOOD example for NPC slug naming |
| `ApplyParsedIntegrationTest.kt` | 6 new test cases |

## Out of Scope

- `logTimeline` VM side-effect refactor (deferred, no user impact)
- `Quest.completed` MutableList immutability (deferred per Strategic Concerns)
- Parser Phase D (gated on data soak)
