package com.sza.fastmediasorter.ui.icon

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceProfile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.util.NotesFolderPolicy
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

    private fun notesFolder(iconId: String?) = MediaResource(
        name = "Notes",
        path = NotesFolderPolicy.notesDirectory().path,
        type = ResourceType.LOCAL,
        iconId = iconId,
    )

    @Test
    fun `the notes folder with a backfilled other-set id or no id gets the notes glyph`() {
        val backfilled = ResourceIconRegistry.idsFor(ResourceIconSet.OTHER).last()
        assertEquals(R.drawable.ic_send_note, ResourceIconComposer.predefinedGlyphFor(notesFolder(backfilled)))
        assertEquals(R.drawable.ic_send_note, ResourceIconComposer.predefinedGlyphFor(notesFolder(null)))
    }

    @Test
    fun `the notes folder keeps an icon picked from another set`() {
        val picked = ResourceIconRegistry.firstIdFor(ResourceIconSet.MUSIC)
        assertNull(ResourceIconComposer.predefinedGlyphFor(notesFolder(picked)))
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

    @Test
    fun `unprofiled local folder without iconId falls back to folder icon instead of OTHER ring`() {
        val localFolder = MediaResource(
            name = "src",
            path = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2Fsrc",
            type = ResourceType.LOCAL,
            profile = ResourceProfile.NONE,
            iconId = null,
        )
        assertEquals(R.drawable.ic_folder, ResourceIconComposer.themeDrawableFor(localFolder))
    }

    @Test
    fun `unprofiled local folder with explicit iconId keeps user choice`() {
        val localFolder = MediaResource(
            name = "src",
            path = "/storage/emulated/0/Download/src",
            type = ResourceType.LOCAL,
            profile = ResourceProfile.NONE,
            iconId = "ico-05-004",
        )
        assertEquals(R.drawable.ico_05_004, ResourceIconComposer.themeDrawableFor(localFolder))
    }
}
