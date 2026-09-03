package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.wear.core.util.WearLocaleManager
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearSettingsMergeResolver
import com.sza.fastmediasorter.wear.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.wear.domain.model.WearSettingsPayloadDecoder
import com.sza.fastmediasorter.wear.domain.model.WearSettingsRegistry
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ApplyWearSettingsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: WearPreferencesRepository
) {

    /**
     * Applies an incoming settings set, keeping whichever side changed each field later (S2093).
     *
     * @param sentAtEpochMillis the envelope's `sentAt`, in the sender's time base, or null when the
     *   caller has no envelope - then no skew can be measured and none is applied.
     * @param receivedAtEpochMillis when this watch took delivery, in its own time base.
     * @param presentFields S2462: the contract keys the phone actually carried, from
     *   [com.sza.fastmediasorter.wear.domain.model.WearSettingsPayloadDecoder]. A field outside this
     *   set is left alone rather than applied, because the payload object cannot express its absence -
     *   Gson builds the class reflectively, so an omitted `audioEnabled` arrives as the JVM default
     *   `false` and is indistinguishable from a phone that switched audio off. Declared last, after the
     *   two timing parameters, so the existing positional call sites keep compiling; defaults to the
     *   whole contract, which is exactly the behaviour that shipped before this ticket.
     */
    suspend operator fun invoke(
        payload: WearSettingsPayload,
        sentAtEpochMillis: Long? = null,
        receivedAtEpochMillis: Long = System.currentTimeMillis(),
        presentFields: Set<String> = WearSettingsPayloadDecoder.CONTRACT_FIELDS
    ) {
        val resolver = WearSettingsMergeResolver(
            incomingStamps = payload.fieldTimestamps,
            localStamps = preferencesRepository.settingTimestamps.first(),
            skewMillis = if (sentAtEpochMillis == null) 0L else receivedAtEpochMillis - sentAtEpochMillis,
            // ADR-2: the watch owns these outright, so a phone build that ever sends them is ignored
            // rather than trusted.
            rejectedFields = WearSettingsRegistry.watchOnlyFields
        )
        val gate = FieldGate(resolver, presentFields)
        applyMediaTypes(payload, gate)
        applySlideshow(payload, gate)
        applyScreen(payload, gate)
        applyLanguage(payload, gate)
    }

    private suspend fun applyMediaTypes(payload: WearSettingsPayload, resolver: FieldGate) {
        apply(resolver, "audioEnabled", payload.audioEnabled) { preferencesRepository.setAudioEnabled(it) }
        apply(resolver, "videoEnabled", payload.videoEnabled) { preferencesRepository.setVideoEnabled(it) }
        apply(resolver, "imagesEnabled", payload.imagesEnabled) { preferencesRepository.setImagesEnabled(it) }
        apply(resolver, "documentsEnabled", payload.documentsEnabled) {
            preferencesRepository.setDocumentsEnabled(it)
        }
        apply(resolver, "downloadAlbumArt", payload.downloadAlbumArt) {
            preferencesRepository.setDownloadAlbumArt(it)
        }
        apply(resolver, "streamsSectionEnabled", payload.streamsSectionEnabled) {
            preferencesRepository.setStreamsSectionEnabled(it)
        }
    }

    private suspend fun applySlideshow(payload: WearSettingsPayload, resolver: FieldGate) {
        apply(resolver, "slideshowEnabled", payload.slideshowEnabled) {
            preferencesRepository.setSlideshowEnabled(it)
        }
        apply(resolver, "slideshowIntervalSeconds", payload.slideshowIntervalSeconds) {
            preferencesRepository.setSlideshowIntervalSeconds(it)
        }
    }

    private suspend fun applyScreen(payload: WearSettingsPayload, resolver: FieldGate) {
        apply(resolver, "viewMode", payload.viewMode) {
            preferencesRepository.setViewMode(WearViewMode.fromNameOrDefault(it))
        }
        apply(resolver, "fileListViewMode", payload.fileListViewMode) {
            preferencesRepository.setFileListViewMode(WearViewMode.fromNameOrDefault(it))
        }
        apply(resolver, "keepScreenAwakeOutsidePlayers", payload.keepScreenAwakeOutsidePlayers) {
            preferencesRepository.setKeepScreenAwakeOutsidePlayers(it)
        }
        apply(resolver, "backgroundMode", payload.backgroundMode) {
            preferencesRepository.setBackgroundMode(WearBackgroundMode.fromNameOrDefault(it))
        }
        apply(resolver, "disableAnimations", payload.disableAnimations) {
            preferencesRepository.setAnimationsDisabled(it)
        }
        apply(resolver, "backgroundPlaybackEnabled", payload.backgroundPlaybackEnabled) {
            preferencesRepository.setBackgroundPlaybackEnabled(it)
        }
    }

    // S1814: the language is a PHONE_ONLY registry entry, so it is inherited rather than merged - the
    // watch never edits it and so can never hold the later value.
    private suspend fun applyLanguage(payload: WearSettingsPayload, gate: FieldGate) {
        val rawLanguage = payload.appLanguage?.takeIf { gate.carries("appLanguage") } ?: return
        val resolvedTag = WearLocaleManager.resolveSupportedTag(context, rawLanguage) ?: return
        preferencesRepository.setAppLanguage(resolvedTag)
        WearLocaleManager.applyLocale(context, resolvedTag)
    }

    /**
     * S1781: a null incoming value means "the other side did not send this" and never "reset it", so an
     * older phone cannot silently undo a choice made on the watch.
     *
     * The setter it calls stamps the field with the current time; the accepted stamp is written over it
     * afterwards, so an applied value does not read as a fresh watch edit and win the next exchange
     * against the very side that sent it.
     */
    private suspend fun <T : Any> apply(
        resolver: FieldGate,
        field: String,
        incoming: T?,
        write: suspend (T) -> Unit
    ) {
        if (incoming == null || !resolver.carries(field)) return
        val decision = resolver.resolve(field)
        if (!decision.apply) return
        write(incoming)
        decision.stampEpochMillis?.let { preferencesRepository.stampSetting(field, it) }
    }

    /**
     * S2462: the merge policy plus the set of keys the phone actually sent.
     *
     * The two travel together because every field consults both, and separating them let a field be
     * resolved without ever asking whether it arrived - which for the six fields that predate
     * nullability is the whole defect: their value is fabricated by Gson when the key is absent, so the
     * existing null test cannot see the absence.
     */
    private class FieldGate(
        private val resolver: WearSettingsMergeResolver,
        private val presentFields: Set<String>
    ) {
        fun carries(field: String): Boolean = field in presentFields

        fun resolve(field: String) = resolver.resolve(field)
    }
}
