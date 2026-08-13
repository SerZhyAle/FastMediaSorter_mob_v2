# Phase 02 - Site Resolver

**Strategic spec:** [../S0117_url-media-downloader-nolegal-flavor.md](../S0117_url-media-downloader-nolegal-flavor.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Add the `noLegal`-only site resolver and a NewPipe-backed extractor bridge that can surface single-item, streaming, auth-required, and album outcomes before the generic strategies run.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.
- [x] New `src/noLegal/java/` package path is available for new classes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/usecase/link/NoLegalExtractionResult.kt` | New | <= 250 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/NewPipeSiteExtractionStrategy.kt` | New | <= 400 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt` | New | <= 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt` | Modified | <= 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt` | Modified | <= 200 |

---

## Steps

### Step 02.1 - Extend the extraction contract for noLegal outcomes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/usecase/link/NoLegalExtractionResult.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend the extraction/open contract so a strategy can surface a structured `site-specific` outcome without forcing generic `direct` or `html` strategies to change behavior. Keep the new types usable from `src/main/` while placing the NewPipe-specific payload model in `src/noLegal/`.

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/domain/usecase/link/NoLegalExtractionResult.kt` exists.
- `Grep` - `data class SiteSpecific` present in `UrlExtractionStrategy.kt`.
- `Grep` - `sealed interface NoLegalExtractionResult` present in `NoLegalExtractionResult.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 - Verification PASS. Added `OpenResult.Batch` + `SiteBatchItem` contract in `src/main` so `src/noLegal` strategies can surface batch payloads without leaking NewPipe APIs into market flavors.

---

### Step 02.2 - Add the NewPipe-backed site resolver

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/NewPipeSiteExtractionStrategy.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Implement a `noLegal`-only `UrlExtractionStrategy` that uses the pinned NewPipe extractor dependency to recognize supported URLs, reuse S0116 cookies where possible, and return either a single downloadable item, a streaming manifest candidate, an auth-required signal, or an album payload. Bind it via Hilt multibinding only in `src/noLegal/`.

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/NewPipeSiteExtractionStrategy.kt` exists.
- `Grep` - `override val id: String = "site"` present in `NewPipeSiteExtractionStrategy.kt`.
- `Grep` - `@IntoSet` present in `NoLegalLinkDownloadModule.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 - Verification PASS. Added `NewPipeOkHttpDownloader`, `NewPipeSiteExtractionStrategy`, and `NoLegalLinkDownloadModule` under `src/noLegal`.

---

### Step 02.3 - Insert site resolver before generic strategies and cover the ordering

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistryTest.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Update the canonical order so the site resolver runs before `direct` and `html`, and add a focused unit test proving the registry prefers `site` while preserving the existing generic order after it.

**Verification:**

- `Grep` - `listOf("site", "direct", "html")` present in `LinkExtractionRegistry.kt`.
- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistryTest.kt` exists.
- `Grep` - `assertEquals(listOf("site", "direct", "html")` present in `LinkExtractionRegistryTest.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 - Verification PASS. `site` now precedes `direct/html`; `LinkExtractionRegistryTest` added for the ordering contract.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles for the touched slice via `:app_v2:compileNoLegalDebugKotlin`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 03 can assume that `site` outcomes are produced before generic URL extraction and that `noLegal` owns all NewPipe-specific code.