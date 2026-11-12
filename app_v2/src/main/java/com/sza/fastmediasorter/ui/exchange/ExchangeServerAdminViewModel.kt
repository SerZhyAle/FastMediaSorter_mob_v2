package com.sza.fastmediasorter.ui.exchange

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeAdminAccount
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
import com.sza.fastmediasorter.domain.model.ExchangeDiagnosis
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.domain.usecase.exchange.AdministerExchangeServerUseCase
import com.sza.fastmediasorter.domain.usecase.exchange.ManageExchangeAccountsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ExchangeAdminScreenState {
    data class SignedOut(val address: String, val failure: ExchangeDiagnosis? = null) : ExchangeAdminScreenState
    data object SigningIn : ExchangeAdminScreenState
    data object Disabled : ExchangeAdminScreenState
    data class SignedIn(
        val status: ExchangeAdminStatus?,
        val accounts: List<ExchangeAdminAccount>? = null,
        val invitations: List<ExchangeAdminInvitation>? = null,
        val shared: ExchangeAdminSharedInvitation? = null,
        val policy: ExchangeAdminPolicy? = null,
    ) : ExchangeAdminScreenState
    data object Lapsed : ExchangeAdminScreenState
    data object MissingEntry : ExchangeAdminScreenState
}

/** The devices of one account, pushed to the dialog that asked for them. */
data class ExchangeAdminAccountDevices(val login: String, val devices: List<ExchangeAdminDevice>)

/** The reply of a password change: this many devices of the account must sign in again. */
data class ExchangeAdminPasswordChanged(val login: String, val signedOutDevices: Int)

@HiltViewModel
class ExchangeServerAdminViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    accounts: ManageExchangeAccountsUseCase,
    private val admin: AdministerExchangeServerUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ExchangeAdminScreenState>(ExchangeAdminScreenState.SignedOut(""))
    val state = mutableState.asStateFlow()

    private val replyFailuresChannel = Channel<ExchangeAdminReplyCode>(Channel.BUFFERED)
    private val createdInvitationChannel = Channel<ExchangeAdminCreatedInvitation>(Channel.BUFFERED)
    private val devicesChannel = Channel<ExchangeAdminAccountDevices>(Channel.BUFFERED)
    private val passwordChannel = Channel<ExchangeAdminPasswordChanged>(Channel.BUFFERED)

    /** A failed `admin-reply`, worded by the screen with `ExchangeMessageText.adminReply`. */
    val replyFailures: Flow<ExchangeAdminReplyCode> = replyFailuresChannel.receiveAsFlow()

    /** The one-time invitation. It is delivered once and held nowhere else, per strategic 5.2. */
    val createdInvitation: Flow<ExchangeAdminCreatedInvitation> = createdInvitationChannel.receiveAsFlow()

    /** A fresh device list of one account, for the dialog that shows it. */
    val devicesOfAccount: Flow<ExchangeAdminAccountDevices> = devicesChannel.receiveAsFlow()

    /** A changed account password, with the count of devices that were signed out. */
    val passwordChanged: Flow<ExchangeAdminPasswordChanged> = passwordChannel.receiveAsFlow()

    private var entry: DeviceExchangeAccountEntry? = null
    private var session: ExchangeAdminSignIn.SignedIn? = null
    private var work: Job? = null

    init {
        val identity = savedStateHandle.get<String>(IDENTITY)
        viewModelScope.launch {
            accounts.entries.collect { entries ->
                entry = entries.firstOrNull { it.identity == identity }
                if (entry == null) {
                    endSession()
                    mutableState.value = ExchangeAdminScreenState.MissingEntry
                } else if (mutableState.value is ExchangeAdminScreenState.SignedOut) {
                    mutableState.value = signedOut()
                }
            }
        }
    }

    /** The host of the entry on screen, for naming the one shared invitation file. */
    val serverHost: String?
        get() = entry?.host

    fun signIn(password: CharArray) {
        val selected = entry
        if (selected == null || work?.isActive == true || session != null) {
            password.fill(CLEARED)
            return
        }
        mutableState.value = ExchangeAdminScreenState.SigningIn
        work = viewModelScope.launch {
            try {
                when (val reply = admin.signIn(selected, password)) {
                    is ExchangeAdminSignIn.SignedIn -> {
                        if (!currentCoroutineContext().isActive) {
                            closeSession(reply)
                            return@launch
                        }
                        session = reply
                        mutableState.value = ExchangeAdminScreenState.SignedIn(null)
                        val statusReply = admin.status(reply.session, selected.host)
                        if (handleOutcome(statusReply) && statusReply is ExchangeAdminOutcome.Done) {
                            updateSignedIn { it.copy(status = statusReply.value) }
                            loadSections(reply, selected)
                        }
                    }
                    is ExchangeAdminSignIn.Refused -> {
                        mutableState.value = if (reply.diagnosis.message == ExchangeMessage.ADMIN_DISABLED) {
                            ExchangeAdminScreenState.Disabled
                        } else {
                            signedOut(reply.diagnosis)
                        }
                    }
                }
            } finally {
                password.fill(CLEARED)
            }
        }.also { job -> job.invokeOnCompletion { password.fill(CLEARED) } }
    }

    fun addAccount(login: String, password: CharArray, kind: String) = operate(
        password,
        { held, selected -> admin.addAccount(held.session, selected.host, login, password, kind) },
    ) { _, held, selected ->
        replaceAccounts(held, selected)
    }

    fun setPassword(login: String, password: CharArray) = operate(
        password,
        { held, selected -> admin.setPassword(held.session, selected.host, login, password) },
    ) { signedOutDevices, held, selected ->
        passwordChannel.send(ExchangeAdminPasswordChanged(login, signedOutDevices))
        replaceAccounts(held, selected)
    }

    fun setKind(login: String, kind: String) = operate(
        null,
        { held, selected -> admin.setKind(held.session, selected.host, login, kind) },
    ) { _, held, selected ->
        replaceAccounts(held, selected)
    }

    fun removeAccount(login: String) = operate(
        null,
        { held, selected -> admin.removeAccount(held.session, selected.host, login) },
    ) { _, held, selected ->
        replaceAccounts(held, selected)
    }

    fun setAccountLimits(login: String, limits: ExchangeAdminOwnLimits) = operate(
        null,
        { held, selected -> admin.setAccountLimits(held.session, selected.host, login, limits) },
    ) { _, held, selected ->
        replaceAccounts(held, selected)
    }

    fun loadDevices(login: String) = operate(
        null,
        { held, selected -> admin.devices(held.session, selected.host, login) },
    ) { devices, _, _ ->
        devicesChannel.send(ExchangeAdminAccountDevices(login, devices))
    }

    fun revokeDevice(login: String, deviceId: String) = operate(
        null,
        { held, selected -> admin.revokeDevice(held.session, selected.host, login, deviceId) },
    ) { _, held, selected ->
        if (replaceAccounts(held, selected)) pushDevices(held, selected, login)
    }

    fun createInvitation(kind: String, expectedLogin: String?) = operate(
        null,
        { held, selected -> admin.createInvitation(held.session, selected.host, kind, expectedLogin) },
    ) { created, held, selected ->
        createdInvitationChannel.send(created)
        replaceInvitations(held, selected)
    }

    fun withdrawInvitation(codeHash: String) = operate(
        null,
        { held, selected -> admin.withdrawInvitation(held.session, selected.host, codeHash) },
    ) { _, held, selected ->
        replaceInvitations(held, selected)
    }

    /** An empty [password] turns the shared invitation off (contract item AP). */
    fun setSharedInvitation(password: CharArray, kind: String) = operate(
        password,
        { held, selected -> admin.setSharedInvitation(held.session, selected.host, password, kind) },
    ) { _, held, selected ->
        replaceShared(held, selected)
    }

    /** A null member stays as it is on the server; the reply is the policy now in force. */
    fun setPolicy(maxAccounts: Int?, limits: ExchangeAdminOwnLimits?) = operate(
        null,
        { held, selected -> admin.setPolicy(held.session, selected.host, maxAccounts, limits) },
    ) { policy, _, _ ->
        updateSignedIn { it.copy(policy = policy) }
    }

    fun endSession() {
        work?.cancel()
        work = null
        session?.let(::closeSession)
        session = null
        mutableState.value = signedOut()
    }

    private fun signedOut(failure: ExchangeDiagnosis? = null) = ExchangeAdminScreenState.SignedOut(
        entry?.let { "${it.host}:${it.port}" }.orEmpty(),
        failure,
    )

    /** The session and entry of a runnable request, or null: the password is erased and the request is dropped. */
    private fun begin(password: CharArray?): Pair<ExchangeAdminSignIn.SignedIn, DeviceExchangeAccountEntry>? {
        val held = session
        val selected = entry
        if (held == null || selected == null || work?.isActive == true) {
            password?.fill(CLEARED)
            return null
        }
        return held to selected
    }

    /** One request, then only its section is re-read; every other outcome is handled by [handleOutcome]. */
    private fun <T> operate(
        password: CharArray?,
        request: suspend (ExchangeAdminSignIn.SignedIn, DeviceExchangeAccountEntry) -> ExchangeAdminOutcome<T>,
        onDone: suspend (T, ExchangeAdminSignIn.SignedIn, DeviceExchangeAccountEntry) -> Unit,
    ) {
        val (held, selected) = begin(password) ?: return
        work = viewModelScope.launch {
            val reply = request(held, selected)
            if (reply is ExchangeAdminOutcome.Done) {
                onDone(reply.value, held, selected)
            } else {
                handleOutcome(reply)
            }
        }
        password?.let { value -> work?.invokeOnCompletion { value.fill(CLEARED) } }
    }

    /** True when the caller may go on; a lapse or a broken stream already ended the session here. */
    private suspend fun handleOutcome(reply: ExchangeAdminOutcome<*>): Boolean = when (reply) {
        is ExchangeAdminOutcome.Done -> true
        is ExchangeAdminOutcome.Failed -> {
            replyFailuresChannel.send(reply.code)
            true
        }
        ExchangeAdminOutcome.Lapsed -> {
            lapse()
            false
        }
        is ExchangeAdminOutcome.Unreachable -> {
            unreachable(reply.diagnosis)
            false
        }
    }

    private suspend fun loadSections(held: ExchangeAdminSignIn.SignedIn, selected: DeviceExchangeAccountEntry) {
        if (replaceAccounts(held, selected) && replaceInvitations(held, selected)) {
            if (replaceShared(held, selected)) {
                replacePolicy(held, selected)
            }
        }
    }

    private suspend fun replaceAccounts(
        held: ExchangeAdminSignIn.SignedIn,
        selected: DeviceExchangeAccountEntry,
    ): Boolean = read(
        { admin.accounts(held.session, selected.host) },
    ) { current, value ->
        current.copy(accounts = value)
    }

    private suspend fun replaceInvitations(
        held: ExchangeAdminSignIn.SignedIn,
        selected: DeviceExchangeAccountEntry,
    ): Boolean = read(
        { admin.invitations(held.session, selected.host) },
    ) { current, value ->
        current.copy(invitations = value)
    }

    private suspend fun replaceShared(
        held: ExchangeAdminSignIn.SignedIn,
        selected: DeviceExchangeAccountEntry,
    ): Boolean = read(
        { admin.sharedInvitation(held.session, selected.host) },
    ) { current, value ->
        current.copy(shared = value)
    }

    private suspend fun replacePolicy(
        held: ExchangeAdminSignIn.SignedIn,
        selected: DeviceExchangeAccountEntry,
    ): Boolean = read(
        { admin.policy(held.session, selected.host) },
    ) { current, value ->
        current.copy(policy = value)
    }

    private suspend fun pushDevices(
        held: ExchangeAdminSignIn.SignedIn,
        selected: DeviceExchangeAccountEntry,
        login: String,
    ): Boolean = when (val reply = admin.devices(held.session, selected.host, login)) {
        is ExchangeAdminOutcome.Done -> {
            devicesChannel.send(ExchangeAdminAccountDevices(login, reply.value))
            true
        }
        else -> handleOutcome(reply)
    }

    private suspend fun <T> read(
        request: suspend () -> ExchangeAdminOutcome<T>,
        into: (ExchangeAdminScreenState.SignedIn, T) -> ExchangeAdminScreenState.SignedIn,
    ): Boolean = when (val reply = request()) {
        is ExchangeAdminOutcome.Done -> {
            updateSignedIn { into(it, reply.value) }
            true
        }
        else -> handleOutcome(reply)
    }

    private fun updateSignedIn(transform: (ExchangeAdminScreenState.SignedIn) -> ExchangeAdminScreenState.SignedIn) {
        (mutableState.value as? ExchangeAdminScreenState.SignedIn)?.let { mutableState.value = transform(it) }
    }

    private fun lapse() {
        endSession()
        mutableState.value = ExchangeAdminScreenState.Lapsed
    }

    private fun unreachable(diagnosis: ExchangeDiagnosis) {
        endSession()
        mutableState.value = signedOut(diagnosis)
    }

    private fun closeSession(held: ExchangeAdminSignIn.SignedIn) {
        // close writes bye, so cleanup must outlive viewModelScope cancellation and stay off the UI thread.
        viewModelScope.launch(ioDispatcher + NonCancellable) { held.session.close() }
    }

    override fun onCleared() {
        endSession()
        super.onCleared()
    }

    companion object {
        const val IDENTITY = "identity"
        private const val CLEARED = '\u0000'
    }
}
