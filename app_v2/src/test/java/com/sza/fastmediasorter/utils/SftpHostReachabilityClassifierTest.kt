package com.sza.fastmediasorter.utils

import com.sza.fastmediasorter.utils.SftpHostReachabilityClassifier.Reachability
import org.junit.Assert.assertEquals
import org.junit.Test

class SftpHostReachabilityClassifierTest {

    @Test
    fun `rfc1918 and cgnat and loopback and link-local and mdns are private`() {
        assertEquals(Reachability.PRIVATE_LAN, SftpHostReachabilityClassifier.classify("10.0.0.9"))
        assertEquals(Reachability.PRIVATE_LAN, SftpHostReachabilityClassifier.classify("172.16.5.4"))
        assertEquals(Reachability.PRIVATE_LAN, SftpHostReachabilityClassifier.classify("192.168.1.23"))
        assertEquals(Reachability.PRIVATE_LAN, SftpHostReachabilityClassifier.classify("100.64.0.1"))
        assertEquals(Reachability.PRIVATE_LAN, SftpHostReachabilityClassifier.classify("127.0.0.1"))
        assertEquals(Reachability.PRIVATE_LAN, SftpHostReachabilityClassifier.classify("169.254.1.1"))
        assertEquals(Reachability.PRIVATE_LAN, SftpHostReachabilityClassifier.classify("nas.local"))
    }

    @Test
    fun `public ipv4 is routable`() {
        assertEquals(Reachability.PUBLIC_ROUTABLE, SftpHostReachabilityClassifier.classify("203.0.113.7"))
        assertEquals(Reachability.PUBLIC_ROUTABLE, SftpHostReachabilityClassifier.classify("8.8.8.8"))
        // 172.32 is outside the 172.16-31 private block.
        assertEquals(Reachability.PUBLIC_ROUTABLE, SftpHostReachabilityClassifier.classify("172.32.0.1"))
    }

    @Test
    fun `dns hostname routability is unknown`() {
        assertEquals(Reachability.HOSTNAME, SftpHostReachabilityClassifier.classify("nas.example.com"))
        assertEquals(Reachability.HOSTNAME, SftpHostReachabilityClassifier.classify(""))
    }
}
