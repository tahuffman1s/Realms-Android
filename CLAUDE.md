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
- Use `superpowers:brainstorming` before designing new features or systems.
- Use `superpowers:writing-plans` for multi-step tasks touching 3+ files, then `superpowers:executing-plans` to execute.
- Use `superpowers:dispatching-parallel-agents` or `superpowers:subagent-driven-development` to coordinate parallel work.
- Use `superpowers:systematic-debugging` before investigating bugs.
- Use `superpowers:test-driven-development` when adding new game mechanics or reducers.
- Use `frontend-design:frontend-design` when building or redesigning any Compose UI (screens, overlays, panels, HUD elements).
- Use `feature-dev:feature-dev` for guided feature development with codebase exploration and architecture focus.
- Break each high-priority task into independent sub-sections (e.g. data layer, UI, viewmodel).
- Dispatch a separate Claude Sonnet agent (`model: "sonnet"`) for each sub-section using the Agent tool. Run independent agents in parallel within a single message.
- Each agent prompt must be self-contained: include file paths, line numbers, what to change, and why. Do not delegate understanding.
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

### Step 4 — Test

Run the full test suite after all tasks are complete:

```bash
gradle test
```

**If tests pass:** proceed to Step 5.

**If tests fail:**
- Read the failure output. Diagnose whether the failure is caused by your changes or was pre-existing.
- Fix broken tests. If your changes altered behavior that existing tests assert, update the tests to match the new correct behavior — do not delete tests to make them pass.
- If you added new game mechanics, reducers, or parser logic, **write new tests** for them (see Testing section below).
- Re-run `gradle test` after fixes. Cap at 3 retry cycles; if still failing, stop and present the remaining failures to the user.

### Step 5 — Build and Deploy

After tests pass, build and install to the phone in one step:

```bash
gradle installDebug && adb shell am start -n com.realmsoffate.game/.MainActivity
```

This builds the debug APK, pushes it to the connected Galaxy S23, and launches the app. If no device is connected, run `phone` (the fish function) which handles ADB reconnection automatically.

**If the build succeeds:** report success. The app is already running on the phone.

**If the build fails:** read the full error output, then:
- Diagnose each error.
- Dispatch Claude Sonnet agents (`model: "sonnet"`) to fix each independent error in parallel.
- After fixes land, rebuild. Re-run tests if fixes touched logic.
- Repeat this diagnose-fix-rebuild loop until the build succeeds.
- Cap at 5 retry cycles. If still failing after 5 attempts, stop and present the remaining errors to the user.

### Step 6 — Verify and Finish

- Use `superpowers:verification-before-completion` before claiming work is done — run tests and build, confirm output, then report.
- Use `commit-commands:commit` or `commit-commands:commit-push-pr` for git operations when the user asks to commit or create a PR.
- Use `superpowers:requesting-code-review` after completing a major feature or before merging.
- Use `superpowers:finishing-a-development-branch` when implementation is complete and you need to decide how to integrate.

### Step 7 — Update README

If your changes altered any of the following, update `README.md` to reflect reality:

- **Project structure** — files added, removed, or moved
- **Game systems** — new mechanics, changed AI integration, new tag types
- **Build requirements** — new dependencies, SDK version changes, new build commands
- **Tech stack** — new libraries, architecture changes

Do not update the README for internal refactors, bug fixes, or changes invisible to someone reading the project overview. The README describes *what the project is*, not *what changed today*.

## Environment Setup

First-time setup on Arch Linux (installs JDK 17/21, Android SDK, Node.js):

```bash
./setup-env.sh
```

## Build Commands

```bash
# Debug APK
gradle assembleDebug

# Release APK
gradle assembleRelease

# Run lint
gradle lint

# Run tests
gradle test

# Clean
gradle clean
```

## Versioning & Releases

This project follows [Semantic Versioning](https://semver.org): `MAJOR.MINOR.PATCH`.

- **MAJOR** — breaking changes or graduation to production (1.0.0 = first Play Store release)
- **MINOR** — new features, new game systems, significant UI changes
- **PATCH** — bug fixes, balance tweaks, prompt adjustments, cosmetic changes

### Version source of truth

`app/build.gradle.kts` contains both `versionName` (semver string) and `versionCode` (integer for Play Store). The `versionCode` is always derived from `versionName`:

```
versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
```

Never set `versionCode` independently — always compute it from the version string.

### When to bump versions

**On release only.** Normal commits do NOT touch version numbers. Versions change exclusively when the user requests a release.

### Release protocol

When the user says "release", "cut a release", "tag a release", or similar:

1. **Determine bump type** from the work done since the last tag. If ambiguous, ask the user. Use `git log` to review changes since the last `v*` tag.
2. **Compute** the new `versionName` and `versionCode`.
3. **Update** `versionName` and `versionCode` in `app/build.gradle.kts`.
4. **Commit** with message: `Release vX.Y.Z`.
5. **Tag** with an annotated tag: `git tag -a vX.Y.Z -m "Release vX.Y.Z"`.
6. **Push** the commit and tag: `git push && git push --tags`.

The tag push triggers `.github/workflows/release.yml`, which runs tests, builds the release APK, and creates a GitHub Release with the APK attached.

### Example

```bash
# What Claude does when you say "release":
# 1. Bumps app/build.gradle.kts: versionName = "0.2.0", versionCode = 200
# 2. git commit -m "Release v0.2.0"
# 3. git tag -a v0.2.0 -m "Release v0.2.0"
# 4. git push && git push --tags
# → GitHub Actions builds APK + creates release automatically
```

## Testing

### Test structure

```
app/src/test/kotlin/com/realmsoffate/game/
├── data/
│   └── TagParserTest.kt              12 tests — tokenizer + segment parser
└── game/
    ├── ApplyParsedIntegrationTest.kt  8 tests — full turn pipeline via reducers
    ├── MerchantHandlerTest.kt         9 tests — buy/sell/buyback/exchange/haggle
    ├── RestHandlerTest.kt             7 tests — short rest, long rest, death saves
    ├── SaveServiceTest.kt             5 tests — export, filenames, snapshots
    ├── ProgressionHandlerTest.kt      7 tests — stat points, feats, level-up
    ├── GameStateFixture.kt            Test helpers: character(), baseState(), viewModelWithState()
    └── ParsedReplyBuilder.kt          Fluent builder for ParsedReply test payloads
```

Tests run on Robolectric (SDK 34) with JUnit 4. No instrumented tests — everything runs on JVM.

### When to write tests

| Changed | Required |
|---------|----------|
| New reducer or modified reducer logic | Add cases to `ApplyParsedIntegrationTest` using `ParsedReplyBuilder` |
| New tag type or changed tag parsing | Add cases to `TagParserTest` |
| New handler (Phase III: Merchant, Rest, Save, Progression) | Add cases to the handler's existing test class |
| Bug fix in game logic | Add a regression test that would have caught the bug |
| UI-only changes, prompt text changes, cosmetic tweaks | No test needed |

### How to write tests

**For reducer/applyParsed tests:** Use the existing fixtures.

```kotlin
@Test fun `your test name`() {
    val vm = viewModelWithState(baseState(
        character = character(hp = 50, maxHp = 100)
    ))
    val parsed = ParsedReplyBuilder()
        .heal(20)
        .narration("You feel restored.")
        .build()

    vm.applyParsed(parsed, diceResult = null)

    val state = vm.readUiState()
    assertEquals(70, state.character.hp)
}
```

**For tag parser tests:** Feed raw AI output strings to the tokenizer and assert segment types/content.

```kotlin
@Test fun `your test name`() {
    val segments = tokenize("[NPC_DIALOG:merchant]\"Fresh wares!\"/[NPC_DIALOG]")
    assertEquals(1, segments.size)
    assertEquals(SegmentType.NPC_DIALOG, segments[0].type)
    assertEquals("merchant", segments[0].speaker)
}
```

### Test commands

```bash
# All tests
gradle test

# Specific test class
gradle test --tests "com.realmsoffate.game.data.TagParserTest"
gradle test --tests "com.realmsoffate.game.game.ApplyParsedIntegrationTest"
```

## Plugin Skills — When to Use What

### Decision flow

```
User request arrives
  ├─ New feature or system? → superpowers:brainstorming → superpowers:writing-plans
  ├─ Bug or test failure?   → superpowers:systematic-debugging
  ├─ UI work?               → frontend-design:frontend-design
  ├─ Multi-file feature?    → feature-dev:feature-dev (or superpowers:writing-plans)
  ├─ 2+ independent tasks?  → superpowers:dispatching-parallel-agents
  ├─ Executing a plan?      → superpowers:executing-plans (or subagent-driven-development)
  ├─ Adding game logic?     → superpowers:test-driven-development
  ├─ Work complete?         → superpowers:verification-before-completion
  ├─ Ready to commit?       → commit-commands:commit (or commit-push-pr)
  ├─ Ready to merge/PR?     → superpowers:finishing-a-development-branch
  ├─ Major feature done?    → superpowers:requesting-code-review
  ├─ Received PR feedback?  → superpowers:receiving-code-review
  ├─ Need isolation?        → superpowers:using-git-worktrees
  └─ Need API docs?         → context7 MCP (via tool call)
```

### Superpowers — workflow discipline

| Skill | When |
|-------|------|
| `superpowers:brainstorming` | Before designing any new feature, system, or non-trivial change |
| `superpowers:test-driven-development` | When adding game mechanics, reducers, parser logic, or handlers |
| `superpowers:systematic-debugging` | When investigating any bug, test failure, or unexpected behavior |
| `superpowers:writing-plans` | For multi-step tasks touching 3+ files |
| `superpowers:executing-plans` | When executing a written plan with review checkpoints |
| `superpowers:subagent-driven-development` | When executing plans with independent tasks in current session |
| `superpowers:dispatching-parallel-agents` | When facing 2+ independent tasks |
| `superpowers:verification-before-completion` | Before claiming work is done — evidence before assertions |
| `superpowers:requesting-code-review` | After completing a major feature or before merging |
| `superpowers:receiving-code-review` | When receiving review feedback — verify before implementing |
| `superpowers:finishing-a-development-branch` | When implementation is complete, deciding how to integrate |
| `superpowers:using-git-worktrees` | When feature work needs isolation from current workspace |

### Frontend Design — UI quality

| Skill | When |
|-------|------|
| `frontend-design:frontend-design` | When creating or redesigning Compose UI: screens, overlays, panels, HUD elements, character creation, map, shop, death screen |

### Feature Dev — guided development

| Skill | When |
|-------|------|
| `feature-dev:feature-dev` | For guided feature development with codebase exploration and architecture focus. Use for medium-to-large features as an alternative to the superpowers brainstorming → planning → execution pipeline. |

### Commit Commands — git workflow

| Skill | When |
|-------|------|
| `commit-commands:commit` | When the user asks to commit changes |
| `commit-commands:commit-push-pr` | When the user wants to commit, push, and open a PR in one step |
| `commit-commands:clean_gone` | When cleaning up stale local branches |

### Code Review — PR quality

| Skill | When |
|-------|------|
| `code-review:code-review` | For automated multi-agent code review of a PR |
| `pr-review-toolkit:review-pr` | For comprehensive PR review using specialized agents (tests, error handling, types, quality) |

### Hookify — behavioral guardrails

| Skill | When |
|-------|------|
| `hookify:hookify` | When you notice a recurring unwanted pattern and want to create a hook to prevent it |
| `hookify:configure` | To enable/disable hookify rules |
| `hookify:list` | To see active rules |

### Context7 — live documentation

Context7 is an MCP server that fetches current documentation. Use it when you need up-to-date API references for:
- Jetpack Compose APIs and patterns
- Kotlin serialization
- Robolectric test setup
- Gradle Kotlin DSL
- DeepSeek API
- Any library where your training data might be stale

## Deploying to the phone

The paired Android target is a Galaxy S23 (`SM-S916U`) on the local LAN. A fish function `phone` is installed at `~/.config/fish/functions/phone.fish` that wraps the full cycle: reconnect if needed → `gradle installDebug` → launch `MainActivity`. Run it from the repo root:

```
phone
```

### Under the hood

The function:
1. Checks `adb devices` for at least one entry in `device` state.
2. If none, runs `adb connect 192.168.68.64:44547` (the last-known ADB port).
3. If still no device, prints reconnection instructions and exits non-zero.
4. Runs `gradle installDebug`.
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
- `app/src/main/kotlin/com/realmsoffate/game/game/reducers/` — Pure reducers (character, combat, NPC log, quest/party, world)
- `app/src/main/kotlin/com/realmsoffate/game/game/handlers/` — VM-level domain handlers (merchant, rest, save, progression)
- `app/src/main/kotlin/com/realmsoffate/game/ui/` — UI layer (game screen, map, overlays, panels, setup screens)
- `app/src/main/kotlin/com/realmsoffate/game/util/` — Utilities (markdown rendering)
- `app/src/test/kotlin/com/realmsoffate/game/` — Tests (tag parser, integration, handler tests, fixtures)
