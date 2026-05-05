package com.sza.fastmediasorter.domain.model

sealed class DeviceStorageState {
    data class Success(val availableGb: Double) : DeviceStorageState()
    data class Error(val message: String) : DeviceStorageState()
}
