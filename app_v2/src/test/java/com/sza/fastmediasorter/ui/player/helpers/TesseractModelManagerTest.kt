package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseractModelManagerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val manager = TesseractModelManager(context)
    private val model = manager.getTessdataDir().resolve("ukr.traineddata")
    private val prefs = context.getSharedPreferences("tesseract_models_prefs", Context.MODE_PRIVATE)

    @After
    fun tearDown() {
        model.delete()
        prefs.edit().clear().commit()
    }

    @Test
    fun `main thread uses retained validation and rejects changed stamp without hashing`() {
        model.parentFile?.mkdirs()
        RandomAccessFile(model, "rw").use { it.setLength(10_000_000L) }
        val stamp = "${model.length()}_${model.lastModified()}"
        prefs.edit().putString("validated_model_ukr", stamp).commit()

        assertTrue(manager.isModelInstalled("ukr"))

        model.setLastModified(model.lastModified() + 1_000L)
        assertFalse(manager.isModelInstalled("ukr"))
    }
}
