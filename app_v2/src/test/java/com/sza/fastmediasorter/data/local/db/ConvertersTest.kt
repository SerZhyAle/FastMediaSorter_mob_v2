package com.sza.fastmediasorter.data.local.db

import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-JVM round-trip checks for [Converters]. Every converter is a symmetric
 * enum<->name mapping; the cloud-provider pair additionally tolerates null.
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `resource type round-trips for every value`() {
        for (value in ResourceType.entries) {
            assertEquals(value, converters.toResourceType(converters.fromResourceType(value)))
        }
    }

    @Test
    fun `sort mode round-trips for every value`() {
        for (value in SortMode.entries) {
            assertEquals(value, converters.toSortMode(converters.fromSortMode(value)))
        }
    }

    @Test
    fun `display mode round-trips for every value`() {
        for (value in DisplayMode.entries) {
            assertEquals(value, converters.toDisplayMode(converters.fromDisplayMode(value)))
        }
    }

    @Test
    fun `cloud provider round-trips for every value`() {
        for (value in CloudProvider.entries) {
            assertEquals(value, converters.toCloudProvider(converters.fromCloudProvider(value)))
        }
    }

    @Test
    fun `cloud provider maps null both ways`() {
        assertNull(converters.fromCloudProvider(null))
        assertNull(converters.toCloudProvider(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown resource type name throws`() {
        converters.toResourceType("NOT_A_TYPE")
    }
}
