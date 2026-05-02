# Phase 04 — HTML Page Extractor Strategy

**Strategic spec:** [`../S0003_link-receive-download.md`](../S0003_link-receive-download.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Add the second extraction strategy: parse an HTML page via jsoup, harvest embedded media candidates (Open Graph / Twitter meta tags, `<video>`/`<audio>`/`<source>`/`<img>` with `src`/`srcset`), exclude `data:`/`blob:`, run capped HEAD probes to learn sizes, select the best candidate per strategic §5.1.D, and reuse the direct strategy to download the chosen URL. Concrete numeric defaults: HEAD fan-out ≤ 8 candidates per page; total candidate-selection budget ≤ 4 s.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlMediaCandidate.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt` | Modified | ≤ 160 |

---

## Steps

### Step 04.1 — Define `HtmlMediaCandidate` and selection policy

**Files:**
`app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlMediaCandidate.kt`,
`app_v2/src/main/java/com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> `HtmlMediaCandidate.kt`: data class with `url: String`, `source: Source`, `tentativeMime: String?`, `tentativeSizeBytes: Long?`. `Source` is an enum: `OG_VIDEO, OG_IMAGE, TWITTER_PLAYER_STREAM, VIDEO_TAG, AUDIO_TAG, SOURCE_TAG, IMG_TAG, IMG_SRCSET, INLINE_LINK`. Order in the enum encodes the tie-breaker for "first by appearance".
>
> `CandidateSelectionPolicy.kt`: object with `fun choose(candidates: List<HtmlMediaCandidate>): HtmlMediaCandidate?`. Rules per strategic §5.1.D:
>
> 1. Filter out candidates whose URL scheme is not `http(s)` (drops `data:`/`blob:`/`javascript:`).
> 2. If any candidate has `tentativeSizeBytes >= 1_048_576` (1 MiB), return the **first such** candidate (preserve input order).
> 3. Otherwise if any candidate has a non-null `tentativeSizeBytes`, return the one with the maximum size; ties broken by source enum ordinal then input order.
> 4. Otherwise return the first candidate by input order.
> 5. Return `null` for an empty filtered list.

**Verification:**

- `Glob` — both files exist.
- `Grep -n "data class HtmlMediaCandidate"` matches exactly once.
- `Grep -n "object CandidateSelectionPolicy"` matches exactly once.
- `Grep -n "1_048_576"` in `CandidateSelectionPolicy.kt` matches at least once.
- `Grep -n "data:|blob:"` in `CandidateSelectionPolicy.kt` returns zero hits in production paths (only in comments — verify by inspection if regex ambiguous).

**Status:** `[ ]` not done

---

### Step 04.2 — Implement `HtmlPageExtractionStrategy.probe`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`
**Depends on:** Steps 04.1, plus Phase 03 contracts.

**Prompt for developer:**

> `@Inject constructor(@Named("linkDownload") httpClient: OkHttpClient, private val direct: DirectFileExtractionStrategy)`. `id = "html"`.
>
> `probe(url)`:
>
> 1. Issue `HEAD`. If `Content-Type` starts with `text/html` (case-insensitive, ignore parameters) → return `Applicable(tentativeMime = null, tentativeSizeBytes = null)`.
> 2. If non-HTML → `NotApplicable` (the direct strategy will handle it).
> 3. On `IOException` → `TransientError(cause)`.

**Verification:**

- `Glob` — file `HtmlPageExtractionStrategy.kt` exists.
- `Grep -n "class HtmlPageExtractionStrategy"` matches exactly once.
- `Grep -n "override val id: String = \"html\""` matches exactly once.
- `Grep -n "text/html"` in the file matches at least once.

**Status:** `[ ]` not done

---

### Step 04.3 — Implement `HtmlPageExtractionStrategy.open`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> `open(url, onProgress)`:
>
> 1. `GET` the page (max body 2 MiB; truncate via `peekBody(2L * 1024 * 1024)` and parse the truncated string with jsoup `Jsoup.parse(html, baseUri = url)` to keep relative-URL resolution working).
> 2. Harvest candidates by source order:
>    - `meta[property=og:video]`, `meta[property=og:video:url]`, `meta[property=og:video:secure_url]`
>    - `meta[property=og:image]`, `meta[property=og:image:url]`, `meta[property=og:image:secure_url]`
>    - `meta[name=twitter:player:stream]`
>    - `video[src]`, `video > source[src]`
>    - `audio[src]`, `audio > source[src]`
>    - `img[src]`, `img[srcset]` (split on commas, take URL token)
>    - Standalone anchor `a[href]` whose href ends in a whitelisted extension (per `MediaMimeWhitelist.extensionFor`).
> 3. Resolve each candidate URL to absolute via the jsoup `absUrl` API; drop `data:`/`blob:`/empty.
> 4. HEAD-probe up to 8 distinct candidates **in parallel** (with `kotlinx.coroutines.async` and a global timeout of 4 s via `withTimeoutOrNull`). For each successful HEAD: capture `Content-Type` and `Content-Length`. HEAD failures leave the candidate's size as `null`.
> 5. Drop candidates whose `Content-Type` exists and fails `MediaMimeWhitelist.isAllowed`.
> 6. Apply `CandidateSelectionPolicy.choose`. If `null` → `OpenResult.NotFound("no_media_in_html")`.
> 7. Delegate the actual download to `direct.open(chosen.url, onProgress)` and return its result verbatim. (This re-validates MIME and handles redirects.)

**Verification:**

- `Grep -n "Jsoup.parse"` in `HtmlPageExtractionStrategy.kt` matches at least once.
- `Grep -n "withTimeoutOrNull"` in the file matches at least once.
- `Grep -n "8\\b"` (HEAD fan-out cap) in the file matches at least once (numeric literal `8`).
- `Grep -n "CandidateSelectionPolicy.choose"` in the file matches exactly once.
- `Grep -n "direct.open"` in the file matches exactly once.

**Status:** `[ ]` not done

---

### Step 04.4 — Bind `HtmlPageExtractionStrategy` into the multibindings set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Add a sibling `@Binds @IntoSet abstract fun bindHtml(impl: HtmlPageExtractionStrategy): UrlExtractionStrategy` to the strategies module from Phase 03.

**Verification:**

- `Grep -n "bindHtml"` in `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt` matches exactly once.
- `Grep -n "HtmlPageExtractionStrategy"` in the same file matches at least once.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Both strategies are bound. `LinkExtractionRegistry.ordered()` returns `[direct, html]`. Phase 05 wires the coordinator to walk the registry: try `probe` in order; on the first `Applicable`, call `open`; on `NotApplicable` proceed to the next strategy; on `TransientError` skip and continue; if every strategy yields `NotApplicable`, surface `Failed.NoMediaFound`.

---

## Rollback Plan

Revert phase commit(s). With Phase 03 alone, the coordinator (when fully wired in Phase 05) still services direct file URLs — only HTML-page support is lost.
