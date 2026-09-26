package com.sza.fastmediasorter.ui.common.input

/**
 * Maps an [UiSurface] to the documentation-portal page that describes that screen, opened from
 * the F1 help dialog.
 *
 * Pages are keyed by the permanent `page_id` of `docs/docs-pages-manifest.jsonl` (S2945), never
 * by a hand-typed address: `InputHelpLinkResolverTest` fails when an id leaves the manifest, its
 * `canonical_path` moves, or a declared translation file is missing. For general support routing
 * (help, bug report, feedback) use [com.sza.fastmediasorter.ui.common.support.SupportIntentFactory].
 */
object InputHelpLinkResolver {

    /** Root the portal's `canonical_path` values are relative to. */
    const val SITE_BASE = "https://serzhyale.github.io/FastMediaSorter_mob_v2/"

    private const val ENGLISH = "en"
    private const val HTML_SUFFIX = ".html"

    /**
     * One portal page.
     *
     * @param translatedLanguages UI languages whose `<name>-<lang>.html` sibling is published
     *   (site locale-suffix scheme). Only the languages listed here get a translated address;
     *   every other language opens the English original.
     */
    data class DocsHelpPage(
        val pageId: String,
        val path: String,
        val translatedLanguages: Set<String> = emptySet(),
    )

    private val MAIN_OVERVIEW = page("getting-started.main-screen-overview", "getting-started/main-screen-overview")
    private val STORAGE_SOURCES = page("storage.storage-sources-setup", "storage/storage-sources-setup")

    /** Page describing [surface]. */
    fun pageFor(surface: UiSurface): DocsHelpPage = when (surface) {
        UiSurface.MAIN -> MAIN_OVERVIEW
        UiSurface.BROWSE -> page("browsing.grid-and-list-views", "browsing/grid-and-list-views")
        UiSurface.PLAYER -> page("player.video-playback-controls", "player/video-playback-controls")
        UiSurface.VR_PLAYER -> page("vr.spatial-cinema-playback", "vr/spatial-cinema-playback")
        UiSurface.SETTINGS -> page("settings.settings-overview-and-search", "settings/settings-overview-and-search")
        UiSurface.ADD_RESOURCE -> STORAGE_SOURCES
        UiSurface.CLOUD_PICKER -> page("storage.network-and-cloud-sources", "storage/network-and-cloud-sources")
        UiSurface.DUPLICATES -> page("storage.cleaning-up-space", "storage/cleaning-up-space")
        UiSurface.RESOURCE_EDITOR -> STORAGE_SOURCES
        UiSurface.RECEIVE_SHARE -> page("tools.fast-sharing-and-export", "tools/fast-sharing-and-export")
        UiSurface.WIDGET_CONFIG -> page("launcher.home-screen-widgets", "launcher/home-screen-widgets")
        UiSurface.WELCOME -> page("getting-started.welcome-and-setup", "getting-started/welcome-and-setup")
        UiSurface.DIALOG -> page("general.keyboard-dpad-tv-navigation", "general/keyboard-dpad-tv-navigation")
        UiSurface.SCHEDULED_OPS -> page("storage.scheduled-operations", "storage/scheduled-operations")
        UiSurface.STREAMS -> page("streams.channel-catalog-browsing", "streams/channel-catalog-browsing")
        UiSurface.AUTH_SESSIONS -> page("storage.network-and-cloud-sources", "storage/network-and-cloud-sources")
        UiSurface.KEYBINDING_REMAP -> page("settings.controls-and-key-remapping", "settings/controls-and-key-remapping")
        UiSurface.WEAR_COMPANION -> page("wear.installation-and-pairing", "wear/installation-and-pairing")
    }

    /** Full address of the page for [surface] in [language], English when no translation is published. */
    fun urlFor(surface: UiSurface, language: String): String = urlFor(pageFor(surface), language)

    /** Full address of [page] in [language], English when no translation is published. */
    fun urlFor(page: DocsHelpPage, language: String): String {
        val lang = language.lowercase()
        val path = if (lang != ENGLISH && lang in page.translatedLanguages) {
            page.path.removeSuffix(HTML_SUFFIX) + "-$lang$HTML_SUFFIX"
        } else {
            page.path
        }
        return SITE_BASE + path
    }

    private fun page(pageId: String, slug: String): DocsHelpPage =
        DocsHelpPage(pageId = pageId, path = "documentation/$slug$HTML_SUFFIX")
}
