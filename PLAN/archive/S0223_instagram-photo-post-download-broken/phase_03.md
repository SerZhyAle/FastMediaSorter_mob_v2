# Phase 03 — HtmlPageExtractionStrategy: Instagram API harvest

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`

## Goal

When `harvestCandidates` yields zero `EMBEDDED_JSON` results for an `instagram.com/p/` URL,
call the Instagram private API to retrieve the post's image/carousel data and return
`EMBEDDED_JSON` candidates that bypass the `SocialPreviewOnly` guard.

## Steps

- [x] 03.1 — Add private helpers to `companion object`:
  - `IG_APP_ID = "936619743392459"` — Instagram web app ID, required by private API.
  - `IG_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"` — base-62 alphabet.
  - `IG_API_BASE = "https://i.instagram.com/api/v1/media"` — private API endpoint prefix.

- [x] 03.2 — Add `private fun isInstagramPhotoPost(url: String): Boolean`:
  - Returns `true` iff host (after `www.` strip) is `instagram.com` and `encodedPath.startsWith("/p/")`.

- [x] 03.3 — Add `private fun extractInstagramShortcode(url: String): String?`:
  - Regex `Regex("/p/([A-Za-z0-9_-]+)")` on `encodedPath`.
  - Returns the capture group, or `null`.

- [x] 03.4 — Add `private fun shortcodeToMediaId(shortcode: String): Long`:
  - Base-62 decode using `IG_ALPHABET`.
  - Each unknown character contributes 0 (no crash on malformed input).

- [x] 03.5 — Add `private suspend fun fetchInstagramApiCandidates(pageUrl: String): List<HtmlMediaCandidate>`:
  - Calls `extractInstagramShortcode(pageUrl)` → returns `emptyList()` if null.
  - Calls `shortcodeToMediaId(shortcode)`.
  - Builds `Request` to `$IG_API_BASE/{mediaId}/info/` with header `x-ig-app-id: $IG_APP_ID`.
  - `httpClient.newCall(request).execute()` inside `withContext(Dispatchers.IO)`.
  - Non-2xx: logs `LinkDownloadTrace.verbose("S0223: ig-api status=${response.code}")` → returns `emptyList()`.
  - 2xx: passes `response.body?.string()` to `structuredMediaSniffer.sniffInstagramApiResponse(json, pageUrl)`.
  - `IOException` caught: `Timber.w(io, "S0223: ig-api fetch failed for %s", pageUrl)` → returns `emptyList()`.
  - Insert `Timber.d("S0223: ig-api fetching shortcode=%s mediaId=%d", shortcode, mediaId)` at entry (debug tag; removed on `Verified`).

- [x] 03.6 — Modify `harvestCandidates(html, baseUri)`:
  - After computing `embedded` (from `sniffEmbeddedJson`), add:
    ```kotlin
    // S0223: Instagram /p/ posts contain no data-sjs; fetch via private API.
    val igApiCandidates = if (embedded.isEmpty() && isInstagramPhotoPost(baseUri)) {
        fetchInstagramApiCandidates(baseUri)
    } else {
        emptyList()
    }
    ```
  - Prepend `igApiCandidates` ahead of `embedded` in the merged list:
    `val merged = (igApiCandidates + embedded + structured + staticCandidates).distinctBy { it.url }`
  - Update the `embeddedCount` log to include `igApiCandidates.size`:
    `val embeddedCount = merged.count { it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON }`
    (no change needed — already counts all EMBEDDED_JSON in merged)

## Verification

- `isInstagramPhotoPost("https://www.instagram.com/p/ABC/")` → `true`.
- `isInstagramPhotoPost("https://www.instagram.com/reel/ABC/")` → `false`.
- `extractInstagramShortcode("https://www.instagram.com/p/DYWvX_3jMsh/?img_index=4")` → `"DYWvX_3jMsh"`.
- `shortcodeToMediaId("B")` → `1` (second character in IG_ALPHABET = index 1).
- `fetchInstagramApiCandidates` does not crash on non-2xx API response.
- Merged list order: `igApiCandidates` first → they win `CandidateSelectionPolicy.choose`.
- File stays under 450 LOC (currently 383; adding ~60 lines → ~443 LOC).
