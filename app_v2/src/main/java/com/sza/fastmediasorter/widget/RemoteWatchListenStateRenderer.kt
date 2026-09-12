package com.sza.fastmediasorter.widget

import android.content.Context
import com.sza.fastmediasorter.service.WatchListenStateRenderer
import com.sza.fastmediasorter.service.WearListenState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2881: the Android half of the state push - hands each session state to the widget provider.
 *
 * A state change arriving while no widget is pinned is harmless by construction: the provider's
 * update walks an empty id list, and [WatchListenWidgetProvider.lastState] still records the value
 * so a widget pinned later renders the truth at once.
 */
@Singleton
class RemoteWatchListenStateRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WatchListenStateRenderer {

    override fun render(state: WearListenState) {
        WatchListenWidgetProvider.updateAllWidgets(context, state)
    }
}
