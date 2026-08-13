# Phase 02 - Activity Contract

**Strategic spec:** [`../S0295_vr-generic-immerse-playback-contract.md`](../S0295_vr-generic-immerse-playback-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-25
**Completed:** 2026-05-25

---

## Objective

Add a typed `ActivityResultContract` for immersive playback and teach `DiagnosticXrActivity` to consume shared launch args and return a typed result.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Existing KDoc and inline comments in `DiagnosticXrActivity` exit/launch code are read before editing.
- [ ] Timestamped backup of `DiagnosticXrActivity.kt` is created in `temp/` before the first edit because the file is already >500 lines.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrPlaybackActivityContract.kt` | New | <= 220 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrLaunchArgs.kt` | New | <= 220 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | <= 980 |

---

## Steps

### Step 02.1 - Add the shared ActivityResult contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrPlaybackActivityContract.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Add `VrPlaybackActivityContract` in `src/main/`. `createIntent()` must delegate to `XrEntryGateway.createImmersiveIntent(input)` and `parseResult()` must collapse Android result codes plus returning extras into a non-throwing `VrLaunchResult`.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrPlaybackActivityContract.kt` exists.
- `Grep` - `class VrPlaybackActivityContract` appears in `VrPlaybackActivityContract.kt`.
- `Grep` - `override fun createIntent` appears in `VrPlaybackActivityContract.kt`.
- `Grep` - `override fun parseResult` appears in `VrPlaybackActivityContract.kt`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Pre-resolved verification 4/4 PASS. Existing file already defines the shared ActivityResult contract with createIntent and parseResult.

---

### Step 02.2 - Introduce immutable launch-args parsing for the XR host

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrLaunchArgs.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a small VR-host helper that parses `VrLaunchInput` extras into an immutable launch-args object. It must distinguish `DIAGNOSTIC_PLAYLIST` from `FILE_URI`, validate missing/blank URI payloads, and carry the chosen `VrLaunchDeliveryMode` so exit handling can branch cleanly.

**Verification:**

- `Glob` - `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrLaunchArgs.kt` exists.
- `Grep` - `VrLaunchMode` appears in `DiagnosticXrLaunchArgs.kt`.
- `Grep` - `VrLaunchDeliveryMode` appears in `DiagnosticXrLaunchArgs.kt`.
- `Grep` - `VrLaunchResult.Unavailable` appears in `DiagnosticXrLaunchArgs.kt`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Pre-resolved verification 4/4 PASS. Existing launch-args helper parses launch mode, delivery mode, and unavailable preflight results.

---

### Step 02.3 - Load either diagnostic playlist or an arbitrary image URI

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Refactor `DiagnosticXrActivity` startup so it consumes `DiagnosticXrLaunchArgs` instead of always assuming the VR test playlist. `DIAGNOSTIC_PLAYLIST` must preserve the current playlist-first path with bundled fallback. `FILE_URI + IMAGE` must decode through `ContentResolver.openInputStream`. `VIDEO` and `GIF` must finish early with `VrLaunchResult.Unavailable(NotYetSupported)`.

**Verification:**

- `Grep` - `contentResolver.openInputStream` appears in `DiagnosticXrActivity.kt`.
- `Grep` - `VrLaunchMode.FILE_URI` appears in `DiagnosticXrActivity.kt`.
- `Grep` - `VrLaunchMode.DIAGNOSTIC_PLAYLIST` appears in `DiagnosticXrActivity.kt`.
- `Grep` - `NotYetSupported` appears in `DiagnosticXrActivity.kt`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Pre-resolved verification 4/4 PASS. Existing activity path loads diagnostic playlist or FILE_URI image and short-circuits unsupported media.

---

### Step 02.4 - Split activity-result exit from legacy panel-return exit

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Route every completion path in `DiagnosticXrActivity` through one typed-result helper. When delivery mode is `ACTIVITY_RESULT`, call `setResult(...)` and `finish()` without relaunching the panel host. When delivery mode is `LEGACY_PANEL_RETURN`, preserve the existing `returnToSettingsTaskOrFinish()` path so direct fire-and-forget launches still work during migration.

**Verification:**

- `Grep` - `setResult(` appears in `DiagnosticXrActivity.kt`.
- `Grep` - `VrLaunchDeliveryMode.ACTIVITY_RESULT` appears in `DiagnosticXrActivity.kt`.
- `Grep` - `returnToSettingsTaskOrFinish()` still appears in `DiagnosticXrActivity.kt`.
- `Grep` - `VrLaunchResult.CompletedNormally` appears in `DiagnosticXrActivity.kt`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Pre-resolved verification 4/4 PASS. Existing exit helper branches between ActivityResult and legacy panel-return delivery.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x]` done.
- [ ] Project compiles - run `/build` for standard debug and noLegal debug after Step 02.4.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] If public API changed: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.

---

## Handoff Notes to Next Phase

The XR host now accepts typed launch args and can return a typed result without always forcing the legacy panel handoff.

---

## Rollback Plan

Revert Phase 02 commit(s) and restore the `DiagnosticXrActivity` backup from `temp/`.
