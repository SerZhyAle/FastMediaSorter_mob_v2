package com.sza.fastmediasorter.wear.domain.usecase

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.sza.fastmediasorter.wear.complication.WearFavouritesComplicationService
import com.sza.fastmediasorter.wear.complication.WearLastResourceComplicationService
import com.sza.fastmediasorter.wear.domain.model.WearComplicationKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * S2047: requests the Wear OS system to update a complication when its underlying data changes.
 *
 * NOW_PLAYING is intentionally rejected because it uses platform polling (300s update period)
 * to avoid exceeding the documented complication update budget during frequent track changes.
 */
class RequestWearComplicationRefreshUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    operator fun invoke(kind: WearComplicationKind) {
        if (kind == WearComplicationKind.NOW_PLAYING) {
            // Strategic §4.4: NOW_PLAYING uses platform polling; manual update pushes are refused.
            return
        }

        val serviceClass = when (kind) {
            WearComplicationKind.LAST_RESOURCE -> WearLastResourceComplicationService::class.java
            WearComplicationKind.FAVOURITES_COUNT -> WearFavouritesComplicationService::class.java
            WearComplicationKind.NOW_PLAYING -> return
        }

        ComplicationDataSourceUpdateRequester
            .create(context, ComponentName(context, serviceClass))
            .requestUpdate()
    }
}
