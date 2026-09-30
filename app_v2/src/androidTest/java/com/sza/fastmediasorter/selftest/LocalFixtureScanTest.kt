package com.sza.fastmediasorter.selftest

import androidx.test.platform.app.InstrumentationRegistry
import com.sza.fastmediasorter.TestFixtures
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.data.repository.MediaStoreRepositoryImpl
import com.sza.fastmediasorter.domain.model.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * S3741: the app's local scanner lists the fixture media `selftest-provision.ps1` pushed to the device.
 *
 * This is the failure that stays silent in the field: without the media-read grant MediaStore returns
 * no rows and throws nothing, so a local folder simply opens empty. Here it fails the run instead.
 */
@RunWith(JUnit4::class)
class LocalFixtureScanTest {

    @get:Rule
    val baseline = SelfTestBaselineRule()

    @Test
    fun scannerListsTheProvisionedFixtures() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scanner = LocalMediaScanner(context, MediaStoreRepositoryImpl(context))
        val names = scanner.scanFolder(
            path = TestFixtures.TEST_LOCAL_FOLDER,
            supportedTypes = MediaType.entries.toSet(),
            sizeFilter = null,
            credentialsId = null,
            scanSubdirectories = false,
            showHiddenFiles = false,
            onProgress = null,
        ).map { it.name }.toSet()

        TestFixtures.DEVICE_FIXTURE_FILES.forEach { expected ->
            assertTrue(
                "$expected missing from ${TestFixtures.TEST_LOCAL_FOLDER} scan $names - run selftest-provision.ps1",
                expected in names,
            )
        }
    }
}
