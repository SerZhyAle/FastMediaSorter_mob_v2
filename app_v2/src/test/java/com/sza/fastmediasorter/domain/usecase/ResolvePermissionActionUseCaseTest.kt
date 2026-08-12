package com.sza.fastmediasorter.domain.usecase

import android.Manifest
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGrantKind
import com.sza.fastmediasorter.domain.model.PermissionGroup
import com.sza.fastmediasorter.domain.model.PermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins the action matrix of [ResolvePermissionActionUseCase]. The claim that matters is that a
 * never-requested plain permission opens the system dialog and never a settings screen - that
 * mis-routing is the defect S1426 removes.
 */
class ResolvePermissionActionUseCaseTest {

    private val useCase = ResolvePermissionActionUseCase()

    private val specialGrants = listOf(
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        Manifest.permission.MANAGE_MEDIA,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
    )

    @Test
    fun `never requested plain permission opens the system dialog`() {
        val action = useCase(plainEntry(), PermissionStatus.NOT_YET_REQUESTED)
        assertEquals(PermissionAction.RequestFromSystem, action)
        assertNotEquals(PermissionAction.OpenAppSettings, action)
    }

    @Test
    fun `denied plain permission opens the system dialog`() {
        assertEquals(
            PermissionAction.RequestFromSystem,
            useCase(plainEntry(), PermissionStatus.DENIED),
        )
    }

    @Test
    fun `permanently denied plain permission opens app settings`() {
        assertEquals(
            PermissionAction.OpenAppSettings,
            useCase(plainEntry(), PermissionStatus.PERMANENTLY_DENIED),
        )
    }

    @Test
    fun `granted plain permission opens app settings`() {
        assertEquals(
            PermissionAction.OpenAppSettings,
            useCase(plainEntry(), PermissionStatus.GRANTED),
        )
    }

    @Test
    fun `inapplicable plain permission resolves to no action`() {
        assertEquals(
            PermissionAction.None,
            useCase(plainEntry(), PermissionStatus.NOT_APPLICABLE),
        )
    }

    @Test
    fun `every special grant opens its own system screen in every state`() {
        specialGrants.forEach { manifestName ->
            PermissionStatus.entries.forEach { status ->
                assertEquals(
                    "$manifestName in $status",
                    PermissionAction.OpenSpecialGrantScreen,
                    useCase(systemScreenEntryOf(manifestName), status),
                )
            }
        }
    }

    @Test
    fun `special grants are the only entries excluded from a bulk request`() {
        specialGrants.forEach { assertEquals(true, useCase.isSpecialGrant(systemScreenEntryOf(it))) }
        assertEquals(false, useCase.isSpecialGrant(plainEntry()))
    }

    private fun plainEntry(): PermissionEntry = entryOf(Manifest.permission.CAMERA)

    // S1436: the route is a property of the entry, not of its manifest name, so a test entry for a
    // special grant has to declare the kind the registry declares for it.
    private fun systemScreenEntryOf(manifestName: String): PermissionEntry =
        entryOf(manifestName).copy(grantKind = PermissionGrantKind.SYSTEM_SCREEN)

    private fun entryOf(manifestName: String) = PermissionEntry(
        id = manifestName.substringAfterLast('.').lowercase(),
        manifestName = manifestName,
        titleRes = 0,
        descriptionRes = 0,
        group = PermissionGroup.SYSTEM,
        optional = false,
    )
}
