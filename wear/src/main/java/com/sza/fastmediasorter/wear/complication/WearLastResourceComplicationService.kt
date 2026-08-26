package com.sza.fastmediasorter.wear.complication

import com.sza.fastmediasorter.wear.domain.model.WearComplicationKind
import dagger.hilt.android.AndroidEntryPoint

/**
 * S2047: Wear OS complication data source for the last used resource.
 */
@AndroidEntryPoint
class WearLastResourceComplicationService : BaseWearComplicationService() {
    override val kind: WearComplicationKind = WearComplicationKind.LAST_RESOURCE
}
