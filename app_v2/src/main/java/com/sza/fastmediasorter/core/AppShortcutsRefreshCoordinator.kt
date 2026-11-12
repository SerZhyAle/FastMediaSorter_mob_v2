package com.sza.fastmediasorter.core

import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4545: the third trigger of [AppShortcutsManager.requestRefresh], and the only one a settings change reaches.
 *
 * The app-icon menu used to be rebuilt after the resource list loaded and after a launcher command, so a program
 * switched off in Settings kept its entry (and one switched on stayed absent) until one of those happened. The
 * trigger here is the SET of launchable routes changing, never a raw settings write: a publish is a Room read plus
 * a platform call, and most writes (sort mode, column counts) cannot change what the menu shows.
 */
@Singleton
class AppShortcutsRefreshCoordinator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val resolveRouteAvailability: ResolvePanelRouteAvailabilityUseCase,
    private val appShortcuts: dagger.Lazy<AppShortcutsManager>,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val started = AtomicBoolean(false)

    /** Idempotent: a second call must not add a second collector. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            LaunchableRouteSet.changes(settingsRepository.getSettings()) { routeKey, settings ->
                resolveRouteAvailability.resolveOrNull(routeKey, settings)?.isLaunchable == true
            }
                .debounce(REFRESH_DEBOUNCE_MS)
                .catch { Timber.w(it, "App shortcuts: settings watcher stopped; other triggers remain") }
                .collect {
                    appShortcuts.get().requestRefresh()
                }
        }
    }

    private companion object {
        // Long enough to fold the enable-all and backup-restore bursts into one publish, short enough that the
        // menu is already right when the user leaves Settings.
        const val REFRESH_DEBOUNCE_MS = 400L
    }
}

/** S4545: the pure half - which routes are launchable, and when that set moves. */
internal object LaunchableRouteSet {

    fun keys(settings: AppSettings, isLaunchable: (routeKey: String, settings: AppSettings) -> Boolean): Set<String> =
        InternalRouteCatalog.all()
            .filter { isLaunchable(it.key, settings) }
            .mapTo(mutableSetOf()) { it.key }

    /**
     * Every set that differs from the one before it. The first emission is the state the process started in and
     * is dropped: `MainViewModel` publishes after the list loads, so republishing it here would only repeat that.
     */
    fun changes(
        settings: Flow<AppSettings>,
        isLaunchable: (routeKey: String, settings: AppSettings) -> Boolean,
    ): Flow<Set<String>> =
        settings
            .map { keys(it, isLaunchable) }
            .distinctUntilChanged()
            .drop(1)
}
