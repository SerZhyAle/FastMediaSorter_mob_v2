package com.sza.fastmediasorter.ui.debug.helpers

import android.app.Activity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.cloud.CloudFile
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveBrowserAuthManager
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveAppDataApi
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import com.sza.fastmediasorter.ui.cloudauth.GoogleDriveAuthResolutionTracker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject

/**
 * Debug-only probe for contract DEVICE-EXCHANGE 8.2 item B: writes one file into the Drive `appDataFolder`
 * through the app's own transport, so another client of the same Cloud project can try to list and read it.
 *
 * The write goes through [GoogleDriveAppDataApi], whose token on the GMS path carries `drive.appdata` alone.
 * A browser (AppAuth) session would hand it the whole consented scope set instead, so the probe refuses while
 * one exists rather than report a write made under a broader grant.
 */
class DriveAppDataProbeManager @Inject constructor(
    private val appData: GoogleDriveAppDataApi,
    private val identityRepository: GoogleIdentityRepository,
    private val browserAuthManager: GoogleDriveBrowserAuthManager,
) {

    /** Writes one `fmsx-probe-android-<8 hex>.json` file and answers the lines to show. */
    suspend fun write(activity: Activity): String = when (val gate = gate()) {
        is Gate.Closed -> gate.reason
        is Gate.Open -> writeAs(activity, gate.email)
    }

    /** Deletes every probe file in the `appDataFolder` root and answers the lines to show. */
    suspend fun delete(): String = when (val gate = gate()) {
        is Gate.Closed -> gate.reason
        is Gate.Open -> when (val listed = appData.listChildren(ROOT)) {
            is CloudResult.Error -> lines(listOf("account: ${gate.email}", "list FAILED: ${describe(listed)}"))
            is CloudResult.Success -> deleteProbes(gate.email, listed.data)
        }
    }

    private suspend fun writeAs(activity: Activity, email: String): String {
        val consent = requestAppDataConsent(activity)
        return if (consent.granted) {
            upload(email, consent.note)
        } else {
            lines(listOf("account: $email", "consent: ${consent.note}", "nothing written"))
        }
    }

    // A first appdata token on the GMS path needs a consent screen; the app's own sign-in never asks for it,
    // and the token issuer refuses a request that needs one, so the probe shows that screen itself.
    private suspend fun requestAppDataConsent(activity: Activity): Consent = runCatching {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GoogleScope.DRIVE_APPDATA.value)))
            .build()
        val result = Identity.getAuthorizationClient(activity).authorize(request).await()
        val pending = result.pendingIntent
        when {
            !result.hasResolution() -> Consent(granted = true, note = "already granted to this app, no prompt")
            pending != null && GoogleDriveAuthResolutionTracker.resolvePendingIntent(activity, pending) ->
                Consent(granted = true, note = "prompt shown and accepted")
            else -> Consent(granted = false, note = "prompt shown and declined")
        }
    }.getOrElse { error ->
        error.rethrowIfCancellation()
        Timber.w(error, "DriveAppDataProbe: drive.appdata authorization failed")
        Consent(granted = false, note = "authorization failed: ${error.javaClass.simpleName}: ${error.message}")
    }

    private suspend fun upload(email: String, consentNote: String): String {
        val name = FILE_PREFIX + randomHex() + FILE_SUFFIX
        val body = E2eBytes.utf8(BODY)
        val header = listOf("account: $email", "token: GMS, scope drive.appdata only", "consent: $consentNote")
        return when (val result = appData.uploadFile(name, ROOT, MIME_JSON, body)) {
            is CloudResult.Error -> lines(header + "write FAILED: ${describe(result)}")
            is CloudResult.Success -> {
                Timber.i("DriveAppDataProbe: wrote %s id=%s", name, result.data.id)
                lines(
                    header + listOf(
                        "file name: $name",
                        "drive file id: ${result.data.id}",
                        "sha-256: ${E2eBytes.hex(E2eBytes.sha256(body))}",
                        "body: $BODY",
                    )
                )
            }
        }
    }

    private suspend fun deleteProbes(email: String, entries: List<CloudFile>): String {
        val probes = entries.filter { !it.isFolder && it.name.startsWith(FILE_PREFIX) }
        val outcomes = probes.map { file ->
            when (val deleted = appData.deleteEntry(file.id)) {
                is CloudResult.Success -> "deleted: ${file.name} (${file.id})"
                is CloudResult.Error -> "delete FAILED: ${file.name} (${file.id}): ${describe(deleted)}"
            }
        }
        Timber.i("DriveAppDataProbe: %d probe file(s) handled", probes.size)
        val summary = "probe files found: ${probes.size}; other entries in the root: ${entries.size - probes.size}"
        return lines(listOf("account: $email", summary) + outcomes)
    }

    private suspend fun gate(): Gate {
        val state = settledIdentityState()
        val email = (state as? PrimaryGoogleAccountState.Bound)?.account?.email
        return when {
            email == null -> Gate.Closed(
                "identity state: ${state::class.simpleName}. Sign in under Settings -> Google account, then retry."
            )
            browserAuthManager.hasActiveSession() || browserAuthManager.hasStoredCredentials() ->
                Gate.Closed(BROWSER_SESSION_REASON)
            else -> Gate.Open(email)
        }
    }

    // The stored binding is restored asynchronously after process start, so a probe fired by adb on a cold
    // start would otherwise read Unbound for an account that is in fact signed in.
    private suspend fun settledIdentityState(): PrimaryGoogleAccountState =
        withTimeoutOrNull(RESTORE_WAIT_MS) {
            identityRepository.state.first { it !is PrimaryGoogleAccountState.Unbound }
        } ?: identityRepository.state.value

    private fun randomHex(): String = E2eBytes.hex(ByteArray(RANDOM_BYTES).also(SecureRandom()::nextBytes))

    private fun describe(error: CloudResult.Error): String =
        error.cause?.let { "${error.message} (${it.javaClass.simpleName})" } ?: error.message

    private fun lines(parts: List<String>): String = parts.joinToString("\n")

    private data class Consent(val granted: Boolean, val note: String)

    private sealed interface Gate {
        data class Open(val email: String) : Gate
        data class Closed(val reason: String) : Gate
    }

    private companion object {
        const val ROOT = GoogleDriveAppDataApi.APP_DATA_FOLDER_ALIAS
        const val FILE_PREFIX = "fmsx-probe-android-"
        const val FILE_SUFFIX = ".json"
        const val MIME_JSON = "application/json"
        const val BODY = """{"probe":"fmsx-probe-android","purpose":"DEVICE-EXCHANGE 8.2 item B","schema":1}"""
        const val RANDOM_BYTES = 4
        const val RESTORE_WAIT_MS = 3_000L
        const val BROWSER_SESSION_REASON =
            "A browser (AppAuth) Google sign-in is stored; its token carries the whole consented scope set, " +
                "not drive.appdata alone. Nothing done."
    }
}
