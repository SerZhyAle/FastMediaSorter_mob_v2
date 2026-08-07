package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Context
import com.sza.fastmediasorter.ui.addresource.AddResourceActivity
import javax.inject.Inject

/**
 * S1423: the single launch path shared by every home-screen entry point - the launcher menu row, the
 * content picker's "Create new.." item, and the empty-desktop long-press menu S1466 brings. A new
 * entry point adds a call here instead of a third handler, because three copies of the launch diverge
 * at the first edit (strategic §5.1.1).
 *
 * It holds no state and never inspects the outcome: the pin is requested inside the creation flow,
 * where success is already known, not by whoever started it.
 */
class LauncherResourceCreateManager @Inject constructor() {

    /** Opens the shared Add Resource screen, asking it to pin a shortcut for whatever gets created. */
    fun startCreateResource(context: Context) {
        context.startActivity(AddResourceActivity.createIntent(context, pinShortcutOnCreate = true))
    }
}
