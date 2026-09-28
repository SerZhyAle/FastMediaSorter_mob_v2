package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.widget.EditText
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextEditorAutoSaveManagerTest {
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("autosave_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `stop during queued or active writes leaves no draft`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val editor = EditText(context)
        val source = "/sdcard/test.txt"
        val draft = File(tempDir, "draft_${source.hashCode().toUInt().toString(16)}.txt")
        val content = "x".repeat(1024 * 1024)

        runTest {
            val manager = TextEditorAutoSaveManager(tempDir, this)
            repeat(10) {
                manager.startAutoSave(editor, source)
                repeat(4) { manager.forceSave(content) }
                manager.stopAutoSave(deleteDraft = true)
            }
        }

        assertFalse(draft.exists())
    }

    @Test
    fun `deleteDraft removes only the requested source`() {
        val manager = TextEditorAutoSaveManager(tempDir, TestScope())
        val first = "/sdcard/first.txt"
        val second = "/sdcard/second.txt"
        val firstDraft = File(tempDir, "draft_${first.hashCode().toUInt().toString(16)}.txt")
        val secondDraft = File(tempDir, "draft_${second.hashCode().toUInt().toString(16)}.txt")
        firstDraft.writeText("first")
        secondDraft.writeText("second")

        manager.deleteDraft(first)

        assertFalse(manager.hasDraft(first))
        assertTrue(manager.hasDraft(second))
    }
}
