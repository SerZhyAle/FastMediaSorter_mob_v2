package com.sza.fastmediasorter.radio

import android.Manifest
import android.app.Application
import android.os.Build
import com.sza.fastmediasorter.domain.radio.RadioKind
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * S1433 step 02.7: pins the refusal contract of [RadioControlContractImpl].
 *
 * The case worth the test is the second one. A firmware that accepts the platform call and then does
 * nothing is invisible on a developer phone - the button looks fine and the radio does not move - so only a
 * test makes the owner's Android 8 head unit and a modern phone agree on what `toggle` returns.
 *
 * This class lives in `src/testNetworkMonitor` rather than `src/test` because its subject is compiled only
 * into the two flavors that mount `src/networkMonitor`; in the shared set it would break unit-test
 * compilation on the other four (S1450, S1453, S1455).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.16.1 maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class RadioControlContractImplTest {

    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val reader: RadioStateReader = mockk(relaxed = true)

    @Test
    fun `toggle reports true when the observed state flips`() = runTest {
        every { reader.read(RadioKind.WIFI) } returns false
        every { reader.state(RadioKind.WIFI) } returns flowOf(false, true)

        assertTrue(controller().toggle(RadioKind.WIFI))
    }

    @Test
    fun `toggle reports false when the call is accepted but nothing moves`() = runTest {
        every { reader.read(RadioKind.WIFI) } returns false
        every { reader.state(RadioKind.WIFI) } returns neverSettling(false)

        assertFalse(controller().toggle(RadioKind.WIFI))
    }

    @Test
    fun `toggle reports false without waiting when the starting state is unknown`() = runTest {
        every { reader.read(RadioKind.WIFI) } returns null

        assertFalse(controller().toggle(RadioKind.WIFI))

        // Never observing the state is the proof that no attempt was made: the confirmation window is the
        // only thing that collects it, and an unknown starting state must not reach that window.
        verify(exactly = 0) { reader.state(any()) }
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `above API 30 a missing BLUETOOTH_CONNECT refuses bluetooth and leaves wifi alone`() = runTest {
        every { reader.read(RadioKind.BLUETOOTH) } returns false
        every { reader.read(RadioKind.WIFI) } returns false
        every { reader.state(RadioKind.WIFI) } returns flowOf(false, true)

        val controller = controller()

        assertFalse(controller.toggle(RadioKind.BLUETOOTH))
        verify(exactly = 0) { reader.state(RadioKind.BLUETOOTH) }
        assertTrue(controller.toggle(RadioKind.WIFI))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `above API 30 a granted BLUETOOTH_CONNECT lets bluetooth through`() = runTest {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        every { reader.read(RadioKind.BLUETOOTH) } returns false
        every { reader.state(RadioKind.BLUETOOTH) } returns flowOf(false, true)

        assertTrue(controller().toggle(RadioKind.BLUETOOTH))
    }

    private fun controller() = RadioControlContractImpl(context, reader)

    /**
     * A state source that reports [value] and then stays open forever.
     *
     * A flow that simply completes would make the confirmation window throw instead of expiring, which
     * would test the wrong thing: the firmware being modelled here keeps reporting the old state, it does
     * not stop reporting.
     */
    private fun neverSettling(value: Boolean): Flow<Boolean?> = flow {
        emit(value)
        awaitCancellation()
    }
}
