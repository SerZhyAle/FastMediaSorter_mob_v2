package com.sza.fastmediasorter.selftest

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.sza.fastmediasorter.data.local.preferences.CollapsibleSectionStore
import com.sza.fastmediasorter.util.getPackageInfoCompat
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * S3741: the app-side half of device-test provisioning. The connected-test task reinstalls the app,
 * which wipes runtime grants and every private preference, so this state cannot be pushed from the
 * workstation - it is set here, inside the test process, before each test.
 *
 * - every dangerous permission the package requests is granted, whatever the API level declares;
 * - onboarding is marked complete with the same four keys a real Finish writes, because a partial map
 *   is rewritten by the app and brings the welcome screen back mid-test;
 * - collapsible settings sections start from their defaults.
 */
class SelfTestBaselineRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            applyBaseline()
            base.evaluate()
        }
    }

    fun applyBaseline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        grantDangerousPermissions(context)
        context.getSharedPreferences(WELCOME_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("welcome_completed", true)
            .putBoolean("first_run_after_welcome", false)
            .putBoolean("onboarding_default_player_shown", true)
            .putBoolean("gesture_defaults_seeded", true)
            .commit()
        context.getSharedPreferences(CollapsibleSectionStore.NAMESPACE, Context.MODE_PRIVATE).edit()
            .clear()
            .commit()
    }

    private fun grantDangerousPermissions(context: Context) {
        val pm = context.packageManager
        val requested = pm.getPackageInfoCompat(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        requested.filter { isDangerous(pm, it) }.forEach { permission ->
            automation.grantRuntimePermission(context.packageName, permission)
        }
    }

    private fun isDangerous(pm: PackageManager, permission: String): Boolean =
        try {
            val info = pm.getPermissionInfo(permission, 0)
            val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.protection
            } else {
                @Suppress("DEPRECATION")
                (info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
            }
            base == PermissionInfo.PROTECTION_DANGEROUS
        } catch (_: PackageManager.NameNotFoundException) {
            // A permission newer than the device (ACCESS_LOCAL_NETWORK below API 37) does not exist
            // there, so there is nothing to grant.
            false
        }

    private companion object {
        const val WELCOME_PREFS = "welcome_prefs"
    }
}
