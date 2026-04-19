# Realms of Fate — Android

Kotlin + Jetpack Compose RPG. Package **`com.realmsoffate.game`**. Gradle Kotlin DSL.

## Project layout

- `app/src/main/kotlin/com/realmsoffate/game/data/` — AI providers, models, preferences, prompts, tag parsing
- `app/src/main/kotlin/com/realmsoffate/game/game/` — ViewModel, classes, races, scenarios, world events, lore, feats
- `app/src/main/kotlin/com/realmsoffate/game/game/reducers/` — Pure reducers (character, combat, NPC log, quest/party, world)
- `app/src/main/kotlin/com/realmsoffate/game/game/handlers/` — Merchant, rest, save, progression
- `app/src/main/kotlin/com/realmsoffate/game/ui/` — Game shell, overlays, panels, setup screens
- `app/src/main/kotlin/com/realmsoffate/game/util/` — Markdown and helpers
- `app/src/debug/kotlin/com/realmsoffate/game/debug/` — Debug Bridge (HTTP, macros, describe) — **debug builds only**
- `app/src/test/kotlin/com/realmsoffate/game/` — JVM tests (Robolectric); see [testing.mdc](.cursor/rules/testing.mdc)

## Environment and Gradle

First-time setup (Arch Linux): `./setup-env.sh` — installs JDK, Android SDK, emulator, Node.js; creates **Pixel7** AVD (API 36.1 x86_64 Google APIs). Requires **KVM**.

```bash
gradle assembleDebug
gradle assembleRelease
gradle lint
gradle test
gradle clean
```

If Gradle cannot write `~/.android`, use `ANDROID_USER_HOME=<project>/.android-user`.

## Shell execution

Run any shell command that does not require `sudo`. Execute commands directly rather than suggesting them. Do not assume `sudo` permission unless the user explicitly requests it.

## Build and deploy (always)

Unless the user explicitly skips deploy, after edits under `app/src/main/`, `app/src/debug/`, `app/build.gradle.kts`, Android resources, or project Gradle/settings that affect `app`:

1. Deploy at the **end** of the work session, then run Debug Bridge verification per [debug-bridge-test-procedures.mdc](.cursor/rules/debug-bridge-test-procedures.mdc) (minimum **P0 + P1**; add contextual steps from the table there):

```bash
cd /path/to/RealmsAndroid && gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735
```

2. Use `adb devices` first; if no emulator, mention starting the Pixel7 AVD (`emulator -avd Pixel7`).
3. Do **not** stop at `assembleDebug` or compile-only when changes touch runnable app code.

## Commit after verification

When this session modified tracked files and verification has **succeeded**:

1. Commit before ending the response — unless the user asked not to commit ("don't commit", "WIP", "leave uncommitted").
2. Do **not** commit broken work: if tests fail, build fails, or verification did not pass, fix first or stop without committing.
3. For **documentation or Cursor rules only** (no app/runtime verification required), commit when work is complete.

Use a clear commit message: short imperative subject line; optional body if multiple concerns.

## Task workflow

### Step 1 — Triage

| Priority | Criteria | Examples |
|----------|----------|---------|
| **High** | Architecture, multi-file refactors, 3+ files, deep bugs | New system, data rework, complex UI flow |
| **Normal** | 1-2 files, straightforward fixes, new screen/component | Button, crash fix, prompt tweak |
| **Low** | Cosmetic, copy, tiny cleanup | Typo, padding |

Present a short checklist before starting.

### Step 2 — Execute

- **High first** — subdivide by layer (data / VM / UI). Parallelize only where dependencies allow.
- **Normal next** — sequential unless independent.
- **Low last** — quick passes.

### Step 3 — Tests

After substantive changes: `gradle test`. If failures: diagnose, fix code or update tests — **do not delete tests** to green. Add regression tests for logic bugs. Retry up to ~3 cycles; then stop and report. See [testing.mdc](.cursor/rules/testing.mdc) for what to test by change type.

### Step 4 — Deploy and verify

Follow the build/deploy section above plus [debug-bridge-verify.mdc](.cursor/rules/debug-bridge-verify.mdc) and [debug-bridge-test-procedures.mdc](.cursor/rules/debug-bridge-test-procedures.mdc). Do not claim runtime verification from compile/tests alone when app sources changed.

### Step 5 — Finish

Confirm tests + deploy/verification before saying work is complete. Mark checklist items done; end with a short summary.

Update **README.md** only when user-visible structure, game systems, build requirements, or stack changed.

## Skills and tools routing

| Situation | Skill / tool |
|-----------|--------------|
| New feature or system design | `superpowers:brainstorming` then `writing-plans` |
| Bug / failing test | `superpowers:systematic-debugging` |
| Compose UI build or redesign | `frontend-design:frontend-design` |
| Multi-file feature | `feature-dev:feature-dev` or `writing-plans` |
| Parallel independent tasks | `dispatching-parallel-agents` / subagents |
| Game mechanics / reducers / parser | `test-driven-development` |
| Before claiming done | `verification-before-completion` |
| Commit / PR | `commit-commands:*` |
| Major feature / merge | `requesting-code-review`, `finishing-a-development-branch` |
| PR feedback | `receiving-code-review` |
| Git isolation | `using-git-worktrees` |
| Live library docs | **Context7** MCP (`plugin-context7-context7`) |

Code review / hooks (`code-review:code-review`, `pr-review-toolkit:review-pr`, `hookify:*`) — use when the user wants automation, not by default.

## Reference rules (read on demand)

These contain specialized procedures — read them when the task requires it:

- [debug-bridge-reference.mdc](.cursor/rules/debug-bridge-reference.mdc) — endpoints, devices, fish helpers, HTTP API sketch
- [debug-bridge-test-procedures.mdc](.cursor/rules/debug-bridge-test-procedures.mdc) — P0-P9 named verification procedures and contextual table
- [debug-bridge-verify.mdc](.cursor/rules/debug-bridge-verify.mdc) — when/how to verify via Debug Bridge, exceptions
- [testing.mdc](.cursor/rules/testing.mdc) — JVM test layout, fixtures, when to add tests
- [releases-signing.mdc](.cursor/rules/releases-signing.mdc) — semver, release protocol, CI signing
