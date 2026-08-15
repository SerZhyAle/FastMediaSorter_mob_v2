# Phase 04 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0173_refactor-playback-position-persistence.md`](../S0173_refactor-playback-position-persistence.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** —
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Regenerate the class catalog for the `app_v2` module and record dev-log entries for all files touched across phases 01–03.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified | n/a |
| `dev/CATALOG/app_v2.md` | Modified | n/a |
| `dev/CHANGELOG.md` | Modified | n/a |

---

## Steps

### Step 4.1 — Regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run the following commands in order:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Then set roles for the two new classes:
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class PositionSaveLoop -Role "utility" -Status "active"
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class PlaybackPositionRestorer -Role "utility" -Status "active"
> ```
> Re-run `render.ps1` after the `set.ps1` calls:
> ```powershell
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```

**Verification:**

- `Grep` — `PositionSaveLoop` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `PlaybackPositionRestorer` present in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 2/2 PASS. Catalog regenerated, PositionSaveLoop and PlaybackPositionRestorer roles set.

---

### Step 4.2 — Record dev log entries

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 4.1

**Prompt for developer:**

> Run one `add_to_dev_log.ps1` call per modified file (phases 01–03 + catalog files):
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PositionSaveLoop.kt" "S0173" "Add PositionSaveLoop — standalone periodic save-loop utility"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionRestorer.kt" "S0173" "Add PlaybackPositionRestorer — suspend restore-and-notify utility"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt" "S0173" "Replace positionSaveRunnable/lastSavedPosition with positionSaveLoop field"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlaybackPositionHelper.kt" "S0173" "Delegate save-loop to PositionSaveLoop; formatTime delegates to PlaybackPositionRestorer"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt" "S0173" "Replace private save-loop with PositionSaveLoop; add serviceScope"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt" "S0173" "SFTP restore via PlaybackPositionRestorer; cloud branch adds save+restore; remove formatTimeMs"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "S0173" "Catalog regen after adding PositionSaveLoop and PlaybackPositionRestorer"
> ```

**Verification:**

- `Grep` — `S0173` present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 1/1 PASS. 14 S0173 entries in CHANGELOG.md.

---

## Phase Done Criteria

- [ ] Every `Step 4.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `PositionSaveLoop` and `PlaybackPositionRestorer` visible in `dev/CATALOG/app_v2.md`.
- [ ] `dev/CHANGELOG.md` contains S0173 entries for all 6 source files.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) — catalog and changelog are regeneratable; no code or data affected.
