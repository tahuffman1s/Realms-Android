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
        val expectedCore = "${first.version.major}.${first.version.minor}.${first.version.patch}" +
            if (first.version.prerelease.isEmpty()) "" else "-" + first.version.prerelease.joinToString(".")
        assertEquals(first.tag.removePrefix("v"), expectedCore)
    }

    @Test fun `sets user agent and accept headers`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        client.fetchReleases()

        val req = server.takeRequest()
        assertEquals("Realms/test", req.getHeader("User-Agent"))
        assertEquals("application/vnd.github+json", req.getHeader("Accept"))
        assertTrue(req.path!!.startsWith("/repos/tahuffman1s/Realms-Android/releases"))
    }

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
}
