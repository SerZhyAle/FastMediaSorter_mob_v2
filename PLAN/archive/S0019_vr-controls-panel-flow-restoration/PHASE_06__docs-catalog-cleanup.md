# Phase 06 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0019_vr-controls-panel-flow-restoration.md`](../S0019_vr-controls-panel-flow-restoration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01–04
**Blocks:** none — final phase
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Refresh `dev/CATALOG/app_v2.{jsonl,md}`; add `docs/FEATURES{,_RU,_UK}.md` bullets for the user-visible parts of S0019 (exit-to-player redirect, «Apply and 3D», immersive prev/next, passive HUD hints); ensure CHANGELOG entries cover all modified files.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Append-only | n/a |

---

## Steps

### Step 06.1 — Trilingual FEATURES bullets

**Files:** three `FEATURES*.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Add to the existing VR section a multi-line bullet:
>
> - EN: «Exit-to-flat-player keeps your file: leaving immersive opens the same file in the 2D player at the same position. The dialog has a new «Apply and 3D» button: change the stereo format and re-enter immersive in one click. In immersive, the controllers' prev/next buttons switch files within the resource without leaving the headset.»
> - RU/UK: переводы по тому же смыслу.
>
> Все три файла обновить в одной коммитной транзакции.

**Verification:**

- `Grep` — `Apply and 3D` matches at least 1 time in `docs/FEATURES.md`.
- `Grep` — `Применить и в 3D` matches at least 1 time in `docs/FEATURES_RU.md`.
- `Grep` — `Застосувати і в 3D` matches at least 1 time in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

---

### Step 06.2 — Catalog refresh

**Files:** `dev/CATALOG/app_v2.{jsonl,md}`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. Files modified in Phases 01-04 under `app_v2/src/main/` are catalogued; vr-flavor files are not (out of scanner scope).

**Verification:**

- `PowerShell` — both scan and render print success summaries.

**Status:** `[x]` done

---

### Step 06.3 — Dev-log coverage

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`)
**Depends on:** Step 06.2

**Prompt for developer:**

> Confirm 2026-04-28-or-later entries for: `VrTaskTransition.kt`, `VrPlayerActivity.kt`, `dialog_playback_control.xml`, three `strings.xml`, `PlaybackControlDialogFragment.kt`, the HUD composer file from Phase 04 step 04.1, three `FEATURES*.md`. Add missing entries via `.\scripts\add_to_dev_log.ps1`.

**Verification:**

- `Grep` — in `dev/CHANGELOG.md`, `VrTaskTransition` matches at least 1 time on a 2026-04-28-or-later line.
- `Grep` — `dialog_playback_apply_and_3d` matches at least 1 time.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.

---

## Rollback Plan

Documentation/catalog refresh — append-only, no rollback.
