package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.domain.model.ResourceEditorMode
import com.sza.fastmediasorter.domain.model.ResourceFieldKey
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.testing.createAppSettings
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM coverage for [ResourceEditorUseCase]'s pure logic: validation, persistence-model building,
 * copy-name/suggestion generation, existing-name/path collection, destination metadata, and
 * initialize(). Network testConnection/credential persistence (CryptoHelper) is out of scope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResourceEditorUseCaseTest {

    private val streamVisibleKeys = setOf(
        ResourceFieldKey.NAME,
        ResourceFieldKey.PATH,
        ResourceFieldKey.COMMENT,
        ResourceFieldKey.ACCESS_PIN
    )

    private val resourceRepository = mockk<ResourceRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>()
    private val cachedFileListRepository = mockk<CachedFileListRepository>(relaxed = true)
    private val addResourceUseCase = mockk<AddResourceUseCase>()
    private val updateResourceUseCase = mockk<UpdateResourceUseCase>()
    private val smbOperationsUseCase = mockk<SmbOperationsUseCase>(relaxed = true)
    private val persistResourceCredentialsUseCase = mockk<PersistResourceCredentialsUseCase>(relaxed = true)
    private val resolveResourceIconUseCase = mockk<ResolveResourceIconUseCase>(relaxed = true)
    private lateinit var useCase: ResourceEditorUseCase

    @Before
    fun setup() {
        every { settingsRepository.getSettings() } returns flowOf(createAppSettings())
        useCase = ResourceEditorUseCase(
            resourceRepository, settingsRepository, cachedFileListRepository,
            addResourceUseCase, updateResourceUseCase, smbOperationsUseCase,
            persistResourceCredentialsUseCase, resolveResourceIconUseCase, UnconfinedTestDispatcher(),
        )
    }

    // S2041: strategyFor is private, so fieldSchema(type) is the only proof from the outside that
    // both stream types reach StreamResourceStrategy rather than the folder schema they once had.
    @Test
    fun `fieldSchema renders only name, path, comment and pin for an http stream`() {
        val visibleKeys = useCase.fieldSchema(ResourceType.HTTP_STREAM)
            .filter { it.visible }
            .map { it.key }
            .toSet()

        assertEquals(streamVisibleKeys, visibleKeys)
    }

    @Test
    fun `fieldSchema renders only name, path, comment and pin for an rtsp stream`() {
        val visibleKeys = useCase.fieldSchema(ResourceType.RTSP_STREAM)
            .filter { it.visible }
            .map { it.key }
            .toSet()

        assertEquals(streamVisibleKeys, visibleKeys)
    }

    @Test
    fun `fieldSchema omits folder scanning and media types for both stream types`() {
        listOf(ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM).forEach { type ->
            val keys = useCase.fieldSchema(type).map { it.key }
            assertFalse("$type must not offer subfolder scanning", keys.contains(ResourceFieldKey.SCAN_SUBDIRECTORIES))
            assertFalse("$type must not offer a media-type filter", keys.contains(ResourceFieldKey.MEDIA_TYPES))
            assertFalse("$type must not offer a destination flag", keys.contains(ResourceFieldKey.IS_DESTINATION))
        }
    }

    @Test
    fun `validate fails on blank name and path for local`() {
        val form = ResourceFormData(type = ResourceType.LOCAL, name = "", path = "")

        assertFalse(useCase.validate(form).isValid)
    }

    @Test
    fun `validate passes for complete local form`() {
        val form = ResourceFormData(type = ResourceType.LOCAL, name = "Pics", path = "/storage/Pics")

        assertTrue(useCase.validate(form).isValid)
    }

    @Test
    fun `buildPersistenceModel maps local form to resource`() {
        val form = ResourceFormData(type = ResourceType.LOCAL, name = "Pics", path = "/storage/Pics")

        val model = useCase.buildPersistenceModel(form)

        assertEquals("Pics", model.name)
        assertEquals("/storage/Pics", model.path)
        assertEquals(ResourceType.LOCAL, model.type)
    }

    @Test
    fun `generateUniqueCopyName appends Copy then numbered variants`() {
        assertEquals("Album (Copy)", useCase.generateUniqueCopyName("Album", emptySet()))
        assertEquals(
            "Album (Copy 1)",
            useCase.generateUniqueCopyName("Album", setOf("Album (Copy)")),
        )
        assertEquals(
            "Album (Copy 2)",
            useCase.generateUniqueCopyName("Album", setOf("Album (Copy)", "Album (Copy 1)")),
        )
    }

    @Test
    fun `buildNameSuggestions returns base when free else numbered`() {
        assertEquals(listOf("Fresh"), useCase.buildNameSuggestions("Fresh", emptySet()))

        val suggestions = useCase.buildNameSuggestions("Taken", setOf("Taken"), maxCount = 2)
        assertEquals(2, suggestions.size)
        assertEquals("Taken 1", suggestions[0])
        assertEquals("Taken 2", suggestions[1])
    }

    @Test
    fun `getExistingResourceNames trims blanks and excludes target`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            createMediaResource(id = 1, name = " Alpha "),
            createMediaResource(id = 2, name = "Beta"),
            createMediaResource(id = 3, name = "  "),
        )

        val names = useCase.getExistingResourceNames(excludeResourceId = 2)

        assertEquals(setOf("Alpha"), names)
    }

    @Test
    fun `getExistingPathKeys returns type-path pairs excluding target`() = runTest {
        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(
            createMediaResource(id = 1, type = ResourceType.LOCAL, path = "/a"),
            createMediaResource(id = 2, type = ResourceType.LOCAL, path = "/b"),
        )

        val keys = useCase.getExistingPathKeys(excludeResourceId = 2)

        assertEquals(setOf(ResourceType.LOCAL to "/a"), keys)
    }

    @Test
    fun `initialize CREATE returns form with default rememberFileList`() = runTest {
        every { settingsRepository.getSettings() } returns
            flowOf(createAppSettings().copy(defaultRememberFileList = true))

        val result = useCase.initialize(ResourceEditorMode.CREATE, ResourceType.LOCAL)

        val form = result.getOrThrow()
        assertEquals(ResourceEditorMode.CREATE, form.mode)
        assertTrue(form.rememberFileList)
    }

    @Test
    fun `initialize EDIT without id fails`() = runTest {
        val result = useCase.initialize(ResourceEditorMode.EDIT, ResourceType.LOCAL, resourceId = null)

        assertTrue(result.isFailure)
    }

    @Test
    fun `initialize EDIT with missing resource fails`() = runTest {
        coEvery { resourceRepository.getResourceById(99) } returns null

        val result = useCase.initialize(ResourceEditorMode.EDIT, ResourceType.LOCAL, resourceId = 99)

        assertTrue(result.isFailure)
    }

    @Test
    fun `emptyForm yields CREATE mode for requested type`() {
        val form = useCase.emptyForm(ResourceType.LOCAL)
        assertEquals(ResourceEditorMode.CREATE, form.mode)
        assertEquals(ResourceType.LOCAL, form.type)
    }
}
