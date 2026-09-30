package com.sza.fastmediasorter.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SecretMaskerTest {

    @Test
    fun `sanitize masks the bearer token and keeps the scheme`() {
        val out = SecretMasker.sanitize("Authorization: Bearer abc.def.ghi")
        assertEquals("Authorization=Bearer ****(11)", out)
        assertFalse(out.contains("abc.def.ghi"))
    }

    @Test
    fun `sanitize masks a basic credential`() {
        assertEquals("authorization=Basic ****(8)", SecretMasker.sanitize("authorization=Basic dXNlcjpw"))
    }

    @Test
    fun `sanitize masks a plain key value pair`() {
        assertEquals("connect password=****(6) ok", SecretMasker.sanitize("connect password=hunter ok"))
    }

    @Test
    fun `sanitize leaves text without secrets untouched`() {
        assertEquals("nothing to hide here", SecretMasker.sanitize("nothing to hide here"))
    }

    @Test
    fun `maskPath masks a password that contains at signs`() {
        assertEquals("smb://user:****(4)@host/share", SecretMasker.maskPath("smb://user:p@ss@host/share"))
    }

    @Test
    fun `maskPath masks a simple password`() {
        assertEquals("smb://user:****(4)@host/share", SecretMasker.maskPath("smb://user:pass@host/share"))
    }

    @Test
    fun `maskPath leaves a port without credentials untouched`() {
        assertEquals("smb://host:445/share", SecretMasker.maskPath("smb://host:445/share"))
    }

    @Test
    fun `empty inputs report empty`() {
        assertEquals("(empty)", SecretMasker.maskPath(""))
        assertEquals("(empty)", SecretMasker.maskFull(null))
    }
}
