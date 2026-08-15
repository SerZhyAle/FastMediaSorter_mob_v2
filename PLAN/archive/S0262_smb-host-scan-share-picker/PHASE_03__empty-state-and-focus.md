# Phase 03 - Empty state and focus

**Strategic spec:** [`../S0262_smb-host-scan-share-picker.md`](../S0262_smb-host-scan-share-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Turn the "nothing found" outcome into an explicit cancellable UI state and verify focus/accessibility behavior for the SMB picker flow.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] Strategic §6.2 is Resolved.
- [x] The UX decision for cancel-only vs. manual-entry CTA is documented.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt` | Modified | ≤ 120 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 40 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 40 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 40 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Separate empty result from error result

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceNetworkScanCoordinator.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Distinguish "scan completed with zero shares" from "scan failed". Zero shares must enter the empty-state UI path; technical failures must keep the error path.

**Verification:**

- `Grep` - the zero-shares branch is distinct from the `.onFailure` branch.
- `Grep` - `msg_no_shares_found` or its replacement is emitted only for the empty result path.
- `Grep` - `msg_share_scan_failed` remains tied to failure handling.

**Status:** `[x]` done

---

### Step 03.2 - Present a cancellable empty-state dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Show an explicit empty-state dialog when no SMB shares are found. The dialog must include a visible cancel action and must not silently mutate the form. If strings change, ensure they follow `docs/COMMUNICATION_POLICY.md` §6.

**Verification:**

- `Grep` - a dedicated dialog builder exists for the empty-state path in `AddResourceConnectionManager.kt`.
- `Grep` - the dialog defines a negative or neutral cancel button.
- `Grep` - new or updated strings exist in EN, RU, and UK resource files.
- `Grep` - strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done

---

### Step 03.3 - Verify input-mode accessibility

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Check that the picker and empty-state remain reachable and operable with touch, keyboard, D-pad, and mouse. If focus handling needs explicit help, add it without introducing a new screen.

**Verification:**

- `Grep` - no dialog action is reachable only via touch-specific listeners.
- `Grep` - picker selection remains based on standard dialog item actions.
- `Grep` - strategic blocker §6.2 can be marked Resolved in the spec.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Final phase should only update docs, catalog metadata, and spec bookkeeping after the SMB picker behavior is stable.

---

## Rollback Plan

Revert phase commit(s). Restore prior strings if the empty-state copy causes regressions.
