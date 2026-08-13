# Phase 06 - Docs and catalog cleanup

**Strategic spec:** [`../S1428_launcher-shortcut-groups.md`](../S1428_launcher-shortcut-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Record the delivered capability, refresh the generated indexes, and park the ticket for the on-device check that is the only available validation.

---

## Prerequisites

- [x] Phases 01-05 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | n/a |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated | n/a |

---

## Steps

### Step 06.1 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record through `scripts/all_features/add.ps1` for the titled desktop section, with flavors `standard,noLegal`. Derive that flavor pair from the source set the launcher home screen actually mounts (`src/launcherEnabled`), not from a sibling record. Do not edit `docs/FEATURES*.md` here.

**Why:**

Strategic §8 states the capability is user-visible, names the flavor pair, and confirms no existing record covers desktop-cell grouping, so there is nothing to deduplicate against.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - exactly one record carries `S1428`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. `all_features/validate.ps1` exit 0, 678 records; `grep '"spec":"S1428"'` returns exactly 1. Record `launcher.titled-sections-on-the-desktop`, area `Launcher`, flavors `standard,noLegal` - the pair that mounts `src/launcherEnabled`, read from the source set rather than a sibling record. `docs/FEATURES*.md` untouched.

---

### Step 06.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `scripts/catalog_sync.ps1 -Module app_v2` once, then set `role` and `status` on the classes this ticket introduced through `set.ps1`. These indexes are gitignored and regenerated, never committed by hand.

**Why:**

CLAUDE.md section 12 requires the catalog to be regenerated once per ticket when public API changed, and this ticket adds new domain and UI classes.

**Verification:**

- `dev/CATALOG/scripts/query.ps1 -ClassMatches "*Section*"` returns the new classes with a non-empty `role`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 1/1 PASS. `catalog_sync.ps1 -Module app_v2` scanned 2164 files into 2680 records; `set.ps1` gave `role` + `status=new` to the three classes this ticket introduced - `LauncherSectionCatalog`, `LauncherSectionMembership`, `LauncherSectionCollapseManager`. Worth knowing for the next `set.ps1` call: `-Path` takes the **module-relative** path from the catalog record (`com/sza/...`), not the repo-relative one, and `-Description` is refused without `-Function` - a class-level description goes in `-Role`.

---

### Step 06.3 - Close through the facade

**Files:** every file touched by phases 01-05
**Depends on:** Step 06.2

**Prompt for developer:**

> Close through `scripts/post-change.ps1` naming the whole changed set with `-Files` and `-ScopeToFile`, `-ChangeType Mixed`. Read the verdict: only a bare `post-change: PASS` is clean, and exit 2 means the gates could not run rather than that they passed.

**Why:**

CLAUDE.md section 12 requires mechanical closure through the facade and distinguishes "found a defect" from "did not look", and the tree carries other sessions' work, so the scoped form is the one that judges this ticket alone.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS`.

**Status:** `[x]` done, with one recorded residual

**Step Log:**

- 2026-08-09 - Verification 0/1 clean, and the miss is named rather than papered over. `post-change.ps1 -ScopeToFile` was run three times across this session - once per closed phase - naming the whole changed set each time. Every gate PASS on every run except `assert-detekt`, which reports the same single finding each time: `LargeClass:LauncherHomeActivity`. That is **S1541**, parked before this session started, with a recorded experiment proving it predates the ticket. So the exit is 1, not 2 - the gates ran and looked; they found one defect that belongs to another ticket. This ticket fixed the other half of S1541 in passing (`CyclomaticComplexMethod` on `registerAddFlowListeners`, split into `openPickerForCategory`), and S1541's own capture was updated to say so. Because the facade aborts before its mutating steps, the three dev-log rows, the catalog sync and the feature-inventory record were run individually; all exit 0.

---

### Step 06.4 - Park for the on-device check

**Files:** `PLAN/spec-catalog.jsonl` (via script)
**Depends on:** Step 06.3

**Prompt for developer:**

> Insert one `Timber.d("S1428: ..")` probe per changed flow entry, then set `BlockNeedUserTest` with a `-StatusNote` describing what to check: a fresh or reset desktop in both orientations, full-width header at two density multipliers, collapse and expand preserving every position, collapsed-state surviving rotation and restart per orientation, a gadget refused inside the section, TalkBack reading the header as a heading with no offered action, and removal plus restore through the picker.

**Why:**

Strategic §3.3 "Validation level" records that no cheap path exists - the home screen is reachable only from inside the app after accepting the system Home-app choice - and §6.6 means the section appears only on a fresh or reset desktop, so the note must say so or the checker will look at the wrong desktop.

**Verification:**

- `select.ps1 -Id S1428 -Format json` shows `BlockNeedUserTest` with a non-empty `statusNote`.
- `Grep` - at least one `Timber.d("S1428:` exists in `.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. `select.ps1 -Id S1428` reports `BlockNeedUserTest` with a non-empty status note, mirrored into the spec header. Five probes, one per changed flow entry, not one per edit: seeding (`SeedLauncherDesktopUseCase`, item and header counts), render fold (`LauncherCellViewBinder.bind`, folded/drawn/total and edit mode), the header tap (`LauncherSectionCollapseManager.toggle`, section and new state), the gadget refusal (`LauncherDesktopRepositoryImpl.coversHeaderRow`, which all three placement paths share, so one probe covers add, move and resize) and the picker restore (`LauncherHomeActivity.onSectionChosen`). Two of the five are line-wrapped, so a single-line `grep 'Timber.d("S1428:'` finds only three - the multiline form is the one that counts. The probes went in **before** the final `.\a.ps1 dq`, so one build validated the implementation and the probes together, and the status was flipped before the gate ran, which is what lets `assert-no-ticket-logs` accept them (`expected: 0 | actual: 0`, 144 allowed probes).

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [~] `post-change: PASS` **not** recorded - exit 1 on one finding, `LargeClass:LauncherHomeActivity`, which is S1541's parked debt and not this ticket's. Every other gate PASS. See step 06.3.
- [x] Ticket sits at `BlockNeedUserTest` with five probes in place and a status note naming what to check.

No phase-boundary audit: this phase's `Files Touched` is the feature inventory and the regenerated catalog,
plus the five temporary probes, which carry no logic. The audits that matter were run at the boundaries of
phases 03 and 05 and are recorded there.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The probes come out when the ticket leaves `BlockNeedUserTest`, never before.

---

## Rollback Plan

Revert the phase commits in reverse order. The only irreversible artefacts are the feature-inventory record and the changelog rows, both append-only and harmless if the code is reverted.
