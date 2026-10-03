package com.sza.fastmediasorter.ui.dialog

import android.content.Context
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.RepinSftpHostKeyUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S4037: the one flow every runtime error surface (browse, playback, transfer) hands a host-key
 * mismatch to. It resolves the resource the pinned fingerprint belongs to, names the fan-out count,
 * shows [HostKeyRepinDialog] and writes only after the explicit confirmation. Surfaces never build
 * their own confirm UI or touch the pin (strategic §5.1 pillars 2-4).
 *
 * Nothing renders when the pin has no owner left or no resource would be updated: the caller's
 * `onDeclined` runs instead, so the static safe message is never lost.
 */
class HostKeyRepinPrompter @Inject constructor(
    private val repinUseCase: RepinSftpHostKeyUseCase
) {

    /**
     * Recognises the typed pair in [throwable]'s cause chain and starts the prompt flow.
     *
     * @return false when the chain carries no typed pair; nothing was started and the caller keeps its
     * static rendering. True means [onDeclined] will run if the user cancels or nothing can be re-pinned.
     */
    fun offer(context: Context, scope: CoroutineScope, throwable: Throwable?, onDeclined: () -> Unit): Boolean {
        val pair = repinUseCase.mismatchIn(throwable) ?: return false
        offer(context, scope, pair.first, pair.second, onDeclined)
        return true
    }

    /** Entry for a surface that already carries the typed pair as data (a persisted terminal event). */
    fun offer(
        context: Context,
        scope: CoroutineScope,
        expected: String,
        actual: String,
        onDeclined: () -> Unit
    ) {
        scope.launch {
            val anchor = repinUseCase.anchorFor(expected)
            val affected = anchor?.let { repinUseCase.affectedCount(it) } ?: 0
            val shown = anchor != null && affected > 0 &&
                HostKeyRepinDialog.show(
                    context = context,
                    expected = expected,
                    actual = actual,
                    affectedCount = affected,
                    onConfirmed = { scope.launch { confirm(context, anchor, actual) } },
                    onCancelled = onDeclined
                )
            if (!shown) onDeclined()
        }
    }

    private suspend fun confirm(context: Context, anchor: Long, actual: String) {
        val updated = repinUseCase(anchor, actual)
        Toast.makeText(
            context,
            if (updated > 0) R.string.host_key_repin_done else R.string.host_key_repin_failed,
            Toast.LENGTH_LONG
        ).show()
    }
}
