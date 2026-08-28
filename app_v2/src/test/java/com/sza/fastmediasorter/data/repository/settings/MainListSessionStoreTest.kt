package com.sza.fastmediasorter.data.repository.settings

import android.content.Context
import com.sza.fastmediasorter.domain.model.MainListSession
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S2199: the store is the whole guarantee this ticket delivers, so the cases that matter are the ones
 * a device walk would not think to try - a filter the user switched off must read back as absent on
 * the next start, and a stored name a later release removed must not stop the screen from opening.
 *
 * Every case writes its own complete baseline rather than relying on a fresh file. `preferencesDataStore`
 * hands out one instance per delegate for the life of the process, so the backing file cannot be swapped
 * between test methods; establishing the full state in each case is what keeps them independent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class MainListSessionStoreTest {

    // RuntimeEnvironment, not ApplicationProvider: androidx.test:core is an androidTest dependency
    // here, so the instrumentation helper does not exist on the unit-test classpath.
    private val context: Context = RuntimeEnvironment.getApplication()
    private val store = MainListSessionStore(context)

    @Test
    fun `a written sort and both filter sets read back unchanged`() = runTest {
        store.write(
            MainListSession(
                sortMode = SortMode.NAME_ASC,
                filterByType = setOf(ResourceType.LOCAL, ResourceType.SMB),
                filterByMediaType = setOf(MediaType.IMAGE),
                filterByName = "holiday"
            )
        )

        val session = store.read()

        assertEquals(SortMode.NAME_ASC, session.sortMode)
        assertEquals(setOf(ResourceType.LOCAL, ResourceType.SMB), session.filterByType)
        assertEquals(setOf(MediaType.IMAGE), session.filterByMediaType)
        assertEquals("holiday", session.filterByName)
    }

    @Test
    fun `a filter cleared to null does not linger as the previously stored one`() = runTest {
        store.write(
            MainListSession(
                sortMode = SortMode.DATE_DESC,
                filterByType = setOf(ResourceType.FTP),
                filterByMediaType = setOf(MediaType.VIDEO),
                filterByName = "draft"
            )
        )

        store.write(MainListSession(sortMode = SortMode.DATE_DESC))
        val session = store.read()

        assertNull(session.filterByType)
        assertNull(session.filterByMediaType)
        assertNull(session.filterByName)
        assertEquals(SortMode.DATE_DESC, session.sortMode)
    }

    @Test
    fun `an empty filter set reads back as no filter rather than as an empty one`() = runTest {
        store.write(
            MainListSession(
                sortMode = SortMode.MANUAL,
                filterByType = emptySet(),
                filterByMediaType = emptySet(),
                filterByName = null
            )
        )

        val session = store.read()

        // Null, not an empty set: MainState spells "no filter" as null, and an empty set there would
        // raise the filters banner over a filter that narrows nothing.
        assertNull(session.filterByType)
        assertNull(session.filterByMediaType)
    }

    @Test
    fun `nothing written reads back as nothing remembered`() = runTest {
        store.write(MainListSession(sortMode = SortMode.MANUAL))

        val session = store.read()

        assertEquals(SortMode.MANUAL, session.sortMode)
        assertNull(session.filterByType)
        assertNull(session.filterByMediaType)
        assertNull(session.filterByName)
    }
}
