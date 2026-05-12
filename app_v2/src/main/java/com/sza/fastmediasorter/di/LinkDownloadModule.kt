package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.data.link.HtmlPageExtractionStrategy
import com.sza.fastmediasorter.data.link.InvisibleWebViewExtractionStrategy
import com.sza.fastmediasorter.data.link.cookie.LinkDownloadCookieJar
import com.sza.fastmediasorter.data.repository.AuthSessionRepositoryImpl
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
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

@Module
@InstallIn(SingletonComponent::class)
object LinkDownloadModule {

    @Provides
    @Singleton
    @Named("linkDownload")
    fun provideLinkDownloadClient(cookieJar: LinkDownloadCookieJar): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .addInterceptor(DefaultUserAgentInterceptor)
        .addNetworkInterceptor(HttpOnlyRedirectInterceptor)
        .build()

    /**
     * S0171: many CDNs / SPA backends (Instagram, TikTok, …) treat OkHttp's default
     * `User-Agent: okhttp/4.x` as a bot and reject it or serve a degraded response.
     * Set a real desktop-browser UA unless the caller already specified one.
     */
    private object DefaultUserAgentInterceptor : Interceptor {
        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.header("User-Agent") != null) return chain.proceed(request)
            return chain.proceed(request.newBuilder().header("User-Agent", BROWSER_UA).build())
        }
    }

    private object HttpOnlyRedirectInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val scheme = request.url.scheme.lowercase()
            require(scheme == "http" || scheme == "https") {
                "linkDownload OkHttp call rejected non-http(s) scheme: $scheme"
            }
            return chain.proceed(request)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LinkDownloadStrategiesModule {

    @Binds
    @IntoSet
    abstract fun bindDirect(impl: DirectFileExtractionStrategy): UrlExtractionStrategy

    @Binds
    @IntoSet
    abstract fun bindHtml(impl: HtmlPageExtractionStrategy): UrlExtractionStrategy

    @Binds
    @IntoSet
    abstract fun bindDynamic(impl: InvisibleWebViewExtractionStrategy): UrlExtractionStrategy

    @Binds
    abstract fun bindAuthSessionRepository(impl: AuthSessionRepositoryImpl): AuthSessionRepository
}