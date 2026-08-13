package com.sza.fastmediasorter.domain.usecase.radio

import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.domain.radio.RadioControlContract
import timber.log.Timber
import javax.inject.Inject

/**
 * S1441: the one place a tap on a radio target is turned into an attempt to switch it.
 *
 * Every surface that can host such a target - the launcher desktop, the Start menu, the app-launch panel -
 * calls this before its own launch path, so the three cannot drift apart (strategic §5.1). It answers a
 * single question and starts nothing itself: `true` means the radio actually flipped and the tap is spent,
 * `false` means the caller carries on and opens a system surface exactly as it did before this ticket.
 */
class ToggleRadioTargetUseCase @Inject constructor(
    private val radioControl: RadioControlContract,
) {

    suspend operator fun invoke(target: OsShortcutCatalog.Target): Boolean {
        val kind = target.radio
        if (kind == null || !radioControl.isToggleSupported) return false
        val toggled = radioControl.toggle(kind)
        // Which way a tap resolved is the first thing a "the button does nothing" report needs, and the
        // answer differs per firmware, so it is worth a line even in a shipped build.
        Timber.d("Radio target %s: direct toggle %s", target.key, if (toggled) "took" else "refused")
        return toggled
    }
}
