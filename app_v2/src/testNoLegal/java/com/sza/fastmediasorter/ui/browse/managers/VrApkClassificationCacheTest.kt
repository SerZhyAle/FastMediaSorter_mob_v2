package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VrApkClassificationCacheTest {

    private val archiveResolver: VrApkArchiveResolver = mockk()
    private val classifier: VrApkClassifier = mockk()
    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun `peek returns null for blank media path`() {
        val cache = createCache()

        assertNull(cache.peek(mediaFile(path = "")))
    }

    @Test
    fun `requestClassification returns NOT_VR immediately for blank path`() = runTest {
        val cache = createCache()
        var result: VrApkClassification? = null

        cache.requestClassification(mediaFile(path = "")) { result = it }

        assertEquals(VrApkClassification.NOT_VR, result)
        coVerify(exactly = 0) { archiveResolver.resolve(any()) }
    }

    @Test
    fun `requestClassification stores cacheable classifier result`() = runTest {
        val archive = File("/tmp/app-vr.apk")
        val mediaFile = mediaFile(
            path = "cloud://drive/app-vr.apk",
            size = 42L,
            lastModified = 100L,
        )
        val classification = VrApkClassification(
            isVrCapable = true,
            signals = setOf(VrApkSignal.FEATURE_VR_MODE),
        )
        coEvery { archiveResolver.resolve(mediaFile) } returns archive
        coEvery { classifier.classify(archive) } returns classification
        val cache = createCache()
        var result: VrApkClassification? = null

        cache.requestClassification(mediaFile) { result = it }

        assertEquals(classification, result)
        assertEquals(classification, cache.peek(mediaFile))
    }

    private fun createCache(): VrApkClassificationCache = VrApkClassificationCache(
        archiveResolver = archiveResolver,
        classifier = classifier,
        applicationScope = kotlinx.coroutines.CoroutineScope(dispatcher),
        mainDispatcher = dispatcher,
    )

    private fun mediaFile(
        path: String,
        size: Long = 0L,
        lastModified: Long = 0L,
    ): MediaFile = MediaFile(
        name = "app.apk",
        path = path,
        type = MediaType.BINARY_EXECUTABLE,
        size = size,
        createdDate = 0L,
        lastModified = lastModified,
    )
}