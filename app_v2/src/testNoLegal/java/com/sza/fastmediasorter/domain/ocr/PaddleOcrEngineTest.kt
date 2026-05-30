package com.sza.fastmediasorter.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import com.sza.fastmediasorter.ui.player.helpers.PaddleOcrModelManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S0300 Phase 07: JVM-reachable branches of [PaddleOcrEngine].
 *
 * The inference core (preprocess/runPredictor/postprocess) is bound to native Paddle-Lite
 * (PaddlePredictor / MobileConfig) and to Android [Bitmap] pixel access, so it is not
 * JVM-testable. The reachable surface is the input-validation guard (recycled / zero-sized
 * bitmap) and the model-initialisation-failure path (model not installed + download fails),
 * both of which short-circuit before any native call. [Bitmap] and the model manager are
 * mocked; no Paddle native library is loaded. Robolectric is used so [Bitmap] resolves to a
 * real (shadowed) class - MockK cannot safely instrument the stubbed android.jar Bitmap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PaddleOcrEngineTest {

    private lateinit var context: Context
    private lateinit var modelManager: PaddleOcrModelManager
    private lateinit var engine: PaddleOcrEngine

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        modelManager = mockk()
        engine = PaddleOcrEngine(context, modelManager)
    }

    private fun bitmap(recycled: Boolean, width: Int, height: Int): Bitmap = mockk {
        every { isRecycled } returns recycled
        every { this@mockk.width } returns width
        every { this@mockk.height } returns height
    }

    @Test
    fun recognizeTextBlocks_recycledBitmap_returnsNull() = runTest {
        val result = engine.recognizeTextBlocks(bitmap(recycled = true, width = 10, height = 10), "ru")
        assertNull(result)
    }

    @Test
    fun recognizeTextBlocks_zeroWidth_returnsNull() = runTest {
        val result = engine.recognizeTextBlocks(bitmap(recycled = false, width = 0, height = 10), "ru")
        assertNull(result)
    }

    @Test
    fun recognizeTextBlocks_zeroHeight_returnsNull() = runTest {
        val result = engine.recognizeTextBlocks(bitmap(recycled = false, width = 10, height = 0), "ru")
        assertNull(result)
    }

    @Test
    fun recognizeTextBlocks_modelNotInstalledAndDownloadFails_returnsNull() = runTest {
        every { modelManager.isModelInstalled(any()) } returns false
        coEvery { modelManager.downloadModel(any()) } returns false

        val result = engine.recognizeTextBlocks(bitmap(recycled = false, width = 32, height = 32), "ru")

        assertNull(result)
    }

    @Test
    fun recognizeTextBlocks_eastSlavicVariantSelected_modelDownloadFailsStillNull() = runTest {
        // "uk" resolves to EAST_SLAVIC; download failure short-circuits before native work.
        every { modelManager.isModelInstalled(PaddleOcrModelManager.ModelVariant.EAST_SLAVIC) } returns false
        coEvery { modelManager.downloadModel(PaddleOcrModelManager.ModelVariant.EAST_SLAVIC) } returns false

        val result = engine.recognizeTextBlocks(bitmap(recycled = false, width = 32, height = 32), "uk")

        assertNull(result)
    }

    @Test
    fun recognizeText_invalidBitmap_returnsNull() = runTest {
        // recognizeText delegates to recognizeTextBlocks; invalid bitmap yields null up the chain.
        val result = engine.recognizeText(bitmap(recycled = true, width = 10, height = 10), "ru")
        assertNull(result)
    }

    @Test
    fun release_doesNotThrow() {
        engine.release()
    }
}
