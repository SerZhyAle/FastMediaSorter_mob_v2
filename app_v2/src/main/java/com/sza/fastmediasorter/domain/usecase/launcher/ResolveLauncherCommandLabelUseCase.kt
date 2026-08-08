package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.launcher.LauncherSectionCatalog
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.LauncherActionCatalog
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.data.launcher.AppShortcutDataSource
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.icon.ResourceIconProvider
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactTarget
import com.sza.fastmediasorter.domain.radio.RadioKind
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.util.getApplicationInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * What a launcher cell shows for its command. Exactly one icon source is set: [iconRes] for our own
 * flat drawables, [iconDrawable] for a picture assembled elsewhere - an installed app's own icon, or
 * (S1289) a resource's composed logo.
 *
 * Not a data class on purpose. Both of those sources hand back a fresh [Drawable] per call and
 * Drawable inherits identity equality, so a generated equals() would make every re-resolution of the
 * same cell unequal - defeating StateFlow conflation and making DiffUtil rebind the whole desktop on
 * every return to Home. Equality is defined over [iconKey] (the icon's source identity) instead.
 */
class LauncherCommandVisual(
    val label: String,
    @DrawableRes val iconRes: Int?,
    val iconDrawable: Drawable? = null,
    /**
     * Stable identity of a Drawable-backed icon - the package it came from, or the resource fields
     * its logo was composed from; null for [iconRes]. Readable because every surface that diffs
     * cells has to compare this instead of the drawable.
     */
    val iconKey: String? = null,
    /**
     * S1176: set when the cell stands for a person, and the string that decides which colour they get.
     * The binder then draws their initials instead of a glyph. Its own field rather than inferring from
     * a null icon, which is also true for an app whose icon could not be loaded.
     */
    val monogramSeed: String? = null,
    /**
     * What a screen reader says instead of the bare [label]. Built where the command's meaning is known,
     * because a cell that only speaks a person's name never says what tapping it will do.
     */
    val spokenLabel: String? = null,
) {

    /** A user-set caption over the same icon. */
    fun withLabel(newLabel: String): LauncherCommandVisual =
        LauncherCommandVisual(newLabel, iconRes, iconDrawable, iconKey, monogramSeed, spokenLabel)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val that = other as? LauncherCommandVisual ?: return false
        return label == that.label && iconRes == that.iconRes && iconKey == that.iconKey &&
            monogramSeed == that.monogramSeed && spokenLabel == that.spokenLabel
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = HASH_MULTIPLIER * result + (iconRes ?: 0)
        result = HASH_MULTIPLIER * result + (iconKey?.hashCode() ?: 0)
        result = HASH_MULTIPLIER * result + (monogramSeed?.hashCode() ?: 0)
        result = HASH_MULTIPLIER * result + (spokenLabel?.hashCode() ?: 0)
        return result
    }

    private companion object {
        const val HASH_MULTIPLIER = 31
    }
}

/**
 * S1441: radio states the caller has already observed, passed in so this resolver stays a pure mapping
 * and never subscribes to a flow of its own.
 *
 * `null` is unknown, never off: a device that refuses the read keeps the on-state glyph, the same rule
 * [com.sza.fastmediasorter.domain.radio.RadioControlContract.state] states.
 */
data class RadioStates(val wifi: Boolean? = null, val bluetooth: Boolean? = null)

/**
 * S0404: resolves the label and icon behind a stored command. Returns null when the command itself
 * is unknown to this build, which the grid renders as an unavailable cell.
 *
 * An uninstalled app is deliberately NOT null: the cell keeps its place with the package name, so
 * the user can see what is gone and remove it, rather than the desktop silently rearranging.
 */
class ResolveLauncherCommandLabelUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val streamSourceRepository: StreamSourceRepository,
    private val scheduledOperationRepository: ScheduledOperationRepository,
    private val resourceIconProvider: ResourceIconProvider,
    private val appShortcutDataSource: AppShortcutDataSource,
) {

    suspend operator fun invoke(
        command: LauncherCellCommand,
        radioStates: RadioStates = RadioStates(),
    ): LauncherCommandVisual? =
        withContext(Dispatchers.IO) {
            when (command) {
                is LauncherCellCommand.App -> appVisual(command.packageName)
                is LauncherCellCommand.Feature -> featureVisual(command.routeKey)
                is LauncherCellCommand.Resource -> resourceVisual(command.resourceId)
                is LauncherCellCommand.Stream -> streamVisual(command.streamId)
                is LauncherCellCommand.OsShortcut -> osVisual(command.targetKey, radioStates)
                is LauncherCellCommand.ScheduledOp -> scheduledOpVisual(command.operationId)
                // S1170: a favourite file is produced by a gadget row tap, never stored in a cell's
                // target, so nothing asks the grid to draw it. Answering with the file's own name still
                // costs nothing and keeps the resolver total, which is what makes this `when` a
                // compile-time check that a new command kind was considered everywhere.
                is LauncherCellCommand.FavoriteFile -> favoriteFileVisual(command.filePath)
                // S1176: the only command that already carries its own label. The snapshot IS the
                // answer - re-deriving one would mean reading the address book, which is exactly the
                // permission this feature exists to avoid (ADR-1).
                is LauncherCellCommand.Contact -> contactVisual(command.target)
                is LauncherCellCommand.LauncherAction -> launcherActionVisual(command.actionKey)
                is LauncherCellCommand.PinnedShortcut -> pinnedShortcutVisual(command)
                is LauncherCellCommand.Section -> sectionVisual(command.sectionKey)
            }
        }

    /**
     * `iconRes` is null on purpose, exactly as it is for an installed app: the picture belongs to the
     * person, and the cell binder draws their photo or a monogram (Phase 03). A generic glyph here would
     * win the race and every contact would look alike.
     *
     * A contact with no name is real - a number-only record - so it falls back to the number, and only
     * then to a generic caption. The label is never empty, because it is the only thing that tells two
     * monograms apart.
     */
    private fun contactVisual(target: LauncherContactTarget): LauncherCommandVisual? {
        if (!target.isUsable) return null
        val label = target.displayName
            .ifBlank { target.phoneNumber }
            .ifBlank { context.getString(R.string.launcher_contact_cell_unnamed) }
        return LauncherCommandVisual(
            label = label,
            iconRes = null,
            // The lookup key first: it survives the person being renamed, so their colour does too.
            monogramSeed = target.lookupKey.ifBlank { target.phoneNumber }.ifBlank { label },
            spokenLabel = context.getString(spokenLabelRes(target.action), label),
        )
    }

    /**
     * "Call: Ivan", not "Call Ivan". Russian and Ukrainian would need the name in the dative here, and
     * nothing can decline an arbitrary contact name - so every locale gets a form that stays correct
     * whatever the name is, rather than one that reads well only in English.
     */
    @StringRes
    private fun spokenLabelRes(action: LauncherContactAction): Int = when (action) {
        LauncherContactAction.PROFILE -> R.string.launcher_contact_a11y_profile
        LauncherContactAction.DIAL -> R.string.launcher_contact_a11y_dial
        LauncherContactAction.MESSAGE -> R.string.launcher_contact_a11y_message
        LauncherContactAction.SMS -> R.string.launcher_contact_a11y_sms
    }

    private fun favoriteFileVisual(filePath: String): LauncherCommandVisual = LauncherCommandVisual(
        label = filePath.substringAfterLast('/').ifEmpty { filePath },
        iconRes = R.drawable.ic_widget_favorites,
    )

    private suspend fun scheduledOpVisual(operationId: Long): LauncherCommandVisual? {
        val operation = scheduledOperationRepository.getById(operationId) ?: return null
        val source = resourceRepository.getResourceById(operation.sourceResourceId)?.name
            ?: operation.sourceResourceId.toString()
        val target = operation.targetResourceId
            ?.let { resourceRepository.getResourceById(it)?.name ?: it.toString() }
            ?: "-"
        return LauncherCommandVisual(
            label = context.getString(
                R.string.launcher_cell_scheduled_op_label,
                operation.operationType.name,
                source,
                target,
            ),
            iconRes = R.drawable.ic_schedule,
        )
    }

    private fun appVisual(packageName: String): LauncherCommandVisual {
        val packageManager = context.packageManager
        return runCatching {
            val info = packageManager.getApplicationInfoCompat(packageName)
            LauncherCommandVisual(
                label = packageManager.getApplicationLabel(info).toString(),
                iconRes = null,
                iconDrawable = packageManager.getApplicationIcon(info),
                iconKey = packageName,
            )
        }.getOrElse {
            Timber.i("Launcher: %s is not installed, cell keeps a placeholder", packageName)
            LauncherCommandVisual(
                label = packageName,
                iconRes = R.drawable.ic_launcher_mode,
            )
        }
    }

    /**
     * S1205: never null, exactly like [appVisual] and for the same reason.
     *
     * A pinned shortcut can be away for a while - its owner is updating, or crashed - so the cell keeps
     * the caption captured when the request was accepted and swaps in the placeholder glyph, which is
     * what marks it inactive. Removing it stays the user's decision; a cell that vanished on its own
     * could not be brought back, because only the publishing app can offer the pin again.
     */
    private fun pinnedShortcutVisual(command: LauncherCellCommand.PinnedShortcut): LauncherCommandVisual {
        val live = appShortcutDataSource.pinned(command.packageName, command.shortcutId)
        return if (live == null) {
            LauncherCommandVisual(label = command.label, iconRes = R.drawable.ic_launcher_mode)
        } else {
            LauncherCommandVisual(
                label = live.label.ifBlank { command.label },
                iconRes = null,
                iconDrawable = live.icon,
                iconKey = "${command.packageName}#${command.shortcutId}",
            )
        }
    }

    private fun featureVisual(routeKey: String): LauncherCommandVisual? {
        val route = InternalRouteCatalog.byKey(routeKey) ?: return null
        return LauncherCommandVisual(
            label = context.getString(route.labelRes),
            iconRes = route.iconRes,
        )
    }

    private suspend fun resourceVisual(resourceId: Long): LauncherCommandVisual? {
        val resource = resourceRepository.getResourceById(resourceId) ?: return null
        val icon = resourceIconProvider.iconFor(resource)
        Timber.d("S1289: composed launcher icon for resource %s, key=%s", resource.name, icon.key)
        // S1414: on the desktop the caption is the only thing telling two cells with the same picture
        // apart, so a resource saved without a name falls back to the folder it points at rather than
        // to an empty caption.
        val label = resource.name
            .ifBlank { resource.path.trimEnd('/').substringAfterLast('/') }
            .ifBlank { resource.path }
        return LauncherCommandVisual(
            label = label,
            iconRes = null,
            iconDrawable = icon.drawable,
            iconKey = icon.key,
        )
    }

    private suspend fun streamVisual(streamId: String): LauncherCommandVisual? {
        val source = streamSourceRepository.getById(streamId) ?: return null
        // S1414: every stream cell draws the same glyph, so a nameless stream would be indistinguishable
        // from the next one. The host of its address is still a distinguishing mark.
        val label = source.title
            .ifBlank { source.url.toUri().host.orEmpty() }
            .ifBlank { source.url }
        return LauncherCommandVisual(
            label = label,
            iconRes = R.drawable.ic_cast,
        )
    }

    /**
     * S1402: null for an unknown key rather than a placeholder caption - the grid already draws an
     * unresolvable shortcut as an unavailable cell the user can remove, and inventing a caption would
     * hide a cell that leads nowhere.
     */
    private fun launcherActionVisual(actionKey: String): LauncherCommandVisual? {
        val action = LauncherActionCatalog.byKey(actionKey) ?: return null
        return LauncherCommandVisual(
            label = context.getString(action.labelRes),
            iconRes = action.iconRes,
        )
    }

    /**
     * S1428: null for an unknown key, exactly like [launcherActionVisual] - the desktop already draws an
     * unresolvable cell as unavailable, and inventing a caption would leave a header naming nothing.
     *
     * `iconRes` stays null because the header is a caption, not a cell with a glyph.
     */
    private fun sectionVisual(sectionKey: String): LauncherCommandVisual? {
        val section = LauncherSectionCatalog.byKey(sectionKey) ?: return null
        return LauncherCommandVisual(label = context.getString(section.labelRes), iconRes = null)
    }

    private fun osVisual(
        targetKey: String,
        radioStates: RadioStates = RadioStates(),
    ): LauncherCommandVisual? {
        val target = OsShortcutCatalog.byKey(targetKey) ?: return null
        // This resolver is launcher-only, so relabel the OS Settings cell "Android settings" to avoid
        // confusion with the app's own settings; the shared OsShortcutCatalog label stays for the panel.
        val labelRes = if (targetKey == OsShortcutCatalog.KEY_SETTINGS) {
            R.string.launcher_menu_android_settings
        } else {
            target.labelRes
        }
        return LauncherCommandVisual(
            label = context.getString(labelRes),
            iconRes = offStateIconRes(target.radio, radioStates) ?: target.iconRes,
        )
    }

    /** Null keeps the catalog's own glyph, which covers both a non-radio target and an unknown state. */
    @DrawableRes
    private fun offStateIconRes(radio: RadioKind?, states: RadioStates): Int? = when (radio) {
        RadioKind.WIFI -> R.drawable.ic_wifi_off.takeIf { states.wifi == false }
        RadioKind.BLUETOOTH -> R.drawable.ic_bluetooth_off.takeIf { states.bluetooth == false }
        null -> null
    }
}
