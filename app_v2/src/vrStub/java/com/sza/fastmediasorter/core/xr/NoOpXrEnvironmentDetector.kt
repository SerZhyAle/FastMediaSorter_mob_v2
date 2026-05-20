package com.sza.fastmediasorter.core.xr

import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op detector used by phone-only flavors. Always reports [XrEnvironment.NONE].
 *
 * Paired with the real `XrEnvironmentDetectorImpl` in `src/vr/java/`. AGP mounts exactly one
 * of the two source sets per flavor - see `app_v2/build.gradle.kts` `sourceSets` block.
 */
@Singleton
class NoOpXrEnvironmentDetector @Inject constructor() : XrEnvironmentDetector {
    override fun detect(): XrEnvironment = XrEnvironment.NONE
}
