package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.domain.repository.FakeWearHardwareDataSource
import com.sza.fastmediasorter.wear.domain.repository.FakeWearHealthDataSource
import com.sza.fastmediasorter.wear.domain.repository.FakeWearSystemInfoDataSource
import com.sza.fastmediasorter.wear.domain.repository.WearNodeDescriptor
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import com.sza.fastmediasorter.wear.domain.systeminfo.AppInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.DeviceInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.FakeWearSystemInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.HealthInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.MemoryInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.PhoneLinkContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.RadioCapabilityContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.SensorsInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.StorageInfoContributor
import com.sza.fastmediasorter.wear.domain.systeminfo.WearSystemInfoContributor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FIRST_ORDER = 1
private const val SECOND_ORDER = 2
private const val THIRD_ORDER = 3

class GatherWearSystemInfoUseCaseTest {

    @Test
    fun `sections keep the fixed order`() = runTest {
        val sections = collect(FakeWearSystemInfoDataSource())

        assertEquals(
            listOf(
                R.string.system_info_section_device,
                R.string.system_info_section_app,
                R.string.system_info_section_health,
                R.string.system_info_section_sensors,
                R.string.system_info_section_radio,
                R.string.system_info_section_memory,
                R.string.system_info_section_storage,
                R.string.system_info_section_phone
            ),
            sections.map { section -> section.titleRes }
        )
    }

    @Test
    fun `the order is the contributor's own, not the set's`() = runTest {
        val shuffled = linkedSetOf<WearSystemInfoContributor>(
            FakeWearSystemInfoContributor(THIRD_ORDER, listOf(named(R.string.system_info_section_phone))),
            FakeWearSystemInfoContributor(FIRST_ORDER, listOf(named(R.string.system_info_section_device))),
            FakeWearSystemInfoContributor(SECOND_ORDER, listOf(named(R.string.system_info_section_app)))
        )

        val sections = GatherWearSystemInfoUseCase(shuffled)()

        assertEquals(
            listOf(
                R.string.system_info_section_device,
                R.string.system_info_section_app,
                R.string.system_info_section_phone
            ),
            sections.map { section -> section.titleRes }
        )
    }

    @Test
    fun `a contributor that throws costs only its own sections`() = runTest {
        val contributors = linkedSetOf<WearSystemInfoContributor>(
            FakeWearSystemInfoContributor(FIRST_ORDER, failure = IllegalStateException("watch refused")),
            FakeWearSystemInfoContributor(SECOND_ORDER, listOf(named(R.string.system_info_section_app)))
        )

        val sections = GatherWearSystemInfoUseCase(contributors)()

        assertEquals(listOf(R.string.system_info_section_app), sections.map { section -> section.titleRes })
    }

    @Test
    fun `a section whose facts are all missing states why instead of vanishing`() = runTest {
        val blindToMemory = FakeWearSystemInfoDataSource().apply {
            totalMemoryBytes = null
            availableMemoryBytes = null
        }

        val memory = collect(blindToMemory).first { section ->
            section.titleRes == R.string.system_info_section_memory
        }

        assertTrue(memory.fields.isEmpty())
        assertEquals(R.string.system_info_empty_unreadable, memory.emptyReasonRes)
    }

    @Test
    fun `a section that filled carries no emptiness reason`() = runTest {
        val memory = collect(FakeWearSystemInfoDataSource()).first { section ->
            section.titleRes == R.string.system_info_section_memory
        }

        assertNull(memory.emptyReasonRes)
    }

    @Test
    fun `a single missing fact drops its line and keeps the rest of the section`() = runTest {
        val noApiLevel = FakeWearSystemInfoDataSource().apply { apiLevel = null }

        val device = collect(noApiLevel).first { section ->
            section.titleRes == R.string.system_info_section_device
        }

        // Asserted by absence and presence rather than as the whole list: the device section grows
        // whenever a build fact is added to it, and a test that pinned its exact contents would fail on
        // every such addition while saying nothing about the rule it exists for - that ONE missing fact
        // costs its own line and no other.
        val labels = device.fields.map { field -> field.labelRes }
        assertFalse(labels.contains(R.string.system_info_api_level))
        assertTrue(labels.contains(R.string.system_info_model))
        assertTrue(labels.contains(R.string.system_info_os_version))
    }

    @Test
    fun `the model line carries the vendor once`() = runTest {
        val vendorInModel = FakeWearSystemInfoDataSource().apply { model = "Samsung SM-L305" }

        assertEquals("Samsung SM-L305", modelValue(vendorInModel))
        assertEquals("Samsung Galaxy Watch7", modelValue(FakeWearSystemInfoDataSource()))
    }

    @Test
    fun `byte counts read in gigabytes above a gigabyte and in megabytes below it`() = runTest {
        val memory = collect(FakeWearSystemInfoDataSource()).first { section ->
            section.titleRes == R.string.system_info_section_memory
        }

        assertEquals("512 MB", textOf(memory, R.string.system_info_free))
        assertEquals("2.0 GB", textOf(memory, R.string.system_info_total))
    }

    @Test
    fun `the link section names the device and how it is reached`() = runTest {
        val phone = collect(FakeWearSystemInfoDataSource()).first { section ->
            section.titleRes == R.string.system_info_section_phone
        }

        assertEquals("Pixel 9", textOf(phone, R.string.system_info_phone_link))
        assertEquals(R.string.system_info_link_nearby, labelOf(phone, R.string.system_info_link_route))
        assertEquals("n1", textOf(phone, R.string.system_info_link_node_id))
    }

    @Test
    fun `a node reachable only by relay says so`() = runTest {
        val relayed = FakeWearSystemInfoDataSource().apply {
            nodes = listOf(WearNodeDescriptor(id = "n1", displayName = "Pixel 9", isNearby = false))
        }

        val phone = collect(relayed).first { section ->
            section.titleRes == R.string.system_info_section_phone
        }

        assertEquals(R.string.system_info_link_cloud, labelOf(phone, R.string.system_info_link_route))
    }

    @Test
    fun `nothing paired reads differently from a Data Layer that would not answer`() = runTest {
        val unpaired = FakeWearSystemInfoDataSource().apply { nodes = emptyList() }
        val silent = FakeWearSystemInfoDataSource().apply { nodes = null }

        assertEquals(R.string.system_info_link_none, linkEmptiness(unpaired))
        assertEquals(R.string.system_info_empty_unreadable, linkEmptiness(silent))
    }

    @Test
    fun `the capability set is one collapsible value, not a line each`() = runTest {
        val phone = collect(FakeWearSystemInfoDataSource()).first { section ->
            section.titleRes == R.string.system_info_section_phone
        }

        val capabilities = phone.fields.single { it.labelRes == R.string.system_info_link_capabilities }
        assertEquals(
            listOf("fms_phone", "fms_send_to"),
            (capabilities.value as WearSystemInfoValue.Enumerated).entries
        )
    }

    @Test
    fun `storage reports the app's own footprint and its cache reserve`() = runTest {
        val storage = collect(FakeWearSystemInfoDataSource()).first { section ->
            section.titleRes == R.string.system_info_section_storage
        }

        assertEquals("128 MB", textOf(storage, R.string.system_info_storage_app_data))
        assertEquals("32 MB", textOf(storage, R.string.system_info_storage_app_cache))
        assertEquals("4.0 GB", textOf(storage, R.string.system_info_storage_cache_quota))
    }

    private suspend fun linkEmptiness(dataSource: FakeWearSystemInfoDataSource): Int? =
        collect(dataSource).first { section ->
            section.titleRes == R.string.system_info_section_phone
        }.emptyReasonRes

    private fun labelOf(section: WearSystemInfoSection, labelRes: Int): Int {
        val field: WearSystemInfoField = section.fields.first { it.labelRes == labelRes }
        return (field.value as WearSystemInfoValue.Label).res
    }

    /** The real contributor set, assembled here the way the Hilt module assembles it in the app. */
    private suspend fun collect(dataSource: WearSystemInfoDataSource): List<WearSystemInfoSection> =
        GatherWearSystemInfoUseCase(
            setOf(
                DeviceInfoContributor(dataSource, FakeWearHardwareDataSource()),
                AppInfoContributor(dataSource),
                HealthInfoContributor(FakeWearHealthDataSource()),
                SensorsInfoContributor(FakeWearHardwareDataSource()),
                RadioCapabilityContributor(FakeWearHardwareDataSource()),
                MemoryInfoContributor(dataSource),
                StorageInfoContributor(dataSource),
                PhoneLinkContributor(dataSource)
            )
        )()

    private fun named(titleRes: Int): WearSystemInfoSection =
        WearSystemInfoSection(titleRes, listOf(WearSystemInfoField(titleRes, WearSystemInfoValue.Text("x"))))

    private suspend fun modelValue(dataSource: WearSystemInfoDataSource): String {
        val device = collect(dataSource).first { section ->
            section.titleRes == R.string.system_info_section_device
        }
        return textOf(device, R.string.system_info_model)
    }

    private fun textOf(
        section: WearSystemInfoSection,
        labelRes: Int
    ): String {
        val field: WearSystemInfoField = section.fields.first { it.labelRes == labelRes }
        return (field.value as WearSystemInfoValue.Text).text
    }
}
