package com.sza.fastmediasorter.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.data.datasource.DeviceProfileLocalDataSource
import com.sza.fastmediasorter.data.model.DetectionConfidence
import com.sza.fastmediasorter.data.model.DeviceProfile
import com.sza.fastmediasorter.data.model.DeviceProfileSource
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.detector.DeviceProfileDetector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S2746: every test builds the repository on a scope tied to its own scheduler. The repository's
 * init coroutine used to run on a self-built scope over the real IO pool, so it outlived the test
 * body and any throw from it landed in the global handler, where kotlinx-coroutines-test charged it
 * to whatever unrelated test ran next on that worker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealDeviceProfileRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val appPrefs = mockk<SharedPreferences>(relaxed = true)
    private val welcomePrefs = mockk<SharedPreferences>(relaxed = true)
    private val appPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)

    private val detector = mockk<DeviceProfileDetector>()
    private val localDataSource = mockk<DeviceProfileLocalDataSource>(relaxed = true)
    private lateinit var repository: RealDeviceProfileRepository

    @Before
    fun setup() {
        every { context.packageName } returns "com.sza.fastmediasorter"
        every {
            context.getSharedPreferences("com.sza.fastmediasorter_preferences", any())
        } returns appPrefs
        every { context.getSharedPreferences("welcome_prefs", any()) } returns welcomePrefs
        every { appPrefs.edit() } returns appPrefsEditor
        every { appPrefsEditor.putBoolean(any(), any()) } returns appPrefsEditor
    }

    @Test
    fun `migrates existing install to OTHER profile on first run`() = runTest {
        every { appPrefs.getBoolean("device_profile_initialized", false) } returns false
        every { welcomePrefs.getBoolean("welcome_completed", false) } returns true

        repository = RealDeviceProfileRepository(
            detector,
            localDataSource,
            context,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )

        coVerify {
            localDataSource.saveProfile(
                match {
                    it.type == DeviceProfileType.OTHER &&
                        it.source == DeviceProfileSource.MIGRATION_EXISTING &&
                        it.confidence == DetectionConfidence.NONE &&
                        it.presetVersion == 0
                }
            )
        }
    }

    @Test
    fun `isMigrationExisting returns true when profile source is MIGRATION_EXISTING`() = runTest {
        every { appPrefs.getBoolean("device_profile_initialized", false) } returns false
        every { welcomePrefs.getBoolean("welcome_completed", false) } returns false

        coEvery { localDataSource.observeProfile() } returns flowOf(
            DeviceProfile(
                type = DeviceProfileType.OTHER,
                source = DeviceProfileSource.MIGRATION_EXISTING,
                confidence = DetectionConfidence.NONE,
                presetVersion = 0,
                appliedAtInstallTime = false,
                lastModified = System.currentTimeMillis()
            )
        )

        repository = RealDeviceProfileRepository(
            detector,
            localDataSource,
            context,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )

        assertTrue(repository.isMigrationExisting())
    }

    @Test
    fun `updatePresetApplied persists preset version on current profile`() = runTest {
        every { appPrefs.getBoolean("device_profile_initialized", false) } returns true
        every { welcomePrefs.getBoolean("welcome_completed", false) } returns false

        coEvery { localDataSource.observeProfile() } returns flowOf(
            DeviceProfile(
                type = DeviceProfileType.TV_MEDIA_BOX,
                source = DeviceProfileSource.MANUAL_SELECTION,
                confidence = DetectionConfidence.NONE,
                presetVersion = 0,
                appliedAtInstallTime = false,
                lastModified = 100L
            )
        )

        repository = RealDeviceProfileRepository(
            detector,
            localDataSource,
            context,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )

        repository.updatePresetApplied(3)

        coVerify {
            localDataSource.saveProfile(
                match {
                    it.type == DeviceProfileType.TV_MEDIA_BOX &&
                        it.presetVersion == 3 &&
                        it.appliedAtInstallTime
                }
            )
        }
    }
}
