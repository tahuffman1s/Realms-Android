package com.realmsoffate.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiRepositoryBalanceTest {
    @Test
    fun `parseBalance extracts USD total when present`() {
        val sample = """
          {"is_available":true,"balance_infos":[
            {"currency":"USD","total_balance":"4.12","granted_balance":"0","topped_up_balance":"4.12"}
          ]}
        """.trimIndent()
        assertEquals("4.12", AiRepository.parseBalance(sample))
    }

    @Test
    fun `parseBalance prefers USD over other currencies`() {
        val sample = """
          {"balance_infos":[
            {"currency":"CNY","total_balance":"30.00"},
            {"currency":"USD","total_balance":"4.12"}
          ]}
        """.trimIndent()
        assertEquals("4.12", AiRepository.parseBalance(sample))
    }

    @Test
    fun `parseBalance falls back to first entry when USD absent`() {
        val sample = """
          {"balance_infos":[
            {"currency":"CNY","total_balance":"30.00"}
          ]}
        """.trimIndent()
        assertEquals("30.00", AiRepository.parseBalance(sample))
    }

    @Test
    fun `parseBalance returns null on malformed input`() {
        assertNull(AiRepository.parseBalance("not json"))
        assertNull(AiRepository.parseBalance("{}"))
        assertNull(AiRepository.parseBalance("""{"balance_infos":[]}"""))
    }

    @Test
    fun `parseBalance returns null when total_balance is missing`() {
        assertNull(AiRepository.parseBalance("""{"balance_infos":[{"currency":"USD"}]}"""))
    }

    @Test
    fun `parseBalance returns null when account is marked unavailable`() {
        val sample = """{"is_available":false,"balance_infos":[{"currency":"USD","total_balance":"4.12"}]}"""
        assertNull(AiRepository.parseBalance(sample))
    }
}
