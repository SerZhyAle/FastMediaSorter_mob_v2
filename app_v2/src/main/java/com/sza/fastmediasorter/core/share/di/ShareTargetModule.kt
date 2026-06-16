package com.sza.fastmediasorter.core.share.di

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareTarget
import com.sza.fastmediasorter.core.share.ShareTargetAvailability
import com.sza.fastmediasorter.core.share.ShareTargetDefault
import com.sza.fastmediasorter.domain.model.MediaType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

/**
 * Declares the `Set<ShareTarget>` multibinding seam (S0452 foundation) and registers the ten
 * receivers of the unified «Send to..» menu (S0459).
 *
 * Each receiver is a pure declaration - id, title, default ([ShareTargetDefault], ADR-7),
 * availability ([ShareTargetAvailability]), candidate packages, and type applicability (ADR-3).
 * Send behaviour lives in the matching `ShareTargetHandler` bound in `ShareTargetHandlerModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ShareTargetModule {

    @Multibinds
    abstract fun shareTargets(): Set<ShareTarget>

    companion object {

        private val KEEP_PACKAGES = listOf("com.google.android.keep", "com.google.android.keep.notes")
        private val TELEGRAM_PACKAGES = listOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram",
            "org.telegram.plus",
            "org.telegram.messenger.beta",
        )

        @Provides
        @IntoSet
        fun systemShareTarget(): ShareTarget = ShareTarget(
            id = "system_share",
            titleRes = R.string.share_target_title_system_share,
            defaultEnabled = ShareTargetDefault.ALWAYS_ON,
            availability = ShareTargetAvailability.ALWAYS,
        )

        @Provides
        @IntoSet
        fun openInTarget(): ShareTarget = ShareTarget(
            id = "open_in",
            titleRes = R.string.share_target_title_open_in,
            defaultEnabled = ShareTargetDefault.ALWAYS_ON,
            availability = ShareTargetAvailability.ALWAYS,
        )

        @Provides
        @IntoSet
        fun printTarget(): ShareTarget = ShareTarget(
            id = "print",
            titleRes = R.string.menu_print,
            defaultEnabled = ShareTargetDefault.ALWAYS_ON,
            availability = ShareTargetAvailability.ALWAYS,
            applicableTypes = setOf(
                MediaType.IMAGE,
                MediaType.GIF,
                MediaType.PDF,
                MediaType.TEXT,
                MediaType.OFFICE_DOCUMENT,
            ),
        )

        @Provides
        @IntoSet
        fun emailTarget(): ShareTarget = ShareTarget(
            id = "email",
            titleRes = R.string.share_target_title_email,
            defaultEnabled = ShareTargetDefault.ON_IF_INTERNET,
            availability = ShareTargetAvailability.REQUIRES_INTERNET,
        )

        @Provides
        @IntoSet
        fun keepTextTarget(): ShareTarget = ShareTarget(
            id = "keep_text",
            titleRes = R.string.text_editor_action_send_keep,
            defaultEnabled = ShareTargetDefault.ON_IF_GOOGLE,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = KEEP_PACKAGES,
            applicableTypes = setOf(MediaType.TEXT),
        )

        @Provides
        @IntoSet
        fun keepDrawingTarget(): ShareTarget = ShareTarget(
            id = "keep_drawing",
            titleRes = R.string.text_editor_action_send_keep,
            defaultEnabled = ShareTargetDefault.ON_IF_GOOGLE,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = KEEP_PACKAGES,
            applicableTypes = setOf(MediaType.IMAGE),
        )

        @Provides
        @IntoSet
        fun lensTarget(): ShareTarget = ShareTarget(
            id = "lens",
            titleRes = R.string.google_lens,
            defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
            availability = ShareTargetAvailability.REQUIRES_GOOGLE,
            applicableTypes = setOf(MediaType.IMAGE, MediaType.GIF),
        )

        @Provides
        @IntoSet
        fun telegramTarget(): ShareTarget = ShareTarget(
            id = "telegram",
            // Package receiver: the menu shows the installed app's own label (S0459 ADR-5/owner
            // 2026-06-16); this neutral string is only a fallback. No brand literal is hardcoded.
            titleRes = R.string.share_target_title_app,
            defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = TELEGRAM_PACKAGES,
        )

        @Provides
        @IntoSet
        fun whatsAppTarget(): ShareTarget = ShareTarget(
            id = "whatsapp",
            titleRes = R.string.share_target_title_app,
            defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = listOf("com.whatsapp", "com.whatsapp.w4b"),
        )

        @Provides
        @IntoSet
        fun instagramTarget(): ShareTarget = ShareTarget(
            id = "instagram",
            titleRes = R.string.share_target_title_app,
            defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = listOf("com.instagram.android"),
            applicableTypes = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.GIF),
        )
    }
}
