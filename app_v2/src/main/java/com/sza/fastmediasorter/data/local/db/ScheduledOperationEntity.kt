package com.sza.fastmediasorter.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sza.fastmediasorter.domain.model.FileTypeFlags

@Entity(
    tableName = "scheduled_operations",
    foreignKeys = [
        ForeignKey(
            entity = ResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_resource_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_resource_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["source_resource_id"], name = "idx_sched_ops_source"),
        Index(value = ["target_resource_id"], name = "idx_sched_ops_target"),
        Index(value = ["is_enabled"], name = "idx_sched_ops_enabled")
    ]
)
data class ScheduledOperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    @ColumnInfo(name = "source_resource_id")
    val sourceResourceId: Long,

    @ColumnInfo(name = "operation_type")
    val operationType: String,         // ScheduledOpType.name()

    @ColumnInfo(name = "target_resource_id")
    val targetResourceId: Long?,       // null when operationType == DELETE

    @ColumnInfo(name = "file_type_mask")
    val fileTypeMask: Int = FileTypeFlags.DEFAULT, // bitmask of FileTypeFlags constants

    @ColumnInfo(name = "time_filter")
    val timeFilter: String,            // TimeFilter.name()

    @ColumnInfo(name = "start_time_hour")
    val startTimeHour: Int,

    @ColumnInfo(name = "start_time_minute")
    val startTimeMinute: Int,

    @ColumnInfo(name = "interval_hours")
    val intervalHours: Int,

    @ColumnInfo(name = "interval_minutes")
    val intervalMinutes: Int,

    @ColumnInfo(name = "overwrite")
    val overwrite: Boolean = false,

    @ColumnInfo(name = "silent_mode")
    val silentMode: Boolean = false,

    @ColumnInfo(name = "last_run_at")
    val lastRunAt: Long? = null,

    @ColumnInfo(name = "next_run_at")
    val nextRunAt: Long? = null,

    @ColumnInfo(name = "last_run_status")
    val lastRunStatus: String? = null, // null | "OK" | "ERROR: <text>"

    @ColumnInfo(name = "worker_id")
    val workerId: String? = null
)
