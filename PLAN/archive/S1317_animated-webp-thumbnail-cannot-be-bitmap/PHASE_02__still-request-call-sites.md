# Phase 02 - Still surfaces declare dontAnimate()

**Strategic spec:** [`../S1317_animated-webp-thumbnail-cannot-be-bitmap.md`](../S1317_animated-webp-thumbnail-cannot-be-bitmap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Every Glide request that applies a **required** bitmap transformation to a still surface declares
`dontAnimate()`, so Phase 01's still-frame branch is the one that runs and no call site can be handed
an `AnimatedImageDrawable`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved. (none exist)
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/FolderPreviewGadget.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoAudioDisplayHelper.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureResultManager.kt` | Modified | ≤ 190 |

All three are under 500 LOC (139, 161, 171) - no backup step required.
`FolderPreviewGadget.kt` is a `launcherEnabled` source-set file and stays there; no `BuildConfig`
flavor guard is introduced. No layout XML is touched, so landscape parity does not apply.

`AdapterThumbnailLoader.kt` is deliberately **absent** from this phase: both of its image legs
(`:530` network, `:571` local) already call `dontAnimate()`, so Phase 01 alone fixes them. Adding a
duplicate call would be a no-op edit.

---

## Steps

### Step 02.1 - Launcher folder-preview tile

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/FolderPreviewGadget.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The preview tile chains `.override(size, size).centerCrop()` on an `asDrawable()` request over
> device-local media, so an animated WebP in a watched folder throws
> `Unable to convert .. to a Bitmap` and the tile falls back to the folder icon. Add `.dontAnimate()`
> to that chain. A launcher tile is a still preview; animating it would also keep a decoder alive
> behind the home screen.

**Verification:**

- `Grep` - `dontAnimate()` matches exactly once in that file.
- `Grep` - `centerCrop()` still matches exactly once in that file.
- `Grep -n` - the `dontAnimate()` line number is greater than the `.load(model)` line number.

**Status:** `[x]` done - added after `.centerCrop()`, before `.placeholder()`.

---

### Step 02.2 - Audio cover art in the file-info dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoAudioDisplayHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The cached cover file is loaded with `.load(coverFile).centerCrop()` on an `asDrawable()` request.
> The cover extension comes from embedded tag data and can be WebP, so the same required-transform
> failure applies. Add `.dontAnimate()` to that chain.

**Verification:**

- `Grep` - `dontAnimate()` matches exactly once in that file.
- `Grep` - `centerCrop()` still matches exactly once in that file.

**Status:** `[x]` done - added after `.centerCrop()`, before `.into()`.

---

### Step 02.3 - Camera capture gallery thumbnail

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureResultManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `Glide.with(activity).load(File(path)).centerCrop().into(galleryThumbnail)` carries the same
> required-transform exposure. The path is app-produced today, so this is defence in depth rather
> than an observed failure - add `.dontAnimate()` so the surface cannot regress if the source of that
> path ever widens.

**Verification:**

- `Grep` - `dontAnimate()` matches exactly once in that file.
- `Grep` - `centerCrop()` still matches exactly once in that file.

**Status:** `[x]` done - added on the same chain, after `.centerCrop()`, before `.into()`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` (`:app_v2:compileStandardDebugKotlin`) `BUILD SUCCESSFUL`. Confirmed
  the `standard` flavor mounts `src/launcherEnabled/java` (`app_v2/build.gradle.kts:601-604`), so Step
  02.1's edit was actually compiled, not skipped.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Probe form `Timber.d("S1317:` returns zero hits in all three touched files (only rationale
  comments, same precedent as Phase 01/03).
- [x] Dev log entry - deferred to the consolidated Phase 04 close (same rationale as Phase 01/03).
- [x] Phase-boundary audit run - all three edits are single-line Glide-chain additions with no new
  state, listener, or lifecycle surface. No P0/P1 found.

---

## Handoff Notes to Next Phase

Every required-transform call site reachable by an animated image now declares `dontAnimate()`.
`DualSurfaceStaticImageRenderer.kt:266` chains a required `fitCenter()` without `dontAnimate()` and is
intentionally left alone: `ImageLoadingManager.kt:499-501` shows the renderer sits behind
`rendererMigrationEnabled` with the legacy path still in use, and it is the surface that must keep
animating when the migration lands. It is recorded as a latent risk, not a defect to patch blind.

---

## Rollback Plan

Revert phase commit(s) - three single-line additions, no data migration or user-facing surface
changed.
