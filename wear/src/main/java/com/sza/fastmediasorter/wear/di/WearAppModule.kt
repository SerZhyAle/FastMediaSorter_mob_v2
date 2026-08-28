package com.sza.fastmediasorter.wear.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.db.VoiceNoteDao
import com.sza.fastmediasorter.wear.data.db.WearVoiceNoteDatabase
import com.sza.fastmediasorter.wear.data.network.StreamNetworkHoldManager
import com.sza.fastmediasorter.wear.data.network.WearNetworkChannelMonitorImpl
import com.sza.fastmediasorter.wear.data.network.ftp.FtpConnectionTest
import com.sza.fastmediasorter.wear.data.network.ftp.FtpDataSource
import com.sza.fastmediasorter.wear.data.network.itunes.ITunesApiService
import com.sza.fastmediasorter.wear.data.network.sftp.SftpConnectionTest
import com.sza.fastmediasorter.wear.data.network.sftp.SftpDataSource
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.data.preferences.NetworkSourceRepositoryImpl
import com.sza.fastmediasorter.wear.data.preferences.WearNowPlayingRepositoryImpl
import com.sza.fastmediasorter.wear.data.preferences.WearPreferencesRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.AlbumArtRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.VoiceNoteRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.WearFavoritesRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.WearFileReceiverRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.WearFileSenderRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.WearLocalFolderRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.WearMediaRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.WearOpenOnPhoneRepositoryImpl
import com.sza.fastmediasorter.wear.data.wear.AndroidWearSystemInfoDataSource
import com.sza.fastmediasorter.wear.domain.game.GameBoardGenerator
import com.sza.fastmediasorter.wear.domain.recorder.VoiceRecordingStateHolder
import com.sza.fastmediasorter.wear.domain.repository.AlbumArtRepository
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.StreamNetworkHold
import com.sza.fastmediasorter.wear.domain.repository.VoiceNoteRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFileReceiverRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFileSenderRepository
import com.sza.fastmediasorter.wear.domain.repository.WearLocalFolderRepository
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearNetworkChannelMonitor
import com.sza.fastmediasorter.wear.domain.repository.WearNowPlayingRepository
import com.sza.fastmediasorter.wear.domain.repository.WearOpenOnPhoneRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

/** Applied to connect, read and write alike: one number, because a watch waits for all three the same. */
private const val HTTP_TIMEOUT_SECONDS = 10L

/**
 * Qualifier for EncryptedSharedPreferences.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EncryptedPrefs

/**
 * Hilt DI module for Wear OS app.
 * Provides repositories, ExoPlayer, and system services.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearAppModule {

    @Provides
    @Singleton
    fun provideContentResolver(
        @ApplicationContext context: Context
    ): android.content.ContentResolver {
        return context.contentResolver
    }

    /**
     * S0725: NOT @Singleton. Audio and video player VMs each own a private ExoPlayer so each can
     * release() it in onCleared without killing the other's instance. A process-lived singleton was
     * never released (native HandlerThread / AudioTrack / codecs leaked for the whole process) and was
     * shared between two owners that could not safely release it. Per-VM ownership matches the per-screen
     * player lifecycle (audio and video are separate screens with no cross-screen playback continuity).
     */
    @Provides
    fun provideExoPlayer(
        @ApplicationContext context: Context
    ): androidx.media3.exoplayer.ExoPlayer {
        // S0896: no setAudioAttributes(..) - the player never requested audio focus, unlike every
        // app_v2 player host (see e.g. ui/player/helpers/PlayerSetupHelper.kt).
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()
        val handleAudioFocus = true
        return androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, handleAudioFocus)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideWearMediaRepository(
        contentResolver: android.content.ContentResolver
    ): WearMediaRepository {
        return WearMediaRepositoryImpl(contentResolver)
    }

    @Provides
    @Singleton
    fun provideWearLocalFolderRepository(
        @ApplicationContext context: Context,
        contentResolver: android.content.ContentResolver
    ): WearLocalFolderRepository {
        return WearLocalFolderRepositoryImpl(context, contentResolver)
    }

    @Provides
    @Singleton
    fun provideWearPreferencesRepository(
        @ApplicationContext context: Context,
        requestWearComplicationRefreshUseCase:
        com.sza.fastmediasorter.wear.domain.usecase.RequestWearComplicationRefreshUseCase
    ): WearPreferencesRepository {
        return WearPreferencesRepositoryImpl(context, requestWearComplicationRefreshUseCase)
    }

    @Provides
    @Singleton
    fun provideWearNowPlayingRepository(
        @ApplicationContext context: Context
    ): WearNowPlayingRepository {
        return WearNowPlayingRepositoryImpl(context)
    }

    // S1710: GameBoardGenerator takes a defaulted attempt budget that Dagger cannot bind, so it
    // is constructed here rather than injected. Stateless, hence shared.
    @Provides
    @Singleton
    fun provideGameBoardGenerator(): GameBoardGenerator = GameBoardGenerator()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideRetrofit(gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideITunesApiService(retrofit: Retrofit): ITunesApiService {
        return retrofit.create(ITunesApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAlbumArtRepository(
        iTunesApiService: ITunesApiService
    ): AlbumArtRepository {
        return AlbumArtRepositoryImpl(iTunesApiService)
    }

    @Provides
    @Singleton
    @EncryptedPrefs
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "network_sources_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Provides
    @Singleton
    fun provideSmbDataSource(): SmbDataSource {
        return SmbDataSource()
    }

    @Provides
    @Singleton
    fun provideFtpConnectionTest(): FtpConnectionTest = FtpConnectionTest()

    @Provides
    @Singleton
    fun provideSftpConnectionTest(): SftpConnectionTest = SftpConnectionTest()

    @Provides
    @Singleton
    fun provideFtpDataSource(): FtpDataSource = FtpDataSource()

    @Provides
    @Singleton
    fun provideSftpDataSource(): SftpDataSource = SftpDataSource()

    @Provides
    @Singleton
    fun provideNetworkSourceRepository(
        @EncryptedPrefs encryptedPrefs: SharedPreferences,
        smbDataSource: SmbDataSource,
        ftpConnectionTest: FtpConnectionTest,
        sftpConnectionTest: SftpConnectionTest
    ): NetworkSourceRepository {
        return NetworkSourceRepositoryImpl(encryptedPrefs, smbDataSource, ftpConnectionTest, sftpConnectionTest)
    }

    @Provides
    @Singleton
    fun provideWearFavoritesRepository(impl: WearFavoritesRepositoryImpl): WearFavoritesRepository = impl

    @Provides
    @Singleton
    fun provideWearFileReceiverRepository(
        impl: WearFileReceiverRepositoryImpl
    ): WearFileReceiverRepository = impl

    @Provides
    @Singleton
    fun provideWearFileSenderRepository(
        impl: WearFileSenderRepositoryImpl
    ): WearFileSenderRepository = impl

    @Provides
    @Singleton
    fun provideWearOpenOnPhoneRepository(
        impl: WearOpenOnPhoneRepositoryImpl
    ): WearOpenOnPhoneRepository = impl

    @Provides
    @Singleton
    fun provideWearSystemInfoDataSource(
        impl: AndroidWearSystemInfoDataSource
    ): WearSystemInfoDataSource = impl

    @Provides
    @Singleton
    fun provideOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .connectTimeout(HTTP_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(HTTP_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(HTTP_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideWearStreamChannelRepository(
        impl: com.sza.fastmediasorter.wear.data.repository.WearStreamChannelRepositoryImpl
    ): com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository = impl

    @Provides
    @Singleton
    fun provideWearNetworkChannelMonitor(
        impl: WearNetworkChannelMonitorImpl
    ): WearNetworkChannelMonitor = impl

    @Provides
    @Singleton
    fun provideStreamNetworkHold(impl: StreamNetworkHoldManager): StreamNetworkHold = impl

    // S1862: the voice-note store. Room, version 1 - no installed watch database predates this
    // build, so no migration branch exists to fall back to.
    @Provides
    @Singleton
    fun provideWearVoiceNoteDatabase(
        @ApplicationContext context: Context
    ): WearVoiceNoteDatabase = Room.databaseBuilder(
        context,
        WearVoiceNoteDatabase::class.java,
        WearVoiceNoteDatabase.DATABASE_NAME
    ).build()

    @Provides
    @Singleton
    fun provideVoiceNoteDao(database: WearVoiceNoteDatabase): VoiceNoteDao = database.voiceNoteDao()

    @Provides
    @Singleton
    fun provideVoiceNoteRepository(impl: VoiceNoteRepositoryImpl): VoiceNoteRepository = impl

    /**
     * Application-scoped by construction: the recording service writes here and the recorder screen
     * reads, and the two must survive each other. A holder scoped to either would drop the state at
     * exactly the moment ADR-4 says the session has to keep going.
     */
    @Provides
    @Singleton
    fun provideVoiceRecordingStateHolder(): VoiceRecordingStateHolder = VoiceRecordingStateHolder()
}
