package com.sza.fastmediasorter.ui.scheduledops

import com.sza.fastmediasorter.domain.model.AppSettings
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun viewModel(
        operations: List<ScheduledOperation> = listOf(sampleOperation),
        resourcesList: List<com.sza.fastmediasorter.domain.model.MediaResource> = emptyList(),
        operationsFlow: Flow<List<ScheduledOperation>>? = null,
    ) =
        ScheduledOperationsViewModel(
            getScheduledOperationsUseCase = getScheduledOperations.also {
                every { it() } returns (operationsFlow ?: flowOf(operations))
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
                every { it() } returns flowOf(resourcesList)
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
    fun resourcesExposesRepositoryList() = runTest {
        val sampleResource = mockk<com.sza.fastmediasorter.domain.model.MediaResource>()
        val viewModel = viewModel(resourcesList = listOf(sampleResource))
        val emitted = mutableListOf<List<com.sza.fastmediasorter.domain.model.MediaResource>>()
        val job = launch { viewModel.resources.toList(emitted) }
        advanceUntilIdle()
        assertEquals(listOf(sampleResource), emitted.last())
        job.cancel()
    }

    // The relaxed repository's settings flow never emits - the slow cold start of the race.
    @Test
    fun reconcileTargetIsNullWhileTheSwitchIsStillAPlaceholder() = runTest {
        val viewModel = viewModel(operations = emptyList())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.operations.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isEnabled.collect() }
        advanceUntilIdle()
        assertNull(viewModel.reconcileTarget())
    }

    @Test
    fun reconcileTargetIsNullForTheFirstObservedEmptyListWhateverTheSwitch() = runTest {
        every { settingsRepository.getSettings() } returns flowOf(AppSettings(enableScheduledOperations = true))
        val viewModel = viewModel(operations = emptyList())
        val loaded = mutableListOf<List<ScheduledOperation>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.loadedOperations.toList(loaded) }
        advanceUntilIdle()
        assertEquals(listOf(emptyList<ScheduledOperation>()), loaded)
        assertNull(viewModel.reconcileTarget())
    }

    @Test
    fun reconcileTargetKeepsAnExplicitOffSwitchWhenOperationsExistOnFirstLoad() = runTest {
        every { settingsRepository.getSettings() } returns flowOf(AppSettings(enableScheduledOperations = false))
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.loadedOperations.collect() }
        advanceUntilIdle()
        assertNull(viewModel.reconcileTarget())
        assertNull(viewModel.reconcileTarget())
    }

    @Test
    fun reconcileTargetTurnsTheSwitchOnWhenTheListGainsItsFirstOperation() = runTest {
        every { settingsRepository.getSettings() } returns flowOf(AppSettings(enableScheduledOperations = false))
        val stored = MutableStateFlow(emptyList<ScheduledOperation>())
        val viewModel = viewModel(operationsFlow = stored)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.loadedOperations.collect() }
        advanceUntilIdle()
        assertNull(viewModel.reconcileTarget())
        stored.value = listOf(sampleOperation)
        advanceUntilIdle()
        assertEquals(true, viewModel.reconcileTarget())
    }

    @Test
    fun reconcileTargetTurnsTheSwitchOffWhenTheListLosesItsLastOperation() = runTest {
        every { settingsRepository.getSettings() } returns flowOf(AppSettings(enableScheduledOperations = true))
        val stored = MutableStateFlow(listOf(sampleOperation))
        val viewModel = viewModel(operationsFlow = stored)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.loadedOperations.collect() }
        advanceUntilIdle()
        assertNull(viewModel.reconcileTarget())
        stored.value = emptyList()
        advanceUntilIdle()
        assertEquals(false, viewModel.reconcileTarget())
    }

    @Test
    fun reconcileTargetIsNullWhenTheTransitionAlreadyMatchesTheSwitch() = runTest {
        every { settingsRepository.getSettings() } returns flowOf(AppSettings(enableScheduledOperations = true))
        val stored = MutableStateFlow(emptyList<ScheduledOperation>())
        val viewModel = viewModel(operationsFlow = stored)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.loadedOperations.collect() }
        advanceUntilIdle()
        assertNull(viewModel.reconcileTarget())
        stored.value = listOf(sampleOperation)
        advanceUntilIdle()
        assertNull(viewModel.reconcileTarget())
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
        assertEquals(listOf("run log body"), viewModel.loadHistory().map { it.raw })
        viewModel.clearLog().join()
        coVerify { clearScheduledOperationsLog() }
    }

    @Test
    fun loadHistoryKeepsOnlyTheNewestRowsNewestFirst() = runTest {
        val total = ScheduledOperationsViewModel.MAX_HISTORY_ROWS + 5
        every { getScheduledOperationsLog() } returns (1..total).joinToString("\n") { "line $it" }
        val history = viewModel().loadHistory()
        assertEquals(ScheduledOperationsViewModel.MAX_HISTORY_ROWS, history.size)
        assertEquals("line $total", history.first().raw)
        assertEquals("line 6", history.last().raw)
    }
}
