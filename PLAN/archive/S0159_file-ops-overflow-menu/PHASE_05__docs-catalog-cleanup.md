# Phase 05 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0159_file-ops-overflow-menu.md`](../S0159_file-ops-overflow-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01–04
**Blocks:** — (final phase)
**Steps done:** 4 / 4
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Update trilingual FEATURES docs, sync the class catalog, and add dev log entries for all remaining files.

---

## Prerequisites

- [ ] Phases 01–04 are all ✅ Done.
- [ ] Working tree is clean or on the feature branch ready for final commit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | +1 bullet |
| `docs/FEATURES_RU.md` | Modified | +1 bullet |
| `docs/FEATURES_UK.md` | Modified | +1 bullet |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-generated) | — |

---

## Steps

### Step 5.1 — Update `docs/FEATURES.md` (EN)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Use `/doc-update` skill to add the following bullet to the **Browsing & File Management** section of `docs/FEATURES.md`. The skill mirrors the change to `_RU.md` and `_UK.md` automatically.
>
> EN bullet (exact text from strategic §8):
> > **File ops overflow menu** — Optional setting to collapse copy/move/rename/delete and other per-file actions into a single «⋮» overflow button on each file row; play button always stays separate.
>
> RU bullet:
> > **Меню ⋮ для операций с файлами** — Необязательная настройка, сворачивающая кнопки копировать/переместить/переименовать/удалить в единую кнопку «⋮» на каждой строке файла; кнопка воспроизведения всегда остаётся отдельной.
>
> UK bullet:
> > **Меню ⋮ для операцій з файлами** — Необов'язкове налаштування, що згортає кнопки копіювати/перемістити/перейменувати/видалити в єдину кнопку «⋮» на кожному рядку файлу; кнопка відтворення завжди залишається окремою.

**Verification:**

- `Grep` — `File ops overflow menu` present in `docs/FEATURES.md`.
- `Grep` — `Меню ⋮ для операций с файлами` present in `docs/FEATURES_RU.md`.
- `Grep` — `Меню ⋮ для операцій з файлами` present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS (EN/RU/UK bullets present). Files: `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` (+1 bullet each). Dev log recorded in Step 5.3.

---

### Step 5.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Phases 01–04

**Prompt for developer:**

> Run catalog scan and render for the `app_v2` module:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then set the role and status for the new class:
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class BrowseFileOverflowMenuManager -Role "Builds the per-file PopupMenu for overflow mode; filters items by permissions and file type." -Status active
> ```

**Verification:**

- `Grep` — `BrowseFileOverflowMenuManager` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `BrowseFileOverflowMenuManager` present in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 2/2 PASS (BrowseFileOverflowMenuManager in jsonl and md). Files: `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` (regenerated, role set). Dev log recorded in Step 5.3.

---

### Step 5.3 — Add dev log entries for all modified files

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Phases 01–04

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` for every file modified across all phases that was not already logged in that phase. Consolidate into one run:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md"    "S0159" "Add file-ops overflow menu feature bullet"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0159" "Add file-ops overflow menu feature bullet (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0159" "Add file-ops overflow menu feature bullet (UK)"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "S0159" "Catalog sync after S0159 implementation"
> ```

**Verification:**

- `Grep` — `S0159` present in `dev/CHANGELOG.md` (at least one entry per phase).

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification PASS (S0159 entries ≥38 in CHANGELOG). Files: `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`, `dev/CATALOG/app_v2.jsonl`. Dev log recorded.

---

### Step 5.4 — Advance spec status and run `/spec-check`

**Files:** `PLAN/S0159_file-ops-overflow-menu.md` (via `update.ps1`)
**Depends on:** Steps 5.1–5.3

**Prompt for developer:**

> Run `/spec-check S0159` to audit implementation against strategic DoD criteria. The skill will flip the status to `Verified` or `Partial` depending on findings, and write a `## Last Audit` section into the strategic spec file.

**Verification:**

- `Grep` — `## Last Audit` present in `PLAN/S0159_file-ops-overflow-menu.md`.
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0159 -Format json` — `status` field equals `Verified`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — `/spec-check S0159` → Verified. PASS 20, WARN 0, FAIL 0, MANUAL 6. `## Last Audit` written to strategic spec. Journal status: Verified. Debug tags: 0 removed.

---

## Phase Done Criteria

- [x] Every Step 5.* above is `[x] done`.
- [x] `dev/CATALOG/app_v2.md` contains `BrowseFileOverflowMenuManager` with a non-empty `role`.
- [x] FEATURES trilingual bullets verified present.
- [x] `dev/CHANGELOG.md` entries cover all phases.
- [x] `/spec-check S0159` returned `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md Completion Gate](INDEX.md#completion-gate).

---

## Rollback Plan

No code changes in this phase. Revert only if a doc/catalog error was introduced.
