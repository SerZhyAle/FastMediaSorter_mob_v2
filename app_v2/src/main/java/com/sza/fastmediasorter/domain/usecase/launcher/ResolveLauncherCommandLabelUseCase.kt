package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.core.panel.ResourceTypeIconMap
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.util.getApplicationInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * What a launcher cell shows for its command. Exactly one icon source is set: [iconRes] for our own
 * drawables, [iconDrawable] for an installed app's own icon.
 *
 * Not a data class on purpose. PackageManager returns a fresh [Drawable] per call and Drawable
 * inherits identity equality, so a generated equals() would make every re-resolution of the same
 * app unequal - defeating StateFlow conflation and making DiffUtil rebind the whole desktop on
 * every return to Home. Equality is defined over [iconKey] (the icon's source identity) instead.
 */
class LauncherCommandVisual(
    val label: String,
    @DrawableRes val iconRes: Int?,
    val iconDrawable: Drawable? = null,
    /** Stable identity of a Drawable-backed icon (the package it came from); null for [iconRes]. */
    private val iconKey: String? = null,
) {

    /** A user-set caption over the same icon. */
    fun withLabel(newLabel: String): LauncherCommandVisual =
        LauncherCommandVisual(newLabel, iconRes, iconDrawable, iconKey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val that = other as? LauncherCommandVisual ?: return false
        return label == that.label && iconRes == that.iconRes && iconKey == that.iconKey
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = HASH_MULTIPLIER * result + (iconRes ?: 0)
        result = HASH_MULTIPLIER * result + (iconKey?.hashCode() ?: 0)
        return result
    }

    private companion object {
        const val HASH_MULTIPLIER = 31
    }
}

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
) {

    suspend operator fun invoke(command: LauncherCellCommand): LauncherCommandVisual? =
        withContext(Dispatchers.IO) {
            when (command) {
                is LauncherCellCommand.App -> appVisual(command.packageName)
                is LauncherCellCommand.Feature -> featureVisual(command.routeKey)
                is LauncherCellCommand.Resource -> resourceVisual(command.resourceId)
                is LauncherCellCommand.Stream -> streamVisual(command.streamId)
                is LauncherCellCommand.OsShortcut -> osVisual(command.targetKey)
            }
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

    private fun featureVisual(routeKey: String): LauncherCommandVisual? {
        val route = InternalRouteCatalog.byKey(routeKey) ?: return null
        return LauncherCommandVisual(
            label = context.getString(route.labelRes),
            iconRes = route.iconRes,
        )
    }

    private suspend fun resourceVisual(resourceId: Long): LauncherCommandVisual? {
        val resource = resourceRepository.getResourceById(resourceId) ?: return null
        return LauncherCommandVisual(
            label = resource.name,
            iconRes = ResourceTypeIconMap.iconFor(resource.type),
        )
    }

    private suspend fun streamVisual(streamId: String): LauncherCommandVisual? {
        val source = streamSourceRepository.getById(streamId) ?: return null
        return LauncherCommandVisual(
            label = source.title,
            iconRes = R.drawable.ic_cast,
        )
    }

    private fun osVisual(targetKey: String): LauncherCommandVisual? {
        val target = OsShortcutCatalog.byKey(targetKey) ?: return null
        return LauncherCommandVisual(
            label = context.getString(target.labelRes),
            iconRes = target.iconRes,
        )
    }
}
