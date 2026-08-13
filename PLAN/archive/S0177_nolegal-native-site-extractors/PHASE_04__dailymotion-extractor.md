# Phase 04 — Dailymotion Extractor

**Strategic spec:** [`../S0177_nolegal-native-site-extractors.md`](../S0177_nolegal-native-site-extractors.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Implement `DailymotionExtractionStrategy` — parse the embed page to extract a time-limited HLS stream URL and return it immediately for playback.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/DailymotionExtractionStrategy.kt` | New | ≤ 180 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt` | Modified | ≤ 70 |

---

## Steps

### Step 04.1 — Implement DailymotionExtractionStrategy

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/DailymotionExtractionStrategy.kt`
**Depends on:** — start of phase (Phase 03 done)

**Prompt for developer:**

> Create `@Singleton class DailymotionExtractionStrategy @Inject constructor` in package `com.sza.fastmediasorter.data.link.nolegal`, implementing `UrlExtractionStrategy`.
>
> `id = "dailymotion"`
>
> **`probe(url)`:** return `Applicable(null, null)` iff host ends with `dailymotion.com`; else `NotApplicable`.
>
> **`open(url, onProgress)`:** run on `Dispatchers.IO`.
>
> 1. Extract video ID: use Regex `dailymotion\.com/(?:video/|embed/video/)?([a-zA-Z0-9]+)` → group 1.
>    If no ID → `OpenResult.NotFound("dailymotion_no_id")`.
>
> 2. Fetch embed page: `GET https://www.dailymotion.com/embed/video/{id}` using `@Named("linkDownload") OkHttpClient` with:
>    - `User-Agent: Mozilla/5.0 (compatible; FastMediaSorter)`
>    - `Referer: https://www.dailymotion.com/`
>    On non-2xx or IOException → `OpenResult.NotFound("dailymotion_embed_failed")`.
>
> 3. Parse player config from embed HTML. Search for a `<script>` block containing either:
>    - `window.__PLAYER_CONFIG__ =` — extract JSON after `=`.
>    - Or a JSON blob containing `"metadata"` and `"qualities"` keys.
>    Use Regex `__PLAYER_CONFIG__\s*=\s*(\{.+?\})\s*;` with `DOT_MATCHES_ALL`.
>
> 4. Extract stream URL from parsed JSON:
>    - Try path `metadata.qualities.auto[0].url` (HLS manifest).
>    - Alternatively `metadata.qualities` → first key's first item's `url`.
>    - Any URL ending in `.m3u8` qualifies as HLS.
>
> 5. Dailymotion HLS URLs are signed and time-limited — do **not** cache. Return immediately:
>    `OpenResult.Streaming(manifest = StreamingManifest.Hls(manifestUrl = hlsUrl), tentativeFileName = "dailymotion_${id}.mp4")`.
>
> 6. If no HLS URL found → `OpenResult.NotFound("dailymotion_parse_failed")`.
>
> Log: `Timber.w("DailymotionExtractor: parse failed for %s", url)` on parse failures.
> Constructor parameters: `@Named("linkDownload") private val httpClient: OkHttpClient`. No `DirectFileExtractionStrategy` needed — result is always streaming.
> No `Log.d()`.

**Verification:**

- `Glob` — `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/DailymotionExtractionStrategy.kt` exists.
- `Grep` — `class DailymotionExtractionStrategy` matches exactly once.
- `Grep` — `override val id: String = "dailymotion"` present.
- `Grep` — `dailymotion.com/embed/video` appears in this file.
- `Grep` — `StreamingManifest.Hls` appears in this file.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — [FIXED by spec-fix] All 6 verification predicates PASS. Implementation existed; metadata was stale.

---

### Step 04.2 — Wire Dailymotion strategy into NoLegalLinkDownloadModule

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add to `NoLegalLinkDownloadModule`:
> ```kotlin
> @Binds
> @IntoSet
> abstract fun bindDailymotion(impl: DailymotionExtractionStrategy): UrlExtractionStrategy
> ```
> Import `com.sza.fastmediasorter.data.link.nolegal.DailymotionExtractionStrategy`.

**Verification:**

- `Grep` — `bindDailymotion` present in `NoLegalLinkDownloadModule.kt`.
- `Grep` — `DailymotionExtractionStrategy` present in the file.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — [FIXED by spec-fix] bindDailymotion + DailymotionExtractionStrategy present in NoLegalLinkDownloadModule. Metadata was stale.

---

### Step 04.3 — Dev log

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 04.2

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/DailymotionExtractionStrategy.kt" "S0177" "New native extractor for dailymotion.com — embed page HLS stream"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt" "S0177" "Bind DailymotionExtractionStrategy"
> ```

**Verification:**

- `Grep` — `DailymotionExtractionStrategy` appears in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — [FIXED by spec-fix] DailymotionExtractionStrategy appears in CHANGELOG (4 hits). Metadata was stale.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for noLegal flavor.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entries added (Step 04.3).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- All four extractor classes and DI bindings are complete.
- Final `NoLegalLinkDownloadModule.kt` has 4 native + 2 existing (site, ytdlp) = 6 `@Binds @IntoSet` bindings — still one DI module, no split needed.
- HLS playback for Dailymotion uses the existing `StreamingPipeline` path — no new infrastructure.

---

## Rollback Plan

Revert phase commit(s) — no data migration, no user-facing surface changed.
