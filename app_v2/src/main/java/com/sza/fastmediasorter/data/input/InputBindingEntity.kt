package com.sza.fastmediasorter.data.input

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "input_bindings", primaryKeys = ["command_id", "device", "slot"])
data class InputBindingEntity(
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "device") val device: String,
    @ColumnInfo(name = "slot") val slot: Int,
    @ColumnInfo(name = "trigger") val trigger: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
