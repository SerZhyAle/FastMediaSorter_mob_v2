package com.sza.fastmediasorter.ui.share

import android.app.Activity
import android.view.Menu
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareTarget
import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.ShareTargetIconResolver
import com.sza.fastmediasorter.core.share.ShareTargetOutcome
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.usecase.BuildSendToReceiverListUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single dispatch point for the «Send to..» menu (S0459 ADR-2, ADR-8).
 *
 * Bar press (and programmatic show): if exactly one applicable receiver is gated-in, invoke it
 * directly without showing a sheet (ADR-8). Otherwise open [SendToBottomSheet].
 *
 * Overflow submenu: [buildOverflowSubMenu] always builds a [Menu.addSubMenu] - even for a single
 * receiver - because the overflow already has a hierarchical container (ADR-8, ADR-2).
 *
 * Receiver gating combines the three content gates from [BuildSendToReceiverListUseCase] with the
 * host-capability gate ([ShareTargetHandler.isSupportedBy]) - the latter can only be evaluated here
 * because the use-case is pure domain and has no Activity (ADR-10; Print needs a SharePrintHost).
 *
 * Multi-file hint (ADR-4): receivers that operate on a single file (`!ShareTarget.batchCapable`)
 * receive [ShareableContent.single]; their overflow item appends "· применится к первому файлу"
 * when the selection has more than one file.
 */
@Singleton
class SendToMenuManager @Inject constructor(
    private val buildReceiverList: BuildSendToReceiverListUseCase,
    private val handlers: Map<String, @JvmSuppressWildcards ShareTargetHandler>,
    private val iconResolver: ShareTargetIconResolver,
    private val materializationManager: ShareMaterializationManager,
) {

    /**
     * Resolve the receivers offered on [activity]: the three content gates plus the host-capability
     * gate. Kept in one place so the bar, bottom-sheet, and overflow presentations stay in sync.
     */
    fun receiversFor(
        activity: Activity,
        content: ShareableContent,
        settings: AppSettings,
    ): List<ShareTarget> =
        buildReceiverList(content, settings)
            .filter { handlers[it.id]?.isSupportedBy(activity) != false }
            // S0631: a text-only payload (uris empty, no sourcePath, text present - e.g. a stream link)
            // can only be sent by text-capable receivers. Filtering them out keeps file-only receivers
            // from appearing as dead entries that would silently no-op on a text payload.
            .filter { !content.isTextOnly || it.textCapable }

    /**
     * Show the send-to UI for a bar press or programmatic invocation.
     *
     * Single applicable receiver → invoke directly (ADR-8). Multiple receivers → bottom sheet.
     */
    fun show(
        activity: FragmentActivity,
        content: ShareableContent,
        settings: AppSettings,
        onPickResource: (() -> Unit)? = null,
    ) {
        val receivers = receiversFor(activity, content, settings)
        // S0681: with a pinned «Select resource..» action the sheet must always open so the row is
        // reachable - skip the empty-return and single-receiver direct-dispatch shortcuts.
        if (onPickResource == null) {
            if (receivers.isEmpty()) {
                Timber.i("SendToMenuManager: no applicable receivers for %s", content.mediaType)
                return
            }
            if (receivers.size == 1) {
                dispatch(activity, receivers[0], content)
                return
            }
        }
        SendToBottomSheet.newInstance(content, settings, onPickResource)
            .show(activity.supportFragmentManager, TAG)
    }

    /**
     * Populates [menu] with a «Send to..» sub-menu for the overflow path (ADR-2).
     *
     * Always builds a sub-menu regardless of receiver count so the overflow hierarchy is
     * consistent. [order] places the submenu header at the command's priority position in the
     * overflow. Single-only receivers include a multi-file hint when the selection > 1 (ADR-4).
     */
    fun buildOverflowSubMenu(
        menu: Menu,
        order: Int,
        content: ShareableContent,
        settings: AppSettings,
        activity: Activity,
        onPickResource: (() -> Unit)? = null,
    ) {
        val receivers = receiversFor(activity, content, settings)
        // S0681: the pinned «Select resource..» action keeps the submenu non-empty even with no receivers.
        if (receivers.isEmpty() && onPickResource == null) return

        val subMenu = menu.addSubMenu(Menu.NONE, Menu.NONE, order, R.string.share_to_menu_title)
        // addSubMenu mirrors the parent item's title into the submenu header, which renders as a
        // headerless-looking row identical to the receiver items (same colour, no icon). The user
        // already tapped «Send to..» to open it, so the header is redundant - drop it.
        subMenu.clearHeader()
        subMenu.item.setIcon(R.drawable.ic_share)
        receivers.forEachIndexed { index, target ->
            val isSingleOnly = !target.batchCapable
            // ADR-5: package-backed receivers show the installed app's label, not a brand literal.
            val baseLabel = iconResolver.resolveLabel(target) ?: activity.getString(target.titleRes)
            val label = if (isSingleOnly && content.uris.size > 1) {
                "$baseLabel · ${activity.getString(R.string.share_to_first_file_hint)}"
            } else {
                baseLabel
            }
            val item = subMenu.add(Menu.NONE, Menu.NONE, index, label)
            // S0478: prefer the installed app's launcher icon (most recognisable); fall back to the
            // receiver's own glyph so logical/uninstalled receivers stay distinguishable.
            item.setIcon(
                iconResolver.resolveIcon(target)
                    ?: target.iconRes?.let { ContextCompat.getDrawable(activity, it) }
            )
            item.setOnMenuItemClickListener {
                dispatch(activity, target, content)
                true
            }
        }
        // S0681: append the pinned «Select resource..» action last, after the system-share receiver.
        if (onPickResource != null) {
            val pickItem = subMenu.add(
                Menu.NONE,
                Menu.NONE,
                receivers.size,
                activity.getString(R.string.share_to_pick_resource),
            )
            pickItem.setIcon(R.drawable.ic_resource)
            pickItem.setOnMenuItemClickListener {
                onPickResource()
                true
            }
        }
    }

    /**
     * Run the chosen receiver's send action, applying ADR-4 first-file scoping for single-only
     * receivers and surfacing a failure to the user (ADR: a tap must never silently no-op). Shared
     * by all three presentations (bar-direct, bottom sheet, overflow submenu).
     */
    fun dispatch(activity: Activity, target: ShareTarget, content: ShareableContent) {
        val effectiveContent = if (!target.batchCapable) content.single() else content
        // S0493: a file-consuming receiver on a remote source needs a local copy first. Download it on
        // demand (progress + cancel), then run the receiver with the localized content. Receivers that
        // do not consume the file (e.g. Keep-text) run immediately on any source.
        if (target.requiresLocalFile && effectiveContent.requiresMaterialization) {
            materializationManager.materializeThenRun(activity, effectiveContent) { localized ->
                runReceiver(activity, target, localized)
            }
            return
        }
        runReceiver(activity, target, effectiveContent)
    }

    /**
     * S1884 ADR-4: the send is a coroutine because a receiver may do the work itself and only then
     * know the outcome - the paired watch waits for the watch's answer before it can say anything.
     * Scoped to the host's lifecycle, mirroring [ShareMaterializationManager]: a message about a send
     * belongs to the screen that started it, and a host that is gone must not be shown a toast.
     */
    private fun runReceiver(activity: Activity, target: ShareTarget, content: ShareableContent) {
        val lifecycleOwner = activity as? LifecycleOwner
        if (lifecycleOwner == null) {
            Timber.w("SendToMenuManager: host is not a LifecycleOwner, cannot run receiver %s", target.id)
            Toast.makeText(activity, R.string.share_to_send_failed, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleOwner.lifecycleScope.launch {
            val outcome = try {
                handlers[target.id]?.send(activity, content) ?: ShareTargetOutcome.Failed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "SendToMenuManager: receiver %s failed to send", target.id)
                ShareTargetOutcome.Failed()
            }
            announce(activity, outcome)
        }
    }

    /**
     * Turns the receiver's answer into what the user sees. [ShareTargetOutcome.Launched] stays silent
     * because another app is now on screen and a toast over it would say nothing new.
     */
    private fun announce(activity: Activity, outcome: ShareTargetOutcome) {
        val message = when (outcome) {
            is ShareTargetOutcome.Launched, is ShareTargetOutcome.NotAttempted -> null
            is ShareTargetOutcome.Delivered -> outcome.message
            is ShareTargetOutcome.Failed ->
                activity.getString(outcome.messageRes ?: R.string.share_to_send_failed)
        }
        if (message != null) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "send_to"
    }
}
