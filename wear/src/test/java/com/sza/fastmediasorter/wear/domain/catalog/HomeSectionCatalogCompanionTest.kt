package com.sza.fastmediasorter.wear.domain.catalog

import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.HomeSectionVisibility
import com.sza.fastmediasorter.wear.domain.model.PhoneCompanionHint
import com.sza.fastmediasorter.wear.domain.model.PhoneCompanionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S4011: the phone-bound rows follow the companion state, and Resources also follows the sources. */
class HomeSectionCatalogCompanionTest {

    private val phoneBound = setOf(HomeSectionId.RESOURCES, HomeSectionId.PHONE, HomeSectionId.PHONE_CAMERA)

    @Test
    fun `present shows every phone-bound row and no hint`() {
        val v = visibility(PhoneCompanionState.PRESENT)

        assertTrue(ids(v).containsAll(phoneBound))
        assertNull(HomeSectionCatalog.companionHintFor(v))
    }

    @Test
    fun `absent hides the three rows and asks to install`() {
        val v = visibility(PhoneCompanionState.ABSENT)

        assertTrue(ids(v).none { it in phoneBound })
        assertEquals(PhoneCompanionHint.INSTALL_ON_PHONE, HomeSectionCatalog.companionHintFor(v))
    }

    @Test
    fun `unreachable hides the three rows and asks to connect`() {
        val v = visibility(PhoneCompanionState.PHONE_UNREACHABLE)

        assertTrue(ids(v).none { it in phoneBound })
        assertEquals(PhoneCompanionHint.CONNECT_PHONE, HomeSectionCatalog.companionHintFor(v))
    }

    @Test
    fun `unknown hides the rows but says nothing yet`() {
        val v = visibility(PhoneCompanionState.UNKNOWN)

        assertTrue(ids(v).none { it in phoneBound })
        assertNull(HomeSectionCatalog.companionHintFor(v))
    }

    @Test
    fun `a registered network source keeps resources without the phone`() {
        PhoneCompanionState.entries.forEach { state ->
            val found = ids(visibility(state, hasNetworkSources = true))

            assertTrue("$state", found.contains(HomeSectionId.RESOURCES))
            assertEquals("$state", state == PhoneCompanionState.PRESENT, found.contains(HomeSectionId.PHONE))
            assertEquals(
                "$state",
                state == PhoneCompanionState.PRESENT,
                found.contains(HomeSectionId.PHONE_CAMERA)
            )
        }
    }

    @Test
    fun `the hint still names the hidden phone rows when resources stays`() {
        val v = visibility(PhoneCompanionState.ABSENT, hasNetworkSources = true)

        assertEquals(PhoneCompanionHint.INSTALL_ON_PHONE, HomeSectionCatalog.companionHintFor(v))
    }

    @Test
    fun `a flavor without phone rows never hints`() {
        val v = HomeSectionVisibility(
            streamsEnabled = false,
            offersRemoteSources = false,
            offersContentTransfer = false,
            phoneCompanion = PhoneCompanionState.ABSENT
        )

        assertFalse(ids(v).any { it in phoneBound })
        assertNull(HomeSectionCatalog.companionHintFor(v))
    }

    private fun ids(v: HomeSectionVisibility) = HomeSectionCatalog.sectionsFor(v).map { it.id }

    private fun visibility(state: PhoneCompanionState, hasNetworkSources: Boolean = false) =
        HomeSectionVisibility(
            streamsEnabled = false,
            phoneCompanion = state,
            hasNetworkSources = hasNetworkSources
        )
}
