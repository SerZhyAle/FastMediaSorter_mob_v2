# Phase 01 — Planner eligibility flip

**Strategic spec:** [`../S0217_bugfix-image-viewer-toolbar-overflow.md`](../S0217_bugfix-image-viewer-toolbar-overflow.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 1 / 1
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Flip `barCapable` from `false` to `true` for the five image-edit commands so the planner treats them as eligible for inline display. No layout or controller changes yet — buttons remain hidden because no inline view exists for them; the planner output will skip them via `barViewForCommand` returning `null`. This phase is the contract change only.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (none — foundation phase)
- [ ] Strategic §6 research items blocking this phase are Resolved. (all four resolved 2026-05-16)
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 400 |

---

## Steps

### Step 01.1 — Flip barCapable to true for OPEN_IN_SEPARATE_WINDOW, CROP, CROP_TO_FILE, COMPRESS_COPY, DRAW_OVERLAY

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `CommandPanelLayoutPlanner.PlayerCommand`, replace the third constructor argument (`barCapable`) from `false` to `true` for these five entries: `OPEN_IN_SEPARATE_WINDOW` (priority 610), `CROP` (620), `CROP_TO_FILE` (630), `COMPRESS_COPY` (640), `DRAW_OVERLAY` (650). Do not touch their priorities, ids, titles, or icons. Leave all other overflow-only entries (`SLEEP_TIMER`, `REOPEN_ENCODING`, `TOGGLE_MARKDOWN`, `READER_SETTINGS`, `READ_ALOUD`, `PDF_SCROLL_MODE`, `PDF_COLOR_MODE`, `PDF_THUMBNAILS`, `EPUB_READER_SETTINGS`, `EPUB_SEARCH_ALL`) with `barCapable = false`.

**Verification:**

- `Grep -n` — pattern `OPEN_IN_SEPARATE_WINDOW\(610, R\.id\.menu_open_in_separate_window, true,` matches exactly once. expected: 1 | actual: 1
- `Grep -n` — pattern `CROP\(620, R\.id\.menu_crop, true,` matches exactly once. expected: 1 | actual: 1
- `Grep -n` — pattern `CROP_TO_FILE\(630, R\.id\.menu_crop_to_file, true,` matches exactly once. expected: 1 | actual: 1
- `Grep -n` — pattern `COMPRESS_COPY\(640, R\.id\.menu_compress_copy, true,` matches exactly once. expected: 1 | actual: 1
- `Grep -n` — pattern `DRAW_OVERLAY\(650, R\.id\.menu_draw_overlay, true,` matches exactly once. expected: 1 | actual: 1
- `Grep` — no `Log\.d\(` calls present in the file (Timber-only rule). expected: 0 | actual: 0
- Build invariant: `assembleStandardDebug` compiles. expected: BUILD SUCCESSFUL | actual: (deferred to Phase Done Criteria)

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 7/7 PASS (5 grep matches, 0 Log.d, build deferred to Phase Done). Files: CommandPanelLayoutPlanner.kt (+3 LOC for S0217 comment). Dev log pending.

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the modified file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (no public API change but body changed).

---

## Handoff Notes to Next Phase

The five commands are now eligible-to-bar at the planner level. Until Phase 03 adds them to `barViewForCommand`, the planner's `result.barCommands` may contain them but `barViewForCommand(cmd)` will return `null` and they silently stay hidden. This is intentional — Phase 01 must not break the build or change observable behavior on its own.

---

## Rollback Plan

Revert phase commit — single-file, body-only change with no migrations or schema impact.
