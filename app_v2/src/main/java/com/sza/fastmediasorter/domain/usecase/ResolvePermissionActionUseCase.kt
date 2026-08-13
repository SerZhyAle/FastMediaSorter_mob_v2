package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGrantKind
import com.sza.fastmediasorter.domain.model.PermissionStatus
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** What a permission row's action button must do for a given entry and status. */
sealed interface PermissionAction {

    /** The system dialog is still available - fire the runtime request. */
    data object RequestFromSystem : PermissionAction

    /** The permission is granted only from its own system screen, never from a runtime dialog. */
    data object OpenSpecialGrantScreen : PermissionAction

    /** The system will not show a request any more - the app settings page is the only route left. */
    data object OpenAppSettings : PermissionAction

    /** Nothing to do - the permission does not apply to this device. */
    data object None : PermissionAction
}

/**
 * Single owner of the "system dialog or settings screen" decision, shared by the onboarding page and
 * the settings screen so the two cannot drift apart. S1436: which route a permission takes is read
 * from its registry entry's [PermissionGrantKind] rather than from a list of exceptions held here,
 * so adding a permission that needs a system screen is a registry edit and nothing else.
 */
@Singleton
class ResolvePermissionActionUseCase @Inject constructor() {

    operator fun invoke(entry: PermissionEntry, status: PermissionStatus): PermissionAction {
        val action = when (entry.grantKind) {
            // Nothing is persisted to grant, so the row has no action to offer - the system will
            // ask again at the moment of use whatever the button did.
            PermissionGrantKind.PER_USE_CONSENT -> PermissionAction.None
            // A special grant is never reachable from a runtime dialog, whatever its status, so the
            // dedicated system screen is the answer before the status is even consulted.
            PermissionGrantKind.SYSTEM_SCREEN -> PermissionAction.OpenSpecialGrantScreen
            PermissionGrantKind.RUNTIME_DIALOG -> when (status) {
                PermissionStatus.NOT_YET_REQUESTED,
                PermissionStatus.DENIED -> PermissionAction.RequestFromSystem
                PermissionStatus.PERMANENTLY_DENIED,
                PermissionStatus.GRANTED -> PermissionAction.OpenAppSettings
                // ASKED_EACH_TIME cannot occur here - it is produced only for a PER_USE_CONSENT
                // entry, which the outer branch already answered. Listed rather than folded into an
                // else so that a future status forces this decision again.
                PermissionStatus.NOT_APPLICABLE,
                PermissionStatus.ASKED_EACH_TIME -> PermissionAction.None
            }
        }
        return action
    }

    /**
     * Special grants are silently dropped by `requestPermissions()`, so a bulk request must skip them
     * and walk their system screens one at a time instead.
     */
    fun isSpecialGrant(entry: PermissionEntry): Boolean =
        entry.grantKind == PermissionGrantKind.SYSTEM_SCREEN
}
