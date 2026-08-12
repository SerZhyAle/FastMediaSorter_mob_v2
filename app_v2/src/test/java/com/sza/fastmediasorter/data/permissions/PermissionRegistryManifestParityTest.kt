package com.sza.fastmediasorter.data.permissions

import android.content.pm.PackageManager
import com.sza.fastmediasorter.util.getPackageInfoCompat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1436 / ADR-4: the registry and the merged manifest must describe the same set of permissions, in
 * both directions, for the variant this test runs under. Both discovered divergences - a microphone
 * row gated on the wrong flag, and a battery row in a build whose manifest strips the permission -
 * survived for months under a manual audit item in the release checklist, so the recurring finding
 * becomes a check that fails a build.
 *
 * The two directions deliberately compare against different registry sets, and the first run of this
 * test is what taught the difference:
 *
 * - declared -> row uses [PermissionRegistryRepositoryImpl.entriesForBuild], the gate-filtered set with
 *   the SDK window ignored. A permission whose row starts at a later API is still legitimately declared
 *   today, and demanding an applicable row would fail on `access_local_network`.
 * - row -> declared uses `getEntries()`, the SDK-filtered set, because `requestedPermissions` is itself
 *   SDK-filtered: a declaration carrying `android:maxSdkVersion` simply is not reported above it. Using
 *   the unfiltered set here failed on `write_external_storage`, whose row and declaration both end at 28.
 *
 * Run it on the variants where the composition actually differs, not only this one -
 * `docs/RELEASE_READINESS_STANDARD.md` names them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PermissionRegistryManifestParityTest {

    private val repo = PermissionRegistryRepositoryImpl()

    private fun declaredPermissions(): Set<String> {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfoCompat(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.toSet().orEmpty()
    }

    @Test
    fun `every declared permission is a registry row or a named exemption`() {
        val rows = repo.entriesForBuild.map { it.manifestName }.toSet()
        val unexplained = declaredPermissions()
            .filterNot { it in rows }
            .filterNot { PermissionManifestExemptions.isDeclaredWithoutRowAllowed(it) }
        assertTrue(
            "Declared with no registry row and no exemption: $unexplained. Add the row, or add it to " +
                "PermissionManifestExemptions.declaredWithoutRow with the reason the user never decides about it.",
            unexplained.isEmpty(),
        )
    }

    @Test
    fun `every registry row names a declared permission or a named exemption`() {
        val declared = declaredPermissions()
        val undeclared = repo.getEntries()
            .map { it.manifestName }
            .filterNot { it in declared }
            .filterNot { it in PermissionManifestExemptions.rowWithoutDeclaration }
        assertTrue(
            "Registry rows naming a permission this variant does not declare: $undeclared. The row would " +
                "offer to grant something the build cannot hold - fix the gate, or add it to " +
                "PermissionManifestExemptions.rowWithoutDeclaration with the reason it is held another way.",
            undeclared.isEmpty(),
        )
    }

    @Test
    fun `no exemption outlives the divergence it excuses`() {
        // An exemption for a permission that is neither declared nor a row is dead weight, and dead
        // weight in this file is how a parity gate quietly stops covering things.
        val declared = declaredPermissions()
        // S1454: matched against every row the registry defines, not the gate-filtered set. A row whose
        // build gate is off in this variant has not stopped existing - on lite the notification-listener
        // row and the <service> it excuses are both absent together, which is consistency, not staleness.
        val stale = PermissionManifestExemptions.rowWithoutDeclaration.keys
            .filterNot { it in repo.allRowManifestNames }

        assertTrue(
            "Exemptions for rows that no longer exist: $stale. A row merely gated off in this variant is " +
                "not stale - only a row deleted from the registry is.",
            stale.isEmpty(),
        )
        // declaredWithoutRow is not checked the same way: it legitimately lists permissions this
        // variant does not declare, because one list covers every flavor.
        assertTrue(
            "Exemption list is empty - it must at least cover the install-time declarations",
            PermissionManifestExemptions.declaredWithoutRow.keys.any { it in declared },
        )
    }
}
