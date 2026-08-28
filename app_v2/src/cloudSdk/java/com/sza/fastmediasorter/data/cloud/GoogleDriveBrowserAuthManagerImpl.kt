package com.sza.fastmediasorter.data.cloud

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.queryIntentActivitiesCompat
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveCredentialsManager
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Browser-based Google Drive authorization driven by AppAuth.
 *
 * S0403: lives in `src/cloudSdk` because it imports `net.openid.appauth` directly. Shared code
 * depends on the [GoogleDriveBrowserAuthManager] contract in `src/main`.
 */
@Singleton
class GoogleDriveBrowserAuthManagerImpl @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val credentialsManager: GoogleDriveCredentialsManager,
    private val networkCredentialsRepository: NetworkCredentialsRepository
) : GoogleDriveBrowserAuthManager {

    private data class StoredCredential(
        val authStateJson: String,
        val accountEmail: String
    ) {
        fun toJson(): String = JSONObject()
            .put(KEY_TYPE, TYPE_APP_AUTH)
            .put(KEY_AUTH_STATE, authStateJson)
            .put(KEY_ACCOUNT_EMAIL, accountEmail)
            .toString()
    }

    private data class ActiveCredential(
        val authState: AuthState,
        val accountEmail: String
    )

    @Volatile
    private var activeCredential: ActiveCredential? = null

    @Volatile
    private var pendingInteractiveResult: AuthResult? = null

    override fun startInteractiveSignIn(activity: Activity) {
        if (!hasAnyBrowser()) {
            throw GoogleDriveBrowserUnavailableException(
                activity.getString(R.string.s0294_google_drive_browser_required_message)
            )
        }

        pendingInteractiveResult = null

        val completionIntent = Intent(activity, com.sza.fastmediasorter.ui.cloudauth.GoogleDriveAuthCompletionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val cancelIntent = Intent(activity, com.sza.fastmediasorter.ui.cloudauth.GoogleDriveAuthCompletionActivity::class.java)
            .setAction(ACTION_AUTH_CANCELLED)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val completionPendingIntent = PendingIntent.getActivity(activity, REQUEST_CODE_COMPLETE, completionIntent, flags)
        val cancelPendingIntent = PendingIntent.getActivity(activity, REQUEST_CODE_CANCEL, cancelIntent, flags)

        val service = AuthorizationService(activity)
        try {
            service.performAuthorizationRequest(buildAuthorizationRequest(activity), completionPendingIntent, cancelPendingIntent)
        } finally {
            service.dispose()
        }
    }

    override suspend fun completeAuthorizationIntent(intent: Intent): AuthResult {
        val result = when {
            intent.action == ACTION_AUTH_CANCELLED -> AuthResult.Cancelled
            else -> finalizeAuthorizationIntent(intent)
        }
        pendingInteractiveResult = result
        return result
    }

    override fun consumePendingInteractiveResult(): AuthResult? {
        val result = pendingInteractiveResult
        pendingInteractiveResult = null
        return result
    }

    override fun hasActiveSession(): Boolean = activeCredential != null

    override fun peekStoredAccountEmail(): String? = activeCredential?.accountEmail
        ?: loadStoredCredential()?.accountEmail

    override fun hasStoredCredentials(accountEmail: String?): Boolean =
        loadStoredCredential(accountEmail) != null

    override suspend fun ensureActiveFromStored(accountEmail: String?): Boolean {
        val stored = loadStoredCredential(accountEmail) ?: return false
        return restoreCredentialBlob(stored.toJson())
    }

    override fun restoreCredentialBlob(credentialsJson: String): Boolean {
        return runCatching {
            val stored = parseStoredCredential(credentialsJson) ?: return false
            activeCredential = ActiveCredential(
                authState = AuthState.jsonDeserialize(stored.authStateJson),
                accountEmail = stored.accountEmail
            )
            true
        }.getOrElse {
            Timber.e(it, "Failed to restore Google Drive browser auth state")
            false
        }
    }

    override suspend fun getFreshAccessToken(): String? {
        val current = activeCredential ?: return null
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val service = AuthorizationService(appContext)
                current.authState.performActionWithFreshTokens(service) { accessToken, _, authException ->
                    service.dispose()
                    if (authException != null || accessToken.isNullOrBlank()) {
                        Timber.w(authException, "Google Drive browser token refresh failed")
                        continuation.resume(null)
                        return@performActionWithFreshTokens
                    }

                    persistActiveState(current.accountEmail)
                    continuation.resume(accessToken)
                }
            }
        }
    }

    private suspend fun finalizeAuthorizationIntent(intent: Intent): AuthResult {
        val response = AuthorizationResponse.fromIntent(intent)
        val authException = AuthorizationException.fromIntent(intent)

        if (response == null) {
            if (authException != null) {
                Timber.w(authException, "Google Drive browser auth returned no authorization response")
            }
            return AuthResult.Error(appContext.getString(R.string.s0294_google_drive_browser_auth_failed_message))
        }

        val authState = AuthState(response, authException)
        val tokenResult = exchangeToken(response)
        authState.update(tokenResult.response, tokenResult.exception)

        if (tokenResult.exception != null || tokenResult.response == null) {
            Timber.e(tokenResult.exception, "Google Drive browser token exchange failed")
            return AuthResult.Error(appContext.getString(R.string.s0294_google_drive_browser_auth_failed_message))
        }

        val accountEmail = tokenResult.response.idToken?.let(::parseEmailFromIdToken)
            ?: tokenResult.response.accessToken?.let { fetchUserEmail(it) }

        if (accountEmail.isNullOrBlank()) {
            Timber.e("Google Drive browser auth completed without an account email")
            return AuthResult.Error(appContext.getString(R.string.s0294_google_drive_browser_auth_failed_message))
        }

        activeCredential = ActiveCredential(authState = authState, accountEmail = accountEmail)
        persistActiveState(accountEmail)
        registerAccountInDatabase(accountEmail)

        return AuthResult.Success(
            accountName = accountEmail,
            credentialsJson = StoredCredential(authState.jsonSerializeString(), accountEmail).toJson()
        )
    }

    private suspend fun exchangeToken(response: AuthorizationResponse): TokenExchangeResult =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val service = AuthorizationService(appContext)
                service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, authException ->
                    service.dispose()
                    continuation.resume(TokenExchangeResult(tokenResponse, authException))
                }
            }
        }

    private suspend fun fetchUserEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = java.net.URL(USER_INFO_URL).openConnection() as java.net.HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.connectTimeout = NETWORK_TIMEOUT_MS
                connection.readTimeout = NETWORK_TIMEOUT_MS
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).optString("email").takeIf { it.isNotBlank() }
            } finally {
                connection.disconnect()
            }
        }.getOrElse {
            Timber.w(it, "Failed to fetch Google Drive browser-auth user info")
            null
        }
    }

    private fun parseEmailFromIdToken(idToken: String): String? = runCatching {
        val payload = idToken.split('.').getOrNull(1) ?: return null
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(String(decoded, Charsets.UTF_8)).optString("email").takeIf { it.isNotBlank() }
    }.getOrElse {
        Timber.w(it, "Failed to parse email from Google ID token")
        null
    }

    private suspend fun registerAccountInDatabase(email: String) {
        val existing = networkCredentialsRepository.getByTypeAndAccountId(CloudProvider.GOOGLE_DRIVE.name, email)
        if (existing != null) {
            return
        }

        val entity = NetworkCredentialsEntity.create(
            credentialId = UUID.randomUUID().toString(),
            type = CloudProvider.GOOGLE_DRIVE.name,
            server = "",
            port = 0,
            username = email,
            plaintextPassword = "",
            accountId = email
        )
        networkCredentialsRepository.insert(entity)
    }

    private fun persistActiveState(accountEmail: String) {
        val current = activeCredential ?: return
        credentialsManager.saveCredentials(
            StoredCredential(current.authState.jsonSerializeString(), accountEmail).toJson(),
            accountEmail
        )
    }

    private fun loadStoredCredential(accountEmail: String? = null): StoredCredential? {
        val json = credentialsManager.loadStoredCredentials(accountEmail) ?: return null
        return parseStoredCredential(json)
    }

    private fun parseStoredCredential(json: String): StoredCredential? = runCatching {
        val objectJson = JSONObject(json)
        if (objectJson.optString(KEY_TYPE) != TYPE_APP_AUTH) {
            return null
        }
        val authStateJson = objectJson.optString(KEY_AUTH_STATE)
        val accountEmail = objectJson.optString(KEY_ACCOUNT_EMAIL)
        if (authStateJson.isBlank() || accountEmail.isBlank()) {
            return null
        }
        StoredCredential(authStateJson = authStateJson, accountEmail = accountEmail)
    }.getOrElse {
        null
    }

    private fun buildAuthorizationRequest(context: Context): AuthorizationRequest =
        AuthorizationRequest.Builder(
            AUTH_SERVICE_CONFIG,
            context.getString(R.string.google_web_client_id),
            ResponseTypeValues.CODE,
            REDIRECT_URI
        )
            .setScope(SCOPES.joinToString(" "))
            // "prompt" is a built-in OAuth/OIDC param - AppAuth rejects it inside
            // setAdditionalParameters() (IllegalArgumentException). It must go through setPrompt().
            // "access_type" is Google-specific and is allowed as an additional parameter. S0746.
            .setPrompt("consent")
            .setAdditionalParameters(
                mapOf("access_type" to "offline")
            )
            .build()

    private fun hasAnyBrowser(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, AUTHORIZATION_URI)
        return appContext.packageManager.queryIntentActivitiesCompat(intent).isNotEmpty()
    }

    private data class TokenExchangeResult(
        val response: net.openid.appauth.TokenResponse?,
        val exception: AuthorizationException?
    )

    companion object {
        const val ACTION_AUTH_CANCELLED = "com.sza.fastmediasorter.action.GOOGLE_DRIVE_AUTH_CANCELLED"

        private const val REQUEST_CODE_COMPLETE = 29041
        private const val REQUEST_CODE_CANCEL = 29042
        private const val NETWORK_TIMEOUT_MS = 20_000
        private const val USER_INFO_URL = "https://openidconnect.googleapis.com/v1/userinfo"
        private const val TYPE_APP_AUTH = "appauth"
        private const val KEY_TYPE = "type"
        private const val KEY_AUTH_STATE = "auth_state"
        private const val KEY_ACCOUNT_EMAIL = "account_email"

        private val REDIRECT_URI: Uri = Uri.parse("com.sza.fastmediasorter:/oauth2redirect")
        private val AUTHORIZATION_URI: Uri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
        private val TOKEN_URI: Uri = Uri.parse("https://oauth2.googleapis.com/token")
        private val AUTH_SERVICE_CONFIG = AuthorizationServiceConfiguration(AUTHORIZATION_URI, TOKEN_URI)
        // Single source of truth (S0639): the browser/AppAuth sign-in must request the same
        // scope set as the GMS path so the two paths cannot diverge.
        private val SCOPES = GoogleDriveAuthPlugin.DRIVE_SIGN_IN_SCOPES.map { it.value }
    }
}