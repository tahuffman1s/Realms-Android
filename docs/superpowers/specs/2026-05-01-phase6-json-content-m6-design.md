# Phase 6 — JSON Content Extraction (M6)

**Date:** 2026-05-01
**Issue:** [#24 — Extract hardcoded static data into JSON assets](https://github.com/tahuffman1s/RealmsAndroid/issues/24)
**Scope:** Milestone M6 — Debug-only hot-reload of content from `Context.filesDir`
**Builds on:** M1+M2, M3, M4, M5a, M5b

## Goal

Let writers (and engineers) edit a content JSON, push it to the device, hit a debug-bridge endpoint, and see the change on the next access — without rebuilding the APK. Production builds are unaffected.

## Decisions

| Decision | Choice |
|---|---|
| Override directory | `Context.filesDir/content/`. Mirrors the asset layout exactly: `content/lore/factions.json` is overridden by `<filesDir>/content/lore/factions.json`. |
| Build-flavor gate | Override only enabled when `BuildConfig.DEBUG`. `RealmsApp.onCreate` passes `BuildConfig.DEBUG` into `ContentRepository.initialize` as the `allowOverride` flag. Release builds always use the bundled assets. |
| Per-file or full-bundle override | **Per-file.** The overlay opener checks the override location first and falls back to assets per path. Writers can drop a single `factions.json` into `filesDir/content/lore/` and leave everything else unchanged. |
| Schema version on override | `schema-version.json` is part of the same overlay. If the override file declares a version that doesn't match `CONTENT_SCHEMA_VERSION`, the reload fails with `SchemaVersionMismatch`. No silent fallback to bundled. |
| Failure behavior on reload | If parsing the new bundle throws, the *current* bundle is left untouched and the endpoint returns the diagnostic. Reload is atomic. |
| Source-of-truth visibility | A `GET /content/info` endpoint reports the active schema version and which paths are coming from the override vs. the bundled assets. |
| Endpoint surface | `GET /content/info`, `POST /content/reload`. Both live in a new `ContentEndpoints.kt` under `app/src/debug/`. |
| Concurrency | `initialize` and `reload` are synchronized on `ContentRepository`. Readers see whichever bundle is current at the moment of property access — no stale snapshots can be torn because every public accessor reads through `bundle` once. |

## Why no schema version bump

M6 ships only a code change — no new JSON shapes, no new assets, no new fields. The shipped content stays at version 5. Override files must declare `version: 5` to load.

## Implementation sketch

### `ContentRepository.initialize(context, allowOverride)`

```kotlin
fun initialize(context: Context, allowOverride: Boolean = false) {
    val overrideRoot = if (allowOverride) File(context.filesDir, "content") else null
    val opener: (String) -> InputStream = { path ->
        val overridden = overrideRoot?.let { root ->
            val rel = path.removePrefix("content/")
            File(root, rel).takeIf { it.isFile }
        }
        overridden?.inputStream() ?: context.assets.open(path)
    }
    synchronized(this) {
        bundle = ContentLoader.loadAndValidate(opener)
        lastSourceMap = computeSourceMap(overrideRoot)
    }
}
```

### Source map for `/content/info`

`ContentLoader` already passes a fixed list of paths through `parse()`. Reuse those paths to compute, post-load, which ones the override actually served. Simplest: have the opener record `path -> "override" | "asset"` into a `MutableMap` and stash that as `ContentRepository.lastSourceMap` after a successful load.

### `RealmsApp.onCreate`

```kotlin
ContentRepository.initialize(this, allowOverride = BuildConfig.DEBUG)
```

### `ContentEndpoints.kt` (new, debug-only)

```kotlin
object ContentEndpoints {
    fun register() {
        DebugServer.route("GET", "/content/info") { _ ->
            val info = ContentRepository.info()
            HttpResponse.json(info.toJson())
        }
        DebugServer.route("POST", "/content/reload") { _ ->
            val app = DebugBridge.requireActivity().application
            try {
                ContentRepository.initialize(app, allowOverride = true)
                HttpResponse.json("""{"ok":true,"info":${ContentRepository.info().toJson()}}""")
            } catch (e: ContentException) {
                HttpResponse.error(409, "reload failed: ${e.message}")
            }
        }
    }
}
```

Wired into `DebugServer.start()` next to the other `*.register()` calls.

### `ContentRepository.info()` shape

```kotlin
data class ContentInfo(
    val schemaVersion: Int,
    val overrideEnabled: Boolean,
    val overrideRoot: String?,
    val sources: Map<String, String>, // path -> "override" | "asset"
)
```

`toJson()` lives in `ContentEndpoints.kt` (debug-only) so the production `ContentRepository` doesn't pull in JSON-emit dependencies.

## What does NOT change

- `assets/content/**` paths and shapes.
- `Mutations.kt`, `Scenarios.kt`, `Spells.kt`, `Races.kt`, `Classes.kt`, `Feats.kt`, `LoreGen.kt`, `WorldEvents.kt` — all still read through `ContentRepository` getters and pick up the new bundle automatically after a reload.
- `CONTENT_SCHEMA_VERSION` stays at `5`.
- Release builds.

## Tests

| Test | Purpose |
|---|---|
| `HotReloadTest.override file wins over asset` | Write a temp file under `filesDir/content/world/mutations.json` with a single fake mutation. Init repo with `allowOverride=true`. Assert `mutations` returns just that fake mutation. |
| `HotReloadTest.missing override file falls back to asset` | Override directory exists but `mutations.json` is absent. Assert `mutations` returns the bundled list. |
| `HotReloadTest.override schema mismatch fails reload` | Override `schema-version.json` declares `99`. Assert `SchemaVersionMismatch` is raised, prior bundle remains queryable. |
| `HotReloadTest.disabled override ignores filesDir` | Write a file under `filesDir/content/...`. Init with `allowOverride=false`. Assert bundled value wins. |
| `HotReloadTest.info reports per-file source` | Override one file, init, assert `info().sources` shows `override` for that path and `asset` for the rest. |

All under `app/src/test/kotlin/com/realmsoffate/game/data/content/HotReloadTest.kt` (Robolectric).

## Manual verification (debug-bridge procedure)

1. `gradle installDebug && launch && forward 8735` (standard).
2. `curl localhost:8735/content/info` — reports `overrideEnabled: true`, all sources `asset`.
3. Push an override:
   `adb shell run-as com.realmsoffate.game mkdir -p files/content/world` then
   `adb push patched/mutations.json /data/local/tmp/m.json` then
   `adb shell run-as com.realmsoffate.game cp /data/local/tmp/m.json files/content/world/mutations.json`
   (or use the `adb shell` techniques the project's debug-bridge already documents)
4. `curl -X POST localhost:8735/content/reload` — `ok:true`, `sources` shows `override` for `content/world/mutations.json`.
5. Trigger a scene that surfaces a mutation — narrator output uses the patched copy.

## Acceptance

- [ ] `ContentRepository.initialize(context, allowOverride: Boolean)` exists; debug build passes `BuildConfig.DEBUG`, release passes `false`.
- [ ] Override is per-file: missing override files fall through to bundled assets without error.
- [ ] Schema mismatch in override → `SchemaVersionMismatch`; prior bundle is preserved across a failed reload.
- [ ] `GET /content/info` reports schema version, override enabled, override root path, per-file source map.
- [ ] `POST /content/reload` re-runs initialize, returns the new info or a 409 with diagnostic on failure.
- [ ] Hot-reload unit tests pass.
- [ ] `gradle test`, `gradle assembleDebug` pass.
- [ ] App boots; `/content/info` returns sensible JSON; manual override + reload demonstrably changes a value.

## Out of scope (M7)

- Deleting the M2/M3/M4/M5 facades.
- Epic-level seeded-output parity test.
