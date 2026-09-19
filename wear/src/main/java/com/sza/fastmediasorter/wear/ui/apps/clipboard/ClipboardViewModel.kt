package com.sza.fastmediasorter.wear.ui.apps.clipboard

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.repository.WearClipboardTextOutcome
import com.sza.fastmediasorter.wear.domain.usecase.SendWearClipboardTextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Reads this watch's clipboard and hands it to the paired phone on the wearer's tap.
 *
 * The read lives here rather than in the screen because it is not a drawing decision, and it happens
 * while this screen is in front of the wearer - since Android 10 only the foreground app may read its
 * own clipboard, which is what ADR-1 built the whole direction of this feature on.
 */
@HiltViewModel
class ClipboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sendWearClipboardText: SendWearClipboardTextUseCase
) : ViewModel() {

    private val state = MutableStateFlow(ClipboardUiState())
    val uiState: StateFlow<ClipboardUiState> = state.asStateFlow()

    private var clipboardText: String = ""

    init {
        refresh()
    }

    /** Re-reads the clipboard, so the preview shows what a send would actually carry. */
    fun refresh() {
        clipboardText = readClipboard()
        state.update { shown ->
            shown.copy(preview = preview(clipboardText), hasText = clipboardText.isNotBlank())
        }
    }

    /**
     * Sends the text the preview shows.
     *
     * Ignored while a send is in flight: a second tap would open a second round trip whose answer
     * would overwrite the first one's for no gain.
     */
    fun sendToPhone() {
        if (state.value.sending) {
            return
        }
        refresh()
        val text = clipboardText
        if (text.isBlank()) {
            state.update { shown -> shown.copy(outcomeRes = R.string.wear_clipboard_empty, outcomeArg = null) }
            return
        }
        state.update { shown -> shown.copy(sending = true, outcomeRes = null, outcomeArg = null) }
        viewModelScope.launch {
            val outcome = sendWearClipboardText(text)
            Timber.i("Watch clipboard: %s", outcome::class.java.simpleName)
            state.update { shown ->
                shown.copy(
                    sending = false,
                    outcomeRes = wording(outcome),
                    outcomeArg = (outcome as? WearClipboardTextOutcome.PhoneRefused)?.reason
                )
            }
        }
    }

    private fun wording(outcome: WearClipboardTextOutcome): Int = when (outcome) {
        is WearClipboardTextOutcome.Delivered -> R.string.wear_clipboard_sent
        is WearClipboardTextOutcome.NothingToSend -> R.string.wear_clipboard_empty
        is WearClipboardTextOutcome.NoConnectedPhone -> R.string.wear_clipboard_no_phone
        is WearClipboardTextOutcome.PhoneDidNotAnswer -> R.string.wear_clipboard_no_answer
        is WearClipboardTextOutcome.PhoneRefused -> R.string.wear_clipboard_refused
    }

    private fun readClipboard(): String {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return ""
        return try {
            clipboard.primaryClip
                ?.takeIf { clip -> clip.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                .orEmpty()
        } catch (e: SecurityException) {
            // Some OEM watch builds restrict clipboard access; the screen then offers nothing to send
            // rather than crashing on a read the wearer cannot influence.
            Timber.w(e, "Watch clipboard: the read was refused")
            ""
        }
    }

    private fun preview(text: String): String =
        if (text.length <= PREVIEW_LENGTH) text else text.take(PREVIEW_LENGTH) + PREVIEW_ELLIPSIS

    private companion object {
        const val PREVIEW_LENGTH = 80
        const val PREVIEW_ELLIPSIS = ".."
    }
}
