package com.sza.fastmediasorter.vr.memory

import com.sza.fastmediasorter.core.memory.MemoryProfile
import com.sza.fastmediasorter.core.memory.MemoryProfileFlavorOverride
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VrMemoryProfileFlavorOverride @Inject constructor() : MemoryProfileFlavorOverride {
    override fun apply(profile: MemoryProfile): MemoryProfile {
        // VR photo/video surfaces visibly band with RGB_565, so force full precision.
        return profile.copy(useRgb565 = false)
    }
}