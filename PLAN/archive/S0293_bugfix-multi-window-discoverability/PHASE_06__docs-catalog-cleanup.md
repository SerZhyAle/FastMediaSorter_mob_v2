# Phase 06 - Docs, Catalog, BlockNeedUserTest Probes

**Strategic spec:** [`../S0293_bugfix-multi-window-discoverability.md`](../S0293_bugfix-multi-window-discoverability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-05-23
**Completed:** 2026-05-24

---

## Objective

Final-phase cleanup: regenerate the catalog for `app_v2`, insert per-flow `Timber.d("S0293: ...")` BlockNeedUserTest probes at every changed flow entry (one per changed flow, not per modified line), confirm all dev-log entries are present, and flip the journal status to `BlockNeedUserTest`. `docs/FEATURES*.md` is skipped per strategic §8 ("Без изменений").

---

## Prerequisites

- [ ] Phases 01-05 are all ✅ Done.
- [ ] Project compiles cleanly under `assembleStandardDebug`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/compat/MultiWindowCapabilityDetector.kt` | Modified (probe insertion) | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified (probe insertion) | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified (probe insertion) | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified (probe insertion) | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified (probe insertion) | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified (probe insertion) | unchanged |

---

## Steps

### Step 06.1 - Insert BlockNeedUserTest probes (one per changed flow entry)

**Files:** All six files above.
**Depends on:** - start of phase

**Prompt for developer:**

> Insert exactly one `Timber.d("S0293: <short description>")` line at the entry point of each distinct flow that this spec changed. Six probes total - one per flow, NOT one per modified line:
>
> 1. `MultiWindowCapabilityDetector.isMultiWindowActiveNow(activity)` - at function entry: `Timber.d("S0293: capability check runtime - install-time=${hasInstallTimeMultiWindowSignal(activity)}, deskMode=${(activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_DESK}")`.
> 2. `SettingsRepositoryImpl` settings flow - inside the same DataStore map block where `fileOpsInOverflowMenu` is resolved, once per emission: `Timber.d("S0293: settings emission - fileOpsInOverflowMenu=${preferences[KEY_FILE_OPS_IN_OVERFLOW_MENU]}, capabilityDefault=${MultiWindowCapabilityDetector.defaultFileOpsInOverflowMenu(context)}, freshInstall=$isFreshInstall")`.
> 3. `BrowseManagerInitializer.showPerFileOverflowMenu(anchor, file)` (the function introduced in Phase 03) - at function entry: `Timber.d("S0293: per-file overflow menu opened for ${file.name}")`.
> 4. `CommandPanelController` - inside the portrait branch around the new pinned-visibility line: `Timber.d("S0293: player portrait pin - allowSeparateWindow=$lastKnownAllowSeparateWindow")`. ONE line in EITHER portrait OR big-buttons branch (pick portrait); not both - one flow.
> 5. `BrowseActivity.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)` - at override entry, after `super`: `Timber.d("S0293: browse multi-window mode changed - isInMultiWindowMode=$isInMultiWindowMode")`.
> 6. `PlayerActivity.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)` - at override entry, after `super`: `Timber.d("S0293: player multi-window mode changed - isInMultiWindowMode=$isInMultiWindowMode")`.
>
> Do NOT insert any probes inside `onConfigurationChanged` overrides - they delegate to the same controller method as `onMultiWindowModeChanged`, so the existing probe in the multi-window override is sufficient. Do NOT insert probes at the read sites in Step 05.1's OR-composition - they fire on every recomposition and would flood the log.

**Verification:**

- `Grep -n "Timber\.d\(\"S0293:"` returns exactly SIX hits across the project (one per file in the list above).
- `Grep -n "Timber\.d\(\"S0293:"` on `CommandPanelController.kt` returns exactly ONE hit (not two - confirming only one of portrait/big-buttons branches got the probe).
- `Grep -n "Log\.d\("` on all six modified files returns zero hits (Timber-only invariant maintained).
- Compile check via `/build` (target: `assembleStandardDebug`) - succeeds.

**Status:** `[x] done`

**Step Log:**

- 2026-05-24 - Verification 4/4 PASS. Probes were inserted in earlier phases (Phase 01.3 / 02.1+02.2 / 03.1 / 04.1 / 05.2 / 05.3) at the exact entry points specified. Grep confirms 6 matches across 6 files; CommandPanelController has exactly 1 hit (portrait branch); no `Log.d(` in the six files; `assembleStandardDebug` build exit 0 (APK `FastMediaSorter_standard_debug_v2.60.5242.323-DEBUG.apk`).

---

### Step 06.2 - Regenerate catalog and confirm dev log coverage

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run the catalog sync wrapper for the `app_v2` module:
>
> `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`
>
> Verify that the new methods (`MultiWindowCapabilityDetector.defaultFileOpsInOverflowMenu`, `MultiWindowCapabilityDetector.isMultiWindowActiveNow`, `BrowseManagerInitializer.notifyMultiWindowModeChanged`, `BrowseManagerInitializer.showPerFileOverflowMenu`, `CommandPanelController.notifyMultiWindowModeChanged`) appear in `dev/CATALOG/app_v2.md` under their respective classes.
>
> Then audit the dev changelog - every file in this phase's "Files Touched" table AND every file modified by Phases 01-05 must have at least one entry in `dev/CHANGELOG.md` from this branch. Grep `dev/CHANGELOG.md` for each file name; if any is missing, append it via `.\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"`.

**Verification:**

- `Glob` - `dev/CATALOG/app_v2.jsonl` exists and is newer than the Phase 06.1 commit.
- `Grep` - in `dev/CATALOG/app_v2.md`, `defaultFileOpsInOverflowMenu` appears (new public method registered).
- `Grep` - in `dev/CATALOG/app_v2.md`, `isMultiWindowActiveNow` appears.
- `Grep` - in `dev/CHANGELOG.md`, each modified file path (`MultiWindowCapabilityDetector.kt`, `SettingsRepositoryImpl.kt`, `BrowseManagerInitializer.kt`, `CommandPanelController.kt`, `BrowseActivity.kt`, `PlayerActivity.kt`) appears at least once with a `2026-05-` (or later) date.

**Status:** `[x] done`

**Step Log:**

- 2026-05-24 - Verification 4/4 PASS. `catalog_sync.ps1 -Module app_v2` produced 1423 records into `dev/CATALOG/app_v2.jsonl` + `.md`. Grep confirms `defaultFileOpsInOverflowMenu`, `isMultiWindowActiveNow`, `notifyMultiWindowModeChanged` (twice - one per class), and `showPerFileOverflowMenu` all appear in `dev/CATALOG/app_v2.md`. All six modified files have `2026-05-22`/`2026-05-23` entries in `dev/CHANGELOG.md` from branch `DEBUG-v008`.

---

### Step 06.3 - Flip journal status to `BlockNeedUserTest`

**Files:** `PLAN/spec-catalog.jsonl` (via CLI - never edit by hand)
**Depends on:** Step 06.2

**Prompt for developer:**

> Run the journal mutator to advance S0293 to `BlockNeedUserTest`:
>
> `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0293 -Status BlockNeedUserTest`
>
> Confirm the transition by reading back via:
>
> `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0293 -Format json`
>
> The returned object's `status` field must be `BlockNeedUserTest`. The six `Timber.d("S0293: ...")` probes inserted in Step 06.1 are now active and will fire in logcat during on-device testing. They will be removed in `/spec-check` when the spec transitions out of `BlockNeedUserTest`.

**Verification:**

- Output of `select.ps1 -Id S0293 -Format json` contains `"status":"BlockNeedUserTest"`.
- `Grep -rn "Timber\.d\(\"S0293:" --include="*.kt"` on the project returns exactly six matches (sanity check that the probes survive the catalog regen).

**Status:** `[x] done`

**Step Log:**

- 2026-05-24 - Verification 2/2 PASS. `update.ps1 -Id S0293 -Status BlockNeedUserTest` returned `S0293 In Progress -> BlockNeedUserTest`; `select.ps1` confirms `"status":"BlockNeedUserTest"`. Project-wide `grep -rn 'Timber.d("S0293:' --include='*.kt'` returns exactly 6 matches.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 bd` (target: `assembleStandardDebug`) returned exit 0 with APK `FastMediaSorter_standard_debug_v2.60.5242.323-DEBUG.apk`.
- [x] `Grep` for `TODO(phase-06)` returns zero hits in `app_v2/src/`.
- [x] Dev log entry added for the catalog sync via `.\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "catalog" "Regenerate app_v2 catalog after S0293"` at 2026-05-24 23:25:45.
- [x] Journal `status` for S0293 is `BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After on-device test confirms all four criteria from strategic §11 are met:

1. Run `/spec-check S0293` to advance to `Verified` (the skill will also delete all six `Timber.d("S0293: ...")` probes).
2. Strategic spec status → `Verified`.

---

## Rollback Plan

Revert the phase commit. Probes are non-functional (logging only) - removing them does not change behavior. The journal status transition is reversible via `update.ps1 -Id S0293 -Status Implemented` if the on-device test reveals a regression and the team prefers to keep the implementation but pause testing.
