package com.sza.fastmediasorter.wear.data.wear

import com.sza.fastmediasorter.wear.domain.model.WearAnimationPalette
import com.sza.fastmediasorter.wear.domain.model.WearClockStyle
import com.sza.fastmediasorter.wear.domain.model.WearClockTypeface
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WearClockStyleCodecTest {

    private val envelopeCodec = WearEventEnvelopeCodec()

    private fun packet(json: String, sentAt: Long = 42L): ByteArray = envelopeCodec.encode(
        WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_CLOCK_STYLE,
            sentAt = sentAt,
            data = json.toByteArray(Charsets.UTF_8)
        )
    )

    @Test
    fun `a full payload decodes field by field`() {
        val json = """
            {"secondsVisible":true,"dialColor":-16711936,"dialTypeface":"serif",
             "animationPalette":"PINK","wallpaperIntensity":0.5,"wallpaperAnimationSpeed":1.5,
             "wallpaperParticleDensity":0.25,"sentAt":1234}
        """.trimIndent()

        val style = WearClockStyleCodec.decodeEnvelope(packet(json))

        assertEquals(
            WearClockStyle(
                secondsVisible = true,
                dialColor = 0xFF00FF00.toInt(),
                typeface = WearClockTypeface.SERIF,
                palette = WearAnimationPalette.PINK,
                wallpaperIntensity = 0.5f,
                wallpaperAnimationSpeed = 1.5f,
                wallpaperParticleDensity = 0.25f,
                sentAt = 1234L
            ),
            style
        )
    }

    @Test
    fun `a null colour means the theme colour and a missing sentAt takes the envelope's`() {
        val json = """{"secondsVisible":false,"dialColor":null,"dialTypeface":"casual"}"""

        val style = requireNotNull(WearClockStyleCodec.decodeEnvelope(packet(json, sentAt = 99L)))

        assertNull(style.dialColor)
        assertEquals(WearClockTypeface.CASUAL, style.typeface)
        assertEquals(99L, style.sentAt)
    }

    @Test
    fun `an unsigned ARGB value reads as the same colour`() {
        val style = requireNotNull(WearClockStyleCodec.decode("""{"dialColor":4294901760}"""))

        assertEquals(0xFFFF0000.toInt(), style.dialColor)
    }

    @Test
    fun `unknown names fall back to the defaults`() {
        val json = """{"dialTypeface":"gothic","animationPalette":"RAINBOW"}"""

        val style = requireNotNull(WearClockStyleCodec.decode(json))

        assertEquals(WearClockTypeface.DEFAULT, style.typeface)
        assertEquals(WearAnimationPalette.DYNAMIC, style.palette)
    }

    @Test
    fun `out-of-range tuning is clamped to the contract bounds`() {
        val json = """{"wallpaperIntensity":3,"wallpaperAnimationSpeed":0.01,"wallpaperParticleDensity":-1}"""

        val style = requireNotNull(WearClockStyleCodec.decode(json))

        assertEquals(WearClockStyle.INTENSITY_MAX, style.wallpaperIntensity)
        assertEquals(WearClockStyle.SPEED_MIN, style.wallpaperAnimationSpeed)
        assertEquals(WearClockStyle.DENSITY_MIN, style.wallpaperParticleDensity)
    }

    @Test
    fun `an empty object is today's look`() {
        assertEquals(WearClockStyle.DEFAULT, WearClockStyleCodec.decode("{}"))
    }

    @Test
    fun `malformed json returns null`() {
        assertNull(WearClockStyleCodec.decode("{not json"))
        assertNull(WearClockStyleCodec.decode("[1,2]"))
        assertNull(WearClockStyleCodec.decodeEnvelope(packet("{not json")))
        assertNull(WearClockStyleCodec.decodeEnvelope("garbage".toByteArray()))
        assertNull(WearClockStyleCodec.decodeEnvelope("""{"eventType":"CLOCK_STYLE"}""".toByteArray()))
    }

    @Test
    fun `the stored form reads back unchanged`() {
        val original = WearClockStyle.DEFAULT.copy(
            secondsVisible = true,
            dialColor = 0xFF123456.toInt(),
            typeface = WearClockTypeface.MONOSPACE,
            palette = WearAnimationPalette.BLUE,
            wallpaperAnimationSpeed = 0.75f,
            sentAt = 7L
        )

        val decoded = WearClockStyleCodec.decode(WearClockStyleCodec.encode(original))

        assertNotNull(decoded)
        assertEquals(original, decoded)
    }
}
