package com.sza.fastmediasorter.ui.launcher.helpers

import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactChannel

/**
 * S2102: the screen's durable store for the contact branch's in-flight step, as four operations.
 *
 * Operations rather than the ViewModel itself, for the reason S1195 gives for the two accessors
 * `LauncherContactPickManager` already takes: the host routes domain access through its ViewModel so
 * the helper never depends on that type. Bundled into one holder rather than passed as four more
 * constructor parameters, which would put that constructor at detekt's `LongParameterList` ceiling.
 *
 * The step and the channel list are separate because they are held at different points of the same
 * chain and cleared by different results - the step covers the permission answer and the two
 * number-branch dialogs, the channel list only the messenger choice that follows a system pick.
 */
class LauncherContactStepState(
    val readStep: () -> LauncherContactAction?,
    val writeStep: (LauncherContactAction?) -> Unit,
    val readChannels: () -> List<LauncherContactChannel>?,
    val writeChannels: (List<LauncherContactChannel>?) -> Unit,
)
