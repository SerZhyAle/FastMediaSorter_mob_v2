package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.screencapture.AccessibilityServiceControl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S2447: the no-op half of the screen-capture seam. `AccessibilityServiceControl` is injected
 * unconditionally from `src/main` (OperationsSettingsFragment), but the only two bindings live in
 * `src/noLegal` and `src/standardScreenCapture` - so every flavor mounting neither failed
 * `hiltJavaCompile` with a Dagger/MissingBinding. This set is mounted by lite / photos / legacy /
 * vr / foss, and by standard when the edge-overlay flag leaves `src/standardScreenCapture` off.
 *
 * The `@IntoSet` bindings of the other two halves are deliberately absent: their contracts declare
 * `@Multibinds` in `src/main`, which resolves to an empty set with no contribution at all.
 */
@Module
@InstallIn(SingletonComponent::class)
object ScreenCaptureModule {

    @Provides
    @Singleton
    fun provideAccessibilityServiceControl(): AccessibilityServiceControl =
        AccessibilityServiceControl.NoOp
}
