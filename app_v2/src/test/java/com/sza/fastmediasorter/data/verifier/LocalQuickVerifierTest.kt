package com.sza.fastmediasorter.data.verifier

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [LocalQuickVerifier]: only paths whose [java.io.File] does not exist are reported
 * missing; existing files are retained. Pure JVM with TemporaryFolder.
 */
class LocalQuickVerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val verifier = LocalQuickVerifier(context)

    @Test
    fun `reports only non-existent paths as missing`() = runBlocking {
        val present = tempFolder.newFile("there.txt")
        val absent = tempFolder.root.resolve("gone.txt").absolutePath

        val missing = verifier.missingFiles(
            resourceId = 1L,
            paths = listOf(present.absolutePath, absent)
        )

        assertEquals(listOf(absent), missing)
    }

    @Test
    fun `empty when all paths exist`() = runBlocking {
        val a = tempFolder.newFile("a.txt").absolutePath
        val b = tempFolder.newFile("b.txt").absolutePath
        assertTrue(verifier.missingFiles(1L, listOf(a, b)).isEmpty())
    }

    @Test
    fun `empty input yields empty result`() = runBlocking {
        assertTrue(verifier.missingFiles(1L, emptyList()).isEmpty())
    }
}

