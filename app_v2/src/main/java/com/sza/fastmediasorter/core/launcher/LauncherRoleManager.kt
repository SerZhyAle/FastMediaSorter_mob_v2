package com.sza.fastmediasorter.core.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
        setComponentEnabled(component, enabled = true)
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
        setComponentEnabled(component, enabled = false)
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
        setComponentEnabled(component, enabled = true)
    }

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

    private fun resolves(intent: Intent): Boolean =
        context.packageManager.resolveActivityCompat(intent, 0) != null

    private fun homeIntent(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
}
