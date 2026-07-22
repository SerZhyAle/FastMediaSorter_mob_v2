package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.data.link.HtmlPageExtractionStrategy
import com.sza.fastmediasorter.data.link.InvisibleWebViewExtractionStrategy
import com.sza.fastmediasorter.data.link.LinkDownloadUserAgents
import com.sza.fastmediasorter.data.link.cookie.LinkDownloadCookieJar
import com.sza.fastmediasorter.data.repository.AuthSessionRepositoryImpl
import com.sza.fastmediasorter.domain.repository.AuthSessionRepository
import com.sza.fastmediasorter.domain.usecase.link.LinkDownloadPacer
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
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
        // S1139: no total-call ceiling. callTimeout bounds the WHOLE call incl. body transfer, so a
        // fixed value cancels a legitimately long media download mid-stream (OkHttp aborts with an
        // HTTP/2 CANCEL -> InterruptedIOException: timeout). Idle connect/read timeouts below still
        // fail a genuinely stalled connection; arbitrary-size downloads are bounded by those, not a
        // wall-clock cap.
        .callTimeout(0, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .addInterceptor(DefaultUserAgentInterceptor)
        .addNetworkInterceptor(HttpOnlyRedirectInterceptor)
        .build()

    /**
     * S0171/S0182: many CDNs / SPA backends reject OkHttp's default
     * `User-Agent: okhttp/4.x`, but a desktop fallback is also wrong for our mobile app.
     * Use the shared Android Chrome Mobile fallback unless the caller already pinned a UA.
     */
    private object DefaultUserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.header("User-Agent") != null) return chain.proceed(request)
            return chain.proceed(
                request.newBuilder()
                    .header("User-Agent", LinkDownloadUserAgents.MOBILE_BROWSER_UA)
                    .build(),
            )
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

    // S0973: declares the (possibly empty) pacer set. No flavor except noLegal contributes an
    // element, so standard/lite/photos/legacy inject an empty set and add no inter-item pause.
    @Multibinds
    abstract fun linkDownloadPacers(): Set<LinkDownloadPacer>
}