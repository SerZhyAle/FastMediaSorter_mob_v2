package com.sza.fastmediasorter.ui.common.widget

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.preferences.CollapsibleSectionStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollapsibleSectionsManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter_App)
    }

    @Test
    fun `register persists a user toggle to the store`() {
        val store = FakeStore()
        val manager = CollapsibleSectionsManager(store)
        val header = CollapsibleSectionHeader(context)
        val container = View(context)

        manager.register(header, container, KEY, defaultExpanded = false)
        assertFalse(container.isVisible)

        header.setExpanded(true) // user toggle (notify = true) -> manager listener persists + shows body

        assertTrue(store.isExpanded(KEY, false))
        assertTrue(container.isVisible)
    }

    @Test
    fun `register restores saved expanded state to the container`() {
        val store = FakeStore().apply { setExpanded(KEY, true) }
        val manager = CollapsibleSectionsManager(store)
        val header = CollapsibleSectionHeader(context)
        val container = View(context)

        manager.register(header, container, KEY, defaultExpanded = false)

        assertTrue(header.isExpanded())
        assertTrue(container.isVisible)
        // Restore used notify = false, so the seeded value is untouched (no spurious re-persist).
        assertTrue(store.isExpanded(KEY, false))
    }

    private class FakeStore : CollapsibleSectionStore {
        private val map = mutableMapOf<String, Boolean>()
        override fun isExpanded(key: String, default: Boolean): Boolean = map[key] ?: default
        override fun setExpanded(key: String, expanded: Boolean) {
            map[key] = expanded
        }
    }

    private companion object {
        const val KEY = "screen__section"
    }
}
