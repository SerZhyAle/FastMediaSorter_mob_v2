package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import kotlinx.coroutines.flow.Flow

/** S0404: icons the user pinned to the launcher taskbar, keyed by their slot in the strip. */
interface LauncherPinsRepository {

    fun observePins(): Flow<List<Pair<Int, LauncherCellCommand>>>

    suspend fun setPin(position: Int, command: LauncherCellCommand)

    suspend fun removePin(position: Int)
}
