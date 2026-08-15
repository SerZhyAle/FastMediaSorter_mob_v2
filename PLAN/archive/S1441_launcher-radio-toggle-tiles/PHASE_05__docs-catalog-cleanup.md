# Phase 05 - Docs, catalog and closure

**Strategic spec:** [`../S1441_launcher-radio-toggle-tiles.md`](../S1441_launcher-radio-toggle-tiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Record the delivered capability, refresh the generated indexes the two new drawables and the new classes affect, and close the ticket through the facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree carries every code change of Phases 01-04.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Appended via script | 1 record |
| `docs/icons/*` | Regenerated | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 05.1 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`

**Depends on:** - start of phase

**Prompt for developer:**

> Append one record via `pwsh -NoProfile -File scripts/all_features/add.ps1` with `-Spec S1441`: the launcher's
> Wi-Fi and Bluetooth tiles now try to switch the radio directly and open the system screen only when the
> platform refuses, and their icon shows the current state. Read `-Flavors` off `docs/FLAVOR_MATRIX.md` -
> the toggle ships where `src/networkMonitor` is mounted, which is the `SUPPORT_NETWORK_MONITOR` row, not the
> `SUPPORT_LAUNCHER` one; check the grid rather than assuming the two agree.
>
> Settings reference is untouched: strategic §8 records that no setting is added.

**Why:**

Strategic §8 states the ticket is a noticeable improvement to an existing capability and instructs that the
record be added to the inventory after implementation.

**Verification:**

- `Grep` - `S1441` matches at least once in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. Record `launcher.radio-toggle-tiles` added with `-Spec S1441`, `-Flavors "standard,noLegal"` read off `docs/FLAVOR_MATRIX.md` row `SUPPORT_NETWORK_MONITOR` (line 43), which is narrower than `SUPPORT_LAUNCHER` (line 25) exactly as the prompt warned. `validate.ps1` exit 0, 669 records. Dev log recorded with step 05.2.

---

### Step 05.2 - Close through the facade

**Files:** `dev/CHANGELOG.md`, `dev/CATALOG/app_v2.jsonl`, `docs/icons/*`

**Depends on:** Step 05.1

**Prompt for developer:**

> Run `scripts/post-change.ps1` once over the whole changed set with `-ScopeToFile` and `-ChangeType Mixed`,
> naming every source file the ticket touched across Phases 01-04. The icon-inventory gate runs inside it and
> will demand the two new drawables be inventoried - regenerate rather than hand-editing the inventory, and read
> the closing verdict: only a bare `post-change: PASS` is clean.
>
> `-Files` writes a changelog row for the first file only, so add a dev-log line by hand for each remaining file.

**Why:**

CLAUDE.md section 12 requires mechanical closure through the facade, and Rule 20 requires the generated indexes
to move in the same change as the assets they describe.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS` (or `PASS WITH ADVISORIES` with each advisory named and judged).
- `Grep` - `dev/CHANGELOG.md` carries a row for every file listed in Phases 01-04 "Files Touched".
- `Grep` - `RadioControlContract` appears in `dev/CATALOG/app_v2.jsonl` after the sync.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. `post-change.ps1 -ChangeType Mixed -ScopeToFile` over all 18 files exit 0, bare `post-change: PASS`, no advisories. `dev/CHANGELOG.md` carries at least one row for every file named in Phases 01-04. `RadioControlContract` appears 4x in `dev/CATALOG/app_v2.jsonl`. The icon-inventory gate reported SKIP, not a demand: the two new drawables are launcher tile glyphs, not settings icons, so `docs/icons/*` needs no regeneration - the step's Files Touched row anticipated a gate that does not fire here.
- 2026-08-08 - The prompt's note that `-Files` writes a changelog row for the first file only is stale as of 2026-08-08: the facade now writes one row naming the whole set (`[set of 18: ..]`), so no hand-written dev-log lines were needed.
- 2026-08-08 - First closure run FAILED on `assert-detekt` with a `ReturnCount` finding in `LaunchAppLaunchPanelTileUseCase.launchOsShortcut`, landed by Phase 03 and invisible to that phase's own single-file scope - the union scope is what surfaced it (the gate defect is already ticketed as S1501). Extracted `systemSurfaceFor` to bring the function to two returns, `.\a.ps1 fk` exit 0, re-ran the closure to a clean PASS.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, 2026-08-08, re-run after the `systemSurfaceFor` extraction.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `post-change.ps1`.
- [x] Phase-boundary audit skipped by rule - documentation and generated indexes only.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit and re-run the generators - every file here is generated from the code that Phases 01-04
landed.
