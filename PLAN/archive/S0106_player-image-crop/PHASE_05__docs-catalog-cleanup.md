# Phase 05 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0106_player-image-crop.md`](../S0106_player-image-crop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** none — final phase
**Steps done:** 4 / 4
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Update the trilingual feature documentation and regenerate the class catalog. This is the completion gate for S0106.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto) | — |

---

## Steps

### Step 5.1 — Update docs/FEATURES.md (EN)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, locate the **Media Player / Image Viewer** section and append a bullet:
>
> `- Crop (overwrite original), Crop to file (save fragment as new file), and Compressed copy (JPG 70%, long side ≤ 1024 px) commands in the overflow menu; work on local and network sources (SMB, SFTP, cloud); read-only sources auto-redirect to Downloads.`

**Verification:**

- `Grep` — `Crop to file` present in `docs/FEATURES.md`.

**Status:** `[x]` done

---

### Step 5.2 — Update docs/FEATURES_RU.md (RU)

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 5.1

**Prompt for developer:**

> In `docs/FEATURES_RU.md`, in the corresponding **Плеер / Просмотр изображений** section, append:
>
> `- Команды «Вырезать» (перезапись оригинала), «Вырезать в файл» (сохранение фрагмента как нового файла) и «Сжатая копия» (JPG 70%, длинная сторона ≤ 1024 пкс) в overflow-меню; работают с локальными и сетевыми источниками (SMB, SFTP, облако); для ресурсов «только для чтения» результат сохраняется в Загрузки.`

**Verification:**

- `Grep` — `Вырезать в файл` present in `docs/FEATURES_RU.md`.

**Status:** `[x]` done

---

### Step 5.3 — Update docs/FEATURES_UK.md (UK)

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 5.1

**Prompt for developer:**

> In `docs/FEATURES_UK.md`, in the corresponding **Плеєр / Перегляд зображень** section, append:
>
> `- Команди «Обрізати» (перезапис оригіналу), «Обрізати у файл» (збереження фрагмента як нового файлу) та «Стиснута копія» (JPG 70%, довга сторона ≤ 1024 пкс) в overflow-меню; працюють з локальними та мережевими джерелами (SMB, SFTP, хмара); для джерел «тільки для читання» результат зберігається у Завантаження.`

**Verification:**

- `Grep` — `Обрізати у файл` present in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

---

### Step 5.4 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` (auto-generated)
**Depends on:** Steps 5.1–5.3

**Prompt for developer:**

> Run the catalog pipeline:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Then set `role` and `status` for new classes via `set.ps1` (see `dev/CATALOG/README.md`):
> - `ImageCropManager` — role: `"Crop/compress engine for image player"`, status: `active`
> - `CropOverlayView` — role: `"Interactive crop selection overlay view"`, status: `active`

**Verification:**

- `Grep` — `ImageCropManager` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `CropOverlayView` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `ImageCropManager` present in `dev/CATALOG/app_v2.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entries added for `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` via `.\scripts\add_to_dev_log.ps1`.
- [ ] Run `/spec-check S0106` — expect `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s). No code or data migration changes.
