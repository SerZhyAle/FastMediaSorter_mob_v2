package com.sza.fastmediasorter.wear.domain.files

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4230: a receiver that would end in a refusal is not offered at all. */
class WearResourceReceiverPolicyTest {

    @Test
    fun `a plain writable folder is offered`() {
        assertTrue(WearResourceReceiverPolicy.isOffered(source(), pinHeld = false))
    }

    @Test
    fun `a read-only folder is never offered`() {
        assertFalse(WearResourceReceiverPolicy.isOffered(source(readOnly = true), pinHeld = true))
    }

    @Test
    fun `a protected folder is offered only while its PIN is held`() {
        assertFalse(WearResourceReceiverPolicy.isOffered(source(pinProtected = true), pinHeld = false))
        assertTrue(WearResourceReceiverPolicy.isOffered(source(pinProtected = true), pinHeld = true))
    }

    @Test
    fun `an exchange-bound writable folder is offered`() {
        assertTrue(WearResourceReceiverPolicy.isOffered(source(exchangeResourceId = "res-1"), pinHeld = false))
    }

    @Test
    fun `an exchange-bound read-only folder is not offered`() {
        assertFalse(
            WearResourceReceiverPolicy.isOffered(
                source(exchangeResourceId = "res-1", readOnly = true),
                pinHeld = true
            )
        )
    }

    @Test
    fun `an exchange-bound protected folder is offered only while its PIN is held`() {
        val protected = source(exchangeResourceId = "res-1", pinProtected = true)

        assertFalse(WearResourceReceiverPolicy.isOffered(protected, pinHeld = false))
        assertTrue(WearResourceReceiverPolicy.isOffered(protected, pinHeld = true))
    }

    private fun source(
        readOnly: Boolean = false,
        pinProtected: Boolean = false,
        exchangeResourceId: String? = null,
    ) = NetworkSource(
        id = "s1",
        type = NetworkSourceType.SFTP,
        name = "Home",
        server = "192.168.1.5",
        username = "u",
        password = "p",
        readOnly = readOnly,
        pinProtected = pinProtected,
        exchangeResourceId = exchangeResourceId,
    )
}
