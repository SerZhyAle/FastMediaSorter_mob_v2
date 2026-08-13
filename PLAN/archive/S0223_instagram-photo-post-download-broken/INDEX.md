# Tactical Plan: S0223 — Instagram Photo Post Download Fix

**Ticket:** S0223
**Status:** Tactical
**Strategic spec:** `PLAN/S0223_instagram-photo-post-download-broken.md`

## Context

Instagram `/p/` post and carousel URLs return `SocialPreviewOnly` despite valid session cookies.
Root cause: `sniffEmbeddedJson` returns `unique=0` (no `data-sjs` on IG `/p/` pages), and `IMG_TAG`
candidates do not bypass the `isPreviewSensitiveHost` guard. yt-dlp 2026.3.17 does not support
image posts. Fix: call Instagram private API from `HtmlPageExtractionStrategy` when the post URL
matches `/p/`, parse the `image_versions2`/`carousel_media` response via the existing
`StructuredMediaSniffer.collectThreadPost` path, producing `EMBEDDED_JSON` candidates that
bypass the guard and trigger `Batch` for carousels.

## Phases

- [Phase 01](phase_01.md) — ytdlp_utils.py: URL-pattern exclusion for IG `/p/`
- [Phase 02](phase_02.md) — StructuredMediaSniffer: `sniffInstagramApiResponse` public method
- [Phase 03](phase_03.md) — HtmlPageExtractionStrategy: Instagram API harvest in `harvestCandidates`
- [Phase 04](phase_04.md) — Unit tests: `StructuredMediaSnifferTest` additions
- [Phase 05](phase_05.md) — Build verification

## Key Files

- `app_v2/src/noLegal/python/ytdlp_utils.py`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/link/StructuredMediaSniffer.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`
- `app_v2/src/test/java/com/sza/fastmediasorter/data/link/StructuredMediaSnifferTest.kt`

## Architecture Decisions

- API call lives in `HtmlPageExtractionStrategy` (HTTP + orchestration layer) — same pattern as oEmbed in `StructuredMediaSniffer.harvestOEmbed`.
- JSON parsing lives in `StructuredMediaSniffer` via new `sniffInstagramApiResponse(json, baseUri)` — reuses private `collectThreadPost` already handling `image_versions2` + `carousel_media`.
- `x-ig-app-id: 936619743392459` — standard Instagram web app ID, required by the private API even with valid session cookies.
- Shortcode → media_id: base-62 decode with IG alphabet (`A-Z a-z 0-9 - _`).
- Endpoint: `https://i.instagram.com/api/v1/media/{media_id}/info/` — cookies from existing `linkDownload` OkHttp client cookie jar are forwarded to `i.instagram.com` via standard subdomain cookie matching.
- All flavors benefit (html strategy is in `src/main/`); ytdlp exclusion is `noLegal`-only (`ytdlp_utils.py` is in `src/noLegal/python/`).
- After implementation: set status `BlockNeedUserTest`; insert `Timber.d("S0223: ...")` tags at API call entry point.
