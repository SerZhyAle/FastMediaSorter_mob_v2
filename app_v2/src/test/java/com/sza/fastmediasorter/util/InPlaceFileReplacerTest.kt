package com.sza.fastmediasorter.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

/**
 * S3967: the replacer stands between every in-place edit and the user's only copy, so each case
 * judges the directory afterwards - the target's bytes and the absence of any temporary sibling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InPlaceFileReplacerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val original = "original bytes".toByteArray()

    @Test
    fun `a successful write replaces the target and leaves no temporary file`() {
        val target = existingFile("photo.jpg")

        InPlaceFileReplacer.replace(target, "test") { out ->
            out.write("new".toByteArray())
            true
        }

        assertEquals("new", target.readText())
        assertEquals(listOf("photo.jpg"), target.parentFile!!.list()!!.toList())
    }

    @Test
    fun `a write reporting failure keeps the original bytes`() {
        val target = existingFile("photo.jpg")

        assertThrows(IOException::class.java) {
            InPlaceFileReplacer.replace(target, "test") { out ->
                out.write("partial".toByteArray())
                false
            }
        }

        assertArrayEquals(original, target.readBytes())
        assertEquals(listOf("photo.jpg"), target.parentFile!!.list()!!.toList())
    }

    @Test
    fun `a write that throws keeps the original bytes`() {
        val target = existingFile("anim.gif")

        assertThrows(IllegalStateException::class.java) {
            InPlaceFileReplacer.replace(target, "test") { out ->
                out.write("partial".toByteArray())
                error("encoder crashed")
            }
        }

        assertArrayEquals(original, target.readBytes())
        assertEquals(listOf("anim.gif"), target.parentFile!!.list()!!.toList())
    }

    @Test
    fun `a missing target is created`() {
        val target = File(tempFolder.newFolder("downloads"), "anim_speed_2_0x.gif")

        InPlaceFileReplacer.replace(target, "test") { out ->
            out.write("gif".toByteArray())
            true
        }

        assertEquals("gif", target.readText())
        assertEquals(listOf("anim_speed_2_0x.gif"), target.parentFile!!.list()!!.toList())
    }

    @Test
    fun `a bitmap is encoded in the format the extension names`() {
        val target = existingFile("pixel.png")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { setPixel(0, 0, Color.GREEN) }

        InPlaceFileReplacer.replaceWithBitmap(target, bitmap, "test")
        bitmap.recycle()

        assertEquals(Color.GREEN, BitmapFactory.decodeFile(target.path).getPixel(0, 0))
        assertEquals(listOf("pixel.png"), target.parentFile!!.list()!!.toList())
    }

    private fun existingFile(name: String): File =
        File(tempFolder.newFolder("dir"), name).apply { writeBytes(original) }
}
