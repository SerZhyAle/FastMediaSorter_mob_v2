package com.sza.fastmediasorter.data.cloud

import android.app.Activity

/**
 * Interactive Google Drive sign-in contract (strategic S0403).
 *
 * The implementation picks between the GMS Identity authorization path and the browser fallback;
 * `src/cloudSdk` supplies [GoogleDriveInteractiveSignInCoordinatorImpl], `src/cloudNoSdk` supplies
 * [NoOpGoogleDriveInteractiveSignInCoordinator].
 *
 * [StartResult] stays nested on the contract because both call sites - `GoogleDriveAuthPlugin` and
 * `BrowseCloudAuthManager` - match on it by that qualified name.
 */
interface GoogleDriveInteractiveSignInCoordinator {

    /** Outcome of [start]: either a result already in hand, or a flow that resumes via the activity. */
    sealed interface StartResult {
        data class Immediate(val result: AuthResult) : StartResult
        data object AwaitResume : StartResult
    }

    /** Begin interactive sign-in. */
    suspend fun start(activity: Activity): StartResult

    /** Take the result parked by a resumed browser flow, clearing it. */
    fun consumePendingInteractiveResult(): AuthResult?
}
