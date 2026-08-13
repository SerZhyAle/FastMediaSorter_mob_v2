# Phase 03 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0180_standalone-player-file-info-button.md`](../S0180_standalone-player-file-info-button.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** nothing — final phase
**Steps done:** 2 / 2
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Regenerate the class catalog, finalize dev log entries, and confirm no stale references remain. No code changes in this phase.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] Project compiles cleanly.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (generated) | — |

---

## Steps

### Step 03.1 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run the catalog scan and render for `app_v2`:
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Commit `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` together with the Phase 01–02 code changes (or as a follow-up commit if phases were committed separately).

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and has a modification timestamp newer than `StandalonePlayerActivity.kt`.
- `Grep` — `StandalonePlayerActivity` entry in `dev/CATALOG/app_v2.jsonl` reflects the current file state (presence of `showFileInfo` — check scan output).

**Status:** `[ ]` not done

---

### Step 03.2 — Final dev log entries

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 03.1

**Prompt for developer:**

> Ensure a dev log entry exists for every file modified across all phases. Run for any missing entries:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt" "S0180" "Restore btnInfoCmd to FileInfoDialog; relocate Open-in-FMS to overflow menu"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/menu/overflow_menu_player.xml" "S0180" "Add menu_open_in_fms item (standalone-only visible)"
> .\scripts\add_to_dev_log.ps1 "PLAN/S0180_standalone-player-file-info-button/INDEX.md" "spec-tech" "Tactical plan complete"
> ```
> Skip any entry that was already added during the respective phase.

**Verification:**

- `Grep` — `S0180` appears at least twice in `dev/CHANGELOG.md` (one entry per modified source file).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every Step 03.* above is `[x] done`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] INDEX.md `Phases:` counter updated to `3 / 3 done`, `Status:` flipped to `Done`.
- [ ] Run `/spec-check S0180` to advance strategic spec to `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Catalog and dev log are append-only artifacts — no rollback needed. If needed, regenerate catalog from scratch via `scan.ps1` + `render.ps1`.
