package com.sza.fastmediasorter.wear.ui.common

import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.wear.input.RemoteInputIntentHelper
import timber.log.Timber

/**
 * S2136: the key the watch's input activity returns the typed or spoken text under.
 *
 * Public because reading the answer back is the caller's half of this exchange - it passes this key
 * to `RemoteInput.getResultsFromIntent`. A launcher that kept the key to itself would hand back a
 * bundle nobody could open.
 */
const val WEAR_SEARCH_INPUT_KEY: String = "search_query"

/**
 * S2136: asks the watch for a search string, by keyboard or by voice.
 *
 * The streams screen has carried this exchange since S2009 and it never took a stream-specific
 * parameter, so it is lifted here unchanged rather than written a second time. Every content list on
 * the watch now reaches the same input path, including the speech one strategic 3.1 wish 1 asks for.
 */
fun launchWearSearchInput(
    searchHint: String,
    onUnavailable: () -> Unit,
    launch: (Intent) -> Unit
) {
    val remoteInput = RemoteInput.Builder(WEAR_SEARCH_INPUT_KEY)
        .setLabel(searchHint)
        .build()
    val remoteInputIntent = RemoteInputIntentHelper.createActionRemoteInputIntent()
    RemoteInputIntentHelper.putRemoteInputsExtra(remoteInputIntent, listOf(remoteInput))
    try {
        launch(remoteInputIntent)
    } catch (_: ActivityNotFoundException) {
        // A watch with no input activity at all is the one case with no text to return, and the
        // dialog has already closed by now - so the refusal has to be handed back, or it reads as a
        // search that matched everything (S1946).
        Timber.w("Wear remote input is unavailable")
        onUnavailable()
    }
}
