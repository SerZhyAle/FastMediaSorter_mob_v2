package com.sza.fastmediasorter.wear.ui.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2486: pins both directions of the add-resource gate.
 *
 * The withheld direction is the one worth a test. After this ticket the developer's own build is `noLegal`,
 * where the answer is always true, so nothing a developer runs by hand ever exercises the false branch - and
 * that branch is the whole compliance claim, WO-P6 refusing credential entry in the build Play distributes.
 * `NetworkSourceEntry` takes the answer as a parameter precisely so this file can reach it.
 *
 * Lives in the shared `src/test` set rather than a flavor one because its subject is in `src/main` and
 * references no flavor-scoped type - `dev/FLAVOR_DEVELOPMENT_RULES.md` Rule 7.
 */
class NetworkSourceEntryTest {

    @Test
    fun `entry is offered when the flavor offers credential entry`() {
        assertTrue(NetworkSourceEntry.isOffered(offersCredentialEntry = true))
    }

    @Test
    fun `entry is withheld when the flavor withholds credential entry`() {
        assertFalse(NetworkSourceEntry.isOffered(offersCredentialEntry = false))
    }
}
