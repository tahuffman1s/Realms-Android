# Debug Bridge — Design Spec

A debug-only subsystem that gives Claude Code full visibility into the running app's visual state, game state, and UI layout, plus the ability to send commands, inject state, and receive real-time events. The goal is to eliminate visual bugs by enabling systematic automated and visual testing without manual screenshot/tap/coordinate workflows.

## Goals

1. **Catch visual bugs before they ship** — contrast failures, theme issues, layout overflow, invisible elements, touch target violations
2. **Enable fast iteration** — deploy, navigate, inspect, fix, repeat without manual emulator interaction
3. **Test edge cases** — inject arbitrary state (0 HP, 200-char names, 50 messages) and verify the UI handles it
4. **Zero release footprint** — every line lives in `app/src/debug/` or behind `BuildConfig.DEBUG` guards

## Architecture

```
┌──────────────────────────────────────────────────────┐
│  Claude Code (host machine)                          │
│                                                      │
│  HTTP:  curl localhost:8735/...   ←── adb forward    │
│  Events: adb logcat -s RealmsDebug:V  ←── live push  │
└──────────────────────────────────────────────────────┘
               │                          ▲
               ▼                          │
┌──────────────────────────────────────────────────────┐
│  Debug Bridge (app, debug builds only)               │
│                                                      │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐ │
│  │ DebugServer  │  │ IssueChecker │  │ EventBus    │ │
│  │ (HTTP :8735) │  │ (automated)  │  │ (logcat)    │ │
│  └──────┬──────┘  └──────┬───────┘  └──────┬──────┘ │
│         │                │                  │        │
│         └────────┬───────┘──────────────────┘        │
│                  ▼                                    │
│          GameViewModel + Compose semantics tree       │
└──────────────────────────────────────────────────────┘
```

### Components

| Component | Responsibility | Location |
|-----------|---------------|----------|
| **DebugServer** | HTTP server on port 8735, routes requests to ViewModel and Compose tree | `app/src/debug/.../debug/DebugServer.kt` |
| **IssueChecker** | Runs automated visual/accessibility checks against the semantics tree | `app/src/debug/.../debug/IssueChecker.kt` |
| **EventBus** | Pushes structured JSON events to logcat under `RealmsDebug` tag | `app/src/debug/.../debug/DebugEventBus.kt` |
| **StateInjector** | Overrides ViewModel state fields for edge case testing | `app/src/debug/.../debug/StateInjector.kt` |
| **MacroRunner** | Executes multi-step test scenarios (new game, advance turns) | `app/src/debug/.../debug/MacroRunner.kt` |
| **DebugBridgeInit** | Lifecycle-aware startup, called from `RealmsApp.onCreate()` behind `BuildConfig.DEBUG` | `app/src/debug/.../debug/DebugBridgeInit.kt` |

All files live under `app/src/debug/kotlin/com/realmsoffate/game/debug/`.

---

## HTTP Endpoints

### State & Awareness

#### `GET /state`
Full game state as JSON. This is the primary way Claude reads what's happening.

```json
{
  "screen": "game",
  "turn": 3,
  "isGenerating": false,
  "activeOverlay": null,
  "character": {
    "name": "Kaelis",
    "class": "Fighter",
    "race": "Human",
    "level": 2,
    "hp": 8,
    "maxHp": 14,
    "xp": 450,
    "xpToNext": 600,
    "gold": 42,
    "stats": {"str": 16, "dex": 12, "con": 14, "int": 10, "wis": 8, "cha": 13},
    "conditions": ["poisoned"],
    "inventory": ["longsword", "chain mail", "healing potion"],
    "location": "Wychfield"
  },
  "messages": [
    {"type": "narration", "text": "The tavern falls silent...", "turn": 3, "bookmarked": false},
    {"type": "npcDialog", "speaker": "Lyra", "text": "They're coming.", "turn": 3},
    {"type": "player", "text": "I ready my weapon.", "turn": 3}
  ],
  "recentMessages": 3,
  "totalMessages": 18,
  "combat": null,
  "scene": {"type": "tavern", "description": "A smoky backroom in the Rusty Nail"},
  "overlayState": {
    "activeOverlay": null,
    "availableOverlays": ["settings", "debug"]
  },
  "npcLog": [
    {"id": "lyra", "name": "Lyra", "disposition": "friendly", "met": true}
  ],
  "quests": [
    {"name": "The Uprising", "status": "active", "description": "Choose a side in Wychfield's revolution"}
  ]
}
```

#### `GET /state/diff`
Returns only fields that changed since the last `/state` or `/state/diff` call. Resets on each call.

```json
{
  "changes": {
    "character.hp": {"from": 12, "to": 8},
    "character.gold": {"from": 25, "to": 42},
    "turn": {"from": 2, "to": 3},
    "messagesAdded": [
      {"type": "narration", "text": "The blade finds its mark..."}
    ],
    "combat": {"from": null, "to": {"inCombat": true, "enemies": ["Bandit"]}}
  }
}
```

#### `GET /state/overlay`
Quick check for what overlay is active and what actions it accepts.

```json
{
  "activeOverlay": "preRoll",
  "data": {
    "action": "I draw my sword and stand with the rebels",
    "skill": "STR",
    "d20": 20,
    "modifier": 3,
    "total": 23,
    "label": "NATURAL TWENTY"
  },
  "actions": ["confirm", "cancel"]
}
```

Returns `{"activeOverlay": null}` when no overlay is showing.

---

### Visual Testing

#### `GET /screenshot`
Captures the current screen and returns the image.

Response: PNG binary with `Content-Type: image/png`

Optional query params:
- `?format=base64` — returns base64-encoded string instead of binary
- `?scale=0.5` — downscale for faster transfer (default: 1.0)

#### `GET /screenshot/both`
Captures screenshots in both light and dark themes. Switches theme, waits for recomposition, captures, switches back.

Response:
```json
{
  "light": "<base64 PNG>",
  "dark": "<base64 PNG>"
}
```

#### `GET /checks`
Runs automated visual/accessibility checks and returns flagged issues.

```json
{
  "screen": "game",
  "theme": "light",
  "timestamp": "2026-04-17T10:30:00Z",
  "issues": [
    {
      "severity": "high",
      "type": "contrast",
      "element": "NpcBubble/Text[LYRA]",
      "bounds": [158, 1295, 890, 1492],
      "detail": "text color #FFFFFF on effective bg #5E7B4A1F, contrast ratio 1.3:1 (WCAG AA minimum: 4.5:1)"
    },
    {
      "severity": "high",
      "type": "hardcoded-color",
      "element": "NpcBubble/Text",
      "detail": "Color.White used directly — will not adapt to system theme"
    },
    {
      "severity": "medium",
      "type": "touch-target",
      "element": "BookmarkBorder icon",
      "bounds": [940, 780, 998, 838],
      "actual": "22x22dp",
      "minimum": "48x48dp"
    },
    {
      "severity": "medium",
      "type": "truncation",
      "element": "SceneBanner/description",
      "detail": "text clipped at maxLines=2, full text requires 4 lines"
    },
    {
      "severity": "low",
      "type": "invisible-element",
      "element": "Divider",
      "bounds": [0, 1680, 1080, 1681],
      "detail": "color #FFFBFE matches parent bg #FFFBFE, element is invisible"
    }
  ],
  "summary": {
    "high": 2,
    "medium": 2,
    "low": 1,
    "passed": 47
  }
}
```

##### Checks performed

| Check | Severity | What it catches |
|-------|----------|----------------|
| **WCAG contrast** | high | Text color vs effective background below 4.5:1 (normal) or 3:1 (large text) |
| **Hardcoded colors** | high | `Color.White`, `Color.Black`, `Color(0xFF...)` used instead of theme tokens |
| **Touch target size** | medium | Interactive elements smaller than 48x48dp |
| **Text truncation** | medium | Text clipped by maxLines or container overflow |
| **Content overflow** | medium | Child bounds extending beyond clipped parent |
| **Invisible elements** | low | Elements with 0 alpha, zero size, color matching background, or fully off-screen |
| **Overlapping interactives** | low | Two clickable elements whose bounds overlap |
| **Z-order conflicts** | low | Interactive element obscured by a non-interactive element drawn on top |
| **Empty text** | low | Text composable with blank or whitespace-only content |

#### `GET /checks/both`
Runs `/checks` in both light and dark themes. Returns both issue lists so theme-specific bugs are caught.

```json
{
  "light": { "issues": [...], "summary": {...} },
  "dark": { "issues": [...], "summary": {...} }
}
```

#### `POST /theme`
Switch the app's theme.

```json
{"mode": "light"}
```
```json
{"mode": "dark"}
```
```json
{"mode": "system"}
```

Response: `{"ok": true, "activeTheme": "dark"}`

#### `POST /font-scale`
Change the in-app font scale for testing text overflow at different sizes.

```json
{"scale": 1.5}
```

Response: `{"ok": true, "scale": 1.5}`

---

### Commands

#### `POST /input`
Submit player text input directly to `vm.submitAction()`. Bypasses the text field entirely.

```json
{"text": "I draw my sword and charge"}
```

Response:
```json
{
  "ok": true,
  "triggered": "preRoll",
  "detail": "STR check queued, waiting for confirm"
}
```

#### `POST /confirm`
Confirm the active pre-roll dice overlay. Equivalent to tapping "Send It".

Response:
```json
{
  "ok": true,
  "roll": {"d20": 14, "modifier": 3, "total": 17},
  "detail": "dispatching to AI"
}
```

#### `POST /cancel`
Cancel/dismiss the active overlay.

Response: `{"ok": true, "dismissed": "preRoll"}`

#### `POST /navigate`
Navigate to a screen or tab.

```json
{"screen": "game"}
{"screen": "title"}
{"screen": "death"}
{"tab": "character"}
{"tab": "map"}
{"tab": "journal"}
{"tab": "chat"}
```

Response: `{"ok": true, "screen": "game", "tab": "character"}`

#### `POST /scroll`
Scroll the chat feed.

```json
{"direction": "top"}
{"direction": "bottom"}
{"direction": "up", "amount": 500}
```

Response: `{"ok": true, "scrollPosition": 0.0}`

#### `POST /tap`
Tap an element by its content description or text label instead of pixel coordinates.

```json
{"contentDesc": "Settings"}
{"text": "Continue"}
{"testTag": "send_button"}
```

Response: `{"ok": true, "tapped": "Settings", "bounds": [923, 158, 1049, 284]}`

Returns 404 if the element isn't found on screen.

---

### State Injection (Edge Case Testing)

#### `POST /inject`
Override specific game state fields to test UI edge cases without playing through the game.

```json
{
  "character.hp": 0,
  "character.maxHp": 100,
  "character.name": "Bartholomew Fitzgerald Worthington III",
  "character.gold": 999999,
  "character.conditions": ["poisoned", "blinded", "frightened", "exhaustion_3"],
  "character.level": 20
}
```

Response: `{"ok": true, "fieldsSet": 6}`

The UI recomposes with the injected state. This lets Claude test:
- **Zero HP** — does the death screen trigger? Does the HP bar handle 0 correctly?
- **Long names** — does text truncate properly or overflow?
- **Large numbers** — does gold "999999" fit in the top bar?
- **Many conditions** — do status pills wrap correctly in FlowRow?
- **Max level** — does the XP bar handle level 20?

#### `POST /inject/messages`
Inject specific display messages for testing chat feed rendering.

```json
{
  "messages": [
    {"type": "npcDialog", "speaker": "A Very Long Named Character Who Talks A Lot", "text": "Short."},
    {"type": "npcDialog", "speaker": "X", "text": "A single character name."},
    {"type": "narration", "text": ""},
    {"type": "narration", "text": "A".repeat(2000)},
    {"type": "system", "text": "✓ STR check: Natural 20 + 3 = 23 vs DC 15 — PASS"}
  ]
}
```

Tests: long speaker names, short speaker names, empty narration, extremely long narration, system lines with special formatting.

#### `POST /inject/reset`
Restore the real game state, discarding all injections.

Response: `{"ok": true, "restored": true}`

---

### Macros

#### `POST /macro/new-game`
Skip through character creation and land in the game screen ready for testing. Uses fast AI generation or a canned first turn.

```json
{
  "name": "Test",
  "class": "fighter",
  "race": "human",
  "scenario": "uprising",
  "skipFirstTurn": true
}
```

`skipFirstTurn: true` loads a pre-recorded first turn response instead of calling the AI, so testing doesn't burn API credits or wait 15+ seconds.

Response: `{"ok": true, "screen": "game", "turn": 1}`

#### `POST /macro/advance`
Auto-play N turns with canned or AI-generated responses.

```json
{"turns": 3, "mode": "canned"}
```

Modes:
- `"canned"` — uses pre-recorded AI responses (fast, free, deterministic)
- `"live"` — actually calls the AI (slow, costs credits, realistic)

Response: `{"ok": true, "turnsPlayed": 3, "finalTurn": 4}`

#### `POST /macro/combat`
Enter a combat encounter immediately for testing combat UI.

```json
{"enemies": ["Goblin", "Goblin Archer"], "surprise": false}
```

#### `POST /macro/shop`
Open a merchant shop for testing shop UI.

```json
{"merchant": "Blacksmith", "items": ["longsword", "shield", "healing potion"]}
```

#### `POST /macro/death`
Trigger character death for testing the death screen.

Response: `{"ok": true, "screen": "death"}`

---

### Error & Event Streaming

All events are pushed to Android logcat under tag `RealmsDebug` as single-line JSON.

Claude Code reads these by running `adb logcat -s RealmsDebug:V` in the background via the Monitor tool.

#### Event types

```
D/RealmsDebug: {"event":"stateChange","field":"character.hp","from":12,"to":8}
D/RealmsDebug: {"event":"screenChange","from":"title","to":"game"}
D/RealmsDebug: {"event":"messageAdded","type":"narration","preview":"The tavern falls..."}
D/RealmsDebug: {"event":"overlayShown","overlay":"preRoll","skill":"STR"}
D/RealmsDebug: {"event":"overlayDismissed","overlay":"preRoll"}
D/RealmsDebug: {"event":"error","message":"API timeout after 90s","stackTrace":"..."}
D/RealmsDebug: {"event":"crash","exception":"NullPointerException","stackTrace":"...","file":"GameViewModel.kt","line":842}
D/RealmsDebug: {"event":"generating","status":"start"}
D/RealmsDebug: {"event":"generating","status":"complete","durationMs":4200}
D/RealmsDebug: {"event":"navigation","tab":"character"}
D/RealmsDebug: {"event":"themeChange","mode":"dark"}
D/RealmsDebug: {"event":"recomposition","composable":"NpcDialogueBubble","count":12,"frameMs":8}
```

#### Crash handler
Install a `Thread.setDefaultUncaughtExceptionHandler` that logs the full crash as a `RealmsDebug` event before the app dies. This ensures Claude sees the crash even if it happens outside a try-catch.

---

### Automated Check Details

#### How contrast is computed

The IssueChecker walks the Compose semantics tree. For each `Text` node:

1. Read the node's text color from its `SemanticsProperties` (available for nodes with `Modifier.semantics`)
2. Walk up the tree to find the nearest ancestor with a background color
3. Compute the WCAG 2.1 relative luminance contrast ratio
4. Flag if below 4.5:1 (normal text) or 3:1 (large text, ≥18sp or ≥14sp bold)

For elements where the semantics tree doesn't expose colors (which is most of them — Compose semantics doesn't carry color data natively), the IssueChecker uses a secondary strategy:

**Source-code static analysis at build time.** A Gradle task scans `app/src/main/kotlin/**/ui/**/*.kt` for:
- `Color.White`, `Color.Black`, `Color.Red`, etc. — hardcoded color literals
- `Color(0x...)` — hex color constructors
- Any `color = ` assignment that doesn't reference `MaterialTheme.colorScheme.*` or `RealmsTheme.colors.*`

These are flagged as `hardcoded-color` issues. This catches the exact class of bug we hit (someone writes `Color.White` instead of `MaterialTheme.colorScheme.onSurface`).

#### How touch targets are measured

The semantics tree provides bounds for every interactive node (clickable, toggleable, etc.). The checker converts pixel bounds to dp using the screen density and flags any interactive element smaller than 48x48dp per Material Design guidelines.

#### How overflow is detected

Compare each node's bounds against its parent's bounds + clip settings. If a child extends beyond a clipped parent, flag it.

---

## Debug-Only Enforcement

### Source set isolation

All debug bridge code lives in `app/src/debug/kotlin/com/realmsoffate/game/debug/`. This directory does not exist in the release source set, so none of this code is compiled into release builds.

### Startup guard

In the main source set, `RealmsApp.onCreate()` gets a single addition:

```kotlin
override fun onCreate() {
    super.onCreate()
    SaveStore.init(this)
    if (BuildConfig.DEBUG) {
        DebugBridgeInit.start(this)
    }
}
```

`DebugBridgeInit` is defined in the debug source set. In release builds, the `if` block is dead code and the class doesn't exist — ProGuard strips it.

### No new dependencies

The HTTP server uses `java.net.ServerSocket` (JDK standard library). JSON serialization uses `kotlinx.serialization` which is already in the project. Screenshot capture uses the existing `adb exec-out screencap` path or `PixelCopy` API. No new libraries are added.

### Build-time lint task

The hardcoded-color scanner runs as a Gradle task (`checkHardcodedColors`) wired into the `assembleDebug` chain. It prints warnings but does not fail the build — Claude reads the output and decides whether to fix.

---

## Integration with Claude Code

### Setup (one-time per session)

```bash
# Forward the debug server port
adb -s emulator-5554 forward tcp:8735 tcp:8735

# Start listening for events in background
adb -s emulator-5554 logcat -s RealmsDebug:V
```

### Typical workflow after a code change

```bash
# 1. Deploy
gradle installDebug

# 2. Launch
adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity

# 3. Navigate to game screen
curl -X POST localhost:8735/navigate -d '{"screen":"game"}'

# 4. Or skip to a ready game state
curl -X POST localhost:8735/macro/new-game -d '{"name":"Test","class":"fighter","race":"human","skipFirstTurn":true}'

# 5. Take screenshots in both themes
curl localhost:8735/screenshot > light.png
curl -X POST localhost:8735/theme -d '{"mode":"dark"}'
curl localhost:8735/screenshot > dark.png
curl -X POST localhost:8735/theme -d '{"mode":"system"}'

# 6. Run automated checks in both themes
curl localhost:8735/checks/both

# 7. Test interactions
curl -X POST localhost:8735/input -d '{"text":"I attack the goblin"}'
curl localhost:8735/state/overlay  # see dice roll
curl -X POST localhost:8735/confirm
curl localhost:8735/state/diff     # see what changed

# 8. Test edge cases
curl -X POST localhost:8735/inject -d '{"character.hp":0}'
curl localhost:8735/screenshot > zero_hp.png
curl localhost:8735/checks
curl -X POST localhost:8735/inject/reset
```

### Fish function (optional convenience)

A `debug` fish function could wrap the port forwarding and provide shortcuts:

```bash
debug state          # curl localhost:8735/state
debug screenshot     # curl localhost:8735/screenshot > screenshots/debug_<ts>.png
debug checks         # curl localhost:8735/checks | jq
debug input "text"   # curl -X POST localhost:8735/input -d '{"text":"..."}'
debug theme dark     # curl -X POST localhost:8735/theme -d '{"mode":"dark"}'
debug inject hp 0    # curl -X POST localhost:8735/inject -d '{"character.hp":0}'
debug reset          # curl -X POST localhost:8735/inject/reset
debug new-game       # curl -X POST localhost:8735/macro/new-game -d '{...}'
```

---

## CLAUDE.md Addition

After implementation, add a `## Debug Bridge` section to CLAUDE.md documenting:
- How to start the bridge (`adb forward`, logcat monitor)
- The endpoint reference
- The recommended post-deploy testing workflow
- When to use `/checks` vs screenshots vs both

This replaces the manual screenshot + tap coordinate workflow entirely.

---

## What This Does NOT Cover

- **Instrumented UI tests** (Espresso/Compose test rules) — this system is for interactive Claude-driven testing, not CI test suites
- **Performance profiling** — recomposition counts are tracked in events but full profiling is out of scope
- **Network mocking** — AI responses in macros use canned data, but there's no general network mock layer
- **Multi-device management** — the server runs on one device; targeting is handled by the `adb -s` flag at the host level
