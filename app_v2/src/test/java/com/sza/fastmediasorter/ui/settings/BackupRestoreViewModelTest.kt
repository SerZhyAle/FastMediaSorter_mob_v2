package com.sza.fastmediasorter.ui.settings

import android.content.Context
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.domain.usecase.BackupToGoogleDriveUseCase
import com.sza.fastmediasorter.domain.usecase.RestoreFromGoogleDriveUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit coverage for [BackupRestoreViewModel]'s gms-only section serialization (S3792): the ambient
 * `gmsOnlyMode` on the GoogleDriveRestClient singleton must never be held across suspension by two
 * of this ViewModel's Drive sections at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = mockk(relaxed = true)
    private val backupUseCase = mockk<BackupToGoogleDriveUseCase>()
    private val restoreUseCase = mockk<RestoreFromGoogleDriveUseCase>()
    private val googleDriveClient = mockk<GoogleDriveRestClient>()

    @Before
    fun setup() {
        every { googleDriveClient.isAuthenticated() } returns true
    }

    private fun createViewModel(): BackupRestoreViewModel = BackupRestoreViewModel(
        context = context,
        backupUseCase = backupUseCase,
        restoreUseCase = restoreUseCase,
        googleDriveClient = googleDriveClient,
        favoritesTransfer = mockk(relaxed = true),
        exportResourcesToFileUseCase = mockk(relaxed = true),
        szaResourcesImporter = mockk(relaxed = true),
        resourceRepository = mockk(relaxed = true),
        identityRepository = mockk(relaxed = true)
    )

    @Test
    fun `concurrent restore runs hold the gms-only mode one section at a time`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // The real coordinator returns the mode it replaced; the fake keeps the same contract.
            var currentMode = false
            val modeLog = mutableListOf<Boolean>()
            every { googleDriveClient.setGmsOnlyMode(any()) } answers {
                val requested = firstArg<Boolean>()
                modeLog.add(requested)
                val previous = currentMode
                currentMode = requested
                previous
            }
            val restoreGate = CompletableDeferred<Unit>()
            coEvery { restoreUseCase() } coAnswers {
                restoreGate.await()
                Result.success(RestoreFromGoogleDriveUseCase.RestoreResult(false, 0, 0, 0))
            }

            val viewModel = createViewModel()
            // confirmRestore reaches the suspending section without the sync pre-check wrapper,
            // so every mode toggle in the log belongs to one serialized section.
            viewModel.confirmRestore()
            viewModel.confirmRestore()
            runCurrent()

            // The first section holds the mode across the gate; the second is parked on the mutex
            // and has not flipped anything yet.
            assertEquals(listOf(true), modeLog)

            restoreGate.complete(Unit)
            runCurrent()

            // Closed pair per section: on -> work -> off, then the second run's own pair. The
            // pre-fix interleaving recorded [true, true, false, false].
            assertEquals(listOf(true, false, true, false), modeLog)
            assertTrue(viewModel.uiState.value is BackupRestoreUiState.RestoreSuccess)
        }
}
