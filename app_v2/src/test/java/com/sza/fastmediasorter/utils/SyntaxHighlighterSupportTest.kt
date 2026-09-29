package com.sza.fastmediasorter.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterSupportTest {

    @Test
    fun `supported extensions are matched case-insensitively`() {
        assertTrue(SyntaxHighlighter.isSupported("KT"))
        assertTrue(SyntaxHighlighter.isSupported("json"))
        assertTrue(SyntaxHighlighter.isSupported("scss"))
    }

    @Test
    fun `unsupported extensions are rejected`() {
        assertFalse(SyntaxHighlighter.isSupported("md"))
        assertFalse(SyntaxHighlighter.isSupported(""))
    }
}
