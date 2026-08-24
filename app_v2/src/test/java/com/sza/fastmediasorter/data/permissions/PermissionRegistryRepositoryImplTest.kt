package com.sza.fastmediasorter.data.permissions

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.domain.model.PermissionGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [PermissionRegistryRepositoryImpl]: SDK-range filtering (minSdk/maxSdk) of the
 * static entry list and group derivation. Robolectric pins Build.VERSION.SDK_INT so the SDK gates
 * are deterministic; flavor gates resolve against the test variant's BuildConfig.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PermissionRegistryRepositoryImplTest {

    private val repo = PermissionRegistryRepositoryImpl()

    @Test
    fun `getEntries excludes entries below or above the SDK window on API 33`() {
        val ids = repo.getEntries().map { it.id }.toSet()
        // READ_EXTERNAL_STORAGE has maxSdk=32 → excluded on 33.
        assertFalse("read_external_storage" in ids)
        // READ_MEDIA_* require minSdk=33 → included on 33.
        assertTrue("read_media_images" in ids)
        assertTrue("read_media_video" in ids)
        // access_local_network needs minSdk=37 → excluded on 33.
        assertFalse("access_local_network" in ids)
    }

    @Test
    fun `getEntries respects minSdk lower bound`() {
        // manage_media needs minSdk=31 → present on 33.
        assertTrue("manage_media" in repo.getEntries().map { it.id })
    }

    @Test
    fun `getGroups covers every group that has applicable entries`() {
        val groups = repo.getGroups().map { it.group }.toSet()
        val entryGroups = repo.getEntries().map { it.group }.toSet()
        // S1454: containment, not equality. getGroups() deliberately keeps the header of a group whose
        // only entry is flavor-gated-out, so the welcome set can render an entry marked
        // shownInWelcomeDespiteGates; BuildPermissionRowsUseCase drops a header that resolves to no
        // entries. Equality only held on flavors where every group happens to keep a gate-passing entry.
        assertTrue(
            "a group with applicable entries must have a header: ${entryGroups - groups}",
            groups.containsAll(entryGroups),
        )
        // STORAGE always has entries on API 33.
        assertTrue(PermissionGroup.STORAGE in groups)
    }

    @Test
    fun `getGroups preserves PermissionGroup declaration order`() {
        val groups = repo.getGroups().map { it.group }
        val declarationOrder = PermissionGroup.entries.filter { it in groups.toSet() }
        assertTrue(groups == declarationOrder)
    }

    @Test
    fun `S0786 registers the opt-in location geotag permission in registry, onboarding and groups`() {
        val location = repo.getEntries().firstOrNull { it.id == "access_fine_location" }
        assertNotNull("location permission must be registered", location)
        assertEquals(PermissionGroup.LOCATION, location!!.group)
        assertTrue("location geotag must be optional (decline = no coordinates)", location.optional)
        // Surfaces in onboarding so the user can grant it at first run, not only via system settings.
        assertTrue("access_fine_location" in repo.getWelcomeEntries().map { it.id })
        assertTrue(PermissionGroup.LOCATION in repo.getGroups().map { it.group })
    }

    @Test
    fun `S1335 registers the read-contacts permission in registry, onboarding and groups`() {
        val contacts = repo.getEntries().firstOrNull { it.id == "read_contacts" }
        // S1454: the row is gated on SUPPORT_LAUNCHER, and a build without the launcher does not compile
        // in the only seam onto ContactsContract - so on those flavors its absence is the correct state,
        // and lite additionally strips the manifest declaration. Assert that direction rather than skip
        // it, so the gate stays covered from both sides.
        if (!BuildConfig.SUPPORT_LAUNCHER) {
            assertTrue("read_contacts must not be offered where the launcher is absent", contacts == null)
            return
        }
        assertNotNull("read_contacts must be registered", contacts)
        assertEquals(PermissionGroup.CONTACTS, contacts!!.group)
        assertTrue("read_contacts must be optional", contacts.optional)
        assertTrue("read_contacts" in repo.getWelcomeEntries().map { it.id })
        assertTrue(PermissionGroup.CONTACTS in repo.getGroups().map { it.group })
    }

    @Test
    fun `S2012 the all-files row is offered only where the flavor declares the permission`() {
        val allFiles = repo.getEntries().firstOrNull { it.id == "manage_external_storage" }
        // Google Play refused two updates over MANAGE_EXTERNAL_STORAGE, so it survives only in
        // src/noLegal. Asserted from both sides like read_contacts above: a row offering a system
        // screen the merged manifest gives the build no standing on is the defect this gate removes.
        if (!BuildConfig.IS_NO_LEGAL_FLAVOR) {
            assertTrue(
                "manage_external_storage must not be offered where the manifest does not declare it",
                allFiles == null,
            )
            return
        }
        assertNotNull("manage_external_storage must be registered on noLegal", allFiles)
        assertEquals(PermissionGroup.STORAGE, allFiles!!.group)
        assertTrue("manage_external_storage" in repo.getWelcomeEntries().map { it.id })
    }

    @Test
    fun `S1436 onboarding is a superset of settings and differs only on welcome-only entries`() {
        val settings = repo.getEntries().map { it.id }.toSet()
        val welcome = repo.getWelcomeEntries()
        val welcomeIds = welcome.map { it.id }.toSet()
        assertTrue("onboarding must never hide a permission Settings shows", settings.all { it in welcomeIds })
        // Anything onboarding adds must say so in the registry - the difference is declared, not coded.
        val extra = welcome.filter { it.id !in settings }
        val undeclared = extra.filterNot { it.shownInWelcomeDespiteGates }.map { it.id }
        assertTrue("onboarding-only entries without the flag: $undeclared", undeclared.isEmpty())
    }

    @Test
    fun `every declared build-gate names an existing boolean BuildConfig field`() {
        // Guards against a typo'd / removed buildGates string silently dropping a permission:
        // such a name resolves on no variant, so this standardDebug run fails fast on it.
        val byName = BuildConfig::class.java.fields.associateBy { it.name }
        repo.declaredBuildGateFields.forEach { fieldName ->
            val field = byName[fieldName]
            assertNotNull("Permission build-gate references unknown BuildConfig field: $fieldName", field)
            assertEquals("Build-gate field $fieldName must be boolean type", java.lang.Boolean.TYPE, field!!.type)
        }
    }

    @Test
    fun `every declared build-gate resolves through a mapped value, not the fallback`() {
        // The BuildConfig-field check above cannot catch a name that exists but has no value in the
        // resolver: such a gate falls through to false on every variant and silently hides its
        // permission. Assert the resolver is total over the declared set instead.
        val unmapped = repo.declaredBuildGateFields - repo.mappedBuildGateFields
        assertTrue("Build-gates with no value in the resolver: $unmapped", unmapped.isEmpty())
    }
}
