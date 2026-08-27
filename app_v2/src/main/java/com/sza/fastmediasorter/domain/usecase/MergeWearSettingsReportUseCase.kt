package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.repository.wear.WearSettingsMirrorStore
import com.sza.fastmediasorter.domain.model.WearSettingsMergeResolver
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.model.WearSettingsRegistry
import javax.inject.Inject

/**
 * S2093: merges the set the watch reported into the phone's mirror, keeping the later edit per field.
 *
 * Before this, the mirror held only what the phone had last sent, so an edit made on the watch did not
 * exist as far as the phone was concerned. The resolution rule is identical to the watch's copy in
 * `wear/../domain/usecase/WearSettingsMergeResolver.kt` and is written twice because the two modules
 * share no source; `scripts/quality/assert-wear-settings-parity.ps1` is what keeps them honest.
 */
class MergeWearSettingsReportUseCase @Inject constructor(
    private val mirrorStore: WearSettingsMirrorStore
) {

    /**
     * @param sentAtEpochMillis the envelope's `sentAt`, in the watch's time base, or null when the
     *   caller has no envelope - then no skew can be measured and none is applied.
     * @param receivedAtEpochMillis when this phone took delivery, in its own time base.
     * @return the merged set, already written to the mirror.
     */
    operator fun invoke(
        incoming: WearSettingsPayload,
        sentAtEpochMillis: Long? = null,
        receivedAtEpochMillis: Long = System.currentTimeMillis()
    ): WearSettingsPayload {
        val stored = mirrorStore.readSettings()
        val stamps = mirrorStore.readFieldTimestamps().toMutableMap()
        val merge = FieldMerge(
            resolver = WearSettingsMergeResolver(
                incomingStamps = incoming.fieldTimestamps,
                localStamps = stamps.toMap(),
                skewMillis = if (sentAtEpochMillis == null) 0L else receivedAtEpochMillis - sentAtEpochMillis,
                // ADR-3 and section 2 Non-goals: the phone owns the interface language and the
                // background picture, so a watch build that ever reports them is ignored.
                rejectedFields = WearSettingsRegistry.phoneOnlyFields
            ),
            stamps = stamps
        )
        // With no mirror yet, the report itself is the baseline: every field then resolves against an
        // absent local stamp, which the resolver already answers with "take the incoming value", so the
        // first report needs no branch of its own.
        val merged = mergeAgainst(stored ?: incoming, incoming, merge)
        mirrorStore.writeSettings(merged)
        mirrorStore.writeFieldTimestamps(stamps)
        mirrorStore.markSynced(receivedAtEpochMillis)
        return merged
    }

    @Suppress("LongMethod")
    private fun mergeAgainst(
        stored: WearSettingsPayload,
        incoming: WearSettingsPayload,
        merge: FieldMerge
    ): WearSettingsPayload = stored.copy(
        audioEnabled = merge.required("audioEnabled", incoming.audioEnabled, stored.audioEnabled),
        videoEnabled = merge.required("videoEnabled", incoming.videoEnabled, stored.videoEnabled),
        imagesEnabled = merge.required("imagesEnabled", incoming.imagesEnabled, stored.imagesEnabled),
        slideshowEnabled = merge.required(
            "slideshowEnabled",
            incoming.slideshowEnabled,
            stored.slideshowEnabled
        ),
        slideshowIntervalSeconds = merge.required(
            "slideshowIntervalSeconds",
            incoming.slideshowIntervalSeconds,
            stored.slideshowIntervalSeconds
        ),
        downloadAlbumArt = merge.required(
            "downloadAlbumArt",
            incoming.downloadAlbumArt,
            stored.downloadAlbumArt
        ),
        viewMode = merge.optional("viewMode", incoming.viewMode, stored.viewMode),
        keepScreenAwakeOutsidePlayers = merge.optional(
            "keepScreenAwakeOutsidePlayers",
            incoming.keepScreenAwakeOutsidePlayers,
            stored.keepScreenAwakeOutsidePlayers
        ),
        fileListViewMode = merge.optional(
            "fileListViewMode",
            incoming.fileListViewMode,
            stored.fileListViewMode
        ),
        backgroundMode = merge.optional("backgroundMode", incoming.backgroundMode, stored.backgroundMode),
        streamsSectionEnabled = merge.optional(
            "streamsSectionEnabled",
            incoming.streamsSectionEnabled,
            stored.streamsSectionEnabled
        ),
        // appLanguage is deliberately absent: it is the PHONE_ONLY entry the copy above preserves, and
        // the resolver would refuse it anyway.
        fieldTimestamps = merge.stamps.toMap(),
        capabilities = incoming.capabilities ?: stored.capabilities
    )

    /**
     * Applies one field's decision and records the stamp the winner brought with it.
     *
     * The stamp map is mutated as fields are resolved rather than rebuilt afterwards, so an accepted
     * value and the time it was edited cannot end up recorded separately.
     */
    private class FieldMerge(
        private val resolver: WearSettingsMergeResolver,
        val stamps: MutableMap<String, Long>
    ) {

        /**
         * S1781: a null incoming value means "the watch did not report this" and never "reset it".
         */
        fun <T : Any> optional(field: String, incoming: T?, stored: T?): T? {
            val decision = if (incoming == null) null else resolver.resolve(field)
            if (decision == null || !decision.apply) return stored
            decision.stampEpochMillis?.let { stamps[field] = it }
            return incoming
        }

        fun <T : Any> required(field: String, incoming: T, stored: T): T = optional(field, incoming, stored) ?: stored
    }
}
