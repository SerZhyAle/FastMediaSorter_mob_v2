package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TextFilePagerTest {
    @Test
    fun `concurrent reads keep page boundaries and line index stable`() {
        val file = Files.createTempFile("pager", ".txt").toFile()
        val executor = Executors.newFixedThreadPool(8)
        try {
            file.writeText((1..200).joinToString("\n") { "line $it has predictable content" } + "\n")
            val expected = TextFilePager(file, chunkSize = 128)
            val pager = TextFilePager(file, chunkSize = 128)
            expected.open()
            pager.open()
            try {
                val pages = (0..30).associateWith { expected.readPage(it) }
                val tasks = (0..30).flatMap { index ->
                    List(4) { executor.submit<String> { pager.readPage(index) } to index }
                }
                tasks.forEach { (future, index) ->
                    assertEquals(pages[index], future.get(10, TimeUnit.SECONDS))
                }
                for (index in 0..30) {
                    assertEquals(expected.getStartLineNumber(index), pager.getStartLineNumber(index))
                }
                assertTrue(pager.currentPage in 0..30)
            } finally {
                pager.close()
                expected.close()
            }
        } finally {
            executor.shutdownNow()
            file.delete()
        }
    }
}
