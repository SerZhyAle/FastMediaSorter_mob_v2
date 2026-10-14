package com.sza.fastmediasorter.data.remote.sftp.server

import com.sza.fastmediasorter.domain.model.ExchangeGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpGrantLoginsTest {

    private val roots = listOf("content://tree/photos", "content://tree/music")
    private val grant = ExchangeGrant(
        grantId = "AAAAAAAAAAAAAAAAAAAAAA",
        kind = ExchangeGrant.Kind.LINK,
        rootUri = "content://tree/photos",
        login = "g-AAAAAAAA",
        linkShareId = "BBBBBBBBBBBBBBBBBBBBBA",
    )
    private val logins = SftpGrantLogins().apply { replace(listOf(SftpGrantLogins.Live(grant, "link-password"))) }

    @Test
    fun `a grant login sees only its granted root`() {
        assertEquals(listOf("content://tree/photos"), logins.rootsFor("g-AAAAAAAA", INSTALL, roots) { it })
    }

    @Test
    fun `the install login sees every root and an unknown login none`() {
        assertEquals(roots, logins.rootsFor(INSTALL, INSTALL, roots) { it })
        assertTrue(logins.rootsFor("stranger", INSTALL, roots) { it }.isEmpty())
        assertTrue(logins.rootsFor(null, INSTALL, roots) { it }.isEmpty())
    }

    @Test
    fun `a grant login authenticates with its own password only`() {
        assertTrue(logins.accepts("g-AAAAAAAA", "link-password"))
        assertFalse(logins.accepts("g-AAAAAAAA", "wrong"))
        assertFalse(logins.accepts(INSTALL, "link-password"))
    }

    @Test
    fun `a withdrawn grant authenticates no more and sees nothing`() {
        logins.replace(emptyList())

        assertFalse(logins.accepts("g-AAAAAAAA", "link-password"))
        assertFalse(logins.isGrantLogin("g-AAAAAAAA"))
        assertTrue(logins.rootsFor("g-AAAAAAAA", INSTALL, roots) { it }.isEmpty())
    }

    private companion object {
        const val INSTALL = "fms"
    }
}
