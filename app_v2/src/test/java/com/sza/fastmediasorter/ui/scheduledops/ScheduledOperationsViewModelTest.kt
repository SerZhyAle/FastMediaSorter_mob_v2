package com.sza.fastmediasorter.ui.scheduledops

import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.TimeFilter
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.CheckLocalFolderWritableUseCase
import com.sza.fastmediasorter.domain.usecase.CleanupHiddenResourceUseCase
import com.sza.fastmediasorter.domain.usecase.ClearScheduledOperationsLogUseCase
import com.sza.fastmediasorter.domain.usecase.ClearScheduledOperationsUseCase
import com.sza.fastmediasorter.domain.usecase.DeleteScheduledOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.GetScheduledOperationsLogUseCase
import com.sza.fastmediasorter.domain.usecase.GetScheduledOperationsUseCase
import com.sza.fastmediasorter.domain.usecase.ResolveLocalFolderResourceUseCase
import com.sza.fastmediasorter.domain.usecase.UpdateScheduledOperationUseCase
import com.sza.fastmediasorter.domain.usecase.UpsertScheduledOperationUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Unit coverage for the relocated [ScheduledOperationsViewModel] (S3365): state exposure plus the
 * per-operation and group run controls the program screen wires. The execution engine stays behind
 * mocks - the ViewModel owns no scheduling arithmetic itself (strategic ADR-3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledOperationsViewModelTest {

    // Unconfined Main so viewModelScope.launch bodies (toggle/delete/run controls) execute
    // eagerly and MockK verifies see the recorded calls without scheduler stepping.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val getScheduledOperations = mockk<GetScheduledOperationsUseCase>()
    private val upsertScheduledOperation = mockk<UpsertScheduledOperationUseCase>()
    private val updateScheduledOperation = mockk<UpdateScheduledOperationUseCase>()
    private val deleteScheduledOperation = mockk<DeleteScheduledOperationUseCase>()
    private val clearScheduledOperations = mockk<ClearScheduledOperationsUseCase>()
    private val getScheduledOperationsLog = mockk<GetScheduledOperationsLogUseCase>()
    private val clearScheduledOperationsLog = mockk<ClearScheduledOperationsLogUseCase>()
    private val workManagerScheduler = mockk<WorkManagerScheduler>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val resolveLocalFolderResource = mockk<ResolveLocalFolderResourceUseCase>()
    private val checkLocalFolderWritable = mockk<CheckLocalFolderWritableUseCase>()
    private val getResources = mockk<GetResourcesUseCase>()
    private val cleanupHiddenResource = mockk<CleanupHiddenResourceUseCase>()

    private val sampleOperation = ScheduledOperation(
        id = 1L,
        sourceResourceId = 10L,
        operationType = ScheduledOpType.COPY,
        targetResourceId = 20L,
        timeFilter = TimeFilter.ALL,
        startTimeHour = 9,
        startTimeMinute = 0,
        intervalHours = 1,
        intervalMinutes = 0,
    )

    private fun viewModel(operations: List<ScheduledOperation> = listOf(sampleOperation)) =
        ScheduledOperationsViewModel(
            getScheduledOperationsUseCase = getScheduledOperations.also {
                every { it() } returns flowOf(operations)
            },
            upsertScheduledOperationUseCase = upsertScheduledOperation,
            updateScheduledOperationUseCase = updateScheduledOperation,
            deleteScheduledOperationUseCase = deleteScheduledOperation,
            clearScheduledOperationsUseCase = clearScheduledOperations,
            getScheduledOperationsLogUseCase = getScheduledOperationsLog,
            clearScheduledOperationsLogUseCase = clearScheduledOperationsLog,
            workManagerScheduler = workManagerScheduler,
            settingsRepository = settingsRepository,
            resolveLocalFolderResourceUseCase = resolveLocalFolderResource,
            checkLocalFolderWritableUseCase = checkLocalFolderWritable,
            getResourcesUseCase = getResources.also {
                every { it() } returns flowOf(emptyList())
            },
            cleanupHiddenResourceUseCase = cleanupHiddenResource,
        )

    @Test
    fun operationsExposesRepositoryList() = runTest {
        val viewModel = viewModel()
        val emitted = mutableListOf<List<ScheduledOperation>>()
        val job = launch { viewModel.operations.toList(emitted) }
        advanceUntilIdle()
        assertEquals(listOf(sampleOperation), emitted.last())
        job.cancel()
    }

    @Test
    fun toggleEnabledCancelsWorkWhenDisabling() = runTest {
        coEvery { updateScheduledOperation(any()) } just runs
        val viewModel = viewModel()
        viewModel.toggleEnabled(sampleOperation)
        coVerify { updateScheduledOperation(match { !it.isEnabled }) }
        verify { workManagerScheduler.cancelOperation(sampleOperation.id) }
    }

    @Test
    fun toggleEnabledSchedulesWorkWhenEnabling() = runTest {
        coEvery { updateScheduledOperation(any()) } just runs
        val viewModel = viewModel()
        viewModel.toggleEnabled(sampleOperation.copy(isEnabled = false))
        coVerify { updateScheduledOperation(match { it.isEnabled }) }
        verify { workManagerScheduler.scheduleOperation(match { it.id == sampleOperation.id }) }
    }

    @Test
    fun deleteCancelsWorkAndCleansHiddenFkResources() = runTest {
        coEvery { deleteScheduledOperation(any()) } just runs
        coEvery { cleanupHiddenResource(any()) } just runs
        val viewModel = viewModel()
        // Subscribe so the stateIn(WhileSubscribed) list is populated before delete reads it.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.operations.collect() }
        advanceUntilIdle()
        viewModel.delete(sampleOperation.id)
        coVerify { deleteScheduledOperation(sampleOperation.id) }
        verify { workManagerScheduler.cancelOperation(sampleOperation.id) }
        coVerify { cleanupHiddenResource(10L) }
        coVerify { cleanupHiddenResource(20L) }
    }

    @Test
    fun runControlsDelegateToScheduler() = runTest {
        val viewModel = viewModel()
        viewModel.runNow(sampleOperation.id)
        viewModel.runAllNow()
        viewModel.pauseAll()
        viewModel.resumeAll()
        verify { workManagerScheduler.runNow(sampleOperation.id) }
        coVerify { workManagerScheduler.runAllNow() }
        coVerify { workManagerScheduler.pauseAll() }
        coVerify { workManagerScheduler.resumeAll() }
    }

    @Test
    fun logReadAndClearRoundTrip() = runTest {
        every { getScheduledOperationsLog() } returns "run log body"
        every { clearScheduledOperationsLog() } just runs
        val viewModel = viewModel()
        assertEquals("run log body", viewModel.getLog())
        viewModel.clearLog()
        coVerify { clearScheduledOperationsLog() }
    }
}
