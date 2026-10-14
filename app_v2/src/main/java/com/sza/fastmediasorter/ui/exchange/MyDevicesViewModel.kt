package com.sza.fastmediasorter.ui.exchange

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeAddressChange
import com.sza.fastmediasorter.domain.model.DeviceExchangeEnrollResult
import com.sza.fastmediasorter.domain.model.DeviceExchangeRemoval
import com.sza.fastmediasorter.domain.model.ExchangeConnectionString
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringCodec
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringRefusal
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringResult
import com.sza.fastmediasorter.domain.model.ExchangeDiagnosis
import com.sza.fastmediasorter.domain.model.ExchangeEndpoint
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.domain.model.ExchangeServerCall
import com.sza.fastmediasorter.domain.model.IntakeTextDispatch
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.WatchExchangeHandoverOutcome
import com.sza.fastmediasorter.domain.usecase.exchange.DiagnoseExchangeFailureUseCase
import com.sza.fastmediasorter.domain.usecase.exchange.ManageExchangeAccountsUseCase
import com.sza.fastmediasorter.domain.usecase.intake.DispatchIntakeTextUseCase
import com.sza.fastmediasorter.domain.usecase.sftpserver.ExchangeProducerLeave
import com.sza.fastmediasorter.domain.usecase.sftpserver.ManageExchangeProducerUseCase
import com.sza.fastmediasorter.domain.usecase.wear.ConnectWatchToExchangeEntryUseCase
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeMessageText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/** What a server card says about its connection, read from one `list` of its account. */
sealed interface ExchangeServerStatus {
    data object Checking : ExchangeServerStatus
    data class Online(val deviceCount: Int) : ExchangeServerStatus
    data class Failed(val message: ExchangeMessage) : ExchangeServerStatus

    /** The server did not answer; what the person added stays in the list (item CZ `server-offline`). */
    data object Offline : ExchangeServerStatus
}

data class ExchangeServerCard(val entry: DeviceExchangeAccountEntry, val status: ExchangeServerStatus)

/** A text the screen shows once; [args] fill its placeholders, a nested message resolved first. */
data class MyDevicesMessage(@StringRes val text: Int, val args: List<Any> = emptyList())

/**
 * What the user typed into the sign-in dialog; the secrets leave memory with the dialog. With [invitation] (from
 * an invitation string) or [invitationPassword] (typed) the form registers a new account instead (19 item AQ).
 */
class ExchangeSignInRequest(
    val endpoint: ExchangeEndpoint,
    val login: String,
    val secret: String,
    val pairingCode: Boolean,
    val invitation: String? = null,
    val invitationPassword: String? = null,
) {
    val registers: Boolean get() = invitation != null || invitationPassword != null

    override fun toString(): String = "ExchangeSignInRequest(pairingCode=$pairingCode, registers=$registers)"
}

sealed interface ExchangeSignInState {
    data object Idle : ExchangeSignInState
    data object InProgress : ExchangeSignInState
    data class Failed(val diagnosis: ExchangeDiagnosis) : ExchangeSignInState

    /** The user must see [presented] and accept it before the entry pins it (amendment D). */
    data class NeedsTrust(
        val request: ExchangeSignInRequest,
        val presented: String,
        val pinned: String?,
    ) : ExchangeSignInState

    data class Done(val endpoint: ExchangeEndpoint, val registered: Boolean = false) : ExchangeSignInState
}

/** A password change or an account deletion asked from its own dialog, which stays open until it ends. */
sealed interface ExchangeAccountActionState {
    data object Idle : ExchangeAccountActionState
    data object InProgress : ExchangeAccountActionState
    data class Failed(val diagnosis: ExchangeDiagnosis) : ExchangeAccountActionState
    data class Done(val message: MyDevicesMessage) : ExchangeAccountActionState
}

/** What the sign-in form does with one piece of intake text or file content (S4155). */
sealed interface ExchangeIntakeOutcome {
    /** A server string was read; the form shows it, and the password stays for the user to type. */
    data class Filled(val value: ExchangeConnectionString) : ExchangeIntakeOutcome

    /** Anything else is one message: [text] is the single refusal or the update-app notice. */
    data class Refused(@StringRes val text: Int) : ExchangeIntakeOutcome
}

/** The device list of the server card the user opened. */
sealed interface ExchangeDevicesState {
    data object Closed : ExchangeDevicesState
    data object Loading : ExchangeDevicesState
    data class Loaded(val entry: DeviceExchangeAccountEntry, val devices: List<SftpRendezvousDevice>) :
        ExchangeDevicesState
    data class Failed(val diagnosis: ExchangeDiagnosis) : ExchangeDevicesState
}

@HiltViewModel
class MyDevicesViewModel @Inject constructor(
    private val accounts: ManageExchangeAccountsUseCase,
    private val producer: ManageExchangeProducerUseCase,
    private val connectWatchToEntry: ConnectWatchToExchangeEntryUseCase,
    private val diagnose: DiagnoseExchangeFailureUseCase,
    private val dispatchIntakeText: DispatchIntakeTextUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val statuses = MutableStateFlow<Map<String, ExchangeServerStatus>>(emptyMap())
    private val messageChannel = Channel<MyDevicesMessage>(Channel.BUFFERED)
    private val failureChannel = Channel<ExchangeDiagnosis>(Channel.BUFFERED)
    private val signInState = MutableStateFlow<ExchangeSignInState>(ExchangeSignInState.Idle)
    private val devicesState = MutableStateFlow<ExchangeDevicesState>(ExchangeDevicesState.Closed)
    private val pairingState = MutableStateFlow<MyDevicesMessage?>(null)
    private val accountActionState = MutableStateFlow<ExchangeAccountActionState>(ExchangeAccountActionState.Idle)

    val cards: StateFlow<List<ExchangeServerCard>> = combine(accounts.entries, statuses) { entries, known ->
        entries.map { entry -> ExchangeServerCard(entry, known[entry.identity] ?: ExchangeServerStatus.Checking) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val messages: Flow<MyDevicesMessage> = messageChannel.receiveAsFlow()

    /** Failures of the screen that used to be toasts: each opens the failure dialog with its walk. */
    val failures: Flow<ExchangeDiagnosis> = failureChannel.receiveAsFlow()
    val signIn: StateFlow<ExchangeSignInState> = signInState.asStateFlow()
    val devices: StateFlow<ExchangeDevicesState> = devicesState.asStateFlow()
    val pairingCode: StateFlow<MyDevicesMessage?> = pairingState.asStateFlow()
    val accountAction: StateFlow<ExchangeAccountActionState> = accountActionState.asStateFlow()

    init {
        viewModelScope.launch {
            accounts.entries.collect { entries ->
                entries.filter { it.identity !in statuses.value }.forEach(::refreshStatus)
            }
        }
    }

    fun parseAddress(text: String): ExchangeEndpoint? = accounts.parseAddress(text)

    fun isValidLogin(login: String): Boolean = accounts.isValidLogin(login)

    fun connectionString(entry: DeviceExchangeAccountEntry): String? = accounts.connectionString(entry)

    fun announcedEndpoint(entry: DeviceExchangeAccountEntry): ExchangeEndpoint? = accounts.announcedEndpoint(entry)

    fun intakeText(text: String): ExchangeIntakeOutcome = when (val kind = dispatchIntakeText.dispatch(text)) {
        is IntakeTextDispatch.ExchangeString -> ExchangeIntakeOutcome.Filled(kind.value)
        is IntakeTextDispatch.NewerExchangeMajor -> ExchangeIntakeOutcome.Refused(R.string.exchange_signin_update_app)
        else -> ExchangeIntakeOutcome.Refused(R.string.exchange_signin_refused)
    }

    fun intakeFile(result: ExchangeConnectionStringResult): ExchangeIntakeOutcome = when (result) {
        is ExchangeConnectionStringResult.Read -> ExchangeIntakeOutcome.Filled(result.value)
        is ExchangeConnectionStringResult.NewerMajor -> ExchangeIntakeOutcome.Refused(
            R.string.exchange_signin_update_app
        )
        is ExchangeConnectionStringResult.Refused -> ExchangeIntakeOutcome.Refused(R.string.exchange_signin_refused)
    }

    suspend fun readConnectionFile(
        contentResolver: ContentResolver,
        uri: Uri,
    ): ExchangeConnectionStringResult = withContext(ioDispatcher) {
        try {
            contentResolver.openInputStream(uri)?.use(ExchangeConnectionStringCodec::readFile)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } ?: ExchangeConnectionStringResult.Refused(ExchangeConnectionStringRefusal.NOT_A_STRING)
    }

    fun signIn(request: ExchangeSignInRequest, acceptedPin: String? = null) {
        Timber.d("S4223: sign-in form submitted, registers=${request.registers}")
        signInState.value = ExchangeSignInState.InProgress
        viewModelScope.launch {
            val result = if (request.registers) {
                accounts.register(
                    request.endpoint,
                    request.login,
                    request.secret,
                    request.invitation,
                    request.invitationPassword,
                    acceptedPin,
                )
            } else {
                accounts.signIn(request.endpoint, request.login, request.secret, request.pairingCode, acceptedPin)
            }
            Timber.d("My devices sign-in result: %s registers=%s", result.javaClass.simpleName, request.registers)
            // A card refused as signed-out or revoked keeps its status until it is read again after a sign-in.
            if (result is DeviceExchangeEnrollResult.Enrolled) {
                cards.value.filter { it.status is ExchangeServerStatus.Failed }.forEach { refreshStatus(it.entry) }
            }
            signInState.value = when (result) {
                is DeviceExchangeEnrollResult.Enrolled -> ExchangeSignInState.Done(request.endpoint, request.registers)
                is DeviceExchangeEnrollResult.CertificateUntrusted ->
                    ExchangeSignInState.NeedsTrust(request, result.presented, pinned = null)
                is DeviceExchangeEnrollResult.CertificateChanged ->
                    ExchangeSignInState.NeedsTrust(request, result.presented, result.pinned)
                is DeviceExchangeEnrollResult.Refused -> ExchangeSignInState.Failed(
                    diagnose(
                        ExchangeMessage.ofWire(result.reason, oneTimeCode = request.pairingCode),
                        request.endpoint.host,
                    )
                )
                is DeviceExchangeEnrollResult.Unreachable ->
                    ExchangeSignInState.Failed(diagnose.fromCause(result.cause, request.endpoint.host))
                is DeviceExchangeEnrollResult.NoAnswer ->
                    ExchangeSignInState.Failed(diagnose(ExchangeMessage.UNANSWERED, request.endpoint.host))
            }
        }
    }

    fun resetSignIn() {
        signInState.value = ExchangeSignInState.Idle
    }

    /** The held address, pin and login that refill the sign-in form after `signed-out` (19 item AT). */
    fun signInAgainString(entry: DeviceExchangeAccountEntry): String? = accounts.signInAgainString(entry)

    fun changePassword(entry: DeviceExchangeAccountEntry, oldPassword: String, newPassword: String) {
        Timber.d("S4223: password change requested")
        accountActionState.value = ExchangeAccountActionState.InProgress
        viewModelScope.launch {
            accountActionState.value = when (val call = accounts.changePassword(entry, oldPassword, newPassword)) {
                is ExchangeServerCall.Done -> {
                    refreshStatus(entry)
                    ExchangeAccountActionState.Done(MyDevicesMessage(R.string.exchange_password_done))
                }
                else -> ExchangeAccountActionState.Failed(callFailure(call, entry))
            }
        }
    }

    fun deleteAccount(entry: DeviceExchangeAccountEntry, password: String) {
        Timber.d("S4223: account deletion requested")
        accountActionState.value = ExchangeAccountActionState.InProgress
        viewModelScope.launch {
            accountActionState.value = when (val call = accounts.deleteAccount(entry, password)) {
                is ExchangeServerCall.Done -> {
                    statuses.update { it - entry.identity }
                    ExchangeAccountActionState.Done(MyDevicesMessage(R.string.exchange_delete_done))
                }
                else -> ExchangeAccountActionState.Failed(callFailure(call, entry))
            }
        }
    }

    fun resetAccountAction() {
        accountActionState.value = ExchangeAccountActionState.Idle
    }

    fun openDevices(entry: DeviceExchangeAccountEntry) {
        devicesState.value = ExchangeDevicesState.Loading
        pairingState.value = null
        viewModelScope.launch { loadDevices(entry) }
    }

    fun closeDevices() {
        devicesState.value = ExchangeDevicesState.Closed
        pairingState.value = null
    }

    fun revokeDevice(entry: DeviceExchangeAccountEntry, device: SftpRendezvousDevice) {
        viewModelScope.launch {
            when (val call = accounts.revokeDevice(entry, device.deviceId)) {
                is ExchangeServerCall.Done -> {
                    post(MyDevicesMessage(R.string.exchange_devices_revoked))
                    devicesState.update { state ->
                        if (state is ExchangeDevicesState.Loaded) {
                            state.copy(devices = state.devices.filterNot { it.deviceId == device.deviceId })
                        } else {
                            state
                        }
                    }
                }
                else -> failureChannel.trySend(callFailure(call, entry))
            }
        }
    }

    fun issuePairingCode(entry: DeviceExchangeAccountEntry) {
        viewModelScope.launch {
            pairingState.value = when (val call = accounts.issuePairingCode(entry)) {
                is ExchangeServerCall.Done -> call.value.expiresAt?.let { expires ->
                    MyDevicesMessage(R.string.exchange_devices_pairing_code, listOf(call.value.code, expires))
                } ?: MyDevicesMessage(R.string.exchange_devices_pairing_code_no_expiry, listOf(call.value.code))
                else -> {
                    failureChannel.trySend(callFailure(call, entry))
                    null
                }
            }
        }
    }

    fun changeAddress(entry: DeviceExchangeAccountEntry, endpoint: ExchangeEndpoint) {
        viewModelScope.launch {
            val text = when (val change = accounts.changeAddress(entry, endpoint)) {
                is DeviceExchangeAddressChange.Changed -> {
                    refreshStatus(change.entry)
                    R.string.exchange_devices_edit_done
                }
                DeviceExchangeAddressChange.IdentityTaken -> R.string.exchange_devices_edit_identity_taken
                DeviceExchangeAddressChange.TokenNotMoved -> R.string.exchange_devices_edit_token_not_moved
            }
            post(MyDevicesMessage(text))
        }
    }

    fun makeOutgoing(entry: DeviceExchangeAccountEntry) {
        viewModelScope.launch {
            val left = producer.makeOutgoing(entry)
            post(MyDevicesMessage(R.string.exchange_devices_outgoing_done, listOf(entry.host)))
            if (left == ExchangeProducerLeave.LEFT_LOCALLY) {
                post(MyDevicesMessage(R.string.exchange_devices_left_locally))
            }
        }
    }

    fun setAutoAttach(entry: DeviceExchangeAccountEntry, enabled: Boolean) {
        viewModelScope.launch { accounts.setAutoAttach(entry, enabled) }
    }

    fun remove(entry: DeviceExchangeAccountEntry) {
        viewModelScope.launch {
            val text = when (accounts.remove(entry)) {
                DeviceExchangeRemoval.REVOKED -> R.string.exchange_devices_removed
                DeviceExchangeRemoval.FORGOTTEN_LOCALLY -> R.string.exchange_devices_removed_locally
            }
            statuses.update { it - entry.identity }
            post(MyDevicesMessage(text))
        }
    }

    fun connectWatch(entry: DeviceExchangeAccountEntry) {
        post(MyDevicesMessage(R.string.exchange_devices_watch_progress))
        viewModelScope.launch { post(watchMessage(connectWatchToEntry(entry))) }
    }

    private fun refreshStatus(entry: DeviceExchangeAccountEntry) {
        statuses.update { it + (entry.identity to ExchangeServerStatus.Checking) }
        viewModelScope.launch {
            val status = when (val call = accounts.directory(entry)) {
                is ExchangeServerCall.Done -> ExchangeServerStatus.Online(call.value.devices.size)
                is ExchangeServerCall.Refused -> ExchangeServerStatus.Failed(ExchangeMessage.ofWire(call.reason))
                is ExchangeServerCall.Unreachable -> ExchangeServerStatus.Offline
            }
            statuses.update { it + (entry.identity to status) }
        }
    }

    private suspend fun loadDevices(entry: DeviceExchangeAccountEntry) {
        devicesState.value = when (val call = accounts.directory(entry)) {
            is ExchangeServerCall.Done -> {
                statuses.update { it + (entry.identity to ExchangeServerStatus.Online(call.value.devices.size)) }
                ExchangeDevicesState.Loaded(entry, call.value.devices)
            }
            else -> ExchangeDevicesState.Failed(callFailure(call, entry))
        }
    }

    private fun callFailure(call: ExchangeServerCall<*>, entry: DeviceExchangeAccountEntry): ExchangeDiagnosis =
        when (call) {
            is ExchangeServerCall.Refused -> diagnose(ExchangeMessage.ofWire(call.reason), entry.host)
            is ExchangeServerCall.Unreachable -> diagnose.fromLocal(call.failure, entry.host)
            is ExchangeServerCall.Done -> diagnose(ExchangeMessage.REFUSED, entry.host)
        }

    private fun watchMessage(outcome: WatchExchangeHandoverOutcome): MyDevicesMessage = when (outcome) {
        WatchExchangeHandoverOutcome.Enrolled -> MyDevicesMessage(R.string.exchange_devices_watch_enrolled)
        is WatchExchangeHandoverOutcome.WatchRefused ->
            MyDevicesMessage(R.string.exchange_devices_watch_refused, listOf(reasonText(outcome.reason)))
        WatchExchangeHandoverOutcome.WatchDidNotAnswer -> MyDevicesMessage(R.string.exchange_devices_watch_no_answer)
        is WatchExchangeHandoverOutcome.CodeRefused ->
            MyDevicesMessage(R.string.exchange_devices_watch_code_refused, listOf(reasonText(outcome.reason)))
        WatchExchangeHandoverOutcome.ServerUnreachable ->
            MyDevicesMessage(ExchangeMessageText.what(ExchangeMessage.UNREACHABLE_SERVER))
        WatchExchangeHandoverOutcome.NoConnectedWatch -> MyDevicesMessage(R.string.exchange_devices_watch_none)
        WatchExchangeHandoverOutcome.CompanionOff -> MyDevicesMessage(R.string.exchange_devices_watch_companion_off)
    }

    private fun reasonText(reason: String) =
        MyDevicesMessage(ExchangeMessageText.what(ExchangeMessageText.ofHandoverReason(reason)))

    private fun post(message: MyDevicesMessage) {
        messageChannel.trySend(message)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
