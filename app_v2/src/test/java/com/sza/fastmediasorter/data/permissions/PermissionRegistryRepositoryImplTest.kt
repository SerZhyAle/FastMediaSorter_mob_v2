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
    fun `getGroups returns only groups that have applicable entries`() {
        val groups = repo.getGroups().map { it.group }.toSet()
        val entryGroups = repo.getEntries().map { it.group }.toSet()
        assertTrue(groups == entryGroups)
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
        assertNotNull("read_contacts must be registered", contacts)
        assertEquals(PermissionGroup.CONTACTS, contacts!!.group)
        assertTrue("read_contacts must be optional", contacts.optional)
        assertTrue("read_contacts" in repo.getWelcomeEntries().map { it.id })
        assertTrue(PermissionGroup.CONTACTS in repo.getGroups().map { it.group })
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
