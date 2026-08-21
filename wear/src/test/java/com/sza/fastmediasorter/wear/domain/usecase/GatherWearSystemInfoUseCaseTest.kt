package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoField
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoValue
import com.sza.fastmediasorter.wear.domain.repository.FakeWearSystemInfoDataSource
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatherWearSystemInfoUseCaseTest {

    @Test
    fun `sections keep the fixed order`() = runTest {
        val sections = GatherWearSystemInfoUseCase(FakeWearSystemInfoDataSource())()

        assertEquals(
            listOf(
                R.string.system_info_section_device,
                R.string.system_info_section_app,
                R.string.system_info_section_memory,
                R.string.system_info_section_storage,
                R.string.system_info_section_phone
            ),
            sections.map { section -> section.titleRes }
        )
    }

    @Test
    fun `a section whose facts are all missing is dropped`() = runTest {
        val blindToMemory = FakeWearSystemInfoDataSource().apply {
            totalMemoryBytes = null
            availableMemoryBytes = null
        }

        val sections = GatherWearSystemInfoUseCase(blindToMemory)()

        assertTrue(sections.none { section -> section.titleRes == R.string.system_info_section_memory })
    }

    @Test
    fun `a single missing fact drops its line and keeps the rest of the section`() = runTest {
        val noApiLevel = FakeWearSystemInfoDataSource().apply { apiLevel = null }

        val device = GatherWearSystemInfoUseCase(noApiLevel)()
            .first { section -> section.titleRes == R.string.system_info_section_device }

        assertEquals(
            listOf(R.string.system_info_model, R.string.system_info_os_version),
            device.fields.map { field -> field.labelRes }
        )
    }

    @Test
    fun `the model line carries the vendor once`() = runTest {
        val vendorInModel = FakeWearSystemInfoDataSource().apply { model = "Samsung SM-L305" }

        assertEquals("Samsung SM-L305", modelValue(vendorInModel))
        assertEquals("Samsung Galaxy Watch7", modelValue(FakeWearSystemInfoDataSource()))
    }

    @Test
    fun `byte counts read in gigabytes above a gigabyte and in megabytes below it`() = runTest {
        val sections = GatherWearSystemInfoUseCase(FakeWearSystemInfoDataSource())()
        val memory = sections.first { section -> section.titleRes == R.string.system_info_section_memory }

        assertEquals("512 MB", textOf(memory, R.string.system_info_free))
        assertEquals("2.0 GB", textOf(memory, R.string.system_info_total))
    }

    @Test
    fun `the phone line reports both states`() = runTest {
        assertEquals(R.string.system_info_phone_connected, phoneAnswer(phoneConnected = true))
        assertEquals(R.string.system_info_phone_not_connected, phoneAnswer(phoneConnected = false))
    }

    private suspend fun modelValue(dataSource: WearSystemInfoDataSource): String {
        val device = GatherWearSystemInfoUseCase(dataSource)()
            .first { section -> section.titleRes == R.string.system_info_section_device }
        return textOf(device, R.string.system_info_model)
    }

    private suspend fun phoneAnswer(phoneConnected: Boolean): Int {
        val source = FakeWearSystemInfoDataSource().apply { this.phoneConnected = phoneConnected }
        val phone = GatherWearSystemInfoUseCase(source)()
            .first { section -> section.titleRes == R.string.system_info_section_phone }
        return (phone.fields.single().value as WearSystemInfoValue.Label).res
    }

    private fun textOf(
        section: WearSystemInfoSection,
        labelRes: Int
    ): String {
        val field: WearSystemInfoField = section.fields.first { it.labelRes == labelRes }
        return (field.value as WearSystemInfoValue.Text).text
    }
}
