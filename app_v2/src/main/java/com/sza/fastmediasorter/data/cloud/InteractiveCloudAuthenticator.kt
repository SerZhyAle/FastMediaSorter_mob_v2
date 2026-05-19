package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.flow.SharedFlow

/**
 * Interface that encapsulates the interactive (UI-based) authentication flow
 * for a specific cloud provider.
 *
 * Each implementation runs its own SDK flow internally (Credential Manager coroutine,
 * Dropbox PKCE + onResume, MSAL callback for OneDrive) and emits exactly one terminal
 * [AuthResult] per attempt through [results]. The orchestrator subscribes to [results]
 * and treats all providers uniformly - no downcast, no timing polls.
 */
interface InteractiveCloudAuthenticator {

    /** The cloud provider this authenticator handles. */
    val provider: CloudProvider

    /**
     * One-shot terminal result channel for a single interactive attempt.
     *
     * The orchestrator subscribes via [kotlinx.coroutines.flow.first] under a per-attempt
     * [kotlinx.coroutines.Job]. Implementations emit exactly one [AuthResult] per call to
     * [startInteractiveSignIn] - Success, Cancelled, or Error.
     *
     * This channel is not a substitute for `identityRepository.state` - that flow remains
     * the ambient observable of "who is signed in now" and is consumed by the Settings card;
     * [results] is the one-shot completion signal of a single interactive attempt.
     */
    val results: SharedFlow<AuthResult>

    /**
     * Starts the interactive sign-in flow.
     * This may launch an Intent (Google Drive Credential Manager), open a browser (Dropbox PKCE),
     * or show a custom UI (MSAL for OneDrive).
     *
     * @param activity The Activity context required to start the flow.
     */
    fun startInteractiveSignIn(activity: Activity)

    /**
     * Activity result lifecycle hook. Plugins that need to react to an Activity result
     * should emit to [results] from here; others may leave the body empty.
     *
     * @param data The Intent data from the ActivityResult.
     */
    suspend fun onIntentResult(data: Intent?) {}

    /**
     * `Activity.onResume` lifecycle hook. Plugins whose SDK finalizes on next foreground
     * (Dropbox) emit to [results] from here; others may leave the body empty.
     */
    suspend fun onResume() {}
}
