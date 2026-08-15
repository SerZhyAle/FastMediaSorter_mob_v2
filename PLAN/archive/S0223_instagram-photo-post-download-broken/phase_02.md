# Phase 02 — StructuredMediaSniffer: `sniffInstagramApiResponse`

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/StructuredMediaSniffer.kt`

## Goal

Add a public method that parses the Instagram private API response
(`{"items": [...]}`) and returns `EMBEDDED_JSON` candidates using the
existing `collectThreadPost` private method.

## Steps

- [x] 02.1 — Add `fun sniffInstagramApiResponse(json: String, baseUri: String): List<HtmlMediaCandidate>`:
  - Parse `json` as `JSONObject`.
  - Iterate `root.optJSONArray("items")`.
  - For each item, call `collectThreadPost(item, baseUri, out)`.
  - Deduplicate by `extractMetaAssetKey`.
  - Wrap in `try/catch`; on any `Throwable` log via `LinkDownloadTrace.verbose` and return `emptyList()`.

- [x] 02.2 — Add `Timber.d("S0223: ig-api sniff items=%d unique=%d", ...)` log inside the method for debug observability (temporary; removed on `Verified`).

## Verification

- `sniffInstagramApiResponse` is `public fun` (no `private` / `internal` modifier).
- `collectThreadPost` reused — no duplicated JSON traversal logic.
- Method handles empty `items` array → returns `emptyList()`.
- Method handles malformed JSON → returns `emptyList()` (no crash).
