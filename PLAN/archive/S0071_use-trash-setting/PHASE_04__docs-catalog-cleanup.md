# Phase 04 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0071_use-trash-setting.md`](../S0071_use-trash-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** —
**Steps done:** 3 / 3
**Completed:** 2026-05-03
**Started:** —
**Completed:** —

---

## Objective

Update the trilingual feature docs, regenerate the catalog, and record all dev log entries.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |

---

## Steps

### Step 4.1 — Update trilingual feature docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`

**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, locate the "File Operations" section. Add a new bullet:
>
> ```
> - **Move to trash toggle** — choose between moving deleted local files to `.trash` (recoverable via Undo) or permanently deleting them immediately (frees disk space at once). Network and cloud files are always deleted permanently.
> ```
>
> In `docs/FEATURES_RU.md`, add the Russian equivalent:
>
> ```
> - **Переключатель корзины** — выбор между перемещением удалённых локальных файлов в `.trash` (восстановление через Undo) и немедленным удалением (место освобождается сразу). Сетевые и облачные файлы всегда удаляются окончательно.
> ```
>
> In `docs/FEATURES_UK.md`, add the Ukrainian equivalent:
>
> ```
> - **Перемикач кошика** — вибір між переміщенням видалених локальних файлів до `.trash` (відновлення через Undo) та негайним видаленням (місце звільняється одразу). Мережеві та хмарні файли завжди видаляються остаточно.
> ```

**Verification:**

- `Grep` — `Move to trash toggle` present in `docs/FEATURES.md`.
- `Grep` — `Переключатель корзины` present in `docs/FEATURES_RU.md`.
- `Grep` — `Перемикач кошика` present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: docs/FEATURES.md (+1 LOC), docs/FEATURES_RU.md (+1 LOC), docs/FEATURES_UK.md (+1 LOC).

---

### Step 4.2 — Regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`

**Depends on:** — start of phase (independent of 4.1)

**Prompt for developer:**

> Run the catalog scan and render scripts:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```

**Verification:**

- `Grep` — `BrowseDeleteManager` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `PlayerDeleteUndoCoordinator` present in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS. Files: dev/CATALOG/app_v2.jsonl, dev/CATALOG/app_v2.md (918 records).

---

### Step 4.3 — Record dev log entries

**Files:** `dev/CHANGELOG.md` (via script, not direct edit)

**Depends on:** Steps 4.1, 4.2

**Prompt for developer:**

> Run the following for each modified file (adjust to actual changed files):
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0071" "Add tooltip_use_trash strings (EN)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0071" "Add tooltip_use_trash strings (RU)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0071" "Add tooltip_use_trash strings (UK)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/fragment_settings_destinations.xml" "S0071" "Add layoutUseTrash row to Safety section"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt" "S0071" "Wire switchUseTrash in Settings UI"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDeleteManager.kt" "S0071" "Honour useTrash setting in browse delete path"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt" "S0071" "Pass settingsRepository to BrowseDeleteManager"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDeleteUndoCoordinator.kt" "S0071" "Honour useTrash setting in player delete path"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0071" "Document use-trash toggle"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0071" "Document use-trash toggle (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0071" "Document use-trash toggle (UK)"
> ```

**Verification:**

- `Grep` — `S0071` appears ≥ 8 times in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification 1/1 PASS. S0071 appears 25 times in dev/CHANGELOG.md (≥ 8 required).

---

## Phase Done Criteria

- [ ] Every Step 04.* above is `[x] done`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Run `/spec-check S0071` — expect `Verified` or `Partial` with known exceptions.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) — docs-only changes; no code or data impact.
