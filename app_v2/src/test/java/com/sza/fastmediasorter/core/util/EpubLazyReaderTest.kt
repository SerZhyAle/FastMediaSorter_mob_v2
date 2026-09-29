package com.sza.fastmediasorter.core.util

import io.documentnode.epub4j.epub.EpubReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpubLazyReaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val coverBytes = ByteArray(COVER_SIZE) { (it % BYTE_RANGE).toByte() }

    // Hand-built rather than EpubWriter: the writer needs kxml, which the unit-test classpath lacks.
    private fun writeBook(): File {
        val file = folder.newFile("book.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("mimetype", "application/epub+zip".toByteArray())
            put("META-INF/container.xml", CONTAINER_XML.toByteArray())
            put("OEBPS/content.opf", CONTENT_OPF.toByteArray())
            put("OEBPS/cover.png", coverBytes)
            put("OEBPS/ch1.xhtml", "<html><body>one</body></html>".toByteArray())
        }
        return file
    }

    @Test
    fun `lazy read returns the same cover as the eager read`() {
        val file = writeBook()
        val eager = file.inputStream().use { EpubReader().readEpub(it) }.coverImage?.data
        val lazy = EpubLazyReader.withBook(file) { it.coverImage?.data }

        assertNotNull(lazy)
        assertArrayEquals(eager, lazy)
        assertArrayEquals(coverBytes, lazy)
    }

    @Test
    fun `lazy read yields the metadata the info extractor needs`() {
        val info = EpubLazyReader.withBook(writeBook(), ::epubInfoOf)

        assertEquals("Lazy Title", info.docTitle)
        assertEquals("Ann Writer", info.docAuthor)
        assertEquals(1, info.chapterCount)
    }

    private companion object {
        const val COVER_SIZE = 4096
        const val BYTE_RANGE = 251
        const val CONTAINER_XML = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
        const val CONTENT_OPF = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="id">lazy-1</dc:identifier>
    <dc:title>Lazy Title</dc:title>
    <dc:creator>Ann Writer</dc:creator>
    <meta name="cover" content="cover"/>
  </metadata>
  <manifest>
    <item id="cover" href="cover.png" media-type="image/png"/>
    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine><itemref idref="ch1"/></spine>
</package>"""
    }
}
