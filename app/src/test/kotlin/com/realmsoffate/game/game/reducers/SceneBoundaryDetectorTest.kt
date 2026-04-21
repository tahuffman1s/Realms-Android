package com.realmsoffate.game.game.reducers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SceneBoundaryDetectorTest {

    private fun snap(
        scene: String = "default",
        location: String = "Ashford",
        inCombat: Boolean = false
    ) = SceneBoundaryDetector.Snapshot(
        sceneTag = scene,
        locationName = location,
        inCombat = inCombat
    )

    @Test
    fun `no boundary when nothing changes`() {
        val prev = snap()
        val cur = snap()
        assertNull(SceneBoundaryDetector.detect(prev, cur))
    }

    @Test
    fun `boundary when location changes`() {
        val prev = snap(location = "Ashford")
        val cur = snap(location = "Greymoor")
        val r = SceneBoundaryDetector.detect(prev, cur)
        assertEquals(SceneBoundaryDetector.Reason.LOCATION_CHANGED, r)
    }

    @Test
    fun `boundary when scene tag changes`() {
        val prev = snap(scene = "ashford-tavern")
        val cur = snap(scene = "ashford-market")
        assertEquals(
            SceneBoundaryDetector.Reason.SCENE_TAG_CHANGED,
            SceneBoundaryDetector.detect(prev, cur)
        )
    }

    @Test
    fun `boundary when combat starts`() {
        val prev = snap(inCombat = false)
        val cur = snap(inCombat = true)
        assertEquals(
            SceneBoundaryDetector.Reason.COMBAT_STARTED,
            SceneBoundaryDetector.detect(prev, cur)
        )
    }

    @Test
    fun `boundary when combat ends`() {
        val prev = snap(inCombat = true)
        val cur = snap(inCombat = false)
        assertEquals(
            SceneBoundaryDetector.Reason.COMBAT_ENDED,
            SceneBoundaryDetector.detect(prev, cur)
        )
    }

    @Test
    fun `location change takes precedence over scene tag change`() {
        val prev = snap(scene = "a", location = "Ashford")
        val cur = snap(scene = "b", location = "Greymoor")
        assertEquals(
            SceneBoundaryDetector.Reason.LOCATION_CHANGED,
            SceneBoundaryDetector.detect(prev, cur)
        )
    }

    @Test
    fun `scene default to non-default triggers boundary`() {
        val prev = snap(scene = "default")
        val cur = snap(scene = "ashford-tavern")
        assertEquals(
            SceneBoundaryDetector.Reason.SCENE_TAG_CHANGED,
            SceneBoundaryDetector.detect(prev, cur)
        )
    }
}
