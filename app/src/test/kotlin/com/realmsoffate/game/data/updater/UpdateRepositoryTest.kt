package com.realmsoffate.game.data.updater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

@RunWith(RobolectricTestRunner::class)
class UpdateRepositoryTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val prefs = UpdatePrefs(ctx)
    private val client = FakeClient()
    private var nowMs = 1_700_000_000_000L
    private val clock = { nowMs }

    private fun newRepo(versionName: String = "0.1.0-alpha.1"): UpdateRepository =
        UpdateRepository(
            currentVersionName = versionName,
            client = client,
            prefs = prefs,
            clock = clock,
            cacheTtlMs = UpdateRepository.DEFAULT_CACHE_TTL_MS
        )

    @After fun tearDown() = runTest { prefs.clearForTest() }

    @Test fun `idle on construction`() = runTest {
        val repo = newRepo()
        assertEquals(UpdateState.Idle, repo.state.value)
    }

    @Test fun `check with no newer release leaves UpToDate`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(release("v0.1.0-alpha.1")))
        repo.check(force = false).join()
        assertEquals(UpdateState.UpToDate, repo.state.value)
        assertEquals(1, client.calls)
    }

    @Test fun `check with newer release becomes Available`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(release("v0.2.0", assetName = "realms-v0.2.0-release.apk")))
        repo.check(force = false).join()
        val s = repo.state.value
        assertTrue("expected Available, was $s", s is UpdateState.Available)
        assertEquals("v0.2.0", (s as UpdateState.Available).release.tag)
    }

    @Test fun `cache hit short-circuits within TTL`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(release("v0.1.0-alpha.1")))
        repo.check(force = false).join()
        repo.check(force = false).join()
        assertEquals("client should be called once when within cache window", 1, client.calls)
    }

    @Test fun `force bypasses cache`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(release("v0.1.0-alpha.1")))
        repo.check(force = false).join()
        repo.check(force = true).join()
        assertEquals(2, client.calls)
    }

    @Test fun `cache expired after TTL passes`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(release("v0.1.0-alpha.1")))
        repo.check(force = false).join()
        nowMs += UpdateRepository.DEFAULT_CACHE_TTL_MS + 1
        repo.check(force = false).join()
        assertEquals(2, client.calls)
    }

    @Test fun `lastCheckedAt persists`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(release("v0.1.0-alpha.1")))
        repo.check(force = false).join()
        assertEquals(nowMs, prefs.lastCheckedAt.first())
    }
}
