package com.sza.fastmediasorter.di

import com.sza.fastmediasorter.data.link.nolegal.ArtStationExtractionStrategy
import com.sza.fastmediasorter.data.link.nolegal.DailymotionExtractionStrategy
import com.sza.fastmediasorter.data.link.nolegal.DeviantArtExtractionStrategy
import com.sza.fastmediasorter.data.link.nolegal.HumanizedCarouselPacer
import com.sza.fastmediasorter.data.link.nolegal.NewPipeSiteExtractionStrategy
import com.sza.fastmediasorter.data.link.nolegal.TelegramExtractionStrategy
import com.sza.fastmediasorter.data.link.nolegal.VimeoExtractionStrategy
import com.sza.fastmediasorter.data.link.nolegal.YtDlpExtractionStrategy
import com.sza.fastmediasorter.domain.usecase.link.LinkDownloadPacer
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

    // S0177: native site extractors - ArtStation, DeviantArt, Vimeo, Dailymotion.
    @Binds
    @IntoSet
    abstract fun bindArtStation(impl: ArtStationExtractionStrategy): UrlExtractionStrategy

    @Binds
    @IntoSet
    abstract fun bindDeviantArt(impl: DeviantArtExtractionStrategy): UrlExtractionStrategy

    @Binds
    @IntoSet
    abstract fun bindVimeo(impl: VimeoExtractionStrategy): UrlExtractionStrategy

    @Binds
    @IntoSet
    abstract fun bindDailymotion(impl: DailymotionExtractionStrategy): UrlExtractionStrategy

    // S0303: lightweight public t.me post extractor (id "telegram"), ahead of "ytdlp" in CANONICAL_ORDER.
    @Binds
    @IntoSet
    abstract fun bindTelegram(impl: TelegramExtractionStrategy): UrlExtractionStrategy

    // S0973: only the noLegal flavor contributes a pacer, so carousel pauses are noLegal-only.
    @Binds
    @IntoSet
    abstract fun bindHumanizedPacer(impl: HumanizedCarouselPacer): LinkDownloadPacer
}