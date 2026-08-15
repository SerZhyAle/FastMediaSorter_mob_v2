# Phase 02 — DeviantArt Extractor

**Strategic spec:** [`../S0177_nolegal-native-site-extractors.md`](../S0177_nolegal-native-site-extractors.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04
**Steps done:** 3 / 3
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Implement `DeviantArtExtractionStrategy` — parse `window.__INITIAL_STATE__` from the deviation page HTML to extract the original file URL, with oEmbed as a parse-failure fallback.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/DeviantArtExtractionStrategy.kt` | New | ≤ 200 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt` | Modified | ≤ 50 |

---

## Steps

### Step 02.1 — Implement DeviantArtExtractionStrategy

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/DeviantArtExtractionStrategy.kt`
**Depends on:** — start of phase (Phase 01 done)

**Prompt for developer:**

> Create `@Singleton class DeviantArtExtractionStrategy @Inject constructor` in package `com.sza.fastmediasorter.data.link.nolegal`, implementing `UrlExtractionStrategy`.
>
> `id = "deviantart"`
>
> **`probe(url)`:** return `Applicable(null, null)` iff host ends with `deviantart.com`; else `NotApplicable`.
>
> **`open(url, onProgress)`:** run on `Dispatchers.IO`.
>
> **Primary path — `__INITIAL_STATE__` extraction:**
> 1. `GET {url}` using `@Named("linkDownload") OkHttpClient` with `User-Agent: Mozilla/5.0 (compatible; FastMediaSorter)`.
>    Cookies are injected automatically by `LinkDownloadCookieJar` — no manual cookie handling needed.
> 2. Search response body for a `<script>` tag whose content contains `window.__INITIAL_STATE__ =`. Extract the JSON object immediately after the `=` sign (everything until the closing `</script>`).
>    Use a `Regex` like `window\\.__INITIAL_STATE__\\s*=\\s*(\\{.+?\\})\\s*;?\\s*</script>` with `DOT_MATCHES_ALL` option.
> 3. Parse the JSON. Navigate: `deviation` → `media` → `baseUri` (String) and `prettyName` (String).
>    Build the original download URL as `"{baseUri}/{prettyName}"` (no trailing slash on baseUri, check with trimEnd('/'), or if baseUri already ends without extension and prettyName is "prettyName", the actual file path may be `baseUri + "/" + prettyName`). Verify the assembled URL starts with `https://`.
>    Alternatively, look for `deviation.media.types[]` where `t == "full"` → `c` field for the CDN URL pattern. The exact structure varies; prefer `baseUri + prettyName` first.
> 4. On success → `direct.open(originalUrl, onProgress, extraHeaders = mapOf("Referer" to "https://www.deviantart.com/"))`.
>
> **Fallback path — oEmbed (preview only, used when `__INITIAL_STATE__` parse fails):**
> 1. `GET https://backend.deviantart.com/oembed?url={encoded_url}&format=json`.
> 2. Parse: `url` field → preview image URL.
> 3. `direct.open(previewUrl, onProgress, extraHeaders = mapOf("Referer" to "https://www.deviantart.com/"))`.
> 4. Log: `Timber.d("DeviantArtExtractor: fallback oEmbed for %s", url)`.
>
> **Error handling:**
> - Parse failure of both paths → `OpenResult.NotFound("deviantart_parse_failed")`.
> - HTTP non-2xx on page fetch → `OpenResult.NotFound("deviantart_fetch_error_${code}")`.
> - `IOException` → `OpenResult.Error(e)`.
>
> Constructor parameters: `@Named("linkDownload") private val httpClient: OkHttpClient`, `private val direct: DirectFileExtractionStrategy`.
> No `Log.d()`.

**Verification:**

- `Glob` — `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/DeviantArtExtractionStrategy.kt` exists.
- `Grep` — `class DeviantArtExtractionStrategy` matches exactly once.
- `Grep` — `override val id: String = "deviantart"` present.
- `Grep` — `__INITIAL_STATE__` appears in this file (Regex pattern string).
- `Grep` — `oembed` appears in this file (fallback URL).
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-12 — Verification 6/6 PASS. Files: DeviantArtExtractionStrategy.kt (new, 168 LOC). Dev log recorded.

---

### Step 02.2 — Wire DeviantArt strategy into NoLegalLinkDownloadModule

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add to `NoLegalLinkDownloadModule`:
> ```kotlin
> @Binds
> @IntoSet
> abstract fun bindDeviantArt(impl: DeviantArtExtractionStrategy): UrlExtractionStrategy
> ```
> Import `com.sza.fastmediasorter.data.link.nolegal.DeviantArtExtractionStrategy`.

**Verification:**

- `Grep` — `bindDeviantArt` present in `NoLegalLinkDownloadModule.kt`.
- `Grep` — `DeviantArtExtractionStrategy` present in the file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-12 — Verification 2/2 PASS. Files: NoLegalLinkDownloadModule.kt (+5 LOC). Dev log recorded.

---

### Step 02.3 — Dev log

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 02.2

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/DeviantArtExtractionStrategy.kt" "S0177" "New native extractor for deviantart.com with __INITIAL_STATE__ + oEmbed fallback"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt" "S0177" "Bind DeviantArtExtractionStrategy"
> ```

**Verification:**

- `Grep` — `DeviantArtExtractionStrategy` appears in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-12 — Verification 1/1 PASS. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for noLegal flavor.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added (Step 02.3).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- DeviantArt `__INITIAL_STATE__` structure is implementation-time discovery — document the actual field path found in a code comment if it differs from the plan.
- Authentication flows through `LinkDownloadCookieJar` automatically. No additional session management needed.

---

## Rollback Plan

Revert phase commit(s) — no data migration, no user-facing surface changed.
