# Tactical Plan: S1317 - animated-webp-thumbnail-cannot-be-bitmap

**Strategic spec:** [`../S1317_animated-webp-thumbnail-cannot-be-bitmap.md`](../S1317_animated-webp-thumbnail-cannot-be-bitmap.md)
**Research inputs:** none
**Feature:** Animated WebP thumbnails decode to a still frame instead of failing and poisoning the failure cache
**Tier:** 2 - Small
**Priority:** 45
**Status:** In Progress
**Phases:** 3 / 4 done
**Last updated:** 2026-08-01

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Root cause (established, source-verified)

Glide 4.16.0 orders decoder buckets `[PREPEND_ALL, ANIMATION, BITMAP, BITMAP_DRAWABLE, APPEND_ALL]`
(`Registry.java:267-278`). The project's own animated decoders sit in `PREPEND_ALL`; Glide's built-in
`AnimatedImageDecoder` sits in `ANIMATION`.

1. A thumbnail request calls `dontAnimate()`, which sets only `GifOptions.DISABLE_ANIMATION`
   (`BaseRequestOptions.java:1096-1098`).
2. `AnimatedImageDecoder.kt:32,52` declines that request (S1026 guard).
3. Declining does **not** reach the downsampler. The next bucket is Glide's own
   `AnimatedImageDecoder`, whose `isHandled` tests the image type only and never reads
   `DISABLE_ANIMATION` (`AnimatedImageDecoder.java:63-66`). It returns an `AnimatedImageDrawable`.
4. `centerCrop()` is a **required** transform (`BaseRequestOptions.java:735-737`).
   `DrawableToBitmapConverter.convert` returns null for any `Animatable`
   (`DrawableToBitmapConverter.java:38`), and `DrawableTransformation.transform` then throws
   `IllegalArgumentException("Unable to convert " + drawable + " to a Bitmap")`
   (`DrawableTransformation.java:51-58`) - the exact string in the captured log.

The single point of control is step 2: the project decoder runs **before** Glide's and must produce
the still frame itself rather than stepping aside.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | animated-still-decode | - | ✅ Done | 4/4 | [PHASE_01__animated-still-decode.md](PHASE_01__animated-still-decode.md) |
| 02 | still-request-call-sites | 01 | ✅ Done | 3/3 | [PHASE_02__still-request-call-sites.md](PHASE_02__still-request-call-sites.md) |
| 03 | failure-cache-hygiene | 01 | ✅ Done | 4/4 | [PHASE_03__failure-cache-hygiene.md](PHASE_03__failure-cache-hygiene.md) |
| 04 | docs-catalog-cleanup | all | 🚧 In Progress | 0/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §3 items 1-3 are all resolved from source in this plan:

- §3.1 (which Glide request builds the network thumbnail, does it ask for a Bitmap) - resolved:
  `AdapterThumbnailLoader.kt:516` is `asDrawable()`, not `asBitmap()`. The Bitmap requirement comes
  from the required `centerCrop()` at line 528, not from the request variant.
- §3.2 (first frame instead of converting the whole drawable) - resolved: Phase 01.
- §3.3 (should this failure reach the failed-thumbnail cache) - resolved: no; Phase 03.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic §8 absent, this is a defect fix, not a showcase item.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1317` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1317`.

---

## Debug probes (final transition only)

Do **not** insert `Timber.d("S1317: ..")` during phases 01-03; the ticket-log gate rejects a probe
whose spec is not yet `BlockNeedUserTest`. `/spec-dev`'s final-phase step and `close-and-log.ps1` own
insertion. Exact lines to use then (both under 120 chars):

- `AnimatedImageDecoder.kt`, still-frame branch:
  `Timber.d("S1317: still-frame decode, req=%dx%d", width, height)`
- `AdapterThumbnailLoader.kt`, skipped-marking branch:
  `Timber.d("S1317: skip failed-thumb mark for ${file.name}")`

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-01 - `/spec-all S1317` resume: found Phases 01 and 03 already implemented in the working tree
  ahead of tactical tracking (CLAUDE.md "working tree = truth"), confirmed every step's verification
  predicate, and fixed three real defects surfaced during that verification: a duplicate `companion
  object` in `AdapterThumbnailLoader.kt` (the actual root cause blocking S1338's kapt build - masked as
  a stackless kapt NPE), a `getResourceClass()` mismatch in `AnimatedImageDecoder.kt` that silently
  defeated Phase 01's disk-cache goal, and an untested `Resources.getSystem()` NPE in
  `AnimatedImageDecoderHandlesTest.kt` that grep-only verification had missed - `testStandardDebugUnitTest`
  now passes 6/6. Implemented Phase 02 (three `dontAnimate()` additions). Phase 04 in progress.
