package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceExchangeRefusalTest {

    @Test
    fun `every reason a screen names maps to its own refusal`() {
        assertEquals(DeviceExchangeRefusal.BAD_CREDENTIALS, DeviceExchangeRefusal.of("bad-credentials"))
        assertEquals(DeviceExchangeRefusal.DEVICE_REVOKED, DeviceExchangeRefusal.of("device-revoked"))
        assertEquals(DeviceExchangeRefusal.DEVICE_REVOKED, DeviceExchangeRefusal.of("not-enrolled"))
        assertEquals(DeviceExchangeRefusal.VERSION_UNSUPPORTED, DeviceExchangeRefusal.of("version-unsupported"))
        assertEquals(DeviceExchangeRefusal.CAPACITY, DeviceExchangeRefusal.of("capacity"))
        assertEquals(DeviceExchangeRefusal.RATE_LIMITED, DeviceExchangeRefusal.of("rate-limited"))
        assertEquals(DeviceExchangeRefusal.UNSUPPORTED, DeviceExchangeRefusal.of("unsupported"))
        assertEquals(
            DeviceExchangeRefusal.TOKEN_NOT_STORED,
            DeviceExchangeRefusal.of(DeviceExchangeEnrollResult.REASON_TOKEN_NOT_STORED),
        )
    }

    @Test
    fun `an unknown or empty reason is the generic refusal`() {
        assertEquals(DeviceExchangeRefusal.OTHER, DeviceExchangeRefusal.of("tls-required"))
        assertEquals(DeviceExchangeRefusal.OTHER, DeviceExchangeRefusal.of("something-new"))
        assertEquals(DeviceExchangeRefusal.OTHER, DeviceExchangeRefusal.of(""))
    }
}
