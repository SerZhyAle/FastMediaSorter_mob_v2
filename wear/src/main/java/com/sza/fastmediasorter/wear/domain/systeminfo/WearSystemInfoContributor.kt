package com.sza.fastmediasorter.wear.domain.systeminfo

import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection

/**
 * One area of the system-information report, answering for itself.
 *
 * The contract used to be one property per fact on a single data source. Eleven facts fit that shape;
 * the head group S2165 selected does not, because thirty facts would mean thirty interface members,
 * thirty overrides and thirty fields in every test double. A contributor hands over finished sections
 * instead, so a group of facts costs one class rather than one member each - and it is the only seam
 * through which the `noLegal` build can add sections without a flavor flag in shared code, the watch
 * module declaring no `buildConfigField` at all.
 *
 * A contributor swallows its own failures and returns an empty list. The report is opened when
 * something already looks wrong, so one area the watch will not answer must not cost the user the rest.
 */
interface WearSystemInfoContributor {

    /**
     * Where this contributor's sections sit in the finished report.
     *
     * Explicit because the contributors arrive as a multibound `Set`, which has no iteration order at
     * all: without this the report would reshuffle itself between builds. The order is observable
     * behaviour and a test asserts it.
     */
    val order: Int

    suspend fun sections(): List<WearSystemInfoSection>
}

/** The one place the report's section order is decided. */
object WearSystemInfoOrder {
    const val DEVICE = 10
    const val APP = 20
    const val HEALTH = 30
    const val SENSORS = 40
    const val RADIO = 50
    const val MEMORY = 60
    const val STORAGE = 70
    const val PHONE_LINK = 80

    /** Contributed only by `wear/src/noLegal`; in the `standard` build nothing occupies this slot. */
    const val EXTENDED = 90
}
