package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3981: every field the two-way merge resolves must be detected by [WearSettingsFieldDiff].
 *
 * A shared field the diff misses is stored without an edit time, and the merge then lets an older
 * watch edit revert the owner's newer phone edit. The test walks [WearSettingsRegistry.sharedFields]
 * rather than a hand-kept list, so a new shared field without a diff line fails here.
 */
class WearSettingsFieldDiffTest {

    private val base = WearSettingsPayload(
        audioEnabled = true,
        videoEnabled = true,
        imagesEnabled = true,
        slideshowEnabled = false,
        slideshowIntervalSeconds = 5,
        downloadAlbumArt = true,
        viewMode = WearSettingsPayload.VIEW_MODE_LIST,
        keepScreenAwakeOutsidePlayers = false,
        fileListViewMode = WearSettingsPayload.VIEW_MODE_LIST,
        backgroundMode = WearSettingsPayload.BACKGROUND_MODE_NONE,
        colorScheme = WearSettingsPayload.COLOR_SCHEME_DARK,
        streamsSectionEnabled = false,
        documentsEnabled = false,
        disableAnimations = false,
        powerSavingTrigger = "OFF",
        backgroundPlaybackEnabled = false,
        panelAutoHideSeconds = 3
    )

    @Test
    fun `each shared field is reported when only that field changes`() {
        WearSettingsRegistry.sharedFields.forEach { field ->
            val changed = WearSettingsFieldDiff.changedFields(base, mutate(field))
            assertEquals("field $field", setOf(field), changed)
        }
    }

    @Test
    fun `identical payloads report no change`() {
        assertTrue(WearSettingsFieldDiff.changedFields(base, base.copy()).isEmpty())
    }

    @Test
    fun `absent previous payload reports no change`() {
        assertTrue(WearSettingsFieldDiff.changedFields(null, mutate("audioEnabled")).isEmpty())
    }

    @Test
    fun `null value on the new side is not a change`() {
        assertTrue(WearSettingsFieldDiff.changedFields(base, base.copy(disableAnimations = null)).isEmpty())
    }

    @Suppress("CyclomaticComplexMethod")
    private fun mutate(field: String): WearSettingsPayload = when (field) {
        "audioEnabled" -> base.copy(audioEnabled = false)
        "videoEnabled" -> base.copy(videoEnabled = false)
        "imagesEnabled" -> base.copy(imagesEnabled = false)
        "documentsEnabled" -> base.copy(documentsEnabled = true)
        "slideshowEnabled" -> base.copy(slideshowEnabled = true)
        "slideshowIntervalSeconds" -> base.copy(slideshowIntervalSeconds = 10)
        "downloadAlbumArt" -> base.copy(downloadAlbumArt = false)
        "viewMode" -> base.copy(viewMode = WearSettingsPayload.VIEW_MODE_GRID_2)
        "fileListViewMode" -> base.copy(fileListViewMode = WearSettingsPayload.VIEW_MODE_GRID_2)
        "keepScreenAwakeOutsidePlayers" -> base.copy(keepScreenAwakeOutsidePlayers = true)
        "backgroundPlaybackEnabled" -> base.copy(backgroundPlaybackEnabled = true)
        "backgroundMode" -> base.copy(backgroundMode = WearSettingsPayload.BACKGROUND_MODE_IMAGE)
        "colorScheme" -> base.copy(colorScheme = WearSettingsPayload.COLOR_SCHEME_LIGHT)
        "streamsSectionEnabled" -> base.copy(streamsSectionEnabled = true)
        "disableAnimations" -> base.copy(disableAnimations = true)
        "powerSavingTrigger" -> base.copy(powerSavingTrigger = "ALWAYS")
        "panelAutoHideSeconds" -> base.copy(panelAutoHideSeconds = 7)
        else -> throw AssertionError("shared field $field has no mutation in this test")
    }
}
