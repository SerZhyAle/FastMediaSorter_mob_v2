# Phase 02 — Follow-up strategic spec for scan progress UI

**Strategic spec:** [`../S0056_smb-scan-slowness-investigation.md`](../S0056_smb-scan-slowness-investigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Author and register a new strategic specification covering **D1 follow-up** from S0056 §6.2: a UI progress indicator + cancel option for long-running resource scans. This phase produces a `Draft` spec only — implementation is out of scope.

---

## Prerequisites

- [ ] Phase 01 done.
- [ ] `scripts/spec_catalog/insert.ps1` available and working under PowerShell 7.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/Sxxxx_scan-progress-indicator.md` (new id allocated by `insert.ps1`) | New | ≤ 300 |
| `PLAN/spec-catalog.jsonl` | Modified (via `insert.ps1`, never edited by hand) | n/a |
| `PLAN/S0056_smb-scan-slowness-investigation.md` | Modified | unchanged file size |

---

## Steps

### Step 02.1 — Allocate ticket id and author the strategic spec

**Files:** `PLAN/Sxxxx_scan-progress-indicator.md` (new)
**Depends on:** — start of phase

**Prompt for developer:**

> Invoke the `/spec` skill with the topic "scan-progress-indicator". Brief: visible progress UI for resource scans (`% / count / current path`) with a cancel affordance, motivated by S0056 §6.2 where long SMB scans of large trees look like a hang. Source-of-truth callback already exists at [SmbDirectoryScanner.kt:60](../../app_v2/src/main/java/com/sza/fastmediasorter/data/network/helpers/SmbDirectoryScanner.kt#L60). The `/spec` skill will call `insert.ps1` to allocate the next free `Sxxxx`. Do **not** allocate the id manually. Status remains `Draft` after this step.

**Verification:**

- `Glob` returns exactly one new file matching `PLAN/S00??_scan-progress-indicator.md` (post-allocation).
- `pwsh -File scripts/spec_catalog/select.ps1 -Name "scan-progress-indicator" -Format json` returns a record with `status=Draft` and `file=PLAN/Sxxxx_scan-progress-indicator.md`.
- `Grep` inside the new spec for the substring `S0056` returns ≥ 1 hit (back-reference to the parent spec).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Allocated S0068 via insert.ps1; authored strategic spec at PLAN/S0068_scan-progress-indicator.md; status remains Draft per phase prompt. Files: PLAN/S0068_scan-progress-indicator.md (new). Dev log recorded.

---

### Step 02.2 — Add forward-link from S0056 strategic to the new ticket

**Files:** `PLAN/S0056_smb-scan-slowness-investigation.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the strategic spec, append a bullet to §10 ("Связи с другими спеками"): `**Sxxxx** (scan-progress-indicator, Draft) — D1 follow-up: UI progress + cancel.` (Substitute the actual id from 02.1.) Do not change any other section.

**Verification:**

- `Grep -n "scan-progress-indicator"` in `PLAN/S0056_smb-scan-slowness-investigation.md` returns exactly 1 hit, located within the §10 block.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 1/1 PASS. Added S0068 bullet to §10 of strategic spec. Files: PLAN/S0056_smb-scan-slowness-investigation.md (+1 LOC). Dev log recorded.

---

### Step 02.3 — Confirm new ticket appears in journal listing

**Files:** none (read-only verification)
**Depends on:** Step 02.2

**Prompt for developer:**

> Run `pwsh -File scripts/spec_catalog/select.ps1 -Status Draft -Format json` and confirm the new `scan-progress-indicator` record is present.

**Verification:**

- Output of the above command contains a record with `name="scan-progress-indicator"` and `status="Draft"`.
- `Grep -n "scan-progress-indicator"` in `PLAN/spec-catalog.jsonl` returns exactly 1 hit.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS. select.ps1 -Status Draft returned record with id=S0068; spec-catalog.jsonl line 68 contains exactly 1 hit. Files: none. Dev log: none (verification only).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] No code change introduced — phase is documentation-only.
- [x] Dev log entry added for the new spec file via `.\scripts\add_to_dev_log.ps1`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits (only self-referencing criterion line matches; no real markers).

---

## Handoff Notes to Next Phase

The new ticket is `Draft`; on-device verification of S0056 in Phase 03 must not depend on the new ticket landing.

---

## Rollback Plan

Run `pwsh -File scripts/spec_catalog/delete.ps1 -Id <new-id>` (soft-delete to `Archived` — id stays in journal) and remove the §10 bullet added by Step 02.2. The new spec file may remain on disk; ids are never reused.
