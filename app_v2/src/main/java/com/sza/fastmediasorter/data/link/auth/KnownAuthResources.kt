package com.sza.fastmediasorter.data.link.auth

data class KnownAuthResource(
    val displayName: String,
    val host: String,
    val loginUrl: String,
    val previewOnlyMeansLogin: Boolean = false,
    val supportsEmbeddedJson: Boolean = false,
)

object KnownAuthResources {

    val all: List<KnownAuthResource> = listOf(
        KnownAuthResource(
            displayName = "Instagram",
            host = "instagram.com",
            loginUrl = "https://www.instagram.com/accounts/login/",
            previewOnlyMeansLogin = true,
            // S0197: public Instagram post pages embed the authoritative media URL in
            // `<script type="application/json" data-sjs>` payloads - the same `image_versions2`
            // schema used by Threads. Enables the embedded-JSON harvester on this host.
            supportsEmbeddedJson = true,
        ),
        KnownAuthResource(
            displayName = "Threads",
            host = "threads.net",
            loginUrl = "https://www.threads.net/login",
            // Threads CDN (instagram.fmla*.fna.fbcdn.net/v/t51.82787-15/) delivers full-res
            // images even without login - previewOnlyMeansLogin caused SocialPreviewOnly on
            // every photo/carousel post (confirmed build .119, S0171 diag log).
            supportsEmbeddedJson = true,
        ),
        KnownAuthResource(
            displayName = "Threads",
            host = "threads.com",
            loginUrl = "https://www.threads.com/login",
            // Same as threads.net - CDN is publicly accessible, login not required for media.
            supportsEmbeddedJson = true,
        ),
        KnownAuthResource("Pinterest", "pinterest.com", "https://www.pinterest.com/login/"),
        KnownAuthResource("TikTok", "tiktok.com", "https://www.tiktok.com/login", previewOnlyMeansLogin = true),
        KnownAuthResource("X (Twitter)", "x.com", "https://x.com/login", previewOnlyMeansLogin = true),
        KnownAuthResource("DeviantArt", "deviantart.com", "https://www.deviantart.com/users/login"),
        KnownAuthResource("Reddit", "reddit.com", "https://www.reddit.com/login/"),
        KnownAuthResource("Tumblr", "tumblr.com", "https://www.tumblr.com/login"),
        KnownAuthResource("Flickr", "flickr.com", "https://identity.flickr.com/login"),
        KnownAuthResource("ArtStation", "artstation.com", "https://www.artstation.com/users/sign_in"),
        KnownAuthResource(
            displayName = "Facebook",
            host = "facebook.com",
            loginUrl = "https://www.facebook.com/login",
            // Facebook requires cookies for most public videos since 2023 -
            // treat SocialPreviewOnly as an auth signal to prompt WebView login.
            previewOnlyMeansLogin = true,
        ),
        // S0281: the historical YouTube entry (added by S0187) was removed because
        // S0200 ADR-4 mandates Chrome Custom Tabs for all Google OAuth domains and
        // CCT has no cookie-return channel. The previous entry made the auth-offer
        // dialog architecturally unreachable - the dialog appeared but no cookies
        // could ever be saved. Google-domain handling is now centralized in
        // `GoogleDomainMatcher` (see ReceiveShareActivity guards inserted by S0281).
    )

    fun isPreviewSensitiveHost(host: String?): Boolean = matchHost(host)?.previewOnlyMeansLogin == true

    fun supportsEmbeddedJson(host: String?): Boolean = matchHost(host)?.supportsEmbeddedJson == true

    fun matchHost(host: String?): KnownAuthResource? {
        val normalized = host?.trim()?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
            ?: return null
        return all.firstOrNull { entry ->
            normalized == entry.host || normalized.endsWith(".${entry.host}")
        }
    }
}