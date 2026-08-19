package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.room.withTransaction
import com.sza.fastmediasorter.data.local.db.AppDatabase
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.LauncherCellDao
import com.sza.fastmediasorter.data.local.db.LauncherCellEntity
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.testing.createAppSettings
import com.sza.fastmediasorter.util.getPackageInfoCompat
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ApplyBackupPayloadUseCaseTest {

    private val context = mockk<Context>(relaxed = true)
    private val packageManager = mockk<PackageManager>(relaxed = true)
    private val db = mockk<AppDatabase>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val resourceRepository = mockk<ResourceRepository>(relaxed = true)
    private val favoritesDao = mockk<FavoritesDao>(relaxed = true)
    private val scheduledRepo = mockk<ScheduledOperationRepository>(relaxed = true)
    private val credentialsRepository = mockk<NetworkCredentialsRepository>(relaxed = true)
    private val authSessionRepository = mockk<AuthSessionRepository>(relaxed = true)
    private val launcherCellDao = mockk<LauncherCellDao>(relaxed = true)
    private val workManagerScheduler = mockk<WorkManagerScheduler>(relaxed = true)

    private lateinit var useCase: ApplyBackupPayloadUseCase

    @Before
    fun setup() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        mockkStatic("com.sza.fastmediasorter.util.PackageManagerCompatKt")

        coEvery { db.withTransaction(any<suspend () -> Any?>()) } answers {
            runBlocking { secondArg<suspend () -> Any?>().invoke() }
        }

        every { context.packageManager } returns packageManager
        every { db.favoritesDao() } returns favoritesDao
        every { db.launcherCellDao() } returns launcherCellDao
        every { settingsRepository.getSettings() } returns flowOf(createAppSettings())
        coEvery { settingsRepository.updateSettings(any<AppSettings>()) } just Runs

        useCase = ApplyBackupPayloadUseCase(
            context = context,
            db = db,
            settingsRepository = settingsRepository,
            resourceRepository = resourceRepository,
            scheduledOperationRepository = scheduledRepo,
            credentialsRepository = credentialsRepository,
            authSessionRepository = authSessionRepository,
            workManagerScheduler = workManagerScheduler
        )
    }

    @Test
    fun `apply launcher cells restores valid shortcuts and filters uninstalled apps`() = runTest {
        val installedApp = BackupLauncherCell(
            orientation = "PORTRAIT",
            rowIndex = 0,
            colIndex = 0,
            spanW = 1,
            spanH = 1,
            kind = "SHORTCUT",
            target = "app:com.installed.app"
        )
        val missingApp = BackupLauncherCell(
            orientation = "PORTRAIT",
            rowIndex = 0,
            colIndex = 1,
            spanW = 1,
            spanH = 1,
            kind = "SHORTCUT",
            target = "app:com.missing.app"
        )
        val sectionHeader = BackupLauncherCell(
            orientation = "PORTRAIT",
            rowIndex = 1,
            colIndex = 0,
            spanW = 4,
            spanH = 1,
            kind = "SECTION",
            target = "sec:app_functions"
        )

        every { packageManager.getPackageInfoCompat("com.installed.app", 0) } returns PackageInfo()
        every { packageManager.getPackageInfoCompat("com.missing.app", 0) } throws PackageManager.NameNotFoundException()

        val payload = BackupPayload(
            version = BackupPayload.CURRENT_VERSION,
            launcherCells = listOf(installedApp, missingApp, sectionHeader)
        )

        val insertedSlot = slot<List<LauncherCellEntity>>()
        coEvery { launcherCellDao.insertAll(capture(insertedSlot)) } just Runs

        val summary = useCase(payload)

        coVerify { launcherCellDao.deleteAll() }
        assertEquals(2, summary.launcherCellsRestored)
        assertEquals(2, insertedSlot.captured.size)
        assertEquals("app:com.installed.app", insertedSlot.captured[0].target)
        assertEquals("sec:app_functions", insertedSlot.captured[1].target)
    }
}
