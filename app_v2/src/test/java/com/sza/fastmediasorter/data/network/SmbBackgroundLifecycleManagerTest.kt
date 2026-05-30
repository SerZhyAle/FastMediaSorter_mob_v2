package com.sza.fastmediasorter.data.network

import androidx.lifecycle.LifecycleOwner
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/** Unit tests for [SmbBackgroundLifecycleManager] - onStop closes SMB UI connections. */
class SmbBackgroundLifecycleManagerTest {

    @Test
    fun `onStop closes UI connections exactly once`() {
        val manager = mockk<SmbConnectionManager>(relaxed = true)
        val observer = SmbBackgroundLifecycleManager(manager)
        observer.onStop(mockk<LifecycleOwner>(relaxed = true))
        verify(exactly = 1) { manager.closeUiConnections() }
    }
}
