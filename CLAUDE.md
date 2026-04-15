# Realms of Fate — Android

Kotlin + Jetpack Compose RPG game. Package: `com.realmsoffate.game`. Build system: Gradle with Kotlin DSL.

## Task Execution Protocol

When the user gives you work, follow this protocol exactly.

### Step 1 — Triage

Interpret the user's request and break it into discrete tasks. Classify each task into one of three priority tiers:

| Priority | Criteria | Examples |
|----------|----------|---------|
| **High** | Architectural changes, multi-file refactors, new features touching 3+ files, bug fixes that require deep investigation | New game system, data layer rework, complex UI flow |
| **Normal** | Self-contained changes in 1-2 files, straightforward bug fixes, adding a screen or component | Add a button, fix a crash with a known cause, update a prompt |
| **Low** | Cosmetic tweaks, copy changes, formatting, small cleanup | Fix a typo, adjust padding, rename a variable |

Present the triage to the user as a numbered checklist before starting work:

```
## Task Plan
### High Priority
- [ ] 1. <task> — (N agents)
### Normal Priority
- [ ] 2. <task>
### Low Priority
- [ ] 3. <task>
```

### Step 2 — Execute by Priority

**High priority tasks — first, in parallel where possible.**
- Break each high-priority task into independent sub-sections (e.g. data layer, UI, viewmodel).
- Dispatch a separate Claude Sonnet agent (`model: "sonnet"`) for each sub-section using the Agent tool. Run independent agents in parallel within a single message.
- Each agent prompt must be self-contained: include file paths, line numbers, what to change, and why. Do not delegate understanding — prove you understood the problem before writing the prompt.
- After all agents return, review every file they touched by reading the actual changes. Trust but verify.

**Normal priority tasks — second, sequentially.**
- Dispatch a single Claude Sonnet agent (`model: "sonnet"`) for each normal-priority task.
- Same rules: self-contained prompt, verify changes after.

**Low priority tasks — last, do them yourself.**
- Handle these directly without spawning agents. They should be quick.

### Step 3 — Update the Checklist

After completing each task, mark it done and tell the user what changed:

```
- [x] 1. <task> — Done. Changed X, Y, Z.
```

When all tasks are done, print the final checklist so the user sees the full summary.

### Step 4 — Audit & Build

After all tasks are complete, run the audit:

1. **Summarize all changes**: list every file modified or created, with a one-line description of what changed in each.

2. **Build the debug APK**:
   ```
   ./gradlew assembleDebug
   ```

3. **If the build succeeds**: report success and the APK path (`app/build/outputs/apk/debug/`).

4. **If the build fails**: read the full error output, then:
   - Diagnose each error.
   - Dispatch Claude Sonnet agents (`model: "sonnet"`) to fix each independent error in parallel.
   - After fixes land, rebuild.
   - Repeat this diagnose-fix-rebuild loop until the build succeeds.
   - Cap at 5 retry cycles. If still failing after 5 attempts, stop and present the remaining errors to the user.

## Build Commands

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Run lint
./gradlew lint

# Clean
./gradlew clean
```

## Project Structure

- `app/src/main/kotlin/com/realmsoffate/game/data/` — Data layer (AI providers, models, preferences, prompts, tag parsing)
- `app/src/main/kotlin/com/realmsoffate/game/game/` — Game logic (viewmodel, classes, races, scenarios, world events, lore, feats)
- `app/src/main/kotlin/com/realmsoffate/game/ui/` — UI layer (game screen, map, overlays, panels, setup screens)
- `app/src/main/kotlin/com/realmsoffate/game/util/` — Utilities (markdown rendering)
