# Phase 05 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0693_landscape-mode-min-width-threshold.md`](../S0693_landscape-mode-min-width-threshold.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** -
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog for the new public node, record the shipped capability, and confirm no non-player aspect-ratio decision remains.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored) | tool |
| `docs/ALL_FEATURES.jsonl` | Appended | 1 record |
| `dev/CHANGELOG.md` | Appended (via script) | tool |

---

## Steps

### Step 05.1 - Set catalog role/status for the new node + regenerate

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Fill the catalog role/status for the new `WideLayout.kt` via `dev/CATALOG/scripts/set.ps1` (role: a core orientation/layout utility; status: active), then run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan and re-render. The catalog indexes are gitignored - regenerate, do not commit.

**Verification:**

- `Bash` - `catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - `WideLayout` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

---

### Step 05.2 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the shipped capability: wide high-resolution devices held in portrait now receive the landscape-style layout once available width reaches the 600dp threshold, via a single shared decision node. Validate with `scripts/all_features/validate.ps1`. Do not touch `docs/FEATURES*.md` (that is `/skill-release`-owned).

**Verification:**

- `Bash` - `scripts/all_features/validate.ps1` exits 0.
- `Grep` - a record mentioning the width threshold / wide-portrait landscape layout is present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x]` done

---

### Step 05.3 - Final no-regression sweep + dev log

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.2

**Prompt for developer:**

> Confirm the migration is complete and the rotation layer is untouched, then batch the dev-log entries. Run `.\scripts\add_to_dev_log.ps1` (or `close-and-log.ps1 -DevLogs`) for the node file, the migrated Kotlin files, and the new resource directories - one logical entry for the ticket.

**Verification:**

- `Grep` - `orientation == Configuration.ORIENTATION_LANDSCAPE` (and `widthPixels > heightPixels`) appears only in player-family files (`ui/player/**`); no non-player site remains.
- `Grep` - `programFollowSystemRotation` and `playerFollowSystemRotation` are unchanged in `AppOrientationManager.kt` / `AppSettings.kt` / `ScreenRotationManager.kt` (rotation policy not regressed).
- `Bash` - `dev/CHANGELOG.md` has an entry referencing S0693.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `docs/ALL_FEATURES.jsonl` has the capability record.
- [ ] `dev/CHANGELOG.md` has entries for every modified file/area.
- [ ] No non-player aspect-ratio decision remains in `src/main`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-dev` to execute, then on-device verification of wide-portrait screens, then `/spec-check S0693`.

---

## Rollback Plan

Revert the catalog/docs entries (regenerable). No runtime surface in this phase.
