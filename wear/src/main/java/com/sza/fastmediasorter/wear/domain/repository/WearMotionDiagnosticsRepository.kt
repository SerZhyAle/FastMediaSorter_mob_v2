package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.motion.WearSensorStreamState
import kotlinx.coroutines.flow.Flow

/**
 * The live motion and activity session behind the Motion Monitor mini-program.
 *
 * The returned flow is cold, and that is the whole lifetime contract: collecting it registers the
 * platform listeners, ending the collection unregisters them, and there is deliberately no separate
 * start or stop call that could fall out of balance with the screen (S2458 §2 goal 3).
 */
interface WearMotionDiagnosticsRepository {

    fun streams(): Flow<List<WearSensorStreamState>>
}
