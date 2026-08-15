# Phase 04 — Unit Tests: StructuredMediaSniffer additions

**File:** `app_v2/src/test/java/com/sza/fastmediasorter/data/link/StructuredMediaSnifferTest.kt`

## Goal

Test `sniffInstagramApiResponse` for the common cases: single image post, carousel,
empty items, and malformed JSON.

## Steps

- [x] 04.1 — Add `sniffInstagramApiResponse_singleImage`: JSON with one item, one `image_versions2.candidates` entry → 1 candidate with `source = EMBEDDED_JSON`.

- [x] 04.2 — Add `sniffInstagramApiResponse_carousel`: JSON with one item containing `carousel_media` with 3 slides → 3 candidates.

- [x] 04.3 — Add `sniffInstagramApiResponse_emptyItems`: `{"items":[]}` → empty list, no crash.

- [x] 04.4 — Add `sniffInstagramApiResponse_malformedJson`: non-JSON string → empty list, no crash.

- [x] 04.5 — Add `sniffInstagramApiResponse_dedup`: two carousel slides with identical CDN-path last segment (different signing params) → deduplicated to 1.

## Verification

- All 5 tests pass via `./gradlew :app_v2:testStandardDebugUnitTest --tests "*.StructuredMediaSnifferTest"`.
- No pre-existing failures in `StructuredMediaSnifferTest` broken by changes.
