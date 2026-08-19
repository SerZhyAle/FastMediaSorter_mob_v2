package com.sza.fastmediasorter.wear.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sza.fastmediasorter.wear.data.network.itunes.ITunesApiService
import com.sza.fastmediasorter.wear.data.network.ftp.FtpConnectionTest
import com.sza.fastmediasorter.wear.data.network.ftp.FtpDataSource
import com.sza.fastmediasorter.wear.data.network.sftp.SftpConnectionTest
import com.sza.fastmediasorter.wear.data.network.sftp.SftpDataSource
import com.sza.fastmediasorter.wear.data.network.smb.SmbDataSource
import com.sza.fastmediasorter.wear.data.preferences.NetworkSourceRepositoryImpl
import com.sza.fastmediasorter.wear.data.preferences.WearPreferencesRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.AlbumArtRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.WearMediaRepositoryImpl
import com.sza.fastmediasorter.wear.data.repository.WearFavoritesRepositoryImpl
import com.sza.fastmediasorter.wear.domain.repository.AlbumArtRepository
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearMediaRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

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
    fun provideWearPreferencesRepository(
        @ApplicationContext context: Context
    ): WearPreferencesRepository {
        return WearPreferencesRepositoryImpl(context)
    }
    
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
    fun provideOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideWearStreamChannelRepository(
        impl: com.sza.fastmediasorter.wear.data.repository.WearStreamChannelRepositoryImpl
    ): com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository = impl
}
