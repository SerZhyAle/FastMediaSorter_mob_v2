# Phase 02 - Bundle-First Cyclic Playlist

**Strategic spec:** [`../S0290_vr_test_quality_overhaul.md`](../S0290_vr_test_quality_overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Make the diagnostic session always start with the bundled in-APK 360° mono asset (Poly Haven `lakeside.jpg`, 8192×4096, CC0) as position 0 of the playlist, with external files appended afterwards; cycle wraps back to the bundle.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved — none block Phase 02.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 710 (current 646 + Phase 01 delta) |

---

## Steps

### Step 02.1 - Introduce a synthetic playlist entry for the bundled asset

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Currently `scanMediaFiles()` maps `VR_TEST_MEDIA_ORDER` to existing files on device. Refactor `mediaPlaylist` to carry **two kinds** of entries: a sentinel `BundledAsset` first, then `ExternalFile(file: File)` items from the existing scan. Use a sealed class or sealed interface (e.g. `private sealed class PlaylistEntry`). `mediaPlaylist` is now `List<PlaylistEntry>`, always non-empty (bundle is always present). Update `currentPlaylistIndex` initialisation to start at 0.

**Verification:**

- `Grep` - `sealed (class|interface) PlaylistEntry` matches exactly once in `DiagnosticXrActivity.kt`.
- `Grep` - `mediaPlaylist: List<PlaylistEntry>` matches exactly once in `DiagnosticXrActivity.kt`.
- `Grep` - `BundledAsset` matches at least twice in `DiagnosticXrActivity.kt` (sealed-class declaration + at least one constructor / reference).

**Status:** `[ ]` not done

---

### Step 02.2 - Wire load logic to dispatch on PlaylistEntry kind

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Update `loadCurrentMediaItem()` and the initial load in `proceedWithInitialization()` to branch on the entry type. For `BundledAsset` → call existing `decodeBundledAsset()` path (image always; never video). For `ExternalFile(file)` → existing image / video branching by extension. Update `navigateToNextMedia()` and `navigateToPrevMedia()` to use modular index over `mediaPlaylist.size` (cyclic wrap-around; existing `% size` already cyclic, just confirm). Update `hudRenderer.currentFilename` accordingly: for bundle use literal `"vr_diagnostic_360_mono.jpg"`, for external use `file.name`.

**Verification:**

- `Grep` - `when (entry)` or `when (val entry` matches at least once in `loadCurrentMediaItem` block (sealed-class dispatch).
- `Grep` - `is BundledAsset` matches at least twice (in `loadCurrentMediaItem` and `proceedWithInitialization`).
- `Grep` - `is ExternalFile` matches at least twice.

**Status:** `[ ]` not done

---

### Step 02.3 - Add Timber.d probe for playlist construction

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> After building the playlist, log one neutral Timber.d line summarising the playlist composition (count of bundle entries + count of external files). Probe text format: `Timber.d("DiagnosticXrActivity: playlist built; bundle=1, external=$count, total=${list.size}")`. Insertion point: end of `scanMediaFiles()` or right after the playlist field is assigned in `proceedWithInitialization()`.

**Verification:**

- `Grep` - `Timber\.d\("DiagnosticXrActivity: playlist built` matches exactly once in `DiagnosticXrActivity.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (target: `nd`).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] On-device check: app launches → first frame shows Poly Haven lakeside 360° sphere; pressing right-controller "next" advances to first external file; cycling past the last external returns to lakeside.

---

## Handoff Notes to Next Phase

`PlaylistEntry` sealed class is the canonical playlist datatype. Phase 03 (filename parser extension) and Phase 04 (metadata strategies) consume `ExternalFile.file` to derive render config; Phase 03 also routes `BundledAsset` through the new format-detector facade for HUD-label consistency. `BundledAsset` is hardcoded as `(SPHERE_360, MONO)` — Phase 03 will not change this default.

---

## Rollback Plan

Revert phase commits — `PlaylistEntry` sealed class is internal, no DI or persistence touched. Restoring the previous flat `List<File>` is a single-file revert.

## Revision History

- **2026-05-22** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability, stability)
	- Applied: 1. Proposed (DISCUSS): 0.
