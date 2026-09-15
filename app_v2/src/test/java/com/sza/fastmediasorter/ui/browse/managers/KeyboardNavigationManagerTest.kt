package com.sza.fastmediasorter.ui.browse.managers

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * S3067: arrow navigation in an empty browser list used to crash with
 * IllegalArgumentException from coerceIn on the empty range 0..-1.
 */
class KeyboardNavigationManagerTest {

    private val recyclerView = mockk<RecyclerView>(relaxed = true)
    private val callbacks = mockk<KeyboardNavigationManager.KeyboardNavigationCallbacks>(relaxed = true)

    private fun manager(): KeyboardNavigationManager {
        every { recyclerView.layoutManager } returns mockk<LinearLayoutManager>(relaxed = true)
        return KeyboardNavigationManager(recyclerView, callbacks)
    }

    @Test
    fun nextFile_onEmptyList_doesNotThrowOrScroll() {
        every { callbacks.getMediaFilesCount() } returns 0
        every { callbacks.getCurrentFocusPosition() } returns 0

        manager().dispatchCommandId("navigation.next_file")

        verify(exactly = 0) { recyclerView.scrollToPosition(any()) }
    }

    @Test
    fun nextFile_onNonEmptyList_scrollsToNextPosition() {
        every { callbacks.getMediaFilesCount() } returns 5
        every { callbacks.getCurrentFocusPosition() } returns 1

        manager().dispatchCommandId("navigation.next_file")

        verify { recyclerView.scrollToPosition(2) }
    }
}
