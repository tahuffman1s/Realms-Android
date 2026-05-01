# Phase 7 — Auto-Update System (design)

Date: 2026-05-01
Issue: [#28](https://github.com/tahuffman1s/RealmsAndroid/issues/28)
Status: Design — awaiting plan

## Problem

Realms ships as a sideloaded signed APK distributed via GitHub Releases (tag-triggered, `realms-vX.Y.Z-release.apk`). Today, users have no in-app indication when a new release is available; they would have to visit the GitHub page manually. We need an in-app system that detects new releases, notifies the user, and walks them through downloading and installing the update.

Out of scope: Play Store distribution, F-Droid, delta/patch updates, background polling, self-uninstall flows.

## Decisions

The shape of this design was settled through brainstorming. The locked-in decisions:

| Question | Decision |
|---|---|
| Install scope | **Notify + download + hand off to system installer.** Adds `REQUEST_INSTALL_PACKAGES`. One-tap update from the user's perspective; Android still confirms via the system install dialog. |
| Update channel | **Toggle in settings.** Default `Include prereleases` while `versionName` is pre-1.0; flip default to `Stable only` after 1.0 ships (config-only change at release time, not version-keyed in code). |
| UI surface | **Title-screen banner + settings entry.** Banner is dismissible per-version. Settings has channel toggle, last-checked timestamp, and a manual "Check now" button. |
| Check cadence | **On launch + manual.** 6h cache prevents rate-limit and traffic on rapid relaunches. No WorkManager / background poll. |
| Debug-build behavior | **Updater runs in debug** with an explicit pre-install dialog warning that signature mismatch will require uninstall (data loss). Detect mismatch by reading the installed package's signing info before launching the install intent; only show the dialog when it actually conflicts. |
| Version comparison | **Semver comparator on `versionName`.** `versionCode` is unchanged (Android still uses it for its own update logic, but we don't rely on it for prerelease ordering). |

## Architecture

A new package under `app/src/main/kotlin/com/realmsoffate/game/data/updater/`:

```
data/updater/
  GitHubReleasesClient.kt   HTTP client for GitHub Releases API
  ReleaseInfo.kt            DTO + parsed semver + asset list
  SemVer.kt                 Semver comparator (pure, unit-tested)
  UpdateChannel.kt          enum { STABLE_ONLY, INCLUDE_PRERELEASES }
  UpdatePrefs.kt            SharedPreferences wrapper
  UpdateRepository.kt       Orchestrates check + download + install
  UpdateState.kt            sealed class for UI consumption
```

UI surfaces under `ui/`:

- `TitleScreen` — adds an `UpdateBanner` composable below the title, above the New/Load tile grid.
- `SettingsSheet` — adds an `UpdateSettingsSection` with channel toggle, current version, "Check now" button, and last-checked timestamp.

Single source of truth: `UpdateRepository` is a process-level singleton constructed in `MainActivity` (or a new `RealmsApplication` class if cleaner during planning) and shared by both UI surfaces via `StateFlow<UpdateState>`.

### Manifest changes

- Add `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>`.
- Register a `FileProvider` with authority `com.realmsoffate.game.updates` and a `cache-path` exposing `updates/` so the install intent can read the downloaded APK from `cacheDir/updates/`.

## Data flow

### Check

Triggered on cold start (from `MainActivity.onCreate`) and from the settings "Check now" button.

1. `UpdateRepository.check(force: Boolean)` — if `!force` and `(now - lastCheckedAt) < 6h`, no network call.
2. `GitHubReleasesClient.fetchReleases()` issues `GET https://api.github.com/repos/tahuffman1s/RealmsAndroid/releases?per_page=10`.
   - Headers: `User-Agent: Realms/<versionName>`, `Accept: application/vnd.github+json`.
   - No auth — public repo, 60 req/hour anonymous limit is fine given 6h cache.
3. Filter the response by current `UpdateChannel`: `STABLE_ONLY` excludes any release with `prerelease: true`.
4. Pick the highest `tag_name` (stripped of leading `v`) by `SemVer.compare`.
5. Compare to `BuildConfig.VERSION_NAME`. If newer → `UpdateState.Available(release)`. Otherwise → `UpdateState.UpToDate`.
6. Persist `lastCheckedAt`. If `release.tag` is in the dismissed-versions set, the state still becomes `Available` but the title-screen banner suppresses itself; settings still shows the row.

### Download + install

Triggered when the user taps "Update" on the banner or the settings row.

1. Find the release asset whose name matches `realms-vX.Y.Z-release.apk`. If absent → `Error("Release missing APK", recoverable=false)`.
2. State → `Downloading(progress=0)`.
3. Stream the asset over OkHttp (already in the project for AI calls — confirm during planning) into `cacheDir/updates/realms-<version>.apk`. Compute SHA-256 incrementally and emit progress events.
4. Validate file size against the asset's declared `size`. Mismatch → delete file, `Error("Download corrupted")`.
5. State → `ReadyToInstall(file)`.
6. Launch `Intent(ACTION_INSTALL_PACKAGE)` with `data = FileProvider.getUriForFile(...)` and `FLAG_GRANT_READ_URI_PERMISSION`. Android shows the standard install dialog. App's job ends here — Android handles signature verification and install.

The `Downloading` state holds an `Int` progress (0–100) so the UI can render a `LinearProgressIndicator`. Cancel is supported by cancelling the coroutine; partial file is deleted on cancellation.

### Debug-build path

Per the Q5 decision, the updater runs in debug builds. Before launching the install intent:

- Read the installed package's signing certificate via `PackageManager`.
- Compare to the signing certificate that would be required to update — in practice, the running build's own cert. If running build is debug-signed and downloaded APK is release-signed, signatures will mismatch.
- If mismatch is detected, show a one-shot AlertDialog: *"Installing the release build will conflict with this debug build's signing certificate. Android will require you to uninstall first, which deletes save data. Continue?"* with `Continue` / `Cancel`.
- If the user cancels, state reverts to `Available`.
- If they continue, launch the install intent anyway. Android's installer will handle the rejection or prompt for uninstall — we don't try to scripted-uninstall ourselves.

Release builds skip this check (their own signature matches by construction).

## Components

### `SemVer`

Pure data class with a comparator. Parses `MAJOR.MINOR.PATCH[-PRERELEASE]` per [semver.org](https://semver.org). Implements §11 ordering:

- Numeric prerelease identifiers compare numerically (`alpha.10 > alpha.2`).
- Alphanumeric identifiers compare lexically.
- A normal release is greater than any prerelease of the same `MAJOR.MINOR.PATCH` (`1.0.0 > 1.0.0-rc.1`).
- Build-metadata suffixes (`+sha`) are ignored for ordering — strip and discard.

~50 lines + ~20 unit tests covering: equality, patch bumps, minor bumps, major bumps, prerelease ordering (`alpha < beta < rc`), numeric vs alphanumeric prerelease ids, `alpha.1 < alpha.2`, `alpha.10 > alpha.2` (numeric, not lexical), release > prerelease at same MMP, malformed input → throws `IllegalArgumentException`.

No third-party dep. Owning ~50 lines beats pulling in a library.

### `GitHubReleasesClient`

Thin OkHttp wrapper. Single suspend method:

```kotlin
suspend fun fetchReleases(): List<ReleaseInfo>
```

Sets `User-Agent: Realms/<versionName>` (GitHub requires it) and `Accept: application/vnd.github+json`. No auth token. Maps the JSON response to `ReleaseInfo(tag, name, prerelease, body, assets: List<Asset>)` where `Asset(name, browserDownloadUrl, size)`.

### `UpdateRepository`

Coroutine-scoped, holds `MutableStateFlow<UpdateState>`. Public API:

```kotlin
fun check(force: Boolean = false)
fun startDownload(release: ReleaseInfo)
fun cancelDownload()
fun dismissBannerForVersion(tag: String)
fun setChannel(channel: UpdateChannel)
val state: StateFlow<UpdateState>
```

Cache rules:
- `UpToDate` result cached for 6h.
- `Available` result persists until install completes or user dismisses.
- `Error(recoverable=true)` extends the next-check delay to 1h to back off (rate-limited or transient network).

### `UpdatePrefs`

Backed by `SharedPreferences("realms_updater", MODE_PRIVATE)`. Keys:

- `channel` — string, default `INCLUDE_PRERELEASES` (will become `STABLE_ONLY` post-1.0).
- `last_checked_at` — long, epoch millis.
- `last_known_release_tag` — string. Used to surface "Available" instantly on next launch before the network round-trip resolves.
- `dismissed_versions` — string set, bounded to 10 entries. Pruned on write (FIFO).

### `UpdateState` (sealed class)

```kotlin
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val release: ReleaseInfo) : UpdateState()
    data class Downloading(val release: ReleaseInfo, val progress: Int) : UpdateState()
    data class ReadyToInstall(val release: ReleaseInfo, val file: File) : UpdateState()
    data class Error(val reason: String, val recoverable: Boolean) : UpdateState()
}
```

### `UpdateBanner` (Compose)

Material card on `TitleScreen`, between title and tile grid. Renders only when state is `Available`, `Downloading`, or `ReadyToInstall` AND the version is not in the dismissed set.

- `Available`: title `Update available: vX.Y.Z`, subtitle = release name, body = first line of release notes (truncated). Buttons: `Update`, `Later` (session dismiss), `Skip this version` (writes to dismissed set).
- `Downloading`: replaces buttons with `LinearProgressIndicator` and a `Cancel` text button.
- `ReadyToInstall`: single `Install` button that re-fires the install intent (in case the user dismissed Android's dialog).

### `UpdateSettingsSection` (Compose)

Added to settings sheet. Rows:

1. **Current version** — `BuildConfig.VERSION_NAME`, read-only.
2. **Update channel** — segmented button or labeled switch: `Stable only` / `Include prereleases`. Writes to prefs and re-runs the check.
3. **Check now** — disabled while `Checking`. Subtitle shows last-checked relative time (`Just now`, `2h ago`, `Yesterday`, `Never`).
4. **Update available** (conditional) — only when state is `Available`/`ReadyToInstall`. Tappable row: `Update to vX.Y.Z`.

## Error handling

Every failure resolves deterministically to an `UpdateState.Error` or back to a known good state. No silent failures, no crashes.

| Failure | State | UI behavior |
|---|---|---|
| No network | `Error("No connection", recoverable=true)` | Settings shows "Offline — try later"; no banner. Auto-retries on next launch. |
| GitHub 5xx / timeout | `Error("Couldn't reach GitHub", recoverable=true)` | Same as above. |
| Rate-limited (403 with `X-RateLimit-Remaining: 0`) | `Error("Rate-limited, try again later", recoverable=true)` | Cache TTL extended to 1h. |
| Malformed release JSON | `Error("Couldn't parse release", recoverable=false)` | Logged with `Log.w`; settings shows "Update check failed"; no banner. |
| No matching APK asset | `Error("Release missing APK", recoverable=false)` | Misconfigured release on our end. Surface in settings, not as a banner. |
| Download interrupted | reverts to `Available(release)` | Partial file deleted; user can retry. |
| SHA / size mismatch | `Error("Download corrupted")` | Delete file, allow retry. |
| User denies install permission at OS level | stays `ReadyToInstall(file)` | They can re-tap. |
| Signature mismatch (debug → release) | `ReadyToInstall(file)` after explicit dialog accept | If they cancel the dialog, revert to `Available`. |

## Testing

JVM / Robolectric tests under `app/src/test/`:

- **`SemVerTest`** — pure unit, ~20 cases as listed above.
- **`UpdateRepositoryTest`** — fakes `GitHubReleasesClient`, `Clock`, `UpdatePrefs`. Cases:
  - Cache hit short-circuits the network call when `force=false` and TTL valid.
  - `force=true` bypasses cache.
  - Channel filter excludes prereleases when `STABLE_ONLY`.
  - Newer prerelease in `INCLUDE_PRERELEASES` surfaces as `Available`.
  - Dismissed version: state is `Available` but `bannerVisible` is false; settings row still surfaces it.
  - Error states populate correctly and don't poison cache (next check still attempts).
  - Cancel during download deletes partial file and reverts state.
- **`GitHubReleasesClientTest`** — `MockWebServer` feeding canned GitHub responses. Save a real fixture from the actual `v0.1.0-alpha.1` release as `app/src/test/resources/github_releases_fixture.json` so the parser is grounded in real shape. Cases: parse success, 403 with rate-limit headers, malformed JSON, missing `assets` array, asset name doesn't match expected pattern.
- **No instrumented `PackageInstaller` tests.** That's Android plumbing. Verified manually per the debug-bridge test procedures: a new `/updater/check` debug endpoint forces a check against an injected mock URL.

Coverage gap accepted: actual `PackageInstaller` interaction (signature dialog, install confirmation) is verified manually once per release.

## Out of scope (explicit)

- WorkManager background polling (would add `POST_NOTIFICATIONS` permission and complicate the model for marginal gain).
- Self-uninstall + reinstall to clear signature mismatch (we surface the dialog and let the user uninstall manually).
- Delta / patch updates (full APK each time — sub-30MB, fine).
- Multi-asset releases / per-ABI splits (the workflow ships one universal APK).
- F-Droid or alternative-store integration.
- Rich in-app changelogs (the banner shows truncated release notes; full body is one tap to GitHub via "View on GitHub" button).

## Risks

- **GitHub rate-limit (60/hr unauthenticated)** — mitigated by 6h cache and 1h back-off on 403. If many users force-check from the same NAT, could conceivably hit. Acceptable.
- **Asset rename drift** — the updater pattern-matches `realms-vX.Y.Z-release.apk`. If `release.yml` ever renames the asset, the updater silently fails the "no matching APK" path. Mitigated by `Error("Release missing APK")` surfacing visibly in settings, plus a code comment in `release.yml` noting the rename is load-bearing for the updater.
- **`REQUEST_INSTALL_PACKAGES` is a sensitive permission** — fine for sideloaded distribution; would block Play Store listing if we ever go there. Phase 7 explicitly assumes sideloaded as the distribution model.
- **First release in the wild has no updater** — `v0.1.0-alpha.1` shipped without this feature. Users on that build won't be auto-notified of the first release that includes the updater; they'll need a manual one-time update. Acceptable since the alpha audience is small.

## Migration

None. New feature, no existing data to migrate. New SharedPreferences file (`realms_updater`) is created lazily on first check.
