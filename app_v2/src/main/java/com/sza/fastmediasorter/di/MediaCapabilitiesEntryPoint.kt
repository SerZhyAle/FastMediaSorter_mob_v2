package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point that exposes the flavor-resolved [MediaCapabilities] to code that cannot use
 * constructor or field injection - e.g. AppWidgetProvider (a BroadcastReceiver invoked by the
 * framework). Such code resolves it via
 * `EntryPointAccessors.fromApplication(context.applicationContext, MediaCapabilitiesEntryPoint::class.java).mediaCapabilities()`
 * so it still never reads BuildConfig.SUPPORT_* directly (CLAUDE.md Rule 14).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaCapabilitiesEntryPoint {
    fun mediaCapabilities(): MediaCapabilities
}
