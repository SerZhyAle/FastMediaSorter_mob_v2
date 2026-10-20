package com.sza.fastmediasorter.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminCodec
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminSession
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeAdminAccount
import com.sza.fastmediasorter.domain.model.ExchangeAdminAccountAdded
import com.sza.fastmediasorter.domain.model.ExchangeAdminCreatedInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminDevice
import com.sza.fastmediasorter.domain.model.ExchangeAdminInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminOwnLimits
import com.sza.fastmediasorter.domain.model.ExchangeAdminPolicy
import com.sza.fastmediasorter.domain.model.ExchangeAdminSharedInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The operations of contract DEVICE-EXCHANGE 1.4 `REMOTE-ADMIN.md` that a phone carries, read into the reply
 * models. It keeps no session and no reply: the caller owns the [DeviceExchangeAdminSession] and closes it. Every
 * failure is the client's exception, unchanged.
 */
@Singleton
class ExchangeServerAdminRepository @Inject constructor(
    private val client: DeviceExchangeAdminClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** The [password] is cleared by the client once it is written. */
    suspend fun signIn(entry: DeviceExchangeAccountEntry, password: CharArray): DeviceExchangeAdminSession {
        var opened: DeviceExchangeAdminSession? = null
        return try {
            withContext(ioDispatcher) { client.signIn(entry, password).also { opened = it } }
        } catch (cancelled: CancellationException) {
            // Cancellation can win the dispatch back to the caller after the stream was opened.
            withContext(ioDispatcher + NonCancellable) { opened?.close() }
            throw cancelled
        }
    }

    suspend fun status(session: DeviceExchangeAdminSession): ExchangeAdminStatus =
        DeviceExchangeAdminCodec.readStatus(call(session, DeviceExchangeAdminCodec.OP_STATUS))

    suspend fun accounts(session: DeviceExchangeAdminSession): List<ExchangeAdminAccount> =
        DeviceExchangeAdminCodec.readAccounts(call(session, DeviceExchangeAdminCodec.OP_ACCOUNT_LIST))

    suspend fun addAccount(
        session: DeviceExchangeAdminSession,
        login: String,
        password: CharArray,
        kind: String,
    ): ExchangeAdminAccountAdded = withPassword(password) { secret ->
        DeviceExchangeAdminCodec.readAccountAdded(
            call(session, DeviceExchangeAdminCodec.OP_ACCOUNT_ADD) {
                addProperty(DeviceExchangeAdminCodec.KEY_LOGIN, login)
                addProperty(DeviceExchangeAdminCodec.KEY_PASSWORD, secret)
                addProperty(DeviceExchangeAdminCodec.KEY_KIND, kind)
            },
        )
    }

    /** Signs out every device of the account; returns how many. */
    suspend fun setPassword(session: DeviceExchangeAdminSession, login: String, password: CharArray): Int =
        withPassword(password) { secret ->
            DeviceExchangeAdminCodec.readSignedOut(
                call(session, DeviceExchangeAdminCodec.OP_ACCOUNT_PASSWD) {
                    addProperty(DeviceExchangeAdminCodec.KEY_LOGIN, login)
                    addProperty(DeviceExchangeAdminCodec.KEY_PASSWORD, secret)
                },
            )
        }

    suspend fun setKind(session: DeviceExchangeAdminSession, login: String, kind: String): Boolean =
        DeviceExchangeAdminCodec.readChanged(
            call(session, DeviceExchangeAdminCodec.OP_ACCOUNT_KIND) {
                addProperty(DeviceExchangeAdminCodec.KEY_LOGIN, login)
                addProperty(DeviceExchangeAdminCodec.KEY_KIND, kind)
            },
        )

    suspend fun removeAccount(session: DeviceExchangeAdminSession, login: String) {
        call(session, DeviceExchangeAdminCodec.OP_ACCOUNT_REMOVE) {
            addProperty(DeviceExchangeAdminCodec.KEY_LOGIN, login)
        }
    }

    /** Replaces the account's own limits as a whole; empty [limits] clear them. */
    suspend fun setAccountLimits(session: DeviceExchangeAdminSession, login: String, limits: ExchangeAdminOwnLimits) {
        call(session, DeviceExchangeAdminCodec.OP_ACCOUNT_LIMITS) {
            addProperty(DeviceExchangeAdminCodec.KEY_LOGIN, login)
            add(DeviceExchangeAdminCodec.KEY_LIMITS, DeviceExchangeAdminCodec.ownLimitsJson(limits))
        }
    }

    suspend fun devices(session: DeviceExchangeAdminSession, login: String): List<ExchangeAdminDevice> =
        DeviceExchangeAdminCodec.readDevices(
            call(session, DeviceExchangeAdminCodec.OP_DEVICE_LIST) {
                addProperty(DeviceExchangeAdminCodec.KEY_LOGIN, login)
            },
        )

    suspend fun revokeDevice(session: DeviceExchangeAdminSession, login: String, deviceId: String) {
        call(session, DeviceExchangeAdminCodec.OP_DEVICE_REVOKE) {
            addProperty(DeviceExchangeAdminCodec.KEY_LOGIN, login)
            addProperty(DeviceExchangeAdminCodec.KEY_DEVICE_ID, deviceId)
        }
    }

    suspend fun createInvitation(
        session: DeviceExchangeAdminSession,
        kind: String,
        expectedLogin: String?,
    ): ExchangeAdminCreatedInvitation = DeviceExchangeAdminCodec.readCreatedInvitation(
        call(session, DeviceExchangeAdminCodec.OP_INVITATION_CREATE) {
            addProperty(DeviceExchangeAdminCodec.KEY_KIND, kind)
            expectedLogin?.let { addProperty(DeviceExchangeAdminCodec.KEY_EXPECTED_LOGIN, it) }
        },
    )

    suspend fun invitations(session: DeviceExchangeAdminSession): List<ExchangeAdminInvitation> =
        DeviceExchangeAdminCodec.readInvitations(call(session, DeviceExchangeAdminCodec.OP_INVITATION_LIST))

    suspend fun withdrawInvitation(session: DeviceExchangeAdminSession, codeHash: String) {
        call(session, DeviceExchangeAdminCodec.OP_INVITATION_WITHDRAW) {
            addProperty(DeviceExchangeAdminCodec.KEY_CODE_HASH, codeHash)
        }
    }

    suspend fun sharedInvitation(session: DeviceExchangeAdminSession): ExchangeAdminSharedInvitation =
        DeviceExchangeAdminCodec.readSharedInvitation(
            call(session, DeviceExchangeAdminCodec.OP_SHARED_INVITATION_STATUS),
        )

    /** An empty [password] turns the shared invitation off (item AP). */
    suspend fun setSharedInvitation(session: DeviceExchangeAdminSession, password: CharArray, kind: String) {
        withPassword(password) { secret ->
            call(session, DeviceExchangeAdminCodec.OP_SHARED_INVITATION_SET) {
                addProperty(DeviceExchangeAdminCodec.KEY_PASSWORD, secret)
                addProperty(DeviceExchangeAdminCodec.KEY_KIND, kind)
            }
        }
    }

    suspend fun policy(session: DeviceExchangeAdminSession): ExchangeAdminPolicy =
        DeviceExchangeAdminCodec.readPolicy(call(session, DeviceExchangeAdminCodec.OP_POLICY_STATUS))

    /** A null member stays as it is on the server; the reply is the policy now in force. */
    suspend fun setPolicy(
        session: DeviceExchangeAdminSession,
        maxAccounts: Int?,
        limits: ExchangeAdminOwnLimits?,
    ): ExchangeAdminPolicy = DeviceExchangeAdminCodec.readPolicy(
        call(session, DeviceExchangeAdminCodec.OP_POLICY_SET) {
            maxAccounts?.let { addProperty(DeviceExchangeAdminCodec.KEY_MAX_ACCOUNTS, it) }
            limits?.let { add(DeviceExchangeAdminCodec.KEY_LIMITS, DeviceExchangeAdminCodec.ownLimitsJson(it)) }
        },
    )

    private suspend fun call(
        session: DeviceExchangeAdminSession,
        op: String,
        members: JsonObject.() -> Unit = {},
    ): JsonElement? = withContext(ioDispatcher) { session.call(op, members) }

    // The array is cleared whatever the reply, so a typed password does not outlive the request.
    private inline fun <T> withPassword(password: CharArray, block: (String) -> T): T = try {
        block(String(password))
    } finally {
        password.fill(CLEARED)
    }

    private companion object {
        const val CLEARED = '\u0000'
    }
}
