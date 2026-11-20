package com.sza.fastmediasorter.data.remotecontrol

import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteCommandRingTest {
    private val context = RuntimeEnvironment.getApplication()
    private var clock = 1_000_000L

    @Before
    fun setUp() {
        File(context.noBackupFilesDir, RemoteCommandRing.RING_FILE).delete()
    }

    private fun ring() = RemoteCommandRing(context).also { it.now = { clock } }

    @Test
    fun `a repeat before expiry gets the first answer`() = runTest {
        val ring = ring()
        ring.record("ctl", "req-1", RemoteCommandAnswer.declined(), keepUntilMillis = clock + 10_000)

        assertEquals(RemoteCommandAnswer.declined(), ring.find("ctl", "req-1"))
        assertNull(ring.find("other", "req-1"))
    }

    @Test
    fun `the answer survives a fresh instance over the same file`() = runTest {
        ring().record("ctl", "req-2", RemoteCommandAnswer.done(), keepUntilMillis = clock + 10_000)

        assertEquals(RemoteCommandAnswer.done(), ring().find("ctl", "req-2"))
    }

    @Test
    fun `an expired entry is gone`() = runTest {
        val ring = ring()
        ring.record("ctl", "req-3", RemoteCommandAnswer.done(), keepUntilMillis = clock + 10_000)
        clock += 10_001

        assertNull(ring.find("ctl", "req-3"))
    }
}
