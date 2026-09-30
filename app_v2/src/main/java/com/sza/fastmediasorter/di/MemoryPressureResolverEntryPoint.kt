package com.sza.fastmediasorter.di

import android.content.Context
import com.sza.fastmediasorter.core.memory.MemoryPressureDecodeFormatResolver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

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
