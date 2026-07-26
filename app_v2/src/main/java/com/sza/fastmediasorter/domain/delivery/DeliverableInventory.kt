package com.sza.fastmediasorter.domain.delivery

import kotlinx.coroutines.flow.Flow

/**
 * Persists the registry/inventory of available deliverable items (modules and language data).
 * Displays statuses, sizes, and manages installation/uninstallation (S0386 Phase 08).
 */
/**
 * Logical grouping the Extensions Manager screen renders as sections (S0386 Phase 11): OCR engines +
 * OCR language data, translation module + translation language packs, and media-playback assets.
 */
enum class ExtensionSection { OCR, TRANSLATION, MEDIA_PLAYBACK, STREAMS }

sealed class ExtensionItem {
    abstract val id: String
    abstract val displayNameRes: Int
    abstract val descriptionRes: Int
    abstract val sizeLabel: String
    abstract val section: ExtensionSection
    abstract val statusFlow: Flow<ExtensionStatus>

    data class Module(
        override val id: String,
        val set: DeliverableSet,
        override val displayNameRes: Int,
        override val descriptionRes: Int,
        override val sizeLabel: String,
        override val section: ExtensionSection,
        override val statusFlow: Flow<ExtensionStatus>
    ) : ExtensionItem()

    data class LanguageData(
        override val id: String,
        val languageCode: String,
        override val displayNameRes: Int,
        override val descriptionRes: Int,
        override val sizeLabel: String,
        override val section: ExtensionSection,
        override val statusFlow: Flow<ExtensionStatus>
    ) : ExtensionItem()

    // S0575: a downloadable catalog of stream sources. Not a DeliverableSet - fetched directly via
    // ImportStreamCatalogUseCase, so it carries no `set` field.
    data class Catalog(
        override val id: String,
        override val displayNameRes: Int,
        override val descriptionRes: Int,
        override val sizeLabel: String,
        override val section: ExtensionSection,
        override val statusFlow: Flow<ExtensionStatus>
    ) : ExtensionItem()
}

sealed class ExtensionStatus {
    object Installed : ExtensionStatus()
    object NotInstalled : ExtensionStatus()

    /**
     * S1200: the payload is present and usable, but it was installed against different pins than this
     * build carries - a newer (or simply other) version is what the app now expects.
     */
    object UpdateAvailable : ExtensionStatus()
    data class Downloading(val percent: Int) : ExtensionStatus()
    data class Failed(val error: String) : ExtensionStatus()
}

interface DeliverableInventory {
    fun getExtensions(): List<ExtensionItem>
    fun download(item: ExtensionItem): Flow<DownloadProgress>
    suspend fun uninstall(item: ExtensionItem)
}
