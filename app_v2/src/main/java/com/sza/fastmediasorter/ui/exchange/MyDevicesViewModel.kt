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
import com.sza.fastmediasorter.domain.model.ExchangeEndpoint
import com.sza.fastmediasorter.domain.model.ExchangeServerCall
import com.sza.fastmediasorter.domain.model.IntakeTextDispatch
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.WatchExchangeHandoverOutcome
import com.sza.fastmediasorter.domain.usecase.exchange.ManageExchangeAccountsUseCase
import com.sza.fastmediasorter.domain.usecase.intake.DispatchIntakeTextUseCase
import com.sza.fastmediasorter.domain.usecase.sftpserver.ExchangeProducerLeave
import com.sza.fastmediasorter.domain.usecase.sftpserver.ManageExchangeProducerUseCase
import com.sza.fastmediasorter.domain.usecase.wear.ConnectWatchToExchangeEntryUseCase
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeRefusalText
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
    data class Refused(@StringRes val text: Int) : ExchangeServerStatus
    data object Unreachable : ExchangeServerStatus
}

data class ExchangeServerCard(val entry: DeviceExchangeAccountEntry, val status: ExchangeServerStatus)

/** A text the screen shows once; [args] fill its placeholders, a nested message resolved first. */
data class MyDevicesMessage(@StringRes val text: Int, val args: List<Any> = emptyList())

/** What the user typed into the sign-in dialog; the secret leaves memory with the dialog. */
class ExchangeSignInRequest(
    val endpoint: ExchangeEndpoint,
    val login: String,
    val secret: String,
    val pairingCode: Boolean,
) {
    override fun toString(): String = "ExchangeSignInRequest(pairingCode=$pairingCode)"
}

sealed interface ExchangeSignInState {
    data object Idle : ExchangeSignInState
    data object InProgress : ExchangeSignInState
    data class Failed(val message: MyDevicesMessage) : ExchangeSignInState

    /** The user must see [presented] and accept it before the entry pins it (amendment D). */
    data class NeedsTrust(
        val request: ExchangeSignInRequest,
        val presented: String,
        val pinned: String?,
    ) : ExchangeSignInState

    data class Done(val endpoint: ExchangeEndpoint) : ExchangeSignInState
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
    data class Failed(val message: MyDevicesMessage) : ExchangeDevicesState
}

@HiltViewModel
class MyDevicesViewModel @Inject constructor(
    private val accounts: ManageExchangeAccountsUseCase,
    private val producer: ManageExchangeProducerUseCase,
    private val connectWatchToEntry: ConnectWatchToExchangeEntryUseCase,
    private val dispatchIntakeText: DispatchIntakeTextUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val statuses = MutableStateFlow<Map<String, ExchangeServerStatus>>(emptyMap())
    private val messageChannel = Channel<MyDevicesMessage>(Channel.BUFFERED)
    private val signInState = MutableStateFlow<ExchangeSignInState>(ExchangeSignInState.Idle)
    private val devicesState = MutableStateFlow<ExchangeDevicesState>(ExchangeDevicesState.Closed)
    private val pairingState = MutableStateFlow<MyDevicesMessage?>(null)

    val cards: StateFlow<List<ExchangeServerCard>> = combine(accounts.entries, statuses) { entries, known ->
        entries.map { entry -> ExchangeServerCard(entry, known[entry.identity] ?: ExchangeServerStatus.Checking) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val messages: Flow<MyDevicesMessage> = messageChannel.receiveAsFlow()
    val signIn: StateFlow<ExchangeSignInState> = signInState.asStateFlow()
    val devices: StateFlow<ExchangeDevicesState> = devicesState.asStateFlow()
    val pairingCode: StateFlow<MyDevicesMessage?> = pairingState.asStateFlow()

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
        Timber.d("S4116: My devices sign-in requested")
        signInState.value = ExchangeSignInState.InProgress
        viewModelScope.launch {
            val result = accounts.signIn(
                request.endpoint,
                request.login,
                request.secret,
                request.pairingCode,
                acceptedPin
            )
            signInState.value = when (result) {
                is DeviceExchangeEnrollResult.Enrolled -> ExchangeSignInState.Done(request.endpoint)
                is DeviceExchangeEnrollResult.CertificateUntrusted ->
                    ExchangeSignInState.NeedsTrust(request, result.presented, pinned = null)
                is DeviceExchangeEnrollResult.CertificateChanged ->
                    ExchangeSignInState.NeedsTrust(request, result.presented, result.pinned)
                is DeviceExchangeEnrollResult.Refused ->
                    ExchangeSignInState.Failed(MyDevicesMessage(ExchangeRefusalText.of(result.reason)))
                is DeviceExchangeEnrollResult.Unreachable ->
                    ExchangeSignInState.Failed(MyDevicesMessage(R.string.exchange_devices_status_unreachable))
            }
        }
    }

    fun resetSignIn() {
        signInState.value = ExchangeSignInState.Idle
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
                else -> post(callFailure(call))
            }
        }
    }

    fun issuePairingCode(entry: DeviceExchangeAccountEntry) {
        viewModelScope.launch {
            pairingState.value = when (val call = accounts.issuePairingCode(entry)) {
                is ExchangeServerCall.Done -> call.value.expiresAt?.let { expires ->
                    MyDevicesMessage(R.string.exchange_devices_pairing_code, listOf(call.value.code, expires))
                } ?: MyDevicesMessage(R.string.exchange_devices_pairing_code_no_expiry, listOf(call.value.code))
                else -> callFailure(call)
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
        Timber.d("S4158: Connect watch chosen on a server card")
        post(MyDevicesMessage(R.string.exchange_devices_watch_progress))
        viewModelScope.launch { post(watchMessage(connectWatchToEntry(entry))) }
    }

    private fun refreshStatus(entry: DeviceExchangeAccountEntry) {
        statuses.update { it + (entry.identity to ExchangeServerStatus.Checking) }
        viewModelScope.launch {
            val status = when (val call = accounts.directory(entry)) {
                is ExchangeServerCall.Done -> ExchangeServerStatus.Online(call.value.devices.size)
                is ExchangeServerCall.Refused -> ExchangeServerStatus.Refused(ExchangeRefusalText.of(call.reason))
                ExchangeServerCall.Unreachable -> ExchangeServerStatus.Unreachable
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
            else -> ExchangeDevicesState.Failed(callFailure(call))
        }
    }

    private fun callFailure(call: ExchangeServerCall<*>): MyDevicesMessage = when (call) {
        is ExchangeServerCall.Refused -> MyDevicesMessage(ExchangeRefusalText.of(call.reason))
        else -> MyDevicesMessage(R.string.exchange_devices_status_unreachable)
    }

    private fun watchMessage(outcome: WatchExchangeHandoverOutcome): MyDevicesMessage = when (outcome) {
        WatchExchangeHandoverOutcome.Enrolled -> MyDevicesMessage(R.string.exchange_devices_watch_enrolled)
        is WatchExchangeHandoverOutcome.WatchRefused ->
            MyDevicesMessage(R.string.exchange_devices_watch_refused, listOf(reasonText(outcome.reason)))
        WatchExchangeHandoverOutcome.WatchDidNotAnswer -> MyDevicesMessage(R.string.exchange_devices_watch_no_answer)
        is WatchExchangeHandoverOutcome.CodeRefused ->
            MyDevicesMessage(R.string.exchange_devices_watch_code_refused, listOf(reasonText(outcome.reason)))
        WatchExchangeHandoverOutcome.ServerUnreachable -> MyDevicesMessage(R.string.exchange_devices_status_unreachable)
        WatchExchangeHandoverOutcome.NoConnectedWatch -> MyDevicesMessage(R.string.exchange_devices_watch_none)
        WatchExchangeHandoverOutcome.CompanionOff -> MyDevicesMessage(R.string.exchange_devices_watch_companion_off)
    }

    private fun reasonText(reason: String) = MyDevicesMessage(ExchangeRefusalText.of(reason))

    private fun post(message: MyDevicesMessage) {
        messageChannel.trySend(message)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
