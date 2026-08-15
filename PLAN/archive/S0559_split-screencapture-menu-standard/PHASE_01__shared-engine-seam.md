# Phase 01 - Shared Engine Seam

**Strategic spec:** [`../S0559_split-screencapture-menu-standard.md`](../S0559_split-screencapture-menu-standard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 5
**Started:** -
**Completed:** -

---

## Objective

Make the confirmable capture engine (`ScreenCaptureConsentActivity` + `ScreenCaptureService`) reachable from the `standard` flavor while keeping the overlay-strip launcher and accessibility silent-capture exclusively in `noLegal`. No UI and no launch trigger for `standard` yet - this phase only relocates code, splits the manifest, and proves both flavors compile.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved (they are).
- [ ] Working tree is clean or on a feature branch.
- [ ] `assembleStandardDebug` and `assembleNoLegalDebug` both build on the current tree (baseline).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` | Moved out (delete here) | - |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | Moved out (delete here) | - |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` | New (moved in) | ≤ 200 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | New (moved in) | ≤ 200 |
| `app_v2/src/screenCapture/AndroidManifest.xml` | New | ≤ 30 |
| `app_v2/src/noLegal/AndroidManifest.xml` | Modified | ≤ 70 |
| `app_v2/build.gradle.kts` | Modified | ≤ 1100 |

> The two moved files keep package `com.sza.fastmediasorter.screencapture`. In `noLegal`, both the `screenCapture` and `noLegal` source sets compile into that package, so the move is source-set only - no package/import churn in callers.
>
> **Flavor placement.** `OverlayHostService` + `ScreenGestureOverlayManager` are the overlay-strip launcher (need `SYSTEM_ALERT_WINDOW` / `specialUse` FGS) and must live in `src/noLegal/java`. The shared confirmable engine stays in `src/screenCapture/java`, which Phase 01 mounts into `standard` as well.

---

## Steps

### Step 01.1 - Move overlay-strip launcher classes to noLegal

**Files:** `app_v2/src/screenCapture/java/.../screencapture/OverlayHostService.kt`, `.../ScreenGestureOverlayManager.kt` (delete), `app_v2/src/noLegal/java/.../screencapture/OverlayHostService.kt`, `.../ScreenGestureOverlayManager.kt` (create)
**Depends on:** - start of phase

**Prompt for developer:**

> Move `OverlayHostService.kt` and `ScreenGestureOverlayManager.kt` from `src/screenCapture/java/com/sza/fastmediasorter/screencapture/` to `src/noLegal/java/com/sza/fastmediasorter/screencapture/`, byte-for-byte, keeping the `com.sza.fastmediasorter.screencapture` package declaration. While moving `OverlayHostService.kt`, correct the stale class/file comment (around the top of the file) that names Play flavours (standard/photos) as using the overlay as their sole capture path - that claim is from the rolled-back S0418 design; the overlay path is now noLegal-only (strategic §6.5). Do not change any other logic. Delete the two originals under `src/screenCapture/`.

**Verification:**

- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` exists.
- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` exists.
- `Glob` - `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` does NOT exist.
- `Grep` - `package com.sza.fastmediasorter.screencapture` present in each moved file.
- `Grep` - in the moved `OverlayHostService.kt`, no comment text containing `standard/photos` (or `Play flavours`) describing it as the capture path remains.

**Status:** `[ ]` not done

---

### Step 01.2 - Confirm the shared engine has no overlay coupling

**Files:** `app_v2/src/screenCapture/java/.../screencapture/ScreenCaptureService.kt`, `.../ScreenCaptureConsentActivity.kt` (read-only verification)
**Depends on:** Step 01.1

**Prompt for developer:**

> Verify the shared engine left in `src/screenCapture/` does not reference the moved classes. Grep `ScreenCaptureService.kt` and `ScreenCaptureConsentActivity.kt` for `OverlayHostService` and `ScreenGestureOverlayManager`. Expected: zero references (the consent activity is launched BY the overlay host, not vice-versa). If any reference exists, STOP - the split needs the referenced symbol lifted to `src/main` first; do not patch around it.

**Verification:**

- `Grep` - `OverlayHostService` returns zero hits in `app_v2/src/screenCapture/java/`.
- `Grep` - `ScreenGestureOverlayManager` returns zero hits in `app_v2/src/screenCapture/java/`.

**Status:** `[ ]` not done

---

### Step 01.3 - Create the shared screenCapture manifest

**Files:** `app_v2/src/screenCapture/AndroidManifest.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `src/screenCapture/AndroidManifest.xml` declaring only the shared confirmable engine: `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />`, the `ScreenCaptureConsentActivity` activity (exported=false, excludeFromRecents=true, noHistory=true, taskAffinity="", theme `@style/Theme.FastMediaSorter.Transparent`), and the `ScreenCaptureService` service (exported=false, foregroundServiceType="mediaProjection"). Copy these three element definitions verbatim from the current `src/noLegal/AndroidManifest.xml`. Do NOT include `OverlayHostService`, the accessibility service, `SYSTEM_ALERT_WINDOW`, or `FOREGROUND_SERVICE_SPECIAL_USE`.

**Verification:**

- `Glob` - `app_v2/src/screenCapture/AndroidManifest.xml` exists.
- `Grep` - `ScreenCaptureConsentActivity` and `ScreenCaptureService` both present.
- `Grep` - `FOREGROUND_SERVICE_MEDIA_PROJECTION` present.
- `Grep` - `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE`, `OverlayHostService`, `AccessibilityService` each return zero hits.

**Status:** `[ ]` not done

---

### Step 01.4 - Trim the noLegal manifest to overlay/a11y only

**Files:** `app_v2/src/noLegal/AndroidManifest.xml`
**Depends on:** Step 01.3

**Prompt for developer:**

> In `src/noLegal/AndroidManifest.xml` remove the now-shared declarations that move to the screenCapture manifest: the `ScreenCaptureConsentActivity` `<activity>`, the `ScreenCaptureService` `<service>`, and the `FOREGROUND_SERVICE_MEDIA_PROJECTION` `<uses-permission>`. Keep `OverlayHostService` (specialUse), the `ScreenshotAccessibilityService`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE`, and `REQUEST_INSTALL_PACKAGES`. The screenCapture manifest (injected into noLegal in Step 01.5) supplies the removed entries.

**Verification:**

- `Grep` - in `src/noLegal/AndroidManifest.xml`: `ScreenCaptureConsentActivity` and `ScreenCaptureService` return zero hits.
- `Grep` - `OverlayHostService`, `ScreenshotAccessibilityService`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE` all still present.

**Status:** `[ ]` not done

---

### Step 01.5 - Mount screenCapture into standard and inject the shared manifest

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.4

**Prompt for developer:**

> Two edits in `app_v2/build.gradle.kts`. (1) In `sourceSets { getByName("standard") { .. } }` add `kotlin.directories.add("src/screenCapture/java")` and `res.directories.add("src/screenCapture/res")`, with a one-line comment that the confirmable capture engine is now shared with the store flavor (S0559). (2) In the `androidComponents { onVariants { .. } }` block, alongside the existing `if (flavorName == "noLegal")` static-manifest injection, inject the screenCapture manifest into both store-and-sideload capture flavors: `if (flavorName == "standard" || flavorName == "noLegal") { variant.sources.manifests.addStaticManifestFile("src/screenCapture/AndroidManifest.xml") }`. Do not alter the existing noLegal `src/vr/AndroidManifest.xml` srcFile or its `src/noLegal/AndroidManifest.xml` injection.

**Verification:**

- `Grep` - in `build.gradle.kts`, `src/screenCapture/java` appears inside the `standard` source-set block (now in both `standard` and `noLegal`).
- `Grep` - `addStaticManifestFile("src/screenCapture/AndroidManifest.xml")` present.
- `Grep` - the `flavorName == "standard"` guard wrapping the screenCapture manifest injection is present.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - `assembleStandardDebug` AND `assembleNoLegalDebug` both succeed (run `/build`; the noLegal build needs `-Pchaquopy.enabled=true` per `build.gradle.kts`). The standard build proves the engine is reachable and store-safe; the noLegal build proves the overlay/a11y path still resolves.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (class source-set moves) - deferred to Phase 04 batch.

---

## Handoff Notes to Next Phase

- The `standard` classpath now contains `ScreenCaptureConsentActivity` + `ScreenCaptureService` and the `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission, with no overlay/`SYSTEM_ALERT_WINDOW`/a11y code or permissions.
- Phase 02 adds the `MenuScreenshotLauncher` seam that starts `ScreenCaptureConsentActivity` without referencing it from `src/main`.

---

## Rollback Plan

Revert the phase commit(s): restore the two moved files to `src/screenCapture/`, delete `src/screenCapture/AndroidManifest.xml`, restore the removed `src/noLegal/AndroidManifest.xml` entries, and revert the `build.gradle.kts` source-set + manifest-injection edits. No data migration or user-facing surface changed.
