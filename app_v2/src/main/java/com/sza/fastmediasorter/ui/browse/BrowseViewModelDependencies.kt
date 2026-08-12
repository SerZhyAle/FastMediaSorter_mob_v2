package com.sza.fastmediasorter.ui.browse

import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.core.util.AudioMetadataLoader
import com.sza.fastmediasorter.core.util.CachedMediaMetadataExtractor
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.local.preferences.BrowseManualOrderPrefs
import com.sza.fastmediasorter.data.local.preferences.BrowseStateDataStore
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.repository.CachedFileListRepository
import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.mutation.MutationJournal
import com.sza.fastmediasorter.domain.path.PathNormalizer
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.usecase.AddResourceAsDestinationUseCase
import com.sza.fastmediasorter.domain.usecase.ArchiveFilesUseCase
import com.sza.fastmediasorter.domain.usecase.CleanupOrphanedTempFilesUseCase
import com.sza.fastmediasorter.domain.usecase.CleanupTrashFoldersUseCase
import com.sza.fastmediasorter.domain.usecase.ClearResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.CreateDirectoryUseCase
import com.sza.fastmediasorter.domain.usecase.CreateDrawingUseCase
import com.sza.fastmediasorter.domain.usecase.CreateTextNoteUseCase
import com.sza.fastmediasorter.domain.usecase.DeleteByFileSizeUseCase
import com.sza.fastmediasorter.domain.usecase.DeleteDirectoriesUseCase
import com.sza.fastmediasorter.domain.usecase.ExtractArchiveUseCase
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.MaterializeFavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.domain.usecase.SaveResumeStateUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.domain.usecase.SyncMediaStoreUseCase
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferCoordinator
import dagger.Lazy
import javax.inject.Inject

// S1350: plain classes on purpose, never data classes - detekt's LongParameterList.ignoreDataClasses
// defaults to true, so a data holder would be invisible to the very gate these bundles exist to
// satisfy (same precedent as ui/launcher/LauncherHomeDependencies.kt). One holder per BrowseViewModel
// dependency group, so a dependency added later joins its group instead of the ViewModel constructor.

/** Cloud/remote access - cloud clients, SMB operations and the remote-source availability gate. */
class BrowseRemoteAccessDependencies @Inject constructor(
    val smbClient: Lazy<SmbClient>,
    val smbOperationsUseCase: SmbOperationsUseCase,
    val googleDriveClient: Lazy<GoogleDriveRestClient>,
    val dropboxClient: Lazy<DropboxClient>,
    val oneDriveClient: Lazy<OneDriveRestClient>,
    val remoteSourceGate: RemoteSourceAvailabilityGate,
)

/** Destructive cleanup operations - size-based delete, trash and orphaned-temp-file cleanup. */
class BrowseCleanupUseCases @Inject constructor(
    val deleteByFileSizeUseCase: DeleteByFileSizeUseCase,
    val cleanupTrashFoldersUseCase: CleanupTrashFoldersUseCase,
    val cleanupOrphanedTempFilesUseCase: CleanupOrphanedTempFilesUseCase,
    val deleteDirectoriesUseCase: DeleteDirectoriesUseCase,
    val browseTransferCoordinator: BrowseFileTransferCoordinator,
)

/** Scan -> cache -> enrich -> sync listing pipeline for the visible file list. */
class BrowseContentDiscoveryDependencies @Inject constructor(
    val getResourcesUseCase: GetResourcesUseCase,
    val getMediaFilesUseCase: GetMediaFilesUseCase,
    val mediaScannerFactory: MediaScannerFactory,
    val cachedFileListRepository: CachedFileListRepository,
    val cachedMediaMetadataExtractor: CachedMediaMetadataExtractor,
    val audioMetadataLoader: AudioMetadataLoader,
    val unifiedCache: UnifiedFileCache,
    val syncMediaStoreUseCase: SyncMediaStoreUseCase,
    val resolveScanFilterUseCase: com.sza.fastmediasorter.domain.usecase.ResolveScanFilterUseCase,
)

/** User/resource-scoped state that outlives the screen - favorites, manual order, resume state. */
class BrowsePersistedStateDependencies @Inject constructor(
    val favoritesUseCase: FavoritesUseCase,
    val materializeFavoritesUseCase: MaterializeFavoritesUseCase,
    val statsSink: StatsSink,
    val browseStateDataStore: BrowseStateDataStore,
    val manualOrderPrefs: BrowseManualOrderPrefs,
    val clearResumeStateUseCase: ClearResumeStateUseCase,
    val getResumeStateUseCase: GetResumeStateUseCase,
    val saveResumeStateUseCase: SaveResumeStateUseCase,
)

/** User actions that create or transform content inside the browsed folder. */
class BrowseContentAuthoringUseCases @Inject constructor(
    val updateResourceUseCase: UpdateResourceUseCase,
    val fileOperationUseCase: FileOperationUseCase,
    val createDirectoryUseCase: CreateDirectoryUseCase,
    val createTextNoteUseCase: CreateTextNoteUseCase,
    val createDrawingUseCase: CreateDrawingUseCase,
    val archiveFilesUseCase: ArchiveFilesUseCase,
    val extractArchiveUseCase: ExtractArchiveUseCase,
    val addResourceAsDestinationUseCase: AddResourceAsDestinationUseCase,
)

/** File-mutation infrastructure - unified operation handler, mutation journal, path normalizer. */
class BrowseFileMutationDependencies @Inject constructor(
    val settingsRepository: SettingsRepository,
    val unifiedFileOperationHandler: UnifiedFileOperationHandler,
    val mutationJournal: MutationJournal,
    val pathNormalizer: PathNormalizer,
)
