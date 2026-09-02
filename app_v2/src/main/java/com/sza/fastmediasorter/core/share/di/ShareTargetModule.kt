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
 * Declares the `Set<ShareTarget>` multibinding seam (S0452 foundation) and registers the available
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
        internal val VIBER_PACKAGES = listOf("com.viber.voip")
        internal val MESSENGER_PACKAGES = listOf("com.facebook.orca")
        internal val TIKTOK_PACKAGES = listOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
        )

        @Provides
        @IntoSet
        fun systemShareTarget(): ShareTarget = ShareTarget(
            id = "system_share",
            titleRes = R.string.share_target_title_system_share,
            iconRes = R.drawable.ic_share,
            defaultEnabled = ShareTargetDefault.ALWAYS_ON,
            availability = ShareTargetAvailability.ALWAYS,
            batchCapable = true,
            // S0631: the system chooser also carries a plain-text payload (EXTRA_TEXT), so it is the
            // universal text receiver for a stream link (messenger / clipboard / SMS / email).
            textCapable = true,
            subtitleRes = R.string.share_target_desc_system_share,
            helpMessageRes = R.string.share_target_help_system_share,
        )

        @Provides
        @IntoSet
        fun openInTarget(): ShareTarget = ShareTarget(
            id = "open_in",
            titleRes = R.string.share_target_title_open_in,
            iconRes = R.drawable.ic_open_in_browse,
            defaultEnabled = ShareTargetDefault.ALWAYS_ON,
            availability = ShareTargetAvailability.ALWAYS,
            subtitleRes = R.string.share_target_desc_open_in,
            helpMessageRes = R.string.share_target_help_open_in,
        )

        @Provides
        @IntoSet
        fun watchTarget(): ShareTarget = ShareTarget(
            id = "watch",
            titleRes = R.string.share_target_title_watch,
            iconRes = R.drawable.ic_watch,
            defaultEnabled = ShareTargetDefault.ON_IF_WATCH,
            availability = ShareTargetAvailability.REQUIRES_WATCH,
            // ADR-3: the watch renders exactly these four families, so a document never shows a dead
            // entry - the type filter hides the receiver rather than letting it fail on the watch.
            applicableTypes = setOf(MediaType.IMAGE, MediaType.GIF, MediaType.VIDEO, MediaType.AUDIO),
            batchCapable = false,
            requiresLocalFile = true,
            textCapable = false,
            subtitleRes = R.string.share_target_desc_watch,
            helpMessageRes = R.string.share_target_help_watch,
        )

        @Provides
        @IntoSet
        fun printTarget(): ShareTarget = ShareTarget(
            id = "print",
            titleRes = R.string.menu_print,
            iconRes = R.drawable.ic_print,
            defaultEnabled = ShareTargetDefault.ALWAYS_ON,
            availability = ShareTargetAvailability.ALWAYS,
            applicableTypes = setOf(
                MediaType.IMAGE,
                MediaType.GIF,
                MediaType.PDF,
                MediaType.TEXT,
                MediaType.OFFICE_DOCUMENT,
            ),
            subtitleRes = R.string.share_target_desc_print,
            helpMessageRes = R.string.share_target_help_print,
        )

        @Provides
        @IntoSet
        fun emailTarget(): ShareTarget = ShareTarget(
            id = "email",
            titleRes = R.string.share_target_title_email,
            iconRes = R.drawable.ic_send_email,
            defaultEnabled = ShareTargetDefault.ON_IF_INTERNET,
            availability = ShareTargetAvailability.REQUIRES_INTERNET,
            batchCapable = true,
            subtitleRes = R.string.share_target_desc_email,
            helpMessageRes = R.string.share_target_help_email,
        )

        @Provides
        @IntoSet
        fun keepTextTarget(): ShareTarget = ShareTarget(
            id = "keep_text",
            // S0463: unique title per content-type variant (was: text_editor_action_send_keep - same as keep_drawing)
            titleRes = R.string.share_target_title_keep_text,
            iconRes = R.drawable.ic_send_note,
            defaultEnabled = ShareTargetDefault.ON_IF_GOOGLE,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = KEEP_PACKAGES,
            applicableTypes = setOf(MediaType.TEXT),
            // Keep-text shares the loaded text payload (content.text), not the file - never needs a
            // downloaded local copy, so it stays available for remote text without materialization (S0493).
            requiresLocalFile = false,
            // S0631: consumes content.text directly, so it survives the text-only «Send to..» filter.
            textCapable = true,
            subtitleRes = R.string.share_target_desc_keep_text,
            helpMessageRes = R.string.share_target_help_keep_text,
        )

        @Provides
        @IntoSet
        fun keepDrawingTarget(): ShareTarget = ShareTarget(
            id = "keep_drawing",
            // S0463: unique title per content-type variant (was: text_editor_action_send_keep - same as keep_text)
            titleRes = R.string.share_target_title_keep_drawing,
            iconRes = R.drawable.ic_send_note_brush,
            defaultEnabled = ShareTargetDefault.ON_IF_GOOGLE,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = KEEP_PACKAGES,
            applicableTypes = setOf(MediaType.IMAGE),
            subtitleRes = R.string.share_target_desc_keep_drawing,
            helpMessageRes = R.string.share_target_help_keep_drawing,
        )

        @Provides
        @IntoSet
        fun lensTarget(): ShareTarget = ShareTarget(
            id = "lens",
            titleRes = R.string.google_lens,
            iconRes = R.drawable.ic_google_lens,
            defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
            availability = ShareTargetAvailability.REQUIRES_GOOGLE,
            applicableTypes = setOf(MediaType.IMAGE, MediaType.GIF),
            subtitleRes = R.string.share_target_desc_lens,
            helpMessageRes = R.string.share_target_help_lens,
        )

        @Provides
        @IntoSet
        fun telegramTarget(): ShareTarget = ShareTarget(
            id = "telegram",
            // Package receiver: the menu shows the installed app's own label (S0459 ADR-5/owner
            // 2026-06-16); this neutral string is only a fallback. No brand literal is hardcoded.
            titleRes = R.string.share_target_title_app,
            iconRes = R.drawable.ic_send_plane,
            defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = TELEGRAM_PACKAGES,
            batchCapable = true,
            subtitleRes = R.string.share_target_desc_package_app,
            helpMessageRes = R.string.share_target_help_package_app,
        )

        @Provides
        @IntoSet
        fun whatsAppTarget(): ShareTarget = ShareTarget(
            id = "whatsapp",
            titleRes = R.string.share_target_title_app,
            iconRes = R.drawable.ic_send_chat,
            defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = listOf("com.whatsapp", "com.whatsapp.w4b"),
            batchCapable = true,
            subtitleRes = R.string.share_target_desc_package_app,
            helpMessageRes = R.string.share_target_help_package_app,
        )

        @Provides
        @IntoSet
        fun viberTarget(): ShareTarget = ShareTarget(
            id = "viber",
            titleRes = R.string.share_target_title_app,
            iconRes = R.drawable.ic_send_phone_chat,
            defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = VIBER_PACKAGES,
            subtitleRes = R.string.share_target_desc_package_app,
            helpMessageRes = R.string.share_target_help_package_app,
        )

        @Provides
        @IntoSet
        fun messengerTarget(): ShareTarget = ShareTarget(
            id = "messenger",
            titleRes = R.string.share_target_title_app,
            iconRes = R.drawable.ic_send_bolt_chat,
            defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = MESSENGER_PACKAGES,
            subtitleRes = R.string.share_target_desc_package_app,
            helpMessageRes = R.string.share_target_help_package_app,
        )

        @Provides
        @IntoSet
        fun instagramTarget(): ShareTarget = ShareTarget(
            id = "instagram",
            titleRes = R.string.share_target_title_app,
            iconRes = R.drawable.ic_send_camera,
            defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = listOf("com.instagram.android"),
            applicableTypes = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.GIF),
            // ADR-4: Instagram's ACTION_SEND share flow accepts a single item; it does not handle
            // ACTION_SEND_MULTIPLE. Single-file receiver - applies to the first file on a multi-select.
            batchCapable = false,
            subtitleRes = R.string.share_target_desc_package_app,
            helpMessageRes = R.string.share_target_help_package_app,
        )

        @Provides
        @IntoSet
        fun tiktokTarget(): ShareTarget = ShareTarget(
            id = "tiktok",
            titleRes = R.string.share_target_title_app,
            iconRes = R.drawable.ic_send_music_note,
            defaultEnabled = ShareTargetDefault.ALWAYS_OFF,
            availability = ShareTargetAvailability.PACKAGE_INSTALLED,
            packages = TIKTOK_PACKAGES,
            applicableTypes = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.GIF),
            subtitleRes = R.string.share_target_desc_package_app,
            helpMessageRes = R.string.share_target_help_package_app,
        )
    }
}
