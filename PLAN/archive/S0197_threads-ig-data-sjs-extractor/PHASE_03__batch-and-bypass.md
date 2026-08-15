# Phase 03 — Carousel Batch trigger by count + SocialPreviewOnly bypass

**Strategic spec:** [`../S0197_threads-ig-data-sjs-extractor.md`](../S0197_threads-ig-data-sjs-extractor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Make the carousel path actually emit `OpenResult.Batch` and stop the SocialPreviewOnly guard from firing on legitimate photo posts. Both strategies (dynamic WebView and cheap HTML) must agree: ≥ 2 image-typed `EMBEDDED_JSON` candidates ⇒ Batch; ≥ 1 image-typed `EMBEDDED_JSON` candidate ⇒ skip SocialPreviewOnly.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done — selection bias is in place.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt` | Modified | ≤ 780 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt` | Modified | ≤ 400 |

---

## Steps

### Step 03.1 — `shouldReturnBatch` counts EMBEDDED_JSON image candidates as substantial

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `InvisibleWebViewExtractionStrategy.shouldReturnBatch(candidates)`, extend the `substantial` predicate so a candidate also counts when its `source == HtmlMediaCandidate.Source.EMBEDDED_JSON`, regardless of `tentativeSizeBytes`. Keep the existing manifest-or-size checks intact — both predicates are OR-joined. The function still returns `false` for `candidates.size < 2`.

**Verification:**

- `Grep` — `it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON` matches in `InvisibleWebViewExtractionStrategy.kt`.
- `Grep` — `private fun shouldReturnBatch` matches once in `InvisibleWebViewExtractionStrategy.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS. Files: `app_v2/.../link/InvisibleWebViewExtractionStrategy.kt` (+5 LOC).

---

### Step 03.2 — Bypass dynamic-strategy SocialPreviewOnly guard when EMBEDDED_JSON image is present

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `InvisibleWebViewExtractionStrategy.open`, locate the branch `if (nonImageCandidates.isEmpty())` that calls `KnownAuthResources.isPreviewSensitiveHost(host)` and may return `OpenResult.SocialPreviewOnly`. Before that guard fires, compute `val hasEmbeddedJsonImage = merged.any { it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON && isImageCandidate(it) }`. If `hasEmbeddedJsonImage` is true, skip the SocialPreviewOnly return — fall through to the existing Batch / single-image selection path. Add a verbose trace `"dynamic-strategy embedded-json-image-present bypass-preview-only host=…"` for diagnostics.

**Verification:**

- `Grep` — `hasEmbeddedJsonImage` matches at least twice in `InvisibleWebViewExtractionStrategy.kt` (declaration + usage; may exceed 2 if a bypass-trace branch references it).
- `Grep` — `embedded-json-image-present` matches in `InvisibleWebViewExtractionStrategy.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS (predicate "at least twice" satisfied with 3 hits — bypass-trace branch references the variable). Files: `app_v2/.../link/InvisibleWebViewExtractionStrategy.kt` (+13 LOC).

---

### Step 03.3 — Add Batch return path to `HtmlPageExtractionStrategy.open` for embedded-JSON carousels

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `HtmlPageExtractionStrategy.open`, after the `filtered` list is finalized and before the call to `CandidateSelectionPolicy.choose`, compute `val embeddedImages = filtered.filter { it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON && isImageCandidate(it) }` (introduce a local helper `isImageCandidate` modeled on the one in `InvisibleWebViewExtractionStrategy` — image MIME, OG/IMG/IMG_SRCSET source, or known image extension). If `embeddedImages.size >= 2`, return `OpenResult.Batch(items = embeddedImages.take(MAX_BATCH_ITEMS).map { SiteBatchItem(url = it.url) })`. Add the constant `const val MAX_BATCH_ITEMS: Int = 12` to the companion object (mirror the dynamic strategy). Add imports `OpenResult.Batch` and `SiteBatchItem` (from `com.sza.fastmediasorter.domain.usecase.link.SiteBatchItem`).

**Verification:**

- `Grep` — `OpenResult.Batch\(` matches in `HtmlPageExtractionStrategy.kt`.
- `Grep` — `SiteBatchItem` matches in `HtmlPageExtractionStrategy.kt`.
- `Grep` — `MAX_BATCH_ITEMS` matches at least twice in `HtmlPageExtractionStrategy.kt` (declaration + usage).
- `Grep` — `private fun isImageCandidate` matches once in `HtmlPageExtractionStrategy.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS. Files: `app_v2/.../link/HtmlPageExtractionStrategy.kt` (+30 LOC).

---

### Step 03.4 — Bypass html-strategy SocialPreviewOnly guard when EMBEDDED_JSON image is present

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `HtmlPageExtractionStrategy.open`, update the existing `KnownAuthResources.isPreviewSensitiveHost(host)` SocialPreviewOnly guard so that the `hasRealContent` check additionally treats a candidate as real-content when `candidate.source == HtmlMediaCandidate.Source.EMBEDDED_JSON`. The current guard counts only non-OG/non-IMG sources as real; `EMBEDDED_JSON` must be added to the accept set. Add a verbose trace `"html-strategy embedded-json-image-present bypass-preview-only host=…"`.

**Verification:**

- `Grep` — `candidate.source == HtmlMediaCandidate.Source.EMBEDDED_JSON` matches in `HtmlPageExtractionStrategy.kt`.
- `Grep` — `embedded-json-image-present` matches in `HtmlPageExtractionStrategy.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS. Files: `app_v2/.../link/HtmlPageExtractionStrategy.kt` (+15 LOC).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for both files in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

After Phase 03: end-to-end flows are complete. Threads single-image → real image. Threads/IG carousel → all slides via Batch. IG photo single → real image, no SocialPreviewOnly. Phase 04 only handles catalog/dev-log cleanup.

---

## Rollback Plan

Revert the two file edits — `shouldReturnBatch` returns to size-only predicate, both SocialPreviewOnly guards return to their previous behavior, html strategy loses its Batch return path. No data migration, no user-visible surface added.
