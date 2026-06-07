package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.utils.SmbPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0139: One-shot backfill of empty `shareName` on SMB credentials, derived from the resource path.
 *
 * Historical write paths could persist SMB credentials with `shareName = null` or `""`. The runtime
 * self-heal in `ResourceRepositoryImpl.testSmbConnection` masks the symptom but produces a noisy
 * `W` log on every connection test. This use-case fixes the persisted value once per install,
 * gated by a SharedPreferences flag, so the warning disappears for legacy data.
 *
 * Idempotent and safe on every cold start: if the flag is set, the call returns immediately.
 * If the flag is not set but no work is needed (no empty-share credentials), the flag is still
 * stored - subsequent launches short-circuit at the first check.
 */
@Singleton
class BackfillSmbCredentialShareNameUseCase @Inject constructor(
    private val credentialsRepository: NetworkCredentialsRepository,
    private val resourceRepository: ResourceRepository,
    @ApplicationContext private val context: Context,
) {

    data class BackfillResult(
        val scanned: Int,
        val updated: Int,
        val skipped: Int,
    )

    suspend operator fun invoke(): BackfillResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) {
            return BackfillResult(scanned = 0, updated = 0, skipped = 0)
        }

        return try {
            val allCredentials = credentialsRepository.getAllCredentials().first()
            val targets = allCredentials.filter {
                it.type.equals("SMB", ignoreCase = true) && it.shareName.isNullOrEmpty()
            }
            if (targets.isEmpty()) {
                prefs.edit().putBoolean(KEY_DONE, true).apply()
                return BackfillResult(scanned = 0, updated = 0, skipped = 0)
            }

            val smbResources: Map<String, MediaResource> = resourceRepository
                .getAllResourcesSync()
                .asSequence()
                .filter { it.type == ResourceType.SMB && !it.credentialsId.isNullOrEmpty() }
                .associateBy { it.credentialsId!! }

            var updated = 0
            var skipped = 0
            for (credential in targets) {
                val resource = smbResources[credential.credentialId]
                if (resource == null) {
                    Timber.w("backfill skipped - no SMB resource for credential ${credential.credentialId}")
                    skipped++
                    continue
                }
                val parsedShare = SmbPathUtils.parseSmbPath(resource.path)
                    ?.connectionInfo
                    ?.shareName
                    ?.takeIf { it.isNotEmpty() }
                if (parsedShare == null) {
                    Timber.w(
                        "S0139: backfill skipped - path has no shareName " +
                            "(credentialId=${credential.credentialId}, path=${resource.path})"
                    )
                    skipped++
                    continue
                }

                credentialsRepository.update(credential.copy(shareName = parsedShare))
                Timber.i(
                    "S0139: backfilled shareName='$parsedShare' for credential ${credential.credentialId}"
                )
                updated++
            }

            prefs.edit().putBoolean(KEY_DONE, true).apply()
            BackfillResult(scanned = targets.size, updated = updated, skipped = skipped)
        } catch (e: Exception) {
            // Do NOT set the flag - next process launch will retry.
            Timber.e(e, "backfill failed")
            BackfillResult(scanned = -1, updated = 0, skipped = 0)
        }
    }

    private companion object {
        const val PREFS_NAME = "s0139_backfill"
        const val KEY_DONE = "smb_share_name_backfill_v1_done"
    }
}
