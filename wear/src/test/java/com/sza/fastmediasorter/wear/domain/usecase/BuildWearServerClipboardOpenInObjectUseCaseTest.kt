package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WEAR_OPEN_IN_TEXT_LIMIT
import com.sza.fastmediasorter.wear.domain.model.WearOpenInObject
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardKind
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSaved
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** S4381: what the received value becomes when it is handed to another application. */
class BuildWearServerClipboardOpenInObjectUseCaseTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun value(kind: WearServerClipboardKind, mimeType: String?) = WearServerClipboardValue(
        clipId = "c",
        kind = kind,
        size = 1,
        sha256 = "h",
        storedAt = null,
        sourceDeviceId = null,
        sourceDeviceName = null,
        mimeType = mimeType,
        name = null,
    )

    private fun build(value: WearServerClipboardValue, saved: WearServerClipboardSaved) =
        runBlocking { BuildWearServerClipboardOpenInObjectUseCase()(value, saved) }

    @Test
    fun `a short text goes as its words`() {
        val file = folder.newFile("clip.txt").apply { writeText("see you at eight") }

        val result = build(
            value(WearServerClipboardKind.TEXT, null),
            WearServerClipboardSaved("clip.txt", file.absolutePath, null),
        )

        assertEquals(WearOpenInObject.Text("see you at eight"), result)
    }

    @Test
    fun `a text over the limit goes as the file it is`() {
        val file = folder.newFile("long.txt").apply { writeText("a".repeat(WEAR_OPEN_IN_TEXT_LIMIT + 1)) }

        val result = build(
            value(WearServerClipboardKind.TEXT, null),
            WearServerClipboardSaved("long.txt", file.absolutePath, null),
        )

        assertTrue(result is WearOpenInObject.File)
        assertEquals("text/plain", (result as WearOpenInObject.File).mimeType)
        assertEquals(file.absolutePath, result.path)
    }

    @Test
    fun `a text whose file is gone gives nothing to hand over`() {
        val result = build(
            value(WearServerClipboardKind.TEXT, null),
            WearServerClipboardSaved("gone.txt", folder.root.resolve("gone.txt").absolutePath, null),
        )

        assertNull(result)
    }

    @Test
    fun `a picture in the media collection goes by its uri and type`() {
        val result = build(
            value(WearServerClipboardKind.IMAGE, "image/png"),
            WearServerClipboardSaved("a.png", null, "content://media/external/images/media/7"),
        )

        assertEquals(
            WearOpenInObject.File("image/png", "a.png", contentUri = "content://media/external/images/media/7"),
            result,
        )
    }

    @Test
    fun `a file in the slot goes by its path and an unknown type is a plain stream`() {
        val result = build(
            value(WearServerClipboardKind.FILE, null),
            WearServerClipboardSaved("book.epub", "/data/slot/book.epub", null),
        )

        assertEquals(
            WearOpenInObject.File("application/octet-stream", "book.epub", path = "/data/slot/book.epub"),
            result,
        )
    }
}
