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
)

object KnownAuthResources {

    val all: List<KnownAuthResource> = listOf(
        KnownAuthResource("Instagram", "instagram.com", "https://www.instagram.com/accounts/login/"),
        KnownAuthResource("Pinterest", "pinterest.com", "https://www.pinterest.com/login/"),
        KnownAuthResource("TikTok", "tiktok.com", "https://www.tiktok.com/login"),
        KnownAuthResource("X (Twitter)", "x.com", "https://x.com/login"),
        KnownAuthResource("DeviantArt", "deviantart.com", "https://www.deviantart.com/users/login"),
        KnownAuthResource("Threads", "threads.net", "https://www.threads.net/login"),
        KnownAuthResource("Reddit", "reddit.com", "https://www.reddit.com/login/"),
        KnownAuthResource("Tumblr", "tumblr.com", "https://www.tumblr.com/login"),
        KnownAuthResource("Flickr", "flickr.com", "https://identity.flickr.com/login"),
        KnownAuthResource("ArtStation", "artstation.com", "https://www.artstation.com/users/sign_in"),
    )

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
