package com.sza.fastmediasorter.wear.ui.apps.clipboard.helpers

import com.sza.fastmediasorter.wear.domain.model.WearOpenInLaunchResult
import com.sza.fastmediasorter.wear.domain.model.WearOpenInObject
import com.sza.fastmediasorter.wear.domain.model.WearOpenInTarget
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSaved
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue
import com.sza.fastmediasorter.wear.domain.usecase.BuildWearServerClipboardOpenInObjectUseCase
import com.sza.fastmediasorter.wear.domain.usecase.LaunchWearOpenInUseCase
import com.sza.fastmediasorter.wear.domain.usecase.ListWearOpenInTargetsUseCase
import com.sza.fastmediasorter.wear.ui.apps.clipboard.ClipboardUiState
import com.sza.fastmediasorter.wear.ui.apps.clipboard.OpenInFailure
import com.sza.fastmediasorter.wear.ui.apps.clipboard.OpenInSource
import com.sza.fastmediasorter.wear.ui.common.OpenInUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S4381: the "Open in.." half of the Clipboard screen - which object is on offer, the dialog's list and the launch.
 *
 * Held outside the view model so the screen's own file stays about the two older actions. Not injected: it lives
 * and dies with the view model's scope, so a list still being built when the wearer leaves the screen is
 * cancelled with it, and the object it keeps is dropped with the instance.
 */
class ClipboardOpenInManager(
    private val scope: CoroutineScope,
    private val listTargets: ListWearOpenInTargetsUseCase,
    private val launchOpenIn: LaunchWearOpenInUseCase,
    private val buildServerObject: BuildWearServerClipboardOpenInObjectUseCase,
    private val update: ((ClipboardUiState) -> ClipboardUiState) -> Unit,
) {

    private var listJob: Job? = null
    private var chosenObject: WearOpenInObject? = null
    private var chosenSource = OpenInSource.CLIPBOARD
    private var received: ReceivedValue? = null

    private class ReceivedValue(val value: WearServerClipboardValue, val saved: WearServerClipboardSaved)

    /** A value arrived from the server on this visit; its chip appears under the result line. */
    fun rememberReceived(value: WearServerClipboardValue, saved: WearServerClipboardSaved) {
        received = ReceivedValue(value, saved)
        update { it.copy(server = it.server.copy(canOpenIn = true)) }
    }

    /** A new fetch starts: the previous value is no longer what the chip under the result line would hand over. */
    fun forgetReceived() {
        received = null
        update { it.copy(server = it.server.copy(canOpenIn = false)) }
    }

    fun openText(text: String) = show(OpenInSource.CLIPBOARD) { WearOpenInObject.Text(text) }

    fun openReceived() {
        val held = received ?: return
        show(OpenInSource.SERVER) { buildServerObject(held.value, held.saved) }
    }

    fun pick(target: WearOpenInTarget) {
        Timber.d("S4381: open in picked, launching")
        val obj = chosenObject ?: return
        val source = chosenSource
        update { it.copy(openIn = null) }
        scope.launch {
            val result = launchOpenIn(target, obj)
            Timber.i("Open in: %s", result)
            chosenObject = null
            val failure = if (result == WearOpenInLaunchResult.Launched) null else OpenInFailure(source, target.label)
            update { it.copy(openInFailure = failure) }
        }
    }

    fun dismiss() {
        listJob?.cancel()
        chosenObject = null
        update { it.copy(openIn = null) }
    }

    private fun show(source: OpenInSource, build: suspend () -> WearOpenInObject?) {
        listJob?.cancel()
        update { it.copy(openIn = OpenInUiState.Loading, openInFailure = null) }
        listJob = scope.launch {
            val obj = build()
            chosenObject = obj
            chosenSource = source
            val targets = obj?.let { listTargets(it) }.orEmpty()
            val shown = if (targets.isEmpty()) OpenInUiState.None else OpenInUiState.Choose(targets)
            update { it.copy(openIn = shown) }
        }
    }
}
