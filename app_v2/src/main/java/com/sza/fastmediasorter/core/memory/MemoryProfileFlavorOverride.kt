package com.sza.fastmediasorter.core.memory

import javax.inject.Inject
import javax.inject.Singleton

interface MemoryProfileFlavorOverride {
    fun apply(profile: MemoryProfile): MemoryProfile
}

@Singleton
class DefaultMemoryProfileFlavorOverride @Inject constructor() : MemoryProfileFlavorOverride {
    override fun apply(profile: MemoryProfile): MemoryProfile = profile
}