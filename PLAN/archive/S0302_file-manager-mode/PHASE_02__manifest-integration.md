# Phase 02 - Manifest Integration

**Strategic spec:** [`../S0302_file-manager-mode.md`](../S0302_file-manager-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 - UI Terminology Alignment
**Blocks:** Phase 03 - Browse UX
**Steps done:** 1 / 1
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Declare the app as a files management application at the Android OS level using intent category integration in `AndroidManifest.xml`.

---

## Prerequisites

- [ ] Phase 01 completed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 10 |

---

## Steps

### Step 02.1 - Add APP_FILES Category to Manifest

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** start of phase

**Prompt for developer:**

> Open `AndroidManifest.xml` and locate `MainActivity`. Inside its `<intent-filter>` (which contains `action.MAIN` and `category.LAUNCHER`), add `<category android:name="android.intent.category.APP_FILES" />`.
> This categorizes the app as a system-recognized Files/Storage application on Android 10+ (API 29+), allowing it to appear in system selectors for file management actions.

**Verification:**

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 1/1 PASS. Added category android.intent.category.APP_FILES into MainActivity's intent-filter inside AndroidManifest.xml. Dev logs recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] Manifest merges successfully.
- [x] Dev log entries added for modified files.

---

## Handoff Notes to Next Phase

Phase 02 completed. Android OS level integration for App Files category declared. Proceeding to Browse UX phase.

---

## Rollback Plan

Revert the category entry in Manifest. No other dependencies.
