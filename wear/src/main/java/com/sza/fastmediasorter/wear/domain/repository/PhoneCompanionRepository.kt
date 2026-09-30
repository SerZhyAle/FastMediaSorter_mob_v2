package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.PhoneCompanionState
import kotlinx.coroutines.flow.StateFlow

/**
 * S4011: the live answer to "is FastMediaSorter on a reachable paired phone".
 *
 * [refresh] only starts a lookup and returns at once; the answer arrives through [state]. Callers ask
 * on every launch and whenever the platform reports a capability change, and while [state] has a
 * subscriber the repository also listens for that change on its own.
 */
interface PhoneCompanionRepository {

    val state: StateFlow<PhoneCompanionState>

    fun refresh()
}
