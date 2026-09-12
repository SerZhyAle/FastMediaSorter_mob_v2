package com.sza.fastmediasorter.wear.domain.repository

/**
 * One device on the Data Layer - this watch, or something it is linked to.
 *
 * [isNearby] is the fact that makes the link section worth reading: it separates a phone in Bluetooth
 * range from one reachable only by relay through the cloud, which the old single "connected" flag could
 * not tell apart and which is exactly the difference a user chasing a sync problem needs (S2165 §2
 * goal 3).
 */
data class WearNodeDescriptor(
    val id: String,
    val displayName: String,
    val isNearby: Boolean
)

/**
 * The device facts the system-information screen reports, named in one place.
 *
 * The collector reaches the platform only through here. This module runs its unit tests on the plain JVM
 * with no Robolectric, where every `android.*` call returns a "not mocked" stub, so a contributor that
 * read `Build` or `ActivityManager` itself could not be tested at all - and the strategic spec asks for a
 * test on exactly that assembly.
 *
 * A fact the device will not answer is null rather than a placeholder string. Only the contributor knows
 * whether a missing line should be dropped or filled in, and a data source that decided for it would put
 * that judgement out of reach of the test.
 */
interface WearSystemInfoDataSource {

    val manufacturer: String?

    val model: String?

    val osVersion: String?

    val apiLevel: Int?

    val appVersion: String?

    val buildNumber: String?

    val totalMemoryBytes: Long?

    val availableMemoryBytes: Long?

    /**
     * What this app itself occupies, and what it may still claim for cache.
     *
     * These replaced a `StatFs` reading of the volume holding the app's directory. That reading
     * measured the whole `/data` partition while its own comment claimed it measured the app's own
     * folder, which is why the section repeated a figure the watch's settings screen already shows
     * (S2165 §4, owner decision §6 question 8).
     */
    val appDataBytes: Long?

    val appCacheBytes: Long?

    val cacheQuotaBytes: Long?

    /**
     * Everything the Data Layer is linked to right now, or null when the lookup failed or timed out.
     *
     * Null and an empty list must stay distinguishable: they are the difference between "the service
     * did not answer" and "no phone is paired", which the report states as two different things.
     */
    suspend fun connectedNodes(): List<WearNodeDescriptor>?

    /** This watch's own entry on the Data Layer - the id a phone-side log names it by. */
    suspend fun localNode(): WearNodeDescriptor?

    /** The capabilities advertised across the pair, by name. */
    suspend fun pairCapabilities(): List<String>?
}
