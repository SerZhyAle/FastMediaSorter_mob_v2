# Phase 04 — Docs, Catalog, and Cleanup

**Strategic spec:** [`../S0094_player-move-currently-playing.md`](../S0094_player-move-currently-playing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01, 02, 03, 05, 06
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Update trilingual feature docs, regenerate the class catalog, and confirm the dev log is complete.

---

## Prerequisites

- [ ] Phases 01, 02, 03, 05, 06 are all ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (generated) | — |

---

## Steps

### Step 04.1 — Add feature bullet to trilingual FEATURES docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, locate the Player or File Operations section and add three bullets:
> ```
> - Moving the currently playing file immediately stops playback, advances to the next track, and performs the transfer in the background; on transfer error the file remains in the playlist.
> - Deleting the currently playing file immediately stops playback and advances to the next track; if no files remain, the player closes.
> - Renaming the currently playing file stops playback before the rename executes; on success, the player resumes on the same file with its new name without reloading the playlist.
> ```
>
> In `docs/FEATURES_RU.md`, add equivalent Russian bullets:
> ```
> - При перемещении воспроизводимого файла плеер немедленно переходит к следующему треку; перенос выполняется в фоне без прерывания нового воспроизведения; при ошибке переноса файл остаётся в списке.
> - При удалении воспроизводимого файла плеер немедленно переходит к следующему треку; если файлов не осталось — плеер закрывается.
> - При переименовании воспроизводимого файла воспроизведение останавливается до начала операции; после успеха плеер возобновляет воспроизведение под новым именем на той же позиции в плейлисте.
> ```
>
> In `docs/FEATURES_UK.md`, add equivalent Ukrainian bullets:
> ```
> - При переміщенні відтворюваного файлу плеєр негайно переходить до наступного треку; перенесення виконується у фоні без переривання нового відтворення; при помилці перенесення файл залишається у списку.
> - При видаленні відтворюваного файлу плеєр негайно переходить до наступного треку; якщо файлів не залишилось — плеєр закривається.
> - При перейменуванні відтворюваного файлу відтворення зупиняється до початку операції; після успіху плеєр відновлює відтворення під новою назвою на тій самій позиції у плейлисті.
> ```

**Verification:**

- `Grep` — `immediately stops playback` present in `docs/FEATURES.md`.
- `Grep` — `Deleting the currently playing file` present in `docs/FEATURES.md`.
- `Grep` — `Renaming the currently playing file` present in `docs/FEATURES.md`.
- `Grep` — `немедленно переходит к следующему треку` present in `docs/FEATURES_RU.md` (at least twice).
- `Grep` — `негайно переходить до наступного треку` present in `docs/FEATURES_UK.md` (at least twice).

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 5/5 PASS. All three EN bullets present; немедленно×2 in RU; негайно×2 in UK.

---

### Step 04.2 — Regenerate app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and modification time is today.
- `Glob` — `dev/CATALOG/app_v2.md` exists and modification time is today.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 2/2 PASS. Both catalog files exist with today's modification time.

---

### Step 04.3 — Run locale string audit

**Files:** _(read-only check)_
**Depends on:** Step 04.1

**Prompt for developer:**

> No new string keys were added in this feature. Confirm no parity regressions by running:
> ```powershell
> pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_move"
> ```
> Exit code must be 0.

**Verification:**

- Exit code of `check_strings_localized.ps1 -KeyPrefix "error_move"` is 0.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification PASS. check_strings_localized.ps1 exit code 0 — error_move and error_move_failed OK in EN/RU/UK.

---

### Step 04.4 — Dev log entry and spec status

**Files:** `dev/CHANGELOG.md`
**Depends on:** Steps 04.1–04.3

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0094 Phase 04" "Add player-move feature bullet (EN/RU/UK)"
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0094 -Status Implemented
> ```

**Verification:**

- `Grep` — `S0094 Phase 04` present in `dev/CHANGELOG.md`.
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0094 -Format json` returns `"status": "Implemented"`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-05 — Verification 2/2 PASS. S0094 Phase 04 in CHANGELOG at line 6333; status=Implemented in journal.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] All three FEATURES docs contain the new bullet.
- [x] Catalog regenerated (Step 04.2).
- [x] Locale audit exits 0 (Step 04.3).
- [x] `S0094` status = `Implemented` in journal.
- [ ] Run `/spec-check S0094` to advance status to `Verified`. MANUAL-REQUIRED

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) — docs and catalog only, no code changes.
