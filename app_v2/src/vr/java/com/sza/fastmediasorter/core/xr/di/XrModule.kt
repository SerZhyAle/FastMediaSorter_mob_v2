package com.sza.fastmediasorter.core.xr.di

import com.sza.fastmediasorter.core.xr.VrMediaSectionContract
import com.sza.fastmediasorter.core.xr.VrMediaSectionContractImpl
import com.sza.fastmediasorter.core.xr.StartVrPlaybackUseCase
import com.sza.fastmediasorter.core.xr.StartVrPlaybackUseCaseImpl
import com.sza.fastmediasorter.core.xr.XrDetectionFacade
import com.sza.fastmediasorter.core.xr.XrDetectionFacadeImpl
import com.sza.fastmediasorter.core.xr.XrEntryGateway
import com.sza.fastmediasorter.core.xr.XrEntryGatewayImpl
import com.sza.fastmediasorter.core.xr.XrEnvironmentDetector
import com.sza.fastmediasorter.core.xr.XrEnvironmentDetectorImpl
import com.sza.fastmediasorter.core.xr.runtime.DiagnosticXrRuntime
import com.sza.fastmediasorter.core.xr.runtime.NativeDiagnosticXrRuntime
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the `vr` source set - mounted into `vr` (auto) and `noLegal` (explicit,
 * see `app_v2/build.gradle.kts` `sourceSets` block). Paired with `NoOpXrModule` in
 * `src/vrStub/java/`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class XrModule {

    @Binds
    @Singleton
    abstract fun bindXrEnvironmentDetector(
        impl: XrEnvironmentDetectorImpl
    ): XrEnvironmentDetector

    @Binds
    @Singleton
    abstract fun bindXrDetectionFacade(
        impl: XrDetectionFacadeImpl
    ): XrDetectionFacade

    @Binds
    @Singleton
    abstract fun bindXrEntryGateway(
        impl: XrEntryGatewayImpl
    ): XrEntryGateway

    @Binds
    @Singleton
    abstract fun bindStartVrPlaybackUseCase(
        impl: StartVrPlaybackUseCaseImpl
    ): StartVrPlaybackUseCase

    /**
     * S0249 Phase 02: native diagnostic runtime. Single binding, VR-flavor only — phone
     * flavors never see this type (the no-op gateway in `vrStub` short-circuits before
     * the runtime is consulted).
     */
    @Binds
    @Singleton
    abstract fun bindDiagnosticXrRuntime(
        impl: NativeDiagnosticXrRuntime
    ): DiagnosticXrRuntime

    /**
     * S0249 Phase 04: VR media-section contract is unconditionally available in vr / noLegal
     * flavors. The fragment itself observes [XrDetectionFacade] and adapts UI state.
     */
    @Binds
    @Singleton
    abstract fun bindVrMediaSectionContract(
        impl: VrMediaSectionContractImpl
    ): VrMediaSectionContract
}
