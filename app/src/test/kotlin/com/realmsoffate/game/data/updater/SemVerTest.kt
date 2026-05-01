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
