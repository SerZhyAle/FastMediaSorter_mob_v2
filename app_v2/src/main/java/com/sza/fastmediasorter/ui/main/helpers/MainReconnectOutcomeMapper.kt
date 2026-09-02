package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.ReconnectResourceUseCase
import com.sza.fastmediasorter.ui.main.MainEvent

/**
 * S2374: turns a reconnect result into the message the user sees.
 *
 * The counts are named only when part of the favorites stayed behind. A complete move needs no
 * number, and before this a partial one read exactly like a complete one - "moved 0 of 40" and
 * "40 of 40" produced the same sentence.
 *
 * The denominator is the rows this reconnect was responsible for, not every favorite the resource
 * holds: an untouched row never sat under the old address, so counting it as not-moved would report
 * a loss that did not happen.
 *
 * Pure by design - the caller logs the failure and emits the event, so the count logic is decidable
 * without a device.
 */
object MainReconnectOutcomeMapper {

    fun toEvent(result: Result<ReconnectResourceUseCase.Outcome>): MainEvent {
        val outcome = result.getOrNull()
        return when {
            outcome == null -> MainEvent.ShowResourceMessage(R.string.reconnect_failed)
            outcome.keptFavorites == 0 -> MainEvent.ShowResourceMessage(R.string.reconnect_success)
            else -> MainEvent.ShowResourceMessage(
                R.string.reconnect_success_partial_favorites,
                arrayOf(outcome.remappedFavorites, outcome.remappedFavorites + outcome.keptFavorites),
            )
        }
    }
}
