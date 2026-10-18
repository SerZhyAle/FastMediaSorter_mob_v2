package com.sza.fastmediasorter.domain.streams.facets

import java.util.Locale

/**
 * Folds a raw catalog topic into the closed rubric set (STREAM-BANK 2.4 topic law): an unknown non-blank
 * topic reads `General`, a blank one stays blank, and every rubric id and alias lands on one canonical id
 * whatever its case, so folding an already folded value changes nothing.
 *
 * The exact table and the ordered rules are a port of `Get-CanonicalTopic` in
 * `scripts/streams/modules/StreamPublisher.Common.ps1`. The publisher, the phone importer and the watch
 * importer are held to one vector file (`stream-bank/topic-fold-v1.tsv`), so an older bank and a freshly
 * rewritten one reach every topic filter with the same ids. Edit both sides and the vectors together.
 */
object StreamTopicFolding {

    const val FALLBACK_TOPIC = "General"

    /** The ids the topic filter matches on; each one folds to itself in any case. */
    val rubricIds: List<String> = listOf(
        "General", "News", "Pop", "Religious", "Kids", "Oldies", "Movies & Series", "Country & Folk",
        "Rock", "Sports", "World", "Jazz & Blues", "Chillout", "Electronic", "Hip-hop", "Talk",
        "Local radio", "Comedy", "Classical", "Latin", "Documentary", "Education", "Test", "R&B & Soul",
        "Metal", "Webcam", "Traffic cams", "Reggae", "Shopping", "Business", "Lifestyle", "Adult",
    )

    private val apostrophes = Regex("[‘’`´]")

    // `\p{Z}` joins `\s` so a no-break space collapses as it does under the publisher's .NET `\s`.
    private val whitespaceRuns = Regex("[\\s\\p{Z}]+")

    // Pins values whose plain reading is wrong ("Adult Contemporary" is a pop format, "Chr" is
    // Contemporary Hit Radio, "Blues Rock" belongs with rock), before any rule below can misread them.
    private val exactAliases: Map<String, String> = mapOf(
        "adult contemporary" to "Pop", "soft adult contemporary" to "Pop", "chr" to "Pop", "am pop" to "Pop",
        "top 40" to "Pop", "top hits" to "Pop", "hits" to "Pop", "charts" to "Pop", "city pop" to "Pop",
        "blues rock" to "Rock", "gothic" to "Rock", "darkwave" to "Rock", "dark wave" to "Rock",
        "new wave" to "Rock", "aor" to "Rock", "indy" to "Rock", "anarchy" to "Rock",
        "alternative" to "Rock", "classic alternative" to "Rock", "experimental" to "Rock",
        "christmas music" to "Pop", "holiday music (nov-dec)" to "Pop",
        "anni 80" to "Oldies",
        "hardcore" to "Metal", "deathcore" to "Metal",
        "beats" to "Electronic", "mixes" to "Electronic", "party" to "Electronic", "balearic" to "Electronic",
        "abstract" to "Electronic", "garage" to "Electronic", "ebm" to "Electronic",
        "acoustic" to "Chillout", "chilled trap" to "Chillout",
        "vocal" to "Jazz & Blues", "all-vinyl" to "Jazz & Blues",
        "classics" to "Classical", "blasmusik" to "Classical", "orquestrada" to "Classical",
        "evergreens" to "Oldies", "discography" to "Oldies", "archive" to "Oldies",
        "calypso" to "World", "roma" to "World", "galicia" to "World", "breton" to "World",
        "amchikonkani" to "World", "akan" to "World", "amharic" to "World",
        "الموسيقى العربية" to "World", "arab music" to "World", "arabic music" to "World",
        "military" to "Talk", "conspiracies" to "Talk", "artists" to "Talk", "p4" to "Talk",
        "legislative" to "Talk", "conservative" to "Talk", "public" to "Talk",
        "fantasy" to "Movies & Series", "video games" to "Movies & Series", "disney" to "Kids",
        "life guide" to "Lifestyle", "romance" to "Lifestyle", "auto" to "Lifestyle", "bikers" to "Lifestyle",
        "commercial" to "Business", "explicit" to "Adult",
        "eclectic" to "General", "variety" to "General", "music" to "General", "misc" to "General",
        "others" to "General", "undefined" to "General", "bizzare" to "General", "aris" to "General",
        "ai" to "General", "acir" to "General", "adazoa" to "General", "apache 207" to "Hip-hop",
        "traffic cams" to "Traffic cams", "traffic" to "Traffic cams",
    )

    // A rubric id outranks an alias spelled the same, so an id always round-trips.
    private val exactTable: Map<String, String> =
        exactAliases + rubricIds.associateBy { it.lowercase(Locale.ROOT) }

    // Order is the specificity ranking and the first match wins: 'metal' before 'rock', 'adult
    // contemporary' (exact table) before 'adult'. Each rule lists its top-level alternatives.
    private val orderedRules: List<Pair<Regex, String>> = listOf(
        rule("Test", "test pattern", "^m3u8$", "^https?:", "^#$", """^\d+kbps$"""),
        rule(
            "Religious",
            "quran", "قران", "القرآن", "islam", "اسلامي", "christ", "gospel", "bible", "biblia", "catholic",
            "evangel", "adventist", "baptist", "^ccm$", "religio", "cristian", "alistair begg",
        ),
        rule("Webcam", "webcam", "outdoor", "traffic", "^zoo", "beach", "weather", "nature cam"),
        rule("Metal", "metal", "deathcore"),
        rule("Hip-hop", "rap", "hip.?hop", "^trap$", "^drill$", "g-funk", "deutschrap"),
        rule("Rock", "punk", "rock", "grunge", "deutschrock"),
        rule("Jazz & Blues", "jazz", "blues", "bebop", "bossa"),
        rule(
            "Classical",
            "classic(al)? music", "opera", "choral", "orchestr", "symphon", "medieval", "ancient music",
            "cl[aá]ssic[oa]",
        ),
        rule(
            "Electronic",
            "techno", "house", "trance", "electro", "^edm$", "drum ?(and|&) ?bass", "breakbeat", "broken beat",
            "chiptune", "^dance", "dance classics", "club", "^#?dj$", "mashup",
        ),
        rule("Chillout", "ambient", "chill", "lo-?fi", "lounge", "relax"),
        rule("R&B & Soul", "soul", "funk", "r ?& ?b", "rnb", "boogie", "amapiano", "groovy"),
        rule("Reggae", "reggae", "dancehall", "^ska$"),
        rule(
            "Country & Folk",
            "country", "folk", "bluegrass", "americana", "celtic", "ethnic", "etnic", "türkü", "aboriginal",
            "nordic",
        ),
        rule(
            "Latin",
            "latin", "salsa", "bachata", "cumbia", "reggaeton", "sertanej", "pagode", "^banda$", "brasil",
            "bras[ií]lia", "mexic", "tango", "fiesta", "clásicos", "clasicos", "baladas", "argentin", "bogota",
            "buenos aires", "^chile$", "ciudad de m",
        ),
        rule(
            "World",
            "afro", "afric", "arab", "bolly", "india", "indones", "dangdut", "koplo", "campursari", "yogyakarta",
            "greek", "chines", "korea", "^kpop$", "^enka$", "filipino", "bangla", "balkan", "bosnia", "biesiada",
            "chanson", "musique", "müzik", "world", "international", "^opm$", "^turkey$", "anadolu",
        ),
        rule("Oldies", "oldies", "nostalgi", "golden", "^classic hits", "^classic$", "disco"),
        rule("Oldies", """^#?(19|20)?\d{2}'?(s|er)\b""", """^\d{4}'?s"""),
        rule("Pop", "j-?pop", "^pop", "pop music", "pop$", "charts", "hits", "ballad"),
        rule("News", "news", "noticia", "actualidad", "^infos?$", "informa"),
        rule(
            "Talk",
            "talk", "podcast", "audiobook", "^books?$", "drama", "politic", "debate", "speech", "public radio",
            "culture", "cultural",
        ),
        rule(
            "Sports",
            "sport", "futbol", "futebol", "soccer", "hockey", "baseball", "deporte", "desporto", "^spor$",
            "era spor",
        ),
        rule("Kids", "kids", "child", "crian", "fairytale", "cartoon"),
        rule("Movies & Series", "movie", "series", "cinema", "^film", "anime"),
        rule("Documentary", "document", "science", "space", "history"),
        rule("Education", "educat", "learn", "school", "universit"),
        rule("Comedy", "comedy", "humor"),
        rule("Business", "business", "finance", "econom"),
        rule("Shopping", "shop"),
        rule("Lifestyle", "lifestyle", "cooking", "^food", "health", "fashion"),
        rule("Adult", "adult"),
        rule(
            "Local radio",
            """^\d{2,4}([.,]\d+)?\s*(fm|am|mhz|khz)?$""", """^(fm|am)\b""", """\bfm\b""", """\bam\d""",
            "local", "region", "community radio", "public radio", "iheart", "bauer radio", "sveriges", "duna",
            "full service",
        ),
    )

    /** Canonical rubric id for [raw], or null when the cell states no topic. */
    fun canonicalTopic(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val key = foldKey(raw)
        return exactTable[key]
            ?: orderedRules.firstOrNull { (pattern, _) -> pattern.containsMatchIn(key) }?.second
            ?: FALLBACK_TOPIC
    }

    // Typographic apostrophes fold to the straight one first, so every rule can assume one spelling.
    private fun foldKey(raw: String): String =
        raw.replace(apostrophes, "'").trim().lowercase(Locale.ROOT).replace(whitespaceRuns, " ")

    private fun rule(rubric: String, vararg alternatives: String): Pair<Regex, String> =
        Regex(alternatives.joinToString("|")) to rubric
}
