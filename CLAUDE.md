# Realms of Fate — Android

Kotlin + Jetpack Compose RPG game. Package: `com.realmsoffate.game`. Build system: Gradle with Kotlin DSL.

## Agent Model Rule

Opus (you) handles planning, design, architecture, and coordination. Sonnet handles execution.

- **Dispatch all agents with `model: "sonnet"`** — implementation, exploration, review, research.
- **Opus does the thinking** — brainstorming, writing plans, reviewing agent output, making judgment calls. Never delegate these to agents.
- **Write agent prompts optimized for Sonnet** — be explicit and literal. Include exact file paths, line numbers, code snippets, and expected outcomes. Sonnet executes precisely what you specify; don't ask it to make design decisions or explore alternatives.
- **Low-priority tasks** (typos, one-line fixes) don't need agents — do them yourself.

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

### Step 5 — Build, Deploy, and Verify Visually

**Owner preference:** Whenever agent work touches app code or resources, **always** rebuild and install to a connected device at the end of that work (do not stop at compile-only), unless the user explicitly says to skip deploy. Use `installDebug` + launch `MainActivity` + Debug Bridge port forward when an emulator or device is available. If Gradle cannot write `~/.android`, set `ANDROID_USER_HOME` to a writable directory (e.g. project `.android-user`). If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, `adb uninstall com.realmsoffate.game` then reinstall.

After tests pass, build, deploy, connect the Debug Bridge, and verify visually:

```bash
# Build and deploy
gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity

# Ensure Debug Bridge port is forwarded (idempotent)
adb -s emulator-5554 forward tcp:8735 tcp:8735
```

If no device is connected, either start the emulator (`emulator -avd Pixel7 &`) or run `phone` for the Galaxy S23.

**If the build fails:** read the full error output, then:
- Diagnose each error.
- Dispatch Claude Sonnet agents (`model: "sonnet"`) to fix each independent error in parallel.
- After fixes land, rebuild. Re-run tests if fixes touched logic.
- Repeat this diagnose-fix-rebuild loop until the build succeeds.
- Cap at 5 retry cycles. If still failing after 5 attempts, stop and present the remaining errors to the user.

**After successful deploy, run the visual verification protocol:**

1. **Navigate to the relevant screen** via the Debug Bridge:
   ```bash
   # For UI changes — skip straight to a game state (no AI, instant)
   curl -X POST localhost:8735/macro/new-game -d '{"name":"Test","class":"fighter","race":"human","skipFirstTurn":true}'
   # For specific screens:
   curl -X POST localhost:8735/navigate -d '{"screen":"death"}'
   ```

2. **Screenshot and visually inspect:**
   ```bash
   curl localhost:8735/screenshot > screenshots/verify.png
   ```
   Read the screenshot. Look for layout issues, text readability, alignment, and anything that looks off.

3. **Test edge cases** if the change touches UI:
   ```bash
   # Long names, zero HP, max gold, many conditions
   curl -X POST localhost:8735/inject -d '{"character.hp":0}'
   curl localhost:8735/screenshot > screenshots/edge_zero_hp.png
   curl -X POST localhost:8735/inject -d '{"character.name":"Bartholomew Fitzgerald Worthington III","character.gold":999999}'
   curl localhost:8735/screenshot > screenshots/edge_long_name.png
   curl -X POST localhost:8735/inject/reset
   ```

4. **Check the other theme** if the change touches colors or backgrounds:
   ```bash
   curl -X POST localhost:8735/theme -d '{"mode":"dark"}'
   curl localhost:8735/screenshot > screenshots/verify_dark.png
   curl -X POST localhost:8735/theme -d '{"mode":"system"}'
   ```

**Skip visual verification only for:** test-only changes, build config changes, prompt text changes, or non-UI game logic.

### Step 6 — Finish

- Use `superpowers:verification-before-completion` before claiming work is done — run tests, build, and visual verification (Step 5), then report.
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

First-time setup on Arch Linux (installs JDK 17/21, Android SDK, emulator, Node.js):

```bash
./setup-env.sh
```

The script creates a **Pixel7** AVD (Android 16 / API 36.1, x86_64 with Google APIs) for local emulator testing. Requires KVM — AMD-V or VT-x must be enabled in BIOS.

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

## Release Signing

Release APKs are signed with an upload keystore. The signing config in `app/build.gradle.kts` reads credentials from two sources (in order):

1. **Local builds** — `keystore.properties` in the project root (gitignored):
   ```properties
   storeFile=../realms-upload.jks
   storePassword=<password>
   keyAlias=realms
   keyPassword=<password>
   ```

2. **CI builds** — environment variables from GitHub Secrets:
   - `KEYSTORE_BASE64` — base64-encoded `.jks` file
   - `KEYSTORE_PASSWORD` — keystore password
   - `KEY_ALIAS` — key alias (e.g. `realms`)
   - `KEY_PASSWORD` — key password

If neither source is configured, the release build produces an unsigned APK.

### Setting up GitHub Secrets

To enable signed CI releases, the user must add these secrets to the repo (Settings → Secrets → Actions):

```bash
# Encode the keystore for GitHub Secrets:
base64 -w0 realms-upload.jks | pbcopy  # or xclip on Linux
```

Then add `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` as repository secrets.

### APK naming

Release APKs are named `realms-of-fate-vX.Y.Z-release.apk` automatically via `archivesBaseName` in `build.gradle.kts`.

## Testing

### Test structure

```
app/src/test/kotlin/com/realmsoffate/game/
├── data/
│   └── TagParserTest.kt              12 tests — tokenizer + segment parser
└── game/
    ├── ApplyParsedIntegrationTest.kt  14 tests — full turn pipeline via reducers
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

## Deploying to a device

Two deployment targets are available. The **emulator is preferred** for development — it supports the Debug Bridge for visual testing.

### Emulator (preferred)

```bash
# Start the emulator if not running
emulator -avd Pixel7 &

# Deploy, launch, and connect Debug Bridge
adb wait-for-device && gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity
adb -s emulator-5554 forward tcp:8735 tcp:8735
```

After this, all Debug Bridge endpoints are available at `localhost:8735`. The AVD `Pixel7` is created by `setup-env.sh`.

### Physical phone (Galaxy S23)

The fish function `phone` handles wireless ADB reconnection + deploy + launch:

```
phone
```

The phone is a Galaxy S23 (`SM-S916U`) on the LAN at `192.168.68.64`. The Debug Bridge also works on the phone — just forward the port to the phone's serial instead: `adb -s <phone-serial> forward tcp:8735 tcp:8735`.

**When the ADB port rotates** (reboots, Wi-Fi drops): open **Settings → Developer options → Wireless debugging** on the phone, note the new port, then `adb connect 192.168.68.64:<new-port>` or update `phone.fish`.

## Debugging workflow

**Primary:** Use the Debug Bridge (`curl localhost:8735/state`, `/screenshot`). See Step 5 for the full visual verification protocol.

**For AI exchange logs:** The in-game Debug button (More menu → Debug) dumps full game state + AI exchange log to the device. Pull with:

```
pulldebug
```

Yanks the newest dump into `<repo-root>/debug-dumps/` (gitignored). The freshest dump is:
```
ls -t debug-dumps/debug_*.txt 2>/dev/null | head -1
```

## Debug Bridge (debug builds only)

HTTP server on port 8735 — full access to app state, UI, and commands. Code lives in `app/src/debug/`. Zero release footprint. The visual verification workflow in Step 5 uses this.

### Setup

Port forwarding (idempotent, include in deploy commands):
```bash
adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Real-time events (run in background):
```bash
adb -s emulator-5554 logcat -s RealmsDebug:V
```

### Endpoint Reference

**State:** `GET /state` · `/state/diff` · `/state/overlay`

**Visual:** `GET /screenshot` (`?format=base64`) · `/screenshot/both` · `/describe`

**Commands:** `POST /input {"text":"..."}` · `/confirm` · `/cancel` · `/navigate {"screen":"game"}` · `/tap {"text":"Continue"}`

**Theme:** `POST /theme {"mode":"dark"}` · `/font-scale {"scale":1.5}`

**Injection:** `POST /inject {"character.hp":0}` · `/inject/messages {"messages":[...]}` · `/inject/reset`

**Macros:** `POST /macro/new-game {"name":"Test","class":"fighter","race":"human","skipFirstTurn":true}` · `/macro/advance {"turns":3,"mode":"canned"}` · `/macro/death` · `/macro/simulate-gameplay` (dense fake mid-campaign state for UI/QA — no AI)

### Key interactions

**Navigate without coordinate guessing:**
```bash
curl -X POST localhost:8735/tap -d '{"text":"Continue"}'   # tap by label
curl -X POST localhost:8735/navigate -d '{"screen":"game"}' # switch screen directly
curl -X POST localhost:8735/input -d '{"text":"I attack"}'  # submit player input
curl localhost:8735/state/overlay                            # check if dice roll is pending
curl -X POST localhost:8735/confirm                          # confirm dice roll
```

### Fish function

The `debug` fish function wraps all endpoints:
```
debug state | diff | overlay | screenshot | describe
debug input <text> | confirm | cancel | navigate <screen> | tap <label>
debug theme <light|dark|system> | font-scale <n>
debug inject <field> <value> | reset
debug new-game | death | advance [n]
debug events
```

## Project Structure

- `app/src/main/kotlin/com/realmsoffate/game/data/` — Data layer (AI providers, models, preferences, prompts, tag parsing)
- `app/src/main/kotlin/com/realmsoffate/game/game/` — Game logic (viewmodel, classes, races, scenarios, world events, lore, feats)
- `app/src/main/kotlin/com/realmsoffate/game/game/reducers/` — Pure reducers (character, combat, NPC log, quest/party, world)
- `app/src/main/kotlin/com/realmsoffate/game/game/handlers/` — VM-level domain handlers (merchant, rest, save, progression)
- `app/src/main/kotlin/com/realmsoffate/game/ui/` — UI layer (game screen, map, overlays, panels, setup screens)
- `app/src/main/kotlin/com/realmsoffate/game/util/` — Utilities (markdown rendering)
- `app/src/debug/kotlin/com/realmsoffate/game/debug/` — Debug Bridge (HTTP server, issue checker, event bus, macros — debug builds only)
- `app/src/test/kotlin/com/realmsoffate/game/` — Tests (tag parser, integration, handler tests, fixtures)
