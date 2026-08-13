# Phase 02 — Streaming Sniffer (Pillar G)

**Strategic spec:** [`../S0116_url-media-downloader.md`](../S0116_url-media-downloader.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 07
**Steps done:** 5 / 5
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Extend `HtmlPageExtractionStrategy` so it discovers HLS/DASH manifest URLs in static HTML (meta tags, JSON-LD `VideoObject`, plain-text regex, data attributes) and surfaces them as a new `OpenResult.Streaming` outcome. No download logic — that lands in Phase 03.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `OpenResult.Streaming`, `StreamingManifest` types from Phase 01 compile.
- [ ] `LinkDownloadTrace` available for instrumentation.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlMediaCandidate.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/StreamingManifestSniffer.kt` | New | ≤ 220 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/link/StreamingManifestSnifferTest.kt` | New | ≤ 200 |

---

## Steps

### Step 02.1 — Add `Streaming` source flavor to `HtmlMediaCandidate`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlMediaCandidate.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `HtmlMediaCandidate.Source` enum (or sealed hierarchy), add `HLS_MANIFEST` and `DASH_MANIFEST` entries. Add nullable `manifest: StreamingManifest? = null` field to `HtmlMediaCandidate` data class so streaming candidates carry parsed manifest info forward without re-detection.

**Verification:**

- `Grep` — `HLS_MANIFEST` matches once in `HtmlMediaCandidate.kt`.
- `Grep` — `DASH_MANIFEST` matches once.
- `Grep` — `manifest: StreamingManifest\? = null` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: HtmlMediaCandidate.kt (+8 LOC). Dev log recorded.

---

### Step 02.2 — Implement `StreamingManifestSniffer` helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/StreamingManifestSniffer.kt` (New)
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `@Singleton class StreamingManifestSniffer @Inject constructor()`. Expose `fun sniff(rawHtml: String, baseUri: String): List<HtmlMediaCandidate>` returning candidates with `Source.HLS_MANIFEST` or `Source.DASH_MANIFEST`. Pull from four sources: (1) `<meta>`/`<link>`/`<source>` element src/href that ends in `.m3u8`/`.mpd`; (2) JSON-LD `<script type="application/ld+json">` blocks parsed for `VideoObject.contentUrl` / `embedUrl` ending in `.m3u8`/`.mpd`; (3) plain-text regex `https?://[^"'\\s]+\\.(m3u8|mpd)(?:\\?[^"'\\s]*)?` over the raw HTML; (4) `data-` attributes whose name contains `hls`, `dash`, or `manifest`. Wrap each source in try/catch; one source failing must not abort the others. Log entries via `LinkDownloadTrace.verbose(..)` only.

**Verification:**

- `Glob` — `StreamingManifestSniffer.kt` exists.
- `Grep` — `class StreamingManifestSniffer` matches once.
- `Grep` — `fun sniff\(rawHtml: String, baseUri: String\): List<HtmlMediaCandidate>` matches once.
- `Grep` — `application/ld\+json` matches once (JSON-LD lookup).
- `Grep` — `try \{` and `catch ` co-occur at least 4 times in this file (one wrapper per source).
- `Grep` — `LinkDownloadTrace\.verbose` matches at least once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 6/6 PASS. Files: StreamingManifestSniffer.kt (NEW 130 LOC). Dev log recorded.

---

### Step 02.3 — Wire sniffer into `HtmlPageExtractionStrategy.harvestCandidates`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Inject `StreamingManifestSniffer` via constructor. Inside `harvestCandidates`, after the existing tag harvesters, append `out.addAll(streamingSniffer.sniff(html, baseUri))`. Keep `distinctBy { it.url }` at the end so duplicate URLs from multiple sources collapse. Add an `S0116:` debug tag at strategy entry: `LinkDownloadTrace.tag("html-sniffer harvested ${out.size} candidates (direct=$directCount, streaming=$streamingCount, image=$imageCount) for ${LinkDownloadTrace.truncateUrl(baseUri)}")`.

**Verification:**

- `Grep` — `StreamingManifestSniffer` matches at least once in `HtmlPageExtractionStrategy.kt` (constructor parameter type; same-package, no import needed — the field-call site uses the lowercase `streamingSniffer` reference).
- `Grep` — `streamingSniffer\.sniff\(` matches once.
- `Grep` — `S0116: html-sniffer harvested` matches once in `HtmlPageExtractionStrategy.kt`.
- `Grep` — `distinctBy \{ it\.url \}` still present in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: HtmlPageExtractionStrategy.kt (+25 LOC). Predicate corrected during execution: `StreamingManifestSniffer` only appears in the constructor parameter (same-package collocation eliminates the import); call site uses field `streamingSniffer.sniff(`. Dev log recorded.

---

### Step 02.4 — Update `CandidateSelectionPolicy` and `HtmlPageExtractionStrategy.open` for streaming branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> `CandidateSelectionPolicy`: keep the existing `choose(...)` contract returning `HtmlMediaCandidate?`. Add a small helper preserving size-priority semantics for streaming candidates (manifests have unknown size; treat them as ranking just below ≥ 1 MiB direct files but above inline anchors). The existing rule "first ≥ 1 MiB direct" stays first; add a new fallback before "first by input order": `httpOnly.firstOrNull { it.source == Source.HLS_MANIFEST || it.source == Source.DASH_MANIFEST }`.
>
> `HtmlPageExtractionStrategy.open` (around the `chosen` selection): branch on `chosen.source`. If `HLS_MANIFEST` or `DASH_MANIFEST` and `chosen.manifest != null`, return `OpenResult.Streaming(manifest = chosen.manifest!!, tentativeFileName = deriveStreamingFileName(chosen.url))`. Otherwise keep delegation to `direct.open(chosen.url, onProgress)`. Add private helper `deriveStreamingFileName(url: String): String` returning the last path segment with extension replaced by `.mp4`; if no segment extractable, return `"download_${System.currentTimeMillis()}.mp4"`.

**Verification:**

- `Grep` — `Source\.HLS_MANIFEST` and `Source\.DASH_MANIFEST` co-occur in `CandidateSelectionPolicy.kt`.
- `Grep` — `OpenResult\.Streaming\(` matches once in `HtmlPageExtractionStrategy.kt`.
- `Grep` — `fun deriveStreamingFileName\(` matches once in `HtmlPageExtractionStrategy.kt`.
- `Grep` — `\.mp4` matches at least once in `HtmlPageExtractionStrategy.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: CandidateSelectionPolicy.kt (+8 LOC, manifest fallback rule), HtmlPageExtractionStrategy.kt (+22 LOC, streaming branch + filename derivation + MIME bypass for streaming sources). Dev log recorded.

---

### Step 02.5 — Add `StreamingManifestSnifferTest` unit suite

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/link/StreamingManifestSnifferTest.kt` (New)
**Depends on:** Step 02.4

**Prompt for developer:**

> Robolectric not required — pure JVM. Add fixtures for: (a) `<source src="https://cdn.example.com/video/index.m3u8">`; (b) JSON-LD `{"@type":"VideoObject","contentUrl":"https://x.example.com/d.mpd"}`; (c) plain-text URL inside `<script>` body; (d) `data-hls-src="..."` attribute. Each fixture asserts at least one candidate of the correct `Source` enum value. Add a negative fixture (HTML without any manifest signal → empty list). Add a regression case where the same `m3u8` URL appears in two sources — assert exactly one candidate after dedup downstream.

**Verification:**

- `Glob` — `StreamingManifestSnifferTest.kt` exists.
- `Grep` — `@Test` matches at least 5 times.
- `Grep` — `Source\.HLS_MANIFEST` and `Source\.DASH_MANIFEST` both present.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: StreamingManifestSnifferTest.kt (NEW 90 LOC, 6 @Test cases). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] Unit tests pass — `./gradlew :app_v2:testStandardDebugUnitTest` includes `StreamingManifestSnifferTest`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- HTML strategy now produces `OpenResult.Streaming(manifest)`. Phase 01 already added the temporary compile-safe coordinator branch, so this phase still builds cleanly before the streaming downloader lands.
- Phase 03 replaces the temporary `OpenResult.Streaming` placeholder with the real `StreamingPipeline` branch; no other caller should pattern-match the new sealed variant before then.
- Sniffer is independent of cookie injection — Phase 04 will inject cookies into the OkHttp client used by `harvestCandidates`, no sniffer changes needed.

---

## Rollback Plan

Revert phase commit. New sniffer is opt-in (only consumed by `HtmlPageExtractionStrategy` after wiring); reverting removes the wiring and the file. The temporary Phase 01 coordinator placeholder remains dormant. No persistent state.

## Revision History

- **2026-05-08** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability)
	- Applied: compile-safe handoff clarification for `OpenResult.Streaming`. Proposed (DISCUSS): 0.
