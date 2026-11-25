package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.repository.ExchangeNotesRepository
import com.sza.fastmediasorter.domain.repository.NotesSyncRemote
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotesSyncModule {

    @Binds
    abstract fun bindNotesSyncRemote(impl: ExchangeNotesRepository): NotesSyncRemote
}
