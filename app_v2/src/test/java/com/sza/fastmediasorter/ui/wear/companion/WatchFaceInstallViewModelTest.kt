package com.sza.fastmediasorter.ui.wear.companion

import com.sza.fastmediasorter.domain.model.WatchFaceOpenResult
import com.sza.fastmediasorter.domain.usecase.OpenWatchFaceOnWatchUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WatchFaceInstallViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val openWatchFace = mockk<OpenWatchFaceOnWatchUseCase>()

    private fun viewModel() = WatchFaceInstallViewModel(openWatchFace, dispatcherRule.testDispatcher)

    private fun assertEmits(expected: WatchFaceOpenResult) = runTest(dispatcherRule.testDispatcher) {
        coEvery { openWatchFace() } returns expected
        val vm = viewModel()

        vm.install()
        advanceUntilIdle()

        assertEquals(expected, vm.events.first())
    }

    @Test
    fun `opened on the watch reaches the host with the watch name`() =
        assertEmits(WatchFaceOpenResult.OpenedOnWatch("Galaxy Watch"))

    @Test
    fun `no watch reaches the host`() = assertEmits(WatchFaceOpenResult.NoWatch)

    @Test
    fun `no store on the watch reaches the host`() = assertEmits(WatchFaceOpenResult.WatchStoreUnavailable)

    @Test
    fun `failure reaches the host`() = assertEmits(WatchFaceOpenResult.Failed)

    @Test
    fun `a second tap while the first is running asks the watch once`() = runTest(dispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<WatchFaceOpenResult>()
        coEvery { openWatchFace() } coAnswers { gate.await() }
        val vm = viewModel()

        vm.install()
        advanceUntilIdle()
        vm.install()
        gate.complete(WatchFaceOpenResult.NoWatch)
        advanceUntilIdle()

        coVerify(exactly = 1) { openWatchFace() }
        assertEquals(WatchFaceOpenResult.NoWatch, vm.events.first())
    }
}
