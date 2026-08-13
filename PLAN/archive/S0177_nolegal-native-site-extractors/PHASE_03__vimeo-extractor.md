# Phase 03 — Vimeo Extractor

**Strategic spec:** [`../S0177_nolegal-native-site-extractors.md`](../S0177_nolegal-native-site-extractors.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Implement `VimeoExtractionStrategy` — fetch the Vimeo player config JSON to extract a direct MP4 stream or HLS manifest without WebView.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/VimeoExtractionStrategy.kt` | New | ≤ 180 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt` | Modified | ≤ 60 |

---

## Steps

### Step 03.1 — Implement VimeoExtractionStrategy

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/VimeoExtractionStrategy.kt`
**Depends on:** — start of phase (Phase 02 done)

**Prompt for developer:**

> Create `@Singleton class VimeoExtractionStrategy @Inject constructor` in package `com.sza.fastmediasorter.data.link.nolegal`, implementing `UrlExtractionStrategy`.
>
> `id = "vimeo"`
>
> **`probe(url)`:** return `Applicable(null, null)` iff host ends with `vimeo.com` AND path contains a numeric video ID (Regex `^/(\d+)` on path, or `^/channels/.+/(\d+)`, `^/groups/.+/videos/(\d+)`, etc.). Return `NotApplicable` for `/user/`, `/categories/`, `/tag/` paths. Simple host check is acceptable if path discrimination is complex — fallback to `NotApplicable` from `open()` is safe.
>
> **`open(url, onProgress)`:** run on `Dispatchers.IO`.
>
> 1. Extract video ID: use Regex `vimeo\.com/(?:video/|channels/\S+/|groups/\S+/videos/|album/\d+/video/)?(\d+)` → group 1.
>    If no ID found → return `OpenResult.NotFound("vimeo_no_id")`.
>
> 2. Fetch config: `GET https://player.vimeo.com/video/{id}/config` using `@Named("linkDownload") OkHttpClient` with headers:
>    - `Referer: https://vimeo.com/`
>    - `User-Agent: Mozilla/5.0 (compatible; FastMediaSorter)`
>
>    On non-2xx or `IOException` (including 403 for password-protected / unavailable) → `OpenResult.NotFound("vimeo_config_failed")`. Password-protected fallback is **not** implemented in v1; WebView-dynamic handles it.
>
> 3. Parse config JSON: `request.files.progressive[]` — array of objects with `url`, `quality`, `width`, `height`. Sort descending by `width` (highest resolution first), pick first with a non-blank `url`.
>    If `progressive` is absent or empty → fall back to `request.files.hls.url` (HLS manifest).
>
> 4. Result:
>    - Progressive MP4 → `direct.open(mp4Url, onProgress, extraHeaders = mapOf("Referer" to "https://vimeo.com/"))`.
>    - HLS → `OpenResult.Streaming(manifest = StreamingManifest.Hls(manifestUrl = hlsUrl), tentativeFileName = "vimeo_${id}.mp4")`.
>    - Neither found → `OpenResult.NotFound("vimeo_no_stream")`.
>
> Constructor parameters: `@Named("linkDownload") private val httpClient: OkHttpClient`, `private val direct: DirectFileExtractionStrategy`.
> No `Log.d()`.

**Verification:**

- `Glob` — `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/VimeoExtractionStrategy.kt` exists.
- `Grep` — `class VimeoExtractionStrategy` matches exactly once.
- `Grep` — `override val id: String = "vimeo"` present.
- `Grep` — `player.vimeo.com` appears in this file (config endpoint).
- `Grep` — `Referer` appears in this file.
- `Grep` — `StreamingManifest.Hls` appears in this file (HLS fallback).
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — [FIXED by spec-fix] All 7 verification predicates PASS. Implementation existed; metadata was stale.

---

### Step 03.2 — Wire Vimeo strategy into NoLegalLinkDownloadModule

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add to `NoLegalLinkDownloadModule`:
> ```kotlin
> @Binds
> @IntoSet
> abstract fun bindVimeo(impl: VimeoExtractionStrategy): UrlExtractionStrategy
> ```
> Import `com.sza.fastmediasorter.data.link.nolegal.VimeoExtractionStrategy`.

**Verification:**

- `Grep` — `bindVimeo` present in `NoLegalLinkDownloadModule.kt`.
- `Grep` — `VimeoExtractionStrategy` present in the file.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — [FIXED by spec-fix] bindVimeo + VimeoExtractionStrategy present in NoLegalLinkDownloadModule. Metadata was stale.

---

### Step 03.3 — Dev log

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 03.2

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/VimeoExtractionStrategy.kt" "S0177" "New native extractor for vimeo.com — player config JSON, MP4 or HLS"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt" "S0177" "Bind VimeoExtractionStrategy"
> ```

**Verification:**

- `Grep` — `VimeoExtractionStrategy` appears in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — [FIXED by spec-fix] VimeoExtractionStrategy appears in CHANGELOG (4 hits). Metadata was stale.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for noLegal flavor.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added (Step 03.3).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Vimeo password-protected videos return `OpenResult.NotFound("vimeo_config_failed")` — the registry's WebView-dynamic strategy handles them automatically.
- `StreamingManifest.Hls` import: `com.sza.fastmediasorter.domain.model.link.StreamingManifest`.

---

## Rollback Plan

Revert phase commit(s) — no data migration, no user-facing surface changed.
