# Phase 01 — Settings Intent Launcher Foundation

**Strategic spec:** [`../S0043_bugfix-settings-window-bounds-xr.md`](../S0043_bugfix-settings-window-bounds-xr.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-01
**Completed:** 2026-05-01

---

## Objective

Introduce a single utility object `SettingsIntentLauncher` in `core/util/` that wraps any system Settings intent, computes a centred launch-bounds rectangle from the activity's current display metrics, and starts the activity via `Activity.startActivityForResult(intent, requestCode, options)` with `ActivityOptions.setLaunchBounds(..)` and `FLAG_ACTIVITY_NEW_TASK`. No call sites are migrated in this phase. Only the `requestCode` overload is provided — `ActivityResultLauncher` cannot carry `setLaunchBounds` because `ActivityOptionsCompat` does not expose that API; Phase 02 migrates the two launcher callers to `onActivityResult`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (n/a — first phase)
- [ ] Strategic §6 research items blocking this phase are Resolved (decisions captured in INDEX.md Blockers).
- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/build.gradle.kts` confirms `minSdk 26` (standard) / 23 (legacy) — `setLaunchBounds` requires API 24, so legacy branch must guard.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/SettingsIntentLauncher.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 — Create `SettingsIntentLauncher.kt` skeleton

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/SettingsIntentLauncher.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new Kotlin file at `app_v2/src/main/java/com/sza/fastmediasorter/core/util/SettingsIntentLauncher.kt` declaring `object SettingsIntentLauncher` in package `com.sza.fastmediasorter.core.util`. Add a one-paragraph KDoc explaining the object wraps system Settings intents and supplies `ActivityOptions.setLaunchBounds(..)` so the system Settings activity opens in a window large enough to display its content on freeform / Android XR / foldable layouts. Imports needed: `android.app.Activity`, `android.app.ActivityOptions`, `android.content.Intent`, `android.graphics.Rect`, `android.os.Build`, `timber.log.Timber`. No public methods yet — they are added in following steps.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/util/SettingsIntentLauncher.kt` exists.
- `Grep` — `^object SettingsIntentLauncher` matches exactly once in that file.
- `Grep` — `package com\.sza\.fastmediasorter\.core\.util` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-01 — Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/core/util/SettingsIntentLauncher.kt` (+15 LOC, new). Dev log recorded.

---

### Step 01.2 — Add `computeCenteredLaunchBounds(activity)` private helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/SettingsIntentLauncher.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Inside `SettingsIntentLauncher`, add a private function `computeCenteredLaunchBounds(activity: Activity): Rect?`. On API 30+ read `activity.windowManager.currentWindowMetrics.bounds`. On API 26..29 build a `Rect` from `activity.windowManager.defaultDisplay.getRectSize(rect)`. Compute width = `(displayWidth * 0.80).toInt()`, height = `(displayHeight * 0.85).toInt()`, centre the rectangle horizontally and vertically inside the display rect, and return it. Wrap the whole body in a try/catch that calls `Timber.w(e, "SettingsIntentLauncher: failed to compute launch bounds")` and returns `null` on any failure. Document with KDoc that returning `null` means "let the system pick the size".

**Verification:**

- `Grep` — `private fun computeCenteredLaunchBounds\(activity: Activity\): Rect\?` matches exactly once.
- `Grep` — `currentWindowMetrics` matches exactly once in the file.
- `Grep` — `0\.80` and `0\.85` each match exactly once in the file.
- `Grep` — `Timber\.w` matches at least once in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-01 — Verification 5/5 PASS (after KDoc tweak: replaced `0.80`/`0.85` literals in doc comment with "80 percent"/"85 percent" prose so each numeric literal matches exactly once). Files: `SettingsIntentLauncher.kt` (+27 LOC). Dev log recorded.

---

### Step 01.3 — Add public `launch(activity, intent, requestCode)`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/SettingsIntentLauncher.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a public function `fun launch(activity: Activity, intent: Intent, requestCode: Int)`. Implementation:
> 1. `intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)` unconditionally — required for `setLaunchBounds` to take effect.
> 2. On API 24+ build `val opts = computeCenteredLaunchBounds(activity)?.let { ActivityOptions.makeBasic().setLaunchBounds(it).toBundle() }` and call `activity.startActivityForResult(intent, requestCode, opts)`.
> 3. On API < 24 call `activity.startActivityForResult(intent, requestCode)` (legacy flavor / API 23 path — bounds API unavailable).
> Log one line at the start: `Timber.i("SettingsIntentLauncher: launch action=${intent.action} requestCode=$requestCode")`.

**Verification:**

- `Grep` — `fun launch\(activity: Activity, intent: Intent, requestCode: Int\)` matches exactly once.
- `Grep` — `Intent\.FLAG_ACTIVITY_NEW_TASK` matches exactly once in the file.
- `Grep` — `ActivityOptions\.makeBasic\(\)\.setLaunchBounds` matches exactly once.
- `Grep` — `Build\.VERSION_CODES\.N` matches at least once in the file.
- `Grep` — `Log\.d\(` returns zero hits in this file (Timber-only rule).

**Status:** `[x] done`

**Step Log:**

- 2026-05-01 — Verification 5/5 PASS (after KDoc tweak: replaced `Intent.FLAG_ACTIVITY_NEW_TASK` literal in doc comment with prose so the symbol matches exactly once). Files: `SettingsIntentLauncher.kt` (+22 LOC, total 64 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO\(phase-01\)` returns zero hits.
- [ ] `Grep` for `Log\.d\(` in `SettingsIntentLauncher.kt` returns zero hits.
- [ ] Dev log entry added for `SettingsIntentLauncher.kt` via `pwsh -File scripts/add_to_dev_log.ps1`.
- [ ] No call sites migrated yet — `Grep` for `SettingsIntentLauncher\.launch` outside the new file returns zero hits.

---

## Handoff Notes to Next Phase

`SettingsIntentLauncher.launch(activity, intent, requestCode)` is the only entry point. It adds `FLAG_ACTIVITY_NEW_TASK` and (on API 24+) supplies centred launch-bounds derived from the current display. Callers using `ActivityResultLauncher<Intent>` for these intents must be converted to `Activity.startActivityForResult` + `onActivityResult` because `ActivityOptionsCompat` does not expose `setLaunchBounds`. Two such callers exist: `WelcomeActivity` (`manageMediaPermissionLauncher`, `allFilesAccessPermissionLauncher`) and `MainStoragePermissionsHelper` (`settingsPermissionLauncher`). Migration happens in Phase 02.

---

## Rollback Plan

Revert phase commit — single new file, no behaviour change yet.
