package com.sza.fastmediasorter.wear.domain.repository

/**
 * Every device fact the system-information screen reports, named in one place.
 *
 * The collector reaches the platform only through here. This module runs its unit tests on the plain JVM
 * with no Robolectric, where every `android.*` call returns a "not mocked" stub, so a collector that read
 * `Build` or `ActivityManager` itself could not be tested at all - and the strategic spec asks for a test
 * on exactly that assembly.
 *
 * A fact the device will not answer is null rather than a placeholder string. Only the collector knows
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

    val totalStorageBytes: Long?

    val availableStorageBytes: Long?

    /**
     * Whether a phone is reachable right now.
     *
     * A one-shot question, matching every other Data Layer caller in this module: no continuous
     * connectivity observer exists on the watch, and this screen is a report to read rather than a monitor
     * to watch.
     */
    suspend fun isPhoneConnected(): Boolean
}
