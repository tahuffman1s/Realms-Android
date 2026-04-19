# Realms of Fate — Android

Kotlin + Jetpack Compose RPG. `com.realmsoffate.game`. Gradle Kotlin DSL.

## Layout

`app/src/main/kotlin/com/realmsoffate/game/`  
`data/` AI, models, prefs, prompts, parsing · `game/` VM, classes, races, scenarios, events, lore · `game/reducers/` pure reducers · `game/handlers/` merchant, rest, save, progression · `ui/` shell, overlays, panels, setup · `util/` markdown, helpers  
`app/src/debug/.../debug/` Debug Bridge (HTTP, macros) · `app/src/test/` JVM/Robolectric

## Commands

`gradle assembleDebug | assembleRelease | lint | test | clean`  
Setup: `./setup-env.sh` (Arch — JDK, SDK, emulator, Pixel7 AVD, needs KVM)  
Gradle home fix: `ANDROID_USER_HOME=<project>/.android-user`

## Rules

**Shell:** Run commands directly, no `sudo` unless asked.

**Deploy** after edits to `app/src/main/`, `app/src/debug/`, `app/build.gradle.kts`, resources, or app-affecting Gradle (unless user skips):
`gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && adb -s emulator-5554 forward tcp:8735 tcp:8735`
Then [debug-bridge-test-procedures.mdc](.cursor/rules/debug-bridge-test-procedures.mdc) **P0+P1** min + contextual steps. Never claim runtime verification from compile-only.

**Commit** after verification succeeds. Not broken work. Skip if user says WIP. Doc-only: commit when complete.

**Tests:** `gradle test` after substantive changes. Don't delete tests to green. Regression tests for bugs.

## Workflow

Triage (High 3+ files → Normal 1-2 → Low cosmetic), show checklist, execute high-first by layer, test, deploy+verify, finish with summary. README only for user-visible changes.

## Skills

| When | Use |
|------|-----|
| New feature | `brainstorming` → `writing-plans` |
| Bug | `systematic-debugging` |
| Compose UI | `frontend-design` |
| Multi-file | `feature-dev` / `writing-plans` |
| Parallel tasks | `dispatching-parallel-agents` |
| Reducers/parser | `test-driven-development` |
| Before done | `verification-before-completion` |
| Commit/PR | `commit-commands:*` |
| Merge | `requesting-code-review` / `finishing-a-development-branch` |
| Library docs | Context7 MCP |

## Style

Be terse. No trailing summaries. No restating what was just done. Chain bash commands where possible. Use targeted file reads (offset/limit) over full reads. Grep before Read for large files.

## Reference (read on demand)

- [debug-bridge-reference.mdc](.cursor/rules/debug-bridge-reference.mdc) — endpoints, devices, HTTP API
- [debug-bridge-test-procedures.mdc](.cursor/rules/debug-bridge-test-procedures.mdc) — P0-P9 verification
- [debug-bridge-verify.mdc](.cursor/rules/debug-bridge-verify.mdc) — verify triggers, exceptions
- [testing.mdc](.cursor/rules/testing.mdc) — test layout, fixtures, coverage
- [releases-signing.mdc](.cursor/rules/releases-signing.mdc) — semver, releases, signing
