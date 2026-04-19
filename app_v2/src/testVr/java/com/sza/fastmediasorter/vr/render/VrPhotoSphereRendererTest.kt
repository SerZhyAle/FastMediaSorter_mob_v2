package com.sza.fastmediasorter.vr.render

import org.junit.Assert.assertEquals
import org.junit.Test

class VrPhotoSphereRendererTest {

    @Test
    fun `calculateUploadDimensions keeps source size when already within texture limit`() {
        val dimensions = VrPhotoSphereRenderer.calculateUploadDimensions(
            sourceWidth = 3840,
            sourceHeight = 3840,
            maxTextureSize = 4096,
        )

        assertEquals(VrPhotoSphereRenderer.UploadDimensions(3840, 3840), dimensions)
    }

    @Test
    fun `calculateUploadDimensions preserves aspect ratio for 8k panorama`() {
        val dimensions = VrPhotoSphereRenderer.calculateUploadDimensions(
            sourceWidth = 8000,
            sourceHeight = 4000,
            maxTextureSize = 4096,
        )

        assertEquals(VrPhotoSphereRenderer.UploadDimensions(4096, 2048), dimensions)
    }
}