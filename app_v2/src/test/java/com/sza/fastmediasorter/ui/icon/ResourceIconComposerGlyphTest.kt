package com.sza.fastmediasorter.ui.icon

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S4477: Recent and the pinned All files resource were seeded with the same OTHER-set placeholder and drew
 * the same circle. A missing or placeholder id on those two now picks a dedicated glyph; any other id is the
 * user's choice and stays.
 */
class ResourceIconComposerGlyphTest {

    private val placeholder = ResourceIconRegistry.firstIdFor(ResourceIconSet.OTHER)

    private fun recent(iconId: String?) = MediaResource(
        name = "Recent",
        path = LocalMediaScanner.VIRTUAL_PATH_RECENT,
        type = ResourceType.LOCAL,
        iconId = iconId,
    )

    private fun allFiles(iconId: String?) = MediaResource(
        name = "All files",
        path = "/storage/emulated/0",
        type = ResourceType.LOCAL,
        profile = ResourceProfile.ALL_FILES,
        allFiles = true,
        iconId = iconId,
    )

    @Test
    fun `recent with the placeholder or no id gets the recent glyph`() {
        assertEquals(R.drawable.ic_tile_recent, ResourceIconComposer.predefinedGlyphFor(recent(placeholder)))
        assertEquals(R.drawable.ic_tile_recent, ResourceIconComposer.predefinedGlyphFor(recent(null)))
    }

    @Test
    fun `pinned all files with the placeholder or no id gets the all files glyph`() {
        assertEquals(R.drawable.ic_tile_all_files, ResourceIconComposer.predefinedGlyphFor(allFiles(placeholder)))
        assertEquals(R.drawable.ic_tile_all_files, ResourceIconComposer.predefinedGlyphFor(allFiles(null)))
    }

    @Test
    fun `the two predefined resources never share a glyph`() {
        val recentGlyph = ResourceIconComposer.predefinedGlyphFor(recent(placeholder))
        val allFilesGlyph = ResourceIconComposer.predefinedGlyphFor(allFiles(placeholder))
        assertNotEquals(recentGlyph, allFilesGlyph)
    }

    @Test
    fun `a stored id other than the placeholder is the users choice`() {
        assertNull(ResourceIconComposer.predefinedGlyphFor(recent("ico-05-009")))
        assertNull(ResourceIconComposer.predefinedGlyphFor(allFiles("ico-05-002")))
    }

    @Test
    fun `an ordinary resource keeps the registry icon even with the placeholder`() {
        val ordinary = MediaResource(
            name = "Docs",
            path = "/storage/emulated/0/Docs",
            type = ResourceType.LOCAL,
            iconId = placeholder,
        )
        assertNull(ResourceIconComposer.predefinedGlyphFor(ordinary))
    }
}
