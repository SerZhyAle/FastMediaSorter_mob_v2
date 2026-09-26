package com.sza.fastmediasorter.wear.data.wear

import com.sza.fastmediasorter.wear.domain.model.WearComplicationKind
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.wear.domain.model.WearFaceSlots
import com.sza.fastmediasorter.wear.domain.model.WearFaceSystemItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearFaceSlotsCodecTest {

    private val envelopeCodec = WearEventEnvelopeCodec()

    private fun packet(json: String, sentAt: Long = 42L): ByteArray = envelopeCodec.encode(
        WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_FACE_SLOTS,
            sentAt = sentAt,
            data = json.toByteArray(Charsets.UTF_8)
        )
    )

    @Test
    fun `a full payload decodes slot by slot`() {
        val json = """
            {"slot1":"dest:CALCULATOR","slot2":"sys:BATTERY","slot3":"none",
             "slot4":"data:NOW_PLAYING","sentAt":1234}
        """.trimIndent()

        val slots = WearFaceSlotsCodec.decodeEnvelope(packet(json))

        assertEquals(
            WearFaceSlots(
                listOf(
                    WearFaceSlotOption.Destination(WearDestinationId.CALCULATOR),
                    WearFaceSlotOption.System(WearFaceSystemItem.BATTERY),
                    WearFaceSlotOption.None,
                    WearFaceSlotOption.AppData(WearComplicationKind.NOW_PLAYING)
                ),
                sentAt = 1234L
            ),
            slots
        )
    }

    @Test
    fun `the stored form round-trips`() {
        val slots = WearFaceSlots(
            listOf(
                WearFaceSlotOption.System(WearFaceSystemItem.TIMER),
                WearFaceSlotOption.Destination(WearDestinationId.SOS),
                WearFaceSlotOption.AppData(WearComplicationKind.LAST_RESOURCE),
                WearFaceSlotOption.System(WearFaceSystemItem.NEXT_ALARM)
            ),
            sentAt = 77L
        )

        assertEquals(slots, WearFaceSlotsCodec.decode(WearFaceSlotsCodec.encode(slots)))
    }

    @Test
    fun `an unknown id falls back to that slot's own default`() {
        val json = """
            {"slot1":"dest:NOT_A_SCREEN","slot2":"sys:WEATHER","slot3":"data:","slot4":"bogus"}
        """.trimIndent()

        val slots = requireNotNull(WearFaceSlotsCodec.decodeEnvelope(packet(json)))

        assertEquals(WearFaceSlots.DEFAULT_OPTIONS, slots.options)
    }

    @Test
    fun `a missing slot takes its default and a missing sentAt takes the envelope's`() {
        val json = """{"slot2":"dest:STOPWATCH","slot4":42}"""

        val slots = requireNotNull(WearFaceSlotsCodec.decodeEnvelope(packet(json, sentAt = 99L)))

        assertEquals(WearFaceSlots.DEFAULT_OPTIONS[0], slots.optionFor(1))
        assertEquals(WearFaceSlotOption.Destination(WearDestinationId.STOPWATCH), slots.optionFor(2))
        assertEquals(WearFaceSlots.DEFAULT_OPTIONS[2], slots.optionFor(3))
        assertEquals(WearFaceSlotOption.None, slots.optionFor(4))
        assertEquals(99L, slots.sentAt)
    }

    @Test
    fun `a packet that is not a JSON object is refused`() {
        assertNull(WearFaceSlotsCodec.decodeEnvelope("not json".toByteArray(Charsets.UTF_8)))
        assertNull(WearFaceSlotsCodec.decode("[1,2,3]"))
    }
}
