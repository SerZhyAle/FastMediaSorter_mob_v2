package com.sza.fastmediasorter.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

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
        handleError(throwable)
    }

    protected fun updateState(update: (State) -> State) {
        _state.value = update(_state.value)
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

    protected open fun handleError(throwable: Throwable) {
        val errorMessage = throwable.message ?: "Unknown error occurred"
        setError(errorMessage)
        setLoading(false)
    }

    fun clearError() {
        _error.value = null
    }
}
