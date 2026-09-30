package com.sza.fastmediasorter.data.networkmonitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.URL

/**
 * The router URLs come from whatever host answers SSDP on the LAN, so a non-HTTP scheme must surface as the
 * `IOException` every probe step already treats as "no answer", never as a `ClassCastException`.
 */
class RouterWanAddressDataSourceTest {

    @Test
    fun `http and https locations are accepted`() {
        assertTrue(URL("http://192.168.1.1:5000/rootDesc.xml").isHttpScheme())
        assertTrue(URL("https://192.168.1.1/rootDesc.xml").isHttpScheme())
    }

    @Test
    fun `file ftp and jar locations are refused`() {
        assertFalse(URL("file:///etc/hosts").isHttpScheme())
        assertFalse(URL("ftp://192.168.1.1/desc.xml").isHttpScheme())
        assertFalse(URL("jar:file:///tmp/x.jar!/desc.xml").isHttpScheme())
    }

    @Test
    fun `a file location fails as an IOException instead of a ClassCastException`() {
        assertThrows(IOException::class.java) { URL("file:///etc/hosts").openHttpConnection() }
    }

    @Test
    fun `a file controlURL resolved against an http description fails as an IOException`() {
        val control = URL(URL("http://192.168.1.1:5000/rootDesc.xml"), "file:///data/local/tmp/x")

        assertThrows(IOException::class.java) { control.openHttpConnection() }
    }
}
