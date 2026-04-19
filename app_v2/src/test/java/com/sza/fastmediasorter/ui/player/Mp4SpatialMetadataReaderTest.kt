package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.domain.model.StereoMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Mp4SpatialMetadataReaderTest {

    private val reader = Mp4SpatialMetadataReader()

    @Test
    fun `detectStereoMode returns EQUIRECT_360_MONO for st3d mono and equi projection`() {
        val file = createMp4WithSpatialMetadata(st3dLayout = 0, projectionBox = "equi")

        assertEquals(StereoMode.EQUIRECT_360_MONO, reader.detectStereoMode(file.absolutePath))
    }

    @Test
    fun `detectStereoMode returns EQUIRECT_360_SBS for st3d left-right and equi projection`() {
        val file = createMp4WithSpatialMetadata(st3dLayout = 2, projectionBox = "equi")

        assertEquals(StereoMode.EQUIRECT_360_SBS, reader.detectStereoMode(file.absolutePath))
    }

    @Test
    fun `detectStereoMode returns EQUIRECT_360_OU for st3d top-bottom and equi projection`() {
        val file = createMp4WithSpatialMetadata(st3dLayout = 1, projectionBox = "equi")

        assertEquals(StereoMode.EQUIRECT_360_OU, reader.detectStereoMode(file.absolutePath))
    }

    @Test
    fun `detectStereoMode returns UNKNOWN for cubemap projection`() {
        val file = createMp4WithSpatialMetadata(st3dLayout = 2, projectionBox = "cbmp")

        assertEquals(StereoMode.UNKNOWN, reader.detectStereoMode(file.absolutePath))
    }

    @Test
    fun `detectStereoMode returns UNKNOWN when no spatial metadata exists`() {
        val file = File.createTempFile("mp4-spatial-none-", ".mp4")
        file.deleteOnExit()
        file.writeBytes(box("ftyp", byteArrayOf(0, 0, 0, 0)) + box("mdat", byteArrayOf(1, 2, 3, 4)))

        assertEquals(StereoMode.UNKNOWN, reader.detectStereoMode(file.absolutePath))
    }

    private fun createMp4WithSpatialMetadata(st3dLayout: Int, projectionBox: String): File {
        val file = File.createTempFile("mp4-spatial-", ".mp4")
        file.deleteOnExit()
        file.writeBytes(spatialMp4Bytes(st3dLayout, projectionBox))
        return file
    }

    private fun spatialMp4Bytes(st3dLayout: Int, projectionBox: String): ByteArray {
        return box("ftyp", byteArrayOf(0, 0, 0, 0)) +
            box(
                "moov",
                box(
                    "trak",
                    box(
                        "mdia",
                        box(
                            "minf",
                            box(
                                "stbl",
                                stsdBox(
                                    visualSampleEntry(
                                        "hvc1",
                                        box("st3d", fullBoxPayload(byteArrayOf(st3dLayout.toByte()))) +
                                            box(
                                                "sv3d",
                                                box(
                                                    "proj",
                                                    box(
                                                        "mshp",
                                                        box(projectionBox, byteArrayOf(0, 0, 0, 0))
                                                    )
                                                )
                                            )
                                    )
                                )
                            )
                        )
                    )
                )
            )
    }

    private fun fullBoxPayload(payload: ByteArray): ByteArray = byteArrayOf(0, 0, 0, 0) + payload

    private fun stsdBox(entry: ByteArray): ByteArray = box(
        "stsd",
        byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1) + entry
    )

    private fun visualSampleEntry(type: String, childBoxes: ByteArray): ByteArray = box(
        type,
        ByteArray(78) + childBoxes
    )

    private fun box(type: String, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(8 + payload.size)
        buffer.put(type.toByteArray(Charsets.US_ASCII))
        buffer.put(payload)
        return buffer.array()
    }
}