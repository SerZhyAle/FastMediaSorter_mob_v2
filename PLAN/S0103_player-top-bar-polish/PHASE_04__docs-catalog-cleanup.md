# Phase 04 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0103_player-top-bar-polish.md`](../S0103_player-top-bar-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Regenerate the app_v2 catalog after all Kotlin changes, remove all S0103 debug Timber tags from modified files, and record dev log entries for every changed file.

---

## Prerequisites

- [ ] Phases 01, 02, 03 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-generated) | — |
| All `.kt` files modified in phases 01–03 | Modified (tag removal) | — |

---

## Steps

### Step 04.1 — Remove all S0103 Timber debug tags

**Files:** All `.kt` files that received `Timber.d("S0103: ...")` tags in phases 01–03.
**Depends on:** — start of phase (all code phases done)

**Prompt for developer:**

> Grep for `Timber.d("S0103:` across all `.kt` files. Remove every matching line. Do not remove any other Timber calls.

**Verification:**

- `Grep` — `Timber\.d\("S0103:` returns zero hits across all `.kt` files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification PASS: zero S0103 Timber tags in kt files.

---

### Step 04.2 — Regenerate app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run catalog scan and render for app_v2:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> If any new classes were added (`btnSleepTimerCmd`-related), fill in `role` + `status` via `set.ps1` per `dev/CATALOG/README.md`.

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and has a modification timestamp newer than Phase 01 start.
- `Glob` — `dev/CATALOG/app_v2.md` exists and has a modification timestamp newer than Phase 01 start.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — scan.ps1 and render.ps1 run; app_v2.jsonl and app_v2.md updated (928 records).

---

### Step 04.3 — Dev log entries for all changed files

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 04.2

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` once for each file changed across all phases. Minimum entries:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt" "S0103" "Fix audio exclusions for fullscreen/edit; SLIDESHOW removed from adaptive set; SLEEP_TIMER barCapable=true"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt" "S0103" "Fix landscape black-screen + audio visibility; slideshow as fixed anchor; sleep timer click listener"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/activity_player_unified.xml" "S0103" "Move btnSlideshowCmd to fixed anchor before nav block; add btnSleepTimerCmd"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/activity_player_unified.xml" "S0103" "Add btnSleepTimerCmd; note slideshow already in correct position"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "S0103" "Catalog regen after command panel changes"
> ```

**Verification:**

- Run `.\scripts\add_to_dev_log.ps1` with no args (or check `dev/CHANGELOG.md`) — confirm S0103 entries exist for all five files above.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Dev log entries recorded for all five files.

---

## Phase Done Criteria

- [x] Every Step above is `[x] done`.
- [x] `Grep` — `Timber\.d\("S0103:` returns zero hits across all `.kt` files.
- [x] `dev/CHANGELOG.md` updated.
- [ ] Run `/spec-check S0103` — result should be `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

No code changes in this phase. Revert any generated catalog files if needed; re-run `scan.ps1` + `render.ps1` to restore from source.
