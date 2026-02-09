package com.sza.fastmediasorter.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveCredentialsManager
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveHttpClient
import com.sza.fastmediasorter.data.remote.ITunesApiService
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context

    @IoDispatcher
    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @MainDispatcher
    @Provides
    @Singleton
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @DefaultDispatcher
    @Provides
    @Singleton
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @ApplicationScope
    @Provides
    @Singleton
    fun provideApplicationScope(@IoDispatcher ioDispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(ioDispatcher + SupervisorJob())

    
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(
                produceNewData = { emptyPreferences() }
            ),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("settings") }
        )
    }
    
    @Provides
    @Singleton
    fun provideSftpClient(): SftpClient {
        return SftpClient()
    }
    
    @Provides
    @Singleton
    fun provideFtpClient(): FtpClient {
        return FtpClient()
    }
    
    @Provides
    @Singleton
    fun provideGoogleDriveRestClient(
        @ApplicationContext context: Context,
        credentialsManager: GoogleDriveCredentialsManager,
        httpClient: GoogleDriveHttpClient
    ): GoogleDriveRestClient {
        return GoogleDriveRestClient(context, credentialsManager, httpClient)
    }
    
    @Provides
    @Singleton
    fun provideMediaFilesCacheManager(): MediaFilesCacheManager {
        return MediaFilesCacheManager
    }
    
    @Provides
    @Singleton
    fun provideUnifiedFileCache(@ApplicationContext context: Context): UnifiedFileCache {
        return UnifiedFileCache(context)
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideITunesApiService(retrofit: Retrofit): ITunesApiService {
        return retrofit.create(ITunesApiService::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {
    
    // LocalMediaScanner and SmbMediaScanner will be automatically provided via @Inject constructor
    // MediaScannerFactory will handle scanner selection based on resource type
}
