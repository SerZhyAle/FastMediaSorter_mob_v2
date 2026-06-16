package com.sza.fastmediasorter.core.share.di

import com.sza.fastmediasorter.core.share.ShareTargetHandler
import com.sza.fastmediasorter.core.share.handlers.EmailShareTargetHandler
import com.sza.fastmediasorter.core.share.handlers.InstagramShareTargetHandler
import com.sza.fastmediasorter.core.share.handlers.KeepDrawingShareTargetHandler
import com.sza.fastmediasorter.core.share.handlers.KeepTextShareTargetHandler
import com.sza.fastmediasorter.core.share.handlers.LensShareTargetHandler
import com.sza.fastmediasorter.core.share.handlers.OpenInShareTargetHandler
import com.sza.fastmediasorter.core.share.handlers.PrintShareTargetHandler
import com.sza.fastmediasorter.core.share.handlers.SystemShareTargetHandler
import com.sza.fastmediasorter.core.share.handlers.TelegramShareTargetHandler
import com.sza.fastmediasorter.core.share.handlers.WhatsAppShareTargetHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

/**
 * Binds each [ShareTargetHandler] into a `Map<String, ShareTargetHandler>` keyed by the receiver id
 * (S0459), so the unified «Send to..» menu resolves the handler for a chosen receiver. Every id
 * registered as a `ShareTarget` in [ShareTargetModule] has exactly one handler entry here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ShareTargetHandlerModule {

    @Binds
    @IntoMap
    @StringKey("system_share")
    abstract fun bindSystemShare(handler: SystemShareTargetHandler): ShareTargetHandler

    @Binds
    @IntoMap
    @StringKey("open_in")
    abstract fun bindOpenIn(handler: OpenInShareTargetHandler): ShareTargetHandler

    @Binds
    @IntoMap
    @StringKey("print")
    abstract fun bindPrint(handler: PrintShareTargetHandler): ShareTargetHandler

    @Binds
    @IntoMap
    @StringKey("email")
    abstract fun bindEmail(handler: EmailShareTargetHandler): ShareTargetHandler

    @Binds
    @IntoMap
    @StringKey("keep_text")
    abstract fun bindKeepText(handler: KeepTextShareTargetHandler): ShareTargetHandler

    @Binds
    @IntoMap
    @StringKey("keep_drawing")
    abstract fun bindKeepDrawing(handler: KeepDrawingShareTargetHandler): ShareTargetHandler

    @Binds
    @IntoMap
    @StringKey("lens")
    abstract fun bindLens(handler: LensShareTargetHandler): ShareTargetHandler

    @Binds
    @IntoMap
    @StringKey("telegram")
    abstract fun bindTelegram(handler: TelegramShareTargetHandler): ShareTargetHandler

    @Binds
    @IntoMap
    @StringKey("whatsapp")
    abstract fun bindWhatsApp(handler: WhatsAppShareTargetHandler): ShareTargetHandler

    @Binds
    @IntoMap
    @StringKey("instagram")
    abstract fun bindInstagram(handler: InstagramShareTargetHandler): ShareTargetHandler
}
