package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** S4011: the three answers of strategic 3.1, from the two Data Layer node sets. */
class PhoneCompanionStateTest {

    @Test
    fun `no connected node is an unreachable phone`() {
        assertEquals(
            PhoneCompanionState.PHONE_UNREACHABLE,
            resolvePhoneCompanionState(emptySet(), setOf("phone"))
        )
    }

    @Test
    fun `a connected node with the capability is present`() {
        assertEquals(
            PhoneCompanionState.PRESENT,
            resolvePhoneCompanionState(setOf("phone"), setOf("phone"))
        )
    }

    @Test
    fun `a connected node without the capability is absent`() {
        assertEquals(
            PhoneCompanionState.ABSENT,
            resolvePhoneCompanionState(setOf("phone"), emptySet())
        )
    }

    @Test
    fun `a capability on a node that is no longer connected does not count`() {
        assertEquals(
            PhoneCompanionState.ABSENT,
            resolvePhoneCompanionState(setOf("phone"), setOf("old-phone"))
        )
    }
}
