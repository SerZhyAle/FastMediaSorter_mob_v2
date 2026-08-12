package com.sza.fastmediasorter.ui.networkmonitor.helpers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the user-requested external address only for the process lifetime. */
@Singleton
class ExternalIpSessionStore @Inject constructor() {

    private val mutableAddress = MutableStateFlow<String?>(null)

    val address: StateFlow<String?> = mutableAddress.asStateFlow()

    fun update(address: String) {
        mutableAddress.value = address
    }

    fun clear() {
        mutableAddress.value = null
    }
}
