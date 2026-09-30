package com.sza.fastmediasorter.ui.main.helpers

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MainVoiceCaptureSaveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `cancelling the launching scope mid-write still finishes the write before deleting the temp`() = runTest {
        val tempFile = tmp.newFile("audio_1.m4a")
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        var tempExistedWhenWriteFinished = false
        var writeCompleted = false

        val job = launch {
            writeThenDeleteTemp(tempFile) {
                writeStarted.complete(Unit)
                releaseWrite.await()
                tempExistedWhenWriteFinished = tempFile.exists()
                writeCompleted = true
                "audio_1.m4a"
            }
        }
        writeStarted.await()
        job.cancel()
        releaseWrite.complete(Unit)
        job.join()

        assertTrue(writeCompleted)
        assertTrue(tempExistedWhenWriteFinished)
        assertFalse(tempFile.exists())
    }

    @Test
    fun `a throwing write deletes the temp and reports no saved name`() = runTest {
        val tempFile = tmp.newFile("audio_2.m4a")

        val saved = writeThenDeleteTemp(tempFile) { error("disk full") }

        assertNull(saved)
        assertFalse(tempFile.exists())
    }

    @Test
    fun `a successful write returns the saved name and deletes the temp`() = runTest {
        val tempFile = tmp.newFile("audio_3.m4a")

        val saved = writeThenDeleteTemp(tempFile) { "audio_3 (2).m4a" }

        assertEquals("audio_3 (2).m4a", saved)
        assertFalse(tempFile.exists())
    }
}
