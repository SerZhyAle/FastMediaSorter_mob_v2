package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.data.link.HtmlPageExtractionStrategy
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * S0003: Hilt bindings for the link-auto-download channel.
 *
 * Phase 02 only ships the OkHttp client (with redirect-scheme guard).
 * Phase 03/04 add the strategies via `@IntoSet` multibindings (in a sibling abstract module).
 */
@Module
@InstallIn(SingletonComponent::class)
object LinkDownloadModule {

    @Provides
    @Singleton
    @Named("linkDownload")
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor(HttpOnlyRedirectInterceptor)
        .build()

    private object HttpOnlyRedirectInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val scheme = req.url.scheme.lowercase()
            require(scheme == "http" || scheme == "https") {
                "linkDownload OkHttp call rejected non-http(s) scheme: $scheme"
            }
            return chain.proceed(req)
        }
    }
}

/**
 * Multibindings module for [UrlExtractionStrategy] implementations.
 * Phase 03 binds `direct`; Phase 04 will append `html`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LinkDownloadStrategiesModule {

    @Binds
    @IntoSet
    abstract fun bindDirect(impl: DirectFileExtractionStrategy): UrlExtractionStrategy

    @Binds
    @IntoSet
    abstract fun bindHtml(impl: HtmlPageExtractionStrategy): UrlExtractionStrategy
}
