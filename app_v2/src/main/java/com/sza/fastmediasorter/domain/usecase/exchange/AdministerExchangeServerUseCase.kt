package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminReplyException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminSession
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAdminSessionLapsedException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeRefusedException
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeWireException
import com.sza.fastmediasorter.data.repository.ExchangeServerAdminRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeAdminAccount
import com.sza.fastmediasorter.domain.model.ExchangeAdminAccountAdded
import com.sza.fastmediasorter.domain.model.ExchangeAdminCreatedInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminDevice
import com.sza.fastmediasorter.domain.model.ExchangeAdminInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminOutcome
import com.sza.fastmediasorter.domain.model.ExchangeAdminOwnLimits
import com.sza.fastmediasorter.domain.model.ExchangeAdminPolicy
import com.sza.fastmediasorter.domain.model.ExchangeAdminReplyCode
import com.sza.fastmediasorter.domain.model.ExchangeAdminSharedInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminSignIn
import com.sza.fastmediasorter.domain.model.ExchangeAdminStatus
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * The one door of the administration screen to the server (S4356 ADR-3): every refusal and every silence comes back
 * in the words of the shared table with the check ladder, every failed reply as its code, never as an exception.
 * [host] on each operation is the entry's address, used to walk the ladder when the stream fails.
 */
class AdministerExchangeServerUseCase @Inject constructor(
    private val repository: ExchangeServerAdminRepository,
    private val diagnose: DiagnoseExchangeFailureUseCase,
) {

    suspend fun signIn(entry: DeviceExchangeAccountEntry, password: CharArray): ExchangeAdminSignIn = try {
        ExchangeAdminSignIn.SignedIn(repository.signIn(entry, password))
    } catch (e: DeviceExchangeRefusedException) {
        Timber.w("Exchange admin sign-in refused (%s)", e.reason)
        ExchangeAdminSignIn.Refused(diagnose(ExchangeMessage.ofWire(e.reason), entry.host))
    } catch (e: DeviceExchangeWireException) {
        Timber.i(e, "Exchange admin sign-in not answered")
        ExchangeAdminSignIn.Refused(diagnose(ExchangeMessage.UNANSWERED, entry.host))
    } catch (e: IOException) {
        Timber.i(e, "Exchange admin server not reached")
        ExchangeAdminSignIn.Refused(diagnose.fromCause(e, entry.host))
    }

    suspend fun status(session: DeviceExchangeAdminSession, host: String?): ExchangeAdminOutcome<ExchangeAdminStatus> =
        run(host) { repository.status(session) }

    suspend fun accounts(
        session: DeviceExchangeAdminSession,
        host: String?,
    ): ExchangeAdminOutcome<List<ExchangeAdminAccount>> = run(host) { repository.accounts(session) }

    suspend fun addAccount(
        session: DeviceExchangeAdminSession,
        host: String?,
        login: String,
        password: CharArray,
        kind: String,
    ): ExchangeAdminOutcome<ExchangeAdminAccountAdded> =
        run(host) { repository.addAccount(session, login, password, kind) }

    suspend fun setPassword(
        session: DeviceExchangeAdminSession,
        host: String?,
        login: String,
        password: CharArray,
    ): ExchangeAdminOutcome<Int> = run(host) { repository.setPassword(session, login, password) }

    suspend fun setKind(
        session: DeviceExchangeAdminSession,
        host: String?,
        login: String,
        kind: String,
    ): ExchangeAdminOutcome<Boolean> = run(host) { repository.setKind(session, login, kind) }

    suspend fun removeAccount(
        session: DeviceExchangeAdminSession,
        host: String?,
        login: String,
    ): ExchangeAdminOutcome<Unit> = run(host) { repository.removeAccount(session, login) }

    suspend fun setAccountLimits(
        session: DeviceExchangeAdminSession,
        host: String?,
        login: String,
        limits: ExchangeAdminOwnLimits,
    ): ExchangeAdminOutcome<Unit> = run(host) { repository.setAccountLimits(session, login, limits) }

    suspend fun devices(
        session: DeviceExchangeAdminSession,
        host: String?,
        login: String,
    ): ExchangeAdminOutcome<List<ExchangeAdminDevice>> = run(host) { repository.devices(session, login) }

    suspend fun revokeDevice(
        session: DeviceExchangeAdminSession,
        host: String?,
        login: String,
        deviceId: String,
    ): ExchangeAdminOutcome<Unit> = run(host) { repository.revokeDevice(session, login, deviceId) }

    suspend fun createInvitation(
        session: DeviceExchangeAdminSession,
        host: String?,
        kind: String,
        expectedLogin: String?,
    ): ExchangeAdminOutcome<ExchangeAdminCreatedInvitation> =
        run(host) { repository.createInvitation(session, kind, expectedLogin) }

    suspend fun invitations(
        session: DeviceExchangeAdminSession,
        host: String?,
    ): ExchangeAdminOutcome<List<ExchangeAdminInvitation>> = run(host) { repository.invitations(session) }

    suspend fun withdrawInvitation(
        session: DeviceExchangeAdminSession,
        host: String?,
        codeHash: String,
    ): ExchangeAdminOutcome<Unit> = run(host) { repository.withdrawInvitation(session, codeHash) }

    suspend fun sharedInvitation(
        session: DeviceExchangeAdminSession,
        host: String?,
    ): ExchangeAdminOutcome<ExchangeAdminSharedInvitation> = run(host) { repository.sharedInvitation(session) }

    suspend fun setSharedInvitation(
        session: DeviceExchangeAdminSession,
        host: String?,
        password: CharArray,
        kind: String,
    ): ExchangeAdminOutcome<Unit> = run(host) { repository.setSharedInvitation(session, password, kind) }

    suspend fun policy(session: DeviceExchangeAdminSession, host: String?): ExchangeAdminOutcome<ExchangeAdminPolicy> =
        run(host) { repository.policy(session) }

    suspend fun setPolicy(
        session: DeviceExchangeAdminSession,
        host: String?,
        maxAccounts: Int?,
        limits: ExchangeAdminOwnLimits?,
    ): ExchangeAdminOutcome<ExchangeAdminPolicy> = run(host) { repository.setPolicy(session, maxAccounts, limits) }

    // Cancellation is not an IOException, so a cancelled operation propagates past every branch here.
    private suspend fun <T> run(host: String?, block: suspend () -> T): ExchangeAdminOutcome<T> = try {
        ExchangeAdminOutcome.Done(block())
    } catch (e: DeviceExchangeAdminReplyException) {
        Timber.w("Exchange admin reply failed (%s)", e.code)
        ExchangeAdminOutcome.Failed(ExchangeAdminReplyCode.ofWire(e.code))
    } catch (e: DeviceExchangeAdminSessionLapsedException) {
        Timber.i(e, "Exchange admin session lapsed")
        ExchangeAdminOutcome.Lapsed
    } catch (e: DeviceExchangeWireException) {
        Timber.i(e, "Exchange admin request not answered")
        ExchangeAdminOutcome.Unreachable(diagnose(ExchangeMessage.UNANSWERED, host))
    } catch (e: IOException) {
        Timber.i(e, "Exchange admin stream failed")
        ExchangeAdminOutcome.Unreachable(diagnose.fromCause(e, host))
    }
}
