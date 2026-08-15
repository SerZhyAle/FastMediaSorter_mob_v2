# Phase 01 — manifest-leanback

**Strategic spec:** [`../S0081_tv-remote-key-coverage.md`](../S0081_tv-remote-key-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 1 / 1
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Add `android.software.leanback android:required="false"` to the main manifest, expanding Play Store visibility to Android TV set-top boxes without enabling TV mode.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 470 |

---

## Steps

### Step 1.1 — Add leanback feature declaration to AndroidManifest.xml

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/src/main/AndroidManifest.xml`, after the `android.hardware.faketouch` `<uses-feature>` declaration (currently the last `<uses-feature>` tag before the `<supports-screens>` block), add:
> ```xml
> <!-- TV set-top boxes appear in Play Store without forcing leanback mode -->
> <uses-feature android:name="android.software.leanback" android:required="false" />
> ```

**Verification:**

- `Grep` — `android.software.leanback` present in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` — `android:required="false"` on the same line as `android.software.leanback`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: app_v2/src/main/AndroidManifest.xml (+2 lines). Dev log recorded.

---

## Phase Done Criteria

- [ ] Step 1.1 is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/AndroidManifest.xml" "AndroidManifest.xml" "S0081 Phase 01: add android.software.leanback required=false"`.

---

## Handoff Notes to Next Phase

Manifest now declares optional leanback, optional touchscreen, and optional faketouch — all three are required for full Android TV set-top box reach. Phase 02 adds runtime key routing; Phase 03 fixes focus traversal edge behaviour.

---

## Rollback Plan

Revert the single `<uses-feature>` line. No data migration or user-facing surface changed.
