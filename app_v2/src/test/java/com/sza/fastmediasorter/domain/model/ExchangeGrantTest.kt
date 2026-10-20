package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeGrantTest {

    @Test
    fun `a link has its own share id and a login derived from its grant id`() {
        val link = ExchangeGrants.newLink("content://tree/photos", "Photos")

        assertTrue(SftpShareId.isValid(link.grantId))
        assertTrue(SftpShareId.isValid(link.linkShareId))
        assertNotEquals(link.grantId, link.linkShareId)
        assertEquals("g-" + link.grantId.take(8), link.login)
        assertNull(link.guest)
        assertEquals(ExchangeGrant.Kind.LINK, link.kind)
    }

    @Test
    fun `a guest grant names the guest and has no share id`() {
        val guest = ExchangeGrants.newGuest("content://tree/photos", "Photos", "  anna ")

        assertEquals("anna", guest.guest)
        assertNull(guest.linkShareId)
        assertEquals(ExchangeGrant.Kind.GUEST, guest.kind)
    }

    @Test
    fun `generated passwords are long and a typed link password needs eight characters`() {
        assertEquals(ExchangeGrants.GENERATED_PASSWORD_LENGTH, ExchangeGrants.generatePassword().length)
        assertFalse(ExchangeGrants.isAcceptableLinkPassword("1234567"))
        assertTrue(ExchangeGrants.isAcceptableLinkPassword("12345678"))
    }

    @Test
    fun `toString carries neither the grant id nor the share id`() {
        val link = ExchangeGrants.newLink("content://tree/photos", "Photos")

        assertFalse(link.toString().contains(link.grantId))
        assertFalse(link.toString().contains(checkNotNull(link.linkShareId)))
    }
}
