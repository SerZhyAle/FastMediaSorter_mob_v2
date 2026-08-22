package com.sza.fastmediasorter.ui.browse.managers

import android.graphics.Bitmap
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.share.handlers.OpenInShareTargetHandler
import com.sza.fastmediasorter.core.storage.RestrictedTreeTargetPolicy
import com.sza.fastmediasorter.core.util.AudioMetadataLoader
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.databinding.ActivityBrowseBinding
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import com.sza.fastmediasorter.ui.browse.helpers.BrowseFileOverflowMenuManager
import com.sza.fastmediasorter.ui.browse.helpers.ReviewRequestManager
import com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferCoordinator
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import dagger.Lazy

/**
 * S1269: cohesive parameter bundles for [BrowseManagerInitializer]. The 37 host-supplied constructor
 * inputs kept re-keying the detekt LongParameterList baseline entry on every addition, surfacing the
 * whole constructor as new debt for unrelated tickets (S1252, S1316). Grouping is pure re-plumbing:
 * every value still originates at the same call site, so instances are identical by construction and
 * DI scoping is untouched. Each bundle stays under the 10-parameter constructor threshold with
 * headroom, so the next added dependency does not re-arm the mechanism.
 */

/** Views, view model and scope of the hosting [BrowseActivity] - things Hilt cannot supply. */
data class BrowseHostUi(
    val activity: BrowseActivity,
    val binding: ActivityBrowseBinding,
    val viewModel: BrowseViewModel,
    val lifecycleScope: LifecycleCoroutineScope,
)

/** Lazy network/cloud protocol clients used by browse managers for remote resources. */
data class BrowseRemoteClients(
    val smbClient: Lazy<SmbClient>,
    val sftpClient: Lazy<SftpClient>,
    val ftpClient: Lazy<FtpClient>,
    val googleDriveClient: Lazy<GoogleDriveRestClient>,
    val dropboxClient: Lazy<DropboxClient>,
    val oneDriveClient: Lazy<OneDriveRestClient>,
)

/**
 * Domain-layer services. Built by [BrowseHostFactory], which injects the first four itself
 * (S1329: repositories and use cases stay off `BrowseActivity`).
 */
data class BrowseDomainServices(
    val fileOperationUseCase: FileOperationUseCase,
    val getDestinationsUseCase: GetDestinationsUseCase,
    val settingsRepository: SettingsRepository,
    val credentialsRepository: Lazy<NetworkCredentialsRepository>,
    val unifiedFileOperationHandler: UnifiedFileOperationHandler,
    val audioMetadataLoader: AudioMetadataLoader,
    val unifiedFileCache: UnifiedFileCache,
    val restrictedTreeTargetPolicy: RestrictedTreeTargetPolicy,
    val mediaCapabilities: MediaCapabilities,
)

/** Sibling managers and handlers the host constructs or injects and shares with the initializer. */
data class BrowseHostManagers(
    val passwordManager: ResourcePasswordManager,
    val resourceOpsMenuManager: ResourceOpsMenuManager,
    val browseFileOverflowMenuManager: BrowseFileOverflowMenuManager,
    val launcherManager: BrowseLauncherManager,
    val browseApkTileBadgeBinder: BrowseApkTileBadgeBinder,
    val reviewRequestManager: ReviewRequestManager,
    val browseTransferCoordinator: BrowseFileTransferCoordinator,
    val sendToMenuManager: SendToMenuManager,
    val openInShareTargetHandler: OpenInShareTargetHandler,
)

/**
 * Host-owned UI callbacks: thumbnail preference accessors plus the S0783 favicon sprite-atlas hooks
 * for STREAM favorites rows. No-op favicon defaults keep non-stream construction paths unaffected.
 */
data class BrowseUiHooks(
    val showVideoThumbnailsGetter: () -> Boolean,
    val showPdfThumbnailsGetter: () -> Boolean,
    val updateShowVideoThumbnails: (Boolean) -> Unit,
    val updateShowPdfThumbnails: (Boolean) -> Unit,
    val faviconResolver: (String) -> Int? = { null },
    val faviconTileLoader: suspend (Int) -> Bitmap? = { null },
)

/**
 * Flavor- and launch-dependent inputs. The action set arrives via multibinding so market builds
 * stay feature-agnostic; the passthrough provider exists only in the vr flavor.
 */
data class BrowseFlavorHooks(
    val isSkipAvailabilityCheck: Boolean,
    val passthroughProvider: BrowsePassthroughCaptureProvider? = null,
    val binaryFileMenuActions: Set<@JvmSuppressWildcards BrowseBinaryFileMenuAction> = emptySet(),
)
