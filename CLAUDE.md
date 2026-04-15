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

## Deploying to the phone

The paired Android target is a Galaxy S23 (`SM-S916U`) on the local LAN. A fish function `phone` is installed at `~/.config/fish/functions/phone.fish` that wraps the full cycle: reconnect if needed → `./gradlew installDebug` → launch `MainActivity`. Run it from the repo root:

```
phone
```

### Under the hood

The function:
1. Checks `adb devices` for at least one entry in `device` state.
2. If none, runs `adb connect 192.168.68.64:37865` (the last-known ADB port).
3. If still no device, prints reconnection instructions and exits non-zero.
4. Runs `./gradlew installDebug`.
5. Runs `adb shell am start -n com.realmsoffate.game/.MainActivity`.

### When the port rotates

Android's wireless ADB port changes on reboots, Wi-Fi drops, or toggling the Wireless debugging switch. When `phone` reports it can't reach the phone:

1. Open **Settings → System → Developer options → Wireless debugging** on the phone.
2. Note the current port shown at the top of that screen.
3. Either run once-off: `adb connect 192.168.68.64:<new-port>`
4. Or edit `~/.config/fish/functions/phone.fish` and update `phone_port` so the reconnect path stays automatic.

The one-time pairing (via **Pair device with pairing code**) does NOT need to be redone — pairing identity persists across ports. Only re-pair if you wipe the phone, reset developer options, or hit "Revoke authorizations".

## Debugging workflow

The in-game Debug button (More menu → Debug) dumps the full game state + AI exchange log to the app's scoped external storage on the phone:

```
/sdcard/Android/data/com.realmsoffate.game/files/debug/debug_<character>_T<turn>_<epoch>.txt
```

No file picker — one tap writes the dump. To pull it to the PC, run the fish function from anywhere inside the repo:

```
pulldebug
```

It yanks the newest file into `<repo-root>/debug-dumps/` (gitignored) and prints the local path. Falls back to `~/Downloads/` only when run from outside a git repo. Needs the same wireless ADB pairing that `phone` uses.

### For Claude sessions

When the user asks to "check the latest debug dump" or similar, the freshest dump is always at:

```
ls -t debug-dumps/debug_*.txt 2>/dev/null | head -1
```

Read that file directly — no need to ask the user for the path. Files are named `debug_<characterslot>_T<turn>_<epoch>.txt` so newest-by-mtime is also newest-by-turn for a given character.

## Project Structure

- `app/src/main/kotlin/com/realmsoffate/game/data/` — Data layer (AI providers, models, preferences, prompts, tag parsing)
- `app/src/main/kotlin/com/realmsoffate/game/game/` — Game logic (viewmodel, classes, races, scenarios, world events, lore, feats)
- `app/src/main/kotlin/com/realmsoffate/game/ui/` — UI layer (game screen, map, overlays, panels, setup screens)
- `app/src/main/kotlin/com/realmsoffate/game/util/` — Utilities (markdown rendering)
