package com.sza.fastmediasorter.domain.model

data class ScheduledOperation(
    val id: Long = 0,
    val isEnabled: Boolean = true,
    val sourceResourceId: Long,
    val operationType: ScheduledOpType,
    val targetResourceId: Long?,          // null when operationType == DELETE
    val fileTypeMask: Int = FileTypeFlags.DEFAULT, // bitmask of FileTypeFlags constants
    val timeFilter: TimeFilter,
    val startTimeHour: Int,               // 0..23
    val startTimeMinute: Int,             // 0..59
    val intervalHours: Int,               // 0..1440
    val intervalMinutes: Int,             // 0..59; if hours==0 then min >= 15
    val overwrite: Boolean = false,          // overwrite existing files on COPY/MOVE
    val silentMode: Boolean = false,
    val lastRunAt: Long? = null,          // Unix timestamp ms
    val nextRunAt: Long? = null,          // Unix timestamp ms
    val lastRunStatus: String? = null,    // null | "OK" | "ERROR: <text>"
    val workerId: String? = null          // WorkManager unique work name = "sched_op_$id"
)
