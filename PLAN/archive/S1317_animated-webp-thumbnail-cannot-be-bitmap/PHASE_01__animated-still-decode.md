# Phase 01 - Animated decoder returns a still frame for still requests

**Strategic spec:** [`../S1317_animated-webp-thumbnail-cannot-be-bitmap.md`](../S1317_animated-webp-thumbnail-cannot-be-bitmap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-07-31 (implemented ahead of tactical tracking)
**Completed:** 2026-08-01 (confirmed against working tree during `/spec-all S1317` resume - CLAUDE.md "working tree = truth")

---

## Objective

Make `AnimatedImageByteBufferDecoder` / `AnimatedImageStreamDecoder` claim every animated WebP/APNG
they sniff - including requests that set `GifOptions.DISABLE_ANIMATION` - and return a
`BitmapDrawable`-backed first frame for those, so no required bitmap transformation can ever be
handed an `AnimatedImageDrawable`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none)
- [ ] Strategic §6 research items blocking this phase are Resolved. (none exist)
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/AnimatedImageDecoder.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt` | Modified | ≤ 300 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/glide/AnimatedImageDecoderHandlesTest.kt` | Modified | ≤ 120 |

All three files are under 500 LOC (199, 285 and 73) - no backup step required.

---

## Steps

### Step 01.1 - Stop declining still requests

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/AnimatedImageDecoder.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In both `AnimatedImageByteBufferDecoder.handles` and `AnimatedImageStreamDecoder.handles`, remove
> the `if (isAnimationDisabled(options)) return false` early return so the header sniff alone decides.
> Keep `isAnimationDisabled` - Step 01.2 reuses it inside `decode`. Rewrite the KDoc that currently
> sits above `isAnimationDisabled`: its claim that declining "hands the file back to the built-in
> downsampler" is false. Declining passes the file to Glide's own `AnimatedImageDecoder` in the later
> `BUCKET_ANIMATION`, which ignores `GifOptions.DISABLE_ANIMATION` and returns an
> `AnimatedImageDrawable` anyway. State that this decoder must therefore serve the still case itself.

**Verification:**

- `Grep` - `isAnimationDisabled(options)) return false` returns zero hits in that file.
- `Grep` - `private fun isAnimationDisabled` still matches exactly once.
- `Grep` - `hands the file back` returns zero hits in that file. (Do not grep `built-in downsampler`
  alone - the phrase legitimately survives at line 24, describing static files.)

**Status:** `[x]` done - all three verification greps confirmed against working tree.

---

### Step 01.2 - Decode a still first frame when animation is disabled

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/AnimatedImageDecoder.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a private `decodeStillFrame(imageSource: ImageDecoder.Source, width: Int, height: Int):
> Resource<Drawable>?` next to `decodeAnimatedDrawable`. Implement it with
> `ImageDecoder.decodeBitmap`, reusing the existing header listener body: call
> `decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)` and apply
> `AnimatedImageHeaderSniffer.sampleSize(info.size.width, info.size.height, width, height)` through
> `decoder.setTargetSampleSize(sample)` when it exceeds 1. `ImageDecoder.decodeBitmap` on an animated
> source yields the first frame. Wrap the bitmap in a `BitmapDrawable` and return it through a new
> private `Resource<Drawable>` whose `getResourceClass()` reports `BitmapDrawable::class.java`, whose
> `getSize()` returns the bitmap's `allocationByteCount`, and whose `recycle()` is a no-op. Catch
> `IOException` and return null exactly as `decodeAnimatedDrawable` does. The decoders need a
> `Resources` to build the `BitmapDrawable`; add a constructor parameter and pass `context.resources`
> from `GlideAppModule.registerComponents`, which already holds `context`.

**Verification:**

- `Grep` - `private fun decodeStillFrame` matches exactly once.
- `Grep` - `ImageDecoder.decodeBitmap` matches exactly once in that file.
- `Grep` - `BitmapDrawable::class.java` matches exactly once in that file.
- `Grep` - `ALLOCATOR_SOFTWARE` matches at least twice in that file (animated branch plus still branch).

**Status:** `[x]` done. Found and fixed a real defect while verifying: `BitmapDrawableResource.getResourceClass()`
had been left returning `Drawable::class.java` (copy-pasted from `AnimatedImageDrawableResource`), which
silently defeated this step's own disk-cache goal - Glide's registry keys the encoder lookup off
`getResourceClass()`, so the still-frame result was resolving the same `AnimatedImageDrawableNoOpEncoder`
as the animated branch instead of the built-in `BitmapDrawableEncoder`. Corrected to
`BitmapDrawable::class.java` (with the narrowed constructor param and an unchecked-cast suppress scoped to
that one accessor) and swapped the misplaced KDoc onto the correct class. `fk` reconfirmed green after the
fix.

---

### Step 01.3 - Route decode() through the still branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/AnimatedImageDecoder.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In both decoders' `decode`, branch on `isAnimationDisabled(options)`: true selects
> `decodeStillFrame`, false keeps `decodeAnimatedDrawable`. Update the two `registry.prepend` calls in
> `GlideAppModule.registerComponents` to pass the constructor argument added in Step 01.2. Leave the
> registration order, the buckets, and `AnimatedImageDrawableNoOpEncoder` untouched - the no-op
> encoder still backs the animated branch, while the still branch now reports `BitmapDrawable` and so
> resolves Glide's own `BitmapDrawableEncoder`, making these thumbnails disk-cacheable for the first
> time.

**Verification:**

- `Grep` - `isAnimationDisabled(options)` matches exactly twice in `AnimatedImageDecoder.kt` (once per decoder's `decode`).
- `Grep` - `decodeStillFrame(` matches exactly three times in `AnimatedImageDecoder.kt` - one declaration plus one call in each decoder's `decode`.
- `Grep` - `AnimatedImageDrawableNoOpEncoder()` still matches exactly once in `GlideAppModule.kt`.
- `Grep` - `registry.prepend` still matches exactly twice inside the `isAnimatedImageDecodeSupported()` block.

**Status:** `[x]` done - all four verification greps confirmed against working tree.

---

### Step 01.4 - Invert the regression test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/glide/AnimatedImageDecoderHandlesTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> This test currently locks in the defect: its KDoc and the two `declines when the request disabled
> animation` cases assert the exact behaviour that lets Glide's built-in animated decoder win. Rewrite
> the KDoc to state the S1317 contract - the decoder claims animated input unconditionally, because
> declining forfeits the file to a decoder that ignores `DISABLE_ANIMATION`. Replace both "declines"
> cases with cases asserting `handles(..) == true` when `GifOptions.DISABLE_ANIMATION` is set. Keep
> the static-WebP case unchanged: a static file must still never be claimed. Update construction to
> pass the `Resources` argument added in Step 01.2.

**Verification:**

- `Grep` - `declines when the request disabled animation` returns zero hits in that file.
- `Grep` - `assertFalse` matches exactly twice (the static-WebP case only).
- `Grep` - `DISABLE_ANIMATION` still matches at least once.
- `Grep` - `a static webp is never claimed` still matches exactly once.

**Status:** `[x]` done - test file already asserted the S1317 contract and all four verification greps
passed, but the test had never actually been RUN: `resources: Resources = Resources.getSystem()` throws
`NullPointerException` under the plain JVM unit-test stub (no real Android framework backing it), failing
all 6 cases at construction. Grep-only verification would have missed this. Replaced with
`io.mockk.mockk()` - `handles()` never touches `Resources`, only `decode()` does, so a bare mock is
sufficient. `testStandardDebugUnitTest --tests AnimatedImageDecoderHandlesTest` now green, 6/6 passed.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` (`:app_v2:compileStandardDebugKotlin`) `BUILD SUCCESSFUL`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `Grep` for the literal probe form `Timber.d("S1317:` returns zero hits. The two plain `// S1317:`
  rationale comments (this file's merged companion-object constant, the test KDoc) are precedent-consistent
  with the existing `// S1026:` comments in the same file and are not probes.
- [x] Dev log entry - deferred to the consolidated Phase 04 close (`close-and-log.ps1 -DevLogs`, one entry
  for all six touched files per CLAUDE.md journaling granularity, not per phase).
- [x] Public API changed (decoder constructors gained a `Resources` param) - `dev/CATALOG/app_v2.jsonl`
  regeneration deferred to Phase 04 Step 04.1 (same rationale - once per ticket, not per phase).
- [x] Phase-boundary audit run - reviewed `AnimatedImageDecoder.kt` end to end: exception handling matches
  the established `decodeAnimatedDrawable` pattern, no resource leak, `recycle()` no-ops are consistent with
  the sibling class. No P0/P1 found. One P2-adjacent defect found and fixed inline (see Step 01.2 note).

---

## Handoff Notes to Next Phase

Any Glide request that sets `dontAnimate()` now receives a `BitmapDrawable` for animated WebP/APNG, so
required bitmap transformations on those requests are safe. Requests that do **not** set
`dontAnimate()` still receive an `AnimatedImageDrawable` and remain unsafe - Phase 02 closes those.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed. Behaviour returns to the
pre-S1317 state where animated WebP thumbnails fail.
