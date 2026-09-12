package com.sza.fastmediasorter.wear.di

import android.content.Context
import androidx.room.Room
import com.sza.fastmediasorter.wear.data.db.MediaMetadataVoiceNoteDurationReader
import com.sza.fastmediasorter.wear.data.db.VoiceNoteDao
import com.sza.fastmediasorter.wear.data.db.VoiceNoteDurationReader
import com.sza.fastmediasorter.wear.data.db.VoiceNoteIndexRebuilder
import com.sza.fastmediasorter.wear.data.db.WearDatabaseResetNotice
import com.sza.fastmediasorter.wear.data.db.WearVoiceNoteDatabase
import com.sza.fastmediasorter.wear.data.db.WearVoiceNoteMigrations
import com.sza.fastmediasorter.wear.data.repository.VoiceNoteRepositoryImpl
import com.sza.fastmediasorter.wear.domain.recorder.VoiceRecordingStateHolder
import com.sza.fastmediasorter.wear.domain.repository.VoiceNoteRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

/**
 * Hilt DI module providing database, DAO and recorder state for Wear voice notes.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearVoiceNoteModule {

    @Provides
    @Singleton
    fun provideVoiceNoteDurationReader(): VoiceNoteDurationReader = MediaMetadataVoiceNoteDurationReader()

    @Provides
    @Singleton
    @Suppress("TooGenericExceptionCaught")
    fun provideWearVoiceNoteDatabase(
        @ApplicationContext context: Context,
        rebuilder: VoiceNoteIndexRebuilder
    ): WearVoiceNoteDatabase = try {
        buildWearVoiceNoteDatabase(context).also { it.openHelper.writableDatabase }
    } catch (e: RuntimeException) {
        Timber.e(e, "Wear voice-note database failed to open - recreating it and rebuilding the index")
        recreateAndRebuild(context, rebuilder, e)
    }

    private fun recreateAndRebuild(
        context: Context,
        rebuilder: VoiceNoteIndexRebuilder,
        failure: Throwable
    ): WearVoiceNoteDatabase {
        context.deleteDatabase(WearVoiceNoteDatabase.DATABASE_NAME)
        val database = buildWearVoiceNoteDatabase(context)
        val recovered = rebuilder.rebuildInto(database.openHelper.writableDatabase)
        WearDatabaseResetNotice.recordReset(context, failure, recovered)
        return database
    }

    private fun buildWearVoiceNoteDatabase(context: Context): WearVoiceNoteDatabase =
        Room.databaseBuilder(
            context,
            WearVoiceNoteDatabase::class.java,
            WearVoiceNoteDatabase.DATABASE_NAME
        )
            .addMigrations(WearVoiceNoteMigrations.MIGRATION_1_2)
            .build()

    @Provides
    @Singleton
    fun provideVoiceNoteDao(database: WearVoiceNoteDatabase): VoiceNoteDao = database.voiceNoteDao()

    @Provides
    @Singleton
    fun provideVoiceNoteRepository(impl: VoiceNoteRepositoryImpl): VoiceNoteRepository = impl

    @Provides
    @Singleton
    fun provideVoiceRecordingStateHolder(): VoiceRecordingStateHolder = VoiceRecordingStateHolder()
}
