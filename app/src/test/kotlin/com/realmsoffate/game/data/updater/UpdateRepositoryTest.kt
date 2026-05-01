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

    private fun newRepo(
        versionName: String = "0.1.0-alpha.1",
        downloader: ApkDownloaderLike? = null
    ): UpdateRepository =
        UpdateRepository(
            currentVersionName = versionName,
            client = client,
            prefs = prefs,
            clock = clock,
            cacheTtlMs = UpdateRepository.DEFAULT_CACHE_TTL_MS,
            downloader = downloader
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

    @Test fun `STABLE_ONLY excludes prereleases`() = runTest {
        val repo = newRepo()
        prefs.setChannel(UpdateChannel.STABLE_ONLY)
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(
            release("v0.2.0-alpha.1", prerelease = true)
        ))
        repo.check(force = true).join()
        assertEquals(UpdateState.UpToDate, repo.state.value)
    }

    @Test fun `INCLUDE_PRERELEASES surfaces alpha`() = runTest {
        val repo = newRepo()
        prefs.setChannel(UpdateChannel.INCLUDE_PRERELEASES)
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(
            release("v0.2.0-alpha.1", prerelease = true, assetName = "realms-v0.2.0-alpha.1-release.apk")
        ))
        repo.check(force = true).join()
        assertTrue(repo.state.value is UpdateState.Available)
    }

    @Test fun `dismissed version still becomes Available state`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(
            release("v0.2.0", assetName = "realms-v0.2.0-release.apk")
        ))
        prefs.dismissVersion("v0.2.0")
        repo.check(force = true).join()
        val state = repo.state.value
        assertTrue(state is UpdateState.Available)
        assertEquals(false, repo.bannerVisibleFor((state as UpdateState.Available).release))
    }

    @Test fun `non-dismissed version banner visible`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(
            release("v0.2.0", assetName = "realms-v0.2.0-release.apk")
        ))
        repo.check(force = true).join()
        val state = repo.state.value as UpdateState.Available
        assertEquals(true, repo.bannerVisibleFor(state.release))
    }

    @Test fun `network error becomes recoverable Error`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.NetworkError("no route")
        repo.check(force = true).join()
        val state = repo.state.value
        assertTrue(state is UpdateState.Error)
        assertTrue((state as UpdateState.Error).recoverable)
    }

    @Test fun `parse error is non-recoverable`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.ParseError("bad json")
        repo.check(force = true).join()
        val state = repo.state.value as UpdateState.Error
        assertEquals(false, state.recoverable)
    }

    @Test fun `rate limited is recoverable`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.RateLimited(null)
        repo.check(force = true).join()
        val state = repo.state.value as UpdateState.Error
        assertTrue(state.recoverable)
    }

    @Test fun `picks highest semver when multiple newer`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(
            release("v0.2.0-alpha.1", prerelease = true, assetName = "realms-v0.2.0-alpha.1-release.apk"),
            release("v0.2.0-alpha.10", prerelease = true, assetName = "realms-v0.2.0-alpha.10-release.apk"),
            release("v0.1.5", assetName = "realms-v0.1.5-release.apk")
        ))
        repo.check(force = true).join()
        val state = repo.state.value as UpdateState.Available
        assertEquals("v0.2.0-alpha.10", state.release.tag)
    }

    @Test fun `lastKnownReleaseTag persists across checks`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(
            release("v0.2.0", assetName = "realms-v0.2.0-release.apk")
        ))
        repo.check(force = true).join()
        assertEquals("v0.2.0", prefs.lastKnownReleaseTag.first())
    }

    @Test fun `setChannel triggers re-check`() = runTest {
        val repo = newRepo()
        client.nextResult = GitHubReleasesClient.Result.Ok(emptyList())
        repo.setChannel(UpdateChannel.STABLE_ONLY).join()
        assertEquals(UpdateChannel.STABLE_ONLY, prefs.channel.first())
        assertEquals(1, client.calls)
    }

    @Test fun `startDownload transitions to ReadyToInstall on success`() = runTest {
        val rel = release("v0.2.0", assetName = "realms-v0.2.0-release.apk")
        val tempFile = java.io.File.createTempFile("fake-apk", ".apk").apply { writeBytes(ByteArray(8)) }
        val fakeDownloader = FakeDownloader().apply {
            scriptedProgress = listOf(
                ApkDownloader.Progress.Streaming(4, 8, 50),
                ApkDownloader.Progress.Done(tempFile)
            )
        }
        val repo = newRepo(downloader = fakeDownloader)
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(rel))
        repo.check(force = true).join()

        repo.startDownload((repo.state.value as UpdateState.Available).release).join()
        val terminal = repo.state.value
        assertTrue("expected ReadyToInstall, was $terminal", terminal is UpdateState.ReadyToInstall)
    }

    @Test fun `download Failed reverts to Available`() = runTest {
        val rel = release("v0.2.0", assetName = "realms-v0.2.0-release.apk")
        val fakeDownloader = FakeDownloader().apply {
            scriptedProgress = listOf(ApkDownloader.Progress.Failed("Network: no route"))
        }
        val repo = newRepo(downloader = fakeDownloader)
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(rel))
        repo.check(force = true).join()

        repo.startDownload(rel).join()
        val state = repo.state.value
        assertTrue("expected Available after failure, was $state", state is UpdateState.Available)
    }

    @Test fun `download with no realms apk asset emits Error`() = runTest {
        val rel = release("v0.2.0") // no asset
        val repo = newRepo(downloader = FakeDownloader())
        client.nextResult = GitHubReleasesClient.Result.Ok(listOf(rel))
        repo.check(force = true).join()

        repo.startDownload(rel).join()
        val state = repo.state.value as UpdateState.Error
        assertEquals(false, state.recoverable)
    }
}

private class FakeDownloader : ApkDownloaderLike {
    var scriptedProgress: List<ApkDownloader.Progress> = emptyList()
    override fun download(downloadUrl: String, filename: String, expectedSize: Long) =
        kotlinx.coroutines.flow.flow { scriptedProgress.forEach { emit(it) } }
}
