package com.sza.fastmediasorter.core.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.panel.ProgramScreenGuardCatalog
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4544: keeps a switched-off program's screen from opening, whatever reached it.
 *
 * The decision is the availability resolver's own answer for the route key [ProgramScreenGuardCatalog]
 * assigns to the screen, so there is no second copy of any switch here. A program that is compiled in
 * and switched off is replaced by its settings destination and a short message; an unknown route or one
 * the build leaves out is admitted unchanged, because build gating is not this guard's business.
 *
 * The settings snapshot is held for the whole process - the shape [AppKeepScreenAwakeManager] already
 * runs - so a warm start is decided inside `onCreate` and a refused screen finishes before its first
 * frame. Only a cold process waits for the first read, and [admit] holds the caller's work until then.
 */
@Singleton
class ProgramScreenGuardManager @Inject constructor(
    @ApplicationScope scope: CoroutineScope,
    settingsRepository: SettingsRepository,
    private val resolveAvailability: ResolvePanelRouteAvailabilityUseCase,
) : Application.ActivityLifecycleCallbacks {

    private val settings: StateFlow<AppSettings?> =
        settingsRepository.getSettings().stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Runs [onAdmitted] when [routeKey] may launch, now when the answer is already known and after the
     * first settings read otherwise; a refused launch is redirected and [onAdmitted] never runs.
     */
    fun admit(activity: ComponentActivity, routeKey: String, onAdmitted: () -> Unit) {
        Timber.d("S4544: admit requested for a guarded launcher or screen")
        val snapshot = settings.value
        if (snapshot != null) {
            decide(activity, routeKey, snapshot, onAdmitted)
        } else {
            activity.lifecycleScope.launch {
                decide(activity, routeKey, settings.filterNotNull().first(), onAdmitted)
            }
        }
    }

    /** [admit] for the route [activity]'s class guards; an unguarded screen runs [onAdmitted] at once. */
    fun admitScreen(activity: ComponentActivity, onAdmitted: () -> Unit) {
        val routeKey = ProgramScreenGuardCatalog.screenRouteKeyFor(activity.javaClass)
        if (routeKey == null) onAdmitted() else admit(activity, routeKey, onAdmitted)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        val routeKey = ProgramScreenGuardCatalog.screenRouteKeyFor(activity.javaClass)
        if (routeKey != null && activity is ComponentActivity) observe(activity, routeKey)
    }

    // A recents instance outlives the setting, so the answer is re-read every time the screen is started.
    private fun observe(activity: ComponentActivity, routeKey: String) {
        val snapshot = settings.value
        if (snapshot != null && isRefused(routeKey, snapshot)) {
            redirect(activity, routeKey)
            return
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settings.filterNotNull().collect { current ->
                    if (isRefused(routeKey, current)) redirect(activity, routeKey)
                }
            }
        }
    }

    private fun decide(activity: ComponentActivity, routeKey: String, snapshot: AppSettings, onAdmitted: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (isRefused(routeKey, snapshot)) redirect(activity, routeKey) else onAdmitted()
    }

    private fun isRefused(routeKey: String, snapshot: AppSettings): Boolean {
        val availability = resolveAvailability.resolveOrNull(routeKey, snapshot) ?: return false
        return availability.availableInBuild && !availability.enabledAtRuntime
    }

    private fun redirect(activity: Activity, routeKey: String) {
        if (activity.isFinishing) return
        Timber.d("S4544: switched-off program redirected to its settings")
        Timber.i("ProgramScreenGuard: %s is switched off, leaving %s", routeKey, activity.javaClass.simpleName)
        val route = InternalRouteCatalog.byKey(routeKey)
        route?.let {
            val message = activity.getString(R.string.program_guard_disabled_toast, activity.getString(it.labelRes))
            // The text is resolved on the activity, whose context carries the app locale; the toast is
            // shown on the application context because the activity finishes before it disappears.
            Toast.makeText(activity.applicationContext, message, Toast.LENGTH_LONG).show()
        }
        val settingsIntent = route?.settingsIntent?.invoke(activity)
            ?: SettingsActivity.openProgramsSectionIntent(activity)
        activity.startActivity(settingsIntent)
        activity.finish()
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
