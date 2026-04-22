package com.sza.fastmediasorter.ui.addresource

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope

/**
 * Narrow surface that AddResourceViewModel exposes to its coordinators.
 * Keeps BaseViewModel's protected members scoped to the VM while letting
 * coordinators mutate state, emit events, and share VM-level helpers.
 */
internal interface AddResourceBridge {
    val vmScope: CoroutineScope
    val appScope: CoroutineScope
    val ioDispatcher: CoroutineDispatcher
    val exHandler: CoroutineExceptionHandler
    val stateValue: AddResourceState
    fun mutate(transform: (AddResourceState) -> AddResourceState)
    fun emit(event: AddResourceEvent)
    fun markLoading(loading: Boolean)
    fun reportError(e: Throwable)
    suspend fun supportedMediaTypes(): Set<MediaType>
    suspend fun runSpeedTest(resource: MediaResource)
}
