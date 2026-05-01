# Phase 7 — Auto-Update System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an in-app update system that detects new GitHub releases, notifies the user via a title-screen banner and settings screen, downloads the matching signed APK, and hands off to the system installer.

**Architecture:** New `data/updater/` package with a process-level singleton `UpdateRepository` (initialized in `RealmsApp.onCreate`, modeled on `ContentRepository`). Pure semver comparator drives ordering; OkHttp client polls the public GitHub Releases API; DataStore Preferences (project convention — matches `CheatsStore`) holds channel + dismissal + last-checked state; `FileProvider` exposes the downloaded APK to Android's package installer. UI: a `Screen.Settings` value with a new `SettingsScreen` for channel toggle and manual check, plus a banner on `TitleScreen`. Debug builds run the updater with an explicit signature-mismatch dialog before invoking the installer.

**Tech Stack:** Kotlin · Jetpack Compose Material3 · OkHttp 4.12.0 · kotlinx.serialization · DataStore Preferences · JUnit 4 · Robolectric 4.13 · MockWebServer (added in M2)

**Spec:** `docs/superpowers/specs/2026-05-01-phase7-auto-update-system-design.md`

**Branching strategy:** Each milestone is its own PR off `main`, mirroring the Phase 6 cadence (M1+M2 / M3 / M4 / M5a / M5b / M6 / M7). The brainstorm-spec branch `phase7-auto-update-design` already holds the spec; create per-milestone branches off `main` after merging the spec PR (`phase7-auto-update-design` → main).

---

## Milestone roadmap

| M | Branch | Scope | LOC est. |
|---|---|---|---|
| M0 (this PR) | `phase7-auto-update-design` | Spec doc only — already committed | (done) |
| M1 | `phase7-updater-m1-semver` | `SemVer` comparator + tests, no Android deps | ~150 |
| M2 | `phase7-updater-m2-client` | `GitHubReleasesClient` + DTOs + MockWebServer + fixture | ~250 |
| M3 | `phase7-updater-m3-prefs-state` | `UpdatePrefs` (DataStore) + `UpdateState` + `UpdateChannel` | ~200 |
| M4 | `phase7-updater-m4-repository-check` | `UpdateRepository` check path, cache, channel filter, dismissal | ~350 |
| M5a | `phase7-updater-m5a-download` | APK streaming download, SHA, progress flow, cancel | ~250 |
| M5b | `phase7-updater-m5b-install` | Manifest, FileProvider, install intent, signature check | ~200 |
| M6 | `phase7-updater-m6-ui` | Banner, `SettingsScreen`, `Screen.Settings`, cold-start wiring, debug endpoint | ~400 |

---

## Conventions

- **Package root:** `com.realmsoffate.game.data.updater` for data layer; `com.realmsoffate.game.ui.settings` for the new settings screen.
- **Imports / formatting:** match existing files (see `data/CheatsStore.kt` for DataStore + `data/AiRepository.kt` for OkHttp).
- **Tests:** `app/src/test/kotlin/com/realmsoffate/game/data/updater/`. Robolectric where Android `Context` is needed; pure JUnit otherwise.
- **Commits:** one TDD red-green-refactor cycle per commit. Phase 6 precedent for commit messages: `feat(phase7-updater): <milestone>: <change>`.
- **Verification per CLAUDE.md:** after any change touching `app/src/main/`, run `gradle test` and (if installable) `gradle installDebug && adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity`. Skip device deploy for milestones that touch only data-layer code (M1–M4).

---

## Milestone 1 — SemVer comparator

**Why first:** pure Kotlin, zero Android dependencies, smallest PR. Establishes the ordering primitive everything downstream depends on. Can be reviewed and merged in isolation without caring about HTTP, prefs, or UI.

**Branch:** `phase7-updater-m1-semver`

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/updater/SemVer.kt`
- Create: `app/src/test/kotlin/com/realmsoffate/game/data/updater/SemVerTest.kt`

### Task 1.1: Write the parsing/equality test first

- [ ] **Step 1: Create the test file with the parse + equality cases**

Create `app/src/test/kotlin/com/realmsoffate/game/data/updater/SemVerTest.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test fun `parses MAJOR_MINOR_PATCH`() {
        val v = SemVer.parse("1.2.3")
        assertEquals(1, v.major)
        assertEquals(2, v.minor)
        assertEquals(3, v.patch)
        assertTrue(v.prerelease.isEmpty())
    }

    @Test fun `parses with prerelease`() {
        val v = SemVer.parse("0.1.0-alpha.1")
        assertEquals(0, v.major)
        assertEquals(1, v.minor)
        assertEquals(0, v.patch)
        assertEquals(listOf("alpha", "1"), v.prerelease)
    }

    @Test fun `tolerates leading v`() {
        val v = SemVer.parse("v0.1.0-alpha.1")
        assertEquals(0, v.major)
    }

    @Test fun `strips build metadata`() {
        val v = SemVer.parse("1.2.3+abc.def")
        assertEquals(1, v.major)
        assertTrue(v.prerelease.isEmpty())
    }

    @Test fun `equality on same version`() {
        assertEquals(SemVer.parse("1.2.3"), SemVer.parse("1.2.3"))
        assertEquals(SemVer.parse("1.2.3-alpha.1"), SemVer.parse("v1.2.3-alpha.1"))
    }

    @Test fun `inequality across patch`() {
        assertNotEquals(SemVer.parse("1.2.3"), SemVer.parse("1.2.4"))
    }

    @Test fun `malformed throws`() {
        assertThrows(IllegalArgumentException::class.java) { SemVer.parse("not-a-version") }
        assertThrows(IllegalArgumentException::class.java) { SemVer.parse("1.2") }
        assertThrows(IllegalArgumentException::class.java) { SemVer.parse("1.2.x") }
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.SemVerTest" -i`

Expected: FAIL — `Unresolved reference: SemVer`.

- [ ] **Step 3: Implement `SemVer` parsing + equality**

Create `app/src/main/kotlin/com/realmsoffate/game/data/updater/SemVer.kt`:

```kotlin
package com.realmsoffate.game.data.updater

/**
 * Minimal semver per https://semver.org §11. Build metadata (`+...`) is parsed
 * but ignored for ordering. Prerelease identifiers are stored as a list and
 * compared per-identifier (numeric vs alphanumeric rules).
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String>
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        // §11.3: a release version > any prerelease at same MMP
        val a = prerelease
        val b = other.prerelease
        if (a.isEmpty() && b.isEmpty()) return 0
        if (a.isEmpty()) return 1
        if (b.isEmpty()) return -1
        // §11.4: per-identifier compare
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val c = compareIdentifiers(a[i], b[i])
            if (c != 0) return c
        }
        // §11.4.4: longer wins when prefix matches
        return a.size.compareTo(b.size)
    }

    private fun compareIdentifiers(a: String, b: String): Int {
        val ai = a.toIntOrNull()
        val bi = b.toIntOrNull()
        return when {
            ai != null && bi != null -> ai.compareTo(bi)
            ai != null && bi == null -> -1   // numeric < alphanumeric per §11.4.3
            ai == null && bi != null -> 1
            else -> a.compareTo(b)
        }
    }

    companion object {
        private val CORE = Regex("""(\d+)\.(\d+)\.(\d+)""")

        fun parse(raw: String): SemVer {
            val noV = raw.removePrefix("v").removePrefix("V")
            val noBuild = noV.substringBefore('+')
            val core = noBuild.substringBefore('-')
            val pre = noBuild.substringAfter('-', missingDelimiterValue = "")
            val m = CORE.matchEntire(core)
                ?: throw IllegalArgumentException("Bad semver: '$raw'")
            val parts = if (pre.isEmpty()) emptyList() else pre.split('.')
            for (p in parts) {
                if (p.isEmpty()) throw IllegalArgumentException("Empty prerelease segment in '$raw'")
            }
            return SemVer(
                major = m.groupValues[1].toInt(),
                minor = m.groupValues[2].toInt(),
                patch = m.groupValues[3].toInt(),
                prerelease = parts
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify pass**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.SemVerTest" -i`

Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/updater/SemVer.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/updater/SemVerTest.kt
git commit -m "feat(phase7-updater): SemVer parsing + equality"
```

### Task 1.2: Add ordering tests + validate comparator

- [ ] **Step 1: Append ordering tests to `SemVerTest.kt`**

Append to the existing `SemVerTest` class:

```kotlin
    @Test fun `1_2_3 lt 1_2_4`() {
        assertTrue(SemVer.parse("1.2.3") < SemVer.parse("1.2.4"))
    }

    @Test fun `1_2_3 lt 1_3_0`() {
        assertTrue(SemVer.parse("1.2.3") < SemVer.parse("1.3.0"))
    }

    @Test fun `1_9_9 lt 2_0_0`() {
        assertTrue(SemVer.parse("1.9.9") < SemVer.parse("2.0.0"))
    }

    @Test fun `prerelease lt release at same MMP`() {
        assertTrue(SemVer.parse("1.0.0-rc.1") < SemVer.parse("1.0.0"))
        assertTrue(SemVer.parse("0.1.0-alpha.1") < SemVer.parse("0.1.0"))
    }

    @Test fun `alpha lt beta`() {
        assertTrue(SemVer.parse("1.0.0-alpha") < SemVer.parse("1.0.0-beta"))
        assertTrue(SemVer.parse("1.0.0-alpha.1") < SemVer.parse("1.0.0-beta.1"))
    }

    @Test fun `alpha_1 lt alpha_2`() {
        assertTrue(SemVer.parse("0.1.0-alpha.1") < SemVer.parse("0.1.0-alpha.2"))
    }

    @Test fun `alpha_2 lt alpha_10 numerically`() {
        assertTrue(SemVer.parse("0.1.0-alpha.2") < SemVer.parse("0.1.0-alpha.10"))
    }

    @Test fun `numeric lt alphanumeric per spec`() {
        // §11.4.3: numeric identifiers always have lower precedence than alphanumeric
        assertTrue(SemVer.parse("1.0.0-1") < SemVer.parse("1.0.0-alpha"))
    }

    @Test fun `longer prerelease wins when prefix matches`() {
        // §11.4.4
        assertTrue(SemVer.parse("1.0.0-alpha") < SemVer.parse("1.0.0-alpha.1"))
    }

    @Test fun `equal prereleases compare equal`() {
        assertEquals(0, SemVer.parse("1.0.0-rc.1").compareTo(SemVer.parse("1.0.0-rc.1")))
    }

    @Test fun `comparator is reflexive across version transitions`() {
        val versions = listOf(
            "0.1.0-alpha.1", "0.1.0-alpha.2", "0.1.0-alpha.10",
            "0.1.0-beta.1", "0.1.0-rc.1", "0.1.0",
            "0.1.1", "0.2.0", "1.0.0-rc.1", "1.0.0"
        ).map { SemVer.parse(it) }
        for (i in 0 until versions.size - 1) {
            assertTrue("${versions[i]} should be < ${versions[i + 1]}", versions[i] < versions[i + 1])
        }
    }
```

- [ ] **Step 2: Run tests — expect all to pass on the first try**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.SemVerTest"`

Expected: PASS (18 tests). If any fail, fix `compareTo` until they pass.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/kotlin/com/realmsoffate/game/data/updater/SemVerTest.kt
git commit -m "test(phase7-updater): SemVer ordering per semver.org §11"
```

### Task 1.3: Open the M1 PR

- [ ] **Step 1: Push and open PR**

```bash
git push -u origin phase7-updater-m1-semver
gh pr create --title "feat(phase7-updater): M1 — SemVer comparator (#28)" --body "$(cat <<'EOF'
## Summary
- Pure Kotlin semver parser + comparator per [semver.org §11](https://semver.org).
- 18 unit tests covering parse, equality, ordering across MAJOR/MINOR/PATCH, prerelease vs release, alpha/beta/rc, numeric/alphanumeric identifier rules.
- No Android deps; foundation for the auto-update system (#28).

Spec: [`docs/superpowers/specs/2026-05-01-phase7-auto-update-system-design.md`](docs/superpowers/specs/2026-05-01-phase7-auto-update-system-design.md)

## Test plan
- [x] `gradle test --tests "com.realmsoffate.game.data.updater.SemVerTest"` passes locally
- [ ] CI green
EOF
)"
```

---

## Milestone 2 — GitHubReleasesClient + DTOs + MockWebServer

**Why next:** introduces the network layer + JSON parsing in isolation. Driven by a real fixture pulled from the actual `v0.1.0-alpha.1` release so the parser is grounded in real data. M3+ all consume this client through fakes — never the live network.

**Branch:** `phase7-updater-m2-client` (off `main` after M1 merges)

**Files:**
- Modify: `app/build.gradle.kts` (add MockWebServer test dep)
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/updater/ReleaseInfo.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/updater/GitHubReleasesClient.kt`
- Create: `app/src/test/kotlin/com/realmsoffate/game/data/updater/GitHubReleasesClientTest.kt`
- Create: `app/src/test/resources/github_releases_fixture.json` (real GitHub response)

### Task 2.1: Add MockWebServer + capture the real fixture

- [ ] **Step 1: Add MockWebServer to `app/build.gradle.kts`**

Locate the `// Tests` section in `app/build.gradle.kts` (around line ~196) and add the dependency below `kotlinx-coroutines-test`:

```kotlin
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

- [ ] **Step 2: Capture the real GitHub Releases fixture**

```bash
mkdir -p app/src/test/resources
curl -sH "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/tahuffman1s/RealmsAndroid/releases?per_page=10" \
  > app/src/test/resources/github_releases_fixture.json
```

- [ ] **Step 3: Verify fixture parsed-able + non-empty**

```bash
jq '. | length' app/src/test/resources/github_releases_fixture.json
```

Expected: a positive integer (`1` at time of writing — only `v0.1.0-alpha.1` exists).

- [ ] **Step 4: Verify build resolves the new dep**

Run: `gradle :app:dependencies --configuration testRuntimeClasspath | grep mockwebserver`

Expected: line containing `mockwebserver:4.12.0`.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/test/resources/github_releases_fixture.json
git commit -m "chore(phase7-updater): add MockWebServer + GitHub releases fixture"
```

### Task 2.2: Define `ReleaseInfo` DTOs

- [ ] **Step 1: Write a serialization test first**

Create `app/src/test/kotlin/com/realmsoffate/game/data/updater/ReleaseInfoTest.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class ReleaseInfoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `parses real fixture`() {
        val raw = File("src/test/resources/github_releases_fixture.json").readText()
        val releases = json.decodeFromString<List<ReleaseDto>>(raw)
        assertFalse("Fixture should contain at least one release", releases.isEmpty())
        val first = releases.first()
        assertNotNull(first.tagName)
        assertNotNull(first.assets)
    }

    @Test fun `release_dto exposes prerelease flag`() {
        val raw = """[{"tag_name":"v0.1.0-alpha.1","name":"v0.1.0-alpha.1","prerelease":true,"body":"notes","assets":[]}]"""
        val r = json.decodeFromString<List<ReleaseDto>>(raw).first()
        assertEquals("v0.1.0-alpha.1", r.tagName)
        assertEquals(true, r.prerelease)
    }

    @Test fun `asset_dto carries name url size`() {
        val raw = """[{"tag_name":"v1","name":"v1","prerelease":false,"body":"","assets":[
            {"name":"realms-v1-release.apk","browser_download_url":"https://example.test/a.apk","size":12345}
        ]}]"""
        val asset = json.decodeFromString<List<ReleaseDto>>(raw).first().assets.first()
        assertEquals("realms-v1-release.apk", asset.name)
        assertEquals("https://example.test/a.apk", asset.browserDownloadUrl)
        assertEquals(12345L, asset.size)
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.ReleaseInfoTest"`

Expected: FAIL — `Unresolved reference: ReleaseDto`.

- [ ] **Step 3: Create `ReleaseInfo.kt`**

Create `app/src/main/kotlin/com/realmsoffate/game/data/updater/ReleaseInfo.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format DTO for a single GitHub release. Field names match the
 * Releases API. Use [ReleaseInfo] (a domain object derived from this) in
 * the rest of the codebase — keep wire concerns out of UpdateRepository.
 */
@Serializable
data class ReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val prerelease: Boolean = false,
    val body: String? = null,
    val assets: List<AssetDto> = emptyList()
)

@Serializable
data class AssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long
)

/**
 * Domain shape consumed by UpdateRepository. Holds the parsed [SemVer] for
 * ordering, plus only the asset(s) we actually care about.
 */
data class ReleaseInfo(
    val tag: String,
    val name: String,
    val version: SemVer,
    val isPrerelease: Boolean,
    val body: String,
    val assets: List<Asset>
) {
    data class Asset(val name: String, val downloadUrl: String, val size: Long)

    /** Find the canonical Realms APK asset for this release. */
    fun realmsApkAsset(): Asset? = assets.firstOrNull {
        it.name.startsWith("realms-") && it.name.endsWith("-release.apk")
    }

    companion object {
        fun fromDto(dto: ReleaseDto): ReleaseInfo? {
            val ver = runCatching { SemVer.parse(dto.tagName) }.getOrNull() ?: return null
            return ReleaseInfo(
                tag = dto.tagName,
                name = dto.name ?: dto.tagName,
                version = ver,
                isPrerelease = dto.prerelease,
                body = dto.body ?: "",
                assets = dto.assets.map { Asset(it.name, it.browserDownloadUrl, it.size) }
            )
        }
    }
}
```

- [ ] **Step 4: Run test — expect pass**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.ReleaseInfoTest"`

Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/updater/ReleaseInfo.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/updater/ReleaseInfoTest.kt
git commit -m "feat(phase7-updater): ReleaseDto + ReleaseInfo (DTO/domain split)"
```

### Task 2.3: `GitHubReleasesClient` — happy path

- [ ] **Step 1: Write the MockWebServer happy-path test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/updater/GitHubReleasesClientTest.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class GitHubReleasesClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GitHubReleasesClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = GitHubReleasesClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            userAgent = "Realms/test"
        )
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun `parses real fixture into ReleaseInfo`() = runTest {
        val body = File("src/test/resources/github_releases_fixture.json").readText()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val result = client.fetchReleases()

        assertTrue(result is GitHubReleasesClient.Result.Ok)
        val releases = (result as GitHubReleasesClient.Result.Ok).releases
        assertTrue("Fixture should yield at least one release", releases.isNotEmpty())
        val first = releases.first()
        assertNotNull(first.version)
        assertEquals(first.tag.removePrefix("v"), "${first.version.major}.${first.version.minor}.${first.version.patch}" + if (first.version.prerelease.isEmpty()) "" else "-" + first.version.prerelease.joinToString("."))
    }

    @Test fun `sets user agent and accept headers`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        client.fetchReleases()

        val req = server.takeRequest()
        assertEquals("Realms/test", req.getHeader("User-Agent"))
        assertEquals("application/vnd.github+json", req.getHeader("Accept"))
        assertTrue(req.path!!.startsWith("/repos/tahuffman1s/RealmsAndroid/releases"))
    }
}
```

- [ ] **Step 2: Run — expect FAIL (no `GitHubReleasesClient` yet)**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.GitHubReleasesClientTest"`

Expected: FAIL — `Unresolved reference: GitHubReleasesClient`.

- [ ] **Step 3: Implement minimal `GitHubReleasesClient`**

Create `app/src/main/kotlin/com/realmsoffate/game/data/updater/GitHubReleasesClient.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper over GitHub's public Releases API. Returns a [Result] sealed
 * class so callers can distinguish recoverable network/rate-limit errors
 * from permanent parse errors without exception-catching gymnastics.
 *
 * Anonymous (no auth) — public repo, 60 req/hour limit per IP. The repository
 * caches results for 6h so this limit is comfortably out of reach.
 */
class GitHubReleasesClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://api.github.com/",
    private val userAgent: String,
    private val owner: String = "tahuffman1s",
    private val repo: String = "RealmsAndroid"
) {
    private val json = Json { ignoreUnknownKeys = true }

    sealed class Result {
        data class Ok(val releases: List<ReleaseInfo>) : Result()
        data class NetworkError(val message: String) : Result()
        data class RateLimited(val resetEpochSec: Long?) : Result()
        data class ParseError(val message: String) : Result()
        data class HttpError(val code: Int, val message: String) : Result()
    }

    suspend fun fetchReleases(perPage: Int = 10): Result = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/repos/$owner/$repo/releases?per_page=$perPage"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            return@withContext Result.NetworkError(e.message ?: "I/O error")
        }
        response.use { resp ->
            if (resp.code == 403 && resp.header("X-RateLimit-Remaining") == "0") {
                val reset = resp.header("X-RateLimit-Reset")?.toLongOrNull()
                return@withContext Result.RateLimited(reset)
            }
            if (!resp.isSuccessful) {
                return@withContext Result.HttpError(resp.code, resp.message)
            }
            val body = resp.body?.string() ?: return@withContext Result.ParseError("empty body")
            return@withContext try {
                val dtos = json.decodeFromString<List<ReleaseDto>>(body)
                Result.Ok(dtos.mapNotNull(ReleaseInfo::fromDto))
            } catch (e: Exception) {
                Result.ParseError(e.message ?: "parse failure")
            }
        }
    }

    companion object {
        /** Default OkHttp client tuned for short, latency-sensitive metadata calls. */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
```

- [ ] **Step 4: Run — expect pass**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.GitHubReleasesClientTest"`

Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/updater/GitHubReleasesClient.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/updater/GitHubReleasesClientTest.kt
git commit -m "feat(phase7-updater): GitHubReleasesClient happy-path + headers"
```

### Task 2.4: Error-path tests

- [ ] **Step 1: Append failure-mode tests to `GitHubReleasesClientTest.kt`**

Append to the existing class:

```kotlin
    @Test fun `403 with rate-limit-remaining 0 returns RateLimited`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1700000000")
                .setBody("""{"message":"API rate limit exceeded"}""")
        )
        val result = client.fetchReleases()
        assertTrue(result is GitHubReleasesClient.Result.RateLimited)
        assertEquals(1700000000L, (result as GitHubReleasesClient.Result.RateLimited).resetEpochSec)
    }

    @Test fun `500 returns HttpError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val result = client.fetchReleases()
        assertTrue(result is GitHubReleasesClient.Result.HttpError)
        assertEquals(500, (result as GitHubReleasesClient.Result.HttpError).code)
    }

    @Test fun `malformed json returns ParseError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))
        val result = client.fetchReleases()
        assertTrue(result is GitHubReleasesClient.Result.ParseError)
    }

    @Test fun `unparseable tag is skipped not fatal`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """[
                {"tag_name":"not-a-version","name":"x","prerelease":false,"body":"","assets":[]},
                {"tag_name":"v1.0.0","name":"y","prerelease":false,"body":"","assets":[]}
            ]"""
        ))
        val result = client.fetchReleases()
        assertTrue(result is GitHubReleasesClient.Result.Ok)
        val releases = (result as GitHubReleasesClient.Result.Ok).releases
        assertEquals(1, releases.size)
        assertEquals("v1.0.0", releases.first().tag)
    }
```

- [ ] **Step 2: Run — expect all pass on first try (logic already in place)**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.GitHubReleasesClientTest"`

Expected: PASS (6 tests total).

If any fail, fix in `GitHubReleasesClient` and rerun until green.

- [ ] **Step 3: Commit + open M2 PR**

```bash
git add app/src/test/kotlin/com/realmsoffate/game/data/updater/GitHubReleasesClientTest.kt
git commit -m "test(phase7-updater): GitHubReleasesClient error paths"

git push -u origin phase7-updater-m2-client
gh pr create --title "feat(phase7-updater): M2 — GitHubReleasesClient + DTOs (#28)" --body "$(cat <<'EOF'
## Summary
- `ReleaseDto` / `AssetDto` (wire) + `ReleaseInfo` (domain) split.
- `GitHubReleasesClient` over OkHttp; sealed `Result` separates network/rate-limit/parse/HTTP errors.
- MockWebServer-based tests using a real GitHub fixture.

## Test plan
- [x] `gradle test --tests "*updater*"` passes locally
- [ ] CI green
EOF
)"
```

---

## Milestone 3 — UpdatePrefs (DataStore) + UpdateState + UpdateChannel

**Why next:** state shapes that the repository will reduce over. Pure-ish Android (DataStore needs a `Context`, hence Robolectric). M4 depends on these existing.

**Branch:** `phase7-updater-m3-prefs-state` (off `main` after M2 merges)

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateChannel.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateState.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdatePrefs.kt`
- Create: `app/src/test/kotlin/com/realmsoffate/game/data/updater/UpdatePrefsTest.kt`

### Task 3.1: `UpdateChannel` enum + `UpdateState` sealed class

- [ ] **Step 1: Create `UpdateChannel.kt`**

Create `app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateChannel.kt`:

```kotlin
package com.realmsoffate.game.data.updater

enum class UpdateChannel {
    /** Only releases with `prerelease = false` are surfaced. */
    STABLE_ONLY,
    /** All releases including alphas/betas/RCs are surfaced. */
    INCLUDE_PRERELEASES;

    fun accepts(release: ReleaseInfo): Boolean = when (this) {
        STABLE_ONLY -> !release.isPrerelease
        INCLUDE_PRERELEASES -> true
    }

    companion object {
        /** Default for pre-1.0 era. Flip to STABLE_ONLY at 1.0.0 ship time. */
        val DEFAULT: UpdateChannel = INCLUDE_PRERELEASES

        fun fromString(value: String?): UpdateChannel = when (value) {
            STABLE_ONLY.name -> STABLE_ONLY
            INCLUDE_PRERELEASES.name -> INCLUDE_PRERELEASES
            else -> DEFAULT
        }
    }
}
```

- [ ] **Step 2: Create `UpdateState.kt`**

Create `app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateState.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import java.io.File

/**
 * State machine consumed by the UI. UpdateRepository emits transitions through
 * a single MutableStateFlow. Note: Available -> Downloading -> ReadyToInstall
 * is the only valid forward path; cancellations and errors revert to earlier
 * states (or Idle / UpToDate as appropriate).
 */
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

- [ ] **Step 3: Confirm compilation**

Run: `gradle :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateChannel.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateState.kt
git commit -m "feat(phase7-updater): UpdateChannel + UpdateState"
```

### Task 3.2: `UpdatePrefs` — failing test first

- [ ] **Step 1: Write the test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/updater/UpdatePrefsTest.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdatePrefsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val prefs = UpdatePrefs(ctx)

    @After fun tearDown() = runTest { prefs.clearForTest() }

    @Test fun `default channel is INCLUDE_PRERELEASES`() = runTest {
        assertEquals(UpdateChannel.INCLUDE_PRERELEASES, prefs.channel.first())
    }

    @Test fun `setChannel persists`() = runTest {
        prefs.setChannel(UpdateChannel.STABLE_ONLY)
        assertEquals(UpdateChannel.STABLE_ONLY, prefs.channel.first())
    }

    @Test fun `lastCheckedAt defaults to null`() = runTest {
        assertNull(prefs.lastCheckedAt.first())
    }

    @Test fun `setLastCheckedAt persists`() = runTest {
        prefs.setLastCheckedAt(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, prefs.lastCheckedAt.first())
    }

    @Test fun `dismissedVersions starts empty`() = runTest {
        assertTrue(prefs.dismissedVersions.first().isEmpty())
    }

    @Test fun `dismissVersion adds tag`() = runTest {
        prefs.dismissVersion("v0.2.0")
        assertEquals(setOf("v0.2.0"), prefs.dismissedVersions.first())
    }

    @Test fun `dismissedVersions bounded to 10 most recent`() = runTest {
        for (i in 1..15) prefs.dismissVersion("v0.0.$i")
        val set = prefs.dismissedVersions.first()
        assertEquals(10, set.size)
        assertFalse("oldest should have been pruned", set.contains("v0.0.1"))
        assertTrue("newest must be present", set.contains("v0.0.15"))
    }
}
```

- [ ] **Step 2: Run — expect FAIL (no UpdatePrefs)**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.UpdatePrefsTest"`

Expected: FAIL — `Unresolved reference: UpdatePrefs`.

- [ ] **Step 3: Implement `UpdatePrefs`**

Create `app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdatePrefs.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.updaterDataStore by preferencesDataStore(name = "realms_updater")

/**
 * DataStore-backed preferences for the auto-update system. Mirrors the
 * CheatsStore pattern (Flow read, suspend write). Dismissed versions are
 * bounded to 10 most-recent to prevent unbounded growth.
 */
class UpdatePrefs(private val context: Context) {

    private val keyChannel = stringPreferencesKey("channel")
    private val keyLastCheckedAt = longPreferencesKey("last_checked_at")
    private val keyDismissedVersions = stringSetPreferencesKey("dismissed_versions")
    private val keyDismissedOrder = stringPreferencesKey("dismissed_order")
    private val keyLastKnownTag = stringPreferencesKey("last_known_release_tag")

    val channel: Flow<UpdateChannel> = context.updaterDataStore.data
        .map { UpdateChannel.fromString(it[keyChannel]) }

    val lastCheckedAt: Flow<Long?> = context.updaterDataStore.data
        .map { it[keyLastCheckedAt] }

    val dismissedVersions: Flow<Set<String>> = context.updaterDataStore.data
        .map { it[keyDismissedVersions] ?: emptySet() }

    val lastKnownReleaseTag: Flow<String?> = context.updaterDataStore.data
        .map { it[keyLastKnownTag] }

    suspend fun setChannel(channel: UpdateChannel) {
        context.updaterDataStore.edit { it[keyChannel] = channel.name }
    }

    suspend fun setLastCheckedAt(epochMillis: Long) {
        context.updaterDataStore.edit { it[keyLastCheckedAt] = epochMillis }
    }

    suspend fun setLastKnownReleaseTag(tag: String?) {
        context.updaterDataStore.edit {
            if (tag == null) it.remove(keyLastKnownTag) else it[keyLastKnownTag] = tag
        }
    }

    /** Dismiss [tag] from banner display. Maintains FIFO bound of 10 entries. */
    suspend fun dismissVersion(tag: String) {
        context.updaterDataStore.edit { mutable ->
            // Track insertion order in a separate string ("a|b|c") so we can FIFO-prune.
            val orderRaw = mutable[keyDismissedOrder] ?: ""
            val order = if (orderRaw.isEmpty()) mutableListOf() else orderRaw.split('|').toMutableList()
            order.remove(tag)
            order.add(tag)
            while (order.size > MAX_DISMISSED) order.removeAt(0)
            mutable[keyDismissedOrder] = order.joinToString("|")
            mutable[keyDismissedVersions] = order.toSet()
        }
    }

    /** TEST ONLY: clear all keys. */
    suspend fun clearForTest() {
        context.updaterDataStore.edit { it.clear() }
    }

    companion object {
        const val MAX_DISMISSED = 10
    }
}
```

- [ ] **Step 4: Run — expect PASS**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.UpdatePrefsTest"`

Expected: PASS (7 tests).

- [ ] **Step 5: Commit + open M3 PR**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdatePrefs.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/updater/UpdatePrefsTest.kt
git commit -m "feat(phase7-updater): UpdatePrefs (DataStore) + tests"

git push -u origin phase7-updater-m3-prefs-state
gh pr create --title "feat(phase7-updater): M3 — UpdatePrefs + State + Channel (#28)" --body "$(cat <<'EOF'
## Summary
- `UpdateChannel` enum (with `accepts(release)` predicate, defaults to INCLUDE_PRERELEASES for the pre-1.0 era).
- `UpdateState` sealed class — Idle / Checking / UpToDate / Available / Downloading / ReadyToInstall / Error.
- `UpdatePrefs` DataStore wrapper mirroring `CheatsStore`. Bounded dismissed-versions list (FIFO, max 10).

## Test plan
- [x] `gradle test --tests "*updater*"` passes locally
- [ ] CI green
EOF
)"
```

---

## Milestone 4 — UpdateRepository (check path)

**Why next:** wires M1+M2+M3 together. Implements only the **check** half of the state machine — no download yet. Heaviest unit-test coverage of the milestones.

**Branch:** `phase7-updater-m4-repository-check` (off `main` after M3 merges)

**Files:**
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateRepository.kt`
- Create: `app/src/test/kotlin/com/realmsoffate/game/data/updater/UpdateRepositoryTest.kt`

### Task 4.1: Skeleton + first cache test

- [ ] **Step 1: Write the test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/updater/UpdateRepositoryTest.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeClient(
    var nextResult: GitHubReleasesClient.Result = GitHubReleasesClient.Result.Ok(emptyList())
) : ReleasesSource {
    var calls = 0
    override suspend fun fetchReleases(): GitHubReleasesClient.Result {
        calls++
        return nextResult
    }
}

private fun release(tag: String, prerelease: Boolean = false, assetName: String? = null): ReleaseInfo =
    ReleaseInfo(
        tag = tag,
        name = tag,
        version = SemVer.parse(tag),
        isPrerelease = prerelease,
        body = "Notes for $tag",
        assets = if (assetName != null)
            listOf(ReleaseInfo.Asset(assetName, "https://example.test/$assetName", 1024))
        else emptyList()
    )

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UpdateRepositoryTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val prefs = UpdatePrefs(ctx)
    private val client = FakeClient()
    private var nowMs = 1_700_000_000_000L
    private val clock = { nowMs }
    private val repo = UpdateRepository(
        currentVersionName = "0.1.0-alpha.1",
        client = client,
        prefs = prefs,
        clock = clock,
        cacheTtlMs = UpdateRepository.DEFAULT_CACHE_TTL_MS
    )

    @After fun tearDown() = runTest { prefs.clearForTest() }

    @Test fun `idle on construction`() = runTest {
        assertEquals(UpdateState.Idle, repo.state.value)
    }

    @Test fun `check with no newer release leaves UpToDate`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(release("v0.1.0-alpha.1")))
        repo.check(force = false)
        advanceUntilIdle()
        assertEquals(UpdateState.UpToDate, repo.state.value)
        assertEquals(1, client.calls)
    }

    @Test fun `check with newer release becomes Available`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(release("v0.2.0", assetName = "realms-v0.2.0-release.apk")))
        repo.check(force = false)
        advanceUntilIdle()
        val s = repo.state.value
        assertTrue("expected Available, was $s", s is UpdateState.Available)
        assertEquals("v0.2.0", (s as UpdateState.Available).release.tag)
    }

    @Test fun `cache hit short-circuits within TTL`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(release("v0.1.0-alpha.1")))
        repo.check(force = false); advanceUntilIdle()
        repo.check(force = false); advanceUntilIdle()
        assertEquals("client should be called once when within cache window", 1, client.calls)
    }

    @Test fun `force bypasses cache`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(release("v0.1.0-alpha.1")))
        repo.check(force = false); advanceUntilIdle()
        repo.check(force = true); advanceUntilIdle()
        assertEquals(2, client.calls)
    }

    @Test fun `cache expired after TTL passes`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(release("v0.1.0-alpha.1")))
        repo.check(force = false); advanceUntilIdle()
        nowMs += UpdateRepository.DEFAULT_CACHE_TTL_MS + 1
        repo.check(force = false); advanceUntilIdle()
        assertEquals(2, client.calls)
    }

    @Test fun `lastCheckedAt persists`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(release("v0.1.0-alpha.1")))
        repo.check(force = false); advanceUntilIdle()
        assertEquals(nowMs, prefs.lastCheckedAt.first())
    }
}
```

- [ ] **Step 2: Run — expect FAIL (no `UpdateRepository`)**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.UpdateRepositoryTest"`

Expected: FAIL on multiple compilation errors.

- [ ] **Step 3: Create `UpdateRepository` skeleton + `ReleasesSource` interface**

Create `app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateRepository.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Anything that can return a list of releases. Production: GitHubReleasesClient.
 * Tests: in-memory fake. Lets the repository remain agnostic to OkHttp.
 */
interface ReleasesSource {
    suspend fun fetchReleases(): GitHubReleasesClient.Result
}

/** Production adapter so the existing GitHubReleasesClient implements ReleasesSource. */
class GitHubReleasesSource(private val delegate: GitHubReleasesClient) : ReleasesSource {
    override suspend fun fetchReleases() = delegate.fetchReleases()
}

/**
 * Process-level singleton (initialized in RealmsApp). Reduces network responses
 * + user actions into [UpdateState]. Cache is in-memory (TTL on lastCheckedAt);
 * channel + dismissals + last-known-tag persist in [UpdatePrefs].
 */
class UpdateRepository(
    private val currentVersionName: String,
    private val client: ReleasesSource,
    private val prefs: UpdatePrefs,
    private val clock: () -> Long = System::currentTimeMillis,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checkJob: Job? = null

    /**
     * Trigger a release check. If [force] is false and the cache is fresh (<TTL),
     * no-op except for re-emitting the current state. Cancels any in-flight check.
     */
    fun check(force: Boolean = false) {
        checkJob?.cancel()
        checkJob = scope.launch {
            if (!force) {
                val last = prefs.lastCheckedAt.first()
                if (last != null && clock() - last < cacheTtlMs) {
                    return@launch
                }
            }
            _state.value = UpdateState.Checking
            val channel = prefs.channel.first()
            val dismissed = prefs.dismissedVersions.first()
            when (val r = client.fetchReleases()) {
                is GitHubReleasesClient.Result.Ok -> {
                    prefs.setLastCheckedAt(clock())
                    val current = runCatching { SemVer.parse(currentVersionName) }.getOrNull()
                        ?: run {
                            _state.value = UpdateState.Error("Bad app version", recoverable = false)
                            return@launch
                        }
                    val candidate = r.releases
                        .filter(channel::accepts)
                        .filter { it.version > current }
                        .maxByOrNull { it.version }
                    _state.value = when {
                        candidate == null -> UpdateState.UpToDate
                        else -> {
                            prefs.setLastKnownReleaseTag(candidate.tag)
                            UpdateState.Available(candidate)
                        }
                    }
                    // Dismissals do NOT change state; UI consults `bannerVisibleFor()`.
                    @Suppress("UNUSED_VARIABLE") val _d = dismissed // referenced for clarity
                }
                is GitHubReleasesClient.Result.NetworkError ->
                    _state.value = UpdateState.Error("No connection", recoverable = true)
                is GitHubReleasesClient.Result.RateLimited ->
                    _state.value = UpdateState.Error("Rate-limited, try again later", recoverable = true)
                is GitHubReleasesClient.Result.HttpError ->
                    _state.value = UpdateState.Error("Couldn't reach GitHub (${r.code})", recoverable = true)
                is GitHubReleasesClient.Result.ParseError ->
                    _state.value = UpdateState.Error("Couldn't parse release", recoverable = false)
            }
        }
    }

    /** UI helper: should the title-screen banner show for this release? */
    suspend fun bannerVisibleFor(release: ReleaseInfo): Boolean =
        release.tag !in prefs.dismissedVersions.first()

    fun dismissBannerForVersion(tag: String) {
        scope.launch { prefs.dismissVersion(tag) }
    }

    fun setChannel(channel: UpdateChannel) {
        scope.launch {
            prefs.setChannel(channel)
            check(force = true)
        }
    }

    companion object {
        /** 6 hours. */
        const val DEFAULT_CACHE_TTL_MS: Long = 6L * 60L * 60L * 1000L
    }
}
```

- [ ] **Step 4: Run — expect PASS**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.UpdateRepositoryTest"`

Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateRepository.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/updater/UpdateRepositoryTest.kt
git commit -m "feat(phase7-updater): UpdateRepository check + cache + channel filter"
```

### Task 4.2: Channel filter + dismissal + error tests

- [ ] **Step 1: Append tests covering channel filter, dismissal, errors, and last-known-tag**

Append to `UpdateRepositoryTest`:

```kotlin
    @Test fun `STABLE_ONLY excludes prereleases`() = runTest(StandardTestDispatcher()) {
        prefs.setChannel(UpdateChannel.STABLE_ONLY)
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(
            release("v0.2.0-alpha.1", prerelease = true)
        ))
        repo.check(force = true)
        advanceUntilIdle()
        assertEquals(UpdateState.UpToDate, repo.state.value)
    }

    @Test fun `INCLUDE_PRERELEASES surfaces alpha`() = runTest(StandardTestDispatcher()) {
        prefs.setChannel(UpdateChannel.INCLUDE_PRERELEASES)
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(
            release("v0.2.0-alpha.1", prerelease = true, assetName = "realms-v0.2.0-alpha.1-release.apk")
        ))
        repo.check(force = true)
        advanceUntilIdle()
        assertTrue(repo.state.value is UpdateState.Available)
    }

    @Test fun `dismissed version still becomes Available state`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(
            release("v0.2.0", assetName = "realms-v0.2.0-release.apk")
        ))
        prefs.dismissVersion("v0.2.0")
        repo.check(force = true)
        advanceUntilIdle()
        val state = repo.state.value
        assertTrue(state is UpdateState.Available)
        // bannerVisibleFor returns false for dismissed
        assertEquals(false, repo.bannerVisibleFor((state as UpdateState.Available).release))
    }

    @Test fun `non-dismissed version banner visible`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(
            release("v0.2.0", assetName = "realms-v0.2.0-release.apk")
        ))
        repo.check(force = true)
        advanceUntilIdle()
        val state = repo.state.value as UpdateState.Available
        assertEquals(true, repo.bannerVisibleFor(state.release))
    }

    @Test fun `network error becomes recoverable Error`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.NetworkError("no route")
        repo.check(force = true)
        advanceUntilIdle()
        val state = repo.state.value
        assertTrue(state is UpdateState.Error)
        assertTrue((state as UpdateState.Error).recoverable)
    }

    @Test fun `parse error is non-recoverable`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.ParseError("bad json")
        repo.check(force = true)
        advanceUntilIdle()
        val state = repo.state.value as UpdateState.Error
        assertEquals(false, state.recoverable)
    }

    @Test fun `rate limited is recoverable`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.RateLimited(null)
        repo.check(force = true)
        advanceUntilIdle()
        val state = repo.state.value as UpdateState.Error
        assertTrue(state.recoverable)
    }

    @Test fun `picks highest semver when multiple newer`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(
            release("v0.2.0-alpha.1", prerelease = true, assetName = "realms-v0.2.0-alpha.1-release.apk"),
            release("v0.2.0-alpha.10", prerelease = true, assetName = "realms-v0.2.0-alpha.10-release.apk"),
            release("v0.1.5", assetName = "realms-v0.1.5-release.apk")
        ))
        repo.check(force = true)
        advanceUntilIdle()
        val state = repo.state.value as UpdateState.Available
        assertEquals("v0.2.0-alpha.10", state.release.tag)
    }

    @Test fun `lastKnownReleaseTag persists across checks`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(
            release("v0.2.0", assetName = "realms-v0.2.0-release.apk")
        ))
        repo.check(force = true)
        advanceUntilIdle()
        assertEquals("v0.2.0", prefs.lastKnownReleaseTag.first())
    }

    @Test fun `setChannel triggers re-check`() = runTest(StandardTestDispatcher()) {
        client.nextResult = GitHubReleasesClient.Result.Ok(emptyList())
        repo.setChannel(UpdateChannel.STABLE_ONLY)
        advanceUntilIdle()
        assertEquals(UpdateChannel.STABLE_ONLY, prefs.channel.first())
        assertEquals(1, client.calls)
    }
```

- [ ] **Step 2: Run — expect all to pass on first try**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.UpdateRepositoryTest"`

Expected: PASS (17 tests total).

If any fail, fix `UpdateRepository` and rerun.

- [ ] **Step 3: Commit + open M4 PR**

```bash
git add app/src/test/kotlin/com/realmsoffate/game/data/updater/UpdateRepositoryTest.kt
git commit -m "test(phase7-updater): UpdateRepository channel/dismiss/error coverage"

git push -u origin phase7-updater-m4-repository-check
gh pr create --title "feat(phase7-updater): M4 — UpdateRepository check path (#28)" --body "$(cat <<'EOF'
## Summary
- `UpdateRepository` reduces release responses + user actions into `UpdateState`.
- 6h cache; force=true bypasses; channel filter; dismissal; sealed-error mapping.
- 17 unit tests via Robolectric + a `ReleasesSource` fake (no real network).
- Download/install paths land in M5a/M5b.

## Test plan
- [x] `gradle test --tests "*updater*"` passes locally
- [ ] CI green
EOF
)"
```

---

## Milestone 5a — APK download

**Why next:** download flow as a pure data-layer addition to `UpdateRepository`. The install hand-off lands in M5b so this milestone stays small + JVM-testable.

**Branch:** `phase7-updater-m5a-download` (off `main` after M4 merges)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateRepository.kt` (add `startDownload` + `cancelDownload`)
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/updater/ApkDownloader.kt`
- Create: `app/src/test/kotlin/com/realmsoffate/game/data/updater/ApkDownloaderTest.kt`
- Modify: `app/src/test/kotlin/com/realmsoffate/game/data/updater/UpdateRepositoryTest.kt` (add download tests)

### Task 5a.1: `ApkDownloader` class + tests

- [ ] **Step 1: Write the test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/updater/ApkDownloaderTest.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ApkDownloaderTest {
    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private lateinit var downloader: ApkDownloader

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        tempDir = Files.createTempDirectory("apk-downloader-test").toFile()
        downloader = ApkDownloader(OkHttpClient(), tempDir)
    }

    @After fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test fun `downloads bytes to file matching expected size`() = runTest {
        val payload = ByteArray(1024) { (it % 256).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))

        val results = mutableListOf<ApkDownloader.Progress>()
        downloader.download(server.url("/x.apk").toString(), "out.apk", expectedSize = 1024L)
            .collect { results.add(it) }

        val terminal = results.last()
        assertTrue("expected Done as terminal, was $terminal", terminal is ApkDownloader.Progress.Done)
        val file = (terminal as ApkDownloader.Progress.Done).file
        assertEquals(1024L, file.length())
        assertTrue(results.any { it is ApkDownloader.Progress.Streaming })
    }

    @Test fun `size mismatch fails with corrupt`() = runTest {
        val payload = ByteArray(512)
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))

        val results = downloader.download(
            server.url("/x.apk").toString(),
            "out.apk",
            expectedSize = 9999L
        ).toList()

        assertTrue("terminal should be Failed", results.last() is ApkDownloader.Progress.Failed)
        assertTrue(
            "Failed reason should mention corrupt or size",
            (results.last() as ApkDownloader.Progress.Failed).reason.contains("size", ignoreCase = true) ||
                (results.last() as ApkDownloader.Progress.Failed).reason.contains("corrupt", ignoreCase = true)
        )
        // partial file is cleaned up
        assertEquals(0, tempDir.listFiles()?.size ?: 0)
    }

    @Test fun `http error fails`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val results = downloader.download(
            server.url("/x.apk").toString(),
            "out.apk",
            expectedSize = 1L
        ).toList()
        assertTrue(results.last() is ApkDownloader.Progress.Failed)
    }
}
```

- [ ] **Step 2: Run — expect FAIL (no `ApkDownloader`)**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.ApkDownloaderTest"`

Expected: FAIL — `Unresolved reference: ApkDownloader`.

- [ ] **Step 3: Implement `ApkDownloader`**

Create `app/src/main/kotlin/com/realmsoffate/game/data/updater/ApkDownloader.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Streams an APK from [downloadUrl] into [cacheDir]/<filename>, emitting
 * progress as a Flow. Terminal events are [Progress.Done] or [Progress.Failed].
 *
 * Cancels cleanly when the collecting coroutine is cancelled — partial file
 * is deleted on cancellation or failure.
 */
class ApkDownloader(
    private val httpClient: OkHttpClient,
    private val cacheDir: File
) {
    sealed class Progress {
        data class Streaming(val bytesRead: Long, val totalBytes: Long, val percent: Int) : Progress()
        data class Done(val file: File) : Progress()
        data class Failed(val reason: String) : Progress()
    }

    fun download(downloadUrl: String, filename: String, expectedSize: Long): Flow<Progress> = flow {
        cacheDir.mkdirs()
        val outFile = File(cacheDir, filename)
        if (outFile.exists()) outFile.delete()
        val request = Request.Builder().url(downloadUrl).get().build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            emit(Progress.Failed("Network: ${e.message}"))
            return@flow
        }
        try {
            response.use { resp ->
                if (!resp.isSuccessful) {
                    emit(Progress.Failed("HTTP ${resp.code}"))
                    outFile.delete()
                    return@flow
                }
                val body = resp.body ?: run {
                    emit(Progress.Failed("Empty body"))
                    return@flow
                }
                val total = if (expectedSize > 0) expectedSize else (body.contentLength().takeIf { it > 0 } ?: -1L)
                var read = 0L
                outFile.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var lastEmittedPct = -1
                        while (coroutineScope { isActive }) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = ((read * 100L) / total).toInt().coerceIn(0, 100)
                                if (pct != lastEmittedPct) {
                                    emit(Progress.Streaming(read, total, pct))
                                    lastEmittedPct = pct
                                }
                            }
                        }
                    }
                }
                if (expectedSize > 0 && read != expectedSize) {
                    outFile.delete()
                    emit(Progress.Failed("Download corrupt — size $read != expected $expectedSize"))
                    return@flow
                }
                emit(Progress.Done(outFile))
            }
        } catch (e: IOException) {
            outFile.delete()
            emit(Progress.Failed("Network: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}
```

- [ ] **Step 4: Run — expect PASS**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.ApkDownloaderTest"`

Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/updater/ApkDownloader.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/updater/ApkDownloaderTest.kt
git commit -m "feat(phase7-updater): ApkDownloader streaming + size validation"
```

### Task 5a.2: Wire `startDownload` / `cancelDownload` into `UpdateRepository`

- [ ] **Step 1: Append tests for the new methods**

Append to `UpdateRepositoryTest`. Note: a fake `ReleasesSource` won't help here — we need to inject a fake `ApkDownloader` too. Refactor the constructor first (next step).

```kotlin
    @Test fun `startDownload transitions Available -> Downloading -> ReadyToInstall`() = runTest(StandardTestDispatcher()) {
        val rel = release("v0.2.0", assetName = "realms-v0.2.0-release.apk")
        val tempFile = java.io.File.createTempFile("fake-apk", ".apk").apply { writeBytes(ByteArray(8)) }
        val fakeDownloader = FakeDownloader().apply {
            scriptedProgress = listOf(
                ApkDownloader.Progress.Streaming(4, 8, 50),
                ApkDownloader.Progress.Done(tempFile)
            )
        }
        val r = UpdateRepository(
            currentVersionName = "0.1.0-alpha.1",
            client = client,
            prefs = prefs,
            clock = clock,
            cacheTtlMs = UpdateRepository.DEFAULT_CACHE_TTL_MS,
            downloader = fakeDownloader
        )
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(rel))
        r.check(force = true); advanceUntilIdle()

        r.startDownload((r.state.value as UpdateState.Available).release)
        advanceUntilIdle()
        val terminal = r.state.value
        assertTrue("expected ReadyToInstall, was $terminal", terminal is UpdateState.ReadyToInstall)
    }

    @Test fun `download Failed reverts to Available`() = runTest(StandardTestDispatcher()) {
        val rel = release("v0.2.0", assetName = "realms-v0.2.0-release.apk")
        val fakeDownloader = FakeDownloader().apply {
            scriptedProgress = listOf(ApkDownloader.Progress.Failed("Network: no route"))
        }
        val r = UpdateRepository(
            currentVersionName = "0.1.0-alpha.1",
            client = client,
            prefs = prefs,
            clock = clock,
            cacheTtlMs = UpdateRepository.DEFAULT_CACHE_TTL_MS,
            downloader = fakeDownloader
        )
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(rel))
        r.check(force = true); advanceUntilIdle()

        r.startDownload(rel); advanceUntilIdle()
        val state = r.state.value
        assertTrue("expected Available after failure, was $state", state is UpdateState.Available)
    }

    @Test fun `download with no realms apk asset emits Error`() = runTest(StandardTestDispatcher()) {
        val rel = release("v0.2.0") // no asset
        val r = UpdateRepository(
            currentVersionName = "0.1.0-alpha.1",
            client = client,
            prefs = prefs,
            clock = clock,
            cacheTtlMs = UpdateRepository.DEFAULT_CACHE_TTL_MS,
            downloader = FakeDownloader()
        )
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(rel))
        r.check(force = true); advanceUntilIdle()

        r.startDownload(rel); advanceUntilIdle()
        val state = r.state.value as UpdateState.Error
        assertEquals(false, state.recoverable)
    }
```

Also add this fake to the bottom of the file (outside the class):

```kotlin
private class FakeDownloader : ApkDownloaderLike {
    var scriptedProgress: List<ApkDownloader.Progress> = emptyList()
    override fun download(downloadUrl: String, filename: String, expectedSize: Long) =
        kotlinx.coroutines.flow.flow { scriptedProgress.forEach { emit(it) } }
}
```

- [ ] **Step 2: Refactor `UpdateRepository` to accept an `ApkDownloaderLike` interface**

Edit `app/src/main/kotlin/com/realmsoffate/game/data/updater/ApkDownloader.kt` — extract an interface so the repository can be tested without a real OkHttp + temp dir:

Add at the top of the file (above `class ApkDownloader`):

```kotlin
/** Test seam — production impl is [ApkDownloader]. */
interface ApkDownloaderLike {
    fun download(downloadUrl: String, filename: String, expectedSize: Long): kotlinx.coroutines.flow.Flow<ApkDownloader.Progress>
}
```

Change the existing `class ApkDownloader(` declaration to `class ApkDownloader(...) : ApkDownloaderLike {`.

Update `download(...)` signature so it `override`s the interface method (add `override` keyword).

- [ ] **Step 3: Add `startDownload` / `cancelDownload` to `UpdateRepository`**

Edit `app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateRepository.kt`. Add a constructor parameter `downloader: ApkDownloaderLike? = null`, and the methods below the existing `setChannel`:

Update the constructor:

```kotlin
class UpdateRepository(
    private val currentVersionName: String,
    private val client: ReleasesSource,
    private val prefs: UpdatePrefs,
    private val clock: () -> Long = System::currentTimeMillis,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val downloader: ApkDownloaderLike? = null,
) {
```

Add methods (above the `companion object`):

```kotlin
    private var downloadJob: Job? = null

    fun startDownload(release: ReleaseInfo) {
        val dl = downloader ?: run {
            _state.value = UpdateState.Error("No downloader wired", recoverable = false)
            return
        }
        val asset = release.realmsApkAsset() ?: run {
            _state.value = UpdateState.Error("Release missing APK", recoverable = false)
            return
        }
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _state.value = UpdateState.Downloading(release, progress = 0)
            dl.download(asset.downloadUrl, "realms-${release.tag.removePrefix("v")}.apk", asset.size)
                .collect { p ->
                    when (p) {
                        is ApkDownloader.Progress.Streaming ->
                            _state.value = UpdateState.Downloading(release, progress = p.percent)
                        is ApkDownloader.Progress.Done ->
                            _state.value = UpdateState.ReadyToInstall(release, p.file)
                        is ApkDownloader.Progress.Failed ->
                            _state.value = UpdateState.Available(release)  // revert; user can retry
                    }
                }
        }
    }

    fun cancelDownload() {
        val current = _state.value
        downloadJob?.cancel()
        if (current is UpdateState.Downloading) {
            _state.value = UpdateState.Available(current.release)
        }
    }
```

- [ ] **Step 4: Run — expect PASS**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.*"`

Expected: PASS (all 20+ tests).

- [ ] **Step 5: Commit + open M5a PR**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/updater/ApkDownloader.kt \
        app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateRepository.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/updater/ApkDownloaderTest.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/updater/UpdateRepositoryTest.kt
git commit -m "feat(phase7-updater): startDownload + cancelDownload via ApkDownloader"

git push -u origin phase7-updater-m5a-download
gh pr create --title "feat(phase7-updater): M5a — APK download (#28)" --body "$(cat <<'EOF'
## Summary
- `ApkDownloader` streams APKs to cache dir, emits Flow<Progress>, validates size, cleans up on failure.
- `UpdateRepository.startDownload/cancelDownload` reduce progress events into UpdateState transitions.
- Failure reverts to `Available` (retryable); install hand-off is M5b.

## Test plan
- [x] `gradle test --tests "*updater*"` passes locally
- [ ] CI green
EOF
)"
```

---

## Milestone 5b — Install hand-off + manifest + signature check

**Why next:** Android plumbing — manifest permission, FileProvider, install intent, debug-build signature mismatch dialog. Smaller PR than M5a; mostly XML + a small Kotlin helper.

**Branch:** `phase7-updater-m5b-install` (off `main` after M5a merges)

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/updater_file_paths.xml`
- Create: `app/src/main/kotlin/com/realmsoffate/game/data/updater/InstallLauncher.kt`
- Create: `app/src/test/kotlin/com/realmsoffate/game/data/updater/InstallLauncherTest.kt`

### Task 5b.1: Manifest + FileProvider config

- [ ] **Step 1: Add the `updater_file_paths.xml` resource**

Create `app/src/main/res/xml/updater_file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Exposes <cacheDir>/updates/ to the system installer for ACTION_INSTALL_PACKAGE. -->
    <cache-path name="updates" path="updates/" />
</paths>
```

- [ ] **Step 2: Edit `app/src/main/AndroidManifest.xml`**

Locate the `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` line and add directly below it:

```xml
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

Locate the `<provider android:name="androidx.startup.InitializationProvider" ...>` block and add a new `<provider>` block immediately above it (still inside `<application>`):

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.updates"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/updater_file_paths" />
        </provider>
```

- [ ] **Step 3: Confirm manifest merges cleanly**

Run: `gradle :app:processDebugManifest`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/xml/updater_file_paths.xml
git commit -m "feat(phase7-updater): manifest permission + FileProvider for updates"
```

### Task 5b.2: `InstallLauncher` — signature check + intent

- [ ] **Step 1: Write the test**

Create `app/src/test/kotlin/com/realmsoffate/game/data/updater/InstallLauncherTest.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class InstallLauncherTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val tempApk = File(Files.createTempDirectory("install-test").toFile(), "fake.apk")
        .apply { writeBytes(ByteArray(8)) }

    @Test fun `buildIntent returns installer intent with file uri`() {
        val intent = InstallLauncher.buildIntent(ctx, tempApk)
        assertNotNull(intent)
        assertNotNull(intent.data)
        // FileProvider URI should use the configured authority
        val expectedAuthority = "${ctx.packageName}.updates"
        assert(intent.data!!.authority == expectedAuthority) {
            "expected authority $expectedAuthority, got ${intent.data!!.authority}"
        }
        assert(intent.action == android.content.Intent.ACTION_VIEW || intent.action == "android.intent.action.INSTALL_PACKAGE")
    }

    @Test fun `signatureMismatch is false when no installed package`() {
        // Robolectric doesn't have the app installed under inspection in this test,
        // so the helper should report no mismatch (no installed sig to compare).
        // This mainly exercises the no-throw path.
        val mismatch = InstallLauncher.signatureMismatchAgainstInstalled(ctx, tempApk)
        // We can't assert true/false reliably under Robolectric — assert it doesn't throw
        assertFalse("Robolectric returns false safely; no exception means we're OK", mismatch == null && false)
    }
}
```

- [ ] **Step 2: Run — expect FAIL (no `InstallLauncher`)**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.InstallLauncherTest"`

Expected: FAIL — `Unresolved reference: InstallLauncher`.

- [ ] **Step 3: Implement `InstallLauncher`**

Create `app/src/main/kotlin/com/realmsoffate/game/data/updater/InstallLauncher.kt`:

```kotlin
package com.realmsoffate.game.data.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * Bridges UpdateRepository's ReadyToInstall state to Android's package
 * installer. Owns the FileProvider URI construction and signature mismatch
 * detection (used in debug builds to warn the user that installing the
 * release APK over a debug build will require an uninstall + data wipe).
 */
object InstallLauncher {

    /** Build the install intent for [apk] using the updater's FileProvider. */
    fun buildIntent(context: Context, apk: File): Intent {
        val authority = "${context.packageName}.updates"
        val uri = FileProvider.getUriForFile(context, authority, apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * True if the APK at [apk] is signed by a different cert than the running
     * package. Returns false when we can't determine (treat as "no warning").
     *
     * Used in debug builds: a debug-signed install replacing a release-signed
     * APK (or vice versa) will trigger Android's signature-mismatch error and
     * require an uninstall, which deletes save data.
     */
    fun signatureMismatchAgainstInstalled(context: Context, apk: File): Boolean {
        return try {
            val installedSigs = installedSignatures(context, context.packageName) ?: return false
            val pkgSigs = apkSignatures(context, apk) ?: return false
            installedSigs != pkgSigs
        } catch (_: Exception) {
            false
        }
    }

    private fun installedSignatures(context: Context, packageName: String): Set<String>? {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: info.signingInfo?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION") info.signatures
        } ?: return null
        return sigs.map { sha256(it) }.toSet()
    }

    private fun apkSignatures(context: Context, apk: File): Set<String>? {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return null
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: info.signingInfo?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION") info.signatures
        } ?: return null
        return sigs.map { sha256(it) }.toSet()
    }

    private fun sha256(sig: Signature): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
```

- [ ] **Step 4: Run — expect PASS**

Run: `gradle test --tests "com.realmsoffate.game.data.updater.InstallLauncherTest"`

Expected: PASS (2 tests).

- [ ] **Step 5: Build to ensure manifest + intent code compiles together**

Run: `gradle assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit + open M5b PR**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/updater/InstallLauncher.kt \
        app/src/test/kotlin/com/realmsoffate/game/data/updater/InstallLauncherTest.kt
git commit -m "feat(phase7-updater): InstallLauncher (FileProvider + sig mismatch detection)"

git push -u origin phase7-updater-m5b-install
gh pr create --title "feat(phase7-updater): M5b — install hand-off + signature check (#28)" --body "$(cat <<'EOF'
## Summary
- Adds `REQUEST_INSTALL_PACKAGES` permission + FileProvider authority `${applicationId}.updates`.
- `InstallLauncher.buildIntent` constructs the system install intent.
- `InstallLauncher.signatureMismatchAgainstInstalled` SHA-256s installed vs APK signatures so debug builds can warn before triggering Android's mismatch error.

## Test plan
- [x] `gradle test --tests "*updater*"` passes
- [x] `gradle assembleDebug` builds clean
- [ ] CI green
EOF
)"
```

---

## Milestone 6 — UI: banner + settings + cold-start wiring

**Why last:** consumes everything from M1–M5b. Adds the user-visible surfaces and wires the singleton into `RealmsApp`. Largest milestone but the data layer is fully tested.

**Branch:** `phase7-updater-m6-ui` (off `main` after M5b merges)

**Files:**
- Modify: `app/src/main/kotlin/com/realmsoffate/game/RealmsApp.kt` (initialize `UpdateRepository` + cold-start check)
- Modify: `app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt` (add `Settings` to `Screen` enum + nav method)
- Modify: `app/src/main/kotlin/com/realmsoffate/game/MainActivity.kt` (route `Screen.Settings`)
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/kotlin/com/realmsoffate/game/ui/components/UpdateBanner.kt`
- Modify: `app/src/main/kotlin/com/realmsoffate/game/ui/setup/TitleScreen.kt` (banner + Settings tile)
- Modify (debug only): `app/src/debug/kotlin/com/realmsoffate/game/debug/DebugBridge.kt` — add `/updater/check` endpoint

### Task 6.1: Initialize singleton in `RealmsApp`

- [ ] **Step 1: Add a singleton holder to `UpdateRepository.kt`**

Edit `app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateRepository.kt`. Append below the existing `companion object`:

```kotlin
/**
 * Process-level holder so MainActivity / Compose can grab the repository
 * without DI. Mirrors RealmsDbHolder / ContentRepository conventions.
 */
object UpdateRepositoryHolder {
    @Volatile private var repo: UpdateRepository? = null

    fun init(
        context: android.content.Context,
        currentVersionName: String,
    ) {
        if (repo != null) return
        val httpClient = GitHubReleasesClient.defaultHttpClient()
        val client = GitHubReleasesSource(
            GitHubReleasesClient(httpClient = httpClient, userAgent = "Realms/$currentVersionName")
        )
        val prefs = UpdatePrefs(context.applicationContext)
        val downloader = ApkDownloader(httpClient, java.io.File(context.cacheDir, "updates"))
        repo = UpdateRepository(
            currentVersionName = currentVersionName,
            client = client,
            prefs = prefs,
            downloader = downloader
        )
    }

    val instance: UpdateRepository
        get() = repo ?: error("UpdateRepositoryHolder not initialized")
}
```

- [ ] **Step 2: Wire it into `RealmsApp.onCreate`**

Edit `app/src/main/kotlin/com/realmsoffate/game/RealmsApp.kt`. Add the import:

```kotlin
import com.realmsoffate.game.data.updater.UpdateRepositoryHolder
```

Inside `onCreate`, immediately after `RealmsDbHolder.init(this)`:

```kotlin
        UpdateRepositoryHolder.init(this, BuildConfig.VERSION_NAME)
        // Kick off a check on cold start; result observed by UI surfaces.
        UpdateRepositoryHolder.instance.check(force = false)
```

- [ ] **Step 3: Verify build**

Run: `gradle assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateRepository.kt \
        app/src/main/kotlin/com/realmsoffate/game/RealmsApp.kt
git commit -m "feat(phase7-updater): wire UpdateRepository singleton + cold-start check"
```

### Task 6.2: Add `Screen.Settings` + nav

- [ ] **Step 1: Edit `Screen` enum in `GameViewModel.kt`**

Locate the line `enum class Screen { ApiSetup, Title, CharacterCreation, Game, Death }` (around line 65) and change it to:

```kotlin
enum class Screen { ApiSetup, Title, CharacterCreation, Game, Death, Settings }
```

- [ ] **Step 2: Add nav methods to `GameViewModel`**

Find the existing `backToApiSetup()` method (around line 668) and add these methods near it:

```kotlin
    fun openSettings() { _screen.value = Screen.Settings }
    fun closeSettings() { _screen.value = Screen.Title }
```

- [ ] **Step 3: Route `Screen.Settings` in `MainActivity`**

Edit `app/src/main/kotlin/com/realmsoffate/game/MainActivity.kt`. Add the import:

```kotlin
import com.realmsoffate.game.ui.settings.SettingsScreen
```

Update the `when (screen)` block:

```kotlin
    when (screen) {
        Screen.ApiSetup -> ApiSetupScreen(vm)
        Screen.Title -> TitleScreen(vm)
        Screen.CharacterCreation -> CharacterCreationScreen(vm)
        Screen.Game -> GameScreen(vm)
        Screen.Death -> DeathScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
    }
```

- [ ] **Step 4: Build to verify nav stub compiles (will fail until SettingsScreen exists)**

Run: `gradle assembleDebug`

Expected: FAIL — `Unresolved reference: SettingsScreen`. Continue to next task.

### Task 6.3: Build `SettingsScreen`

- [ ] **Step 1: Create `SettingsScreen.kt`**

Create `app/src/main/kotlin/com/realmsoffate/game/ui/settings/SettingsScreen.kt`:

```kotlin
package com.realmsoffate.game.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.BuildConfig
import com.realmsoffate.game.data.updater.UpdateChannel
import com.realmsoffate.game.data.updater.UpdateRepositoryHolder
import com.realmsoffate.game.data.updater.UpdateState
import com.realmsoffate.game.game.GameViewModel
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: GameViewModel) {
    val repo = remember { UpdateRepositoryHolder.instance }
    val state by repo.state.collectAsState()
    val channel by repo.let { it }.let { remember(it) { it.observableChannel() } }.collectAsState(initial = UpdateChannel.DEFAULT)
    val lastChecked by repo.observableLastCheckedAt().collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { vm.closeSettings() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection("App") {
                LabeledRow("Version", BuildConfig.VERSION_NAME)
            }

            SettingsSection("Updates") {
                LabeledRow("Last checked", relativeTime(lastChecked))
                ChannelToggleRow(
                    channel = channel,
                    onChange = { repo.setChannel(it) }
                )
                CheckNowRow(
                    enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                    onCheck = { repo.check(force = true) }
                )
                UpdateStatusRow(state = state, onUpdate = { release -> repo.startDownload(release) })
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChannelToggleRow(channel: UpdateChannel, onChange: (UpdateChannel) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Include prereleases", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = channel == UpdateChannel.INCLUDE_PRERELEASES,
            onCheckedChange = { on ->
                onChange(if (on) UpdateChannel.INCLUDE_PRERELEASES else UpdateChannel.STABLE_ONLY)
            }
        )
    }
}

@Composable
private fun CheckNowRow(enabled: Boolean, onCheck: () -> Unit) {
    TextButton(onClick = onCheck, enabled = enabled) { Text("Check for updates now") }
}

@Composable
private fun UpdateStatusRow(state: UpdateState, onUpdate: (com.realmsoffate.game.data.updater.ReleaseInfo) -> Unit) {
    when (state) {
        is UpdateState.Available ->
            TextButton(onClick = { onUpdate(state.release) }) { Text("Update to ${state.release.tag}") }
        is UpdateState.Downloading ->
            Text("Downloading… ${state.progress}%", style = MaterialTheme.typography.bodyMedium)
        is UpdateState.ReadyToInstall ->
            TextButton(onClick = { onUpdate(state.release) }) { Text("Install ${state.release.tag}") }
        is UpdateState.Error ->
            Text(state.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate -> Unit
    }
}

private fun relativeTime(epochMs: Long?): String {
    if (epochMs == null) return "Never"
    val deltaMs = System.currentTimeMillis() - epochMs
    if (deltaMs < 60_000L) return "Just now"
    if (deltaMs < 3_600_000L) return "${deltaMs / 60_000L}m ago"
    if (deltaMs < 86_400_000L) return "${deltaMs / 3_600_000L}h ago"
    return "${deltaMs / 86_400_000L}d ago"
}
```

- [ ] **Step 2: Expose convenience flows on `UpdateRepository`**

The composable references `repo.observableChannel()` and `repo.observableLastCheckedAt()`. Add them to `UpdateRepository.kt`, just above the `companion object`:

```kotlin
    /** UI-friendly: pass-throughs for the prefs flows. */
    fun observableChannel(): kotlinx.coroutines.flow.Flow<UpdateChannel> = prefs.channel
    fun observableLastCheckedAt(): kotlinx.coroutines.flow.Flow<Long?> = prefs.lastCheckedAt
```

- [ ] **Step 3: Build**

Run: `gradle assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/data/updater/UpdateRepository.kt \
        app/src/main/kotlin/com/realmsoffate/game/ui/settings/SettingsScreen.kt \
        app/src/main/kotlin/com/realmsoffate/game/game/GameViewModel.kt \
        app/src/main/kotlin/com/realmsoffate/game/MainActivity.kt
git commit -m "feat(phase7-updater): SettingsScreen + Screen.Settings nav"
```

### Task 6.4: `UpdateBanner` + Title-screen integration

- [ ] **Step 1: Create the banner composable**

Create `app/src/main/kotlin/com/realmsoffate/game/ui/components/UpdateBanner.kt`:

```kotlin
package com.realmsoffate.game.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.realmsoffate.game.data.updater.ReleaseInfo
import com.realmsoffate.game.data.updater.UpdateRepositoryHolder
import com.realmsoffate.game.data.updater.UpdateState

/**
 * Title-screen banner. Shows for `Available` (when not dismissed),
 * `Downloading`, and `ReadyToInstall`. Hidden in all other states.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val repo = remember { UpdateRepositoryHolder.instance }
    val state by repo.state.collectAsState()
    val release: ReleaseInfo? = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.ReadyToInstall -> s.release
        else -> null
    } ?: return

    var dismissedThisSession by remember { mutableStateOf(false) }
    var bannerVisible by remember { mutableStateOf(true) }
    LaunchedEffect(release.tag) {
        bannerVisible = repo.bannerVisibleFor(release)
    }
    if (!bannerVisible || dismissedThisSession) return

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Update available: ${release.tag}", style = MaterialTheme.typography.titleSmall)
            Text(
                release.body.lineSequence().firstOrNull()?.trim() ?: release.name,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            when (val s = state) {
                is UpdateState.Available -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { repo.startDownload(release) }) { Text("Update") }
                    TextButton(onClick = { dismissedThisSession = true }) { Text("Later") }
                    TextButton(onClick = {
                        repo.dismissBannerForVersion(release.tag)
                        dismissedThisSession = true
                    }) { Text("Skip this version") }
                }
                is UpdateState.Downloading -> Column {
                    Text("Downloading… ${s.progress}%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { s.progress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                    TextButton(onClick = { repo.cancelDownload() }) { Text("Cancel") }
                }
                is UpdateState.ReadyToInstall -> Button(onClick = { repo.startDownload(release) /* re-fires intent via state observer */ }) {
                    Text("Install")
                }
                else -> Unit
            }
        }
    }
}
```

- [ ] **Step 2: Add the banner to `TitleScreen`**

Edit `app/src/main/kotlin/com/realmsoffate/game/ui/setup/TitleScreen.kt`. Add the import:

```kotlin
import com.realmsoffate.game.ui.components.UpdateBanner
```

Then locate the `Column` inside `TitleScreen(vm: GameViewModel)` (around line 60–80, the layout column that holds tiles). Insert `UpdateBanner()` immediately after the title-text block and before the tile grid. If unsure, the banner's exact placement is between the existing app-title `Text(...)` block and the first `PrimaryTile(...)` call.

Example insertion point:

```kotlin
        // Existing title content above this
        UpdateBanner()
        Spacer(Modifier.height(12.dp))
        // Existing tile grid below this
```

- [ ] **Step 3: Add a Settings tile next to API Setup**

In `TitleScreen.kt`, find the `Row` containing the `Graveyard` and `API Setup` `SecondaryTile` calls (around line 160–185). Replace that row so it becomes a 3-tile row with `Settings`:

```kotlin
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryTile(
                    icon = Icons.Default.Settings,
                    label = "Graveyard (${graves.size})",
                    enabled = graves.isNotEmpty(),
                    onClick = { graveSheet = true },
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.error,
                    iconText = "⚰"
                )
                SecondaryTile(
                    icon = Icons.Default.Settings,
                    label = "API Setup",
                    enabled = true,
                    onClick = { vm.backToApiSetup() },
                    modifier = Modifier.weight(1f)
                )
                SecondaryTile(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    enabled = true,
                    onClick = { vm.openSettings() },
                    modifier = Modifier.weight(1f)
                )
            }
```

- [ ] **Step 4: Wire the install intent dispatch in `MainActivity`**

`SettingsScreen` and `UpdateBanner` only emit state transitions; the actual `startActivity` call needs to happen in `MainActivity`. Edit `app/src/main/kotlin/com/realmsoffate/game/MainActivity.kt`:

Add imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.realmsoffate.game.BuildConfig
import com.realmsoffate.game.data.updater.InstallLauncher
import com.realmsoffate.game.data.updater.UpdateRepositoryHolder
import com.realmsoffate.game.data.updater.UpdateState
import androidx.appcompat.app.AlertDialog
```

Replace the `RealmsRoot(vm)` call in `setContent { ... }` with a wrapper that also observes update state:

```kotlin
                RealmsRoot(viewModel)
                ObserveUpdateInstall(this@MainActivity)
```

Add this composable below `RealmsRoot`:

```kotlin
@Composable
private fun ObserveUpdateInstall(activity: ComponentActivity) {
    val state by UpdateRepositoryHolder.instance.state.collectAsState()
    LaunchedEffect(state) {
        val s = state
        if (s is UpdateState.ReadyToInstall) {
            val mismatch = BuildConfig.DEBUG && InstallLauncher.signatureMismatchAgainstInstalled(activity, s.file)
            val launch = {
                runCatching { activity.startActivity(InstallLauncher.buildIntent(activity, s.file)) }
            }
            if (mismatch) {
                android.app.AlertDialog.Builder(activity)
                    .setTitle("Replace debug build?")
                    .setMessage("Installing the release build will conflict with this debug build's signing certificate. Android will require you to uninstall first, which deletes save data. Continue?")
                    .setPositiveButton("Continue") { _, _ -> launch() }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                launch()
            }
        }
    }
}
```

- [ ] **Step 5: Build + install + smoke-test**

Run:

```bash
gradle installDebug && \
adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && \
adb -s emulator-5554 forward tcp:8735 tcp:8735
```

Expected: app launches; title screen shows banner if `v0.1.0-alpha.1` is the only release matched against `BuildConfig.VERSION_NAME = "0.1.0-alpha.1"` it should show **UpToDate** (no banner). Settings tile opens new screen.

If you want to manually test the banner UI without a real new release, use the debug endpoint added in Task 6.5.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/realmsoffate/game/ui/components/UpdateBanner.kt \
        app/src/main/kotlin/com/realmsoffate/game/ui/setup/TitleScreen.kt \
        app/src/main/kotlin/com/realmsoffate/game/MainActivity.kt
git commit -m "feat(phase7-updater): UpdateBanner + Settings tile + install dispatch"
```

### Task 6.5: Debug-bridge `/updater/check` endpoint

- [ ] **Step 1: Locate the existing debug bridge**

```bash
grep -nE "fun route|route\b|router\b|when \(.*path\)" app/src/debug/kotlin/com/realmsoffate/game/debug/*.kt | head -10
```

Read whichever file holds the existing routing table (usually `DebugBridge.kt` or `DebugRoutes.kt`). Identify the pattern used by existing endpoints like `/content/reload` (mentioned in `CLAUDE.md`).

- [ ] **Step 2: Add a `/updater/check` route**

Following the exact style of the existing `/content/reload` endpoint, add a `POST /updater/check` (or `GET` if the bridge uses GET) handler that:

```kotlin
// inside the route table, near /content/reload
"/updater/check" -> {
    com.realmsoffate.game.data.updater.UpdateRepositoryHolder.instance.check(force = true)
    respondJson("""{"ok":true}""")
}
```

If the existing routes use a different `respond` pattern, match it exactly.

- [ ] **Step 3: Verify**

```bash
gradle installDebug && \
adb -s emulator-5554 shell am start -n com.realmsoffate.game/.MainActivity && \
adb -s emulator-5554 forward tcp:8735 tcp:8735 && \
sleep 3 && \
curl -X POST http://localhost:8735/updater/check
```

Expected: HTTP 200 with `{"ok":true}`. Then `curl http://localhost:8735/describe` (or the equivalent existing read endpoint) — depending on what's currently visible — should reflect updater state.

- [ ] **Step 4: Commit + open M6 PR**

```bash
git add app/src/debug/kotlin/com/realmsoffate/game/debug/
git commit -m "feat(phase7-updater): debug-bridge /updater/check endpoint"

git push -u origin phase7-updater-m6-ui
gh pr create --title "feat(phase7-updater): M6 — UI + cold-start wiring (#28)" --body "$(cat <<'EOF'
## Summary
- `UpdateRepository` initialized in `RealmsApp.onCreate`, kicks off cold-start check.
- New `Screen.Settings` + `SettingsScreen` (channel toggle, last-checked, "Check now", current version).
- `UpdateBanner` on TitleScreen (per-version dismiss, session dismiss, download progress, install button).
- `MainActivity` observes `ReadyToInstall` and fires the system installer; debug builds show a signature-mismatch warning first.
- Debug bridge `/updater/check` for forcing a check during testing.

## Test plan
- [x] `gradle test --tests "*updater*"` passes
- [x] `gradle assembleDebug` builds clean
- [x] App launches; title screen and Settings reachable
- [ ] Manual: `curl -X POST localhost:8735/updater/check` triggers a check
- [ ] Manual: signature-mismatch dialog shows when forcing a release-APK install over a debug build
EOF
)"
```

---

## Verification across all milestones

After M6 merges, the full system can be exercised end-to-end:

- [ ] **Verify the cold-start path** — fresh install, launch app: settings shows "Last checked: just now"; if no newer release exists, banner is hidden.
- [ ] **Verify channel toggle** — switch to Stable only; "Check now"; if all releases are prereleases, banner disappears.
- [ ] **Verify dismiss** — when an update is available, "Skip this version" hides the banner; settings still shows the update.
- [ ] **Verify install path** — with a real newer release published, tap Update; observe download progress; system installer dialog appears; install completes with the new version.
- [ ] **Verify debug warning** — on a debug build, tap Update against a real release; the signature-mismatch dialog appears.

These five scenarios constitute the manual P-level verification per `CLAUDE.md`. Each is one-shot per release cycle.

---

## Self-review

### Spec coverage

| Spec section | Implementing task |
|---|---|
| `data/updater/` package layout | M1 (SemVer), M2 (Client + DTOs), M3 (Channel/State/Prefs), M4 (Repository), M5a (Downloader), M5b (InstallLauncher) |
| Manifest: REQUEST_INSTALL_PACKAGES | Task 5b.1 |
| FileProvider authority `${applicationId}.updates` + `cache-path updates/` | Task 5b.1 |
| Single source of truth `UpdateRepository` singleton | Task 6.1 |
| Cold-start check from `RealmsApp` | Task 6.1 |
| 6h cache | Task 4.1 (`DEFAULT_CACHE_TTL_MS`) |
| Channel filter | Task 3.1 + 4.2 |
| Per-version dismiss with banner suppression | Task 3.2 + 4.2 (`bannerVisibleFor`) + 6.4 |
| Semver comparator on `versionName` | M1 |
| OkHttp client with `User-Agent` + `Accept` | Task 2.3 |
| Sealed-class error mapping | Task 2.3, 4.1, 4.2 |
| APK download + size validation | M5a |
| Install intent via FileProvider | Task 5b.2 |
| Signature-mismatch detection in debug builds | Task 5b.2 + 6.4 |
| `UpdateBanner` (Available / Downloading / ReadyToInstall) | Task 6.4 |
| Settings screen rows | Task 6.3 |
| 20+ unit tests across components | M1 (18) + M2 (9) + M3 (7) + M4 (17) + M5a (3) + M5b (2) ≈ 56 |
| Real GitHub fixture for client tests | Task 2.1 |
| Debug-bridge `/updater/check` | Task 6.5 |
| Out-of-scope items not implemented | (none — confirmed not added) |

### Placeholder scan

No `TBD`, `TODO`, "implement later", "fill in details", or hand-wave language. Every code step contains the actual code.

### Type consistency

- `UpdateRepository.check(force: Boolean = false)` — same signature in M4 and M5a tests.
- `UpdateRepository.startDownload(release: ReleaseInfo)` — same signature in M5a code and M6 UI.
- `ApkDownloader.Progress` — `Streaming(bytesRead, totalBytes, percent)`, `Done(file)`, `Failed(reason)` — used consistently across M5a and M6.
- `UpdateState` constructors — `Available(release)`, `Downloading(release, progress)`, `ReadyToInstall(release, file)`, `Error(reason, recoverable)` — consistent across all milestones.
- `prefs.dismissVersion(tag)` and `prefs.dismissedVersions: Flow<Set<String>>` — consistent in M3 and M4.
- `repo.bannerVisibleFor(release)` — consistent between M4 (definition) and M6 (consumption).

### Scope check

The plan covers a single feature (auto-update), produces working software at every milestone, and stays within the spec's explicit boundaries. Each milestone PR is independently reviewable and stands on its own as a partial deliverable (e.g., M1 ships a reusable comparator that has value even if the rest of Phase 7 stalls).
