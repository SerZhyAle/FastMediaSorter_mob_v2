# Phase 05 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0050_player-black-screen-mode.md`](../S0050_player-black-screen-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** — (final phase)
**Steps done:** 4 / 4
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Update the trilingual feature documentation, regenerate the module catalog, and confirm all dev-log entries are in place. This phase has no code changes.

---

## Prerequisites

- [ ] All phases 01–04 are ✅ Done.
- [ ] Feature works end-to-end (manual smoke test: toggle setting ON, press Black Screen button, verify screen goes black, tap to dismiss).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | existing file |
| `docs/FEATURES_RU.md` | Modified | existing file |
| `docs/FEATURES_UK.md` | Modified | existing file |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto) | regenerated |
| `dev/CATALOG/app_v2.md` | Modified (auto) | regenerated |

---

## Steps

### Step 5.1 — Update FEATURES.md (EN)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In section **7. Video Player** and section **9. Audio Player**, add the following bullet under each:
>
> - **Black Screen mode**: A toolbar button (enabled in Settings › Behaviour) collapses the screen to solid black while playback continues uninterrupted — ideal for hands-free listening while driving. Volume keys and media controls (play/pause, next, previous) remain active; any other tap on the screen instantly restores the player UI. Assignable keyboard shortcut included.

**Verification:**

- `Grep` — `Black Screen mode` in `docs/FEATURES.md` (2 hits: one in §7, one in §9).

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 1/1 PASS (2 hits in FEATURES.md: §7 and §9). Files: docs/FEATURES.md (+2 bullets). Dev log recorded.

---

### Step 5.2 — Update FEATURES_RU.md

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 5.1

**Prompt for developer:**

> Mirror the new bullet in both relevant sections of `docs/FEATURES_RU.md`:
>
> - **Режим «Чёрный экран»**: Кнопка в панели плеера (включается в Настройки › Поведение) переводит экран в режим полной темноты без остановки воспроизведения — удобно слушать музыку или подкасты за рулём. Кнопки громкости и медиаклавиши (воспроизведение, следующий, предыдущий) продолжают работать; любое касание экрана мгновенно возвращает интерфейс плеера. Поддерживается назначение клавиатурного сочетания.

**Verification:**

- `Grep` — `Режим «Чёрный экран»` in `docs/FEATURES_RU.md` (2 hits).

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 1/1 PASS (2 hits). Files: docs/FEATURES_RU.md (+2 bullets). Dev log recorded.

---

### Step 5.3 — Update FEATURES_UK.md

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 5.1

**Prompt for developer:**

> Mirror the new bullet in both relevant sections of `docs/FEATURES_UK.md`:
>
> - **Режим «Чорний екран»**: Кнопка на панелі плеєра (вмикається в Налаштування › Поведінка) переводить екран у режим повної темряви без зупинки відтворення — зручно слухати музику або подкасти за кермом. Кнопки гучності та медіаклавіші (відтворення, наступний, попередній) продовжують працювати; будь-яке торкання екрана миттєво повертає інтерфейс плеєра. Підтримується призначення клавіатурного скорочення.

**Verification:**

- `Grep` — `Режим «Чорний екран»` in `docs/FEATURES_UK.md` (2 hits).

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 1/1 PASS (2 hits). Files: docs/FEATURES_UK.md (+2 bullets). Dev log recorded.

---

### Step 5.4 — Regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 5.1

**Prompt for developer:**

> Run catalog scan and render for the `app_v2` module:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Then set `role` and `status` for the new `BlackScreenOverlayManager` class via `set.ps1` (see `dev/CATALOG/README.md`):
> - Role: `overlay-manager`
> - Status: `active`
>
> Add dev-log entries for every file modified across all five phases that hasn't already been logged.

**Verification:**

- `Grep` — `BlackScreenOverlayManager` in `dev/CATALOG/app_v2.md`.
- `Grep` — `BlackScreenOverlayManager` in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: dev/CATALOG/app_v2.jsonl (regen 883 files), dev/CATALOG/app_v2.md (render). BlackScreenOverlayManager role=overlay-manager, status=new set via set.ps1. Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 5.* above is `[x] done`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entries present for all modified files (verify `dev/CHANGELOG.md` tail).
- [ ] Run `/spec-check S0050` — expect `Verified` or `Partial` (partial only if a strategic criterion is explicitly deferred).

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Documentation and catalog changes only. Revert commits if needed; no functional code affected.
