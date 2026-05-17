package com.sza.fastmediasorter.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.data.browser.CctAvailabilityChecker
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import com.sza.fastmediasorter.domain.identity.IdentityFailureReason
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S0200 Phase 06 — ViewModel for the "Google Account" Settings card.
 *
 * Observes the identity-domain state Flow, drives sign-in / sign-out via the identity domain,
 * exposes CCT availability for the diagnostics line. On successful sign-in, clears the
 * `needs_sign_in` flag for every Drive resource matching the bound account email. On sign-out,
 * marks every Drive resource as `needs_sign_in = true`.
 */
@HiltViewModel
class GoogleAccountSettingsViewModel @Inject constructor(
    private val identityRepository: GoogleIdentityRepository,
    private val cctChecker: CctAvailabilityChecker,
    private val resourceDao: ResourceDao
) : ViewModel() {

    data class UiState(
        val state: PrimaryGoogleAccountState,
        val cctPackage: String?,
        val showDiagnostics: Boolean = false
    )

    sealed interface Event {
        data class ShowSignInError(val reason: IdentityFailureReason) : Event
    }

    private val showDiagnostics = MutableStateFlow(false)

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        // S0234: emit a one-shot ShowSignInError event on each transition INTO Error state.
        // The "prev != Error && current == Error" predicate fires exactly once per error
        // episode and stays silent on repeated Error -> Error ticks.
        //
        // S0234 fix: seed `previous` with the current singleton state value BEFORE collect
        // begins. Otherwise `previous=null` would treat an already-Error singleton state (left
        // over from a sign-in attempt in a prior VM lifecycle within the same process) as a
        // fresh transition, replaying the error dialog every time the user re-opens Settings.
        viewModelScope.launch {
            var previous: PrimaryGoogleAccountState = identityRepository.state.value
            identityRepository.state.collect { current ->
                if (previous !is PrimaryGoogleAccountState.Error && current is PrimaryGoogleAccountState.Error) {
                    _events.send(Event.ShowSignInError(current.cause))
                }
                previous = current
            }
        }
    }

    val uiState: StateFlow<UiState> = combine(
        identityRepository.state,
        showDiagnostics
    ) { state, diag ->
        UiState(
            state = state,
            cctPackage = cctChecker.resolveCctPackage(),
            showDiagnostics = diag
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UiState(PrimaryGoogleAccountState.Unbound, cctChecker.resolveCctPackage())
    )

    fun signInPrimary(activity: Activity) {
        viewModelScope.launch {
            val result = identityRepository.signInPrimary(activity, primaryScopes())
            // On success, clear needs_sign_in for resources bound to this email.
            if (result is com.sza.fastmediasorter.domain.identity.IdentitySignInResult.Success) {
                resourceDao.clearNeedsSignInForCredentials(result.account.email)
            }
        }
    }

    fun signOutPrimary() {
        viewModelScope.launch {
            // S0200 §11.5 fix: capture the bound primary email BEFORE signing out, then mark only
            // its Drive resources as needs-sign-in. Secondary multi-account Drive resources (under
            // different Google accounts) keep access — strategic ADR-3 / §5.2.
            val primaryEmail = (identityRepository.state.value as? PrimaryGoogleAccountState.Bound)
                ?.account
                ?.email
                ?: (identityRepository.state.value as? PrimaryGoogleAccountState.NeedsResignIn)
                    ?.account
                    ?.email
            identityRepository.signOutPrimary()
            if (primaryEmail != null) {
                resourceDao.markDriveNeedsSignInForCredentials(primaryEmail, true)
            }
        }
    }

    fun reconnect(activity: Activity) = signInPrimary(activity)

    fun toggleDiagnostics() {
        showDiagnostics.value = !showDiagnostics.value
    }

    private fun primaryScopes(): Set<GoogleScope> = setOf(
        GoogleScope.DRIVE,
        GoogleScope.DRIVE_READONLY,
        GoogleScope.EMAIL,
        GoogleScope.PROFILE,
        GoogleScope.OPENID
    )
}
