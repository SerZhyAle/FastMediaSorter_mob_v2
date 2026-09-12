package com.sza.fastmediasorter.wear.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * S2915: marks the process-lifetime application scope. Mirrors app_v2's
 * `com.sza.fastmediasorter.core.di.ApplicationScope` - the wear module is standalone and cannot see
 * the phone's qualifier, so it declares its own of the same name.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Provides the [ApplicationScope]. Kept out of [WearAppModule] - that object sits at detekt's
 * TooManyFunctions ceiling.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearScopeModule {

    // S2915: the process-lifetime scope Data Layer work launches on. The platform destroys a
    // WearableListenerService shortly after the callback returns, so a scope owned by the service
    // cancels jobs still mid-flight - and a cancelled job reports nothing. This one is never
    // cancelled by anything shorter-lived than the process.
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
