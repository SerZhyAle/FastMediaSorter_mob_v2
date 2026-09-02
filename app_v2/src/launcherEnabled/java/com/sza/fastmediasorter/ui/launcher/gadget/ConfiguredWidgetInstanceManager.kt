package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import com.sza.fastmediasorter.domain.launcher.ConfiguredWidgetInstanceCleaner
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
 * in as many words. The single-cell removal was the only caller until S2217; the mass launcher reset
 * is the second that the old comment predicted.
 *
 * Every guard is inside [ConfigurableWidgetCatalog], so the caller passes a raw `target` column and
 * needs to know nothing about which gadgets configure anything - which is also why the reset's
 * [ConfiguredWidgetInstanceCleaner] seam lands on this class unchanged.
 */
@Singleton
class ConfiguredWidgetInstanceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gadgetRegistry: LauncherGadgetRegistry,
) : ConfiguredWidgetInstanceCleaner {

    /**
     * Clears whatever [target] names, if it names a configured widget cell at all. A cell of any other
     * kind, an unparseable target, or a param that is not a launcher token are all no-ops, so the
     * removal path can call this for every cell it deletes without asking first.
     */
    override fun clearInstanceOf(target: String?) {
        val decoded = gadgetRegistry.decodeTarget(target) ?: return
        val token = ConfigurableWidgetCatalog.tokenOf(decoded.second) ?: return
        Timber.d("S1930: clear instance %s token %d", decoded.first, token)
        ConfigurableWidgetCatalog.clearInstance(context, decoded.first, token)
    }
}
