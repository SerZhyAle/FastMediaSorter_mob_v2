package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.ticket.TicketInboxRepositoryImpl
import com.sza.fastmediasorter.domain.ticket.TicketInboxRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** S4554: binds the phone half of the SZA ticket inbox to its Google Drive implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TicketInboxModule {

    @Binds
    @Singleton
    abstract fun bindTicketInboxRepository(impl: TicketInboxRepositoryImpl): TicketInboxRepository
}
