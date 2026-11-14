package com.sza.fastmediasorter.ui.companionimport.qr

import org.junit.Assert.assertEquals
import org.junit.Test

class QrCodeAnalyzerGuardTest {

    private val delivered = mutableListOf<String>()
    private val analyzer = QrCodeAnalyzer { delivered += it }

    @Test
    fun singleDecodeDoesNotFire() {
        analyzer.accept("FMSBCAST1:abc")

        assertEquals(emptyList<String>(), delivered)
    }

    @Test
    fun twoEqualConsecutiveDecodesFireOnce() {
        repeat(REPEATS) { analyzer.accept("FMSBCAST1:abc") }

        assertEquals(listOf("FMSBCAST1:abc"), delivered)
    }

    @Test
    fun frameWithoutDecodeRestartsTheConfirmation() {
        analyzer.accept("FMSBCAST1:abc")
        analyzer.accept(null)
        analyzer.accept("FMSBCAST1:abc")

        assertEquals(emptyList<String>(), delivered)
    }

    @Test
    fun differentTextRestartsTheConfirmation() {
        analyzer.accept("FMSBCAST1:abc")
        analyzer.accept("FMSBCAST1:xyz")

        assertEquals(emptyList<String>(), delivered)
    }

    @Test
    fun textAboveTheLengthBoundNeverFires() {
        val tooLong = "x".repeat(QrCodeAnalyzer.MAX_PAYLOAD_LENGTH + 1)

        repeat(REPEATS) { analyzer.accept(tooLong) }

        assertEquals(emptyList<String>(), delivered)
    }

    @Test
    fun textAtTheLengthBoundStillFires() {
        val atBound = "x".repeat(QrCodeAnalyzer.MAX_PAYLOAD_LENGTH)

        repeat(REPEATS) { analyzer.accept(atBound) }

        assertEquals(listOf(atBound), delivered)
    }

    private companion object {
        const val REPEATS = 3
    }
}
