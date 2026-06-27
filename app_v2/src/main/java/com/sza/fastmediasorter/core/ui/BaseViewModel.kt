package com.sza.fastmediasorter.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.debug.DebugToolsBridge
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val UI_STATE_SHARING_TIMEOUT_MS = 5_000L

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
    data class Content<T>(val data: T, val isRefreshing: Boolean = false) : UiState<T>
}

/**
 * Base ViewModel that provides common functionality for all ViewModels.
 * - Provides state and event flows
 * - Handles exceptions
 * - Provides loading states
 */
abstract class BaseViewModel<State, Event> : ViewModel() {

    protected abstract fun getInitialState(): State

    private val _state = MutableStateFlow(getInitialState())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    protected val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Coroutine exception in ${this::class.simpleName}")
        DebugToolsBridge.onCoroutineException(throwable)
        handleError(throwable)
    }

    protected fun updateState(update: (State) -> State) {
        _state.update(update)
    }

    protected fun sendEvent(event: Event) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    protected fun setLoading(isLoading: Boolean) {
        _loading.value = isLoading
    }

    protected fun setError(message: String?) {
        _error.value = message
    }

    protected fun createUiState(isEmpty: (State) -> Boolean): StateFlow<UiState<State>> {
        fun resolveUiState(
            currentState: State,
            isLoading: Boolean,
            errorMessage: String?
        ): UiState<State> {
            val empty = isEmpty(currentState)
            return when {
                empty && isLoading -> UiState.Loading
                empty && errorMessage != null -> UiState.Error(errorMessage)
                empty -> UiState.Empty
                else -> UiState.Content(currentState, isRefreshing = isLoading)
            }
        }

        return combine(state, loading, error) { currentState, isLoading, errorMessage ->
            resolveUiState(currentState, isLoading, errorMessage)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_SHARING_TIMEOUT_MS),
            initialValue = resolveUiState(state.value, loading.value, error.value)
        )
    }

    protected open fun handleError(throwable: Throwable) {
        val errorMessage = throwable.message ?: "Unknown error occurred"
        setError(errorMessage)
        setLoading(false)
    }

    fun clearError() {
        _error.value = null
    }
}
