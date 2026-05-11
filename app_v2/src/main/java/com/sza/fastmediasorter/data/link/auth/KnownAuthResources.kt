package com.sza.fastmediasorter.data.link.auth

/**
 * S0116 / S0144: built-in catalog of popular social-media resources whose content
 * downloads typically need a signed-in session.
 *
 * Consumed by the "Add authorization" picker and by the shared-link auth-offer flow.
 * Brand names are proper nouns — kept as literals (identical across locales).
 * Extend the catalog by adding a [KnownAuthResource] entry to [all]; no other change
 * is required to surface the resource in the picker and the share offer.
 */
data class KnownAuthResource(
    val displayName: String,
    val host: String,
    val loginUrl: String,
    /**
     * S0151 §6.3: when true, an extraction result consisting solely of OG/image
     * previews for this host is treated as "no real content" → the coordinator
     * surfaces [com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator.Result.Failed.SocialPreviewOnly].
     * Keep false for image-first sites where the preview image is the desired result.
     */
    val previewOnlyMeansLogin: Boolean = false,
)

object KnownAuthResources {

    val all: List<KnownAuthResource> = listOf(
        KnownAuthResource("Instagram", "instagram.com", "https://www.instagram.com/accounts/login/", previewOnlyMeansLogin = true),
        KnownAuthResource("Pinterest", "pinterest.com", "https://www.pinterest.com/login/"),
        KnownAuthResource("TikTok", "tiktok.com", "https://www.tiktok.com/login", previewOnlyMeansLogin = true),
        KnownAuthResource("X (Twitter)", "x.com", "https://x.com/login", previewOnlyMeansLogin = true),
        KnownAuthResource("DeviantArt", "deviantart.com", "https://www.deviantart.com/users/login"),
        KnownAuthResource("Threads", "threads.net", "https://www.threads.net/login", previewOnlyMeansLogin = true),
        KnownAuthResource("Threads", "threads.com", "https://www.threads.com/login", previewOnlyMeansLogin = true),
        KnownAuthResource("Reddit", "reddit.com", "https://www.reddit.com/login/"),
        KnownAuthResource("Tumblr", "tumblr.com", "https://www.tumblr.com/login"),
        KnownAuthResource("Flickr", "flickr.com", "https://identity.flickr.com/login"),
        KnownAuthResource("ArtStation", "artstation.com", "https://www.artstation.com/users/sign_in"),
    )

    /** S0151 §6.3: true iff [host] is a known auth resource flagged [KnownAuthResource.previewOnlyMeansLogin]. */
    fun isPreviewSensitiveHost(host: String?): Boolean = matchHost(host)?.previewOnlyMeansLogin == true

    /**
     * Resolve a request host to a known resource. Case-insensitive; a leading `www.`
     * is stripped; sub-hosts match their registrable parent (`m.instagram.com` → Instagram).
     */
    fun matchHost(host: String?): KnownAuthResource? {
        val normalized = host?.trim()?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
            ?: return null
        return all.firstOrNull { entry ->
            normalized == entry.host || normalized.endsWith(".${entry.host}")
        }
    }
}
