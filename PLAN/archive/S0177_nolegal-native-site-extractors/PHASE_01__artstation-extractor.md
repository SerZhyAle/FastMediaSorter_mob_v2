# Phase 01 — ArtStation Extractor

**Strategic spec:** [`../S0177_nolegal-native-site-extractors.md`](../S0177_nolegal-native-site-extractors.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 4 / 4
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Register the four native-extractor strategy IDs in `LinkExtractionRegistry.CANONICAL_ORDER`, then implement and wire `ArtStationExtractionStrategy` into the noLegal DI module.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] noLegal build toolchain available (`./gradlew assembleNoLegalDebug -Pchaquopy.enabled=true`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt` | Modified | ≤ 35 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/ArtStationExtractionStrategy.kt` | New | ≤ 150 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt` | Modified | ≤ 40 |

---

## Steps

### Step 01.1 — Expand CANONICAL_ORDER in LinkExtractionRegistry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `LinkExtractionRegistry.CANONICAL_ORDER`, prepend the four native-extractor IDs before `"ytdlp"`:
> `listOf("artstation", "deviantart", "vimeo", "dailymotion", "ytdlp", "site", "direct", "html", "dynamic")`.
> These IDs are registered only in the noLegal DI module — in other flavors they are absent from the strategy set, so `indexOf` returns -1 → `Int.MAX_VALUE`, which sorts them last and is harmless (same comment pattern as S0174 "ytdlp").
> Add a comment: `// S0177: native site extractors — noLegal only, benign no-op in other flavors.`

**Verification:**

- `Grep` — `"artstation"` present in `LinkExtractionRegistry.kt`.
- `Grep` — `"deviantart"` present in `LinkExtractionRegistry.kt`.
- `Grep` — `"vimeo"` present in `LinkExtractionRegistry.kt`.
- `Grep` — `"dailymotion"` present in `LinkExtractionRegistry.kt`.
- `Grep` — order: `artstation` appears before `ytdlp` on the same list literal.

**Status:** `[x] done`

**Step Log:**
- 2026-05-12 — Verification 5/5 PASS. Files: LinkExtractionRegistry.kt (+1 line). Dev log recorded.

---

### Step 01.2 — Implement ArtStationExtractionStrategy

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/ArtStationExtractionStrategy.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `@Singleton class ArtStationExtractionStrategy @Inject constructor` in package `com.sza.fastmediasorter.data.link.nolegal`, implementing `UrlExtractionStrategy`.
>
> `id = "artstation"`
>
> **`probe(url)`:** return `Applicable(null, null)` iff `url.toHttpUrlOrNull()?.host` ends with `artstation.com`; else `NotApplicable`. O(1), no network.
>
> **`open(url, onProgress)`:** extract the project slug from the URL path (last non-empty segment of `artstation.com/artwork/{slug}` or `artstation.com/{user}/projects/{slug}` — take the last path segment).
> Call `GET https://www.artstation.com/projects/{slug}.json` using `@Named("linkDownload") OkHttpClient` with header `User-Agent: Mozilla/5.0 (compatible; FastMediaSorter)`.
> Parse response JSON (`org.json.JSONObject`): iterate `assets` array. For each asset:
> - If `asset_type == "video_clip"` and `video_clip_url` is non-blank → preferred.
> - Else `image_url` non-blank → fallback.
> Pick video if present, else largest image by checking `has_image` / take first `image_url`.
> Then call `direct.open(mediaUrl, onProgress, extraHeaders = mapOf("Referer" to "https://www.artstation.com/"))`.
>
> On parse failure or empty `assets` → return `OpenResult.NotFound("artstation_parse_failed")` (WebView-dynamic will handle it).
> On HTTP non-2xx → `OpenResult.NotFound("artstation_api_error_${code}")`.
> On `IOException` → `OpenResult.Error(e)`.
>
> Constructor parameters: `@Named("linkDownload") private val httpClient: OkHttpClient`, `private val direct: DirectFileExtractionStrategy`.
> Dispatcher: `withContext(Dispatchers.IO)`.
> Logging: `Timber.w(e, "ArtStationExtractor: open failed %s", url)` on errors.
> No `Log.d()`.

**Verification:**

- `Glob` — `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/ArtStationExtractionStrategy.kt` exists.
- `Grep` — `class ArtStationExtractionStrategy` matches exactly once.
- `Grep` — `override val id: String = "artstation"` present.
- `Grep` — `probe` function present.
- `Grep` — `open` function present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-12 — Verification 6/6 PASS. Files: ArtStationExtractionStrategy.kt (new, 97 LOC). Dev log recorded.

---

### Step 01.3 — Wire ArtStation strategy into NoLegalLinkDownloadModule

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add to `NoLegalLinkDownloadModule`:
> ```kotlin
> @Binds
> @IntoSet
> abstract fun bindArtStation(impl: ArtStationExtractionStrategy): UrlExtractionStrategy
> ```
> Import `com.sza.fastmediasorter.data.link.nolegal.ArtStationExtractionStrategy`.

**Verification:**

- `Grep` — `bindArtStation` present in `NoLegalLinkDownloadModule.kt`.
- `Grep` — `ArtStationExtractionStrategy` present in the file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-12 — Verification 2/2 PASS. Files: NoLegalLinkDownloadModule.kt (+6 LOC). Dev log recorded.

---

### Step 01.4 — Dev log

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 01.3

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt" "S0177" "Add artstation/deviantart/vimeo/dailymotion to CANONICAL_ORDER"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/ArtStationExtractionStrategy.kt" "S0177" "New native extractor for artstation.com"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt" "S0177" "Bind ArtStationExtractionStrategy"
> ```

**Verification:**

- `Grep` — `ArtStationExtractionStrategy` appears in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-12 — Verification 1/1 PASS. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for noLegal flavor (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entries added (Step 01.4).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `CANONICAL_ORDER` already contains all four strategy IDs — Phases 02–04 only add new files and DI bindings.
- `NoLegalLinkDownloadModule.kt` has the `bindArtStation` binding — follow the same pattern for the remaining three.
- `@Named("linkDownload") OkHttpClient` + `DirectFileExtractionStrategy` are the only two constructor dependencies needed by all four extractors.

---

## Rollback Plan

Revert phase commit(s) — no data migration, no user-facing surface changed, no Room schema touched.
