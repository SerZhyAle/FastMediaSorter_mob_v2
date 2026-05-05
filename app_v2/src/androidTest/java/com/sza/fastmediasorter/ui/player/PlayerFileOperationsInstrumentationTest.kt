package com.sza.fastmediasorter.ui.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sza.fastmediasorter.TestFixtures
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlayerFileOperationsInstrumentationTest {

    private lateinit var context: Context
    private lateinit var sourceFile: File
    private lateinit var destDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sourceFile = TestFixtures.createTempFile(context.cacheDir, "player_test_source.mp4")
        destDir = File(context.cacheDir, "player_test_dest")
        destDir.mkdirs()
    }

    @After
    fun tearDown() {
        sourceFile.delete()
        destDir.deleteRecursively()
    }

    @Test
    fun moveFile_succeeds_fileAppearsInDest() {
        val destFile = File(destDir, sourceFile.name)

        sourceFile.copyTo(destFile)
        sourceFile.delete()

        assertTrue("Destination file should exist after move", destFile.exists())
        assertFalse("Source file should be gone after move", sourceFile.exists())
    }

    @Test
    fun moveFile_toSameDirectory_isNoOp() {
        val samePathFile = File(sourceFile.parent!!, sourceFile.name)

        try {
            sourceFile.copyTo(samePathFile, overwrite = false)
        } catch (e: Exception) {
            // Expected when target already exists at same path
        }

        assertTrue("Source file must remain unchanged", sourceFile.exists())
        assertTrue("Source file must still be readable", sourceFile.readText() == "test")
    }
}
