package com.sza.fastmediasorter.ui.player.standalone

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.Lazy
import javax.inject.Inject

// S1329: plain classes on purpose, never data classes - detekt's LongParameterList.ignoreDataClasses
// defaults to true, so a data holder would be invisible to the very gate these bundles exist to
// satisfy. Same precedent and same reason as ui/browse/BrowseViewModelDependencies.kt.

/**
 * The remote clients a standalone host forwards but never calls.
 *
 * Grouped rather than passed through, because StandaloneViewManager takes all seven and
 * NetworkFileManager takes them again: threading them one by one through a factory method would
 * cross detekt's function threshold several times over. `credentialsRepository` rides here instead
 * of on the factory itself, and that is what actually removes it from the hosts.
 */
class StandaloneNetworkClients @Inject constructor(
    val smbClient: Lazy<SmbClient>,
    val sftpClient: Lazy<SftpClient>,
    val ftpClient: Lazy<FtpClient>,
    val googleDriveClient: Lazy<GoogleDriveRestClient>,
    val dropboxClient: Lazy<DropboxClient>,
    val oneDriveClient: Lazy<OneDriveRestClient>,
    val credentialsRepository: Lazy<NetworkCredentialsRepository>,
)

/** The per-protocol write handlers and the shared cache, forwarded beside [StandaloneNetworkClients]. */
class StandaloneFileOpHandlers @Inject constructor(
    val smbFileOperationHandler: Lazy<SmbFileOperationHandler>,
    val sftpFileOperationHandler: Lazy<SftpFileOperationHandler>,
    val ftpFileOperationHandler: Lazy<FtpFileOperationHandler>,
    val cloudFileOperationHandler: Lazy<CloudFileOperationHandler>,
    val unifiedCache: Lazy<UnifiedFileCache>,
)

/**
 * What only the Activity can give a file-operations handler: the file it is showing, its result
 * launchers, and what it wants done once an operation finishes.
 *
 * Deliberately not `@Inject` - every member is Activity-scoped, so the host builds this holder and the
 * factory supplies the domain half. The trailing default repeats the manager's own, so a host that
 * wires only some members keeps the behaviour it had.
 */
class StandaloneFileOpsCallbacks(
    val getCurrentMediaFile: () -> MediaFile?,
    val onRenameComplete: (Uri, String) -> Unit,
    val updateAudioMediaItem: (Uri) -> Unit,
    val batchDeleteLauncher: ActivityResultLauncher<IntentSenderRequest>,
    val recoverableDeleteLauncher: ActivityResultLauncher<IntentSenderRequest>,
    val onPickCustomFolderForCopy: () -> Unit = {},
)
