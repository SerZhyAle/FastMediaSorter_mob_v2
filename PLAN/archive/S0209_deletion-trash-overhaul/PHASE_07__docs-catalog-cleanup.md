# Phase 07 — docs, catalog, functionality log

**Strategic spec:** [`../S0209_deletion-trash-overhaul.md`](../S0209_deletion-trash-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05, 06
**Blocks:** /spec-check
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Final phase. Synchronise catalog, feature docs, functionality log, and changelog with the full set of changes from Phases 01–06.

---

## Prerequisites

- [ ] All previous phases ✅ Done.
- [ ] Working tree clean or on feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Generated | — |
| `dev/CATALOG/app_v2.md` | Generated | — |
| `docs/FEATURES.md` | Modified | +1..2 lines |
| `docs/FEATURES_RU.md` | Modified | +1..2 lines |
| `docs/FEATURES_UK.md` | Modified | +1..2 lines |
| `dev/CHANGELOG.md` | Append via script | — |
| `dev/FUNCTIONALITY.log` | Append via script | — |

---

## Steps

### Step 07.1 — Final catalog scan and render

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** all prior phases

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. Confirm all new classes have `role` and `status` set: `TrashFolderContract` (`domain-utility`, `Active`), `TrashRenameUnavailableException` (`domain-exception`, `Active`). For files that became dead-code-free (`CleanupTrashUseCase` if kept, `LocalDeleteFileOperation` after consolidation), confirm role/status reflect their new state.

**Verification:**

- `expected: scan.ps1 exit 0 | actual: <observed>`.
- `expected: render.ps1 exit 0 | actual: <observed>`.
- `Grep -n TrashFolderContract dev/CATALOG/app_v2.jsonl` returns ≥ 1.
- `Grep -n TrashRenameUnavailableException dev/CATALOG/app_v2.jsonl` returns ≥ 1.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Final `scan.ps1 -Module app_v2` and `render.ps1 -Module app_v2` completed successfully.
- 2026-05-15 — `TrashFolderContract` is present in the refreshed catalog. `TrashRenameUnavailableException` role/status set via `set.ps1 -Path "strategy/LocalOperationStrategy.kt" -Role "domain-exception" -Status "new"`.
- 2026-05-15 — Post-fix re-scan and render after `TrashMetadata`, `BrowseFileObserverManager`, `BrowseUndoManager` changes: `scan.ps1 exit 0 | actual: exit 0`, `render.ps1 exit 0 | actual: exit 0`.

---

### Step 07.2 — `docs/FEATURES*.md` update for new Trash TTL behaviour

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** all prior phases

**Prompt for developer:**

> Locate the existing Trash entry in each file (the feature already exists per strategic §8). Append one sentence describing the new visible contract: "After deletion to Trash, files remain recoverable for up to 5 minutes; a background task then permanently removes them." Mirror the wording in RU and UK. Compliance with `docs/COMMUNICATION_POLICY.md` §6 mandatory. Do not introduce a new feature heading — this is an addition to the existing entry. Skip this step if the Trash entry already states a TTL with the same value.

**Verification:**

- `Grep -n "5 minutes" docs/FEATURES.md` returns ≥ 1 inside the Trash section (or equivalent if the existing language uses different phrasing).
- `Grep -n "5 минут" docs/FEATURES_RU.md` returns ≥ 1.
- `Grep -n "5 хвилин" docs/FEATURES_UK.md` returns ≥ 1.
- Strings/sentences pass §6 tone checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Updated the existing Trash bullet in `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md` to document the 5-minute recoverable window before background cleanup removes items permanently.

---

### Step 07.3 — Dev changelog and functionality log

**Files:** `dev/CHANGELOG.md` (via script), `dev/FUNCTIONALITY.log` (via script)
**Depends on:** all prior phases

**Prompt for developer:**

> For each file touched across Phases 01–06 that has not yet been recorded, run `.\scripts\add_to_dev_log.ps1 "<path>" "phase-NN" "<description>"`. Then add one functionality log entry summarising the user-visible behaviour change:
> ```powershell
> .\scripts\add_to_functionality_log.ps1 -Id S0209 -Op FIX -Description "Trash now actually releases storage after 5-minute TTL; soft-delete degrades to permanent delete with a notice on devices without MANAGE_MEDIA"
> ```

**Verification:**

- Latest `dev/CHANGELOG.md` block contains an entry for every Phase 01–06 file.
- `Grep -n S0209 dev/FUNCTIONALITY.log` returns ≥ 1.
- `expected: scripts exit 0 | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Added missing dev log entries for the S0209 follow-up files and appended the user-visible summary to `dev/FUNCTIONALITY.log` via `scripts/add_to_functionality_log.ps1`.

---

### Step 07.4 — String locale audit and final compile

**Files:** all files modified in Phases 04 and 06 that touch `strings.xml`
**Depends on:** Step 07.3

**Prompt for developer:**

> Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "delete_trash"` and `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "trash_cleared"`. Resolve any exit-code-1 result by adding the missing key in EN/RU/UK before commit. Then run `/build` for the target variant and confirm green.

**Verification:**

- Both `check_strings_localized.ps1` invocations return exit 0.
- `expected: build exit 0 | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Both localization audits passed: `check_strings_localized.ps1 -KeyPrefix delete_trash` and `-KeyPrefix trash_cleared` returned OK for EN/RU/UK.
- 2026-05-15 — `compileStandardDebugKotlin`: `expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL in 7s` (3 tasks executed, 10 up-to-date).

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `dev/CHANGELOG.md` has an entry for every file touched across the spec.
- [ ] `dev/FUNCTIONALITY.log` has the S0209 entry.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `docs/FEATURES*.md` trilingual entries consistent.

---

## Handoff Notes to Next Phase

Final phase — next action is `/spec-check S0209` to advance status to `Verified`.

---

## Rollback Plan

- Revert the docs/catalog commits only. Code phases (01–06) remain in place; doc state can be re-derived from current code if needed.
