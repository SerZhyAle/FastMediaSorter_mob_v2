package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneOutcome
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneRequest

/**
 * S2004: the twelfth bridge path - the watch asks the phone to show one of the phone's own files.
 *
 * The only operation in the file-action set that moves no bytes: the phone already holds the file, so
 * the request carries its address and the answer carries what the phone did with it.
 */
interface WearOpenOnPhoneRepository {

    /**
     * Sends [request] to the paired phone and waits for its answer.
     *
     * Returns null when nothing answered - a lost link and a refusal are different things to say to
     * the user, so they are not collapsed into one outcome here (strategic 11 criterion 9).
     */
    suspend fun requestOpen(request: WearOpenOnPhoneRequest): WearOpenOnPhoneOutcome?
}
