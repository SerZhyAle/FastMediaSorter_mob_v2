package com.sza.fastmediasorter.ui.cameraocr.helpers

import android.os.Bundle
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.testing.MainDispatcherRule
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CameraOcrFlowManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val storageManager: CameraOcrStorageManager = mockk(relaxed = true)
    private val translationManager: TranslationManager = mockk(relaxed = true)
    private val callback: CameraOcrFlowManager.Callback = mockk(relaxed = true)

    private val testScope = TestScope()
    private lateinit var flowManager: CameraOcrFlowManager

    @Before
    fun setUp() {
        flowManager = CameraOcrFlowManager(
            scope = testScope,
            settingsRepository = settingsRepository,
            storageManager = storageManager,
            translationManager = translationManager,
            callback = callback
        )
    }

    @Test
    fun `saveState and restoreState preserve text results and trigger showResults`() {
        val bundle = Bundle().apply {
            putString("camera_ocr_recognized_text", "Recognized Original Text")
            putString("camera_ocr_translated_text", "Translated Result")
            putBoolean("camera_ocr_only_active", false)
            putLong("camera_ocr_capture_millis", 123456789L)
        }

        flowManager.restoreState(bundle)

        // Verify that restored results are displayed on UI callback
        verify(exactly = 1) {
            callback.showResults("Recognized Original Text", "Translated Result", false)
        }

        // Now save state to new bundle and verify fields match
        val outBundle = Bundle()
        flowManager.saveState(outBundle)

        assertEquals("Recognized Original Text", outBundle.getString("camera_ocr_recognized_text"))
        assertEquals("Translated Result", outBundle.getString("camera_ocr_translated_text"))
        assertEquals(false, outBundle.getBoolean("camera_ocr_only_active"))
        assertEquals(123456789L, outBundle.getLong("camera_ocr_capture_millis"))
    }
}
