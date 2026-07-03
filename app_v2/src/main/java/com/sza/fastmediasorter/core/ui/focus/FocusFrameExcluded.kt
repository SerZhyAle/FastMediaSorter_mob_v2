package com.sza.fastmediasorter.core.ui.focus

/**
 * S0819: marker for Activities that must NOT receive the app-wide travelling focus frame (e.g. VR
 * surfaces that render their own focus affordance). Implemented by opt-out screens; not applied to
 * any Activity yet - VR exclusion is decided in a later phase.
 */
interface FocusFrameExcluded
