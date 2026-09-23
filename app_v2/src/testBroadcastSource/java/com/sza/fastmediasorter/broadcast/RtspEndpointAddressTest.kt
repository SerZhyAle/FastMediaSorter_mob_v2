package com.sza.fastmediasorter.broadcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RtspEndpointAddressTest {

    @Test
    fun `any-address library host is replaced by the LAN host with port and path kept`() {
        assertEquals(
            "rtsp://192.168.1.20:8554/",
            RtspEndpointAddress.onLanHost("rtsp://0.0.0.0:8554/", "192.168.1.20"),
        )
    }

    @Test
    fun `mobile carrier library host is replaced by the LAN host`() {
        assertEquals(
            "rtsp://10.0.0.7:8554/live",
            RtspEndpointAddress.onLanHost("rtsp://100.64.3.9:8554/live", "10.0.0.7"),
        )
    }

    @Test
    fun `endpoint without a path gets the root path`() {
        assertEquals(
            "rtsp://192.168.1.20:8554/",
            RtspEndpointAddress.onLanHost("rtsp://0.0.0.0:8554", "192.168.1.20"),
        )
    }

    @Test
    fun `loopback LAN host is never published`() {
        assertNull(RtspEndpointAddress.onLanHost("rtsp://0.0.0.0:8554/", "127.0.0.1"))
        assertNull(RtspEndpointAddress.onLanHost("rtsp://0.0.0.0:8554/", "127.3.4.5"))
        assertNull(RtspEndpointAddress.onLanHost("rtsp://0.0.0.0:8554/", "localhost"))
    }

    @Test
    fun `any-address or blank LAN host is never published`() {
        assertNull(RtspEndpointAddress.onLanHost("rtsp://192.168.1.20:8554/", "0.0.0.0"))
        assertNull(RtspEndpointAddress.onLanHost("rtsp://192.168.1.20:8554/", ""))
    }

    @Test
    fun `unparsable endpoint or one without a port yields null`() {
        assertNull(RtspEndpointAddress.onLanHost("rtsp://bad host:8554/", "192.168.1.20"))
        assertNull(RtspEndpointAddress.onLanHost("rtsp://0.0.0.0/", "192.168.1.20"))
        assertNull(RtspEndpointAddress.onLanHost("", "192.168.1.20"))
    }
}
