package com.sza.fastmediasorter.wear.di

import com.sza.fastmediasorter.wear.data.documents.WearDocumentRepositoryImpl
import com.sza.fastmediasorter.wear.domain.repository.WearDocumentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * S2532: the document-reading binding, in its own module for the reason S2655 gave for the settings
 * one - `WearAppModule` already declares 39 functions against detekt's `TooManyFunctions` ceiling of
 * 40, so the next binding added there fails the build rather than the review. The implementation
 * carries an `@Inject` constructor, so Hilt needs only the interface-to-implementation statement.
 */
@Module
@InstallIn(SingletonComponent::class)
interface WearDocumentModule {

    @Binds
    @Singleton
    fun bindWearDocumentRepository(impl: WearDocumentRepositoryImpl): WearDocumentRepository
}
