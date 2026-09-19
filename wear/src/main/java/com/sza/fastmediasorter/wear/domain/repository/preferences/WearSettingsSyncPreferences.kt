package com.sza.fastmediasorter.wear.domain.repository.preferences

import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendPolicy
import kotlinx.coroutines.flow.Flow

/** What the watch records about its own edits, and the two settings the exchange itself needs. */
interface WearSettingsSyncPreferences {

    /**
     * S1862: whether a finished voice note leaves the watch on its own. Absent reads as
     * [VoiceNoteSendPolicy.AUTOMATIC] - a watch recording is meant for the phone by default.
     */
    val voiceNoteSendPolicy: Flow<VoiceNoteSendPolicy>
    suspend fun setVoiceNoteSendPolicy(policy: VoiceNoteSendPolicy)

    /**
     * S1961: whether POST_NOTIFICATIONS has already been asked for once.
     *
     * Set after the first ask regardless of the answer, because a refusal simply returns the
     * behaviour the watch had before this ticket - it is a valid choice, not an error to retry. It is
     * kept here rather than derived from `shouldShowRequestPermissionRationale`, which cannot tell a
     * first run apart from a permanent refusal.
     */
    val notificationPermissionAsked: Flow<Boolean>
    suspend fun setNotificationPermissionAsked(asked: Boolean)

    /**
     * S3186: whether the first-run welcome and permission walk has been finished.
     *
     * Watch-local on purpose and absent from `WearSettingsRegistry`: the phone has its own welcome, and
     * a synced value would let one device's answer skip or replay the other's first run.
     */
    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    /**
     * S2093: contract field name to epoch-millis of that field's last change on this watch.
     *
     * Every setter that backs a `WearSettingsRegistry` entry stamps itself, so the two-way exchange can
     * tell a watch edit apart from a phone edit without any caller having to remember to record one.
     * A field absent from the map has never been changed on this watch, which the merge reads as
     * "the other side's value wins" rather than as a zero timestamp.
     */
    val settingTimestamps: Flow<Map<String, Long>>

    /**
     * S2093: records [field] as last changed at [atEpochMillis], in this watch's own time base.
     *
     * Called by the merge after it accepts an incoming value, with the sender's stamp corrected for
     * clock skew - stamping "now" instead would make every applied value look like a fresh local edit
     * and win the next exchange against the phone that just sent it.
     */
    suspend fun stampSetting(field: String, atEpochMillis: Long)

    /**
     * S2093: epoch-millis the watch and the phone last brought their settings to one state, or 0 when
     * they never have - which the root settings screen shows as "never synced" rather than as a date
     * in 1970.
     */
    val lastSettingsSyncAt: Flow<Long>

    suspend fun markSettingsSynced(atEpochMillis: Long)
}
