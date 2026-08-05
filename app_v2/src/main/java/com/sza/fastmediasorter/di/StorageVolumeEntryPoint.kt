package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.repository.StorageVolumeSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * S1378: exposes [StorageVolumeSource] to `UriPathResolver`, which is a stateless object called
 * synchronously from picker result callbacks and therefore cannot take constructor injection or
 * await the suspend `StorageVolumeRepository`. Same pattern as `MediaCapabilitiesEntryPoint`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface StorageVolumeEntryPoint {
    fun storageVolumeSource(): StorageVolumeSource
}
