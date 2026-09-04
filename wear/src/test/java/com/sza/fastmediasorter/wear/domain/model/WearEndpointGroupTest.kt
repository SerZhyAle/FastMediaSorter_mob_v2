package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2488: the group is the part of the endpoint choice that can be judged with no socket at all, and
 * the one-element case is what keeps a hand-added source behaving exactly as before.
 */
class WearEndpointGroupTest {

    @Test
    fun `a source with no imported endpoints yields its own address alone`() {
        val group = WearEndpointGroup.candidatesFor(makeSource(endpoints = null))

        assertEquals(listOf(WearEndpoint("192.168.1.100", 22)), group)
    }

    @Test
    fun `a list already holding the source address does not repeat it`() {
        val group = WearEndpointGroup.candidatesFor(
            makeSource(
                endpoints = listOf(
                    WearEndpoint("192.168.1.70", 61423),
                    WearEndpoint("192.168.1.100", 22)
                )
            )
        )

        assertEquals(
            listOf(WearEndpoint("192.168.1.70", 61423), WearEndpoint("192.168.1.100", 22)),
            group
        )
    }

    @Test
    fun `a list omitting the source address gets it appended last`() {
        val group = WearEndpointGroup.candidatesFor(
            makeSource(endpoints = listOf(WearEndpoint("192.168.1.70", 61423)))
        )

        assertEquals(
            listOf(WearEndpoint("192.168.1.70", 61423), WearEndpoint("192.168.1.100", 22)),
            group
        )
    }

    @Test
    fun `the imported order is preserved`() {
        val group = WearEndpointGroup.candidatesFor(
            makeSource(
                endpoints = listOf(
                    WearEndpoint("10.0.0.3", 2222),
                    WearEndpoint("10.0.0.1", 22),
                    WearEndpoint("10.0.0.2", 22)
                )
            )
        )

        assertEquals(
            listOf("10.0.0.3", "10.0.0.1", "10.0.0.2", "192.168.1.100"),
            group.map { it.host }
        )
    }

    @Test
    fun `an empty imported list behaves as no list at all`() {
        val group = WearEndpointGroup.candidatesFor(makeSource(endpoints = emptyList()))

        assertEquals(listOf(WearEndpoint("192.168.1.100", 22)), group)
    }

    private fun makeSource(endpoints: List<WearEndpoint>?) = NetworkSource(
        id = "source-1",
        type = NetworkSourceType.SFTP,
        name = "Companion",
        server = "192.168.1.100",
        port = 22,
        username = "user",
        password = "password",
        endpoints = endpoints
    )
}
