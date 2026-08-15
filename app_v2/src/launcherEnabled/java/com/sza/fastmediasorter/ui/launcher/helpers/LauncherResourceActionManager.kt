package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.menu.MenuActionSurface
import com.sza.fastmediasorter.core.menu.ResourceActionCatalog
import com.sza.fastmediasorter.core.menu.ResourceMenuAction
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceShareFormat
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.usecase.companion.ExportCompanionConfigUseCase
import com.sza.fastmediasorter.ui.companionimport.qr.CompanionQrShareActivity
import com.sza.fastmediasorter.ui.icon.ResourceIconComposer
import com.sza.fastmediasorter.ui.main.ResourceAdapter
import com.sza.fastmediasorter.ui.main.helpers.MainSftpShareManager
import com.sza.fastmediasorter.ui.main.helpers.ResourceDeleteConfirmation
import com.sza.fastmediasorter.ui.main.helpers.ResourcePasswordManager
import com.sza.fastmediasorter.ui.main.helpers.ResourceShareIntents
import com.sza.fastmediasorter.ui.main.helpers.ResourceVrCinemaLaunchManager
import com.sza.fastmediasorter.ui.resourceeditor.ResourceEditorActivity
import com.sza.fastmediasorter.util.VirtualPathUtils
import com.sza.fastmediasorter.util.showBoundToHost
import com.sza.fastmediasorter.widget.ResourceShortcutPinManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * S1424: the suspending work the desktop's resource menu needs from the ViewModel.
 *
 * Bundled rather than passed as four more constructor lambdas, which would put
 * [LauncherResourceActionManager] at detekt's parameter ceiling for no gain in clarity.
 */
class LauncherResourceOperations(
    val scan: suspend (MediaResource) -> Boolean,
    val export: suspend (resourceId: Long, target: Uri) -> Boolean,
    val exportCompanionConfig: suspend (MediaResource, includePassword: Boolean) -> File?,
    val companionQrPayload: suspend (
        MediaResource,
        includePassword: Boolean,
    ) -> ExportCompanionConfigUseCase.CompanionQrExport?,
)

/**
 * S1424: carries out a resource's menu action from the launcher desktop, where there is no main
 * window to route it through (strategic 5.1.2).
 *
 * Every action here is expressed as an intent, as a manager taking a plain `AppCompatActivity`, or
 * as a command the desktop already executes - which is exactly why these transfer without the main
 * window while the three in [DEFERRED] do not yet (strategic 4).
 *
 * Not injected: like [LauncherAppActionMenuManager] it is built by its host and bound to that host's
 * lifetime, so no dialog or toast can outlive the surface that raised it.
 */
class LauncherResourceActionManager(
    private val activity: AppCompatActivity,
    private val scope: CoroutineScope,
    private val loadResource: suspend (Long) -> MediaResource?,
    private val runCommand: (LauncherCellCommand) -> Unit,
    private val deleteResource: (Long) -> Unit,
    private val shortcutPinManager: ResourceShortcutPinManager,
    private val vrCinema: ResourceVrCinemaLaunchManager,
    private val operations: LauncherResourceOperations,
) {

    // Built on first use: most long presses never reach a PIN-protected resource.
    private val passwordManager by lazy { ResourcePasswordManager(activity, activity.layoutInflater) }

    /**
     * Menu rows for the resource behind [resourceId], or an empty list when it is gone - a cell whose
     * resource was deleted elsewhere keeps behaving like a cell with nothing to expand.
     */
    suspend fun rowsFor(resourceId: Long): List<LauncherAppMenuRow> {
        val resource = loadResource(resourceId) ?: return emptyList()
        return ResourceActionCatalog
            .actionsFor(MenuActionSurface.LAUNCHER_DESKTOP, factsFor(resource), ::supports)
            .map { action ->
                LauncherAppMenuRow.Action(activity.getString(action.labelRes), action.iconRes) {
                    run(action, resource)
                }
            }
    }

    /**
     * Whether this host can finish [action]. The catalog asks before offering a row, so an action
     * still waiting for its wiring is left out instead of shown dead (strategic ADR-2).
     */
    fun supports(action: ResourceMenuAction): Boolean = action !in DEFERRED

    private fun run(action: ResourceMenuAction, resource: MediaResource) {
        when (action) {
            ResourceMenuAction.OPEN -> open(resource, LauncherResourceMode.BROWSE)
            ResourceMenuAction.LAUNCH_PLAYER -> open(resource, LauncherResourceMode.SLIDESHOW)
            ResourceMenuAction.OPEN_IN_VR_CINEMA -> vrCinema.launch(resource)
            ResourceMenuAction.ADD_TO_HOME_SCREEN -> pinShortcut(resource)
            ResourceMenuAction.EDIT -> edit(resource)
            ResourceMenuAction.COPY -> copy(resource)
            ResourceMenuAction.EXPORT -> confirmExport(resource)
            ResourceMenuAction.SHARE_SFTP_ACCESS -> shareSftpAccess(resource)
            ResourceMenuAction.SCAN -> scan(resource)
            ResourceMenuAction.DELETE -> confirmDelete(resource)
            // Unreachable while [supports] and the desktop surface gate the rows; a warning rather
            // than silence, because a row that quietly does nothing is the defect this ticket removes.
            else -> Timber.w("Launcher offered resource action %s, which it cannot run", action)
        }
    }

    /** Reuses the desktop's own command path, so a menu open behaves exactly like a tap on the cell. */
    private fun open(resource: MediaResource, mode: LauncherResourceMode) {
        runCommand(LauncherCellCommand.Resource(resource.id, mode))
    }

    private fun edit(resource: MediaResource) {
        startBehindPin(resource, ResourceEditorActivity.createEditIntent(activity, resource.id))
    }

    private fun copy(resource: MediaResource) {
        startBehindPin(resource, ResourceEditorActivity.createCopyIntent(activity, resource.id))
    }

    /**
     * A PIN-protected resource asks for its PIN before the editor opens, exactly as the main window
     * does - the desktop must not become the cheap way around it.
     */
    private fun startBehindPin(resource: MediaResource, intent: Intent) {
        if (resource.accessPin.isNullOrBlank()) {
            activity.startActivity(intent)
            return
        }
        passwordManager.checkResourcePin(resource) { activity.startActivity(intent) }
    }

    /** The same request, icon and message the main window's "Add to home screen" produces. */
    private fun pinShortcut(resource: MediaResource) {
        val icon = ResourceIconComposer.compose(activity, resource)
        val message = when (shortcutPinManager.requestPin(resource.id, resource.name, icon)) {
            ResourceShortcutPinManager.PinResult.Requested -> R.string.resource_shortcut_created
            ResourceShortcutPinManager.PinResult.Unsupported -> R.string.resource_shortcut_unsupported
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    private fun confirmDelete(resource: MediaResource) {
        ResourceDeleteConfirmation.show(activity, resource.name) { deleteResource(resource.id) }
    }

    /**
     * The same warning the main window shows before an export: the file can carry credentials, and
     * the share sheet after it hands the file to somebody else.
     */
    private fun confirmExport(resource: MediaResource) {
        if (activity.isFinishing || activity.isDestroyed) return
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.resource_share_export_title)
            .setMessage(R.string.resource_share_credentials_warning)
            .setPositiveButton(android.R.string.ok) { _, _ -> runExport(resource) }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundToHost(activity)
    }

    private fun runExport(resource: MediaResource) {
        scope.launch {
            val file = File(activity.cacheDir, exportFileNameOf(resource))
            val shared = operations.export(resource.id, Uri.fromFile(file)) && ResourceShareIntents.share(
                activity,
                file.absolutePath,
                ResourceShareFormat.MIME_TYPE,
                R.string.resource_share_export_title,
            )
            if (!shared) {
                toast(R.string.resource_share_export_failed)
            }
        }
    }

    /**
     * Mirrors the main window's own sanitising, so the same resource exports under the same file name
     * whichever surface asked for it.
     */
    private fun exportFileNameOf(resource: MediaResource): String {
        val safeName = resource.name.replace(UNSAFE_FILE_NAME, "_").ifBlank { FALLBACK_EXPORT_NAME }
        return "$safeName.${ResourceShareFormat.EXTENSION}"
    }

    /** The same two-transport dialog the main window raises; it already takes a plain Activity. */
    private fun shareSftpAccess(resource: MediaResource) {
        MainSftpShareManager(activity).show(resource) { includePassword, method ->
            scope.launch { deliverSftpAccess(resource, includePassword, method) }
        }
    }

    private suspend fun deliverSftpAccess(
        resource: MediaResource,
        includePassword: Boolean,
        method: MainSftpShareManager.ShareMethod,
    ) {
        val delivered = when (method) {
            MainSftpShareManager.ShareMethod.FILE -> shareCompanionFile(resource, includePassword)
            MainSftpShareManager.ShareMethod.QR -> showCompanionQr(resource, includePassword)
        }
        if (!delivered) {
            toast(R.string.sftp_share_export_failed)
        }
    }

    private suspend fun shareCompanionFile(resource: MediaResource, includePassword: Boolean): Boolean {
        val file = operations.exportCompanionConfig(resource, includePassword) ?: return false
        return ResourceShareIntents.share(
            activity,
            file.absolutePath,
            ResourceShareIntents.COMPANION_CONFIG_MIME,
            R.string.sftp_share_export_title,
        )
    }

    private suspend fun showCompanionQr(resource: MediaResource, includePassword: Boolean): Boolean {
        val export = operations.companionQrPayload(resource, includePassword) ?: return false
        activity.startActivity(
            CompanionQrShareActivity.createIntent(
                activity,
                export.payload,
                resource.name,
                export.passwordIncluded,
            ),
        )
        return true
    }

    /**
     * An available resource says nothing: the scan's whole effect is the refreshed record behind the
     * cell, and a "still there" message on every rescan would be noise.
     */
    private fun scan(resource: MediaResource) {
        scope.launch {
            if (!operations.scan(resource)) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.resource_unavailable_name, resource.name),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun toast(@StringRes messageRes: Int) {
        Toast.makeText(activity, messageRes, Toast.LENGTH_LONG).show()
    }

    /**
     * `isNewWindowAvailable` stays false on purpose: the desktop excludes "open in a separate window"
     * outright (strategic 5.2), so reading the device's real multi-window state here would decide
     * nothing and only suggest the row could appear.
     */
    private fun factsFor(resource: MediaResource) = ResourceActionCatalog.Facts(
        isPredefinedVirtual = resource.path in VirtualPathUtils.ALL_VIRTUAL_PATHS,
        isSftp = resource.type == ResourceType.SFTP,
        isQuickSlideshowEligible = ResourceAdapter.isQuickSlideshowEligible(resource),
        isNewWindowAvailable = false,
        isVrCinemaAvailable = vrCinema.isAvailable,
    )

    private companion object {

        /** Same sanitising as the main window's exporter - anything outside this set becomes `_`. */
        val UNSAFE_FILE_NAME = Regex("[^A-Za-z0-9._-]")

        const val FALLBACK_EXPORT_NAME = "resource"

        /**
         * Empty since Phase 04: every action the desktop surface offers is now carried out here. It
         * stays as the seam a future action arrives through - absent until wired, never dead
         * (strategic ADR-2).
         */
        val DEFERRED = emptySet<ResourceMenuAction>()
    }
}
