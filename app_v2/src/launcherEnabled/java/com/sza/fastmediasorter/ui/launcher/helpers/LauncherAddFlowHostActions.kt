package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Intent

/**
 * S1930: the two things [LauncherAddFlowManager] needs its host Activity to do, which it cannot do
 * itself.
 *
 * A holder rather than two more constructor parameters, for the reason `LauncherHomeDependencies`
 * gives about the ViewModel: adding `startWidgetConfiguration` beside the nine already there put the
 * constructor at detekt's `LongParameterList` threshold of 10. Grouping is honest here rather than
 * merely convenient - both are the same kind of thing, "open a screen only the Activity can open",
 * and a third one would join them instead of pushing the count up again.
 *
 * A plain class on purpose, never a `data class`: detekt's `LongParameterList.ignoreDataClasses`
 * defaults to true, so a data holder would be invisible to the very gate this exists to satisfy
 * (S1314).
 *
 * Functions rather than the objects behind them, because the host builds this in a field initialiser
 * where nothing may be dereferenced yet - the same constraint that shapes `LauncherContactPickManager`.
 */
class LauncherAddFlowHostActions(
    /** Opens the "create a resource" flow, which the add flow offers when no resource exists yet. */
    val createResource: () -> Unit,
    /**
     * Starts a configurable widget's own configuration Activity and routes its result back to
     * [LauncherAddFlowManager.onWidgetConfigured]. The launcher behind it is registered by the host
     * before it is STARTED, because an activity-result contract registered later throws.
     */
    val startWidgetConfiguration: (Intent) -> Unit,
)
