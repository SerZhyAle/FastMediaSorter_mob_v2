package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * S3365: destinations seam for the scheduled-operations program screen's dialog host. The host
 * builds the manager by hand, and the activity-logic gate (CLAUDE.md Rule 3) refuses a domain-layer
 * field on the activity itself, so the use case is resolved through this entry point instead.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduledOperationsEntryPoint {

    fun getDestinationsUseCase(): GetDestinationsUseCase
}
