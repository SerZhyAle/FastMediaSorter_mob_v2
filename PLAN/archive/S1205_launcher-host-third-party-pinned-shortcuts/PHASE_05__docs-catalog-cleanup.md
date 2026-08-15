# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1205_launcher-host-third-party-pinned-shortcuts.md`](../S1205_launcher-host-third-party-pinned-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Register the delivered capability and refresh the generated class catalog.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> **Flavor placement.** The capability record declares `standard` and `noLegal` only - the two flavors whose `SUPPORT_LAUNCHER` is `[+]` in `docs/FLAVOR_MATRIX.md` and the only ones that mount `src/launcherEnabled`.

---

## Steps

### Step 05.1 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record through `pwsh -NoProfile -File scripts/all_features/add.ps1` describing, in English, that in launcher mode the app accepts a pinned-shortcut request from any other application and places it on the desktop. Take the flavor list from `docs/FLAVOR_MATRIX.md` row `SUPPORT_LAUNCHER`, never from memory. Do not hand-edit the file and do not touch `docs/FEATURES*.md`.

**Why:**

not stated in strategic spec

**Verification:**

- `Grep` - `S1205` matches at least once in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - expected exit 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

---

### Step 05.2 - Regenerate the class catalog and close the ticket mechanically

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1` once for the whole change set, naming every touched source file via `-Files` with `-ScopeToFile` and `-ChangeType Mixed`. Set `role` and `status` for the two new classes - `AcceptPinnedShortcutUseCase` and `LauncherPinRequestActivity` - via `dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "<file>"`, and give the activity `-NoFlavors "lite,photos,legacy,vr"` so its launcher-only placement is searchable.

**Why:**

not stated in strategic spec

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*AcceptPinnedShortcutUseCase*"` returns one record with a non-empty `role`.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*LauncherPinRequestActivity*"` returns one record with a non-empty `role`.
- `post-change.ps1` printed `post-change: PASS` - expected exit 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

---

## Step Log

- 2026-08-06 - Step 05.1 PASS. The capability record landed through `close-and-log.ps1 -FuncOp ADD` rather than a separate `add.ps1` call - same script underneath. `grep S1205 docs/ALL_FEATURES.jsonl` = 1 record, area `Launcher`, flavors `["standard","noLegal"]` taken from the generated `SUPPORT_LAUNCHER` row, never from memory.
- 2026-08-06 - Step 05.2 PASS. `post-change: PASS (Mixed)` - expected: 0 | actual: 0, every gate green including `activity-logic`. `set.ps1` corrected in use: `-Role` is a free-text description and `-Status` takes `new|tested|legacy|todo|unknown`, not `active`. Catalog now carries `AcceptPinnedShortcutUseCase` (domain), `LauncherPinRequestManager` + `LauncherPinRequestOutcome` and `LauncherPinRequestActivity` (ui), the last three with `noFlavors=[lite,photos,legacy,vr]`.
- 2026-08-06 - `.\a.ps1 dq` - expected: 0 | actual: 0. `hiltJavaCompileStandardDebug` executed and the APK packaged, which is what actually validates the new `@Inject` graph - `fk` alone never would have.
- 2026-08-06 - Trap hit and recorded: the same build launched through the Bash tool died on `JAVA_HOME is set to an invalid directory` and the background task still reported exit 0. Gradle goes through the PowerShell tool here, never Bash.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - skipped by contract: this phase's Files Touched are generated inventory and catalog artifacts, no source.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
