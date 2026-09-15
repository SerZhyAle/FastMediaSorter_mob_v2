package com.sza.fastmediasorter.ui.launcher.helpers

import android.net.Uri
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.ExportResourcesToFileUseCase
import com.sza.fastmediasorter.domain.usecase.companion.ExportCompanionConfigUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.ui.launcher.LauncherCellMenuDependencies
import com.sza.fastmediasorter.ui.launcher.LauncherStreamEditDependencies
import com.sza.fastmediasorter.ui.main.helpers.ResourceScanCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * S1424: the domain work behind the long-press menu over a resource or channel cell - the rows that
 * need more than an intent the desktop already runs.
 *
 * S2561: separate from
 * [LauncherHomeViewModel][com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel] for the reason
 * [LauncherSectionCollapseManager] records - that class stands over detekt's `LargeClass` threshold,
 * and while it does, every ticket that touches the file is refused by the scoped detekt gate and
 * cannot close mechanically. These members were chosen because they reach one dependency holder,
 * [LauncherCellMenuDependencies], whose own file states the rule of one holder per surface.
 *
 * [resourceRepository] is the one collaborator that does not come from that holder: the desktop
 * surface owns it, and the menu only reads through it.
 *
 * Reports to the user through [sendMessage] rather than through the ViewModel's event channel: this
 * end needs to say two things and nothing else, so handing it the channel would hand it the right to
 * queue any event at all.
 *
 * @param sendMessage delivers a string resource id to the user as a transient message.
 */
class LauncherCellMenuManager(
    private val dependencies: LauncherCellMenuDependencies,
    private val resourceRepository: ResourceRepository,
    private val observeStreams: ObserveStreamSourcesUseCase,
    private val scope: CoroutineScope,
    private val sendMessage: suspend (Int) -> Unit,
) {

    /**
     * S1424: the long-press menu needs the resource behind a cell to decide which rows it offers -
     * the cell stores the identifier and nothing more. Off the main thread, because a network
     * resource's row can be a disk read.
     */
    suspend fun resourceById(resourceId: Long): MediaResource? = withContext(Dispatchers.IO) {
        resourceRepository.getResourceById(resourceId)
    }

    /**
     * S1424: reached only after the shared confirmation dialog, which the desktop raises rather than
     * copies (strategic 6.2).
     *
     * The cell itself is left where it is on purpose: a cell whose target has gone renders as
     * unavailable, which is what every other vanished target already does, and silently rearranging
     * the desktop under a delete would be a second surprise on top of the first.
     */
    fun deleteResource(resourceId: Long) {
        scope.launch {
            val result = dependencies.deleteResource(resourceId)
            if (result.isFailure) {
                Timber.e(result.exceptionOrNull(), "Deleting resource %d from the desktop failed", resourceId)
            }
            sendMessage(if (result.isSuccess) R.string.resource_deleted else R.string.error_unknown)
        }
    }

    /**
     * S1424: the channel behind a `stream:` cell, or null when it is gone from the catalog. Read from
     * the same flow the picker reads, so the desktop menu cannot describe a channel the streams
     * screen has already dropped.
     */
    // S1832: [cellKey] is the channel's identity, or a row id for a cell written before that ticket.
    // Matched in that order for the same reason the repository resolves it that way - the long-press
    // menu must open on the channel the cell's tap would play, not on a different one.
    suspend fun streamById(cellKey: String): StreamSourceEntity? {
        val sources = observeStreams().first()
        return sources.firstOrNull { it.identityKey == cellKey }
            ?: sources.firstOrNull { it.id == cellKey }
    }

    /**
     * S1500: what backs the desktop's edit-a-channel row. A passthrough property rather than three
     * wrapper methods: this manager adds nothing on the way to those two use cases, and the action
     * manager that reads them already owns the scope the writes need.
     */
    val streamEditDependencies: LauncherStreamEditDependencies
        get() = dependencies.streamEdit

    /** S1424: the pinned block is what decides whether a channel's reorder rows have anywhere to go. */
    suspend fun pinnedStreams(): List<StreamSourceEntity> = observeStreams().first().filter { it.pinned }

    /** S1424: same toggle the streams screen offers - pins to top if loose, unpins if pinned. */
    fun toggleStreamPin(source: StreamSourceEntity) {
        scope.launch {
            if (source.pinned) {
                dependencies.unpinStreamSource(source.id)
            } else {
                dependencies.pinStreamSource(source.id)
            }
        }
    }

    /**
     * S1424: reached only after the shared confirmation dialog (strategic 6.2). The persisted last
     * frame goes with the channel, exactly as it does on the streams screen (S0712) - otherwise a
     * removal from the desktop would leave an orphan file behind.
     */
    fun removeStream(source: StreamSourceEntity) {
        scope.launch {
            dependencies.removeStreamSource(source)
            dependencies.streamFrameStore.remove(source.url)
            // The streams screen needs no message - the row vanishes from its list. A desktop cell
            // does not vanish; it turns unavailable, which alone would not read as "I removed it".
            sendMessage(R.string.launcher_home_channel_removed)
        }
    }

    /**
     * S1424: rescans one resource and reports whether it is reachable. The desktop shows the same
     * "unavailable" message the main window shows; an available resource says nothing, because the
     * scan's whole effect is the refreshed record behind the cell.
     */
    suspend fun scanResource(resource: MediaResource): Boolean = withContext(Dispatchers.IO) {
        val result = dependencies.scanCoordinator.scanAndRefreshSingleResource(resource)
        result is ResourceScanCoordinator.SingleScanResult.Available
    }

    /**
     * S1424: writes the exported resource to [target] and reports whether anything landed there.
     *
     * The caller owns the file, because the cache directory and the share sheet both belong to the
     * Activity; this end owns only the export itself.
     */
    suspend fun exportResource(resourceId: Long, target: Uri): Boolean {
        val result = dependencies.exportResourcesToFile(listOf(resourceId), target)
        return result is ExportResourcesToFileUseCase.ExportResult.Success && result.exported > 0
    }

    /** S1424: the SFTP access payload as a file, or null when it could not be written. */
    suspend fun exportCompanionConfig(resource: MediaResource, includePassword: Boolean): File? =
        dependencies.exportCompanionConfig(resource, includePassword).getOrNull()

    /** S1424: the same access payload as a QR string, or null when it could not be built. */
    suspend fun companionQrPayload(
        resource: MediaResource,
        includePassword: Boolean,
    ): ExportCompanionConfigUseCase.CompanionQrExport? =
        dependencies.exportCompanionConfig.exportQrPayload(resource, includePassword).getOrNull()
}
