# Phase 07 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05, Phase 06
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Update trilingual FEATURES documentation, regenerate the code catalog, and run a dev-log sweep. Final phase — see INDEX.md Completion Gate.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done.
- [ ] Phase 06 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |

---

## Steps

### Step 07.1 — Update `docs/FEATURES.md` (English)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Locate the multi-window section in `docs/FEATURES.md` (search for "Multi-Window" or add under the VR section). Add or replace with:
>
> ```
> **Multi-Window Mode (Quest 3+ / Samsung DeX and similar)**
> The "Allow opening in a separate window" setting (enabled by default on VR)
> unlocks three entry points:
> - Resource card icon → opens Browse for that resource in a new window;
>   main window stays on the home screen.
> - "In separate window" in the Browse top menu → tears off the current
>   Browse (resource, file, scroll position) to a new window; current
>   window returns to home screen.
> - "In separate window" in the player overflow menu → opens the same file
>   in a new player window from the beginning; current player closes and
>   returns to Browse.
> All windows are independent; closing one does not affect others. Playback
> position is not transferred when tearing off the player — seek manually.
> ```

**Verification:**

- `Grep` — `Allow opening in a separate window` matches in `docs/FEATURES.md`.
- `Grep` — `tears off` matches in `docs/FEATURES.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Added Multi-Window Mode bullet after last VR section bullet in FEATURES.md. Dev log recorded.

---

### Step 07.2 — Update `docs/FEATURES_RU.md` (Russian)

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Locate or add the multi-window section in `docs/FEATURES_RU.md`. Add or replace with the Russian equivalent:
>
> ```
> **Многооконный режим (Quest 3+ / Samsung DeX и аналоги)**
> Настройка «Разрешить запуск в отдельном окне» (включена по умолчанию
> в VR-версии) открывает три точки входа:
> - Иконка на карточке ресурса — Browse этого ресурса открывается
>   в новом окне; основное окно остаётся на главном экране.
> - «В отдельном окне» в верхнем меню Browse — текущий Browse
>   «отрывается» в новое окно с сохранением состояния (ресурс, файл,
>   позиция скролла); текущее окно уходит на главный экран.
> - «В отдельном окне» в overflow-меню плеера — плеер открывается
>   в новом окне для того же файла с начала; текущий плеер закрывается,
>   возврат в Browse.
> Все окна живут независимо; закрытие одного не влияет на остальные.
> Позиция воспроизведения при tear-off не передаётся — промотайте вручную.
> ```

**Verification:**

- `Grep` — `Разрешить запуск в отдельном окне` matches in `docs/FEATURES_RU.md`.
- `Grep` — `отрывается` matches in `docs/FEATURES_RU.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Added Многооконный режим bullet after last VR section bullet in FEATURES_RU.md. Dev log recorded.

---

### Step 07.3 — Update `docs/FEATURES_UK.md` (Ukrainian)

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Locate or add the multi-window section in `docs/FEATURES_UK.md`. Add or replace with the Ukrainian equivalent:
>
> ```
> **Режим кількох вікон (Quest 3+ / Samsung DeX і аналоги)**
> Налаштування «Дозволити запуск в окремому вікні» (увімкнено за
> замовчуванням у VR-версії) відкриває три точки входу:
> - Іконка на картці ресурсу — Browse цього ресурсу відкривається
>   в новому вікні; основне вікно залишається на головному екрані.
> - «В окремому вікні» у верхньому меню Browse — поточний Browse
>   «відривається» в нове вікно зі збереженням стану (ресурс, файл,
>   позиція прокрутки); поточне вікно повертається на головний екран.
> - «В окремому вікні» у меню плеєра (overflow) — плеєр відкривається
>   в новому вікні для того самого файлу з початку; поточний плеєр
>   закривається, повернення в Browse.
> Усі вікна живуть незалежно; закриття одного не впливає на інші.
> Позиція відтворення при tear-off не передається — перемотайте вручну.
> ```

**Verification:**

- `Grep` — `Дозволити запуск в окремому вікні` matches in `docs/FEATURES_UK.md`.
- `Grep` — `відривається` matches in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Added Режим кількох вікон bullet after last VR section bullet in FEATURES_UK.md. Dev log recorded.

---

### Step 07.4 — Regenerate catalog and run dev-log sweep

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Steps 07.1, 07.2, 07.3

**Prompt for developer:**

> 1. Run catalog scan and render:
>    ```powershell
>    pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
>    pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
>    ```
> 2. Run a dev-log sweep for every file modified across Phases 01–07 that hasn't been logged yet. Check `dev/CHANGELOG.md` against the full list of touched files. Add any missing entries via `.\scripts\add_to_dev_log.ps1`.
> 3. Run `/spec-check S0028` to confirm Verified.

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and its modification time is newer than `PHASE_06__player-tear-off.md`.
- `Grep` — `AppSettings` matches in `dev/CATALOG/app_v2.jsonl` with `allowSeparateWindow` visible.
- `Grep` — `tearOffPlayer` matches in `dev/CATALOG/app_v2.jsonl` or `app_v2.md` (confirms catalog picked up the new method).

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. catalog scan+render completed (921 files). tearOffPlayer confirmed in app_v2.md; allowSeparateWindow confirmed in app_v2.jsonl. Dev log recorded for all files.

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` all contain multi-window section.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] `Grep` for `TODO(phase-07)` returns zero hits.
- [x] All files in INDEX Completion Gate are checked.
- [x] `/spec-check S0028` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Docs-only changes — revert commit(s). Code catalog can be regenerated at any time.
