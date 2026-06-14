package com.sza.fastmediasorter.core.capability

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Single source-availability node. Every "may the app touch remote source X right now?" decision -
 * listing, the create-resource entry, in-app selection dialogs, background sync, playback - folds
 * through here so the answer is consistent everywhere.
 *
 * Availability = compile-time support (MediaCapabilities) AND the user toggle (AppSettings). A
 * disabled source becomes invisible and inert but is never deleted: already-added resources simply
 * stop being offered until the user re-enables their source.
 *
 * The flag snapshot is kept current by collecting [SettingsRepository.getSettings] on the
 * application scope, so the hot query path is a plain in-memory read with no DataStore access.
 */
class RemoteSourceAvailabilityGate(
    private val mediaCapabilities: MediaCapabilities,
    settingsRepository: SettingsRepository,
    appScope: CoroutineScope,
) {

    private val snapshotFlow = MutableStateFlow(Snapshot.ALL_ENABLED)

    init {
        appScope.launch {
            settingsRepository.getSettings().collect { settings ->
                snapshotFlow.value = settings.toSnapshot()
            }
        }
    }

    /** True when the source is both compile-supported and user-enabled. */
    fun isEnabled(id: RemoteSourceId): Boolean =
        compileSupported(id) && snapshotFlow.value.userEnabled(id)

    /**
     * Emits the set of currently-available remote sources whenever it changes. Persistent
     * source-dependent UI (e.g. the main tab strip) collects this to rebuild on a runtime toggle,
     * without racing the internal snapshot update.
     */
    fun enabledRemoteSources(): Flow<Set<RemoteSourceId>> =
        snapshotFlow
            .map { snap ->
                RemoteSourceId.entries.filterTo(mutableSetOf()) { compileSupported(it) && snap.userEnabled(it) }
            }
            .distinctUntilChanged()

    /** Resolves a resource to its source id; LOCAL and unmapped resources are always available. */
    fun isEnabled(resource: MediaResource): Boolean {
        val id = resource.toRemoteSourceId() ?: return true
        return isEnabled(id)
    }

    fun anyNetworkEnabled(): Boolean = RemoteSourceId.NETWORK.any { isEnabled(it) }

    fun anyCloudEnabled(): Boolean =
        isCloudGroupSupported() && RemoteSourceId.CLOUD.any { isEnabled(it) }

    fun anyRemoteEnabled(): Boolean = anyNetworkEnabled() || anyCloudEnabled()

    /** Whether the cloud group exists at all on this flavor (compile tier only, ignores toggles). */
    fun isCloudGroupSupported(): Boolean = mediaCapabilities.supportsCloud

    private fun compileSupported(id: RemoteSourceId): Boolean =
        if (id in RemoteSourceId.CLOUD) mediaCapabilities.supportsCloud else true

    private fun MediaResource.toRemoteSourceId(): RemoteSourceId? = when (type) {
        ResourceType.CLOUD -> cloudProvider?.let { RemoteSourceId.fromCloudProvider(it) }
        else -> RemoteSourceId.networkFromResourceType(type)
    }

    private fun AppSettings.toSnapshot(): Snapshot = Snapshot(
        smb = smbEnabled,
        sftp = sftpEnabled,
        ftp = ftpEnabled,
        googleDrive = googleDriveEnabled,
        oneDrive = oneDriveEnabled,
        dropbox = dropboxEnabled,
    )

    private data class Snapshot(
        val smb: Boolean,
        val sftp: Boolean,
        val ftp: Boolean,
        val googleDrive: Boolean,
        val oneDrive: Boolean,
        val dropbox: Boolean,
    ) {
        fun userEnabled(id: RemoteSourceId): Boolean = when (id) {
            RemoteSourceId.SMB -> smb
            RemoteSourceId.SFTP -> sftp
            RemoteSourceId.FTP -> ftp
            RemoteSourceId.GOOGLE_DRIVE -> googleDrive
            RemoteSourceId.ONEDRIVE -> oneDrive
            RemoteSourceId.DROPBOX -> dropbox
        }

        companion object {
            val ALL_ENABLED = Snapshot(
                smb = true,
                sftp = true,
                ftp = true,
                googleDrive = true,
                oneDrive = true,
                dropbox = true,
            )
        }
    }
}
