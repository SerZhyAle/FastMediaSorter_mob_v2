# Phase 02 - Camera-OCR compact sizing

**Strategic spec:** [`../S0348_home-widget-icon-refresh.md`](../S0348_home-widget-icon-refresh.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (Camera-OCR layout already icon-only)
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Resize the Camera-OCR widget provider from `2x2` to `1x1` so it becomes a compact action entry, preserving the existing launch `PendingIntent`. No layout change (done in Phase 01), no flow change.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `widget_camera_ocr_translate.xml` is already icon-only.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/xml/widget_camera_ocr_translate_info.xml` | Modified | ≤ 15 |

---

## Steps

### Step 02.1 - Set Camera-OCR provider info to 1x1

**Files:** `app_v2/src/main/res/xml/widget_camera_ocr_translate_info.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Change the Camera-OCR `appwidget-provider` to a `1x1` footprint to match the other compact action widgets (compare `widget_calculator_info.xml`): set `android:minWidth="48dp"`, `android:minHeight="48dp"`, `android:targetCellWidth="1"`, `android:targetCellHeight="1"`, `android:resizeMode="none"`. Keep `updatePeriodMillis="0"`, `initialLayout`, `description`, `widgetCategory="home_screen"`. Add `android:previewImage="@drawable/ic_camera_ocr_translate"` so the picker preview matches the icon surface. Do not change the provider class or its `PendingIntent`.

**Verification:**

- `Grep -n "minWidth=\"48dp\"|minHeight=\"48dp\"|targetCellWidth=\"1\"|targetCellHeight=\"1\"|resizeMode=\"none\""` in the file - expected: all five present | actual: <fill in>.
- `Grep -n "previewImage"` matches once.
- Build: `.\a.ps1 dq` compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS (structural): 5 sizing attrs (48dp/48dp/1/1/none) + previewImage + previewLayout = 7 hits. Build validated jointly at Phase 03 gate (pure resource change; same full build covers it). File: widget_camera_ocr_translate_info.xml.

---

### Step 02.2 - Record existing-instance manual-verification note

**Files:** none (documentation predicate, resolved in Phase 05 release note)
**Depends on:** Step 02.1

**Prompt for developer:**

> Per strategic §6.2.3, a provider-sizing change does not physically shrink an already-placed `2x2` Camera-OCR instance; the launcher keeps the old footprint until the user removes and re-adds it. Note this for the Phase 05 FEATURES/help copy and the device-test checklist: verify (a) a fresh add lands as `1x1`, and (b) an existing placed Camera-OCR still launches the OCR flow. No code change in this step.

**Verification:**

- This note is carried into Phase 05 docs step (`Grep` the Phase 05 file for "re-add" / "fresh add" - expected: present | actual: present - "removed and re-added" + "fresh add" in PHASE_05 step 05.2).

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS: re-add / fresh-add manual-verification note present in PHASE_05 step 05.2 docs prompt. No code change.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - standardDebug + liteDebug + photosDebug BUILD SUCCESSFUL (Phase 03 joint gate).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for the modified info XML.

---

## Handoff Notes to Next Phase

Camera-OCR is now a `1x1` provider. Phase 04 picker will offer it as a pinnable compact widget (subject to flavor manifest gating from Phase 03 - the Camera-OCR receiver is removed in `lite`/`photos`).

---

## Rollback Plan

Revert the single info-XML change - restores the `2x2` footprint. No data migration.
