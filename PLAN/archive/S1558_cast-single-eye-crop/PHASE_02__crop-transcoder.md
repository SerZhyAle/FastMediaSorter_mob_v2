# Phase 02 - Crop transcoder

**Strategic spec:** [`../S1558_cast-single-eye-crop.md`](../S1558_cast-single-eye-crop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Add a self-contained transcoder in the `castEnabled` source set that turns a source file plus a `CastStereoCrop` into a half-frame copy in the Cast cache, with a duration ceiling, cancellation, and a keep-the-original failure path. Nothing calls it yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1558 phase 02"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastStereoCropTranscoder.kt` | New | ≤ 200 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). No file in this phase crosses either.
>
> **Flavor placement.** The transcoder lives in `src/castEnabled/java/` because only Cast-capable flavors ever produce a cast copy. `androidx.media3:media3-transformer` and `media3-effect` are unconditional dependencies in `app_v2/build.gradle.kts`, so no gradle change is needed and no flavor gains a new dependency.

---

## Steps

### Step 02.1 - Create `CastStereoCropTranscoder` with a suspending crop entry point

**Files:** `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastStereoCropTranscoder.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CastStereoCropTranscoder` modelled on `ui/cameracapture/helpers/VideoDigitalZoomProcessor.kt`: read that file first and keep its structure - a held `Transformer`, a held `Transformer.Listener`, `detachTransformer()` symmetric removal, and `runCatching` around `start`. Expose `suspend fun crop(context: Context, source: File, crop: CastStereoCrop, outputDir: File): File?` that builds `Crop(-1f, 0f, -1f, 1f)` for `RIGHT_HALF` and `Crop(-1f, 1f, 0f, 1f)` for `BOTTOM_HALF`, wraps the source in an `EditedMediaItem` with those `Effects`, exports to a new file under `outputDir`, and returns it. Return `null` on any failure and delete the partial output. Bridge the listener to the coroutine with `suspendCancellableCoroutine` and cancel the `Transformer` in `invokeOnCancellation`. Annotate the class `@androidx.annotation.OptIn(markerClass = [UnstableApi::class])` - kotlin's `@OptIn` is a no-op for media3's marker. On the rectangle construction, add a KDoc block fixing the convention it assumes: `Crop(left, right, bottom, top)` over the `[-1, 1]` normalized frame, so `RIGHT_HALF` keeps `x` in `[0, 1]` and `BOTTOM_HALF` keeps `y` in `[-1, 0]`, and state that this reproduces `PanelStereoCropApplier.buildMatrixFor`, which scales ×2 about `w * 0.75` for SBS and about `h * 0.75` for OU - view coordinates whose Y axis points down while the NDC Y axis points up.

**Why:**

Strategic §5 fixes the approach as one Media3 `Transformer` pass with a `Crop` effect built on the shipped S1066 precedent, and §5 also requires that a failed pass degrade to casting the original rather than breaking the session. The result distinguishes the intentional duration skip from export failure so Phase 04 can notify only the former. The KDoc on the rectangle carries §11 criterion 1: the panel and the transcoder express the same eye in axis conventions that point opposite ways, so the inversion has to be checkable in place rather than rediscovered from a wrong-eye report.

**Verification:**

- `Glob` - `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastStereoCropTranscoder.kt` exists.
- `Grep` - `class CastStereoCropTranscoder` matches exactly once in that file.
- `Grep` - `suspend fun crop(` present.
- `Grep` - `androidx.media3.effect.Crop` imported.
- `Grep` - `suspendCancellableCoroutine` present.
- `Grep` - `removeListener` present (symmetric listener release, `docs/CODE_AUDIT_PROTOCOL.md` Layer listener symmetry).
- `Grep` - `Crop(-1f, 0f, -1f, 1f)` and `Crop(-1f, 1f, 0f, 1f)` both present.
- `Grep` - `PanelStereoCropApplier` present (the convention KDoc names its origin).
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Cast transcoder compiles: a.ps1 fk standard debug passed; scoped detekt passed with no new findings.

---

### Step 02.2 - Add the duration ceiling that skips the crop on long media

**Files:** `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastStereoCropTranscoder.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a private companion constant `MAX_CROP_DURATION_MS = 5L * 60L * 1000L` and a private `readDurationMs` helper using `MediaMetadataRetriever` with `runCatching` and a `release()` in the same `also` block, following `VideoDigitalZoomProcessor.readHeight`. Return `null` from `crop` without starting a `Transformer` when the duration is unknown or exceeds the ceiling, and log the skip with `Timber.i` naming the measured duration. Put the tunable in one place so raising the ceiling is a one-constant edit.

**Why:**

Strategic §3.2 requires the CPU and battery budget to be named before the work starts and warns that the phone is range-streaming while it encodes, and the existing `MAX_VIDEO_CAST_BYTES` ceiling in the same class does not cover local files, which is exactly where stereo material lives.

**Verification:**

- `Grep` - `MAX_CROP_DURATION_MS` matches in `CastStereoCropTranscoder.kt`.
- `Grep` - `MediaMetadataRetriever` present.
- `Grep` - `release()` present (retriever released on every path).
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Cast transcoder compiles: a.ps1 fk standard debug passed; scoped detekt passed with no new findings.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done` (02.1, 02.2).
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

`crop` is total: it either returns a playable half-frame file under `outputDir`, or `null` for every reason a caller cannot act on differently - unknown duration, over the ceiling, export error, cancellation. Phase 03 therefore needs no error branching beyond a null check, and owns deleting the returned file when the session ends.

---

## Rollback Plan

Revert phase commit(s) - the class is unreferenced until Phase 03, so reverting it in isolation leaves a compiling tree and no behaviour change.
