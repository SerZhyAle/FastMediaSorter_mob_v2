package com.sza.fastmediasorter.ui.launcher.tray

import android.content.Context
import com.sza.fastmediasorter.R

/**
 * S3475: the one wording of a SIM slot's state, shared by the tray and the dim screen's status row so
 * the two surfaces cannot describe the same signal differently.
 */
object LauncherTraySimDescription {

    /** Level 0 is what the platform reports for "no service", and the tray names it as such. */
    const val NO_SERVICE_LEVEL = 0

    fun describe(context: Context, slotNumber: Int, state: LauncherTraySimState, dataBadge: String?): String {
        val level = state.signalLevel
        return when {
            level == NO_SERVICE_LEVEL -> context.getString(R.string.launcher_tray_sim_signal_none, slotNumber)
            state.roaming && dataBadge != null -> context.getString(
                R.string.launcher_tray_sim_roaming_data_type,
                slotNumber,
                level,
                dataBadge,
            )
            state.roaming -> context.getString(R.string.launcher_tray_sim_roaming, slotNumber, level)
            dataBadge != null -> context.getString(R.string.launcher_tray_sim_data_type, slotNumber, level, dataBadge)
            else -> context.getString(R.string.launcher_tray_sim_signal, slotNumber, level)
        }
    }
}
