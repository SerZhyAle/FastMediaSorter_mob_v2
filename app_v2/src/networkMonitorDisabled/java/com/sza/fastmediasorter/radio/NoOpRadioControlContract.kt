package com.sza.fastmediasorter.radio

import com.sza.fastmediasorter.domain.radio.RadioControlContract
import com.sza.fastmediasorter.domain.radio.RadioKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * S1441: flavors mounting `src/networkMonitorDisabled/java` declare none of the radio permissions, so a
 * toggle attempt could only end in a refusal. Reporting it unsupported up front keeps those builds on
 * exactly today's behaviour - the tap opens the system screen - instead of paying for a doomed attempt.
 */
class NoOpRadioControlContract : RadioControlContract {

    override val isToggleSupported: Boolean = false

    override fun state(kind: RadioKind): Flow<Boolean?> = flowOf(null)

    override suspend fun toggle(kind: RadioKind): Boolean = false
}
