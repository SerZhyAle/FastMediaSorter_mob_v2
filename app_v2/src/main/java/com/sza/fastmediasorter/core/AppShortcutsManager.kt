package com.sza.fastmediasorter.core

import android.content.Context
import android.content.Intent
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.icon.DecoratedShortcutIcons
import com.sza.fastmediasorter.core.panel.IconHueCatalog
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.ResourceTypeIconMap
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.usecase.launcher.QueryRecentLauncherCommandsUseCase
import com.sza.fastmediasorter.ui.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1925: the only writer of the dynamic half of the app-icon long-press menu. Resources and sub-programs
 * share one budget, so they are published together in one ranked call - two writers would each erase the
 * other's half (strategic ADR-3).
 */
@Singleton
class AppShortcutsManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceDao: ResourceDao,
    private val queryRecentCommands: QueryRecentLauncherCommandsUseCase,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    private val publishMutex = Mutex()

    private val refreshFailureHandler = CoroutineExceptionHandler { _, error ->
        Timber.e(error, "App shortcuts: refresh could not read its sources")
    }

    /** Fire-and-forget: callers are launch and list-loading paths that must not wait on shortcut IPC. */
    fun requestRefresh() {
        appScope.launch(refreshFailureHandler) { publishMutex.withLock { publish() } }
    }

    private suspend fun publish() {
        val platformMax = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        val staticCount = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_MANIFEST).size
        val slots = DynamicShortcutBudget.dynamicSlots(platformMax, staticCount)
        val fromJournal = queryRecentCommands.osAppShortcuts(slots).first().mapNotNull { candidateFor(it.command) }
        // The journal only records launcher-mode launches; resources opened from the main list still earn the
        // places the journal leaves empty, so a user who never enters launcher mode keeps today's shortcuts.
        val fromRecentResources = resourceDao.getRecentResourcesSync(slots).map { resourceCandidate(it) }
        val ranked = DynamicShortcutBudget.rankWithinBudget(fromJournal, fromRecentResources, slots) { it.id }
        val shortcuts = ranked.mapIndexed { rank, candidate -> candidate.toShortcut(rank) }
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: IllegalArgumentException) {
            Timber.w(
                e,
                "App shortcuts: platform refused %d dynamic shortcuts (max=%d, static=%d); previous set kept",
                shortcuts.size,
                platformMax,
                staticCount,
            )
        } catch (e: IllegalStateException) {
            Timber.w(e, "App shortcuts: publication rejected while the user is locked or rate-limited")
        }
    }

    private suspend fun candidateFor(command: LauncherCellCommand): ShortcutCandidate? = when (command) {
        is LauncherCellCommand.Resource -> resourceDao.getResourceByIdSync(command.resourceId)?.let {
            resourceCandidate(it)
        }
        is LauncherCellCommand.Feature -> featureCandidate(command.routeKey)
        else -> null
    }

    private fun featureCandidate(routeKey: String): ShortcutCandidate? {
        val route = InternalRouteCatalog.byKey(routeKey) ?: return null
        val intent = route.intent(context).apply {
            // A shortcut intent without an action is rejected by ShortcutInfo.Builder.
            if (action == null) action = Intent.ACTION_VIEW
        }
        return ShortcutCandidate(
            id = FEATURE_ID_PREFIX + routeKey,
            label = context.getString(route.labelRes),
            iconRes = route.iconRes,
            hueRes = IconHueCatalog.forRoute(routeKey),
            intent = intent,
        )
    }

    private fun resourceCandidate(entity: ResourceEntity) = ShortcutCandidate(
        // The id is read back by shortcuts users pinned before S1925, so its form must not change.
        id = RESOURCE_ID_PREFIX + entity.id,
        label = entity.name,
        iconRes = ResourceTypeIconMap.iconFor(entity.type),
        hueRes = IconHueCatalog.forResourceType(entity.type),
        intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_BROWSE_RESOURCE
            putExtra(MainActivity.EXTRA_SHORTCUT_RESOURCE_ID, entity.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )

    private fun ShortcutCandidate.toShortcut(rank: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(DecoratedShortcutIcons.forGlyph(context, iconRes, hueRes))
            .setIntent(intent)
            .setRank(rank)
            .build()

    private class ShortcutCandidate(
        val id: String,
        val label: String,
        @param:DrawableRes val iconRes: Int,
        @param:ColorRes val hueRes: Int,
        val intent: Intent,
    )

    private companion object {
        const val RESOURCE_ID_PREFIX = "resource_"
        const val FEATURE_ID_PREFIX = "route_"
    }
}

/** S1925: the pure half of publication - how many places exist and who gets them. */
internal object DynamicShortcutBudget {

    /** The platform counts manifest shortcuts against the same per-activity limit and refuses the whole call. */
    fun dynamicSlots(platformMax: Int, staticCount: Int): Int = (platformMax - staticCount).coerceAtLeast(0)

    /** Journal entries keep their recency order; fallback entries only fill what the journal left empty. */
    fun <T> rankWithinBudget(journal: List<T>, fallback: List<T>, slots: Int, idOf: (T) -> String): List<T> =
        (journal + fallback).distinctBy(idOf).take(slots)
}
