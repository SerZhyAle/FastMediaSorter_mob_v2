package com.sza.fastmediasorter.data.companion

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S0984: the serializer must produce a payload the parser reads back identically,
 * for both a full config and a passwordless / no-fingerprint one.
 */
class CompanionConfigSerializerTest {

    private val serializer = CompanionConfigSerializer()
    private val parser = CompanionConfigParser()

    private fun roundTrip(dto: CompanionConfigDto): CompanionConfigDto =
        parser.parse(serializer.serialize(dto))

    @Test
    fun `round-trips a full config`() {
        val dto = CompanionConfigDto(
            schemaVersion = 1,
            resourceName = "Home NAS",
            protocol = "sftp",
            accessPaths = listOf(CompanionAccessPathDto(CompanionAccessPathDto.KIND_LAN, "192.168.1.23", 2022)),
            username = "fms",
            password = "s3cret",
            hostKeyFingerprintSha256 = "SHA256:8f6TQvCbXjDMOyu4A9JzKcWlEHmR5pNsGgVaU2wYqhk",
            roots = listOf(CompanionRootDto("/Photos", "Photos")),
            createdAt = "2026-07-11T00:00:00Z"
        )

        val parsed = roundTrip(dto)

        assertEquals(dto, parsed)
    }

    @Test
    fun `round-trips a passwordless no-fingerprint config`() {
        val dto = CompanionConfigDto(
            schemaVersion = 1,
            resourceName = "Shared folder",
            protocol = "sftp",
            accessPaths = listOf(CompanionAccessPathDto(CompanionAccessPathDto.KIND_LAN, "10.0.0.9", 22)),
            username = "guest",
            password = "",
            hostKeyFingerprintSha256 = "",
            roots = listOf(CompanionRootDto("/Media", "Media")),
            createdAt = "2026-07-11T00:00:00Z"
        )

        val parsed = roundTrip(dto)

        assertEquals(dto, parsed)
    }
}
