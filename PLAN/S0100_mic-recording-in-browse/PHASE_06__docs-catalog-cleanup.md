# Phase 06 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0100_mic-recording-in-browse.md`](../S0100_mic-recording-in-browse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all previous phases
**Blocks:** nothing
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Update trilingual feature docs, regenerate the class catalog, and produce dev log entries that close out the ticket.

---

## Prerequisites

- [ ] All phases 01–05 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 500 |
| `docs/FEATURES_RU.md` | Modified | ≤ 500 |
| `docs/FEATURES_UK.md` | Modified | ≤ 500 |
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (generated) | — |

---

## Steps

### Step 6.1 — Update FEATURES.md (EN)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, section **3. File Operations** (after the **Camera capture** bullet), add:
>
> ```
> - **Microphone recording**: Hold the microphone button in the Browse command bar to record audio directly into the current folder — local, network (SMB/SFTP/FTP), or cloud. The recorded file is named by date and time (`REC_YYYYMMDD_HHmmss.m4a`); an optional filename dialog appears before saving. Disabled by default; enable in Settings → Audio → Microphone Recording. Button is hidden automatically on devices/flavors without microphone recording support.
> ```

**Verification:**

- `Grep` — `Microphone recording` present in `docs/FEATURES.md`.

**Status:** `[ ]` not done

---

### Step 6.2 — Update FEATURES_RU.md

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 6.1

**Prompt for developer:**

> In `docs/FEATURES_RU.md`, in the section corresponding to **3. Операции с файлами** (after the bullet про захват кадра с камеры), add:
>
> ```
> - **Запись с микрофона**: удержите кнопку микрофона в командной панели Browse, чтобы записать аудио прямо в текущую папку — локальную, сетевую (SMB/SFTP/FTP) или облачную. Файл называется по дате и времени (`REC_YYYYMMDD_HHmmss.m4a`); перед сохранением опционально появляется диалог переименования. Отключено по умолчанию; включается в Настройки → Аудио → Запись с микрофона.
> ```

**Verification:**

- `Grep` — `Запись с микрофона` present in `docs/FEATURES_RU.md`.

**Status:** `[ ]` not done

---

### Step 6.3 — Update FEATURES_UK.md

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 6.1

**Prompt for developer:**

> In `docs/FEATURES_UK.md`, in the section corresponding to **3. Операції з файлами** (after the bullet про захоплення кадру з камери), add:
>
> ```
> - **Запис із мікрофона**: утримуйте кнопку мікрофона у командній панелі Browse, щоб записати аудіо прямо в поточну папку — локальну, мережеву (SMB/SFTP/FTP) або хмарну. Файл називається за датою й часом (`REC_YYYYMMDD_HHmmss.m4a`); перед збереженням опційно з'являється діалог перейменування. Вимкнено за замовчуванням; вмикається в Налаштування → Аудіо → Запис із мікрофона.
> ```

**Verification:**

- `Grep` — `Запис із мікрофона` present in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 6.4 — Regenerate class catalog and produce dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Steps 6.1, 6.2, 6.3

**Prompt for developer:**

> Run the following commands in order:
>
> ```powershell
> # 1. Scan new/modified Kotlin files into the catalog
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
>
> # 2. Set role for the new manager (if scan did not auto-populate it)
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class BrowseMicRecordingManager -Role "Manages microphone recording lifecycle and file save routing in Browse" -Status active
>
> # 3. Render human-readable catalog
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
>
> # 4. Dev log entries for all modified files (run one per file)
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md"    "spec" "S0100: add microphone recording feature bullet (EN)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "spec" "S0100: add microphone recording feature bullet (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "spec" "S0100: add microphone recording feature bullet (UK)"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "catalog" "S0100: add BrowseMicRecordingManager entry"
> ```

**Verification:**

- `Grep` — `BrowseMicRecordingManager` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `BrowseMicRecordingManager` present in `dev/CATALOG/app_v2.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 6.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] `dev/CHANGELOG.md` has entries for every file modified across all phases.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Run `/spec-check S0100` to advance status to `Verified`.

---

## Rollback Plan

Revert phase commit(s) — docs changes are purely additive; catalog is regenerated from source.
