package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1930: throws away the stored instance behind a configured widget cell when that cell is removed.
 *
 * Its own injectable rather than two more fields on the ViewModel: the work needs a `Context` and the
 * gadget codec, and [LauncherHomeViewModel][com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel]
 * sits at detekt's function and parameter ceilings - the file that holds its dependency bundles says so
 * in as many words. Removal is the only caller today; a future "reset this cell" would be the second.
 *
 * Every guard is inside [ConfigurableWidgetCatalog], so the caller passes a raw `target` column and
 * needs to know nothing about which gadgets configure anything.
 */
@Singleton
class ConfiguredWidgetInstanceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gadgetRegistry: LauncherGadgetRegistry,
) {

    /**
     * Clears whatever [target] names, if it names a configured widget cell at all. A cell of any other
     * kind, an unparseable target, or a param that is not a launcher token are all no-ops, so the
     * removal path can call this for every cell it deletes without asking first.
     */
    fun clearInstanceOf(target: String?) {
        val decoded = gadgetRegistry.decodeTarget(target) ?: return
        val token = ConfigurableWidgetCatalog.tokenOf(decoded.second) ?: return
        Timber.d("S1930: clear instance %s token %d", decoded.first, token)
        ConfigurableWidgetCatalog.clearInstance(context, decoded.first, token)
    }
}
