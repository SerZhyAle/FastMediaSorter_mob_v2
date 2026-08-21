package com.sza.fastmediasorter.wear.domain.repository

private const val MIB = 1024L * 1024L
private const val GIB = MIB * 1024L

/**
 * Mutable rather than constructed: a watch answers eleven separate questions, and a constructor taking
 * all of them is both unreadable at the call site and past detekt's parameter ceiling. Each test names
 * only the fact it is about.
 */
class FakeWearSystemInfoDataSource : WearSystemInfoDataSource {
    override var manufacturer: String? = "Samsung"
    override var model: String? = "Galaxy Watch7"
    override var osVersion: String? = "14"
    override var apiLevel: Int? = 34
    override var appVersion: String? = "0.33"
    override var buildNumber: String? = "330"
    override var totalMemoryBytes: Long? = 2L * GIB
    override var availableMemoryBytes: Long? = 512L * MIB
    override var totalStorageBytes: Long? = 16L * GIB
    override var availableStorageBytes: Long? = 8L * GIB
    var phoneConnected: Boolean = true

    override suspend fun isPhoneConnected(): Boolean = phoneConnected
}
