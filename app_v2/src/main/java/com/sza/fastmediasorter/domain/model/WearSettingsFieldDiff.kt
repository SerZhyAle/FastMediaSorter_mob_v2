package com.sza.fastmediasorter.domain.model

/**
 * S2093: names the contract fields that differ between two watch-settings sets.
 *
 * The companion window rebuilds the whole payload on every edit rather than reporting which control
 * moved, so the phone learns what changed by comparing. Deriving it here instead of stamping at each
 * control is what makes the record survive a control added later - the setting this ticket exists to
 * protect is precisely the one whose author would have forgotten to stamp it.
 *
 * A null field on either side counts as "not stated" and never as a change, matching the S1781 rule
 * the payload itself carries: an absent value must not be mistaken for an edit to a default.
 */
object WearSettingsFieldDiff {

    fun changedFields(before: WearSettingsPayload?, after: WearSettingsPayload): Set<String> {
        if (before == null) return emptySet()
        return buildSet {
            addIfChanged("audioEnabled", before.audioEnabled, after.audioEnabled)
            addIfChanged("videoEnabled", before.videoEnabled, after.videoEnabled)
            addIfChanged("imagesEnabled", before.imagesEnabled, after.imagesEnabled)
            addIfChanged("slideshowEnabled", before.slideshowEnabled, after.slideshowEnabled)
            addIfChanged(
                "slideshowIntervalSeconds",
                before.slideshowIntervalSeconds,
                after.slideshowIntervalSeconds
            )
            addIfChanged("downloadAlbumArt", before.downloadAlbumArt, after.downloadAlbumArt)
            addIfChanged("viewMode", before.viewMode, after.viewMode)
            addIfChanged("fileListViewMode", before.fileListViewMode, after.fileListViewMode)
            addIfChanged(
                "keepScreenAwakeOutsidePlayers",
                before.keepScreenAwakeOutsidePlayers,
                after.keepScreenAwakeOutsidePlayers
            )
            addIfChanged("backgroundMode", before.backgroundMode, after.backgroundMode)
            addIfChanged("streamsSectionEnabled", before.streamsSectionEnabled, after.streamsSectionEnabled)
        }
    }

    private fun MutableSet<String>.addIfChanged(field: String, before: Any?, after: Any?) {
        if (after != null && before != after) add(field)
    }
}
