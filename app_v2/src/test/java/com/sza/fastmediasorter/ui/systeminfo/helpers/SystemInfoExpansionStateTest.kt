package com.sza.fastmediasorter.ui.systeminfo.helpers

import com.sza.fastmediasorter.core.systeminfo.SystemInfoGroup
import com.sza.fastmediasorter.core.systeminfo.SystemInfoSection
import com.sza.fastmediasorter.data.local.preferences.CollapsibleSectionStore
import com.sza.fastmediasorter.data.repository.SystemInfoGroupStateRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemInfoExpansionStateTest {

    private val store = FakeStore()
    private val state = SystemInfoExpansionState(SystemInfoGroupStateRepositoryImpl(store))

    private val device = section("Device", SystemInfoGroup.DEVICE)
    private val battery = section("Battery", SystemInfoGroup.BATTERY)

    @Test
    fun `a group nobody touched is collapsed`() {
        assertFalse(state.isExpanded(device.stateKey))
        assertFalse(state.allExpanded(listOf(device, battery)))
    }

    @Test
    fun `toggle flips and persists the group under the system info key`() {
        state.toggle(device.stateKey)

        assertTrue(state.isExpanded(device.stateKey))
        assertFalse(state.isExpanded(battery.stateKey))
        assertEquals(true, store.values["system_info__device"])

        state.toggle(device.stateKey)

        assertFalse(state.isExpanded(device.stateKey))
    }

    @Test
    fun `a second state over the same store restores what the user left`() {
        state.toggle(battery.stateKey)

        val reopened = SystemInfoExpansionState(SystemInfoGroupStateRepositoryImpl(store))

        assertTrue(reopened.isExpanded(battery.stateKey))
        assertFalse(reopened.isExpanded(device.stateKey))
    }

    @Test
    fun `the remembered state survives a title change because it keys on the group`() {
        state.toggle(battery.stateKey)

        val localizedBattery = section("Аккумулятор", SystemInfoGroup.BATTERY)

        assertTrue(state.isExpanded(localizedBattery.stateKey))
    }

    @Test
    fun `sections with equal titles and different groups do not share state`() {
        val base = section("Network", SystemInfoGroup.NETWORK)
        val diagnostics = section("Network", SystemInfoGroup.NETWORK_DIAGNOSTICS)

        state.toggle(base.stateKey)

        assertTrue(state.isExpanded(base.stateKey))
        assertFalse(state.isExpanded(diagnostics.stateKey))
    }

    @Test
    fun `a section without a group keys on its title`() {
        val untagged = section("Custom", group = null)

        assertEquals("Custom", untagged.stateKey)

        state.toggle(untagged.stateKey)

        assertEquals(true, store.values["system_info__Custom"])
    }

    @Test
    fun `setAll expands and collapses every section and allExpanded follows`() {
        val sections = listOf(device, battery)

        state.setAll(sections, expanded = true)

        assertTrue(state.allExpanded(sections))

        state.setAll(sections, expanded = false)

        assertFalse(state.isExpanded(device.stateKey))
        assertFalse(state.isExpanded(battery.stateKey))
        assertFalse(state.allExpanded(sections))
    }

    @Test
    fun `every group has an icon and a unique key`() {
        SystemInfoGroup.entries.forEach { group ->
            assertNotNull("no icon for $group", SystemInfoGroupIcons.iconFor(group))
        }
        assertEquals(SystemInfoGroup.entries.size, SystemInfoGroup.entries.map { it.key }.toSet().size)
        assertNull(SystemInfoGroupIcons.iconFor(null))
    }

    private fun section(title: String, group: SystemInfoGroup?) =
        SystemInfoSection(title = title, fields = listOf("label" to "value"), group = group)

    private class FakeStore : CollapsibleSectionStore {
        val values = mutableMapOf<String, Boolean>()

        override fun isExpanded(key: String, default: Boolean): Boolean = values[key] ?: default

        override fun setExpanded(key: String, expanded: Boolean) {
            values[key] = expanded
        }
    }
}
