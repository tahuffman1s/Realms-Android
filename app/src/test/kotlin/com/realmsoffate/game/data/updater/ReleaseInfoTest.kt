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
