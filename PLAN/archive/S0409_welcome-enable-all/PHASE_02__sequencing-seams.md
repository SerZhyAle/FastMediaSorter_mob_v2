# Phase 02 - Sequencing seams

**Strategic spec:** [`../S0409_welcome-enable-all.md`](../S0409_welcome-enable-all.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-12
**Completed:** 2026-06-12

---

## Objective

Expose the two reusable seams the orchestrator needs: a completion callback on the permissions
grant-all run, and a launch-for-result default-player variant. No behavior change for existing callers.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerHelper.kt` | Modified | ≤ 440 |

---

## Steps

### Step 02.1 - Add a completion callback to the grant-all run

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `fun runGrantAll(onComplete: () -> Unit)` that stores `onComplete` in a nullable field and then
> runs the existing start logic (rename/reuse the current private `startGrantAllRun()` body). Invoke and
> clear the stored callback exactly where a run ends - the `else` branch of `launchNextSpecialPermission()`
> that sets `grantAllInProgress = false`. Keep the page button working: `btnGrantAll` calls
> `runGrantAll {}` (empty callback). Do not change the special-permission walk or the save/restore state.

**Verification:**

- `Grep` - `fun runGrantAll(` matches once.
- `Grep` - `btnGrantAll.setOnClickListener` present (button still wired).
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - Verification 3/3 PASS. Files: ui/welcome/helpers/WelcomePermissionsManager.kt (+11 LOC). runGrantAll(onComplete) stores callback, fired in the run-finished else branch; button passes empty callback. Dev log recorded.

---

### Step 02.2 - Add a launch-for-result default-player variant

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `fun openChooserOrFallbackForResult(activity: Activity, launcher: ActivityResultLauncher<Intent>,
> mimeType: String)` that mirrors `openChooserOrFallbackFromActivity` but launches the resolved intent
> through `launcher.launch(intent)` instead of `activity.startActivity(intent)`, so the caller gets a
> return callback to advance a sequence (research 01). Reuse `findSampleFile`, the probe path, and the
> default-apps-settings fallback; for the fallback build the same `Intent` and launch it via the same
> `launcher`. Keep `openChooserOrFallbackFromActivity` unchanged for the existing page button.

**Verification:**

- `Grep` - `fun openChooserOrFallbackForResult(` matches once.
- `Grep` - `launcher.launch(` present in the file.
- `Grep` - `fun openChooserOrFallbackFromActivity(` still present (existing caller untouched).

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - Verification 3/3 PASS. Files: ui/settings/helpers/DefaultPlayerHelper.kt (+62 LOC, 465 total). Added openChooserOrFallbackForResult + tryLaunchProbeChooserForResult (launcher-driven); existing fromActivity untouched. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if the public signatures changed.

---

## Handoff Notes to Next Phase

The orchestrator calls `runGrantAll(onComplete)` to chain into the default-player stage, and
`openChooserOrFallbackForResult(activity, launcher, mimeType)` to drive each type's dialog through its
own `ActivityResultLauncher`.

---

## Rollback Plan

Revert phase commit(s) - additive methods only; existing callers unchanged.
