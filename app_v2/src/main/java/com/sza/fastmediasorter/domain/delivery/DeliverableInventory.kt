package com.sza.fastmediasorter.domain.delivery

import kotlinx.coroutines.flow.Flow

/**
 * Persists the registry/inventory of available deliverable items (modules and language data).
 * Displays statuses, sizes, and manages installation/uninstallation (S0386 Phase 08).
 */
sealed class ExtensionItem {
    abstract val id: String
    abstract val displayNameRes: Int
    abstract val descriptionRes: Int
    abstract val sizeLabel: String
    abstract val statusFlow: Flow<ExtensionStatus>
    
    data class Module(
        override val id: String,
        val set: DeliverableSet,
        override val displayNameRes: Int,
        override val descriptionRes: Int,
        override val sizeLabel: String,
        override val statusFlow: Flow<ExtensionStatus>
    ) : ExtensionItem()

    data class LanguageData(
        override val id: String,
        val languageCode: String,
        override val displayNameRes: Int,
        override val descriptionRes: Int,
        override val sizeLabel: String,
        override val statusFlow: Flow<ExtensionStatus>
    ) : ExtensionItem()
}

sealed class ExtensionStatus {
    object Installed : ExtensionStatus()
    object NotInstalled : ExtensionStatus()
    data class Downloading(val percent: Int) : ExtensionStatus()
    data class Failed(val error: String) : ExtensionStatus()
}

interface DeliverableInventory {
    fun getExtensions(): List<ExtensionItem>
    fun download(item: ExtensionItem): Flow<DownloadProgress>
    suspend fun uninstall(item: ExtensionItem)
}
