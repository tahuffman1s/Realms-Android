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

        val results = downloader.download(server.url("/x.apk").toString(), "out.apk", expectedSize = 1024L)
            .toList()

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

        val terminal = results.last()
        assertTrue("terminal should be Failed, was $terminal", terminal is ApkDownloader.Progress.Failed)
        assertTrue(
            "Failed reason should mention corrupt or size",
            (terminal as ApkDownloader.Progress.Failed).reason.contains("size", ignoreCase = true) ||
                terminal.reason.contains("corrupt", ignoreCase = true)
        )
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
