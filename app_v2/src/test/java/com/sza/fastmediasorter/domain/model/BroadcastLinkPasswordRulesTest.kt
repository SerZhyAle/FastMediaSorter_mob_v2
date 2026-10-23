package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordRules.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastLinkPasswordRulesTest {

    @Test
    fun `one character is accepted and weak`() {
        assertEquals(Verdict.OK, BroadcastLinkPasswordRules.validate("a"))
        assertTrue(BroadcastLinkPasswordRules.isWeak("a"))
    }

    @Test
    fun `url and digest delimiters are accepted`() {
        listOf(":", "@", "/", "%", "a b").forEach { text ->
            assertEquals(text, Verdict.OK, BroadcastLinkPasswordRules.validate(text))
        }
    }

    @Test
    fun `cyrillic is accepted`() {
        assertEquals(Verdict.OK, BroadcastLinkPasswordRules.validate("пароль"))
    }

    @Test
    fun `supplementary character counts as one code point`() {
        val emoji = "😀"
        assertEquals(Verdict.OK, BroadcastLinkPasswordRules.validate(emoji.repeat(64)))
        assertEquals(Verdict.TOO_LONG, BroadcastLinkPasswordRules.validate(emoji.repeat(65)))
    }

    @Test
    fun `sixty four code points pass and sixty five do not`() {
        assertEquals(Verdict.OK, BroadcastLinkPasswordRules.validate("x".repeat(64)))
        assertEquals(Verdict.TOO_LONG, BroadcastLinkPasswordRules.validate("x".repeat(65)))
    }

    @Test
    fun `tab is a control character`() {
        assertEquals(Verdict.CONTROL_CHARACTER, BroadcastLinkPasswordRules.validate("a\tb"))
    }

    @Test
    fun `empty string is refused`() {
        assertEquals(Verdict.EMPTY, BroadcastLinkPasswordRules.validate(""))
    }

    @Test
    fun `eight code points are not weak`() {
        assertFalse(BroadcastLinkPasswordRules.isWeak("12345678"))
        assertTrue(BroadcastLinkPasswordRules.isWeak("1234567"))
    }
}
