package com.sza.fastmediasorter.core.ui.focus

/**
 * S0943: marker for Activities that must NOT receive the app-wide in-place focus decoration (e.g. VR
 * surfaces that render their own focus affordance). Implemented by opt-out screens; not applied to any
 * Activity yet - VR exclusion is decided in a later phase.
 */
interface FocusDecorationExcluded
