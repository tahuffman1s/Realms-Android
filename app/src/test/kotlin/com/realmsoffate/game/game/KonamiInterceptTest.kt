package com.realmsoffate.game.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit-level check of the Konami sequence constant + its exact-match behavior.
 * The full VM intercept flow is verified at runtime via the debug bridge.
 */
class KonamiInterceptTest {

    @Test fun `konami constant is exactly the 10-char emoji sequence`() {
        // The constant is defined in GameViewModel's companion object.
        val expected = "⬆️⬆️" +           // ⬆️⬆️
                "⬇️⬇️" +                    // ⬇️⬇️
                "⬅️➡️⬅️➡️" + // ⬅️➡️⬅️➡️
                "🅱️🅰️"         // 🅱️🅰️
        assertEquals(expected, GameViewModel.KONAMI_CODE)
    }

    @Test fun `exact match helper returns true for trimmed exact input`() {
        assertEquals(true, GameViewModel.isKonami(GameViewModel.KONAMI_CODE))
        assertEquals(true, GameViewModel.isKonami("  ${GameViewModel.KONAMI_CODE}  "))
    }

    @Test fun `substring does not match`() {
        assertEquals(false, GameViewModel.isKonami("${GameViewModel.KONAMI_CODE} and also attack"))
        assertEquals(false, GameViewModel.isKonami("hi"))
        assertEquals(false, GameViewModel.isKonami(""))
    }
}
