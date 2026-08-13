# Phase 03 — docs-catalog-cleanup

**Strategic spec:** [`../S0146_bugfix-player-stale-initial-file-path-reload.md`](../S0146_bugfix-player-stale-initial-file-path-reload.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Regenerate the class catalog for `app_v2` (PlayerMediaFilesLoader was modified), log all changed files to `dev/CHANGELOG.md`, and confirm no features doc update is needed.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified | — |
| `dev/CATALOG/app_v2.md` | Modified | — |
| `dev/CHANGELOG.md` (via script only) | Modified | — |

---

## Steps

### Step 03.1 — Regenerate app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** start of phase

**Prompt for developer:**

> Run catalog scan and render for `app_v2`:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```

**Verification:**

- [x] Both commands exit with code 0.
- [x] `dev/CATALOG/app_v2.jsonl` has a fresh `updated` timestamp on the `PlayerMediaFilesLoader` entry.

---

### Step 03.2 — Log all changed files to dev/CHANGELOG.md

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`)
**Depends on:** Step 03.1

**Prompt for developer:**

> Run the dev-log script for each modified file:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt" "PlayerMediaFilesLoader" "S0146: fix stale initial path scope mismatch + smart position fallback"
> .\scripts\add_to_dev_log.ps1 "PLAN/S0146_bugfix-player-stale-initial-file-path-reload/PHASE_01__cache-scope-and-stale-path.md" "spec-all" "Phase 01 done: cache-scope-and-stale-path"
> .\scripts\add_to_dev_log.ps1 "PLAN/S0146_bugfix-player-stale-initial-file-path-reload/PHASE_02__position-fallback.md" "spec-all" "Phase 02 done: position-fallback"
> ```

**Verification:**

- [x] Each command exits with code 0.
- [x] `dev/CHANGELOG.md` contains a new row for `PlayerMediaFilesLoader.kt`.

---

### Step 03.3 — Confirm no FEATURES doc update needed

**Files:** `docs/FEATURES.md` (read-only check)
**Depends on:** —

**Prompt for developer:**

> Strategic spec §8 confirms no user-facing feature entry is required (internal position restore behaviour only). No changes to `docs/FEATURES.md`, `docs/FEATURES_RU.md`, or `docs/FEATURES_UK.md` are needed.

**Verification:**

- [x] `docs/FEATURES.md` is unchanged.
- [x] Run `/spec-check S0146` to confirm all criteria pass.

---

## Phase Done Criteria

- [x] All three steps `[x]` done.
- [x] `dev/CATALOG/app_v2.md` reflects updated `PlayerMediaFilesLoader` entry.
- [x] `dev/CHANGELOG.md` has entries for the modified `.kt` file and the two phase files.
