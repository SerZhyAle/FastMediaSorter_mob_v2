package com.sza.fastmediasorter.core.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.util.resolveActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0404: owns the device-home role for launcher mode.
 *
 * Android never hands the role over programmatically - the app can only make itself a candidate and
 * route the user to the system chooser (research 01). Enabling the HOME component is what makes the
 * app a candidate; on API 29+ the role dialog can be requested directly, below that the system's
 * "Always / Just once" chooser appears on the next Home press. Disabling the component is the one
 * lever fully on our side, which is why it backs every exit path (strategic risk 2).
 */
@Singleton
class LauncherRoleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contract: LauncherModeContract,
) {

    /** True when the HOME component is enabled, i.e. the app is a home-screen candidate. */
    fun isModeEnabled(): Boolean {
        val component = contract.homeComponent(context) ?: return false
        return context.packageManager.getComponentEnabledSetting(component) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    /** True when this app currently holds the home role (is the active launcher). */
    fun isHomeRoleHeld(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            return roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        }
        val resolved = context.packageManager.resolveActivityCompat(
            homeIntent(),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * Makes the app a home-screen candidate and takes the user to the system decision point.
     * [roleLauncher] is the caller's registered result launcher for the API 29+ role dialog; without
     * it (or below API 29) the user lands in the system home-screen settings instead.
     */
    fun enableMode(activity: Activity, roleLauncher: ActivityResultLauncher<Intent>?) {
        val component = contract.homeComponent(context) ?: return
        setLauncherComponentsEnabled(component, enabled = true)
        val roleIntent = createRoleRequestIntent()
        if (roleIntent != null && roleLauncher != null) {
            roleLauncher.launch(roleIntent)
            return
        }
        Timber.i("Launcher mode: no role dialog available, routing to system home settings")
        openHomeChooser(activity)
    }

    /** Stops being a home-screen candidate; the system falls back to the previous launcher. */
    fun disableMode() {
        val component = contract.homeComponent(context) ?: return
        setLauncherComponentsEnabled(component, enabled = false)
    }

    /**
     * Enables the HOME component WITHOUT launching any chooser. Onboarding finishes into a multi-Activity
     * task stack immediately after calling this, and a role dialog launched from that finishing frame is
     * unreliable - it gets buried under the pushed activities (audit 2026-07-17). Enabling the component
     * is durable. S1107: the role itself is then actively requested from the non-finishing first-run
     * Settings screen (ADR-2's "chooser on next Home press" never fires when a default launcher is already
     * set, which is every real device), so this call is only the durable-candidate half.
     */
    fun markAsHomeCandidate() {
        val component = contract.homeComponent(context) ?: return
        setLauncherComponentsEnabled(component, enabled = true)
    }

    /**
     * S1107: records that onboarding asked for the HOME role, so the first-run Settings screen can issue
     * the request from its non-finishing context.
     *
     * The intent that carries the user to Settings used to be the only carrier of this signal, and it kept
     * getting lost: the first-run screen is recreated several times while theme and locale are applied, and
     * whichever instance consumed the extra could be torn down before the system resolved it - four
     * device runs, four different points of loss. A durable flag has no ordering to get wrong. It also
     * survives process death and a user who backs out of Settings and returns later.
     */
    fun markRoleRequestPending() {
        prefs().edit()
            .putBoolean(KEY_ROLE_REQUEST_PENDING, true)
            .putInt(KEY_ROLE_REQUEST_ATTEMPTS, 0)
            .apply()
    }

    /**
     * True while the onboarding request still needs to be issued. Both verdicts are read off state here
     * rather than reported by the dialog: holding the role means the user accepted, and a request that was
     * already issued while the role is still not ours means they declined.
     *
     * Deriving the verdict from state is not a stylistic preference. The dialog's result callback rides a
     * Fragment, and the role dialog rebuilds the task underneath it, so the result never arrives - a
     * declined request stayed pending and re-prompted on the next visit to Settings (device re-test #5,
     * 2026-07-26). Preferences outlive every object in that chain, so reading them on the next screen
     * cannot miss a verdict the way a callback can.
     *
     * One issued request is the whole budget. The earlier retry allowance existed to survive an attempt
     * that died before the dialog appeared, but the caller's settle delay already guarantees a surviving
     * instance issues it, and from here a retry is indistinguishable from re-asking a user who said no.
     */
    fun isRoleRequestPending(): Boolean {
        val prefs = prefs()
        if (!prefs.getBoolean(KEY_ROLE_REQUEST_PENDING, false)) return false
        val settled = isHomeRoleHeld() || prefs.getInt(KEY_ROLE_REQUEST_ATTEMPTS, 0) > 0
        if (settled) clearRoleRequestPending()
        return !settled
    }

    /**
     * Counts the issued request. This is what ends the cycle: the user has been taken to the system
     * decision point once, and whatever they answered there is read back from [isHomeRoleHeld] on the next
     * screen rather than delivered to a callback that may not survive the trip.
     */
    fun recordRoleRequestAttempt() {
        val prefs = prefs()
        val attempts = prefs.getInt(KEY_ROLE_REQUEST_ATTEMPTS, 0)
        prefs.edit().putInt(KEY_ROLE_REQUEST_ATTEMPTS, attempts + 1).apply()
    }

    /** Drops the onboarding request state once a verdict has been established. */
    fun clearRoleRequestPending() {
        prefs().edit()
            .remove(KEY_ROLE_REQUEST_PENDING)
            .remove(KEY_ROLE_REQUEST_ATTEMPTS)
            .apply()
    }

    private fun prefs(): SharedPreferences =
        context.getSharedPreferences(PREFS_LAUNCHER_ROLE, Context.MODE_PRIVATE)

    /** Opens the system home-screen chooser - the only place the role can be reassigned. */
    fun openHomeChooser(activity: Activity) {
        val homeSettings = Intent(Settings.ACTION_HOME_SETTINGS)
        val target = if (resolves(homeSettings)) {
            homeSettings
        } else {
            Timber.i("Launcher mode: home settings screen absent, opening general settings")
            Intent(Settings.ACTION_SETTINGS)
        }
        runCatching { activity.startActivity(target) }
            .onFailure { Timber.w(it, "Launcher mode: failed to open home settings") }
    }

    private fun createRoleRequestIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return context.getSystemService(RoleManager::class.java)
            ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_HOME) }
            ?.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

    private fun setComponentEnabled(component: ComponentName, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            component,
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** The Share-sheet entry follows HOME availability so it cannot create an invisible desktop cell. */
    private fun setLauncherComponentsEnabled(homeComponent: ComponentName, enabled: Boolean) {
        setComponentEnabled(homeComponent, enabled)
        setComponentEnabled(ComponentName(context.packageName, PLACE_SHARE_ALIAS), enabled)
    }

    private fun resolves(intent: Intent): Boolean =
        context.packageManager.resolveActivityCompat(intent, 0) != null

    private fun homeIntent(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private companion object {
        const val PREFS_LAUNCHER_ROLE = "launcher_role_prefs"
        const val KEY_ROLE_REQUEST_PENDING = "onboarding_role_request_pending"
        const val KEY_ROLE_REQUEST_ATTEMPTS = "onboarding_role_request_attempts"
        const val PLACE_SHARE_ALIAS = "com.sza.fastmediasorter.LauncherPlaceShare"
    }
}
