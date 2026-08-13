# Phase 03 - Standard manifest

**Strategic spec:** [`../S0621_hotfix-standard-gesture-settings.md`](../S0621_hotfix-standard-gesture-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** -
**Steps done:** 2 / 2
**Started:** 2026-06-22
**Completed:** 2026-06-22

**Step Log:** 03.1 PASS - src/standard/AndroidManifest.xml declares SYSTEM_ALERT_WINDOW + FOREGROUND_SERVICE_SPECIAL_USE + OverlayHostService (specialUse + subtype property); no a11y service. 03.2 PASS - `.\a.ps1 dq` BUILD SUCCESSFUL in 1m40s; merged standardDebug manifest: OverlayHostService=1, ScreenCaptureService=1, SYSTEM_ALERT_WINDOW=1, SPECIAL_USE=1, ScreenshotAccessibilityService=0, BIND_ACCESSIBILITY_SERVICE=0.

---

## Objective

Declare the overlay-strip service + the overlay/FGS permissions in the `standard` flavor manifest so `OverlayHostService` is registered and the foreground-service runs. No accessibility service is declared - that omission is the Play-policy safety boundary. The MediaProjection consent activity + capture service + `FOREGROUND_SERVICE_MEDIA_PROJECTION` already arrive on standard from the shared `src/screenCapture/AndroidManifest.xml`.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (standard controller starts `OverlayHostService`).
- [ ] Confirm the base `FOREGROUND_SERVICE` permission is already in `src/main/AndroidManifest.xml` (audio FGS uses it).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/standard/AndroidManifest.xml` | Modified | ≤ 60 |

> Landscape parity: not a layout file - N/A. Only the standard manifest changes; the shared `src/screenCapture/AndroidManifest.xml` and `src/noLegal/AndroidManifest.xml` are NOT edited.

---

## Steps

### Step 03.1 - Declare overlay service + permissions in the standard manifest

**Files:** `app_v2/src/standard/AndroidManifest.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In `src/standard/AndroidManifest.xml` replace the comment that says screencapture is "NOT declared here" with the Play-safe overlay subset. Add:
> - `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`
> - `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />`
> - inside `<application>`: `<service android:name="com.sza.fastmediasorter.screencapture.OverlayHostService" android:exported="false" android:foregroundServiceType="specialUse">` with a child `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="persistent edge overlay strip host for screenshot gesture trigger" />`.
> Mirror the Play-safe subset of `src/noLegal/AndroidManifest.xml`. Do NOT add `ScreenshotAccessibilityService`, its config XML, `BIND_ACCESSIBILITY_SERVICE`, or `REQUEST_INSTALL_PACKAGES`. Do NOT re-declare `ScreenCaptureConsentActivity` / `ScreenCaptureService` / `FOREGROUND_SERVICE_MEDIA_PROJECTION` (they merge in from the shared screenCapture manifest).

**Verification:**

- `Grep` - `OverlayHostService` present in `src/standard/AndroidManifest.xml`.
- `Grep` - `SYSTEM_ALERT_WINDOW` and `FOREGROUND_SERVICE_SPECIAL_USE` both present.
- `Grep` - `AccessibilityService` returns zero hits in `src/standard/AndroidManifest.xml`.

**Status:** `[ ]` not done

---

### Step 03.2 - Build standard; assert merged manifest

**Files:** - (build + merged-manifest inspection)
**Depends on:** Step 03.1

**Prompt for developer:**

> Build standard debug and inspect the merged manifest for the presence of the overlay/capture components and the absence of any accessibility service.

**Verification:**

- `.\a.ps1 dq` (`assembleStandardDebug`) exits 0.
- In `app_v2/build/intermediates/merged_manifests/standardDebug/AndroidManifest.xml`: `Grep` finds `OverlayHostService` AND `ScreenCaptureService`; `Grep` for `ScreenshotAccessibilityService` and `BIND_ACCESSIBILITY_SERVICE` returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] standard manifest declares `SYSTEM_ALERT_WINDOW` + `FOREGROUND_SERVICE_SPECIAL_USE` + `OverlayHostService` (specialUse + subtype property).
- [ ] standard manifest declares no accessibility service.
- [ ] `assembleStandardDebug` green; merged standard manifest has `OverlayHostService` + `ScreenCaptureService`, no a11y service.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

The standard capture flow is fully wired and declared. Phase 04 handles the settings-UI split so the accessibility-shortcut rows do not appear on standard.

---

## Rollback Plan

Restore the original `src/standard/AndroidManifest.xml` comment block (no overlay declarations). No data migration.
