package com.sza.fastmediasorter.data.cloud

import android.app.Activity

/**
 * Dropbox provider contract (strategic S0403).
 *
 * Seamed out of the concrete client so a flavor that does not link the Dropbox SDK still compiles:
 * `src/cloudSdk` supplies [DropboxClientImpl], `src/cloudNoSdk` supplies [NoOpDropboxClient], and
 * AGP mounts exactly one of the two per flavor (see `app_v2/build.gradle.kts` `sourceSets`).
 *
 * Members beyond [CloudStorageClient] are exactly the ones `src/main` call sites use - the PKCE
 * sign-in pair driven by `BrowseCloudAuthManager`, the two credential-restore probes used by
 * `BrowseResourceLoadManager` / `CloudOperationStrategy`, and the account label read by the
 * multi-account auth path. Anything no shared-source file calls stays on the implementation.
 */
interface DropboxClient : CloudStorageClient {

    /** Restore a stored session for one specific account. `false` when nothing is stored for it. */
    suspend fun tryRestoreForAccount(email: String): Boolean

    /** Restore whichever session is stored, if any. `false` when the client stays unauthenticated. */
    suspend fun tryRestoreFromStorage(): Boolean

    /** Launch the OAuth 2.0 PKCE flow; the result is collected later by [finishAuthentication]. */
    fun startPkceAuthentication(activity: Activity, appKey: String)

    /** Collect the result of the PKCE flow started by [startPkceAuthentication]. */
    suspend fun finishAuthentication(): AuthResult

    /** Account label for the active session, or `null` when unauthenticated. */
    suspend fun getAccountEmail(): String?
}
