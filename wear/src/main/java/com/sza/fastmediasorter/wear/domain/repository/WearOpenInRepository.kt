package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearOpenInCandidates
import com.sza.fastmediasorter.wear.domain.model.WearOpenInLaunchResult
import com.sza.fastmediasorter.wear.domain.model.WearOpenInObject
import com.sza.fastmediasorter.wear.domain.model.WearOpenInTarget

/**
 * S4381: the applications installed on this watch, seen as receivers of one object, and the launch of one of them.
 */
interface WearOpenInRepository {

    /** Every application that can take [obj], with the packages the system starts by default. */
    suspend fun candidatesFor(obj: WearOpenInObject): WearOpenInCandidates

    /** Starts exactly the activity [target] names with [obj]; never throws for a missing application. */
    suspend fun launch(target: WearOpenInTarget, obj: WearOpenInObject): WearOpenInLaunchResult
}
