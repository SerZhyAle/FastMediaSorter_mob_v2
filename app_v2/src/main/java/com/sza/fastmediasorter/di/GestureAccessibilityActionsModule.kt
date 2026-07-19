package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.screencapture.gesture.GestureAccessibilityActions
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * S1038: declares the [GestureAccessibilityActions] multibinding so consumers can inject the set on
 * every flavor. Only `noLegal` contributes an implementation; elsewhere the set resolves empty, which
 * keeps the SYSTEM gesture group hidden and its dispatch a safe no-op.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GestureAccessibilityActionsModule {

    @Multibinds
    abstract fun accessibilityActions(): Set<GestureAccessibilityActions>
}
