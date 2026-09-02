package com.sza.fastmediasorter.ui.launcher.helpers

import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactChannel

/**
 * S2102: the screen's durable store for the contact branch's in-flight step, as six operations.
 *
 * Operations rather than the ViewModel itself, for the reason S1195 gives for the two accessors
 * `LauncherContactPickManager` already takes: the host routes domain access through its ViewModel so
 * the helper never depends on that type. Bundled into one holder rather than passed as that many more
 * constructor parameters, which would put that constructor at detekt's `LongParameterList` ceiling.
 *
 * The three slots are separate because they are held at different points of the same chain and cleared
 * by different results - the step covers the permission answer and the two number-branch dialogs, the
 * channel list only the messenger choice that follows a system pick, and the messenger package spans the
 * system picker itself.
 */
class LauncherContactStepState(
    val readStep: () -> LauncherContactAction?,
    val writeStep: (LauncherContactAction?) -> Unit,
    val readChannels: () -> List<LauncherContactChannel>?,
    val writeChannels: (List<LauncherContactChannel>?) -> Unit,
    /**
     * S2240: the messaging app the user chose **before** the contact, or null for "any app".
     *
     * Durable for the reason the two slots above are, and more sharply: it is the one value that has to
     * outlive the system contact picker itself. It is written before that picker launches and read when
     * its result comes back, which is exactly the window the OS may kill the process in - and a messenger
     * lost there would let the restored pick read channels unfiltered and pin a row from the wrong app,
     * silently, since every other part of the flow would still look correct.
     */
    val readMessenger: () -> String?,
    val writeMessenger: (String?) -> Unit,
)
