package com.sza.fastmediasorter.core.launcher

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4030: the messenger-thread row is offered only by a build whose manifest declares `READ_CONTACTS`,
 * because the picker's one-time grant does not cover the contact's `/entities` rows.
 *
 * Only the two actions that need no package manager are driven here; the dial and SMS rows resolve
 * intents against a real device and are not part of this decision.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
class ContactActionAvailabilityProviderTest {

    private val provider = ContactActionAvailabilityProvider(mockk<Context>(relaxed = true))

    @Test
    fun `message row is offered when the build declares the permission`() {
        assertTrue(provider.isAvailable(LauncherContactAction.MESSAGE, readContactsDeclared = true))
    }

    @Test
    fun `message row is left out when the rollback strips the permission`() {
        assertFalse(provider.isAvailable(LauncherContactAction.MESSAGE, readContactsDeclared = false))
    }

    @Test
    fun `contact card row never depends on the permission`() {
        assertTrue(provider.isAvailable(LauncherContactAction.PROFILE, readContactsDeclared = true))
        assertTrue(provider.isAvailable(LauncherContactAction.PROFILE, readContactsDeclared = false))
    }

    @Test
    fun `public entry point follows the build fact the manifest overlay also follows`() {
        assertEquals(
            BuildConfig.DECLARES_READ_CONTACTS,
            provider.isAvailable(LauncherContactAction.MESSAGE),
        )
    }
}
