package com.sza.fastmediasorter.di

import android.content.Context
import com.sza.fastmediasorter.core.memory.MemoryPressureDecodeFormatResolver
import com.sza.fastmediasorter.core.memory.NativePressureMonitor
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object NativePressureMonitorModule

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NativePressureEntryPoint {
    fun nativePressureMonitor(): NativePressureMonitor
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MemoryPressureResolverEntryPoint {
    fun memoryPressureDecodeFormatResolver(): MemoryPressureDecodeFormatResolver
}

fun Context.memoryPressureDecodeFormatResolver(): MemoryPressureDecodeFormatResolver {
    return EntryPointAccessors.fromApplication(
        applicationContext,
        MemoryPressureResolverEntryPoint::class.java,
    ).memoryPressureDecodeFormatResolver()
}