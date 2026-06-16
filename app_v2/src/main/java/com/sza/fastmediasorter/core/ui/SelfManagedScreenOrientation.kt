package com.sza.fastmediasorter.core.ui

/**
 * S0439: marker for activities that drive [android.app.Activity.requestedOrientation] themselves
 * (the player family, via ScreenRotationManager). [com.sza.fastmediasorter.core.orientation.AppOrientationManager]
 * skips any activity implementing this so the program-wide orientation policy never fights the
 * player's own rotation control.
 */
interface SelfManagedScreenOrientation
