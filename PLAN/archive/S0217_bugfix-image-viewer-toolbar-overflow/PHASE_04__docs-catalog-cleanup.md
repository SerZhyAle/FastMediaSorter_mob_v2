# Phase 04 — Docs / catalog cleanup

**Strategic spec:** [`../S0217_bugfix-image-viewer-toolbar-overflow.md`](../S0217_bugfix-image-viewer-toolbar-overflow.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Finalize bookkeeping after the code changes: regenerate the class catalog, sweep the dev changelog for any straggling files, append a `FIX` entry to the functionality log. No `docs/FEATURES.md` update — strategic §8 explicitly says "Без изменений в docs/FEATURES" because this is a behavior fix on existing commands, not a new user-visible capability.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | — |
| `dev/CATALOG/app_v2.md` | Regenerated | — |
| `dev/CHANGELOG.md` | Appended | +N entries |
| `dev/FUNCTIONALITY.log` | Appended | +1 line |

---

## Steps

### Step 04.1 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` to refresh auto-fields (lines-of-code, method count) for the touched Kotlin files, then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` to regenerate the human-readable view. Manual fields (role, status, owner) are preserved. No new classes were introduced — `set.ps1` is not needed.

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` is non-empty. expected: file exists | actual: file exists
- `Grep -n` — pattern `"path": "com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt"` in `dev/CATALOG/app_v2.jsonl` returns 1 line. expected: 1 | actual: 1
- `Grep -n` — pattern `"path": "com/sza/fastmediasorter/ui/player/CommandPanelController.kt"` in `dev/CATALOG/app_v2.jsonl` returns 1 line. expected: 1 | actual: 1
- `Grep -n` — pattern `"path": "com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt"` in `dev/CATALOG/app_v2.jsonl` returns 1 line. expected: 1 | actual: 1 (corrected pattern: no spaces around `:` in JSONL — verified 3 matches)

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 4/4 PASS (scan + render done, 3 target paths present in jsonl). Files: app_v2.jsonl, app_v2.md regenerated.

---

### Step 04.2 — Sweep dev changelog for any uncovered file

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Confirm each file modified across Phases 01..03 has its own `dev/CHANGELOG.md` entry. If any is missing, run `.\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"` to add it. Expected entries (one per file):
>
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` — target `app_v2` — `S0217 phase 01: barCapable flipped to true for image-edit commands`
> - `app_v2/src/main/res/layout/activity_player_unified.xml` — target `app_v2` — `S0217 phase 02: portrait inline buttons for image-edit`
> - `app_v2/src/main/res/layout-land/activity_player_unified.xml` — target `app_v2` — `S0217 phase 02: landscape inline buttons for image-edit`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt` — target `app_v2` — `S0217 phase 03: safeView accessors for inline image-edit buttons`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` — target `app_v2` — `S0217 phase 03: wire image-edit click listeners, barViewForCommand, getOverflowableButtons, landscape visibility`
> - `dev/CATALOG/app_v2.jsonl` — target `catalog` — `S0217 phase 04: regenerate catalog after image-edit toolbar wiring`
> - `dev/CATALOG/app_v2.md` — target `catalog` — `S0217 phase 04: regenerate catalog after image-edit toolbar wiring`

**Verification:**

- `Grep` — `S0217` returns ≥ 7 lines in `dev/CHANGELOG.md` (one per file). expected: ≥ 7 | actual: 15

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 1/1 PASS (15 S0217 entries in dev/CHANGELOG.md — covers strategic spec, INDEX, 4 phase files, 7 source/catalog files).

---

### Step 04.3 — Append functionality log entry (FIX)

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 04.2

**Prompt for developer:**

> Append one line via:
>
> ```powershell
> pwsh -File scripts/add_to_functionality_log.ps1 -Id S0217 -Op FIX -Description "Image-edit toolbar actions (crop, crop-to-file, compress copy, draw, open in separate window) now appear inline on the player toolbar when the screen has room, instead of always collapsing into the overflow menu — fix applies to portrait, landscape, and Big Buttons Mode."
> ```

**Verification:**

- `Grep -n` — pattern `^S0217\|FIX` in `dev/FUNCTIONALITY.log` returns ≥ 1 line. expected: ≥ 1 | actual: ≥ 1
- `Grep -n` — pattern `Image-edit toolbar actions` in `dev/FUNCTIONALITY.log` returns 1 line. expected: 1 | actual: 1

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 2/2 PASS (S0217 FIX entry appended to dev/FUNCTIONALITY.log).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (sanity check, no code change in this phase).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Spec catalog `update.ps1 -Id S0217 -Status BlockNeedUserTest` queued for `/spec-dev` end-of-run; not invoked by this phase directly.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md Completion Gate](INDEX.md#completion-gate).

After `/spec-dev` runs through all four phases, the ticket transitions Approved → Tactical → In Progress → Implemented → BlockNeedUserTest. The Phase 03 `Timber.d("S0217: …")` tag stays in code until on-device verification flips the status to `Verified` via `/spec-check`.

---

## Rollback Plan

Roll back Phases 03 → 02 → 01 in reverse order. Catalog/changelog/functionality-log entries in this phase are append-only and harmless to leave in place if implementation is reverted.
