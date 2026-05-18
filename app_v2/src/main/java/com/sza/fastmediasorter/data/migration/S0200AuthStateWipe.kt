package com.sza.fastmediasorter.data.migration

import android.content.Context
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveCredentialsManager
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter.data.local.db.PendingRevocationDao
import com.sza.fastmediasorter.data.local.db.PendingRevocationEntity
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0200 Phase 05 - run-once legacy auth-state wipe per strategic ADR-6.
 *
 * On first launch of the upgraded app, clears ALL legacy Google Drive auth state while preserving
 * the `ResourceEntity` rows (folder name, sync flags, `credentialsId` string). Marks every Drive
 * resource as `needsSignIn = true` so the UI (Phase 06) renders the "requires sign-in" indicator.
 *
 * Idempotent: a sentinel flag in `SharedPreferences("s0200_migration")` prevents re-execution.
 * If any step throws, the sentinel is NOT set, so the next launch retries from scratch - order
 * of operations enqueues token revocation FIRST (so the token strings are not lost), persists
 * the sentinel LAST.
 *
 * Lite flavor: `GoogleIdentityRepository` is bound to the no-op impl; this class still runs and
 * completes in milliseconds with no observable change (no Drive rows, no credentials).
 */
@Singleton
class S0200AuthStateWipe @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkCredentialsDao: NetworkCredentialsDao,
    private val resourceDao: ResourceDao,
    private val identityRepository: GoogleIdentityRepository,
    private val pendingRevocationDao: PendingRevocationDao,
    private val driveCredentialsManager: GoogleDriveCredentialsManager,
) {

    /**
     * Idempotent wipe. Safe to call from `Application.onCreate` on every launch - early-exit
     * when [KEY_DONE] is set.
     */
    suspend fun runIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return
        try {
            // 1. Snapshot stored credential blobs BEFORE clearing - they hold the token strings
            //    used for offline revocation via PendingRevocationEntity (Phase 03's bg revoker).
            val staleBlobs = driveCredentialsManager.snapshotAllCredentialBlobs()
            staleBlobs.forEach { blob ->
                pendingRevocationDao.insert(
                    PendingRevocationEntity(
                        provider = "google",
                        token = blob,
                        revokeUrl = "https://oauth2.googleapis.com/revoke"
                    )
                )
            }

            // 2. Drop primary binding via identity domain (no-op when Unbound).
            identityRepository.signOutPrimary()

            // 3. Wipe legacy Drive credentials store (legacy + per-account keys).
            driveCredentialsManager.clearAllCredentials()

            // 4. Delete every NetworkCredentialsEntity row for Drive.
            networkCredentialsDao.deleteByType("GOOGLE_DRIVE")

            // 5. Mark every Drive ResourceEntity as needs-sign-in. Folder names / sync flags are
            //    preserved - user only has to sign in again to restore access.
            resourceDao.markAllDriveNeedsSignIn(true)

            // 6. Persist sentinel LAST so a partial-failure run re-attempts on next launch.
            prefs.edit().putBoolean(KEY_DONE, true).commit()

            Timber.i("S0200: legacy auth-state wipe completed (revocation-queued=${staleBlobs.size})")
        } catch (t: Throwable) {
            Timber.e(t, "S0200: legacy auth-state wipe failed; will retry on next launch")
        }
    }

    private companion object {
        const val PREFS = "s0200_migration"
        const val KEY_DONE = "wipe_done"
    }
}
