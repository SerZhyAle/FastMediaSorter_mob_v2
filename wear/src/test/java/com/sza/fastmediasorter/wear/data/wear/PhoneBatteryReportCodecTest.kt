package com.sza.fastmediasorter.wear.data.wear

import com.sza.fastmediasorter.wear.domain.model.PhoneBatteryReport
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneBatteryReportCodecTest {

    private val envelopeCodec = WearEventEnvelopeCodec()

    private fun packet(json: String, sentAt: Long = 42L): ByteArray = envelopeCodec.encode(
        WearEventEnvelope(
            eventType = WearDataLayerPaths.EVENT_PHONE_BATTERY,
            sentAt = sentAt,
            data = json.toByteArray(Charsets.UTF_8)
        )
    )

    @Test
    fun `a full payload decodes field by field`() {
        val report = PhoneBatteryReportCodec.decodeEnvelope(
            packet("""{"percent":62,"isCharging":true,"timestampMs":1234}""")
        )

        assertEquals(PhoneBatteryReport(percent = 62, isCharging = true, timestampMs = 1234L), report)
    }

    @Test
    fun `the boundaries of the percent range decode`() {
        assertNull(PhoneBatteryReportCodec.decode("""{"percent":-1,"isCharging":false,"timestampMs":1}"""))
        assertNull(PhoneBatteryReportCodec.decode("""{"percent":101,"isCharging":false,"timestampMs":1}"""))
        assertEquals(0, PhoneBatteryReportCodec.decode("""{"percent":0,"isCharging":false,"timestampMs":1}""")?.percent)
        assertEquals(
            100,
            PhoneBatteryReportCodec.decode("""{"percent":100,"isCharging":false,"timestampMs":1}""")?.percent
        )
    }

    @Test
    fun `a missing or mistyped field refuses the whole packet`() {
        assertNull(PhoneBatteryReportCodec.decode("""{"isCharging":true,"timestampMs":1}"""))
        assertNull(PhoneBatteryReportCodec.decode("""{"percent":50,"timestampMs":1}"""))
        assertNull(PhoneBatteryReportCodec.decode("""{"percent":50,"isCharging":true}"""))
        assertNull(PhoneBatteryReportCodec.decode("""{"percent":"50","isCharging":true,"timestampMs":1}"""))
        assertNull(PhoneBatteryReportCodec.decode("""{"percent":50,"isCharging":"yes","timestampMs":1}"""))
        assertNull(PhoneBatteryReportCodec.decode("""{"percent":null,"isCharging":true,"timestampMs":1}"""))
    }

    @Test
    fun `malformed json returns null`() {
        assertNull(PhoneBatteryReportCodec.decode("{not json"))
        assertNull(PhoneBatteryReportCodec.decode("[1,2]"))
        assertNull(PhoneBatteryReportCodec.decodeEnvelope(packet("{not json")))
        assertNull(PhoneBatteryReportCodec.decodeEnvelope("garbage".toByteArray()))
        assertNull(PhoneBatteryReportCodec.decodeEnvelope("""{"eventType":"PHONE_BATTERY"}""".toByteArray()))
        assertNull(PhoneBatteryReportCodec.decodeEnvelope(byteArrayOf(0x7B, 0x22))) // truncated envelope prefix
    }

    @Test
    fun `the stored form reads back unchanged`() {
        val original = PhoneBatteryReport(percent = 7, isCharging = true, timestampMs = 99L)

        val decoded = PhoneBatteryReportCodec.decode(PhoneBatteryReportCodec.encode(original))

        assertEquals(original, decoded)
    }
}
