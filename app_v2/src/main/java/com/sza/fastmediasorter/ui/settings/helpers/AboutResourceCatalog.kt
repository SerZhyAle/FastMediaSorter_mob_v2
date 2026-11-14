package com.sza.fastmediasorter.ui.settings.helpers

import androidx.annotation.IdRes
import com.sza.fastmediasorter.R

/** What a row of the About page does when activated. */
sealed interface AboutResourceAction {

    /** A published page with an English, Russian and Ukrainian edition. */
    class LocalizedUrl(val en: String, val ru: String, val uk: String) : AboutResourceAction {
        fun forLanguage(language: String): String = when (language) {
            "ru" -> ru
            "uk" -> uk
            else -> en
        }
    }

    data object OpenLicenses : AboutResourceAction
    data object SendSupportEmail : AboutResourceAction
    data object ExportDiagnostics : AboutResourceAction
    data object OpenSystemInfo : AboutResourceAction
    data object OpenPermissions : AboutResourceAction
    data object OpenExtensions : AboutResourceAction
    data object OpenWelcomeTour : AboutResourceAction
    data object OpenExchangePortal : AboutResourceAction
    data object OpenCompanionGuide : AboutResourceAction
    data object OpenCompanionHome : AboutResourceAction
}

/** One About resource: a stable id, the row that shows it and the facility it opens. */
class AboutResource(
    val id: String,
    @IdRes val viewId: Int,
    val action: AboutResourceAction,
)

/**
 * The canonical inventory of the About page (SETTINGS-NAVIGATION 2.1 section 3 rules 3-4).
 *
 * Every product link the page offers is declared here once; the page's grouped row list is the
 * resource index, so a link added anywhere else would be a second, drifting copy. Rows whose facility
 * is flavor-gated (companion, extensions) are hidden by the launch manager, never shown as a stub.
 */
object AboutResourceCatalog {

    const val SITE_ROOT = "https://serzhyale.github.io/FastMediaSorter_mob_v2/"
    const val REPOSITORY_URL = "https://github.com/SerZhyAle/FastMediaSorter_mob_v2"
    private const val DOCS_ROOT = "${SITE_ROOT}docs"

    val resources: List<AboutResource> = listOf(
        AboutResource(
            "user_guide",
            R.id.btnUserGuide,
            AboutResourceAction.LocalizedUrl(
                en = "$DOCS_ROOT/howto/",
                ru = "$DOCS_ROOT/howto/index-ru.html",
                uk = "$DOCS_ROOT/howto/index-uk.html",
            ),
        ),
        AboutResource(
            "how_to",
            R.id.btnHowToGuides,
            AboutResourceAction.LocalizedUrl(
                en = "$DOCS_ROOT/HOW_TO.html",
                ru = "$DOCS_ROOT/HOW_TO-ru.html",
                uk = "$DOCS_ROOT/HOW_TO-uk.html",
            ),
        ),
        AboutResource("welcome_tour", R.id.btnOpenWelcome, AboutResourceAction.OpenWelcomeTour),
        AboutResource(
            "privacy",
            R.id.btnPrivacyPolicy,
            AboutResourceAction.LocalizedUrl(
                en = "$DOCS_ROOT/PRIVACY_POLICY.html",
                ru = "$DOCS_ROOT/PRIVACY_POLICY-ru.html",
                uk = "$DOCS_ROOT/PRIVACY_POLICY-uk.html",
            ),
        ),
        AboutResource("licenses", R.id.btnOpenSourceLicenses, AboutResourceAction.OpenLicenses),
        AboutResource("support_email", R.id.btnAboutSupport, AboutResourceAction.SendSupportEmail),
        AboutResource("diagnostics_export", R.id.btnAboutExportDiagnostics, AboutResourceAction.ExportDiagnostics),
        AboutResource("system_info", R.id.btnSystemInfo, AboutResourceAction.OpenSystemInfo),
        AboutResource("permissions", R.id.btnPermissionsManagement, AboutResourceAction.OpenPermissions),
        AboutResource("extensions", R.id.btnDownloadableExtensions, AboutResourceAction.OpenExtensions),
        AboutResource("site", R.id.btnAboutSite, AboutResourceAction.LocalizedUrl(SITE_ROOT, SITE_ROOT, SITE_ROOT)),
        AboutResource(
            "downloads",
            R.id.btnAboutDownloads,
            AboutResourceAction.LocalizedUrl(
                en = "$DOCS_ROOT/DOWNLOADS_EN.html",
                ru = "$DOCS_ROOT/DOWNLOADS_RU.html",
                uk = "$DOCS_ROOT/DOWNLOADS_UK.html",
            ),
        ),
        AboutResource(
            "repository",
            R.id.btnAboutRepository,
            AboutResourceAction.LocalizedUrl(REPOSITORY_URL, REPOSITORY_URL, REPOSITORY_URL),
        ),
        AboutResource("exchange_portal", R.id.btnMediaExchangePortal, AboutResourceAction.OpenExchangePortal),
        AboutResource("companion_home", R.id.btnAboutCompanionHome, AboutResourceAction.OpenCompanionHome),
        AboutResource("companion_guide", R.id.btnCompanionPublishGuide, AboutResourceAction.OpenCompanionGuide),
    )
}
