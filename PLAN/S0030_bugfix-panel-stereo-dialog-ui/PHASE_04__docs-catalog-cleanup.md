# Phase 04 — docs-catalog-cleanup

**Strategic spec:** [`../S0030_bugfix-panel-stereo-dialog-ui.md`](../S0030_bugfix-panel-stereo-dialog-ui.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Final housekeeping: run catalog scan (no new classes introduced, but two files were modified), update INDEX phase statuses, advance strategic spec to `Implemented`, and run spec-catalog update.

## Files Touched

| File | Change |
|------|--------|
| `PLAN/S0030_bugfix-panel-stereo-dialog-ui/INDEX.md` | Update all phases to ✅ Done |
| `PLAN/S0030_bugfix-panel-stereo-dialog-ui.md` | `Status: Implemented`; add tactical plan link |
| `dev/CATALOG/app_v2.jsonl` | Scan run (updated timestamps for changed files) |
| `dev/CATALOG/app_v2.md` | Render run |

---

## Steps

### Step 4.1 — Catalog scan + render

**Status:** `[ ] not done`
**Depends on:** Phase 01, 02, 03

**Prompt for developer:**
Run catalog scan and render:

```powershell
pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

No new classes were introduced in this spec — only existing files modified. The scan refreshes timestamps and line counts.

**Verification:** Both commands exit 0. `dev/CATALOG/app_v2.md` shows updated `last_modified` for `PlaybackControlDialogFragment.kt`.

---

### Step 4.2 — Update spec-catalog status

**Status:** `[ ] not done`
**Depends on:** 4.1

**Prompt for developer:**
Run:

```powershell
pwsh -File scripts/spec_catalog/update.ps1 -Id S0030 -Status Implemented
```

Then update `PLAN/S0030_bugfix-panel-stereo-dialog-ui.md`: change `Status: Tactical` → `Status: Implemented`.

**Verification:** `pwsh -File scripts/spec_catalog/select.ps1 -Id S0030 -Format json` shows `"status": "Implemented"`.

---

### Step 4.3 — Mark INDEX complete

**Status:** `[ ] not done`
**Depends on:** 4.2

**Prompt for developer:**
Update `PLAN/S0030_bugfix-panel-stereo-dialog-ui/INDEX.md`:

- All phases → ✅ Done
- `Phases: 4 / 4 done`
- `Last updated: 2026-04-29`
- Tick all Completion Gate checkboxes.

**Verification:** No `⬜ Not started` or `🚧 In Progress` left in INDEX.md.
