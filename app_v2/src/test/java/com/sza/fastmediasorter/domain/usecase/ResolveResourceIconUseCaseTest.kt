package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveResourceIconUseCaseTest {

    private val useCase = ResolveResourceIconUseCase()

    @Test
    fun `virtual paths map to fixed deterministic icons`() {
        assertEquals(
            "ico-01-001",
            useCase(LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO, ResourceProfile.NONE, ResourceType.LOCAL)
        )
        assertEquals(
            "ico-02-001",
            useCase(LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO, ResourceProfile.NONE, ResourceType.LOCAL)
        )
        assertEquals(
            "ico-03-001",
            useCase(LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES, ResourceProfile.NONE, ResourceType.LOCAL)
        )
        assertEquals(
            "ico-04-001",
            useCase(LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS, ResourceProfile.NONE, ResourceType.LOCAL)
        )
        assertEquals(
            "ico-05-001",
            useCase(LocalMediaScanner.VIRTUAL_PATH_RECENT, ResourceProfile.NONE, ResourceType.LOCAL)
        )
        assertEquals(
            "ico-03-002",
            useCase(LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS, ResourceProfile.NONE, ResourceType.LOCAL)
        )
    }

    @Test
    fun `profile maps to its set first icon`() {
        assertEquals("ico-01-001", useCase("/x", ResourceProfile.AUDIO_LIBRARY, ResourceType.LOCAL))
        assertEquals("ico-02-001", useCase("/x", ResourceProfile.VIDEO_LIBRARY, ResourceType.LOCAL))
        assertEquals("ico-03-001", useCase("/x", ResourceProfile.PHOTO_STORAGE, ResourceType.LOCAL))
        assertEquals("ico-04-001", useCase("/x", ResourceProfile.DOCUMENTS, ResourceType.LOCAL))
    }

    @Test
    fun `NONE profile falls through to type heuristic producing an Other set icon`() {
        // Type heuristic maps every type to the OTHER set → random valid icon id.
        val id = useCase("/x", ResourceProfile.NONE, ResourceType.SMB)
        assertNotNull(id)
        assertTrue("unexpected id $id", id!!.matches(Regex("ico-\\d{2}-\\d{3}")))
    }

    @Test
    fun `resolveForProfileChange always returns a non-null id`() {
        val id = useCase.resolveForProfileChange(ResourceProfile.NONE, ResourceType.CLOUD)
        assertTrue(id.matches(Regex("ico-\\d{2}-\\d{3}")))
    }
}
