package com.sza.fastmediasorter.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from schema version 35 to 36.
 * S0623: creates app_launch_panel_tiles for the gesture-invoked quick-launch panel.
 * Column names/types match AppLaunchPanelTileEntity so Room's schema hash validates.
 */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS app_launch_panel_tiles (" +
                "slotIndex INTEGER PRIMARY KEY NOT NULL, " +
                "type TEXT NOT NULL, " +
                "targetId TEXT, " +
                "labelOverride TEXT, " +
                "addedAt INTEGER NOT NULL)"
        )
    }
}
