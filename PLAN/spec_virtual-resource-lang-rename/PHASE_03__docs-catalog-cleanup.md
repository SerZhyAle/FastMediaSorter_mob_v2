# Phase 03 — docs-catalog-cleanup

**Strategic spec:** [`../spec_virtual-resource-lang-rename.md`](../spec_virtual-resource-lang-rename.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** nothing — final phase
**Steps done:** 3 / 3
**Started:** 2026-04-26
**Completed:** 2026-04-26

---

## Objective

Update trilingual FEATURES docs, regenerate the catalog, and close the dev changelog for every file touched in this spec.

---

## Prerequisites

- [x] Phase 01 is `✅ Done`.
- [x] Phase 02 is `✅ Done`.
- [x] Build is green.

---

## Files Touched

| File | New / Modified | Line budget |
| --- | --- | --- |
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | — |
| `dev/CATALOG/app_v2.md` | Modified (regenerated) | — |

---

## Steps

### Step 3.1 — Update trilingual FEATURES docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In each FEATURES file, locate the "Virtual resources" or "Resources" section (search for "virtual" or "All Images"). Add one bullet point describing the new auto-rename behaviour, using the exact text below. All three files must be updated in the same commit.
>
> **EN** (`docs/FEATURES.md`):
> `Virtual resources (All Images, All Videos, All Music, etc.) are automatically renamed when the app language changes, provided their name has not been manually edited.`
>
> **RU** (`docs/FEATURES_RU.md`):
> `Виртуальные ресурсы (Все изображения, Все видео, Вся музыка и др.) автоматически переименовываются при смене языка приложения, если их имя не было изменено пользователем вручную.`
>
> **UK** (`docs/FEATURES_UK.md`):
> `Віртуальні ресурси (Усі зображення, Усі відео, Вся музика тощо) автоматично перейменовуються при зміні мови застосунку, якщо їхнє ім'я не було змінено користувачем вручну.`

**Verification:**

- `Grep` — `automatically renamed when the app language` matches in `docs/FEATURES.md`.
- `Grep` — `автоматически переименовываются при смене языка` matches in `docs/FEATURES_RU.md`.
- `Grep` — `автоматично перейменовуються при зміні мови` matches in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

---

### Step 3.2 — Regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 3.1

**Prompt for developer:**

> Run the catalog scan to pick up the two new classes and the changed constructor signature:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> ```
>
> After the scan completes, open `dev/CATALOG/app_v2.jsonl` and set `role` and `status` for the two new entries:
> - `VirtualResourceDefaultNames` — role: `"Hardcoded lookup table: virtual path × language → (name, comment) pairs for auto-rename"`, status: `"stable"`
> - `RenameVirtualResourcesUseCase` — role: `"On-startup use-case: renames virtual resources whose name/comment matches a non-current-language default"`, status: `"stable"`
>
> Use `set.ps1` as described in `dev/CATALOG/README.md`.

**Verification:**

- `Grep` — `VirtualResourceDefaultNames` appears in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `RenameVirtualResourcesUseCase` appears in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `"role"` appears on the same line as `VirtualResourceDefaultNames` in that file (role field set).
- `Grep` — `"role"` appears on the same line as `RenameVirtualResourcesUseCase` in that file.

**Status:** `[x]` done

---

### Step 3.3 — Add dev log entries for all touched files

**Files:** `dev/CHANGELOG.md` (via script only)
**Depends on:** Step 3.2

**Prompt for developer:**

> Run the following commands (one per modified file):
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/VirtualResourceDefaultNames.kt" "spec-dev" "Add hardcoded default-names table for virtual resource auto-rename"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RenameVirtualResourcesUseCase.kt" "spec-dev" "Add use-case: auto-rename virtual resources on language change"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt" "spec-dev" "Wire RenameVirtualResourcesUseCase into startup initializer"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt" "spec-dev" "Inject and pass RenameVirtualResourcesUseCase to AppStartupInitializer"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "spec-dev" "Document virtual-resource auto-rename feature (EN)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "spec-dev" "Document virtual-resource auto-rename feature (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "spec-dev" "Document virtual-resource auto-rename feature (UK)"
> ```

**Verification:**

- `Grep` — `VirtualResourceDefaultNames` appears in `dev/CHANGELOG.md`.
- `Grep` — `RenameVirtualResourcesUseCase` appears in `dev/CHANGELOG.md`.
- `Grep` — `AppStartupInitializer` appears in `dev/CHANGELOG.md` (from this spec run).
- `Grep` — `FastMediaSorterApp` appears in `dev/CHANGELOG.md` (from this spec run).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 3.*` above is `[x] done`.
- [x] All three FEATURES files contain the new auto-rename bullet.
- [x] `dev/CATALOG/app_v2.jsonl` has role+status set for both new classes.
- [x] `dev/CHANGELOG.md` has entries for all 7 files listed in Step 3.3.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the FEATURES doc edits and catalog regeneration. No code change in this phase.

---

## Revision History

- **2026-04-26** — by `/spec-update` (`claude-sonnet-4-6`, focus: all six)
  - ACCEPT applied: 0
  - REVIEW applied: 0
  - DISCUSS proposed: 0 items — phase clean
