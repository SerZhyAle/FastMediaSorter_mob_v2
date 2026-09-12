package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearOpenUrlOnPhoneOutcome

/**
 * S2496: hands an arbitrary address to the paired phone so it opens there.
 *
 * Distinct from the S2004 file bridge on purpose: that one addresses a file by a token the phone app
 * issued, so it can only reach a phone running FastMediaSorter. A link has no such owner and must
 * open on any paired phone.
 */
interface WearOpenUrlOnPhoneRepository {

    suspend fun openOnPhone(url: String): WearOpenUrlOnPhoneOutcome
}
