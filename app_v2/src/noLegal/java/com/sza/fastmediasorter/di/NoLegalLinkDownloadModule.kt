package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.link.nolegal.NewPipeSiteExtractionStrategy
import com.sza.fastmediasorter.data.link.nolegal.YtDlpExtractionStrategy
import com.sza.fastmediasorter.domain.usecase.link.UrlExtractionStrategy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class NoLegalLinkDownloadModule {

    @Binds
    @IntoSet
    abstract fun bindSite(impl: NewPipeSiteExtractionStrategy): UrlExtractionStrategy

    // S0174: bind yt-dlp strategy first in CANONICAL_ORDER ("ytdlp" at index 0).
    @Binds
    @IntoSet
    abstract fun bindYtDlp(impl: YtDlpExtractionStrategy): UrlExtractionStrategy
}