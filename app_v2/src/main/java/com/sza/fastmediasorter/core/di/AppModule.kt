package com.sza.fastmediasorter.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveCredentialsManager
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveHttpClient
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.data.remote.ITunesApiService
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
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
import kotlinx.coroutines.asCoroutineDispatcher
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

internal fun isExpectedHttpFallback(url: HttpUrl): Boolean {
    val path = url.encodedPath
    return path.endsWith("/delivery-manifest.json") ||
        when (url.host) {
            "itunes.apple.com" -> path == "/search"
            "api.deezer.com" -> path == "/search"
            "musicbrainz.org" -> path.startsWith("/ws/2/recording")
            "coverartarchive.org" -> path.startsWith("/release/")
            else -> false
        }
}

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

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudTokenDispatcher


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

    /**
     * Dedicated dispatcher for Google OAuth token issuance, isolated from the shared
     * [Dispatchers.IO] pool.
     *
     * Token issuance (`GoogleAuthUtil.getToken`) is a short blocking auth call that cloud thumbnail
     * Glide DataFetchers invoke through `runBlocking`. Running it on `Dispatchers.IO` let it queue
     * behind a heavy media scan that saturates the elastic IO pool; in the worst case IO threads
     * already parked in their own token `runBlocking` waits could not be served, deadlocking the
     * pool and stalling cloud thumbnails indefinitely. A separate single-thread pool guarantees the
     * token continuation always has a thread, regardless of IO saturation. Token issuance is
     * serialised by a mutex upstream, so one thread suffices; the thread is a daemon so it never
     * blocks JVM shutdown.
     */
    @CloudTokenDispatcher
    @Provides
    @Singleton
    fun provideCloudTokenDispatcher(): CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "cloud-token-issuer").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    
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
    fun provideSettingsMigrationManager(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>
    ): com.sza.fastmediasorter.data.SettingsMigrationManager {
        return com.sza.fastmediasorter.data.SettingsMigrationManager(context, dataStore)
    }
    
    // SftpClient and FtpClient are constructor-injected (@Inject + @Singleton on the class).
    // Their explicit @Provides bindings were removed in S0025 since both now require
    // NetworkReachabilityGate - Hilt resolves them via the constructor.

    // GoogleDriveRestClient is constructor-injected (@Inject + @Singleton on the class).
    // Explicit @Provides removed in S0025 - Hilt resolves it via the constructor.
    
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
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            // Chucker records all HTTP traffic silently in its DB (no notification spam).
            // Access via shake gesture or adb shell am start -n com.sza.fastmediasorter/.chucker.ChuckerActivity
            val chuckerCollector = ChuckerCollector(context, showNotification = false)
            builder.addInterceptor(
                ChuckerInterceptor.Builder(context)
                    .collector(chuckerCollector)
                    .alwaysReadResponseBody(true)
                    .build()
            )
            // Log HTTP errors (4xx / 5xx) to Logcat via Timber so they surface in search-log.ps1 -Errors
            builder.addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (!response.isSuccessful) {
                    if (!isExpectedHttpFallback(request.url)) {
                        Timber.e("HTTP ${response.code} ${request.method} ${request.url}")
                    }
                }
                response
            }
        }

        return builder.build()
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
