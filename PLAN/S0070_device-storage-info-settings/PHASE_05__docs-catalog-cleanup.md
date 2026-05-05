# Phase 05 — docs-catalog-cleanup

**Strategic spec:** [`../S0070_device-storage-info-settings.md`](../S0070_device-storage-info-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** none
**Steps done:** 4 / 4
**Started:** —
**Completed:** —

---

## Objective

Update user-facing feature documentation (`docs/FEATURES*.md`) with device storage info feature; regenerate `dev/CATALOG/` if public API changed; finalize all dev log entries.

---

## Prerequisites

- [ ] Phase 02, Phase 03, Phase 04 are all ✅ Done.
- [ ] All code is committed and builds successfully.
- [ ] `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` exist and are readable.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 1500 |
| `docs/FEATURES_RU.md` | Modified | ≤ 1500 |
| `docs/FEATURES_UK.md` | Modified | ≤ 1500 |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto) | — |

---

## Steps

### Step 05.1 — Update `docs/FEATURES.md` (English)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, locate **section 19. Settings** and add the following bullet point (ideally after existing storage-related items):
> 
> ```markdown
> - **Device storage info:** Quick access to available storage on your device from General Settings, with a manual refresh button — no need to dig into system settings.
> ```
> 
> Maintain the existing format and ordering. The entry should be concise and user-focused.

**Verification:**

- `Grep` — `"Device storage info:"` found in `docs/FEATURES.md` in section 19 (Settings).

**Status:** `[ ]` not done

---

### Step 05.2 — Update `docs/FEATURES_RU.md` (Russian)

**Files:** `docs/FEATURES_RU.md`
**Depends on:** — start of phase (parallel with Step 05.1)

**Prompt for developer:**

> In `docs/FEATURES_RU.md`, locate **section 19. Настройки** and add the Russian equivalent:
> 
> ```markdown
> - **Информация о месте на устройстве:** Быстрый доступ к доступному месту во встроенном накопителе из основных настроек с кнопкой обновления — не нужно копаться в системных параметрах.
> ```

**Verification:**

- `Glob` — `docs/FEATURES_RU.md` exists.
- `Grep` — `"Информация о месте на устройстве:"` found in `docs/FEATURES_RU.md` in section 19.

**Status:** `[ ]` not done

---

### Step 05.3 — Update `docs/FEATURES_UK.md` (Ukrainian)

**Files:** `docs/FEATURES_UK.md`
**Depends on:** — start of phase (parallel with Step 05.1)

**Prompt for developer:**

> In `docs/FEATURES_UK.md`, locate **section 19. Налаштування** and add the Ukrainian equivalent:
> 
> ```markdown
> - **Інформація про місце на пристрої:** Швидкий доступ до доступного місця у вбудованому накопичувачі з основних налаштувань із кнопкою оновлення — не потрібно рутися в системних параметрах.
> ```

**Verification:**

- `Glob` — `docs/FEATURES_UK.md` exists.
- `Grep` — `"Інформація про місце на пристрої:"` found in `docs/FEATURES_UK.md` in section 19.

**Status:** `[ ]` not done

---

### Step 05.4 — Regenerate catalog and finalize dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1, 05.2, 05.3

**Prompt for developer:**

> 1. **Regenerate catalog** (if `GetDeviceStorageUseCase` is a new public domain class):
>    ```powershell
>    pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
>    pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
>    ```
>    Commit the updated `dev/CATALOG/app_v2.jsonl` and `app_v2.md` together with the code changes (already done in Phases 01–04 via individual dev log calls).
> 
> 2. **Add final dev log entries** for documentation:
>    ```powershell
>    .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "feature" "Add device storage info to feature list (EN)"
>    .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "feature" "Add device storage info to feature list (RU)"
>    .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "feature" "Add device storage info to feature list (UK)"
>    .\scripts\add_to_dev_log.ps1 "PLAN/S0070_device-storage-info-settings/INDEX.md" "spec-check" "Phase 05 complete — S0070 ready for verification"
>    ```

**Verification:**

- Catalog script runs without error (or catalog changes are negligible if no new public API).
- Dev log entries are added for documentation files.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles: run `/build`.
- [ ] Feature documentation updated in all three languages.
- [ ] Catalog regenerated (if applicable).
- [ ] All dev log entries finalized.
- [ ] `Grep -n "TODO(phase-05)"` returns zero hits.
- [ ] `PLAN/S0070_device-storage-info-settings/INDEX.md` shows all phases ✅ Done.

---

## Handoff Notes to Next Phase

**Final state:**
- All code, UI, strings, and documentation are complete.
- Ready for `/spec-check S0070` to verify implementation against strategic spec.
- No further phases required.

---

## Rollback Plan

Revert documentation changes in `docs/FEATURES*.md` and re-run catalog scripts to restore prior state. All code changes are unaffected.
