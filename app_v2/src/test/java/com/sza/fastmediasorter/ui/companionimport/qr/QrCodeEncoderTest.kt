package com.sza.fastmediasorter.ui.companionimport.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1039: [QrCodeEncoder.encodeToMatrix] is pure JVM (no Android Bitmap), so it is unit-testable here;
 * the [QrCodeEncoder.encode] bitmap path needs instrumentation and is out of scope for this suite.
 */
class QrCodeEncoderTest {

    @Test
    fun `encodeToMatrix produces a square non-empty matrix`() {
        val matrix = QrCodeEncoder.encodeToMatrix("FMSCFG1:test-payload", QR_SIZE_PX)

        assertTrue(matrix.width > 0)
        assertTrue(matrix.height > 0)
        assertEquals(matrix.width, matrix.height)

        var hasSetModule = false
        loop@ for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    hasSetModule = true
                    break@loop
                }
            }
        }
        assertTrue(hasSetModule)
    }

    private companion object {
        const val QR_SIZE_PX = 256
    }
}
