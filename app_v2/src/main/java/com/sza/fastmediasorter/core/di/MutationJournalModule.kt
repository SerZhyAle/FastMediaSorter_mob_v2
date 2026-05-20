package com.sza.fastmediasorter.core.di

import com.sza.fastmediasorter.data.mutation.InMemoryMutationJournal
import com.sza.fastmediasorter.domain.mutation.MutationJournal
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MutationJournalModule {

    @Binds
    @Singleton
    abstract fun bindMutationJournal(
        impl: InMemoryMutationJournal
    ): MutationJournal
}
