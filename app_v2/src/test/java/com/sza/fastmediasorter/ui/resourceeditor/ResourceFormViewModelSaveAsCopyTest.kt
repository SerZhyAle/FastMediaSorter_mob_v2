package com.sza.fastmediasorter.ui.resourceeditor

import com.sza.fastmediasorter.domain.model.ResourceEditorMode
import com.sza.fastmediasorter.domain.model.ResourceFormData
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.ResourceValidationResult
import com.sza.fastmediasorter.domain.model.ResourceVerificationStatus
import com.sza.fastmediasorter.domain.usecase.GenerateUniqueCopyNameUseCase
import com.sza.fastmediasorter.domain.usecase.ResolveResourceIconUseCase
import com.sza.fastmediasorter.domain.usecase.ResourceEditorSaveResult
import com.sza.fastmediasorter.domain.usecase.ResourceEditorUseCase
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceFormViewModelSaveAsCopyTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val editedForm = ResourceFormData(
        id = EDITED_ID,
        mode = ResourceEditorMode.EDIT,
        type = ResourceType.LOCAL,
        name = "Home",
        path = "/storage/emulated/0/Home"
    )

    private val generateCopyName = mockk<GenerateUniqueCopyNameUseCase>().also { generate ->
        every { generate.invoke(any<String>(), any<Set<String>>()) } returns "Home (Copy)"
    }

    private val useCase = mockk<ResourceEditorUseCase>(relaxed = true) {
        coEvery { initialize(any(), any(), any()) } returns Result.success(editedForm)
        coEvery { getExistingResourceNames(any()) } returns setOf("Home", "Work")
        coEvery { getExistingPathKeys(any()) } returns emptySet()
        coEvery { getResourceStatistics(any()) } returns null
        every { validate(any()) } returns ResourceValidationResult(isValid = true)
        every { fieldSchema(any()) } returns emptyList()
        every { buildNameSuggestions(any(), any(), any()) } returns emptyList()
    }

    private fun initializedViewModel(): ResourceFormViewModel = runBlocking {
        val viewModel = ResourceFormViewModel(useCase, ResolveResourceIconUseCase(), generateCopyName)
        viewModel.initialize(ResourceEditorMode.EDIT, ResourceType.LOCAL, EDITED_ID)
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.originalSnapshot != null } }
        viewModel
    }

    @Test
    fun `failed copy save leaves the editor on the edited resource`() = runBlocking {
        coEvery { useCase.save(any(), any()) } returns Result.failure(IllegalStateException("disk full"))
        val viewModel = initializedViewModel()

        viewModel.onSaveAsCopy()
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { !it.isSaving } }

        val form = viewModel.uiState.value.formData
        assertEquals(ResourceEditorMode.EDIT, form.mode)
        assertEquals(EDITED_ID, form.id)
        assertEquals("Home", form.name)
    }

    @Test
    fun `copy save passes a new unique resource to the use case`() = runBlocking {
        val saved = slot<ResourceFormData>()
        coEvery { useCase.save(capture(saved), any<CoroutineScope>()) } returns
            Result.success(ResourceEditorSaveResult(COPY_ID, ResourceVerificationStatus.VERIFIED))
        val viewModel = initializedViewModel()

        viewModel.onSaveAsCopy()
        withTimeout(TIMEOUT_MS) { viewModel.uiState.first { it.saveResult != null } }

        assertEquals(ResourceEditorMode.COPY, saved.captured.mode)
        assertNull(saved.captured.id)
        assertEquals("Home (Copy)", saved.captured.name)
        assertEquals(ResourceEditorMode.EDIT, viewModel.uiState.value.formData.mode)
    }

    @Test
    fun `colliding copy name is refused without saving or touching the form`() = runBlocking {
        every { generateCopyName.invoke(any<String>(), any<Set<String>>()) } returns "work"
        val viewModel = initializedViewModel()

        viewModel.onSaveAsCopy()

        coVerify(exactly = 0) { useCase.save(any(), any()) }
        assertEquals(ResourceEditorMode.EDIT, viewModel.uiState.value.formData.mode)
        assertEquals(EDITED_ID, viewModel.uiState.value.formData.id)
    }

    private companion object {
        const val EDITED_ID = 5L
        const val COPY_ID = 9L
        const val TIMEOUT_MS = 5_000L
    }
}
