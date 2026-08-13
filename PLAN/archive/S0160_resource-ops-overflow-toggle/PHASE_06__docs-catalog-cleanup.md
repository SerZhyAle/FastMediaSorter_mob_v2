# Phase 06 — docs-catalog-cleanup

**Strategic spec:** [`../S0160_resource-ops-overflow-toggle.md`](../S0160_resource-ops-overflow-toggle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all previous phases
**Blocks:** nothing — final phase
**Steps done:** 3 / 3
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Update the trilingual feature docs, regenerate the class catalog, and ensure the dev changelog is complete.

---

## Prerequisites

- [ ] All phases 01–05 are ✅ Done.
- [ ] On-device smoke test passed (setting toggle → overflow mode visible on list and grid; Refresh updates file count; unavailability toast appears for unreachable resource).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-generated) | — |

---

## Steps

### Step 06.1 — Update `docs/FEATURES.md` + mirrors

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Use `/doc-update` to add the following bullet to section **1. Resource / Source Management** in all three feature files:
>
> **EN** (`FEATURES.md`):
> > **Compact resource actions**: An optional setting collapses per-resource action buttons into a single ⋮ overflow menu, reducing visual clutter on small screens and in portrait orientation. The overflow menu includes a dedicated Refresh action to test availability and update the file count for a single resource without opening it.
>
> **RU** (`FEATURES_RU.md`):
> > **Компактные операции с ресурсами**: Настройка сворачивает кнопки действий каждой планки ресурса в единое меню «⋮», уменьшая визуальный шум на небольших экранах и в портретной ориентации. В меню добавлен пункт «Обновить» для проверки доступности и обновления счётчика файлов одного ресурса без его открытия.
>
> **UK** (`FEATURES_UK.md`):
> > **Компактні операції з ресурсами**: Налаштування згортає кнопки дій кожної панелі ресурсу в єдине меню «⋮», зменшуючи візуальний шум на малих екранах і в портретній орієнтації. У меню додано пункт «Оновити» для перевірки доступності та оновлення лічильника файлів одного ресурсу без його відкриття.

**Verification:**

- `Grep` — `Compact resource actions` matches in `docs/FEATURES.md`.
- `Grep` — `Компактные операции` matches in `docs/FEATURES_RU.md`.
- `Grep` — `Компактні операції` matches in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 3/3 PASS. Files: docs/FEATURES.md, docs/FEATURES_RU.md, docs/FEATURES_UK.md (+1 bullet each). Dev log recorded.

---

### Step 06.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — can run in parallel with Step 06.1

**Prompt for developer:**

> Run:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Verify that `ResourceAdapter`, `ResourceScanCoordinator`, and `MainViewModel` appear in the updated catalog.

**Verification:**

- `Grep` — `ResourceAdapter` matches in `dev/CATALOG/app_v2.md`.
- `Grep` — `scanAndRefreshSingleResource` matches in `dev/CATALOG/app_v2.jsonl` or the catalog reflects the updated `ResourceScanCoordinator` entry.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 2/2 PASS. scan.ps1 (1037 records) + render.ps1 regenerated. ResourceAdapter, ResourceScanCoordinator, MainViewModel present in catalog.

---

### Step 06.3 — Add dev changelog entries for all modified files

**Files:** `dev/CHANGELOG.md` (via script — do not edit directly)
**Depends on:** Step 06.1, Step 06.2

**Prompt for developer:**

> Run `add_to_dev_log.ps1` for every file touched across all phases. Minimum required entries (one line each):
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/.../domain/model/AppSettings.kt" "S0160" "Add resourceOpsInOverflowMenu flag"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/.../data/repository/SettingsRepositoryImpl.kt" "S0160" "Wire resourceOpsInOverflowMenu to DataStore"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/.../ui/main/helpers/ResourceScanCoordinator.kt" "S0160" "Add public scanAndRefreshSingleResource"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/.../ui/main/MainViewModel.kt" "S0160" "Add scanSingleResource method"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/.../ui/main/ResourceAdapter.kt" "S0160" "Add overflow mode and scan callback"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/menu/resource_item_actions.xml" "S0160" "Add action_scan item"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/item_resource_grid.xml" "S0160" "Add btnMoreActions for overflow mode"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings_s0160.xml" "S0160" "Add EN strings"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings_s0160.xml" "S0160" "Add RU strings"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings_s0160.xml" "S0160" "Add UK strings"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/fragment_settings_general.xml" "S0160" "Add resourceOpsInOverflowMenu toggle"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/fragment_settings_general.xml" "S0160" "Add resourceOpsInOverflowMenu toggle landscape"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/.../ui/settings/helpers/GeneralSettingsViewSetupHelper.kt" "S0160" "Wire overflow toggle switch"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/.../ui/settings/helpers/GeneralSettingsObserversHelper.kt" "S0160" "Observe overflow toggle state"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/.../ui/main/MainActivity.kt" "S0160" "Wire onScanClick and setOverflowModeEnabled"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0160" "Add compact resource actions bullet"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0160" "Add compact resource actions bullet RU"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0160" "Add compact resource actions bullet UK"
> ```

**Verification:**

- `Grep` — `S0160` matches in `dev/CHANGELOG.md` at least 15 times.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 1/1 PASS. S0160 in CHANGELOG.md: 29 occurrences (≥15). All 18 files logged across phases.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Final `/spec-check S0160` run succeeds and advances status to `Verified`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit — no code changes, only docs/catalog. Revert `docs/FEATURES*.md` to prior content.
