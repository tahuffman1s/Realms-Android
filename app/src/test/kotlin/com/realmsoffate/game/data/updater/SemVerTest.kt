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
}
