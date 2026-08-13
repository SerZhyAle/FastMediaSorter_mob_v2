# Phase 03 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0074_copy-move-dialog-progress.md`](../S0074_copy-move-dialog-progress.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** — (final phase)
**Steps done:** 4 / 4
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Update trilingual feature docs, regenerate the app_v2 catalog, and record dev-log entries for all modified files.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Project compiles cleanly.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | +1 bullet |
| `docs/FEATURES_RU.md` | Modified | +1 bullet |
| `docs/FEATURES_UK.md` | Modified | +1 bullet |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |

---

## Steps

### Step 3.1 — Update `docs/FEATURES.md` (English)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, locate section **3. File Operations**. Add the following bullet at an appropriate position (after the existing copy/move bullets):
>
> ```
> - **Copy/move progress: percentage and ETA**: The copy/move progress dialog now shows the overall transfer percentage (byte-based, not file-count-based), current transfer speed, and estimated time remaining (ETA) with a moving-average speed for stability. All values refresh every 3 seconds to keep the UI responsive on slow network transfers.
> ```

**Verification:**

- `Grep` — `percentage and ETA` in `docs/FEATURES.md` returns exactly 1 match.

**Status:** `[ ]` not done

---

### Step 3.2 — Update `docs/FEATURES_RU.md` (Russian)

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 3.1

**Prompt for developer:**

> In `docs/FEATURES_RU.md`, find the section equivalent to **3. File Operations** and add:
>
> ```
> - **Прогресс копирования/перемещения — процент и ETA**: диалог копирования и перемещения теперь показывает общий процент выполнения (по байтам, а не по числу файлов), текущую скорость и оставшееся время (ETA) со сглаживанием скорости. Все значения обновляются каждые 3 секунды.
> ```

**Verification:**

- `Grep` — `процент и ETA` in `docs/FEATURES_RU.md` returns exactly 1 match.

**Status:** `[ ]` not done

---

### Step 3.3 — Update `docs/FEATURES_UK.md` (Ukrainian)

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 3.2

**Prompt for developer:**

> In `docs/FEATURES_UK.md`, find the section equivalent to **3. File Operations** and add:
>
> ```
> - **Прогрес копіювання/переміщення — відсоток і ETA**: діалог копіювання та переміщення тепер показує загальний відсоток виконання (за байтами, не за кількістю файлів), поточну швидкість і час, що залишився (ETA), зі згладжуванням швидкості. Усі значення оновлюються кожні 3 секунди.
> ```

**Verification:**

- `Grep` — `відсоток і ETA` in `docs/FEATURES_UK.md` returns exactly 1 match.

**Status:** `[ ]` not done

---

### Step 3.4 — Regenerate app_v2 catalog and add dev-log entries

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 3.3

**Prompt for developer:**

> Run catalog regeneration and record dev-log entries for every file modified across all phases.
>
> ```powershell
> # Catalog regen
> & "C:/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
>
> # Dev-log entries (one per modified file)
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt" "S0074" "Add totalOperationBytes to Starting, completedOperationBytes to Processing"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/dialog_file_operation_progress.xml" "S0074" "Add tvOverallPercent and tvEta views"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt" "S0074" "Show overall %, ETA, 3s throttle"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0074" "Add transfer_overall_progress_desc, transfer_eta_desc strings"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0074" "Add RU strings for transfer progress/ETA"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0074" "Add UK strings for transfer progress/ETA"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0074" "Document copy/move progress % and ETA feature"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0074" "RU feature doc: copy/move % and ETA"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0074" "UK feature doc: copy/move % and ETA"
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.md` exists and `Last` field shows today's date for `FileOperationProgressDialog`.
- `Grep` — `S0074` in `dev/CHANGELOG.md` returns ≥ 9 matches (one per modified file).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 3.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Run `/spec-check S0074` to close the ticket.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Run `/spec-check S0074` after this phase completes.

---

## Rollback Plan

Revert phase commit(s) — docs changes only; no code or data affected.
