package com.sza.fastmediasorter.domain.remotecontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4701: how the account directory and the stored grants meet in the "Who can control" list. */
class ManageRemoteControlUseCaseTest {

    private fun candidates(vararg devices: Pair<String, String>, complete: Boolean = true) =
        RemoteControllerCandidates(
            devices = devices.map { RemoteControllerCandidate(it.first, it.second) },
            complete = complete,
            unreachable = false,
        )

    @Test
    fun `a listed device without a grant shows no rights and a new grant carries safe only`() {
        val choices = RemoteControllerChoice.merge(candidates("d1" to "Laptop"), emptyList())

        assertEquals(1, choices.size)
        assertNull(choices.single().grant)
        assertEquals(
            mapOf(RemoteRiskClass.SAFE to RemoteConsentMode.ALWAYS),
            RemoteControllerGrant("d1", "Laptop").classes,
        )
    }

    @Test
    fun `a listed device keeps the rights it was granted`() {
        val classes = mapOf(
            RemoteRiskClass.SAFE to RemoteConsentMode.ALWAYS,
            RemoteRiskClass.WRITES_USER_DATA to RemoteConsentMode.ASK,
        )
        val grant = RemoteControllerGrant("d1", "Laptop", classes)
        val choice = RemoteControllerChoice.merge(candidates("d1" to "Laptop"), listOf(grant)).single()

        assertEquals(grant, choice.grant)
        assertTrue(choice.onAccount)
    }

    @Test
    fun `a controller the complete directory no longer lists stays visible as off the account`() {
        val gone = RemoteControllerGrant("d9", "Old tablet")
        val choices = RemoteControllerChoice.merge(candidates("d1" to "Laptop"), listOf(gone))

        val kept = choices.single { it.deviceId == "d9" }
        assertEquals(gone, kept.grant)
        assertFalse(kept.onAccount)
    }

    @Test
    fun `an incomplete directory does not claim a controller left the account`() {
        val unknown = RemoteControllerGrant("d9", "Old tablet")
        val choices = RemoteControllerChoice.merge(candidates(complete = false), listOf(unknown))

        assertTrue(choices.single().onAccount)
    }

    @Test
    fun `rows are ordered by name regardless of case`() {
        val choices = RemoteControllerChoice.merge(
            candidates("a" to "tablet", "b" to "Laptop"),
            listOf(RemoteControllerGrant("c", "Desktop")),
        )

        assertEquals(listOf("Desktop", "Laptop", "tablet"), choices.map { it.deviceName })
    }
}
