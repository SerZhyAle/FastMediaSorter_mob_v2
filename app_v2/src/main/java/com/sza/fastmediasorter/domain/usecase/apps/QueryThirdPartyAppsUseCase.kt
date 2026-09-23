package com.sza.fastmediasorter.domain.usecase.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.sza.fastmediasorter.util.getPackageInfoCompat
import com.sza.fastmediasorter.util.queryIntentActivitiesCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S2015: the applications this user installed themselves, for the launcher's Apps section.
 *
 * Deliberately not backed by `InstalledAppsRepository`, for the same reason as
 * [ResolveInstalledPackagesUseCase]: that cache is filled by `DeferredStartupWorker`, which has not run
 * yet at the one moment the desktop is seeded, so reading it there answers "no apps" on every device.
 *
 * The list is enumerated rather than curated (strategic ADR-2). A hand-written catalogue of popular
 * brands answers "what is popular in general" when the desktop needs "what is on this device", and it
 * rots silently - a renamed or regional package name is indistinguishable from an uninstalled app, so
 * the cell simply never appears and nobody finds out.
 */
class QueryThirdPartyAppsUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * At most [MAX_APPS] package names, publisher-declared category first (social and communication,
     * then games, then the rest) and most recently updated first inside a group - S2717: an app Play
     * keeps updating is one the device actually runs, while a factory preinstall keeps the ROM's date.
     * [excluded] is what the caller already places by name, so the seed never offers the same package
     * twice.
     */
    suspend operator fun invoke(excluded: Set<String>): List<String> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivitiesCompat(intent)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .filterNot { it == context.packageName || it in excluded || isPlatformPackage(it) }
            .mapNotNull { candidateOrNull(packageManager, it) }
            .sortedWith(compareBy<Candidate> { it.categoryRank }.thenByDescending { it.lastUpdateTime })
            .take(MAX_APPS)
            .map { it.packageName }
    }

    /** A package the publisher stamped as part of the Google catalogue or of the AOSP frame. */
    private fun isPlatformPackage(packageName: String): Boolean =
        PLATFORM_PREFIXES.any { packageName.startsWith(it) }

    /**
     * Null for an untouched system app - strategic §6 item 3 rules them out, because a section filled
     * with the vendor's preinstalled clock, calculator and store is not the user's own set of apps.
     *
     * S2717 narrows that to UNTOUCHED: a preinstall the user has updated from the store carries
     * FLAG_UPDATED_SYSTEM_APP, and that update is the user's own signature on the app being one they
     * use. On a vendor ROM most of what the owner actually runs ships preinstalled, so the flag alone
     * emptied the section it was meant to keep clean.
     */
    private fun candidateOrNull(packageManager: PackageManager, packageName: String): Candidate? = try {
        val packageInfo = packageManager.getPackageInfoCompat(packageName)
        val applicationInfo = packageInfo.applicationInfo
        val isUntouchedSystemApp = applicationInfo != null &&
            applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
            applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
        if (applicationInfo == null || isUntouchedSystemApp) {
            null
        } else {
            Candidate(
                packageName = packageName,
                categoryRank = categoryRankOf(applicationInfo),
                lastUpdateTime = packageInfo.lastUpdateTime,
            )
        }
    } catch (e: PackageManager.NameNotFoundException) {
        // Uninstalled between the enumeration and this read. Skipping it is the correct result - the
        // desktop must not be seeded a cell for a package that is already gone.
        Timber.i(e, "Package %s vanished during third-party enumeration", packageName)
        null
    }

    /**
     * The publisher-declared category, which only exists from API 26; the `legacy` flavor still ships to
     * API 23, where every app reads as uncategorised and the order collapses to install time alone. That
     * is also the fallback on a modern device, because the category is declared by the publisher and
     * most never bother (strategic ADR-3).
     */
    private fun categoryRankOf(applicationInfo: ApplicationInfo): Int {
        val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationInfo.category
        } else {
            CATEGORY_UNDEFINED
        }
        return when (category) {
            CATEGORY_SOCIAL -> RANK_SOCIAL
            CATEGORY_GAME -> RANK_GAME
            else -> RANK_OTHER
        }
    }

    private data class Candidate(
        val packageName: String,
        val categoryRank: Int,
        val lastUpdateTime: Long,
    )

    private companion object {
        /**
         * The ceiling of what the layout may ever ask for, not a layout decision of its own. S2717: at
         * 12 this cap decided the section's size before LauncherStarterLayoutRules ever saw the list,
         * so a roomy grid and a compact one seeded the same twelve names.
         */
        const val MAX_APPS = 30

        // `ApplicationInfo.CATEGORY_*`, spelled out so API 23 never resolves the constants - the same
        // reason RefreshInstalledAppsUseCase spells out CATEGORY_UNDEFINED.
        const val CATEGORY_UNDEFINED = -1
        const val CATEGORY_GAME = 0

        /** `CATEGORY_SOCIAL` covers messaging, email and social networks - the messengers the owner asked for. */
        const val CATEGORY_SOCIAL = 4

        const val RANK_SOCIAL = 0
        const val RANK_GAME = 1
        const val RANK_OTHER = 2

        /**
         * `com.google.` is the Google catalogue, which the Google section owns; `com.android.` is the
         * AOSP frame, which is not an app anyone installed. Both are also filtered by the system flag on
         * most devices - this prefix pass is what covers the ones where they are not.
         */
        /**
         * S2717: the AOSP frame only. `com.google.` used to sit here too, which banned every Google
         * application outside GOOGLE_APP_PACKAGES from this section as well - Messages, Keep, Docs,
         * Meet, Translate all fell through both sections and reached no desktop. Whatever the Google
         * section already owns still arrives here through the caller's `excluded` set.
         */
        val PLATFORM_PREFIXES = listOf("com.android.")
    }
}
